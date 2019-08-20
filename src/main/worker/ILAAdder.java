package main.worker;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;

import com.xilinx.rapidwright.debug.ILAInserter;
import com.xilinx.rapidwright.design.Cell;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.design.SitePinInst;
import com.xilinx.rapidwright.design.blocks.PBlock;
import com.xilinx.rapidwright.device.BELPin;
import com.xilinx.rapidwright.edif.*;
import com.xilinx.rapidwright.router.Router;
import com.xilinx.rapidwright.util.FileTools;
import com.xilinx.rapidwright.util.MessageGenerator;

import main.directive.Directive;
import main.directive.DirectiveHeader;
import main.parser.ArgsContainer;
import main.tcl.TCLEnum;
import main.tcl.TCLScript;
import main.util.DesignUtils;
import main.util.FileUtils;
import main.util.StringUtils;

public class ILAAdder {

	/**
	 * Main workflow for adding an ILA (derived from AddILA#start), but hardcoded to a specific example.
	 * 
	 * @param input_dcp
	 * @param output_dcp
	 * @param head
	 * @param args
	 */
	public static void testAddILA(File input_dcp, File output_dcp, DirectiveHeader head, ArgsContainer args) {
		// TODO remove this test
		// Design d = DesignUtils.safeReadCheckpoint(input_dcp, head.isVerbose(),
		// head.getIII());
		// Merger merger = new Merger(d, head, args);
		// merger.testAddILA(output_dcp, head, args);
		// end test

		int probe_count = 6;
		int probe_depth = 4096;
		String clk_net = "clk_100MHz";
		Map<String, String> probe_map = new HashMap<>();
		probe_map.put("top/u_ila_0/probe0[0]", "design_2_i/gpio_switch/gpio_io_i[0]");
		probe_map.put("top/u_ila_0/probe0[1]", "design_2_i/gpio_switch/gpio_io_i[1]");
		probe_map.put("top/u_ila_0/probe0[2]", "design_2_i/microblaze_0_xlconcat/In0[0]");
		probe_map.put("top/u_ila_0/probe0[3]", "design_2_i/reset_rtl_0");
		probe_map.put("top/u_ila_0/probe0[4]", "design_2_i/reset_rtl_0");
		probe_map.put("top/u_ila_0/probe0[5]", "design_2_i/reset_rtl_0");

		TCLScript script = null;
		String intermediate_dcp = output_dcp.getAbsolutePath().replace(".dcp", "_inter.dcp");

		File edif = new File(intermediate_dcp.replace(".dcp", ".edf"));
		if (edif.exists()) {
			printIfVerbose("Deleting intermediate edif file '" + edif.getAbsolutePath() + "'.", head.isVerbose());
			FileTools.deleteFile(edif.getAbsolutePath());
		}

		String filename_bad_edif = intermediate_dcp.replace(".dcp", "_bad_edif.dcp");

		String[] ila_inserter_args = new String[6];
		ila_inserter_args[5] = head.fsys().getRoot(FileSys.FILE_ROOT.PWD) + "/.ila/ila.dcp";

		// String[] ila_inserter_args = new String[5];
		ila_inserter_args[0] = input_dcp.getAbsolutePath();
		ila_inserter_args[1] = filename_bad_edif;
		ila_inserter_args[2] = Integer.toString(probe_count);
		ila_inserter_args[3] = Integer.toString(probe_depth);
		ila_inserter_args[4] = clk_net;

		// Add ila and write intermediate checkpoint
		ILAInserter.main(ila_inserter_args);

		DesignUtils.fixEdifInDCPTop(filename_bad_edif, intermediate_dcp, "design_1_wrapper", head.isVerbose());

		// place design
		File tcl_script_file = new File(head.getIII(), "place_design.tcl");
		script = new TCLScript(intermediate_dcp, intermediate_dcp, "f", tcl_script_file.getAbsolutePath());

		String pblock_name = "top_pblock";
		String pblock_str = "";
		String debug_top = "top";
		if (pblock_name != null && pblock_str != null && debug_top != null) {
			script.addCustomCmd("create_pblock " + pblock_name);
			// script.addCustomCmd(
			// "resize_pblock -add {" + pblock_str + "} [get_pblocks " + pblock_name + "]");
			script.addCustomCmd("add_cells_to_pblock [get_pblocks " + pblock_name + "] [get_cells " + debug_top + "]");
			script.addCustomCmd("set_property CONTAIN_ROUTING 1 [get_pblocks " + pblock_name + "]");
			script.addCustomCmd("set_property SNAPPING_MODE ROUTING [get_pblocks " + pblock_name + "]");
			script.addCustomCmd("place_pblocks [get_pblocks " + pblock_name + "]");
		}
		script.add(TCLEnum.PLACE);
		script.add(TCLEnum.WRITE_DCP);
		script.add(TCLEnum.WRITE_EDIF);
		script.run();

		Design design = DesignUtils.safeReadCheckpoint(intermediate_dcp, head.isVerbose(), head.getIII());

		// route probes into design
		printIfVerbose("\nStarting to place probes into design.", head.isVerbose());
		my_updateProbeConnections(design, probe_map, null);
		printIfVerbose("Finished placing probes.\n", head.isVerbose());
		design.writeCheckpoint(output_dcp.getAbsolutePath());

		// write probes
		// String output_probes_file = output_dcp.getAbsolutePath().replace(".dcp",
		// "_probes.txt");
		// FileUtils.writeMapFile(m, filename, separator, header);
		// writeProbesFile(output_probes_file);

		tcl_script_file = new File(head.getIII(), "place_route_write.tcl");
		script = new TCLScript(output_dcp.getAbsolutePath(), output_dcp.getAbsolutePath(), "f",
				tcl_script_file.getAbsolutePath());
		script.add(TCLEnum.ROUTE); // route
		script.add(TCLEnum.WRITE_LTX); // write ltx, dcp, bitstream
		script.add(TCLEnum.WRITE_DCP);
		script.add(TCLEnum.WRITE_EDIF);
		script.add(TCLEnum.WRITE_BITSTREAM);
		script.run();

		printIfVerbose("\nFinal outputs written for ILAAdder.", head.isVerbose());
	}

