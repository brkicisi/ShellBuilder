package main.directive;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.edif.EDIFCellInst;
import com.xilinx.rapidwright.util.FileTools;
import com.xilinx.rapidwright.util.MessageGenerator;

import org.w3c.dom.Element;

import main.directive.DirectiveHeader.HEADER;
import main.directive.Directive.INST;
import main.directive.Directive.FILE;
import main.parser.XMLParser;
import main.parser.XMLParser.BaseEnum;
import main.parser.XMLParser.KEY;
import main.parser.XMLParser.TAG;
import main.util.DesignUtils;
import main.worker.FileSys;

/**
 * Writes xml builder template from imput dcp.
 */
public class DirectiveWriter {
	boolean verbose = false;
	DirectiveHeader head = null;
	boolean include_primitives = false;

	String iii_dir = "";
	String ooc_dir = "";
	String out_dir = "";

	DirectiveWriter(DirectiveHeader head, boolean include_primitives) {
		this.verbose = head.isVerbose();
		this.head = head;
		this.include_primitives = include_primitives;

		File dir = head.getIII();
		iii_dir = (dir == null) ? "" : dir.getAbsolutePath();
		dir = head.fsys().getRoot(FileSys.FILE_ROOT.OOC);
		ooc_dir = (dir == null) ? "" : dir.getAbsolutePath();
		dir = head.fsys().getRoot(FileSys.FILE_ROOT.OUT);
		out_dir = (dir == null) ? "" : dir.getAbsolutePath();
	}

	/**
	 * Write an xml builder template from an input_dcp.
	 * <p>
	 * Warning: The generated builder may have filled in fields incorrectly and/or
	 * not inserted values for other fields (particularly dcps and pblocks).
	 * 
	 * @param input_dcp          Dcp to use as template.
	 * @param include_primitives Write primitive blocks into template.
	 * @param output_xml         File to which xml output will be written.
	 * @param verbose            Print extra messages.
	 * @param dir                Temp dir to write tcl file to if required
	 *                           (suggested: iii dir).
	 */
	public static void writeTemplate(File input_dcp, boolean include_primitives, File output_xml,
			DirectiveHeader head) {
		writeTemplate(input_dcp.getAbsolutePath(), include_primitives, output_xml.getAbsolutePath(), head);
	}

	/**
	 * Write an xml builder template from an input_dcp.
	 * <p>
	 * Warning: The generated builder may have filled in fields incorrectly and/or
	 * not inserted values for other fields (particularly dcps and pblocks).
	 * 
	 * @param input_dcp          Dcp to use as template.
	 * @param include_primitives Write primitive blocks into template.
	 * @param output_xml         File to which xml output will be written.
	 * @param verbose            Print extra messages.
	 * @param dir                Temp dir to write tcl file to if required
	 *                           (suggested: iii dir).
	 */
	public static void writeTemplate(String input_dcp, boolean include_primitives, String output_xml,
			DirectiveHeader head) {

		if (head.isVerbose())
			MessageGenerator.briefMessage("\nStarting to construct template with input '" + input_dcp + "'.");

		Design design = DesignUtils.safeReadCheckpoint(input_dcp, head.isVerbose(), head.getIII());
		DirectiveWriter dw = new DirectiveWriter(head, include_primitives);
		EDIFCellInst top_ci = design.getNetlist().getTopCellInst();

		// Construct template
		WrNode top = dw.constructBuild(top_ci);

		// Change root tag from <inst type = "build"> to <root>.
		// To do this, must create new TAG by copying all since TAGs cannot be modified
		// after construction.
		// Also add prompts for iii, ooc, out in top level header.
		List<TAG> children = new ArrayList<>();
		for (TAG t : top.children())
			if (t.key.equals(DirectiveHeader.header.key)) {
				List<TAG> header_children = new ArrayList<>(t.children());
				header_children.add(new WrLeaf(HEADER.iii_dir.key, dw.iii_dir));
				header_children.add(new WrLeaf(HEADER.ooc_dir.key, dw.ooc_dir));
				header_children.add(new WrLeaf(HEADER.out_dir.key, dw.out_dir));
				children.add(new WrNode(t.key, header_children, t.attributes()));
			} else if (t.key.equals(Directive.inst.key))
				children.add(t);

		children.add(dw.constructWrite());

		WrNode root = new WrNode("root", children, null);
		if (head.isVerbose())
			MessageGenerator.briefMessage("\nWriting build template to '" + output_xml + "'.");
		root.write(output_xml);
	}

