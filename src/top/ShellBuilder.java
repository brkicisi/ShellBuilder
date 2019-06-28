package top;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.blocks.PBlock;
import com.xilinx.rapidwright.edif.*;
import com.xilinx.rapidwright.util.MessageGenerator;

import parser.Args;
import parser.ArgsContainer;
import tcl.TCLEnum;
import tcl.TCLScript;
import util.DesignUtils;
import worker.Merge;
import worker.BuildDirectives;
import worker.Connections;
import worker.Directive;
import worker.FileSys;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShellBuilder {

	ArgsContainer args = null;
	final String TEMP_III_DIR = "/nfs/ug/thesis/thesis0/pc2019/Igi/shell/pieces/.iii";

	public ShellBuilder() {

	}

	/**
	 * Prints the string if verbose was part of the command line args.
	 * 
	 * @param s String to be printed.
	 */
	private void printIfVerbose(String s) {
		if (args.verbose())
			MessageGenerator.briefMessage(s);
	}

	private void tryMerge(String d1, String clk1, String d2, String clk2, String dout) {
		printIfVerbose("Trying to merge some designs.");
		Design design1 = DesignUtils.safeReadCheckpoint(d1, true, TEMP_III_DIR);
		printIfVerbose("\n");
		Design design2 = DesignUtils.safeReadCheckpoint(d2, true, TEMP_III_DIR);

		Merge cat = new Merge(design1);
		cat.merge(design2, null);

		File output_dcp = new File(dout);
		File output_dcp_bad = new File(dout.replace(".dcp", "_bad_edif.dcp"));
		cat.connectPortsToGround();
		cat.getDesign().writeCheckpoint(output_dcp_bad.getAbsolutePath());
		String cell_name = design2.getNetlist().getTopCell().getName();
		DesignUtils.fixEdifInDCP(output_dcp_bad.getAbsolutePath(), output_dcp.getAbsolutePath(), cell_name, cell_name,
				args.verbose());
	}

	private void tryMergeDriver(int test) {
		final String constant_dcp = "/thesis0/pc2019/Igi/shell/pieces/build/constant.dcp";
		// final String constant_dcp =
		// "/thesis0/pc2019/Igi/shell/pieces/constant/constant.runs/synth_1/design_1.dcp";
		final String gpio_0 = "/nfs/ug/thesis/thesis0/pc2019/Igi/shell/pieces/tutorial_2/tutorial/tutorial.runs/design_1_axi_gpio_0_0_synth_1/design_1_axi_gpio_0_0.dcp";
		final String gpio_0_clk = "s_axi_aclk";
		final String gpio_1 = "/nfs/ug/thesis/thesis0/pc2019/Igi/shell/pieces/tutorial_2/tutorial/tutorial.runs/design_1_axi_gpio_1_1_synth_1/design_1_axi_gpio_1_1.dcp";
		final String gpio_1_clk = "s_axi_aclk";

		final String out1 = "/nfs/ug/thesis/thesis0/pc2019/Igi/shell/pieces/build/added_1.dcp";
		final String out2 = "/nfs/ug/thesis/thesis0/pc2019/Igi/shell/pieces/build/added_2.dcp";

		String d1 = constant_dcp;
		String clk1 = null;
		String d2 = constant_dcp;
		String clk2 = null;
		String dout = out1;

		switch (test) {
		case 1:
			d2 = gpio_0;
			clk2 = gpio_0_clk;
			dout = out1;
			break;
		case 2:
			// d1 = out1;
			d1 = "/nfs/ug/thesis/thesis0/pc2019/Igi/shell/pieces/build/added_1_bpr.dcp";
			clk1 = gpio_0_clk;
			d2 = gpio_1;
			clk2 = gpio_1_clk;
			dout = out2;
			break;
		default:
			MessageGenerator.briefMessage("No valid merge test selected.");
			return;
		}
		tryMerge(d1, clk1, d2, clk2, dout);
	}

	private void tryPlace() {
		printIfVerbose("Trying to place a single ooc block");
		Design design = DesignUtils.safeReadCheckpoint(
				"/nfs/ug/thesis/thesis0/pc2019/Igi/shell/pieces/tutorial_2/tutorial/tutorial.runs/design_1_axi_gpio_0_0_synth_1/design_1_axi_gpio_0_0.dcp",
				true, TEMP_III_DIR);

		// create_pblock pblock_1
		// resize_pblock pblock_1 -add SLICE_X28Y50:SLICE_X35Y53
		// add_cells_to_pblock pblock_1 [get_cells [list top]] -clear_locs

		PBlock block = new PBlock(design.getDevice(), "SLICE_X28Y50:SLICE_X35Y53");
		Merge cat = new Merge(design);

		File output_dcp = new File("/thesis0/pc2019/Igi/shell/tests/test_merge.dcp");
		cat.connectPortsToGround();
		cat.getDesign().writeCheckpoint(output_dcp.getAbsolutePath());

	}

	private void tryConnectPorts() {
		Design design = DesignUtils.safeReadCheckpoint(
				"/nfs/ug/thesis/thesis0/pc2019/Igi/shell/pieces/build/added_2.dcp", true, TEMP_III_DIR);

		EDIFCell top_cell = design.getNetlist().getTopCell();
		EDIFNet n = new EDIFNet("clk", top_cell);

		EDIFCellInst ci = top_cell.getCellInst("design_1_axi_gpio_0_0");
		EDIFPortInst old_pi = ci.getPortInst("s_axi_aclk");
		if (old_pi != null)
			old_pi.getNet().removePortInst(old_pi);
		n.createPortInst(ci.getPort("s_axi_aclk"), ci);

		ci = top_cell.getCellInst("design_1_axi_gpio_1_1");
		old_pi = ci.getPortInst("s_axi_aclk");
		if (old_pi != null)
			old_pi.getNet().removePortInst(old_pi);
		n.createPortInst(ci.getPort("s_axi_aclk"), ci);

		List<EDIFNet> net = new ArrayList<>();
		for (int i = 0; i <= 1; i++)
			net.add(new EDIFNet("gpio_passthrough[" + i + "]", top_cell));

		ci = top_cell.getCellInst("design_1_axi_gpio_0_0");
		EDIFPort port = ci.getPort("gpio_io_o");
		if (port.isBus()) {
			for (int i = port.getRight(); i <= port.getLeft(); i++) {
				old_pi = ci.getPortInst(port.getBusName() + "[" + i + "]");
				if (old_pi != null)
					old_pi.getNet().removePortInst(old_pi);
				net.get(i).createPortInst(port, i, ci);
			}
		} else {
			old_pi = ci.getPortInst(port.getBusName());
			if (old_pi != null)
				old_pi.getNet().removePortInst(old_pi);
			net.get(0).createPortInst(port, ci);
		}

		ci = top_cell.getCellInst("design_1_axi_gpio_1_1");
		port = ci.getPort("gpio_io_i");
		if (port.isBus()) {
			for (int i = port.getRight(); i <= port.getLeft(); i++) {
				old_pi = ci.getPortInst(port.getBusName() + "[" + i + "]");
				if (old_pi != null)
					old_pi.getNet().removePortInst(old_pi);
				net.get(i).createPortInst(port, i, ci);
			}
		} else {
			old_pi = ci.getPortInst(port.getBusName());
			if (old_pi != null)
				old_pi.getNet().removePortInst(old_pi);
			net.get(0).createPortInst(port, ci);
		}

		design.writeCheckpoint("/nfs/ug/thesis/thesis0/pc2019/Igi/shell/pieces/build/added_2_clk.dcp");
	}

	private void tryMergeLong() {
		final String constant_dcp = "/thesis0/pc2019/Igi/shell/pieces/build/constant.dcp";
		final String output_dcp_base = "/nfs/ug/thesis/thesis0/pc2019/Igi/shell/pieces/build/added_1.dcp";

		List<String> ooc_dcps = new ArrayList<>();
		List<String> pblock_range = new ArrayList<>();
		ooc_dcps.add(
				"/nfs/ug/thesis/thesis0/pc2019/Igi/shell/pieces/tutorial_2/tutorial/tutorial.runs/design_1_axi_gpio_0_0_synth_1/design_1_axi_gpio_0_0.dcp");
		pblock_range.add("SLICE_X0Y0:SLICE_X3Y5");
		ooc_dcps.add(
				"/nfs/ug/thesis/thesis0/pc2019/Igi/shell/pieces/tutorial_2/tutorial/tutorial.runs/design_1_axi_gpio_1_1_synth_1/design_1_axi_gpio_1_1.dcp");
		pblock_range.add("SLICE_X0Y6:SLICE_X3Y11");
		// ooc_dcps.add(
		// "/nfs/ug/thesis/thesis0/pc2019/Igi/shell/pieces/tutorial_2/tutorial/tutorial.runs/design_1_clk_wiz_1_0_synth_1/design_1_clk_wiz_1_0.dcp");
		ooc_dcps.add(
				"/nfs/ug/thesis/thesis0/pc2019/Igi/shell/pieces/tutorial_2/tutorial/tutorial.runs/design_1_rst_clk_wiz_1_100M_0_synth_1/design_1_rst_clk_wiz_1_100M_0.dcp");
		pblock_range.add("SLICE_X0Y18:SLICE_X3Y24");

		Map<String, String> connections = new HashMap<>();
		connections.put("design_1_axi_gpio_0_0/gpio_io_o", "design_1_axi_gpio_1_1/gpio_io_i");

		Design d = DesignUtils.safeReadCheckpoint(constant_dcp, true, TEMP_III_DIR);
		Merge cat = new Merge(d);
		String output_dcp = output_dcp_base;

		for (int i = 0; i < ooc_dcps.size(); i++) {
			Merge.lockPlacement(cat.getDesign());
			Merge.lockRouting(cat.getDesign());

			if (i > 0) {
				d = DesignUtils.safeReadCheckpoint(output_dcp, true, TEMP_III_DIR);
				cat = new Merge(d);
			}

			d = DesignUtils.safeReadCheckpoint(ooc_dcps.get(i), true, TEMP_III_DIR);
			PBlock block = new PBlock(d.getDevice(), pblock_range.get(i));
			Connections conn = null;
			cat.merge(d, conn);

			output_dcp = output_dcp_base.replace("1.dcp", (i + 1) + ".dcp");
			String output_dcp_bad = output_dcp.replace(".dcp", "_bad_edif.dcp");
			cat.getDesign().writeCheckpoint(output_dcp_bad);
			String cell_name = d.getNetlist().getTopCell().getName();
			DesignUtils.fixEdifInDCP(output_dcp_bad, output_dcp, cell_name, cell_name, args.verbose());

			String pblock_name = d.getName() + "_pblock";
			TCLScript script = new TCLScript(output_dcp, output_dcp, args.options("f"),
					"../pieces/pblock_place_route_step.tcl");
			script.addCustomCmd("create_pblock " + pblock_name);
			script.addCustomCmd("resize_pblock -add " + pblock_range.get(i) + " [get_pblocks " + pblock_name + "]");
			script.addCustomCmd(
					"add_cells_to_pblock [get_pblocks " + pblock_name + "] [get_cells " + d.getName() + "]");
			script.add(TCLEnum.PLACE);
			script.add(TCLEnum.ROUTE);
			script.add(TCLEnum.WRITE_DCP);
			script.add(TCLEnum.WRITE_EDIF);
			script.run();
		}
	}

	/**
	 * Ensure that if the user has not specified to force overwrite, output dcp
	 * files don't collide with already existing files.
	 */
	private void checkFileCollision(Directive directive) {
		if (args.force() || directive.isForce())
			return;

		boolean any_err = false;
		File f = directive.getDCP();
		if ((f != null) && f.exists()) {
			MessageGenerator.briefError("An output dcp would overwrite another file at '" + f.getAbsolutePath() + "'.");
			any_err = true;
		}
		// if any would overwrite, exit
		if (any_err)
			MessageGenerator.briefErrorAndExit("Use force (-f) to overwrite.\nCanceling operation.\n");
	}

	private void runDirective(Directive directive) {
		if (directive.isMerge()) {
			System.out.println("merge: " + directive.getDCP().getAbsolutePath());

		} else if (directive.isWrite()) {
			System.out.println("write" + directive.getDCP().getAbsolutePath());
			checkFileCollision(directive);

		} else
			MessageGenerator
					.briefErrorAndExit("Unrecognized directive '" + directive.getType().getStr() + "'.\nExiting.");
	}

	public void start(String[] cmd_line_args) {
		args = new ArgsContainer(cmd_line_args);

		// tryMergeDriver(1);
		// tryMergeDriver(2);
		// tryPlace();
		// tryConnectPorts();
		// tryMergeLong();

		File xml_directives = new FileSys(args.verbose()).getExistingFile(args.getOneArg(Args.Tag.XML_DIRECTIVES), true);
		BuildDirectives directive_builder = new BuildDirectives();
		directive_builder.parse(xml_directives, args.verbose());

		// this currently just executes everything (like refresh)
		// doesn't check for partial solutions
		for (Directive step : directive_builder.getDirectives())
			runDirective(step);

		MessageGenerator.briefMessage("\nFinished.");
	}

	public static void main(String[] args) {
		ShellBuilder builder = new ShellBuilder();
		builder.start(args);
	}
}