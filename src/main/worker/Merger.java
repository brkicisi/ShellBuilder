package main.worker;

import java.awt.Point;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Map.Entry;

import com.xilinx.rapidwright.design.Cell;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Module;
import com.xilinx.rapidwright.design.ModuleInst;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.design.NetType;
import com.xilinx.rapidwright.design.blocks.PBlock;
import com.xilinx.rapidwright.device.Device;
import com.xilinx.rapidwright.device.PIP;
import com.xilinx.rapidwright.device.Site;
import com.xilinx.rapidwright.device.Tile;
import com.xilinx.rapidwright.edif.EDIFCell;
import com.xilinx.rapidwright.edif.EDIFCellInst;
import com.xilinx.rapidwright.edif.EDIFDesign;
import com.xilinx.rapidwright.edif.EDIFDirection;
import com.xilinx.rapidwright.edif.EDIFHierCellInst;
import com.xilinx.rapidwright.edif.EDIFLibrary;
import com.xilinx.rapidwright.edif.EDIFNet;
import com.xilinx.rapidwright.edif.EDIFNetlist;
import com.xilinx.rapidwright.edif.EDIFPort;
import com.xilinx.rapidwright.edif.EDIFPortInst;
import com.xilinx.rapidwright.edif.EDIFTools;
import com.xilinx.rapidwright.placer.handplacer.HandPlacer;
import com.xilinx.rapidwright.util.FileTools;
import com.xilinx.rapidwright.util.MessageGenerator;

import main.parser.ArgsContainer;
import main.tcl.TCLEnum;
import main.tcl.TCLScript;
import main.directive.Directive;
import main.directive.DirectiveBuilder;
import main.directive.DirectiveHeader;
import main.directive.DependancyMeta;
import main.util.DesignUtils;

/**
 * Main worker to cache and merge dcps.
 * <p>
 * Must be initialized with a design. Either an initial design is provided or a
 * new Design is created. After that, other designs can be merged into the
 * Merger. This is important if working with cells that only have wires since
 * such cells are copied from synth design and don't have a design themselves.
 * <p>
 * {@link main.top.ShellBuilder#runBuilder(main.directive.DirectiveBuilder)
 * ShellBuilder.runBuilder} runs all sibling directives in a directive builder
 * using a Merger.
 * <p>
 * A Merger object can only work at one level of hierarchy. To construct
 * hierarchial levels, merge the design from one Merger into another Merger. See
 * {@link main.top.ShellBuilder#runDirective(Directive directive, Merger merger)
 * ShellBuilder.runDirective} for how a build directive recurses on runBuilder
 * to create hierarchy.
 */
public class Merger {
	private Design design = null;
	private Device device = null;
	private EDIFNetlist synth_netlist = null;
	private Map<String, EDIFCellInst> wire_cells = new HashMap<>();
	private File final_dcp = null;

	/**
	 * Name of cache folder in iii directory.
	 */
	public static final String MODULE_CACHE = "moduleCache";

	/**
	 * Uninitialized Merger.
	 */
	public Merger() {
	}

	/**
	 * Initialize Merger by calling
	 * {@link #init(Design, DirectiveHeader, ArgsContainer) init}
	 */
	public Merger(Design design, DirectiveHeader head, ArgsContainer args) {
		init(design, head, args);
	}

	/**
	 * Initialize Merger.
	 * <p>
	 * Copies all cells and copies top level ports from synth design cell with same
	 * name as design d.
	 * 
	 * @param d    Base design to merge other designs into.
	 * @param head Data common to the sibling directives that will be merged into
	 *             this design.
	 * @param args Arguments from the command line.
	 */
	public void init(Design d, DirectiveHeader head, ArgsContainer args) {
		design = d;
		design.setDesignOutOfContext(!head.isBufferedInputs());
		design.setAutoIOBuffers(head.isBufferedInputs());
		device = design.getDevice();

		File synth_dcp = head.getTopLevelSynth();
		if (synth_dcp == null)
			return;

		Design synth = DesignUtils.safeReadCheckpoint(synth_dcp, args.verbose(), head.getIII());
		synth_netlist = synth.getNetlist();

		EDIFCell top = design.getNetlist().getTopCell();
		EDIFCell synth_top = synth_netlist.getCell(top.getName());
		if (synth_top == null)
			return;

		for (EDIFCellInst synth_inst : synth_top.getCellInsts()) {
			EDIFCell synth_cell = synth_inst.getCellType();
			design.getNetlist().migrateCellAndSubCells(synth_cell);
			if (synth_cell.isPrimitive())
				new EDIFCellInst(synth_inst.getName(), synth_cell, top);
		}

		for (EDIFPort synth_port : synth_top.getPorts())
			if (top.getPort(synth_port.getBusName()) == null)
				top.createPort(synth_port.getName(), synth_port.getDirection(), synth_port.getWidth());
	}

	/**
	 * Top level function of Merger class. Fetches dcp (from
	 * {@link Directive#getDCP() directive} or from .iii/{@link #MODULE_CACHE}),
	 * inserts it into design and connects it to as many {@link EDIFNet EDIFNets} as
	 * it can find using top level synthesized design from header.
	 * 
	 * @param directive Merge directive including the dcp to be merged.
	 * @param args      Arguments from command line.
	 */
	public void merge(Directive directive, ArgsContainer args) {
		Module mod = fetchAndPrepModule(directive, args);
		if (design == null) {
			// design hasn't been initialized yet
			if (directive.getHeader().getModuleName() != null)
				init(new Design(directive.getHeader().getModuleName(), mod.getDevice().getDeviceName()),
						directive.getHeader(), args);
			else
				init(new Design("top", mod.getDevice().getDeviceName()), directive.getHeader(), args);
		}
		insertOOC(mod, directive);
		connectAll(args);
		// TODO lock P&R here?
	}