	/**
	 * Construct a {@literal <inst type = "merge">}. If ci has contents, this
	 * instead returns {@link #constructBuild(EDIFCellInst)}
	 * 
	 * @param ci Root for this block. Cell instance from input_dcp's netlist.
	 * @return Node representing ci and it's descendants.
	 */
	WrNode constructInst(EDIFCellInst ci) {
		// TODO check this
		// I've only seen ci.isBlackBox() == false and ci.getCellType().getCellInsts()
		// != null
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

		List<TAG> children = new ArrayList<>();
		List<KEY> attributes = new ArrayList<>();
		String key = Directive.inst.key;

		attributes.add(new WrKey(INST.type.key, INST.TYPE.TypeEnum.MERGE));
		children.add(new WrLeaf(INST.inst_name.key, ci.getName()));
		children.add(newDCP(ci));
		children.add(new WrLeaf(INST.pblock.key, ""));

		return new WrNode(key, children, attributes);
	}

	/**
	 * Create a leaf that represents the cell.
	 * 
	 * @param ci Cell instance to represent.
	 * @return A leaf representing the cell instance using the best guess for the
	 *         dcp from {@link #getCellInstDCP}.
	 */
	private WrLeaf newDCP(EDIFCellInst ci) {
		String dcp = getCellInstDCP(ci);
		if (!ooc_dir.equals("") && dcp.startsWith(ooc_dir + "/")) {
			List<KEY> attributes = Arrays.asList(new WrKey(FILE.loc.key, FILE.LOC.LocEnum.OOC));
			dcp = dcp.substring(ooc_dir.length() + 1);
			return new WrLeaf(INST.dcp.key, attributes, dcp);
		}
		new WrLeaf(INST.dcp.key, dcp);
		return null;
	}

	/**
	 * Try to find the corresponding dcp for this cell instance.
	 * 
	 * @param ci Cell instance to find ooc dcp of.
	 * @return A string representing the best guess for which dcp under ooc_dir
	 *         represents the cell instance.
	 */
	private String getCellInstDCP(EDIFCellInst ci) {
		File ooc_dir = head.fsys().getRoot(FileSys.FILE_ROOT.OOC);
		if (ooc_dir == null || !ooc_dir.isDirectory())
			return "";

		File[] possible_dirs = ooc_dir
				.listFiles(FileTools.getFilenameFilter("(.*)" + ci.getCellType().getName() + "(.*)"));
		Queue<File> possible_dcps = new LinkedList<>();
		for (File dir : possible_dirs) {
			if (!dir.isDirectory())
				continue;
			File[] dir_dcps = dir.listFiles(FileTools.getDCPFilenameFilter());
			if (dir_dcps.length < 1)
				continue;
			possible_dcps.add(dir_dcps[0]);
		}

		String shortest = "";
		int shortest_length = Integer.MAX_VALUE;
		for (File dcp : possible_dcps)
			if (dcp.getAbsolutePath().length() < shortest_length)
				shortest = dcp.getAbsolutePath();
		return shortest;
	}

	/**
	 * Construct a {@literal <header>}.
	 * 
	 * @param ci Root for this block. Cell instance from input_dcp's netlist.
	 * @return Node representing ci's header data.
	 */
	WrNode contructHeader(EDIFCellInst ci) {
		List<TAG> children = new ArrayList<>();
		List<KEY> attributes = new ArrayList<>();
		String key = DirectiveHeader.header.key;

		children.add(new WrLeaf(HEADER.module_name.key, ci.getCellName()));

		return new WrNode(key, children, attributes);
	}

	/**
	 * Construct a {@literal <inst type = "build">}.
	 * 
	 * @param ci Root for this block. Cell instance from input_dcp's netlist.
	 * @return Node representing ci and it's descendants.
	 */
	WrNode constructBuild(EDIFCellInst ci) {
		List<TAG> children = new ArrayList<>();
		List<KEY> attributes = new ArrayList<>();
		String key = Directive.inst.key;

		if (!include_primitives) {
			boolean all_primitive = true;
			for (EDIFCellInst cell_inst : ci.getCellType().getCellInsts()) {
				if (!cell_inst.getCellType().isPrimitive()) {
					all_primitive = false;
					break;
				}
			}

			// if only sub cell instances are primative
			if (all_primitive) {
				attributes.add(new WrKey(INST.type.key, INST.TYPE.TypeEnum.MERGE));
				children.add(new WrLeaf(INST.inst_name.key, ci.getName()));
				children.add(contructHeader(ci));
				children.add(new WrLeaf(INST.only_wires.key));
				return new WrNode(key, children, attributes);
			}
		}

		attributes.add(new WrKey(INST.type.key, INST.TYPE.TypeEnum.BUILD));
		children.add(new WrLeaf(INST.inst_name.key, ci.getName()));
		children.add(contructHeader(ci));

		for (EDIFCellInst cell_inst : ci.getCellType().getCellInsts())
			if (include_primitives || !cell_inst.getCellType().isPrimitive())
				children.add(constructInst(cell_inst));

		return new WrNode(key, children, attributes);
	}

