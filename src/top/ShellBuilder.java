package top;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.blocks.PBlock;
import com.xilinx.rapidwright.edif.*;
import com.xilinx.rapidwright.util.MessageGenerator;

import parser.Args;
import parser.Parser;
import tcl.TCLEnum;
import tcl.TCLScript;
import util.DesignUtils;
import worker.Merge;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShellBuilder {

    Map<String, List<String>> arg_map = null;

    public ShellBuilder() {

    }

    /**
     * Get the first argument from the command line with given key.
     * 
     * @param key Argument to search for.
     * @return First input argument or null if no arguments found.
     */
    String getOneArg(String key) {
        List<String> list = (arg_map == null) ? null : arg_map.get(key);
        return (list == null) ? null : list.get(0);
    }

    /**
     * True if force was part of the command line args.
     * 
     * @return
     */
    public boolean force() {
        return arg_map.containsKey(Args.Tag.FORCE.toString());
    }

    /**
     * True if verbose or extra_verbose was part of the command line args and quiet
     * wasn't.
     * 
     * @return
     */
    public boolean verbose() {
        return !quiet() && (arg_map.containsKey(Args.Tag.VERBOSE.toString())
                || arg_map.containsKey(Args.Tag.EXTRA_VERBOSE.toString()));
    }

    /**
     * True if extra_verbose was part of the command line args and quiet wasn't.
     * 
     * @return
     */
    public boolean extraVerbose() {
        return !quiet() && arg_map.containsKey(Args.Tag.EXTRA_VERBOSE.toString());
    }

    /**
     * True if quiet was part of the command line args.
     * 
     * @return
     */
    public boolean quiet() {
        return arg_map.containsKey(Args.Tag.QUIET.toString());
    }

    /**
     * Generate options in format for TCLScript. Note only pass verbose to tcl if
     * extra-verbose is selected.
     * 
     * @return String of options.
     */
    public String options() {
        return (extraVerbose() ? "v" : "") + (quiet() ? "q" : "") + (force() ? "f" : "");
    }

    /**
     * Generate options in format for TCLScript. Note only pass verbose to tcl if
     * extra-verbose is selected.
     * 
     * @param addnl_opts Additional options to add to options string. Error checking
     *                   is not done to confirm that this string does not create
     *                   problems.
     * @return String of options.
     */
    public String options(String addnl_opts) {
        return options() + addnl_opts;
    }

    /**
     * Prints the string if verbose was part of the command line args.
     * 
     * @param s String to be printed.
     */
    private void printIfVerbose(String s) {
        if (verbose())
            MessageGenerator.briefMessage(s);
    }

    private void tryMerge(String d1, String clk1, String d2, String clk2, String dout) {
        printIfVerbose("Trying to merge some designs.");
        Design design1 = DesignUtils.safeReadCheckpoint(d1, true);
        printIfVerbose("\n");
        Design design2 = DesignUtils.safeReadCheckpoint(d2, true);

        Merge cat = new Merge(design1, clk1, null);
        cat.merge(design2, clk2, null);

        File output_dcp = new File(dout);
        File output_dcp_bad = new File(dout.replace(".dcp", "_bad_edif.dcp"));
        cat.connectPortsToGround();
        cat.getDesign().writeCheckpoint(output_dcp_bad.getAbsolutePath());
        String cell_name = design2.getNetlist().getTopCell().getName();
        DesignUtils.fixEdifInDCP(output_dcp_bad.getAbsolutePath(), output_dcp.getAbsolutePath(), cell_name, cell_name,
                verbose());
    }

    private void tryMergeDriver(int test) {
        final String constant_dcp = "/thesis0/pc2019/Igi/shell/pieces/build/constant.dcp";
        // final String constant_dcp =
        // "/thesis0/pc2019/Igi/shell/pieces/constant/constant.runs/synth_1/design_1.dcp";
        final String constant_dcp_clk = null;
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
                true);

        // create_pblock pblock_1
        // resize_pblock pblock_1 -add SLICE_X28Y50:SLICE_X35Y53
        // add_cells_to_pblock pblock_1 [get_cells [list top]] -clear_locs

        PBlock block = new PBlock(design.getDevice(), "SLICE_X28Y50:SLICE_X35Y53");
        Merge cat = new Merge(design, "s_axi_aclk", block);

        File output_dcp = new File("/thesis0/pc2019/Igi/shell/tests/test_merge.dcp");
        cat.connectPortsToGround();
        cat.getDesign().writeCheckpoint(output_dcp.getAbsolutePath());

    }

    private void tryConnectPorts() {
        Design design = DesignUtils
                .safeReadCheckpoint("/nfs/ug/thesis/thesis0/pc2019/Igi/shell/pieces/build/added_2.dcp", true);

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

        Map<String, String> connections = new HashMap<>();
        connections.put("design_1_axi_gpio_0_0/gpio_io_o", "design_1_axi_gpio_1_1/gpio_io_i");

        Design d = DesignUtils.safeReadCheckpoint(constant_dcp, true);
        PBlock block = null;
        Merge cat = new Merge(d, null, block);
        String output_dcp = output_dcp_base;

        for (int i = 0; i < ooc_dcps.size(); i++) {
            Merge.lockPlacement(cat.getDesign());
            Merge.lockRouting(cat.getDesign());

            if (i > 0) {
                d = DesignUtils.safeReadCheckpoint(output_dcp, true);
                cat = new Merge(d, null, null);
            }

            d = DesignUtils.safeReadCheckpoint(ooc_dcps.get(i), true);
            block = new PBlock(d.getDevice(), pblock_range.get(i));
            cat.merge(d, null, block);
            cat.connectPortsToGround();
            // cat.connect(connections);

            output_dcp = output_dcp_base.replace("1.dcp", (i + 1) + ".dcp");
            String output_dcp_bad = output_dcp.replace(".dcp", "_bad_edif.dcp");
            cat.getDesign().writeCheckpoint(output_dcp_bad);
            String cell_name = d.getNetlist().getTopCell().getName();
            DesignUtils.fixEdifInDCP(output_dcp_bad, output_dcp, cell_name, cell_name, verbose());

            String pblock_name = d.getName() + "_pblock";
            TCLScript script = new TCLScript(output_dcp, output_dcp, options("f"), "../pieces/pblock_place_route_step.tcl");
            script.addCustomCmd("create_pblock " + pblock_name);
            script.addCustomCmd("resize_pblock -add " + pblock_range.get(i) + " [get_pblocks " + pblock_name + "]");
            script.addCustomCmd("add_cells_to_pblock [get_pblocks " + pblock_name + "] [get_cells " + d.getName() + "]");
            script.add(TCLEnum.PLACE);
            script.add(TCLEnum.ROUTE);
            script.add(TCLEnum.WRITE_DCP);
            script.add(TCLEnum.WRITE_EDIF);
            script.run();
        }

        final String gpio_0_clk = "s_axi_aclk";
    }

    public void start(String[] args) {
        Parser parser = new Parser();
        arg_map = parser.mapArgs(args);

        // tryMergeDriver(1);
        // tryMergeDriver(2);
        // tryPlace();
        // tryConnectPorts();
        tryMergeLong();

        MessageGenerator.briefMessage("\nFinished.");
    }

    public static void main(String[] args) {
        ShellBuilder builder = new ShellBuilder();
        builder.start(args);
    }
}