	/**
	 * Find and load a {@link Module} from cache - place & route and store in cache
	 * if not already in cache.
	 * <p>
	 * Check if module exists in cache
	 * ({@link #findModuleInCache(Directive, ArgsContainer) findModuleInCache}). If
	 * it is not in cache, fetch dcp from {@link Directive#getDCP() directive}.
	 * Place and Route dcp ooc in pblock and save to cache.
	 * <p>
	 * Then load design from cache into a Module.
	 * 
	 * @param directive Merge directive including dcp and pblock (or build directive
	 *                  whose dcp has been set to built sub design dcp in cache).
	 * @param args      Arguments from command line.
	 * @return Placed and routed module.
	 */
	private Module fetchAndPrepModule(Directive directive, ArgsContainer args) {
		Module mod = null;
		boolean verbose = (args == null) ? false : args.verbose();

		if (directive.isOnlyWires()) {
			EDIFCell top = null;
			if (directive.getDCP() == null) {
				top = design.getNetlist().getTopCell();
				EDIFCell synth_top = synth_netlist.getCell(top.getName());
				wire_cells.put(directive.getInstName(), synth_top.getCellInst(top.getName()));
			} else {
				Design d = DesignUtils.safeReadCheckpoint(directive.getDCP(), directive.getHeader().isVerbose(),
						directive.getIII());
				EDIFCellInst ci = d.getNetlist().getTopCellInst();
				wire_cells.put(directive.getInstName(), ci);
			}
			return null;
		} else if (directive.getDCP() == null) {
			MessageGenerator.briefErrorAndExit("\nNo dcp in merge directive.\nExiting.");
		} else if (!directive.getDCP().exists()) {
			MessageGenerator.briefErrorAndExit("\nCould not find dcp '" + directive.getDCP().getAbsolutePath()
					+ "' specified in merge directive.\nExiting.");
		}

		boolean was_already_cached = false;
		String cached_dcp_str = null;

		// Try to find in cache. If is in the cache and is newer than all it's
		// dependancies, then use it.
		if (!directive.isRefresh()) {
			File cached_dcp = findModuleInCache(directive, args, false);
			if (cached_dcp != null) {
				// TODO reuse module if already loaded?
				// how to know if same implementation of module?

				// mod = (design == null) ? null : design.getModule(mod_name);
				// if (mod != null) {
				// printIfVerbose("\nReusing already loaded module '" + mod_name + "'",
				// verbose);
				// return mod;
				// }

				cached_dcp_str = cached_dcp.getAbsolutePath();
				was_already_cached = true;
			}
		}

		// if didn't find in cache or refresh requested then place and route from dcp.
		if (cached_dcp_str == null) {
			printIfVerbose("\nPlacing and routing '" + directive.getDCP().getAbsolutePath() + "'", verbose);
			cached_dcp_str = placeRouteOOC(directive, args);
		}
		if (cached_dcp_str == null)
			MessageGenerator.briefErrorAndExit("Failed to find design to load.");

		Design d = DesignUtils.safeReadCheckpoint(cached_dcp_str, verbose, directive.getIII());
		d.getNetlist().getTopCellInst();
		d.getNetlist().renameNetlistAndTopCell(d.getName());
		mod = new Module(d);

		String mod_name = FileTools.removeFileExtension(directive.getDCP().getName());
		mod.setName(mod_name);
		if (directive.getPBlockStr() != null)
			mod.setPBlock(directive.getPBlockStr());

		if (design == null) {
			// design hasn't been initialized yet
			if (directive.getHeader().getModuleName() != null)
				init(new Design(directive.getHeader().getModuleName(), mod.getDevice().getDeviceName()),
						directive.getHeader(), args);
			else
				init(new Design("top", mod.getDevice().getDeviceName()), directive.getHeader(), args);
		}
		// intended to remove black box (will end up removing any previous
		// implementation of the cell)
		// design.getNetlist().getWorkLibrary().removeCell(mod.getNetlist().getTopCell().getName());
		// design.getNetlist().migrateCellAndSubCells(mod.getNetlist().getTopCell());
		myMigrateCellAndSubCells(mod.getNetlist().getTopCell());

		if (was_already_cached)
			printIfVerbose("\nLoaded cached module '" + mod_name + "'", verbose);
		else
			printIfVerbose("\nLoaded new module '" + mod_name + "'", verbose);
		return mod;
	}

	/**
	 * Searches cache for a module.
	 * <p>
	 * Checks if design specified by directive exists in cache. If directive is a
	 * "build" then recurse on all designs this design is built from and check that
	 * none has been modified more recently than the candidate cached design for
	 * this directive. Else directive is a "merge", check that it's specified dcp is
	 * not more recent than the candidate cached design for this directive. Check
	 * for consistancy in the stored metadata and the specifications of this
	 * instruction.
	 * <p>
	 * Notes:
	 * <p>
	 * Specified pblock is used as part of search path for cache. Thus a cached
	 * design will not be found if the pblock of this directive doesn't match the
	 * pblock of any cached instance of this design (no pblock specified is a match
	 * with cached module not having a specified pblock).
	 * <p>
	 * A given design may be cached multiple times with different pblocks.
	 * <p>
	 * Pblock specification must be exactly consistant when searching cache
	 * especially when a pblock string consists of multiple rectangles.
	 * '{@literal <rect_1>}&nbsp;{@literal <rect_2>}' is not the same as
	 * '{@literal <rect_2>}&nbsp;{@literal <rect_1>}' even though they are the same
	 * in tcl.
	 * 
	 * @param directive      Merge or build directive to search for in cache.
	 * @param args           Arguments from command line.
	 * @param ignore_refresh If true does not check whether submodules have
	 *                       requested a refresh. If false returns null if any
	 *                       submodule requested a refresh.
	 * @return File if found in cache, matches dependancies and pblock, and up to
	 *         date. Null otherwise.
	 */
	public static File findModuleInCache(Directive directive, ArgsContainer args, boolean ignore_refresh) {
		return findModuleInCache(directive, args, new DependancyMeta.DepSet(), ignore_refresh);
	}

