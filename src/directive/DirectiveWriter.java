package directive;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.edif.EDIFCellInst;
import com.xilinx.rapidwright.util.FileTools;

import org.w3c.dom.Element;

import directive.Directive.HEADER;
import directive.Directive.INST;
import directive.Directive.FILE;
import parser.XMLParser;
import parser.XMLParser.BaseEnum;
import parser.XMLParser.KEY;
import parser.XMLParser.TAG;
import util.DesignUtils;

public class DirectiveWriter {
	boolean verbose = false;
	boolean include_primitives = false;

	DirectiveWriter(boolean verbose, boolean include_primitives) {
		this.verbose = verbose;
		this.include_primitives = include_primitives;
	}

	public static void writeTemplate(File input_dcp, boolean include_primitives, File output_xml, boolean verbose,
			File dir) {
		writeTemplate(input_dcp.getAbsolutePath(), include_primitives, output_xml.getAbsolutePath(), verbose,
				dir.getAbsolutePath());
	}

	public static void writeTemplate(String input_dcp, boolean include_primitives, String output_xml, boolean verbose,
			String dir) {

		Design design = DesignUtils.safeReadCheckpoint(input_dcp, verbose, dir);
		DirectiveWriter dw = new DirectiveWriter(verbose, include_primitives);
		EDIFCellInst top_ci = design.getNetlist().getTopCellInst();

		WrNode top = dw.constructBuild(top_ci);

		List<TAG> children = new ArrayList<>();
		for (TAG t : top.children())
			if (t.key.equals(Directive.header.key)) {
				List<TAG> header_children = new ArrayList<>(t.children());
				header_children.add(new WrLeaf(HEADER.iii_dir.key, ""));
				header_children.add(new WrLeaf(HEADER.ooc_dir.key, ""));
				header_children.add(new WrLeaf(HEADER.out_dir.key, ""));
				children.add(new WrNode(t.key, header_children, t.attributes()));
			} else if (t.key.equals(Directive.inst.key))
				children.add(t);

		children.add(dw.constructWrite());

		WrNode root = new WrNode("root", children, null);
		root.write(output_xml);
	}

	WrNode constructInst(EDIFCellInst ci) {
		List<TAG> children = new ArrayList<>();
		List<KEY> attributes = new ArrayList<>();
		String key = Directive.inst.key;

		attributes.add(new WrKey(INST.type.key, INST.TYPE.TypeEnum.MERGE));
		children.add(new WrLeaf(INST.inst_name.key, ci.getName()));
		children.add(new WrLeaf(INST.dcp.key, ""));
		children.add(new WrLeaf(INST.pblock.key, ""));

		if (ci.isBlackBox()) {
			System.out.println("Black: " + ci.getName() + " - " + ci.getCellName());

		} else if (!ci.getCellType().hasContents()) {
			// System.out.println("Empty: " + ci.getName() + " - " + ci.getCellName());

		} else if (ci.getCellType().getCellInsts() == null) {
			// ? only wires
			System.out.println("Wires: " + ci.getName() + " - " + ci.getCellName());

		} else {
			// System.out.println("Front: " + ci.getName() + " - " + ci.getCellName());
			return constructBuild(ci);
			// System.out.println("End : " + ci.getName() + " - " + ci.getCellName());
		}

		return new WrNode(key, children, attributes);
	}

	WrNode contructHeader(EDIFCellInst ci) {
		List<TAG> children = new ArrayList<>();
		List<KEY> attributes = new ArrayList<>();
		String key = Directive.header.key;

		children.add(new WrLeaf(HEADER.module_name.key, ci.getCellName()));

		return new WrNode(key, children, attributes);
	}

