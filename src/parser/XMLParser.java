package parser;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import com.xilinx.rapidwright.util.MessageGenerator;

import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class XMLParser {
	/**
	 * Returns a 'org.w3c.dom.Document' containing a parsed XML file.
	 * 
	 * @param input_file File to parse
	 */
	public static Document parse(File input_file) {
		if (input_file == null)
			MessageGenerator.briefErrorAndExit("Null file input to xml parser.\nExiting.\n");
		if (!input_file.isFile())
			MessageGenerator.briefErrorAndExit(
					"The input to xml parser was not a file.\n'" + input_file.getAbsolutePath() + "'\nExiting.\n");

		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(input_file);
			doc.getDocumentElement().normalize();
			return doc;
		} catch (Exception e) {
			MessageGenerator.briefError("Error parsing xml file '" + input_file.getAbsolutePath() + "'");
			e.printStackTrace();
		}
		return null;
	}

	public static Queue<Element> getChildElementsFromTagName(Element top_element, String name) {
		Queue<Element> children = new LinkedList<>();
		Node node = top_element.getFirstChild();
		if (node == null)
			return children;

		do {
			if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals(name))
				children.add((Element) node);

			node = node.getNextSibling();
		} while (node != null && node != top_element.getLastChild());
		return children;
	}

	public static String getFirst(Element elem, TAG t) {
		String str = null;
		try {
			// str = elem.getElementsByTagName(t.key).item(0).getTextContent();
			str = XMLParser.getChildElementsFromTagName(elem, t.key).peek().getTextContent();
		} catch (NullPointerException e) {
			str = null;
		}
		return str;
	}

	public static boolean getFirstBool(Element elem, TAG t) {
		// return (elem.getElementsByTagName(t.key).item(0) != null);
		return (XMLParser.getChildElementsFromTagName(elem, t.key).peek() != null);
	}

	public static int getFirstInt(Element elem, TAG t) {
		int num = -1;
		String str = getFirst(elem, t);
		try {
			num = Integer.parseInt(str);
		} catch (NumberFormatException e) {
			num = -1;
		}
		return num;
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
	public static BaseEnum getFirstAttr(Element elem, TAG t, KEY k) {
		if (k == null)
			return null;
		try {
			Element elem2 = XMLParser.getChildElementsFromTagName(elem, t.key).peek();
			return k.valueOf(elem2.getAttribute(k.key));
		} catch (NullPointerException e) {
		}
		return null;
	}

	public static class TAG {
		public final String key;
		private final List<TAG> children;
		private final List<KEY> attributes;

		public TAG(String key) {
			this.key = key;
			children = new ArrayList<>();
			attributes = new ArrayList<>();
		}

		public TAG(String key, List<TAG> children, List<KEY> attributes) {
			this.key = key;
			this.children = (children == null) ? new ArrayList<>() : children;
			this.attributes = (attributes == null) ? new ArrayList<>() : attributes;
		}

		public List<TAG> children() {
			return Collections.unmodifiableList(children);
		}

		public List<KEY> attributes() {
			return Collections.unmodifiableList(attributes);
		}
	}

	public static interface BaseEnum {
		@Override
		String toString();
	}

	public static class KEY {
		public final String key;
		private Map<String, BaseEnum> inverse_map;
		private Set<BaseEnum> values;

		public KEY(String key, Set<BaseEnum> values) {
			this.key = key;
			this.values = values;
			inverse_map = new HashMap<>();
			for (BaseEnum v : values)
				inverse_map.put(v.toString(), v);
		}

		public Set<BaseEnum> values() {
			return Collections.unmodifiableSet(values);
		}

		public boolean isValid(String value) {
			if (value == null)
				return false;
			return inverse_map.containsKey(value);
		}

		public BaseEnum valueOf(String value) {
			if (value == null)
				return null;
			return inverse_map.get(value);
		}
	}
}