	boolean verbose = false;
	String default_net = null;
	Map<String, String> probe_map = null;
	int probe_count = 0;
	int probe_depth = 4096;
	String clk_net = null;

	Design design = null;

	private static final int MAX_PROBE_COUNT = 4096;

	public ILAAdder(Design design, boolean verbose) {
		this.design = design;
		this.verbose = verbose;
	}

	/**
	 * Set probe_map using data from input_probes_file. This function populates
	 * probe_map and ensures that the entries in it cover probes numbering 0 to
	 * probe_map.size()-1.
	 */
	public void getProbesFromFile(File input_probes_file) {
		printIfVerbose("\nLoading probes from probes file '" + input_probes_file.getAbsolutePath() + "'.");
		Map<String, String> input_probes = FileUtils.readMapFile(input_probes_file.getAbsolutePath(), " ", true);
		String[] probe_str = { "top/u_ila_0/probe0[", "]" };
		LinkedList<String> bad_probe = new LinkedList<>();
		TreeSet<Integer> probe_nums = new TreeSet<>();
		probe_map = new TreeMap<>(StringUtils.getNaturalComparitor());

		// set default net
		setDefaultNet(input_probes.values());

		// add probe->net pairs that are valid (conform to probe_str) to probe_map and
		// the probe index to probe_nums
		// if probe is invalid, add net to bad_probes
		int p_num = -1;
		for (Entry<String, String> p : input_probes.entrySet()) {
			if (p.getKey().startsWith(probe_str[0]) && p.getKey().endsWith(probe_str[1])) {
				try {
					p_num = Integer.parseInt(
							p.getKey().substring(probe_str[0].length(), p.getKey().length() - probe_str[1].length()));
					probe_nums.add(p_num);
					probe_map.put(p.getKey(), p.getValue());
				} catch (NumberFormatException nfe) {
					bad_probe.add(p.getValue());
				}
			} else
				bad_probe.add(p.getValue());
		}
		/*
		 * for(contiguous i = 0 to end) continue if: there are more nets in bad_probe
		 * (as long as i does not exceed MAX_PROBE_COUNT) or there is a good probe with
		 * a higher index than i
		 */
		for (int i = 0; i < probe_nums.last() || (!bad_probe.isEmpty() && i < MAX_PROBE_COUNT); i++) {
			if (probe_nums.contains(i))
				continue;
			if (!bad_probe.isEmpty()) {
				String s = bad_probe.pollFirst();
				probe_map.put(probe_str[0] + i + probe_str[1], s);
			} else
				probe_map.put(probe_str[0] + i + probe_str[1], default_net);
		}
	}

