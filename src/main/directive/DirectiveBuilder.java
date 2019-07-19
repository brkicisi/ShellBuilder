package main.directive;

import main.parser.XMLParser;
import main.directive.DirectiveWriter.TemplateBuilder;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Queue;

import org.w3c.dom.Document;
import org.w3c.dom.Element;



/**
 * Wrapper to parse and store a set of sibling {@link Directive direcitves} and
 * a {@link DirectiveHeader header}.
 * <p>
 * Also stores a list of {@link TemplateBuilder TemplateBuilders} when parsing.
 */
public class DirectiveBuilder {
	private ArrayDeque<Directive> directives = null;
	private ArrayDeque<TemplateBuilder> template_builders = null;
	private DirectiveHeader head = null;

	public DirectiveBuilder() {
		directives = new ArrayDeque<>();
		template_builders = new ArrayDeque<>();
	}

	/**
	 * Initializes builder and {@link #parse(Element, boolean) parses} file.
	 * 
	 * @param input_file File to parse.
	 * @param verbose    Print extra messages.
	 */
	public DirectiveBuilder(File input_file, boolean verbose) {
		this();
		parse(input_file, verbose);
	}

	/**
	 * Parse the input xml build file and appends directives and template builders
	 * in this object.
	 * <p>
	 * If calling this multiple times, be aware that lists are not cleared between
	 * parses but header is replaced which may cause errors. You probably want
	 * multiple {@link DirectiveBuilder} objects instead.
	 * 
	 * @param input_file XML build file to parse.
	 * @param verbose    Print extra messages.
	 */
	public void parse(File input_file, boolean verbose) {
		Document doc = XMLParser.parse(input_file);
		parse(doc.getDocumentElement(), verbose);
	}

	/**
	 * Parse the input xml build file and appends directives and template builders
	 * in this object.
	 * <p>
	 * If calling this multiple times, be aware that lists are not cleared between
	 * parses but header is replaced which may cause errors. You probably want
	 * multiple {@link DirectiveBuilder} objects instead.
	 * 
	 * @param element Root element containing {@link DirectiveHeader header} and/or
	 *                {@link Directive directives} and/or {@link TemplateBuilder
	 *                template builders} to parse.
	 * @param verbose Print extra messages.
	 */
	public void parse(Element element, boolean verbose) {
		parse(element, verbose, null);
	}

	/**
	 * Parse the input xml build file and appends directives and template builders
	 * in this object.
	 * <p>
	 * If calling this multiple times, be aware that lists are not cleared between
	 * parses but header is replaced which may cause errors. You probably want
	 * multiple {@link DirectiveBuilder} objects instead.
	 * 
	 * @param element     Root element containing {@link DirectiveHeader header}
	 *                    and/or {@link Directive directives} and/or
	 *                    {@link TemplateBuilder template builders} to parse.
	 * @param verbose     Print extra messages.
	 * @param parent_head Sibling header to parent of element.
	 */
	public void parse(Element element, boolean verbose, DirectiveHeader parent_head) {
		// Parse one header
		head = new DirectiveHeader(parent_head, verbose);
		Queue<Element> header_children = XMLParser.getChildElementsFromTagName(element, DirectiveHeader.header.key);
		if (!header_children.isEmpty())
			head.addHeader(header_children.peek());

		// Parse each directive onto list
		Queue<Element> inst_children = XMLParser.getChildElementsFromTagName(element, Directive.inst.key);
		for (Element elem : inst_children)
			directives.offer(new Directive(elem, head));

		// Parse each directive onto list
		Queue<Element> template_children = XMLParser.getChildElementsFromTagName(element, DirectiveWriter.template.key);
		for (Element elem : template_children)
			template_builders.offer(new TemplateBuilder(elem, head));
	}

	public Collection<Directive> getDirectives() {
		return Collections.unmodifiableCollection(directives);
	}

	public DirectiveHeader getHeader() {
		return head;
	}

	public Collection<TemplateBuilder> getTemplateBuilders() {
		return Collections.unmodifiableCollection(template_builders);
	}
}