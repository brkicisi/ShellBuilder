package worker;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

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
import com.xilinx.rapidwright.edif.EDIFCell;
import com.xilinx.rapidwright.edif.EDIFCellInst;
import com.xilinx.rapidwright.edif.EDIFDesign;
import com.xilinx.rapidwright.edif.EDIFDirection;
import com.xilinx.rapidwright.edif.EDIFLibrary;
import com.xilinx.rapidwright.edif.EDIFNet;
import com.xilinx.rapidwright.edif.EDIFNetlist;
import com.xilinx.rapidwright.edif.EDIFPort;
import com.xilinx.rapidwright.edif.EDIFPortInst;
import com.xilinx.rapidwright.edif.EDIFTools;
import com.xilinx.rapidwright.placer.handplacer.HandPlacer;
import com.xilinx.rapidwright.util.FileTools;
import com.xilinx.rapidwright.util.MessageGenerator;

import parser.ArgsContainer;
import tcl.TCLEnum;
import tcl.TCLScript;
import directive.*;
import util.DesignUtils;

public class Merger {
	private Design design = null;
	private Device device = null;
	private EDIFNetlist synth_netlist = null;
	private Set<String> wire_cells = new HashSet<>();
	private File final_dcp = null;

	public static final String MODULE_CACHE = "moduleCache";

	public Merger() {
	}

	public Merger(Design design, DirectiveHeader head, ArgsContainer args) {
		init(design, head, args);
	}

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
	 * Top level functions to fetch dcp (from Directive:getDCP() or from
	 * .iii/cache), insert it into this.design and connect as many connections as it
	 * can find using top level synthesized design from header.
	 * 
	 * @param directive Merge directive including the dcp to be merged.
	 * @param args      Command line args.
	 */
	public void merge(Directive directive, ArgsContainer args) {
		// String module_name = getModuleName(directive, args);
		// if (module_name == null)
		// MessageGenerator.briefErrorAndExit("Null module name");
		// String pblock = directive.getPBlockStr();
		// if (pblock == null)
		// MessageGenerator.briefErrorAndExit("No pblock specified for '" + module_name
		// + "'.");
		// // create cache folder in iii
		// File cache_impl_dir = new File(directive.getIII(),
		// MODULE_CACHE + "/" + module_name + "/" + pblock.replace(' ', '+'));
		// if (!cache_impl_dir.isDirectory())
		// cache_impl_dir.mkdirs();
		// DependancyMeta.writeMeta(cache_impl_dir, directive, args);

		Module mod = fetchAndPrepModule(directive, args);
		if (design == null) {
			if (directive.getHeader().getModuleName() != null)
				init(new Design(directive.getHeader().getModuleName(), mod.getDevice().getDeviceName()),
						directive.getHeader(), args);
			else
				init(new Design("top", mod.getDevice().getDeviceName()), directive.getHeader(), args);
		}
		insertOOC(mod, directive);
		connectAll(directive.getHeader(), args);
	}