	/**
	 * This function searches the top level user design for a reset (or rst) net if
	 * it can find one. This net will be used to connect unused probe wires since
	 * unconnected probe wires cause errors.
	 * 
	 * @param input_probes A collection of probe strings to use to find the name of
	 *                     the top instance of the user design.
	 */
	private void setDefaultNet(Collection<String> input_probes) {
		List<EDIFNet> reset_nets = new ArrayList<>();
		List<EDIFNet> rst_nets = new ArrayList<>();
		List<EDIFNet> other_nets = new ArrayList<>();
		String extra_net = null;
		String dsgn_inst = null;

		// Try to find a reset net to connect to the unconnected pins
		try {
			for (String hier_name : input_probes) {
				try {
					String[] path = hier_name.split("/");
					dsgn_inst = path[0];
					break;
				} catch (IndexOutOfBoundsException iob) {
					continue;
				}
			}
			if (dsgn_inst == null)
				throw new NullPointerException();

			for (EDIFNet n : design.getNetlist().getTopCell().getNets()) {
				String name = n.getName().toLowerCase();
				boolean buffered_name = n.getName().contains("BUF");

				if (name.contains("reset") && !buffered_name)
					reset_nets.add(n);
				else if (name.contains("rst") && !buffered_name)
					rst_nets.add(n);
				else if (!name.contains("clk") && !name.contains("clock") && !buffered_name) // not a clk net
					other_nets.add(n);
			}

			for (EDIFNet n : reset_nets) {
				default_net = dsgn_inst + "/" + n.getName();
				if (EDIFTools.getNet(design.getNetlist(), default_net) != null)
					return;
			}
			for (EDIFNet n : rst_nets) {
				default_net = dsgn_inst + "/" + n.getName();
				if (EDIFTools.getNet(design.getNetlist(), default_net) != null)
					return;
			}
			printIfVerbose("\nNo nets found in top module containing 'reset' or 'rst'.");
			printIfVerbose("Selecting net to connect unused probes to at random.");
			for (EDIFNet n : other_nets) {
				default_net = dsgn_inst + "/" + n.getName();
				if (EDIFTools.getNet(design.getNetlist(), default_net) != null)
					return;
			}
			printIfVerbose("\nFailed to find a net to which to connect unused probes.");
		} catch (NullPointerException npe) {
			printIfVerbose("\nFailed to find a net to which to connect unused probes.");
			default_net = null;
		}
	}

	/**
	 * Adds default connections to probe_map so that it's size is equal to p_count.
	 * 
	 * @param p_count Number of probes desired.
	 */
	private void padProbeMap(int p_count) {
		String[] probe_str = { "top/u_ila_0/probe0[", "]" };
		for (int i = probe_map.size(); i < p_count; i++)
			probe_map.put(probe_str[0] + i + probe_str[1], default_net);
	}

	private void getProbesFromDCP(Directive directive, boolean use_design) {
		printIfVerbose(
				"\nLoading probes from nets marked for debug in '" + directive.getDCP().getAbsolutePath() + "'.");

		List<String> debug_nets = null;
		if (use_design) {
			debug_nets = ILAInserter.getNetsMarkedForDebug(design);
		} else {
			Design d = DesignUtils.safeReadCheckpoint(directive.getDCP(), verbose, directive.getHeader().getIII());
			debug_nets = ILAInserter.getNetsMarkedForDebug(d);
		}
		setDefaultNet(debug_nets);
		probe_map = new HashMap<>();

		String[] probe_str = { "top/u_ila_0/probe0[", "]" };
		for (int i = 0; i < debug_nets.size() && i < MAX_PROBE_COUNT; i++)
			probe_map.put(probe_str[0] + i + probe_str[1], debug_nets.get(i));

		if (debug_nets.size() > MAX_PROBE_COUNT)
			MessageGenerator.briefMessage(
					"\nMore than " + MAX_PROBE_COUNT + " nets marked for debug. \n" + "Truncating list of debug nets.");
	}