	/**
	 * Searches cache for a design assuming visited designs exist and are up to
	 * date.
	 * <p>
	 * Returns null if directive is a build and any of the subdirectives are to be
	 * refreshed.
	 * 
	 * @param directive      Merge or build directive to search for in cache.
	 * @param args           Arguments from command line.
	 * @param visited        Already visited modules.
	 * @param ignore_refresh If true does not check whether submodules have
	 *                       requested a refresh. If false returns null if any
	 *                       submodule requested a refresh.
	 * @return File if found in cache, matches dependancies and pblock, and up to
	 *         date. Null otherwise.
	 * 
	 * @see #findModuleInCache(Directive, ArgsContainer)
	 */
	public static File findModuleInCache(Directive directive, ArgsContainer args, DependancyMeta.DepSet visited,
			boolean ignore_refresh) {
		if (visited == null)
			visited = new DependancyMeta.DepSet();

		File cache_dir = new File(directive.getIII(), MODULE_CACHE);
		if (!cache_dir.isDirectory()) {
			printIfVerbose("\nCache directory was not found at '" + cache_dir.getAbsolutePath() + "'.", args.verbose());
			return null;
		}

		String module_name = getModuleName(directive, args);
		if (module_name == null)
			return null;

		File mod_dir = new File(cache_dir, module_name);
		if (!mod_dir.isDirectory()) {
			printIfVerbose("\nCan't find module '" + module_name + "' in cache.", args.verbose());
			return null;
		}
		String pblock = directive.getPBlockStr();
		File impl_dir;
		if (pblock != null) {
			impl_dir = new File(mod_dir, getPblockPath(pblock));
		} else {
			printIfVerbose("\nNo pblock specified for module '" + module_name + "'.", args.verbose());
			impl_dir = mod_dir;
		}

		if (!impl_dir.isDirectory()) {
			printIfVerbose("\nCan't find module '" + module_name + "' in cache.", args.verbose());
			return null;
		}
		File cached_dcp = new File(impl_dir, module_name + ".dcp");
		if (!cached_dcp.isFile()) {
			printIfVerbose("\nCan't find module '" + module_name + "' in cache.", args.verbose());
			return null;
		}
		File meta_file = new File(impl_dir, DependancyMeta.META_FILENAME);
		if (!meta_file.isFile()) {
			printIfVerbose("\nCan't find metadata for module '" + module_name + "' in cache.", args.verbose());
			return null;
		}

		// read metadata
		DependancyMeta meta = new DependancyMeta(meta_file, args.verbose());
		DependancyMeta.DepSet dep_set = new DependancyMeta.DepSet(meta);
		DependancyMeta.DepSet dep_set2 = new DependancyMeta.DepSet(dep_set);

		String s1 = directive.getHeader().getTopLevelSynth() == null ? ""
				: directive.getHeader().getTopLevelSynth().getAbsolutePath();
		String s2 = meta.getTopLevelSynth() == null ? "" : meta.getTopLevelSynth().getAbsolutePath();
		if (!s1.equals(s2)) {
			printIfVerbose(
					"Synth file from build directives '" + directive.getHeader().getTopLevelSynth()
							+ "' does not match synth file from dependancies '" + meta.getTopLevelSynth() + "'.",
					args.verbose());
			return null;
		}
		s1 = directive.getHeader().getInitial() == null ? "" : directive.getHeader().getInitial().getAbsolutePath();
		s2 = meta.getInitial() == null ? "" : meta.getInitial().getAbsolutePath();
		if (!s1.equals(s2)) {
			printIfVerbose(
					"Initial file from build directives '" + directive.getHeader().getInitial()
							+ "' does not match initial file from dependancies '" + meta.getInitial() + "'.",
					args.verbose());
			return null;
		}

		// for each subbuilder directive
		if (directive.isSubBuilder()) {
			if (directive.isRefresh() && !ignore_refresh) {
				printIfVerbose("Module " + module_name + " requested a refresh.", args.verbose());
				return null;
			}

			for (Directive dir : directive.getSubBuilder().getDirectives()) {
				if (dir.isOnlyWires())
					continue;
				// if sub module requested refresh don't use cached module
				if ((dir.isRefresh() || dir.getHeader().isRefresh()) && !ignore_refresh) {
					printIfVerbose("Module " + getModuleName(dir, args) + " requested a refresh.", args.verbose());
					return null;
				}
				String sub_mod = getModuleName(dir, args);
				String sub_pblock = (dir.getPBlockStr() == null) ? "" : dir.getPBlockStr();

				String sub_pblock_plus = getPblockPath(sub_pblock);
				if (!dep_set.contains(sub_mod, sub_pblock_plus)) {
					printIfVerbose("\nModule '" + sub_mod + "' with pblock '" + sub_pblock
							+ "' was not found the dependancies of " + module_name + ".", args.verbose());
					return null;
				}
				dep_set2.remove(sub_mod, sub_pblock_plus);

				if (visited.contains(sub_mod, sub_pblock))
					continue;
				visited.put(sub_mod, sub_pblock);

				File sub = findModuleInCache(dir, args, visited, ignore_refresh);
				if (sub == null)
					return null;

				if (!FileTools.isFileNewer(cached_dcp.getAbsolutePath(), sub.getAbsolutePath())) {
					printIfVerbose("\nModule '" + module_name + "' is outdated.", args.verbose());
					return null;
				}
			}
		} else {
			if (!FileTools.isFileNewer(cached_dcp.getAbsolutePath(), directive.getDCP().getAbsolutePath())) {
				printIfVerbose("\nModule '" + module_name + "' is outdated.", args.verbose());
				return null;
			}
			dep_set2.remove(getModuleName(directive, args), "");
		}

		if (!dep_set2.isEmpty()) {
			printIfVerbose("Not all dependancies of " + module_name + " were specified", args.verbose());
			return null;
		}
		printIfVerbose("Module '" + module_name + "' was found in cache.", args.verbose());
		return cached_dcp;
	}

