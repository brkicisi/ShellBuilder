package main.directive;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import com.xilinx.rapidwright.util.MessageGenerator;

import org.w3c.dom.Element;

import main.parser.XMLParser;
import main.parser.XMLParser.BaseEnum;
import main.parser.XMLParser.TAG;
import main.parser.XMLParser.KEY;
import main.worker.FileSys;

/**
 * Describes an instruction regarding how to construct the design.
 * <p>
 * {@link INST.TYPE Types}: merge, build, write.
 */
public class Directive {
	/**
	 * Header from this {@link Directive directive}'s parent (ie. header which is
	 * sibling to this inst)
	 */
	DirectiveHeader head = null;
	BaseEnum type = null;
	String inst_name = null;
	File dcp = null;
	String pblock_str = null;
	boolean force = false;
	boolean hand_placer = false;
	boolean refresh = false;
	boolean only_wires = false;
	DirectiveBuilder sub_builder = null;

	/**
	 * @return Header from this directive's parent (ie. header which is sibling to
	 *         this inst)
	 */
	public DirectiveHeader getHeader() {
		return head;
	}

	public File getIII() {
		return head.fsys().getRoot(FileSys.FILE_ROOT.III);
	}

	public BaseEnum getType() {
		return type;
	}

	public String getPBlockStr() {
		return pblock_str;
	}

	public String getInstName() {
		return inst_name;
	}

	public File getDCP() {
		return dcp;
	}

	public DirectiveBuilder getSubBuilder() {
		return sub_builder;
	}

	public boolean isMerge() {
		return (type == INST.TYPE.TypeEnum.MERGE);
	}

	public boolean isWrite() {
		return (type == INST.TYPE.TypeEnum.WRITE);
	}

	public boolean isSubBuilder() {
		return (type == INST.TYPE.TypeEnum.BUILD);
	}

	public boolean isForce() {
		return force;
	}

	public boolean isRefresh() {
		return refresh || head.isRefresh();
	}

	public boolean isOnlyWires() {
		return only_wires;
	}

	public boolean isHandPlacer() {
		return hand_placer || head.isHandPlacer();
	}

	public void setDCP(File new_dcp) {
		dcp = new_dcp;
	}

	/**
	 * Parses an element describing a directive.
	 * <p>
	 * Sets fields of this object to the value from the first found child of the field's
	 * type.
	 * 
	 * @param elem Element to parse.
	 * @param head Header includes data and fsys to resolve files against.
	 */
	public Directive(Element elem, DirectiveHeader head) {
		this.head = head;

		if (elem == null)
			return;

		// Determine what 'type' of inst
		// <inst type="value">
		String inst_type_str = elem.getAttribute(INST.type.key);
		type = INST.type.valueOf(inst_type_str);
		if (type == null)
			MessageGenerator.briefErrorAndExit(
					"'type = \"" + inst_type_str + "\"' is not a valid attribute for 'inst'.\nExiting.");

		pblock_str = XMLParser.getFirst(elem, INST.pblock);
		inst_name = XMLParser.getFirst(elem, INST.inst_name);
		force = XMLParser.getFirstBool(elem, INST.force);
		hand_placer = XMLParser.getFirstBool(elem, INST.hand_placer);
		refresh = XMLParser.getFirstBool(elem, INST.refresh);
		only_wires = XMLParser.getFirstBool(elem, INST.only_wires);

		// File must exist if it a merge and not an only wires cell
		boolean err_if_not_found = !only_wires && (type == INST.TYPE.TypeEnum.MERGE);
		dcp = getFirstFile(elem, INST.dcp, head.fsys(), err_if_not_found);

		if (type == INST.TYPE.TypeEnum.BUILD) {
			sub_builder = new DirectiveBuilder();
			sub_builder.parse(elem, head.isVerbose(), head);
		}
	}

