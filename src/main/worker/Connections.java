
package main.worker;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.edif.EDIFCell;
import com.xilinx.rapidwright.edif.EDIFNetlist;
import com.xilinx.rapidwright.edif.EDIFPort;

import main.util.DesignUtils;
import main.worker.MyPort;
import main.directive.DirectiveBuilder;
import main.directive.Directive;

/**
 * @deprecated Using top level synth as a template instead.
 */
public class Connections {

	Map<MyPort, Set<MyPort>> conn = null;

	public Connections() {
		conn = new HashMap<>();
	}

	public Connections(String synth_1_dcp, DirectiveBuilder directive_builder, boolean verbose) {
		conn = new HashMap<>();
		Design synth_1 = DesignUtils.safeReadCheckpoint(synth_1_dcp, verbose, directive_builder.getHeader().getIII());
		addConnections(synth_1, directive_builder);
	}

	public void addConnection(String driver_port, String sink_port) {
		MyPort from = new MyPort(driver_port);
		MyPort to = new MyPort(sink_port);

		if (!conn.containsKey(from))
			conn.put(from, new HashSet<>());
		conn.get(from).add(to);
	}

	public boolean removeConnection(String driver_port, String sink_port) {
		MyPort from = new MyPort(driver_port);
		MyPort to = new MyPort(sink_port);

		if (!conn.containsKey(from))
			return false;
		return conn.get(from).remove(to);
	}

	public void addConnections(Design synth_1, DirectiveBuilder directive_builder) {
		EDIFNetlist netlist = synth_1.getNetlist();

		for (Directive directive : directive_builder.getDirectives()) {
			if (!directive.isMerge())
				continue;
			String cell_name = directive.getDCP().getName().replace(".dcp", "");
			EDIFCell cell = netlist.getCell(cell_name);
			if (cell == null)
				continue;

			for (EDIFPort port : cell.getPorts()) {
				if (port.isOutput()) {
					;
				}
			}
		}
	}

	public Set<MyPort> getSinks(String module, String port) {
		return conn.get(new MyPort(module, port, MyPort.Type.WIRE));
	}
	public Set<MyPort> getSinks(String module, String port, int width) {
		if(width > 1)
			return conn.get(new MyPort(module, port, MyPort.Type.BUS));
		else
			return getSinks(module, port);
	}
}