	/**
	 * Load the probes map from input_probes_file if specified, else from nets
	 * marked for debug. Also sets the probe count from command line if given, else
	 * sets it same as size of probe map.
	 */
	private void loadProbes(Directive directive) {
		String probe_file = (input_probes_file == null) ? null : input_probes_file.getAbsolutePath();

		// if given probe file, load from it
		if (probe_file != null) {
			if (probe_file.endsWith(".dcp")) {
				File f_dcp = directive.getHeader().fsys().getExistingFile(probe_file, true);
				boolean use_design = (step == 0) && f_dcp.equals(no_ila_dcp_file)
						|| (step == 1) && f_dcp.equals(no_probes_dcp_file);
				getProbesFromDCP(f_dcp.getAbsolutePath(), use_design);

				if (probe_map.size() < 1) {
					StringBuilder sb = new StringBuilder();
					sb.append("No nets marked for debug in '");
					if (step == 0)
						sb.append(no_ila_dcp_file.getAbsolutePath());
					else if (step == 1)
						sb.append(no_probes_dcp_file.getAbsolutePath());
					else
						sb.append("????");
					sb.append("'.\nExiting.");
					MessageGenerator.briefErrorAndExit(sb.toString());
				}
			} else {
				getProbesFromFile();

				if (probe_map == null || probe_map.size() < 1)
					MessageGenerator.briefErrorAndExit(
							"No probes found in probe file '" + input_probes_file.getAbsolutePath() + "'.\nExiting.");
				else if (probe_map.size() > MAX_PROBE_COUNT)
					MessageGenerator.briefErrorAndExit("Too many probes (or too high index probes) "
							+ "found in probe file '" + input_probes_file.getAbsolutePath()
							+ "'.\nMaximum index of a probe is " + (MAX_PROBE_COUNT - 1) + " .\nExiting.");
			}
		}
		// load from nets marked for debug
		else {
			if (step == 0)
				getProbesFromDCP(no_ila_dcp_file.getAbsolutePath(), true);
			else if (step == 1)
				getProbesFromDCP(no_probes_dcp_file.getAbsolutePath(), true);
			else
				printIfVerbose("\n'step' was not 0 or 1.");

			if (probe_map.size() < 1) {
				StringBuilder sb = new StringBuilder();
				sb.append("No nets marked for debug in '");
				if (step == 0)
					sb.append(no_ila_dcp_file.getAbsolutePath());
				else if (step == 1)
					sb.append(no_probes_dcp_file.getAbsolutePath());
				else
					sb.append("????");
				sb.append("'.\nExiting.");
				MessageGenerator.briefErrorAndExit(sb.toString());
			}
		}

		ArrayList<String> list = arg_map.get("probe_count");
		if (list != null) {
			try {
				int np = Integer.parseInt(list.get(0));
				if (probe_map.size() > np) {
					printIfVerbose("\nIgnoring input probe_count of '" + np + "'.");
					printIfVerbose("It is smaller than probe_map size of '" + probe_map.size() + "'.");
					probe_count = probe_map.size();
				} else if (np > MAX_PROBE_COUNT) {
					printIfVerbose("\nIgnoring input probe_count of '" + np + "'.");
					printIfVerbose("It is larger than maximum number of probes (" + probe_map.size() + ").");
					probe_count = probe_map.size();
				} else {
					probe_count = np;
					padProbeMap(np);
				}
			} catch (NumberFormatException nfe) {
				printIfVerbose("Couldn't parse '" + list.get(0) + "' as an integer probe count.");
				probe_count = probe_map.size();
			}
		} else
			probe_count = probe_map.size();

		printIfVerbose("\nProbe count is " + probe_count + ".");

		if (step == 0)
			return 0;

		// check if intermediate soln has enough probe wires
		int p_count = -1;
		try {
			Collection<EDIFPort> ports = design.getNetlist().getTopCell().getCellInst("top").getCellPorts();
			for (EDIFPort p : ports)
				if (p.toString().startsWith("probes"))
					p_count = p.getWidth();

			if (p_count == -1)
				throw new NullPointerException();

			printIfVerbose("Width of probes bus in ila of intermediate design is " + p_count + ".");
		} catch (NullPointerException npe) {
			printIfVerbose("Couldn't find width of probes bus in ila. The intermediate design being "
					+ "used might not be usable by this application.");
		}

		if (p_count < probe_count) {
			printIfVerbose("Not enough wires. Must add an ila with more wires to input dcp.");

			design = safeReadCheckpoint(no_ila_dcp_file);
			return 0;
		} else
			padProbeMap(p_count);
		return step;
	}