	WrNode constructBuild(EDIFCellInst ci) {
		List<TAG> children = new ArrayList<>();
		List<KEY> attributes = new ArrayList<>();
		String key = Directive.inst.key;

		attributes.add(new WrKey(INST.type.key, INST.TYPE.TypeEnum.BUILD));
		children.add(new WrLeaf(INST.inst_name.key, ci.getName()));
		children.add(contructHeader(ci));

		for (EDIFCellInst cell_inst : ci.getCellType().getCellInsts())
			if (include_primitives || !cell_inst.getCellType().isPrimitive())
				children.add(constructInst(cell_inst));

		return new WrNode(key, children, attributes);
	}

	WrNode constructWrite() {
		List<TAG> children = new ArrayList<>();
		List<KEY> attributes = new ArrayList<>();
		String key = Directive.inst.key;

		attributes.add(new WrKey(INST.type.key, INST.TYPE.TypeEnum.WRITE));
		children.add(new WrLeaf(INST.dcp.key, Arrays.asList(new WrKey(INST.dcp.key, FILE.LOC.LocEnum.OUT)), ""));

		return new WrNode(key, children, attributes);
	}

	public static class WrNode extends TAG {
		public WrNode(String key, List<TAG> children, List<KEY> attributes) {
			super(key, children, attributes);
		}

		public WrNode(String key) {
			super(key);
		}

		public void write(String filename) {
			List<String> lines = new ArrayList<>();
			lines.add("<?xml version = \"1.0\"?>");
			toLines(lines, "");
			FileTools.writeLinesToTextFile(lines, filename);
		}

		void toLines(List<String> lines, String indentation) {
			StringBuilder sb = new StringBuilder(indentation + "<" + key);
			for (KEY k : attributes())
				sb.append(" " + ((WrKey) k).getFirstPairStr());
			sb.append(">");
			lines.add(sb.toString());
			for (TAG t : children())
				((WrNode) t).toLines(lines, indentation + "\t");
			lines.add(indentation + "</" + key + ">");
		}
	}

	public static class WrKey extends KEY {
		public WrKey(String key, BaseEnum value) {
			super(key, new HashSet<>(Arrays.asList(value)));
		}

		public String getFirstPairStr() {
			if (values().isEmpty())
				return "";
			String v = values().iterator().next().toString();
			return key + " = \"" + v + "\"";
		}
	}

	public static class WrLeaf extends WrNode {
		String data = null;

		public WrLeaf(String key, String data) {
			super(key);
			this.data = data;
		}

		public WrLeaf(String key, List<KEY> attributes, String data) {
			super(key, null, attributes);
			this.data = data;
		}

		@Override
		void toLines(List<String> lines, String indentation) {
			StringBuilder sb = new StringBuilder(indentation + "<" + key);
			for (KEY k : attributes())
				sb.append(" " + ((WrKey) k).getFirstPairStr());
			sb.append(">");
			sb.append(data);
			sb.append("</" + key + ">");
			lines.add(sb.toString());
		}
	}

	public static class TemplateBuilder {
		DirectiveHeader head = null;
		File dcp = null;
		File out = null;
		boolean include_primitives = false;

		TemplateBuilder(Element elem, DirectiveHeader head) {
			this.head = head;

			if (elem == null)
				return;

			include_primitives = XMLParser.getFirstBool(elem, TEMPLATE.include_primitives);
			dcp = Directive.getFirstFile(elem, TEMPLATE.dcp, head.fsys(), true);
			out = Directive.getFirstFile(elem, TEMPLATE.out, head.fsys(), false);
		}

		public void writeTemplate() {
			DirectiveWriter.writeTemplate(dcp, include_primitives, out, head.isVerbose(), head.getIII());
		}
	}

	public static class TEMPLATE extends TAG {
		public static final FILE dcp = new FILE("dcp");
		public static final FILE out = new FILE("out");
		public static final TAG include_primitives = new TAG("include_primitives");

		TEMPLATE() {
			super("template", Arrays.asList(dcp, out, include_primitives), Arrays.asList());
		}
	}

	public static TEMPLATE template = new TEMPLATE();
}
