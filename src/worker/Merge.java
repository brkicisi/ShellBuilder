package worker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.xilinx.rapidwright.design.*;
import com.xilinx.rapidwright.design.blocks.PBlock;
import com.xilinx.rapidwright.device.PIP;
import com.xilinx.rapidwright.edif.*;
import com.xilinx.rapidwright.util.MessageGenerator;

public class Merge {
    Design original = null;
    String clk_name = null;
    List<EDIFPort> unconnected_ports = null;

    public Merge() {
    }

    public Merge(Design d, String clk_name, PBlock block) {
        insertFirstDesign(d, clk_name, block);
    }

    public Design getDesign() {
        return original;
    }

    public String getClkName() {
        return clk_name;
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

    private void insertFirstDesign(Design mergee, String mergee_clk_name, PBlock block) {
        mergee.getNetlist().getTopCellInst();
        mergee.getNetlist().renameNetlistAndTopCell(mergee.getName());
        this.original = mergee;
        this.clk_name = mergee_clk_name;

        // TODO may want this to be something other than a Collection.
        // I think these top ports will all be unconnected if loading from ooc dcp
        unconnected_ports = new ArrayList<>(original.getNetlist().getTopCell().getPorts());

        // if this is not empty, there is something connected to the top level of the
        // design
        ArrayList<EDIFPortInst> connected_ports = new ArrayList<>(mergee.getNetlist().getTopCellInst().getPortInsts());

        // TODO do something here
        // place, connect, route, ...

    }

    /**
     * Merge another design with this design. Mostly copied from RapidWright class
     * ILAInserter:applyILAToDesign().
     * 
     * Assumes clock net is already at top of hierchy if it exists.
     * 
     * @param mergee          Design to merge with this.
     * @param mergee_clk_name Name of top level clk in mergee design.
     */
    public void merge(Design mergee, String mergee_clk_name, PBlock block) {
        if (original == null) {
            insertFirstDesign(mergee, mergee_clk_name, block);
            return;
        }
        mergee.getNetlist().getTopCellInst(); // Creates a top cell inst. Must exist to be renamed.
        mergee.getNetlist().renameNetlistAndTopCell(mergee.getName());
        unconnected_ports.addAll(mergee.getNetlist().getTopCell().getPorts());

        // Logical netlist
        EDIFNetlist e = original.getNetlist();

        EDIFCellInst mergeeInst = e.getTopCell().addCellInst(mergee.getNetlist().getTopCellInst());
        EDIFCell mergeeTop = mergee.getNetlist().getTopCell();
        mergeeTop.setView("netlist");
        e.getTopCell().getLibrary().addCell(mergeeTop);
        for (EDIFLibrary lib : mergee.getNetlist().getLibraries()) {
            EDIFLibrary orig = e.getLibrary(lib.getName());
            if (orig == null) {
                orig = e.getWorkLibrary();
            }
            if (orig.getName().equals(EDIFTools.EDIF_LIBRARY_HDI_PRIMITIVES_NAME)) {
                for (EDIFCell c : lib.getCells()) {
                    if (!orig.containsCell(c)) {
                        orig.addCell(c);
                    }
                }
            } else {
                for (EDIFCell c : lib.getCells()) {
                    orig.addCell(c);
                }
            }
        }
        // **
        // ** clk should already a port at the top instance if needed b/c the input dcps
        // were generated ooc
        // **

        // Need to bring out clk net to top level
        // EDIFNet clk = null;
        // clk = original.getNetlist().getTopCell().getNet(clk_name);

        // Connect the clock (assumes all probed signals are synchronous)
        // if (clk != null)
        // clk.createPortInst(mergeeTop.getPort(mergee_clk_name), mergeeInst);
        // else if (clk_name != null)
        // MessageGenerator
        // .briefMessage("No clk found with name '" + clk_name + "' in design '" +
        // original.getName() + "'");

        List<String> constraints = original.getXDCConstraints(ConstraintGroup.NORMAL);
        if (constraints == null) {
            constraints = new ArrayList<>();
            original.setXDCConstraints(constraints, ConstraintGroup.NORMAL);
        }
        for (String c : mergee.getXDCConstraints(ConstraintGroup.NORMAL)) {
            if (c.contains("current_instance ")) {
                if (!c.contains("-quiet")) {
                    c = c.replace("current_instance ",
                            "current_instance " + mergee.getNetlist().getTopCellInst().getName() + "/");
                }
            }
            constraints.add(c);
        }

        // place connect and route
    }

    public void connect(Map<String, String> connections) {
        EDIFCell top_cell = original.getNetlist().getTopCell();

        for (Entry<String, String> e : connections.entrySet()) {
            // find ports
            int separator = e.getKey().lastIndexOf("/");
            String cell_inst_name = (separator >= 0) ? e.getKey().substring(0, separator) : "";
            String port_name = (separator >= 0) ? e.getKey().substring(separator + 1) : e.getKey();
            EDIFCellInst ci1 = original.getNetlist().getCellInstFromHierName(cell_inst_name);
            EDIFPort port1 = ci1.getPort(port_name);

            separator = e.getValue().lastIndexOf("/");
            cell_inst_name = (separator >= 0) ? e.getValue().substring(0, separator) : "";
            port_name = (separator >= 0) ? e.getValue().substring(separator + 1) : e.getValue();
            EDIFCellInst ci2 = original.getNetlist().getCellInstFromHierName(cell_inst_name);
            EDIFPort port2 = ci2.getPort(port_name);

            if (port1.isBus()) {
                for (int i = port1.getRight(); i <= port1.getLeft(); i++) {
                    EDIFNet net = new EDIFNet(port1.getBusName() + "[" + i + "]", top_cell);

                    EDIFPortInst port_i1 = ci1.getPortInst(port_name + "[" + i + "]");
                    if (port_i1 != null)
                        port_i1.getNet().removePortInst(port_i1);
                    net.createPortInst(port1, i, ci1);

                    EDIFPortInst port_i2 = ci2.getPortInst(port_name + "[" + i + "]");
                    if (port_i2 != null)
                        port_i2.getNet().removePortInst(port_i2);
                    net.createPortInst(port2, i, ci2);
                }
            } else {
                EDIFNet net = new EDIFNet(port1.getBusName(), top_cell);

                EDIFPortInst port_i1 = ci1.getPortInst(port_name);
                if (port_i1 != null)
                    port_i1.getNet().removePortInst(port_i1);
                net.createPortInst(port1, ci1);

                EDIFPortInst port_i2 = ci2.getPortInst(port_name);
                if (port_i2 != null)
                    port_i2.getNet().removePortInst(port_i2);
                net.createPortInst(port2, ci2);
            }

            int tmp = 0;
            tmp += 9;
        }
    }

    public void connectPortsToGround() {
        EDIFCell top_cell = original.getNetlist().getTopCell();
        EDIFNet gnd_net = EDIFTools.getStaticNet(NetType.GND, top_cell, original.getNetlist());

        for (EDIFPort port : unconnected_ports) {
            if (port.isInput()) {
                EDIFCell parent = port.getParentCell();
                // EDIFPort outer_port;
                EDIFCellInst parent_cell_i = null;
                for (EDIFCellInst ci : parent.getCellInsts()) {
                    if (ci.getPort(port.getBusName()) != null)
                        parent_cell_i = ci;
                }

                EDIFPortInst pi;
                if (port.isBus())
                    pi = parent_cell_i.getPortInst(port.getBusName() + "[0]");
                else
                    pi = parent_cell_i.getPortInst(port.getBusName());

                EDIFPortInst upper_pi = pi.getNet().getPortInst(pi.getName());
                EDIFCellInst ci = top_cell.getCellInst(pi.getCellInst().getParentCell().getName());
                if (upper_pi != null) {
                    EDIFPort upper_port = upper_pi.getPort();
                    if (upper_port.isBus()) {
                        for (int i = port.getRight(); i <= port.getLeft(); i++)
                            gnd_net.createPortInst(upper_port, i, ci);
                    } else
                        gnd_net.createPortInst(upper_port, ci);
                }
            }
        }
        unconnected_ports = new ArrayList<EDIFPort>();
    }
}