	/**
	 * Check if module exists in cache. If it exists, is more recent and pblock
	 * matches (or no pblock specified) then use cached dcp.
	 * 
	 * Else fetch dcp from Directive:getDCP(). Place and Route dcp ooc in pblock and
	 * save to cache.
	 * 
	 * @param directive Merge directive including dcp and pblock.
	 * @param args      Command line args.
	 * @return Placed and routed module.
	 */
	private Module fetchAndPrepModule(Directive directive, ArgsContainer args) {
		Module mod = null;
		boolean verbose = (args == null) ? false : args.verbose();

		if (directive.getDCP() == null) {
			if (directive.isOnlyWires()) {
				wire_cells.add(directive.getInstName());
				return null;
			} else
				MessageGenerator.briefErrorAndExit("\nNo dcp in merge directive.\nExiting.");
		} else if (!directive.getDCP().exists())
			MessageGenerator.briefErrorAndExit("\nCould not find dcp '" + directive.getDCP().getAbsolutePath()
					+ "' specified in merge directive.\nExiting.");

		boolean was_already_cached = false;
		String cached_dcp_str = null;

		// Try to find in cache. If is in the cache and is newer than all it's
		// dependancies, then use it.
		if (!directive.isRefresh()) {
			File cached_dcp = findModuleInCache(directive, args);
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
		// TODO pblock string may be null
		mod.setPBlock(directive.getPBlockStr());
		if (design == null) {
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

	public static File findModuleInCache(Directive directive, ArgsContainer args) {
		return findModuleInCache(directive, args, new DependancyMeta.DepSet());
	}

	public static File findModuleInCache(Directive directive, ArgsContainer args, DependancyMeta.DepSet visited) {
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
			impl_dir = new File(mod_dir, pblock.replace(' ', '+'));
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
		if (!new File(impl_dir, DependancyMeta.META_FILENAME).isFile()) {
			printIfVerbose("\nCan't find metadata for module '" + module_name + "' in cache.", args.verbose());
			return null;
		}

		// TODO findModuleInCache() checks for build
		// read metadata
		File meta_file = new File(impl_dir, DependancyMeta.META_FILENAME);
		DependancyMeta meta = new DependancyMeta(meta_file, args.verbose());
		DependancyMeta.DepSet dep_set = meta.initDepSet();
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
			for (Directive dir : directive.getSubBuilder().getDirectives()) {
				if (dir.isOnlyWires())
					continue;
				String sub_mod = getModuleName(dir, args);
				String sub_pblock = (dir.getPBlockStr() == null) ? "" : dir.getPBlockStr();
				if (visited.contains(sub_mod, sub_pblock))
					continue;
				visited.put(sub_mod, sub_pblock);

				String sub_pblock_plus = sub_pblock.replace(" ", "+");
				if (!dep_set.contains(sub_mod, sub_pblock_plus)) {
					printIfVerbose("\nModule '" + sub_mod + "' with pblock '" + sub_pblock
							+ "' was not found the dependancies of " + module_name + ".", args.verbose());
					return null;
				}
				dep_set2.remove(sub_mod, sub_pblock_plus);

				File sub = findModuleInCache(dir, args, visited);
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
			} else {
				dep_set2.remove(getModuleName(directive, args), "");
			}
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
			// TODO may not have specified pblock for non base designs
			// ? should pblock be forced to exist for base designs?
			printIfVerbose("\nNo pblock specified for '" + module_name + "'.", args.verbose());
			// return null;
		}
		File cache_impl_dir;
		if (directive.getPBlockStr() == null)
			cache_impl_dir = new File(directive.getIII(), MODULE_CACHE + "/" + module_name);
		else
			cache_impl_dir = new File(directive.getIII(),
					MODULE_CACHE + "/" + module_name + "/" + directive.getPBlockStr().replace(' ', '+'));
		// create cache folder in iii
		// File cache_impl_dir = new File(directive.getIII(),
		// MODULE_CACHE + "/" + module_name + "/" + directive.getPBlockStr().replace('
		// ', '+'));
		if (!cache_impl_dir.isDirectory())
			cache_impl_dir.mkdirs();

		// write metadata for cache
		DependancyMeta.writeMeta(cache_impl_dir, directive, args);

		String options = (args == null) ? "f" : args.options("f");
		String input_dcp = directive.getDCP().getAbsolutePath();
		String output_dcp = cache_impl_dir.getAbsolutePath() + "/" + module_name + ".dcp";
		String tcl_script_file = directive.getIII().getAbsolutePath() + "/pblock_place_route_step.tcl";
		TCLScript script = new TCLScript(input_dcp, output_dcp, options, tcl_script_file);

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
		script.add(TCLEnum.WRITE_DCP);
		script.add(TCLEnum.WRITE_EDIF);
		script.run();

		return output_dcp;
	}

	private static String getModuleName(Directive directive, ArgsContainer args) {
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

	/**
	 * Inserts the ooc placed and routed module to the design.
	 * 
	 * @param mod       Module to be merged into design.
	 * @param directive Merge directive including whether to open handplacer.
	 */
	private ModuleInst insertOOC(Module mod, Directive directive) {
		if (mod == null)
			return null;

		ModuleInst mi = null;
		// PBlock block = new PBlock(device, directive.getPBlockStr());

		// TODO better auto placer

		Site anchor_site = null, tmp_site = null;
		for (int x = 0; x < 100; x++) {
			if (anchor_site != null)
				break;
			for (int y = 0; y < 200; y++) {
				String site_str = "SLICE_X" + x + "Y" + y;
				tmp_site = device.getSite(site_str);
				if (mod.isValidPlacement(tmp_site, device, design)) {
					anchor_site = tmp_site;
					break;
				}
			}
		}

		if (anchor_site != null && mod.isValidPlacement(anchor_site, device, design)) {
			String mi_name = directive.getInstName();
			// TODO set mi name if duplicate
			if (mi_name == null)
				mi_name = mod.getName() + "_i";
			mi = design.createModuleInst(mi_name, mod);
			mi.getCellInst().setCellType(mod.getNetlist().getTopCell());
			MessageGenerator
					.briefMessage("Placing module instance '" + mi.getName() + "' at '" + anchor_site.getName() + "'.");
			mi.place(anchor_site);
		}
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

		if (directive.isHandPlacer())
			HandPlacer.openDesign(design);
		return mi;
	}

	private EDIFNet getOrMakeEDIFNet(String name, EDIFCell parentCell) {
		EDIFNet net = parentCell.getNet(name);
		if (net != null)
			return net;
		return new EDIFNet(name, parentCell);
	}

	private void connectAll(DirectiveHeader head, ArgsContainer args) {
		if (synth_netlist == null) {
			printIfVerbose("\nNo top level synth loaded. Can't make any connections.", args.verbose());
			return;
		}

		EDIFCell top = design.getNetlist().getTopCell();
		EDIFCell synth_top = synth_netlist.getCell(top.getName());

		for (String ci_name : wire_cells) {
			EDIFCellInst synth_ci = synth_top.getCellInst(ci_name);
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
			Design d = new Design(synth_ci.getName(), design.getPartName());
			d.setNetlist(edifnetlist);
			Module mod = new Module(d);
			ModuleInst mi = design.createModuleInst(synth_ci.getName(), mod);
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
	 * location.
	 */
	public void placeAndRoute(File inout_file, DirectiveHeader head, ArgsContainer args) {
		// TODO lock P&R
		lockPlacement(design);
		lockRouting(design);

		String options = (args == null) ? "f" : args.options("f");
		String inout_dcp = inout_file.getAbsolutePath();
		String tcl_script_file = head.getIII().getAbsolutePath() + "/pblock_place_route_step.tcl";

		TCLScript script = new TCLScript(inout_dcp, inout_dcp, options, tcl_script_file);
		// todo opt?
		// script.add(TCLEnum.OPT);
		script.add(TCLEnum.PLACE);
		script.add(TCLEnum.ROUTE);
		script.add(TCLEnum.WRITE_DCP);
		script.add(TCLEnum.WRITE_EDIF);
		script.run();
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
		if (design != null)
			design.writeCheckpoint(dcp_file.getAbsolutePath());
	}

	public void writeCheckpoint(String filename) {
		if (filename != null)
			writeCheckpoint(new File(filename));
	}

	public static void lockPlacement(Design d) {
		for (Cell c : d.getCells()) {
			c.setBELFixed(true);
			c.setSiteFixed(true);
		}
	}

	public static void lockRouting(Design d) {
		for (Net n : d.getNets()) {
			for (PIP p : n.getPIPs()) {
				p.setIsPIPFixed(true);
			}
		}
	}

	/**
	 * Modified from EDIFNetlist.migrate_cellAndSubCells()
	 * 
	 * This should do the same except that it replaces blackboxes.
	 * 
	 * @param cell Cell to merge (along with its subcells) into design.getNetlist().
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
	@SuppressWarnings("unused")
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
