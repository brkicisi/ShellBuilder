package directive;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Map.Entry;

import com.xilinx.rapidwright.util.FileTools;
import com.xilinx.rapidwright.util.MessageGenerator;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import directive.Directive.FILE;
import parser.ArgsContainer;
import parser.XMLParser;
import parser.XMLParser.BaseEnum;
import parser.XMLParser.TAG;
import worker.FileSys;
import worker.Merger;

public class DependancyMeta {
	ArrayDeque<File> dependancies = null;
	FileSys fsys = null;
	boolean verbose = false;
	File synth_1 = null;
	File initial_file = null;

	public static final String META_FILENAME = "metadata.xml";

	public DependancyMeta() {
		dependancies = new ArrayDeque<>();
	}

	public DependancyMeta(File input_file, boolean verbose) {
		this();
		this.verbose = verbose;
		parse(input_file, verbose);
	}

	public void parse(File input_file, boolean verbose) {
		Document doc = XMLParser.parse(input_file);
		parse(doc.getDocumentElement(), verbose);
	}

	public void parse(Element element, boolean verbose) {
		parse(element, verbose, null);
	}

	public void parse(Element element, boolean verbose, FileSys parent_fsys) {
		// parse header
		fsys = new FileSys(verbose);

		Queue<Element> header_children = XMLParser.getChildElementsFromTagName(element, header.key);
		if (!header_children.isEmpty()) {
			Element elem = header_children.peek();

			// set fsys roots
			String text = XMLParser.getFirst(elem, HEADER.iii_dir);
			if (text != null)
				fsys.setDirRoot(FileSys.FILE_ROOT.III, text);
			else if (parent_fsys != null)
				fsys.setDirRoot(FileSys.FILE_ROOT.III, parent_fsys.getRoot(FileSys.FILE_ROOT.III));
			else
				fsys.setDirRoot(FileSys.FILE_ROOT.III, (String) null);

			text = XMLParser.getFirst(elem, HEADER.ooc_dir);
			if (text != null)
				fsys.setDirRoot(FileSys.FILE_ROOT.OOC, text);
			else if (parent_fsys != null)
				fsys.setDirRoot(FileSys.FILE_ROOT.OOC, parent_fsys.getRoot(FileSys.FILE_ROOT.OOC));

			text = XMLParser.getFirst(elem, HEADER.synth_1);
			if (text != null)
				synth_1 = fsys.getExistingFile(text, true);

			text = XMLParser.getFirst(elem, HEADER.initial);
			if (text != null)
				initial_file = fsys.getExistingFile(text, true);
		}

		// parse each directive onto list
		Queue<Element> dep_children = XMLParser.getChildElementsFromTagName(element, dependancy.key);
		for (Element elem : dep_children) {
			String filename = elem.getTextContent();
			BaseEnum attr = FILE.loc.valueOf(elem.getAttribute(FILE.loc.key));
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
			// Incorrect syntax in file being parsed may cause toFile to return null and
			// thus NullPointerException here.
			dependancies.offer(fsys.toFile(filename));
		}
	}

	public static File dcpToMeta(File dcp_file) {
		if (dcp_file == null || !dcp_file.getParentFile().isDirectory())
			return null;
		return new File(dcp_file.getParent(), META_FILENAME);
	}

	public static void writeMeta(File output_dir, Directive directive, ArgsContainer args) {
		boolean verbose = (args == null) ? false : args.verbose();
		if (output_dir == null)
			MessageGenerator.briefErrorAndExit("Can't write metadata inside a null directory.\nExiting.");
		else if (!output_dir.exists())
			FileTools.makeDirs(output_dir.getAbsolutePath());

		File iii_dir = directive.getIII();
		File ooc_dir = directive.getHeader().fsys().getRoot(FileSys.FILE_ROOT.OOC);
		ArrayDeque<File> dependancies = new ArrayDeque<>();

		if (directive.isSubBuilder()) {
			for (Directive dir : directive.getSubBuilder().getDirectives())
				if (!dir.isOnlyWires())
					dependancies.add(Merger.findModuleInCache(dir, args));
		} else
			dependancies.add(directive.getDCP());

		String filename = output_dir.getAbsolutePath() + "/" + META_FILENAME;
		File synth_1 = directive.getHeader().getTopLevelSynth();
		File initial = directive.getHeader().getInitial();
		List<String> lines = toMetaLines(dependancies, iii_dir, ooc_dir, synth_1, initial, filename, verbose);
		FileTools.writeLinesToTextFile(lines, filename);
	}