	/**
	 * Places and routes the design out of context in the specified pblock.
	 * 
	 * @param directive Merge type directive containing design for place and route.
	 * @param args      Arguments from command line.
	 * @return String where output was written.
	 */
	private static String placeRouteOOC(Directive directive, ArgsContainer args) {
		String module_name = getModuleName(directive, args);
		if (module_name == null)
			return null;

		if (directive.getPBlockStr() == null) {
			// TODO should pblock be forced to exist for base designs?
			printIfVerbose("\nNo pblock specified for '" + module_name + "'.", args.verbose());
			// return null;
		}
		File cache_impl_dir;
		if (directive.getPBlockStr() == null)
			cache_impl_dir = new File(directive.getIII(), MODULE_CACHE + "/" + module_name);
		else
			cache_impl_dir = new File(directive.getIII(),
					MODULE_CACHE + "/" + module_name + "/" + getPblockPath(directive.getPBlockStr()));
		// create cache folder in iii
		if (!cache_impl_dir.isDirectory())
			cache_impl_dir.mkdirs();

		// write metadata for cache
		DependancyMeta.writeMeta(cache_impl_dir, directive, null, args);

		String options = (args == null) ? "f" : args.options("f");
		String input_dcp = directive.getDCP().getAbsolutePath();
		String output_dcp = cache_impl_dir.getAbsolutePath() + "/" + module_name + ".dcp";
		String tcl_script_file = directive.getIII().getAbsolutePath() + "/pblock_place_route_step.tcl";
		TCLScript script = new TCLScript(input_dcp, output_dcp, options, tcl_script_file);

		File src_constrs = new File(cache_impl_dir, XDCWriter.CONSTRAINTS_FILE);
		if (!(directive.isSubBuilder() && directive.getSubBuilder().getHeader().isBufferedInputs()))
			script.add(TCLEnum.READ_XDC, args.options(), "-unmanaged -mode out_of_context",
					src_constrs.getAbsolutePath());
		else
			script.add(TCLEnum.READ_XDC, args.options(), "-unmanaged", src_constrs.getAbsolutePath());

		if (directive.isSubBuilder())
			insertEncryptedModules(input_dcp, script, directive.getSubBuilder().getDirectives(), args);
		else
			insertEncryptedModules(input_dcp, script, Arrays.asList(directive), args);

		Design d = DesignUtils.safeReadCheckpoint(input_dcp, directive.getHeader().isVerbose(), directive.getIII());
		EDIFCell top = d.getNetlist().getTopCell();

		if (!top.getCellInsts().isEmpty()) {
			if (directive.getPBlockStr() != null) {
				String pblock_name = "[get_property TOP [current_design ]]" + "_pblock";
				script.addCustomCmd("create_pblock " + pblock_name);
				script.addCustomCmd(
						"resize_pblock -add {" + directive.getPBlockStr() + "} [get_pblocks " + pblock_name + "]");
				script.addCustomCmd("add_cells_to_pblock [get_pblocks " + pblock_name + "] [get_cells]");
				script.addCustomCmd("set_property CONTAIN_ROUTING 1 [get_pblocks " + pblock_name + "]");
				script.addCustomCmd("set_property SNAPPING_MODE ROUTING [get_pblocks " + pblock_name + "]");
			}
			// TODO opt?
			// script.add(TCLEnum.OPT);
			script.add(TCLEnum.PLACE);
			script.add(TCLEnum.ROUTE);
		}
		script.add(TCLEnum.WRITE_DCP);
		script.add(TCLEnum.WRITE_EDIF);
		// script.run();

		int ret = script.run(false);
		if (ret != 0) {
			TCLScript script2 = new TCLScript(input_dcp, output_dcp, options, tcl_script_file);
			if (directive.isSubBuilder())
				insertEncryptedModules(input_dcp, script, directive.getSubBuilder().getDirectives(), args);
			else
				insertEncryptedModules(input_dcp, script, Arrays.asList(directive), args);

			if (!top.getCellInsts().isEmpty()) {
				if (directive.getPBlockStr() != null) {
					String pblock_name = "[get_property TOP [current_design ]]" + "_pblock";
					script2.addCustomCmd("create_pblock " + pblock_name);
					script2.addCustomCmd(
							"resize_pblock -add {" + directive.getPBlockStr() + "} [get_pblocks " + pblock_name + "]");
					script2.addCustomCmd("add_cells_to_pblock [get_pblocks " + pblock_name + "] [get_cells]");
					script2.addCustomCmd("set_property CONTAIN_ROUTING 1 [get_pblocks " + pblock_name + "]");
					script2.addCustomCmd("set_property SNAPPING_MODE ROUTING [get_pblocks " + pblock_name + "]");
				}
				script2.add(TCLEnum.PLACE);
				script2.add(TCLEnum.ROUTE);
			}
			script2.add(TCLEnum.WRITE_DCP);
			script2.add(TCLEnum.WRITE_EDIF);
			script2.run();
		}

		return output_dcp;
	}

	/**
	 * Find the module name of a given directive.
	 * 
	 * @param directive Instructions for creating an instance of a module.
	 * @param args      Arguments from command line.
	 * @return Name of the module specified in directive.
	 */
	public static String getModuleName(Directive directive, ArgsContainer args) {
		String module_name = null;
		if (directive.isSubBuilder())
			module_name = directive.getSubBuilder().getHeader().getModuleName();

		if (module_name == null) {
			if (directive.getDCP() == null) {
				if (directive.getInstName() == null)
					printIfVerbose(
							"Can't find module in cache. Neither dcp nor module_name are specified. No instance name given either.",
							args.verbose());
				else
					printIfVerbose("Can't find module in cache for module instance '" + directive.getInstName()
							+ "'. Neither dcp nor module_name are specified.", args.verbose());
				return null;
			}
			module_name = FileTools.removeFileExtension(directive.getDCP().getName());
		}
		return module_name;
	}

	private Point getAnchorTarget(Directive directive) {
		if (directive == null)
			return null;

		if (directive.getPBlockStr() != null) {
			PBlock block = new PBlock(device, directive.getPBlockStr());
			Tile t = block.getBottomLeftTile();
			return new Point(t.getColumn(), t.getRow());
		}
		if (!directive.isSubBuilder())
			return null;

		Point p = new Point(Integer.MAX_VALUE, Integer.MAX_VALUE);
		for (Directive dir : directive.getSubBuilder().getDirectives()) {
			Point p_sub = getAnchorTarget(dir);
			if (p_sub == null)
				continue;

			if (p_sub.x < p.x)
				p.x = p_sub.x;
			if (p_sub.y < p.y)
				p.y = p_sub.y;
		}
		if (p.x == Integer.MAX_VALUE || p.y == Integer.MAX_VALUE)
			return null;
		return p;
	}

