package directive;

import parser.ArgsContainer;
import parser.XMLParser;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Queue;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class DirectiveBuilder {
	private ArrayDeque<Directive> directives = null;
	private DirectiveHeader head = null;

	public DirectiveBuilder() {
		directives = new ArrayDeque<>();
	}

	public DirectiveBuilder(File input_file, boolean verbose) {
		this();
		parse(input_file, verbose);
	}

	public void parse(File input_file, boolean verbose) {
		Document doc = XMLParser.parse(input_file);
		parse(doc.getDocumentElement(), verbose);
	}

	public void parse(Element element, boolean verbose) {
		parse(element, verbose, null);
	}

	public void parse(Element element, boolean verbose, DirectiveHeader parent_head) {
		// parse header
		head = new DirectiveHeader(parent_head, verbose);

		Queue<Element> header_children = XMLParser.getChildElementsFromTagName(element, Directive.header.key);
		if(!header_children.isEmpty())
			head.addHeader(header_children.peek());

		// parse each directive onto list
		Queue<Element> inst_children = XMLParser.getChildElementsFromTagName(element, Directive.inst.key);
		for(Element elem : inst_children)
			directives.offer(new Directive(elem, head));
	}

	public Collection<Directive> getDirectives() {
		return Collections.unmodifiableCollection(directives);
	}

	public DirectiveHeader getHeader() {
		return head;
	}

	// public static DirectiveBuilder makeWrapperBuilder(File dcp, ArgsContainer args){
	// 	DirectiveBuilder db = new DirectiveBuilder();
	// 	DirectiveHeader head = new DirectiveHeader(args.verbose());
	// 	// head.
	// 	Directive dir = new Directive(null, head);
	// }

	// static Directive makeMergeDirective() {
		
	// 	dir.type = INST.TYPE.TypeEnum.MERGE;
	// 	dir.dcp = dcp;

	// 	pblock_str = getFirst(elem, INST.pblock);
	// 	inst_name = getFirst(elem, INST.inst_name);
	// 	force = getFirstBool(elem, INST.force);
	// 	hand_placer = getFirstBool(elem, INST.hand_placer);
	// 	refresh = getFirstBool(elem, INST.refresh);
	// 	only_wires = getFirstBool(elem, INST.only_wires);
	// }
}