	/**
	 * Specialized function to connect a debug port within an EDIF netlist.
	 * 
	 * Modified from {@link EDIFTools#connectDebugProbe}.
	 * 
	 * @param topPortNet    The top-level net that connects to the debug core's
	 *                      input port.
	 * @param routedNetName The name of the routed net whose source is the net we
	 *                      need to connect to
	 * @param newPortName   The name of the port to be added at each level of
	 *                      hierarchy
	 * @param parentInst    The instance where topPortNet resides
	 * @param instMap       The map of the design created by
	 *                      {@link EDIFTools#generateCellInstMap(EDIFCellInst)}
	 */
	private static void my_connectDebugProbe(EDIFNet topPortNet, String routedNetName, String newPortName,
			EDIFHierCellInst parentInst, EDIFNetlist n, HashMap<EDIFCell, ArrayList<EDIFCellInst>> instMap) {
		EDIFNet currNet = topPortNet;
		String currParentName = parentInst.getHierarchicalInstName();
		EDIFCellInst currInst = parentInst.getInst();
		// Need to check if we need to move up levels of hierarchy before we move down
		while (!routedNetName.startsWith(currParentName)) {
			EDIFPort port = currInst.getCellType().createPort(newPortName, EDIFDirection.INPUT, 1);
			currNet.createPortInst(port);
			EDIFCellInst prevInst = currInst;
			try {
				currParentName = currParentName.substring(0, currParentName.lastIndexOf(EDIFTools.EDIF_HIER_SEP));
			} catch (IndexOutOfBoundsException iob) {
				currParentName = "";
			}
			currInst = n.getCellInstFromHierName(currParentName);
			currNet = currInst.getCellType().getNet(newPortName);
			if (currNet == null)
				currNet = new EDIFNet(newPortName, currInst.getCellType());
			currNet.createPortInst(newPortName, prevInst);
		}

		String[] parts = routedNetName.split(EDIFTools.EDIF_HIER_SEP);
		int idx = 0;
		if (!n.getTopCell().equals(currInst.getCellType())) {
			while (idx < parts.length) {
				if (parts[idx++].equals(currInst.getName())) {
					break;
				}
			}
			if (idx == parts.length) {
				throw new RuntimeException("ERROR: Couldn't find instance " + currInst.getName()
						+ " from routed net name " + routedNetName);
			}
		}

		for (int i = idx; i <= parts.length - 2; i++) {
			currInst = currInst.getCellType().getCellInst(parts[i]);
			EDIFCell type = currInst.getCellType();
			if (instMap != null && instMap.get(type).size() > 1) {
				// TODO Replicate cell type and create new
			}
			EDIFPort newPort = currInst.getCellType().createPort(newPortName, EDIFDirection.OUTPUT, 1);
			EDIFPortInst portInst = new EDIFPortInst(newPort, currNet, currInst);
			currNet.addPortInst(portInst);
			if (i == parts.length - 2) {
				EDIFNet targetNet = currInst.getCellType().getNet(parts[parts.length - 1]);
				targetNet.createPortInst(newPort);
			} else {
				EDIFNet childNet = currInst.getCellType().getNet(topPortNet.getName());
				if (childNet == null)
					childNet = new EDIFNet(topPortNet.getName(), currInst.getCellType());
				childNet.createPortInst(newPort);
				currNet = childNet;
			}
		}
	}