	/**
	 * Inserts the ooc placed and routed module to the design.
	 * 
	 * @param mod       Module to be merged into design.
	 * @param directive Merge directive including whether to open handplacer.
	 * @return An anchored instance of Module mod. Null if failed.
	 */
	private ModuleInst insertOOC(Module mod, Directive directive) {
		if (mod == null)
			return null;

		ModuleInst mi = null;
		// PBlock block = new PBlock(device, directive.getPBlockStr());

		// TODO better auto placer

		Site anchor_site = null;
		// Site tmp_site = null;
		// for (int x = 0; x < 1000; x++) {
		// if (anchor_site != null)
		// break;
		// for (int y = 0; y < 2000; y++) {
		// try {
		// String site_str = "SLICE_X" + x + "Y" + y;
		// tmp_site = device.getSite(site_str);
		// if (mod.isValidPlacement(tmp_site, device, design)) {
		// anchor_site = tmp_site;
		// break;
		// }
		// } catch (NullPointerException npe) {
		// }
		// }
		// }
		// if (anchor_site == null || !mod.isValidPlacement(anchor_site, device,
		// design)) {
		// List<Site> valid_placements = mi.getAllValidPlacements();
		// for (Site s : valid_placements) {
		// if (mod.isValidPlacement(s, device, design)) {
		// anchor_site = s;
		// break;
		// }
		// }

		// }

		// BlockPlacer2 placer = new BlockPlacer2();
		// placer.placeModuleNear(...);
		// placer.placeDesign(design, debugFlow);

		String mi_name = directive.getInstName();
		// TODO set mi name if duplicate
		if (mi_name == null)
			mi_name = mod.getName() + "_i";

		mi = design.createModuleInst(mi_name, mod);
		mi.getCellInst().setCellType(mod.getNetlist().getTopCell());

		List<Site> valid_placements = new ArrayList<Site>();
		// TODO verify that storing and loading placements succeeds properly
		// loadValidSitesFromMeta(valid_placements, directive, device);
		if (valid_placements.size() <= 0) {
			printIfVerbose("No valid placements found in metadata. Finding all valid placements for module '"
					+ mod.getName() + "'", directive.getHeader().isVerbose());
			valid_placements = mi.getAllValidPlacements();
			// writeValidSitesToMeta(valid_placements, directive);
		}

		int left_pblock_row = 0, bot_pblock_col = 0;
		// if (directive.getPBlockStr() != null) {
		// PBlock block = new PBlock(device, directive.getPBlockStr());
		// int count = 0;
		// for (Tile t : block.getAllTiles()) {
		// bot_pblock_col += t.getColumn();
		// left_pblock_row += t.getRow();
		// count++;
		// }
		// if (count > 0) {
		// bot_pblock_col /= count;
		// left_pblock_row /= count;
		// }
		// } else if (directive.isSubBuilder()) {
		// int count = 0;
		// for (Directive dir : directive.getSubBuilder().getDirectives()) {
		// if (dir.getPBlockStr() != null) {
		// PBlock block = new PBlock(device, dir.getPBlockStr());
		// for (Tile t : block.getAllTiles()) {
		// bot_pblock_col += t.getColumn();
		// left_pblock_row += t.getRow();
		// count++;
		// }
		// }
		// }
		// if (count > 0) {
		// bot_pblock_col /= count;
		// left_pblock_row /= count;
		// }
		// }
		Point p = getAnchorTarget(directive);
		if (p != null) {
			bot_pblock_col = p.x;
			left_pblock_row = p.y;
		}

		int min_manhattan_distance = Integer.MAX_VALUE;
		for (Site s : valid_placements) {
			Tile t = s.getTile();
			int distance = java.lang.Math.abs(t.getRow() - left_pblock_row)
					+ java.lang.Math.abs(t.getColumn() - bot_pblock_col);
			if (mod.isValidPlacement(s, device, design) && distance < min_manhattan_distance) {
				anchor_site = s;
				min_manhattan_distance = distance;
			}
		}

		if (anchor_site != null) {
			printIfVerbose("Placing module instance '" + mi.getName() + "' at '" + anchor_site.getName() + "'.",
					directive.getHeader().isVerbose());
			mi.place(anchor_site);
		} else
			MessageGenerator.briefError("\nCould not find anywhere to place module instance '" + mi_name + "'.\n");

		// if (!mi.placeMINearTile(block.getBottomLeftTile(), SiteTypeEnum.SLICEL)
		// && !mi.placeMINearTile(block.getBottomLeftTile(), SiteTypeEnum.SLICEM)) {

		// If can't find a valid site using placeMINearTile(), pick a site to place
		// module instance.
		// Site anchor_site = null;
		// Set<Site> pblock_sites = block.getAllSites(null);
		// for (Site site : pblock_sites) {
		// if (mod.isValidPlacement(site, device, design)) {
		// anchor_site = site;
		// break;
		// }
		// }
		// List<Site> all_valid_placements = (anchor_site != null) ? new ArrayList<>()
		// : mod.calculateAllValidPlacements(device);
		// for (Site site : all_valid_placements) {
		// if (mod.isValidPlacement(site, device, design)) {
		// anchor_site = site;
		// break;
		// }
		// }
		// mi.place(anchor_site);
		// }

		// TODO only do this if anchor_site != null ?
		if (directive.isHandPlacer() && (anchor_site != null))
			HandPlacer.openDesign(design);
		return mi;
	}

	/**
	 * Read in the sites that have been stored as valid sites in the metadata file.
	 * 
	 * @param valid_placements Append valid placements retrieved from metadata to
	 *                         this list.
	 * @param directive        The module to which the metadata belongs.
	 * @param device           Device for the design. Used to parse strings into
	 *                         Sites.
	 */
	public static void loadValidSitesFromMeta(List<Site> valid_placements, Directive directive, Device device) {
		File impl_dir = XDCWriter.findOrMakeCacheDir(directive, null);
		File metadata = new File(impl_dir, DependancyMeta.META_FILENAME);

		if (!metadata.isFile()) {
			printIfVerbose(
					"Can't load valid sites from metadata file '" + metadata.getAbsolutePath() + "'. It doesn't exist.",
					directive.getHeader().isVerbose());
			return;
		}
		printIfVerbose("Loading valid sites from metadata.", directive.getHeader().isVerbose());

		DependancyMeta meta = new DependancyMeta(metadata, directive.getHeader().isVerbose());
		for (String site_str : meta.getValidSiteStrs()) {
			Site s = device.getSite(site_str);
			if (s != null)
				valid_placements.add(s);
		}
	}

	/**
	 * Append a list of Sites to a metadata file.
	 * <p>
	 * To do this, the metadata file is read in, then overwritten with the loaded
	 * results plus the valid_placements.
	 * 
	 * @param valid_placements Write this list of placements to the metadata.
	 * @param directive        The module to which the metadata belongs.
	 */
	public static void writeValidSitesToMeta(List<Site> valid_placements, Directive directive) {
		File impl_dir = XDCWriter.findOrMakeCacheDir(directive, null);
		File metadata = new File(impl_dir, DependancyMeta.META_FILENAME);

		printIfVerbose("Writing valid sites to metadata.", directive.getHeader().isVerbose());

		DependancyMeta meta = new DependancyMeta(metadata, directive.getHeader().isVerbose());
		ArrayDeque<String> site_strs = new ArrayDeque<>();
		for (Site s : valid_placements)
			site_strs.offer(s.getName());
		meta.setValidSites(site_strs);
		meta.writeMeta(impl_dir, null);
	}