	/**
	 * Construct a {@literal <inst type = "write">}.
	 * 
	 * @return Node representing an empty write.
	 */
	WrNode constructWrite() {
		List<TAG> children = new ArrayList<>();
		List<KEY> attributes = new ArrayList<>();
		String key = Directive.inst.key;

		attributes.add(new WrKey(INST.type.key, INST.TYPE.TypeEnum.WRITE));
		String output_file_name = ((head.getModuleName() == null) ? "default" : head.getModuleName()) + "_out.dcp";
		children.add(new WrLeaf(INST.dcp.key, Arrays.asList(new WrKey(FILE.loc.key, FILE.LOC.LocEnum.OUT)),
				output_file_name));

		return new WrNode(key, children, attributes);
	}

	/**
	 * Node to store and write template data (extends {@link TAG}).
	 */
	public static class WrNode extends TAG {
		public WrNode(String key, List<TAG> children, List<KEY> attributes) {
			super(key, children, attributes);
		}

		public WrNode(String key) {
			super(key);
		}

		/**
		 * Write data from this node and all it's descendants to a file.
		 * 
		 * @param filename File to which data will be written.
		 */
		public void write(String filename) {
			List<String> lines = new ArrayList<>();
			lines.add("<?xml version = \"1.0\"?>");
			toLines(lines, "");
			FileTools.writeLinesToTextFile(lines, filename);
		}

		/**
		 * Convert data to strings and append to lines.
		 * 
		 * @param lines       List of generated lines of xml code.
		 * @param indentation Padding for the front of each line.
		 */
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

	/**
	 * Key value pair (extends {@link KEY}).
	 * <p>
	 * Simplify KEY to one value instead of a set of possible values.
	 */
	public static class WrKey extends KEY {
		public WrKey(String key, BaseEnum value) {
			super(key, new HashSet<>(Arrays.asList(value)));
		}

		/**
		 * @return Key, value pair as an xml formatted string.
		 */
		public String getFirstPairStr() {
			if (values().isEmpty())
				return "";
			String v = values().iterator().next().toString();
			return key + " = \"" + v + "\"";
		}
	}

	/**
	 * Node to store a node with no children, but instead a data string (extends
	 * {@link TAG}).
	 */
	public static class WrLeaf extends WrNode {
		String data = null;

		public WrLeaf(String key) {
			super(key);
		}

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
			if (data == null && attributes().isEmpty()) {
				lines.add(indentation + "<" + key + "/>");
				return;
			}
			StringBuilder sb = new StringBuilder(indentation + "<" + key);
			for (KEY k : attributes())
				sb.append(" " + ((WrKey) k).getFirstPairStr());
			sb.append(">");
			sb.append(data);
			sb.append("</" + key + ">");
			lines.add(sb.toString());
		}
	}

	/**
	 * Parses an xml builder write template instruction {@literal <template>}.
	 * Builds and writes xml build instructions using {@link DirectiveWriter}.
	 */
	public static class TemplateBuilder {
		DirectiveHeader head = null;
		File dcp = null;
		File out = null;
		boolean include_primitives = false;
		boolean force = false;

		/**
		 * Parses an element describing a template build.
		 * <p>
		 * Sets fields of this object to the value from the first found child of the
		 * field's type.
		 * 
		 * @param elem Element to parse.
		 * @param head Header includes data and fsys to resolve files against.
		 */
		TemplateBuilder(Element elem, DirectiveHeader head) {
			this.head = head;

			if (elem == null)
				return;

			include_primitives = XMLParser.getFirstBool(elem, TEMPLATE.include_primitives);
			force = XMLParser.getFirstBool(elem, TEMPLATE.force);
			dcp = Directive.getFirstFile(elem, TEMPLATE.dcp, head.fsys(), true);
			out = Directive.getFirstFile(elem, TEMPLATE.out, head.fsys(), false);
		}

		/**
		 * Write this build template.
		 * <p>
		 * Warning: The generated builder may have filled in fields incorrectly and/or
		 * not inserted values for other fields (particularly dcps and pblocks).
		 * 
		 * @param args_force Force overwrite file.
		 */
		public void writeTemplate(boolean args_force) {
			if (!(args_force || force)) {
				// check for file collision
				if ((out != null) && out.exists()) {
					MessageGenerator.briefError("\nThe output xml for input '" + dcp.getAbsolutePath()
							+ "' would overwrite another file at '" + out.getAbsolutePath() + "'.");

					// if any would overwrite, exit
					MessageGenerator.briefErrorAndExit("Use force (-f) or <force> to overwrite.\nExiting.\n");
				}
			}
			DirectiveWriter.writeTemplate(dcp, include_primitives, out, head);
		}
	}

	/**
	 * Recognized tags for {@link TemplateBuilder} (extends {@link TAG}).
	 */
	public static class TEMPLATE extends TAG {
		public static final FILE dcp = new FILE("dcp");
		public static final FILE out = new FILE("out");
		public static final TAG include_primitives = new TAG("include_primitives");
		public static final TAG force = new TAG("force");

		TEMPLATE() {
			super("template", Arrays.asList(dcp, out, include_primitives, force), Arrays.asList());
		}
	}

	public static TEMPLATE template = new TEMPLATE();
}