	/**
	 * Return file described by first child of elem with tag t.
	 * <p>
	 * File is described by the text content and optionally loc attribute of the
	 * instance of tag t.
	 * 
	 * @param elem             Search children of this element.
	 * @param t                Tag to search for.
	 * @param fsys             FileSys to resolve file against.
	 * @param err_if_not_found Must this file already exist? Usually true for input
	 *                         files, false for output files.
	 * @return File found under elem with tag t. Null if no instances of t were
	 *         found.
	 */
	public static File getFirstFile(Element elem, FILE t, FileSys fsys, boolean err_if_not_found) {
		String filename = XMLParser.getFirst(elem, t);
		if (filename == null)
			return null;
		BaseEnum attr = XMLParser.getFirstAttr(elem, t, FILE.loc);
		if (attr != null) {
			if (attr == FILE.LOC.LocEnum.III)
				filename = FileSys.FILE_ROOT.III.subsumePath(filename);
			else if (attr == FILE.LOC.LocEnum.OOC)
				filename = FileSys.FILE_ROOT.OOC.subsumePath(filename);
			else if (attr == FILE.LOC.LocEnum.OUT)
				filename = FileSys.FILE_ROOT.OUT.subsumePath(filename);
			else
				MessageGenerator.briefErrorAndExit("Unrecognized " + FILE.loc.key + " '" + attr + "'.");
		}
		if (err_if_not_found)
			return fsys.getExistingFile(filename, err_if_not_found);
		return fsys.toFile(filename);
	}

	/**
	 * Tag describing a file (extends {@link TAG}).
	 * <p>
	 * Provides support for the optional attribute loc to indicate iii, ooc, or out.
	 * Attribute {@link LOC loc} should be used in conjunction with the sibling
	 * {@link DirectiveHeader#fsys()} to determine an absolute location of the file.
	 */
	public static class FILE extends TAG {
		/**
		 * Location attribute (extends {@link TAG}).
		 * <p>
		 * Parallels {@link FileSys.FILE_ROOT}
		 */
		public static class LOC extends KEY {
			public enum LocEnum implements BaseEnum {
				III("iii"), OOC("ooc"), OUT("out");
				final String tag_str;

				LocEnum(String tag_str) {
					this.tag_str = tag_str;
				}

				@Override
				public String toString() {
					return tag_str;
				}
			}

			LOC() {
				super("loc", new HashSet<>(Arrays.asList(LocEnum.III, LocEnum.OOC, LocEnum.OUT)));
			}
		}

		public static final LOC loc = new LOC();

		FILE(String tag) {
			super(tag, Arrays.asList(), Arrays.asList(loc));
		}
	}

	/**
	 * Recognized tags for {@link Directive} inst (extends {@link TAG}).
	 */
	public static class INST extends TAG {
		/**
		 * Attribute describing type of instruction (extends {@link KEY}).
		 */
		public static class TYPE extends KEY {
			public enum TypeEnum implements BaseEnum {
				MERGE("merge"), WRITE("write"), BUILD("build");
				final String tag_str;

				TypeEnum(String tag_str) {
					this.tag_str = tag_str;
				}

				@Override
				public String toString() {
					return tag_str;
				}
			}

			TYPE() {
				super("type", new HashSet<>(Arrays.asList(TypeEnum.MERGE, TypeEnum.WRITE, TypeEnum.BUILD)));
			}
		}

		public static final FILE dcp = new FILE("dcp");
		public static final TAG pblock = new TAG("pblock");
		public static final TAG inst_name = new TAG("iname");
		public static final TAG force = new TAG("force");
		public static final TAG hand_placer = new TAG("hand_placer");
		public static final TAG refresh = new TAG("refresh");
		public static final TAG only_wires = new TAG("only_wires");
		public static final TYPE type = new TYPE();

		INST() {
			super("inst", new ArrayList<TAG>(Arrays.asList(dcp, pblock, inst_name, force, hand_placer, refresh)),
					new ArrayList<KEY>(Arrays.asList(type)));
		}
	}

	public static final INST inst = new INST();
}
