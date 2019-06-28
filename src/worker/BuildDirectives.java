package worker;

import parser.XMLParser;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;

public class BuildDirectives {
	private ArrayDeque<Directive> directives = null;
	private DirectiveHeader head = null;

	
	public BuildDirectives() {
		directives = new ArrayDeque<>();
	}

	public BuildDirectives(File input_file, boolean verbose) {
		this();
		parse(input_file, verbose);
	}

	public void parse(File input_file, boolean verbose) {
		Document doc = XMLParser.parse(input_file);

		// parse header
		head = new DirectiveHeader(verbose);
		NodeList header_nodes = doc.getElementsByTagName(Directive.header.key);
		for (int i = 0; i < header_nodes.getLength(); i++) {
			Node node = header_nodes.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				Element elem = (Element) node;
				// this elem is of tag header. ie elem.getElementsByTagName(key) will return the
				// tags inside <header>This stuff</header>
				head.addHeader(elem);
				break; // Only use first header
			}
			// ignore all other types of nodes
		}

		// parse each directive onto list
		NodeList inst_nodes = doc.getElementsByTagName(Directive.inst.key);
		for (int i = 0; i < inst_nodes.getLength(); i++) {
			Node node = inst_nodes.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				Element elem = (Element) node;
				directives.offer(new Directive(elem, head));
			}
			// ignore all other types of nodes
		}

	}

	public Collection<Directive> getDirectives() {
		return Collections.unmodifiableCollection(directives);
	}
}