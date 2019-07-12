package parser;

import java.io.File;
import java.util.LinkedList;
import java.util.Queue;

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
}
