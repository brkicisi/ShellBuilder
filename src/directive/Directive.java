package directive;

import java.io.File;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.xilinx.rapidwright.util.MessageGenerator;

import org.w3c.dom.Element;

import parser.XMLParser;
import worker.FileSys;

public class Directive {
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
	 * Parses an element describing an <inst>.
	 * 
	 * @param elem Element to parse
	 */
	public Directive(Element elem, DirectiveHeader head) {
		this.head = head;
		
		if (elem == null)
			return;

		// determine what 'type' of inst
		// <inst type="value">
		String inst_type_str = elem.getAttribute(INST.type.key);
		type = INST.type.valueOf(inst_type_str);
		if (type == null)
			MessageGenerator.briefErrorAndExit(
					"'type = \"" + inst_type_str + "\"' is not a valid attribute for 'inst'.\nExiting.");

		pblock_str = getFirst(elem, INST.pblock);
		inst_name = getFirst(elem, INST.inst_name);
		force = getFirstBool(elem, INST.force);
		hand_placer = getFirstBool(elem, INST.hand_placer);
		refresh = getFirstBool(elem, INST.refresh);
		only_wires = getFirstBool(elem, INST.only_wires);
		// file must exist if it a merge and not an only wires cell
		boolean err_if_not_found = !only_wires && (type == INST.TYPE.TypeEnum.MERGE);
		dcp = getFirstFile(elem, INST.dcp, head.fsys(), err_if_not_found);

		if (type == INST.TYPE.TypeEnum.BUILD) {
			sub_builder = new DirectiveBuilder();
			sub_builder.parse(elem, head.isVerbose(), head);
		}
	}

	static String getFirst(Element elem, TAG t) {
		String str = null;
		try {
			// str = elem.getElementsByTagName(t.key).item(0).getTextContent();
			str = XMLParser.getChildElementsFromTagName(elem, t.key).peek().getTextContent();
		} catch (NullPointerException e) {
			str = null;
		}
		return str;
	}

	static boolean getFirstBool(Element elem, TAG t) {
		// return (elem.getElementsByTagName(t.key).item(0) != null);
		return (XMLParser.getChildElementsFromTagName(elem, t.key).peek() != null);
	}

	// static int getFirstInt(Element elem, TAG t) {
	// int num = -1;
	// String str = getFirst(elem, t);
	// try {
	// num = Integer.parseInt(str);
	// } catch (NumberFormatException e) {
	// num = -1;
	// }
	// return num;
	// }

	static File getFirstFile(Element elem, TAG t, FileSys fsys, boolean err_if_not_found) {
		String filename = getFirst(elem, t);
		BaseEnum attr = getFirstAttr(elem, t, FILE.loc);
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
	 * Gets an attribute with key=k of the first node with tag=t.
	 * 
	 * @param elem Parent to search for tag under.
	 * @param t    Tag to search for. Use first found instance.
	 * @param k    Key of attribute to check under node with tag t.
	 * @return String found representing attribute k of node with type t. Null if
	 *         can't find.
	 */
	static BaseEnum getFirstAttr(Element elem, TAG t, KEY k) {
		if (k == null)
			return null;
		try {
			// Node node = elem.getElementsByTagName(t.key).item(0);
			// if (node.getNodeType() == Node.ELEMENT_NODE) {
			// Element elem2 = (Element) node;
			// return k.valueOf(elem2.getAttribute(k.key));
			// }
			Element elem2 = XMLParser.getChildElementsFromTagName(elem, t.key).peek();
			return k.valueOf(elem2.getAttribute(k.key));
		} catch (NullPointerException e) {
		}
		return null;
	}

	static class TAG {
		final String key;
		private final List<TAG> children;
		private final List<KEY> attributes;

		TAG(String key) {
			this.key = key;
			children = new ArrayList<>();
			attributes = new ArrayList<>();
		}

		TAG(String key, List<TAG> children, List<KEY> attributes) {
			this.key = key;
			this.children = children;
			this.attributes = attributes;
		}

		public List<TAG> children() {
			return Collections.unmodifiableList(children);
		}

		public List<KEY> attributes() {
			return Collections.unmodifiableList(attributes);
		}
	}

	public static interface BaseEnum {
		String getStr();
	}

	static abstract class KEY {
		final String key;
		private Map<String, BaseEnum> inverse_map;
		private Set<BaseEnum> values;

		KEY(String key, Set<BaseEnum> values) {
			this.key = key;
			this.values = values;
			inverse_map = new HashMap<>();
			for (BaseEnum v : values)
				inverse_map.put(v.getStr(), v);
		}

		Set<BaseEnum> values() {
			return Collections.unmodifiableSet(values);
		}

		boolean isValid(String value) {
			if (value == null)
				return false;
			return inverse_map.containsKey(value);
		}

		BaseEnum valueOf(String value) {
			if (value == null)
				return null;
			return inverse_map.get(value);
		}
	}

	static class FILE extends TAG {
		static class LOC extends KEY {
			enum LocEnum implements BaseEnum {
				III("iii"), OOC("ooc"), OUT("out");
				final String tag_str;

				LocEnum(String tag_str) {
					this.tag_str = tag_str;
				}

				@Override
				public String getStr() {
					return tag_str;
				}
			}

			LOC() {
				super("loc", new HashSet<>(Arrays.asList(LocEnum.III, LocEnum.OOC, LocEnum.OUT)));
			}
		}

		static final LOC loc = new LOC();

		FILE(String tag) {
			super(tag, Arrays.asList(), Arrays.asList(loc));
		}
	}

	static class HEADER extends TAG {
		static final TAG iii_dir = new TAG("iii_dir");
		static final TAG ooc_dir = new TAG("ooc_dir");
		static final TAG out_dir = new TAG("out_dir");
		static final FILE initial = new FILE("initial");
		static final FILE synth = new FILE("synth");
		static final TAG name = new TAG("name");
		static final TAG refresh = new TAG("refresh");
		static final TAG hand_placer = new TAG("hand_placer");
		static final TAG buffer_inputs = new TAG("buffer_inputs");

		HEADER() {
			super("header", Arrays.asList(iii_dir, ooc_dir, out_dir, initial, synth, name, refresh, buffer_inputs),
					Arrays.asList());
		}
	}

	static class INST extends TAG {
		static class TYPE extends KEY {
			private enum TypeEnum implements BaseEnum {
				MERGE("merge"), WRITE("write"), BUILD("build");
				final String tag_str;

				TypeEnum(String tag_str) {
					this.tag_str = tag_str;
				}

				@Override
				public String getStr() {
					return tag_str;
				}
			}

			TYPE() {
				super("type", new HashSet<>(Arrays.asList(TypeEnum.MERGE, TypeEnum.WRITE, TypeEnum.BUILD)));
			}
		}

		static final FILE dcp = new FILE("dcp");
		static final TAG pblock = new TAG("pblock");
		static final TAG inst_name = new TAG("name");
		static final TAG force = new TAG("force");
		static final TAG hand_placer = new TAG("hand_placer");
		static final TAG refresh = new TAG("refresh");
		static final TAG only_wires = new TAG("only_wires");
		static final TYPE type = new TYPE();

		INST() {
			super("inst", new ArrayList<TAG>(Arrays.asList(dcp, pblock, inst_name, force, hand_placer, refresh)),
					new ArrayList<KEY>(Arrays.asList(type)));
		}
	}

	static final HEADER header = new HEADER();
	static final INST inst = new INST();
}