	/**
	 * Get named EDIFNet if it exists in parent_cell else create the net in
	 * parent_cell.
	 * 
	 * @param name        Search for an EDIFNet of this name.
	 * @param parent_cell Search nets in this EDIFCell.
	 * @return An EDIFNet with the given name and parent cell.
	 */
	private EDIFNet getOrMakeEDIFNet(String name, EDIFCell parent_cell) {
		EDIFNet net = parent_cell.getNet(name);
		if (net != null)
			return net;
		return new EDIFNet(name, parent_cell);
	}

	/**
	 * Use synth_netlist loaded in
	 * {@link #init(Design, DirectiveHeader, ArgsContainer) init} as template to
	 * connect module instances together and to top level ports.
	 * 
	 * @param args Arguments from the command line.
	 */
	private void connectAll(ArgsContainer args) {
		if (synth_netlist == null) {
			printIfVerbose("\nNo top level synth loaded. Can't make any connections.", args.verbose());
			return;
		}

		EDIFCell top = design.getNetlist().getTopCell();
		EDIFCell synth_top = synth_netlist.getCell(top.getName());

		for (Entry<String, EDIFCellInst> e : wire_cells.entrySet()) {
			String ci_name = e.getKey();
			EDIFCellInst synth_ci = e.getValue();

			if (synth_ci == null) {
				printIfVerbose("Couldn't find wire cell instance '" + ci_name + "'.", args.verbose());
				continue;
			}
			String cell_name = synth_ci.getCellName();
			EDIFNetlist edifnetlist = new EDIFNetlist(synth_ci.getCellName());
			edifnetlist.migrateCellAndSubCells(synth_ci.getCellType());
			EDIFCell cell = edifnetlist.getCell(cell_name);
			EDIFDesign edifdsgn = new EDIFDesign(cell_name);
			edifdsgn.setTopCell(cell);
			edifnetlist.setDesign(edifdsgn);
			Design d = new Design(ci_name, design.getPartName());
			d.setNetlist(edifnetlist);

			// EDIFCell sub_top = d.getNetlist().getTopCell();

			// for (EDIFCellInst synth_inst : sub_synth_top.getCellInsts()) {
			// EDIFCell synth_cell = synth_inst.getCellType();
			// // design.getNetlist().migrateCellAndSubCells(synth_cell);
			// myMigrateCellAndSubCells(synth_cell);
			// if (synth_cell.isPrimitive())
			// new EDIFCellInst(synth_inst.getName(), synth_cell, sub_top);
			// }

			// for (EDIFPort synth_port : sub_synth_top.getPorts())
			// if (sub_top.getPort(synth_port.getBusName()) == null)
			// sub_top.createPort(synth_port.getName(), synth_port.getDirection(),
			// synth_port.getWidth());

			Module mod = new Module(d);
			ModuleInst mi = design.createModuleInst(ci_name, mod);
			mi.getCellInst().setCellType(mod.getNetlist().getTopCell());
			wire_cells.remove(ci_name);
		}

		for (EDIFNet synth_net : synth_top.getNets()) {
			for (EDIFPortInst synth_pi : synth_net.getPortInsts()) {
				if (synth_pi.isTopLevelPort()) {
					EDIFPort port = top.getPort(synth_pi.getPort().getBusName());
					if (port == null)
						continue;
					getOrMakeEDIFNet(synth_net.getName(), top).createPortInst(port, synth_pi.getIndex());
				} else {
					EDIFCellInst ci = top.getCellInst(synth_pi.getCellInst().getName());
					if (ci == null)
						continue;
					EDIFPort port = ci.getCellType().getPort(synth_pi.getPort().getBusName());
					if (port == null)
						continue;
					getOrMakeEDIFNet(synth_net.getName(), top).createPortInst(port, synth_pi.getIndex(), ci);
				}
			}
		}
	}

	/**
	 * Places and routes a dcp and writes the routed design back to the same file
	 * location using vivado tcl.
	 * <p>
	 * Calls
	 * {@link #insertEncryptedModules(TCLScript, DirectiveBuilder, ArgsContainer)
	 * insertEncryptedModules}.
	 * 
	 * @param inout_file File where input dcp is located and where final design will
	 *                   be written.
	 * @param head       Data common to the sibling directives that were merged to
	 *                   build this design.
	 * @param args       Arguments from the command line.
	 */
	public void placeAndRoute(File inout_file, DirectiveBuilder directive_builder, ArgsContainer args) {
		DirectiveHeader head = directive_builder.getHeader();

		String options = (args == null) ? "f" : args.options("f");
		String inout_dcp = inout_file.getAbsolutePath();
		String tcl_script_file = head.getIII().getAbsolutePath() + "/place_route_step.tcl";

		TCLScript script = new TCLScript(inout_dcp, inout_dcp, options, tcl_script_file);
		insertEncryptedModules(inout_dcp, script, directive_builder.getDirectives(), args);

		File cache_impl_dir = inout_file.getParentFile();
		File src_constrs = new File(cache_impl_dir, XDCWriter.CONSTRAINTS_FILE);
		if (!head.isBufferedInputs())
			script.add(TCLEnum.READ_XDC, args.options(), "-unmanaged -mode out_of_context",
					src_constrs.getAbsolutePath());
		else
			script.add(TCLEnum.READ_XDC, args.options(), "-unmanaged", src_constrs.getAbsolutePath());

		// todo opt?
		// script.add(TCLEnum.OPT);
		script.add(TCLEnum.PLACE);
		script.add(TCLEnum.ROUTE);
		script.add(TCLEnum.WRITE_DCP);
		script.add(TCLEnum.WRITE_EDIF);
		// script.run();

		int ret = script.run(false);
		if (ret != 0) {
			TCLScript script2 = new TCLScript(inout_dcp, inout_dcp, options, tcl_script_file);
			insertEncryptedModules(inout_dcp, script, directive_builder.getDirectives(), args);
			script2.add(TCLEnum.PLACE);
			script2.add(TCLEnum.ROUTE);
			script2.add(TCLEnum.WRITE_DCP);
			script2.add(TCLEnum.WRITE_EDIF);
			script2.run();
		}
	}

	private static void printIfVerbose(String msg, boolean verbose) {
		if (verbose)
			MessageGenerator.briefMessage(msg);
	}

	public void setFinalDCP(File out_dcp) {
		final_dcp = out_dcp;
	}

	public File getFinalDCP() {
		return final_dcp;
	}

	public void writeCheckpoint(File dcp_file) {
		dcp_file.getParentFile().mkdirs();
		if (design != null) {
			// TODO lock Placement and Routing
			// lockPlacement(design);
			// lockRouting(design);

			design.writeCheckpoint(dcp_file.getAbsolutePath());
		}
	}