	/**
	 * Updates a design containing ILA (integrated logic analyzer) probe connections
	 * that already exist in a design.
	 * 
	 * Modified from
	 * {@link com.xilinx.rapidwright.debug.ProbeRouter#updateProbeConnections}.
	 */
	public static void my_updateProbeConnections(Design d, Map<String, String> probeToTargetNets, PBlock pblock) {
		ArrayList<SitePinInst> pinsToRoute = new ArrayList<>();
		for (Entry<String, String> e : probeToTargetNets.entrySet()) {
			String hierPinName = e.getKey();
			String cellInstName = EDIFTools.getHierarchicalRootFromPinName(hierPinName);
			EDIFCellInst i = d.getNetlist().getCellInstFromHierName(cellInstName);
			String pinName = hierPinName.substring(hierPinName.lastIndexOf(EDIFTools.EDIF_HIER_SEP) + 1);
			EDIFPortInst portInst = i.getPortInst(pinName);
			EDIFNet net = portInst.getNet();
			String parentCellInstName = cellInstName.contains(EDIFTools.EDIF_HIER_SEP)
					? cellInstName.substring(0, cellInstName.lastIndexOf(EDIFTools.EDIF_HIER_SEP))
					: "";
			Net oldPhysNet = null;
			try {
				oldPhysNet = d.getNetlist().getPhysicalNetFromPin(parentCellInstName, portInst, d);
			} catch (IndexOutOfBoundsException iobe) {
				oldPhysNet = null;
			}
			// Find the sink flop
			String hierInstName = cellInstName.contains(EDIFTools.EDIF_HIER_SEP)
					? cellInstName.substring(0, cellInstName.lastIndexOf('/'))
					: "";
			EDIFHierPortInst startingPoint = new EDIFHierPortInst(hierInstName, portInst);
			ArrayList<EDIFHierPortInst> sinks = EDIFTools.findSinks(startingPoint);
			if (sinks.size() != 1) {
				System.err.println("ERROR: Currently we only support a single flip flop "
						+ "sink for probe re-routes, found " + sinks.size() + " on " + e.getKey() + ", skipping...");
				continue;
			}

			EDIFHierPortInst sinkFlop = sinks.get(0);
			Cell c = d.getCell(sinkFlop.getFullHierarchicalInstName());
			SitePinInst physProbeInPin = null;
			try {
				physProbeInPin = c.unrouteLogicalPinInSite(sinkFlop.getPortInst().getName());
			} catch (NullPointerException npe) {
				physProbeInPin = null;
			}

			// Disconnect probe from current net
			net.removePortInst(portInst);
			// Unroute the portion of physical route to old probe net
			if (physProbeInPin != null)
				oldPhysNet.removePin(physProbeInPin, true);

			// Connect probe to new net
			String newPortName = "rw_" + pinName;
			EDIFNet newNet = net.getParentCell().createNet(newPortName);
			newNet.addPortInst(portInst);

			EDIFCellInst parent = d.getNetlist().getCellInstFromHierName(parentCellInstName);
			EDIFHierCellInst parentInst = new EDIFHierCellInst(parentCellInstName, parent);
			my_connectDebugProbe(newNet, e.getValue(), newPortName, parentInst, d.getNetlist(), null);

			String parentNet = d.getNetlist().getParentNetName(e.getValue());
			Net destPhysNet = d.getNet(parentNet);

			// Route the site appropriately

			String sitePinName = c.getBELName().charAt(0) + "X";
			BELPin inPin = c.getBEL().getPin(c.getPhysicalPinMapping(sinkFlop.getPortInst().getName()));
			c.getSiteInst().routeIntraSiteNet(destPhysNet, c.getSite().getBELPin(sitePinName), inPin);

			if (physProbeInPin == null) {
				// Previous connection was internal to site, need to route out to site pin
				physProbeInPin = new SitePinInst(false, sitePinName, c.getSiteInst());
			}
			destPhysNet.addPin(physProbeInPin);
			pinsToRoute.add(physProbeInPin);
		}

		// Attempt route new net to probe
		// TODO - Should we add a flop?
		Router r = new Router(d);
		r.routePinsReEntrant(pinsToRoute, false);
	}

	private void printIfVerbose(String msg) {
		if (verbose)
			MessageGenerator.briefMessage(msg);
	}

	private static void printIfVerbose(String msg, boolean verbose) {
		if (verbose)
			MessageGenerator.briefMessage(msg);
	}
}