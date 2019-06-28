package worker;

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
import org.w3c.dom.Node;

public class Directive {
	private DirectiveHeader head = null;
	BaseEnum type = null;
	File dcp = null;
	String pblock_str = null;
	boolean force = false;
	// int order = -1;

	public DirectiveHeader getHeader() {
		return head;
	}

	public BaseEnum getType() {
		return type;
	}

	public String getPBlockStr() {
		return pblock_str;
	}

	public File getDCP() {
		return dcp;
	}

	public boolean isMerge() {
		return (type == INST.TYPE.TypeEnum.merge);
	}

	public boolean isWrite() {
		return (type == INST.TYPE.TypeEnum.write);
	}

	public boolean isForce() {
		return force;
	}

	/**
	 * Parses an element describing an <inst>.
	 * 
	 * @param elem Element to parse
	 */
	public Directive(Element elem, DirectiveHeader head) {
		this.head = head;
		// determine what 'type' of inst
		// <inst type="value">
		String inst_type_str = elem.getAttribute(INST.type.key);
		type = INST.type.valueOf(inst_type_str);
		if (type == null)
			MessageGenerator.briefErrorAndExit("'type = \"" + inst_type_str + "\"' is not a valid attribute for 'inst'.\nExiting.");

		// file must exist if not tagged write (ie is an output file)
		dcp = getDCPFile(elem, head.fsys(), type != INST.TYPE.TypeEnum.write);
		pblock_str = getFirst(elem, INST.pblock);
		force = getFirstBool(elem, INST.force);
		// order = getFirstInt(elem, INST.order); // example of how to include an int
		// from xml
	}

	static String getFirst(Element elem, TAG t) {
		String str = null;
		try {
			str = elem.getElementsByTagName(t.key).item(0).getTextContent();
		} catch (NullPointerException e) {
			str = null;
		}
		return str;
	}

	static boolean getFirstBool(Element elem, TAG t) {
		return (elem.getElementsByTagName(t.key).item(0) != null);
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

	static File getDCPFile(Element elem, FileSys fsys, boolean err_if_not_found) {
		String filename = getFirst(elem, INST.dcp);
		BaseEnum attr = getFirstAttr(elem, INST.dcp, INST.DCP.loc);
		if (attr != null) {
			if (attr == INST.DCP.LOC.LocEnum.III)
				filename = FileSys.FILE_ROOT.III.subsumePath(filename);
			else if (attr == INST.DCP.LOC.LocEnum.OOC)
				filename = FileSys.FILE_ROOT.OOC.subsumePath(filename);
			else if (attr == INST.DCP.LOC.LocEnum.OUT)
				filename = FileSys.FILE_ROOT.OUT.subsumePath(filename);
			else
				MessageGenerator.briefErrorAndExit("Unrecognized " + INST.DCP.loc.key + " '" + attr + "'.");
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
			Node node = elem.getElementsByTagName(t.key).item(0);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				Element elem2 = (Element) node;
				return k.valueOf(elem2.getAttribute(k.key));
			}
		} catch (NullPointerException e) {
		}
		return null;
	}

	static abstract class TAG {
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

	static class HEADER extends TAG {
		static class III_DIR extends TAG {
			III_DIR() {
				super("iii_dir");
			}
		}

		static class OOC_DIR extends TAG {
			OOC_DIR() {
				super("ooc_dir");
			}
		}

		static class OUT_DIR extends TAG {
			OUT_DIR() {
				super("out_dir");
			}
		}

		static final III_DIR iii_dir = new III_DIR();
		static final OOC_DIR ooc_dir = new OOC_DIR();
		static final OUT_DIR out_dir = new OUT_DIR();

		HEADER() {
			super("header");
		}
	}

	static class INST extends TAG {
		static class DCP extends TAG {
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

			DCP() {
				super("dcp");
			}
		}

		static class PBLOCK extends TAG {
			PBLOCK() {
				super("pblock");
			}
		}

		static class FORCE extends TAG {
			FORCE() {
				super("force");
			}
		}

		// static class ORDER extends TAG {
		// ORDER() {
		// super("order");
		// }
		// }

		static class TYPE extends KEY {
			private enum TypeEnum implements BaseEnum {
				merge("merge"), write("write");
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
				super("type", new HashSet<>(Arrays.asList(TypeEnum.merge, TypeEnum.write)));
			}
		}

		static final DCP dcp = new DCP();
		static final PBLOCK pblock = new PBLOCK();
		static final TYPE type = new TYPE();
		static final FORCE force = new FORCE();
		// static final ORDER order = new ORDER();

		INST() {
			// super("inst", new ArrayList<TAG>(Arrays.asList(order, dcp, pblock)), new
			// ArrayList<KEY>(Arrays.asList(type)));
			super("inst", new ArrayList<TAG>(Arrays.asList(dcp, pblock)), new ArrayList<KEY>(Arrays.asList(type)));
		}
	}

	static final HEADER header = new HEADER();
	static final INST inst = new INST();
}