	public static List<String> toMetaLines(Collection<File> dependancies, File iii_dir, File ooc_dir, File synth_1,
			File initial, String output_filename, boolean verbose) {
		List<String> lines = new ArrayList<>();
		lines.add("<root>");

		if (iii_dir != null || ooc_dir != null) {
			lines.add("\t<" + header.key + ">");
			if (iii_dir != null)
				lines.add("\t\t<" + HEADER.iii_dir.key + ">" + iii_dir.getAbsolutePath() + "</" + HEADER.iii_dir.key
						+ ">");
			if (ooc_dir != null)
				lines.add("\t\t<" + HEADER.ooc_dir.key + ">" + ooc_dir.getAbsolutePath() + "</" + HEADER.ooc_dir.key
						+ ">");
			if (synth_1 != null)
				lines.add("\t\t<" + HEADER.synth_1.key + ">" + synth_1.getAbsolutePath() + "</" + HEADER.synth_1.key
						+ ">");
			if (initial != null)
				lines.add("\t\t<" + HEADER.initial.key + ">" + initial.getAbsolutePath() + "</" + HEADER.initial.key
						+ ">");
			lines.add("\t</" + header.key + ">");
		}
		for (File dep : dependancies) {
			if (dep == null) {
				printIfVerbose("Ignoring attempt to add null file to cache file '"
						+ (output_filename == null ? null : output_filename) + "'.", verbose);
				continue;
			} else if (!dep.isFile())
				printIfVerbose("Adding dependancy which does not exist yet '" + dep.getAbsolutePath() + "'.", verbose);

			String dep_str = dep.getAbsolutePath();
			if (dep_str.startsWith(iii_dir.getAbsolutePath())) {
				dep_str = dep_str.substring(iii_dir.getAbsolutePath().length() + 1);
				lines.add("\t<" + dependancy.key + " " + FILE.loc.key + " = \"" + FILE.LOC.LocEnum.III + "\"" + ">"
						+ dep_str + "</" + dependancy.key + ">");
			} else if (dep_str.startsWith(ooc_dir.getAbsolutePath())) {
				dep_str = dep_str.substring(ooc_dir.getAbsolutePath().length() + 1);
				lines.add("\t<" + dependancy.key + " " + FILE.loc.key + " = \"" + FILE.LOC.LocEnum.OOC + "\"" + ">"
						+ dep_str + "</" + dependancy.key + ">");
			} else {
				lines.add("\t<" + dependancy.key + ">" + dep_str + "</" + dependancy.key + ">");
			}
		}
		lines.add("</root>");
		return lines;
	}

	public Collection<File> getDependancies() {
		return Collections.unmodifiableCollection(dependancies);
	}

	public File getTopLevelSynth() {
		return synth_1;
	}

	public File getInitial() {
		return initial_file;
	}

	private static void printIfVerbose(String msg, boolean verbose) {
		if (verbose)
			MessageGenerator.briefMessage(msg);
	}

	public static class HEADER extends TAG {
		public static final TAG iii_dir = new TAG("iii_dir");
		public static final TAG ooc_dir = new TAG("ooc_dir");
		public static final TAG synth_1 = new TAG("synth");
		public static final TAG initial = new TAG("initial");

		HEADER() {
			super("header", Arrays.asList(iii_dir, ooc_dir), Arrays.asList());
		}
	}

	public static final HEADER header = new HEADER();
	public static final FILE dependancy = new FILE("dependancy");

	/**
	 * Initializes a dependancy set.
	 */
	public DepSet initDepSet() {
		DepSet deps = new DepSet();
		for (File dep : dependancies) {
			String[] path = dep.getAbsolutePath().split("/");
			if (path.length < 1)
				printIfVerbose("The path '" + dep.getAbsolutePath() + "' is not a valid filepath.", verbose);

			String module_name = FileTools.removeFileExtension(path[path.length - 1]);
			if (path.length >= 4 && path[path.length - 4].equals(Merger.MODULE_CACHE))
				deps.put(module_name, path[path.length - 2]);
			else
				deps.put(module_name, "");
		}

		return deps;
	}

	public static class DepSet {
		private Map<String, Collection<String>> map = null;

		public DepSet() {
			map = new HashMap<>();
		}

		public DepSet(DepSet ds) {
			map = new HashMap<>();
			for (Entry<String, Collection<String>> e : ds.map.entrySet())
				this.map.put(e.getKey(), new HashSet<>(e.getValue()));
		}

		public void put(String key, String value) {
			if (!map.containsKey(key))
				map.put(key, new HashSet<>());
			map.get(key).add(value);
		}

		public boolean remove(String key, String value) {
			if (map.get(key) != null) {
				boolean ret = map.get(key).remove(value);
				if (map.get(key).isEmpty())
					map.remove(key);
				return ret;
			}
			return false;
		}

		public boolean containsKey(String key) {
			return map.containsKey(key);
		}

		public boolean contains(String key, String value) {
			return map.containsKey(key) && map.get(key).contains(value);
		}

		public boolean isEmpty() {
			return map.isEmpty();
		}
	}
}