	public void writeCheckpoint(String filename) {
		if (filename != null)
			writeCheckpoint(new File(filename));
	}

	/**
	 * Lock placement of a design.
	 * <p>
	 * From {@link com.xilinx.rapidwright.debug.ILAInserter#main(String[])}
	 * 
	 * @param d Design whose placement is to be locked.
	 */
	public static void lockPlacement(Design d) {
		for (Cell c : d.getCells()) {
			c.setBELFixed(true);
			c.setSiteFixed(true);
		}
	}

	/**
	 * Lock routing of a design.
	 * <p>
	 * From {@link com.xilinx.rapidwright.debug.ILAInserter#main(String[])}
	 * 
	 * @param d Design whose routing is to be locked.
	 */
	public static void lockRouting(Design d) {
		for (Net n : d.getNets()) {
			for (PIP p : n.getPIPs()) {
				p.setIsPIPFixed(true);
			}
		}
	}

	/**
	 * Transform pblock path into a path that is acceptable to vivado but is still
	 * human readable as well.
	 * 
	 * @param pblock PBlock string to transform.
	 * @return A human and vivado readable representation of pblock.
	 */
	public static String getPblockPath(String pblock) {
		return pblock == null ? "" : pblock.replaceAll("_", "").replaceAll(":", "_").replaceAll(" ", "__");
	}

	/**
	 * Merge encrypted modules back into the design.
	 * <p>
	 * Looks for encrypted modules in every cached submodule by checking for files
	 * of name '.edn'. Only checks direct descendants, because the encrypted edifs
	 * will be rewritten for each 'write_edif'. If found, it tries to load these
	 * encrypted files back into the vivado.
	 * 
	 * @param inout_dcp  File to load encrypted modules into.
	 * @param directives Load encrypted edifs from these modules
	 */
	public static void insertEncryptedModules(File inout_dcp, Collection<Directive> directives, ArgsContainer args) {
		if (directives.size() <= 0)
			return;
		DirectiveHeader head = directives.iterator().next().getHeader();

		String tcl_script_name = new File(head.getIII(), "load_encrypted_edifs.tcl").getAbsolutePath();
		String inout_filename = inout_dcp.getAbsolutePath();
		String options = (args == null) ? "f" : args.options("f");
		TCLScript script = new TCLScript(inout_filename, inout_filename, options, tcl_script_name);

		insertEncryptedModules(inout_dcp.getAbsolutePath(), script, directives, args);
		script.add(TCLEnum.WRITE_DCP);
		script.add(TCLEnum.WRITE_EDIF);
		script.run();
	}

	/**
	 * Adds the commands to Merge encrypted modules back into the design into the
	 * given script.
	 * <p>
	 * Looks for encrypted modules in every cached submodule by checking for files
	 * of name '.edn'. Only checks direct descendants, because the encrypted edifs
	 * will be rewritten for each 'write_edif'. If found, it tries to load these
	 * encrypted files back into the vivado.
	 * 
	 * @param script     Tcl script to add commands to.
	 * @param directives Load encrypted edifs from these modules
	 * @param args       Arguments from command line.
	 */
	public static void insertEncryptedModules(String input_dcp, TCLScript script, Collection<Directive> directives,
			ArgsContainer args) {
		if (directives.size() <= 0)
			return;
		DirectiveHeader head = directives.iterator().next().getHeader();

		Design d = DesignUtils.safeReadCheckpoint(input_dcp, head.isVerbose(), head.getIII());
		// for each cell with no contents (black box when opened in Vivado), try to find
		// an encrypted edif with the same cell name.
		for (Directive dir : directives) {
			for (EDIFHierCellInst hci : d.getNetlist().findCellInsts(dir.getInstName() + "*")) {
				EDIFCell cell = hci.getInst().getCellType();
				if (cell.hasContents() || cell.isPrimitive())
					continue;

				File cache_impl_dir = XDCWriter.findOrMakeCacheDir(dir, null);
				File edn_file = new File(cache_impl_dir, cell.getName() + ".edn");
				// File[] possible_edns =
				// cache_impl_dir.listFiles(FileTools.getFilenameFilter(cell.getName() +
				// ".edn"));
				if (!edn_file.isFile()) {
					printIfVerbose(
							"Cell '" + hci.getFullHierarchicalInstName() + "' has no contents since encrypted edif '"
									+ edn_file.getAbsolutePath() + "' was not found.",
							head.isVerbose());
					continue;
				}
				script.addCustomCmd("update_design -cells [get_cells *" + hci.getFullHierarchicalInstName()
						+ "] -from_file " + edn_file.getAbsolutePath());
				// File src_constrs = new File(cache_impl_dir, XDCWriter.CONSTRAINTS_FILE);
				// if (!(dir.isSubBuilder() &&
				// dir.getSubBuilder().getHeader().isBufferedInputs()))
				// script.add(TCLEnum.READ_XDC, args.options(), "-unmanaged -mode
				// out_of_context",
				// src_constrs.getAbsolutePath());
				// else
				// script.add(TCLEnum.READ_XDC, args.options(), "-unmanaged",
				// src_constrs.getAbsolutePath());
			}
		}
	}

	/**
	 * Migrates cell and all it's descendant cells from the library of some other
	 * design to the library of this design's netlist.
	 * <p>
	 * Modified from {@link EDIFNetlist#migrateCellAndSubCells(EDIFCell)} This
	 * should do the same except that it replaces blackboxes in the work library.
	 * 
	 * @param cell Cell from other design's netlist to merge (along with its
	 *             subcells) into this design's netlist.
	 */
	private void myMigrateCellAndSubCells(EDIFCell cell) {
		EDIFNetlist netlist = design.getNetlist();
		Queue<EDIFCell> cells = new LinkedList<>();
		cells.add(cell);
		while (!cells.isEmpty()) {
			EDIFCell curr = cells.poll();
			EDIFLibrary destLib = netlist.getLibrary(curr.getLibrary().getName());
			if (destLib == null) {
				if (curr.getLibrary().getName().equals(EDIFTools.EDIF_LIBRARY_HDI_PRIMITIVES_NAME)) {
					destLib = netlist.getHDIPrimitivesLibrary();
				} else {
					destLib = netlist.getWorkLibrary();
				}
			}

			if (!destLib.containsCell(curr)) {
				destLib.addCell(curr);
			} else if (curr.hasContents()) {
				destLib.removeCell(curr.getName());
				destLib.addCell(curr);
			}

			for (EDIFCellInst inst : curr.getCellInsts()) {
				cells.add(inst.getCellType());
			}
		}
	}

