package main.parser;

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

/**
 * Wrapper for {@link org.w3c.dom} xml parser.
 * <p>
 * Provides convenience methods for working with org.w3c.dom Elements.
 */
public class XMLParser {
	/**
	 * Returns a {@link org.w3c.dom.Document} containing a parsed XML file.
	 * 
	 * @param input_file File to parse.
	 * @return Document representing whole file.
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

	/**
	 * Find only direct children of element rather than all descendants with
	 * 
	 * @param top_element Element whose direct children will be searched.
	 * @param tag_name    Tag name to search for.
	 * @return Queue of direct children with specified tag.
	 */
	public static Queue<Element> getChildElementsFromTagName(Element top_element, String tag_name) {
		Queue<Element> children = new LinkedList<>();
		Node node = top_element.getFirstChild();
		if (node == null)
			return children;

		do {
			if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals(tag_name))
				children.add((Element) node);

			node = node.getNextSibling();
		} while (node != null && node != top_element.getLastChild());
		return children;
	}

	/**
	 * Find text from first direct child of elem found with tag t.
	 * 
	 * @param elem Element whose direct children will be searched.
	 * @param t    Tag to search for.
	 * @return Text context from child with tag t.
	 */
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

	/**
	 * Find if any direct children of elem have tag t.
	 * 
	 * @param elem Element whose direct children will be searched.
	 * @param t    Tag to search for.
	 * @return True if elem has one or more direct children with tag t.
	 */
	public static boolean getFirstBool(Element elem, TAG t) {
		return (XMLParser.getChildElementsFromTagName(elem, t.key).peek() != null);
	}

	/**
	 * Find text from first direct child of elem found with tag t and try converting
	 * it to an int.
	 * 
	 * @param elem Element whose direct children will be searched.
	 * @param t    Tag to search for.
	 * @return int from child with tag t or Integer.MIN_VALUE if parse as Integer
	 *         fails.
	 */
	public static int getFirstInt(Element elem, TAG t) {
		int num = Integer.MIN_VALUE;
		String str = getFirst(elem, t);
		try {
			num = Integer.parseInt(str);
		} catch (NumberFormatException e) {
			num = Integer.MIN_VALUE;
		}
		return num;
	}

	/**
	 * Gets the first attribute with key k of the first direct child of elem with
	 * tag t.
	 * 
	 * @param elem Element whose direct children will be searched.
	 * @param t    Tag to search for.
	 * @param k    Key of attribute to find under node with tag t.
	 * @return Enum instance representing attribute k of node with tag t. Null if
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

	/**
	 * Base class to represent a generic node.
	 * <p>
	 * It has a tag key, it can have children TAGs and it can have attributes
	 * ({@link KEY}).
	 * <p>
	 * It cannot be modified once constructed.
	 */
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

	/**
	 * Provide a common base for enums to fascilitate their generic use.
	 */
	public static interface BaseEnum {
		@Override
		String toString();
	}

	/**
	 * Base class to represent a generic attribute.
	 * <p>
	 * It has a tag key, and a set of valid values ({@link BaseEnum}).
	 * <p>
	 * It cannot be modified once constructed.
	 */
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

		/**
		 * @param value String to try to map to a {@link BaseEnum}.
		 * @return True if value maps to one of the valid BaseEnum values.
		 */
		public boolean isValid(String value) {
			if (value == null)
				return false;
			return inverse_map.containsKey(value);
		}

		/**
		 * @param value String to try to map to a {@link BaseEnum}.
		 * @return BaseEnum mapped to by value or null if it doesn't map to any of the
		 *         valid BaseEnum values.
		 */
		public BaseEnum valueOf(String value) {
			if (value == null)
				return null;
			return inverse_map.get(value);
		}
	}
}