	/**
	 * DEPRICATED
	 * 
	 * Makes as many connections as it can find between this module instance and the
	 * rest of the design.
	 * 
	 * @param mi    Module instance to make connections to/from.
	 * @param conns Possible connections to make.
	 */
	@Deprecated
	@SuppressWarnings("unused")
	private void connectAll(ModuleInst mi, Connections conns) {
		// TODO fix connectAll()
		// ! either rearrange Connections to search by module inst or loop through every
		// connection
		if (conns == null)
			return;

		EDIFCell top = design.getNetlist().getTopCell();
		final String CLK = "clk_100MHz";
		final String RST = "reset_rtl_0";
		if (top.getNet(CLK) == null) {
			// Create clk and rst
			design.setAutoIOBuffers(false);

			EDIFNet clk = top.addNet(new EDIFNet(CLK, top));
			clk.createPortInst(top.createPort(CLK, EDIFDirection.INPUT, 1));
			top.createPort(RST, EDIFDirection.INPUT, 1);
			top.addNet(new EDIFNet(RST, top));

			// ! TEMP GROUNDING
			EDIFNet gnd_net = EDIFTools.getStaticNet(NetType.GND, top, design.getNetlist());

			gnd_net.createPortInst(top.createPort("uart_rtl_0_rxd", EDIFDirection.INPUT, 1));
			EDIFPort port = top.createPort("gpio_io_i_0[1:0]", EDIFDirection.INPUT, 2);
			gnd_net.createPortInst(port, 0);
			gnd_net.createPortInst(port, 1);
			gnd_net.createPortInst(top.createPort("uart_rtl_0_txd", EDIFDirection.OUTPUT, 1));
			port = top.createPort("gpio_io_o_0[1:0]", EDIFDirection.OUTPUT, 2);
			gnd_net.createPortInst(port, 0);
			gnd_net.createPortInst(port, 1);

		}
		EDIFNet clk = top.getNet(CLK);
		// clk.createPortInst(CLK, mi.getCellInst());
		clk.createPortInst("s_axi_aclk", mi.getCellInst());
		mi.connect("s_axi_aresetn", RST);

		for (ModuleInst modi : design.getModuleInsts()) {
			if (mi == modi)
				continue;

		}
		// if (mi != null)
		// return;

		// EDIFCellInst mi_cell_i =
		// design.getNetlist().getCellInstFromHierName(mi.getName());
		// EDIFCell top_cell = design.getNetlist().getTopCell();

		// for (EDIFPort mi_port : mi_cell_i.getCellPorts()) {
		// MyPort sourcep = new MyPort(mi_port.getBusName());
		// Set<MyPort> sinks = conns.getSinks(mi_cell_i.getName(),
		// mi_port.getBusName());
		// if (sinks == null)
		// continue;

		// for (MyPort sinkp : sinks) {
		// EDIFCellInst other_cell_i =
		// design.getNetlist().getCellInstFromHierName(sinkp.getModuleInst());
		// EDIFPort other_port = other_cell_i.getPort(sinkp.getPortBusName());

		// edifConnect(top_cell, mi_cell_i, mi_port, other_cell_i, other_port);
		// }
		// }
	}

	/**
	 * DEPRICATED
	 * 
	 * Connects two ports (single bit or bus). Adds port instances and nets as
	 * needed. If both have port instances on different nets, moves sink_port
	 * instance to source port net.
	 * 
	 * If one is a bus but not the other, the single bit is connected to bus[0]. If
	 * differently alligned bus indicies, connects those indicies that overlap. Ex
	 * port1[6:0] port2[4:3] connects 4:3. Ex port1[5:2] port2[4:0] connects 4:2.
	 */
	@Deprecated
	@SuppressWarnings("unused")
	private void edifConnect(EDIFCell parent, EDIFCellInst source_ci, EDIFPort source_port, EDIFCellInst sink_ci,
			EDIFPort sink_port) {
		if (source_port.isBus() && sink_port.isBus()) {
			int top = (source_port.getLeft() < sink_port.getLeft()) ? source_port.getLeft() : sink_port.getLeft();
			int bot = (source_port.getRight() > sink_port.getRight()) ? source_port.getRight() : sink_port.getRight();
			for (int i = bot; i <= top; i++)
				edifConnect(parent, source_ci, source_port, source_port.getBusName() + "[" + i + "]", sink_ci,
						sink_port, sink_port.getBusName() + "[" + i + "]");
		} else if (source_port.isBus())
			edifConnect(parent, source_ci, source_port, source_port.getBusName() + "[0]", sink_ci, sink_port,
					sink_port.getName());
		else if (sink_port.isBus())
			edifConnect(parent, source_ci, source_port, source_port.getName(), sink_ci, sink_port,
					sink_port.getBusName() + "[0]");
		else
			edifConnect(parent, source_ci, source_port, source_port.getName(), sink_ci, sink_port, sink_port.getName());
	}

	/**
	 * DEPRICATED
	 * 
	 * Connects two single bit ports/one bit on bus. Adds port instances and nets as
	 * needed. If both have port instances on different nets, moves sink_port
	 * instance to source port net.
	 */
	@Deprecated
	// @SuppressWarnings("unused")
	private void edifConnect(EDIFCell parent, EDIFCellInst source_ci, EDIFPort source_port, String source_pi_name,
			EDIFCellInst sink_ci, EDIFPort sink_port, String sink_pi_name) {

		EDIFPortInst source_pi = source_ci.getPortInst(source_pi_name);
		EDIFPortInst sink_pi = sink_ci.getPortInst(sink_pi_name);
		if (source_pi == null && sink_pi == null) {
			EDIFNet net = new EDIFNet(source_port.getName(), parent);
			net.createPortInst(source_port, source_ci);
			net.createPortInst(sink_port, sink_ci);
		} else if (source_pi == null) {
			EDIFNet net = sink_pi.getNet();
			net.createPortInst(source_port, source_ci);
		} else if (sink_pi == null) {
			EDIFNet net = source_pi.getNet();
			net.createPortInst(sink_port, sink_ci);
		} else if (source_pi.getNet() != sink_pi.getNet()) {
			EDIFNet prev_sink_net = sink_pi.getNet();
			prev_sink_net.removePortInst(sink_pi);
			source_pi.getNet().addPortInst(sink_pi);
		}
		// else already connected to same net
	}
}
