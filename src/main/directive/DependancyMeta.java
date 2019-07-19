package main.directive;

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

import main.directive.Directive.FILE;
import main.parser.ArgsContainer;
import main.parser.XMLParser;
import main.parser.XMLParser.BaseEnum;
import main.parser.XMLParser.TAG;
import main.worker.FileSys;
import main.worker.Merger;

/**
 * Provides support for reading and writing metadata dependancy files for the
 * cache.
 */
public class DependancyMeta {
	ArrayDeque<File> dependancies = null;
	FileSys fsys = null;
	boolean verbose = false;
	File synth_1 = null;
	File initial_file = null;

	/**
	 * Default name for metadata files.
	 */
	public static final String META_FILENAME = "metadata.xml";

	public DependancyMeta(boolean verbose) {
		dependancies = new ArrayDeque<>();
		this.verbose = verbose;
	}

	/**
	 * Create a new dependancy file reader and {@link #parse} this file.
	 * 
	 * @param input_file Depencancy metadata file to parse.
	 * @param verbose    Print extra messages.
	 */
	public DependancyMeta(File input_file, boolean verbose) {
		this(verbose);
		parse(input_file, verbose);
	}

	/**
	 * Parse the input dependancy metadata and file appends results in this object.
	 * <p>
	 * If calling this multiple times, be aware that dependancy list is not cleared
	 * between parses. You can manually clear the dependancies while keeping other
	 * data by calling {@link #clearDependancies()}.
	 * 
	 * @param input_file Depencancy metadata file to parse.
	 * @param verbose    Print extra messages.
	 */
	public void parse(File input_file, boolean verbose) {
		Document doc = XMLParser.parse(input_file);
		parse(doc.getDocumentElement(), verbose);
	}

	/**
	 * Parse the input dependancy metadata file and appends results in this object.
	 * <p>
	 * If calling this multiple times, be aware that dependancy list is not cleared
	 * between parses. You can manually clear the dependancies while keeping other
	 * data by calling {@link #clearDependancies()}.
	 * 
	 * @param element Root element containing depencancy metadata to parse.
	 * @param verbose Print extra messages.
	 */
	void parse(Element element, boolean verbose) {
		parse(element, verbose, null);
	}

	/**
	 * Parse the input dependancy metadata file and appends results in this object.
	 * <p>
	 * If calling this multiple times, be aware that dependancy list is not cleared
	 * between parses. You can manually clear the dependancies while keeping other
	 * data by calling {@link #clearDependancies()}.
	 * 
	 * @param element     Root element containing depencancy metadata to parse.
	 * @param verbose     Print extra messages.
	 * @param parent_fsys Parent to inport FileSys roots from if not specified in
	 *                    element.
	 */
	void parse(Element element, boolean verbose, FileSys parent_fsys) {
		// Parse header to initialize fsys
		fsys = new FileSys(verbose);

		Queue<Element> header_children = XMLParser.getChildElementsFromTagName(element, header.key);
		if (!header_children.isEmpty()) {
			Element elem = header_children.peek();

			// Set fsys roots
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

			synth_1 = Directive.getFirstFile(elem, HEADER.synth_1, fsys, true);
			initial_file = Directive.getFirstFile(elem, HEADER.initial, fsys, true);
		}

		// Parse each directive onto list
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
			try {
				dependancies.offer(fsys.toFile(filename));
			} catch (NullPointerException npe) {
				MessageGenerator.briefError("Can't convert '" + elem.getTextContent() + "' to a file.");
				if (attr != null) {
					if ((attr == FILE.LOC.LocEnum.III && fsys.getRoot(FileSys.FILE_ROOT.III) == null)
							|| (attr == FILE.LOC.LocEnum.OOC && fsys.getRoot(FileSys.FILE_ROOT.OOC) == null)
							|| (attr == FILE.LOC.LocEnum.OUT && fsys.getRoot(FileSys.FILE_ROOT.OUT) == null))
						MessageGenerator.briefErrorAndExit("Root " + attr.toString() + " is not specified.");
					MessageGenerator.briefErrorAndExit("Unknown reason.");
				}
			}
		}
	}

	/**
	 * Clear the dependancy list.
	 */
	public void clearDependancies() {
		dependancies.clear();
	}

	/**
	 * Returns a sibling file to the input file with name {@link #META_FILENAME}.
	 * 
	 * @param dcp_file File (needn't be a dcp file) to resolve sibling for.
	 * @return File for storing metadata.
	 */
	public static File dcpToMeta(File dcp_file) {
		if (dcp_file == null || !dcp_file.getParentFile().isDirectory())
			return null;
		return new File(dcp_file.getParentFile(), META_FILENAME);
	}

	/**
	 * Write metadata to a file.
	 * 
	 * @param output_dir Directory to which metadata will be written.
	 * @param directive  Directive to write metadata for.
	 * @param args       Arguments from command line.
	 */
	public static void writeMeta(File output_dir, Directive directive, ArgsContainer args) {
		writeMeta(output_dir, META_FILENAME, directive, args);
	}

	/**
	 * Write metadata to a file.
	 * 
	 * @param output_dir Directory to which metadata will be written.
	 * @param filename   Filename to give metadata file in output_dir.
	 * @param directive  Directive to write metadata for.
	 * @param args       Arguments from command line.
	 */
	public static void writeMeta(File output_dir, String filename, Directive directive, ArgsContainer args) {
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

		String meta_filename = output_dir.getAbsolutePath() + "/" + filename;
		File synth_1 = directive.getHeader().getTopLevelSynth();
		File initial = directive.getHeader().getInitial();
		List<String> lines = toMetaLines(dependancies, iii_dir, ooc_dir, synth_1, initial, meta_filename, verbose);
		FileTools.writeLinesToTextFile(lines, meta_filename);
	}

	/**
	 * Transform a collection of dependancy files and some other data to a list of
	 * lines (strings) of xml.
	 * 
	 * @param dependancies    Collection of files to be set as dependancies in
	 *                        metadata.
	 * @param iii_dir         Root iii to help simplify resolving files when reading
	 *                        metadata.
	 * @param ooc_dir         Root ooc to help simplify resolving files when reading
	 *                        metadata.
	 * @param synth_1         Directives are dependant on top level synth if it
	 *                        exists (not null). Included in header.
	 * @param initial         Directives are dependant on initial design if it
	 *                        exists (not null). Included in header.
	 * @param output_filename File that these lines will be written to. Only used in
	 *                        display of error messages.
	 * @param verbose         Print extra messages.
	 * @return A list of lines (strings) of xml code.
	 */
	public static List<String> toMetaLines(Collection<File> dependancies, File iii_dir, File ooc_dir, File synth_1,
			File initial, String output_filename, boolean verbose) {
		List<String> lines = new ArrayList<>();
		lines.add("<root>");

		// Construct header
		if (iii_dir != null || ooc_dir != null) {
			lines.add("\t<" + header.key + ">");
			if (iii_dir != null)
				lines.add("\t\t<" + makeReducedFileLine(HEADER.iii_dir, iii_dir, null, null));
			if (ooc_dir != null)
				lines.add("\t\t<" + makeReducedFileLine(HEADER.ooc_dir, iii_dir, null, null));
			if (synth_1 != null)
				lines.add("\t\t" + makeReducedFileLine(HEADER.synth_1, synth_1, iii_dir, ooc_dir));
			if (initial != null)
				lines.add("\t\t" + makeReducedFileLine(HEADER.initial, initial, iii_dir, ooc_dir));
			lines.add("\t</" + header.key + ">");
		}

		// Construct a dependancy instance for each file in dependancies.
		for (File dep : dependancies) {
			if (dep == null) {
				printIfVerbose("Ignoring attempt to add null file to cache file '"
						+ (output_filename == null ? null : output_filename) + "'.", verbose);
				continue;
			} else if (!dep.isFile())
				printIfVerbose("Adding dependancy which does not exist yet '" + dep.getAbsolutePath() + "'.", verbose);

			lines.add("\t" + makeReducedFileLine(dependancy, dep, iii_dir, ooc_dir));
		}
		lines.add("</root>");
		return lines;
	}

	/**
	 * Returns an xml line for a file. Tries to reduce filename by seting it
	 * relative to iii or ooc using {@link Directive.FILE.LOC loc} attribute.
	 * <p>
	 * Returns a generic leaf tag with data if iii_dir and ooc_dir are null (for
	 * example, {@literal '<your_tag>your_data</your_tag>'}).
	 * 
	 * @param t       File tag.
	 * @param f       File to get line for.
	 * @param iii_dir Root iii to try resolving against.
	 * @param ooc_dir Root ooc to try resolving against.
	 * @return A line of xml.
	 */
	static String makeReducedFileLine(TAG t, File f, File iii_dir, File ooc_dir) {
		String file_str = f.getAbsolutePath();
		if (iii_dir != null && file_str.startsWith(iii_dir.getAbsolutePath())) {
			file_str = file_str.substring(iii_dir.getAbsolutePath().length() + 1);
			return "<" + t.key + " " + FILE.loc.key + " = \"" + FILE.LOC.LocEnum.III + "\"" + ">" + file_str + "</"
					+ t.key + ">";
		} else if (ooc_dir != null && file_str.startsWith(ooc_dir.getAbsolutePath())) {
			file_str = file_str.substring(ooc_dir.getAbsolutePath().length() + 1);
			return "<" + t.key + " " + FILE.loc.key + " = \"" + FILE.LOC.LocEnum.OOC + "\"" + ">" + file_str + "</"
					+ t.key + ">";
		} else {
			return "<" + t.key + ">" + file_str + "</" + t.key + ">";
		}
	}

	/**
	 * @return Unmodifiable collection of dependancy files.
	 */
	public Collection<File> getDependancies() {
		return Collections.unmodifiableCollection(dependancies);
	}

	/**
	 * Top level synth file is updated each parse if it exists in header of parsed
	 * file.
	 * 
	 * @return Top level synth file.
	 */
	public File getTopLevelSynth() {
		return synth_1;
	}

	/**
	 * Initial file is updated each parse if it exists in header of parsed file.
	 * 
	 * @return Initial file.
	 */
	public File getInitial() {
		return initial_file;
	}

	private static void printIfVerbose(String msg, boolean verbose) {
		if (verbose)
			MessageGenerator.briefMessage(msg);
	}

	/**
	 * Recognized tags for metadata header
	 */
	public static class HEADER extends TAG {
		public static final TAG iii_dir = new TAG("iii_dir");
		public static final TAG ooc_dir = new TAG("ooc_dir");
		public static final FILE synth_1 = new FILE("synth");
		public static final FILE initial = new FILE("initial");

		HEADER() {
			super("header", Arrays.asList(iii_dir, ooc_dir), Arrays.asList());
		}
	}

	public static final HEADER header = new HEADER();
	public static final FILE dependancy = new FILE("dependancy");

	/**
	 * Multimap of modules and pblocks for use by
	 * {@link Merger#findModuleInCache(Directive, ArgsContainer, DepSet)
	 * findModuleInCache} in determining if all dependancies are consistant with
	 * build that was specified.
	 */
	public static class DepSet {
		private Map<String, Collection<String>> map = null;

		public DepSet() {
			map = new HashMap<>();
		}

		/**
		 * Initializes a dependancy set from the dependancies in meta.
		 * 
		 * @param meta Metadata to get dependancies from.
		 */
		public DepSet(DependancyMeta meta) {
			this();
			for (File dep : meta.getDependancies()) {
				String[] path = dep.getAbsolutePath().split("/");
				if (path.length < 1)
					printIfVerbose("The path '" + dep.getAbsolutePath() + "' is not a valid filepath.", meta.verbose);

				String module_name = FileTools.removeFileExtension(path[path.length - 1]);
				if (path.length >= 4 && path[path.length - 4].equals(Merger.MODULE_CACHE))
					put(module_name, path[path.length - 2]);
				else
					put(module_name, "");
			}
		}

		/**
		 * Copy constructor.
		 */
		public DepSet(DepSet other) {
			map = new HashMap<>();
			for (Entry<String, Collection<String>> e : other.map.entrySet())
				this.map.put(e.getKey(), new HashSet<>(e.getValue()));
		}

		/**
		 * Adds key, value pair to this object.
		 * <p>
		 * Note: this object supports inserting multiple values to same key.
		 * 
		 * @param key   Module name.
		 * @param value pblock string with replaced '+' for ' '.
		 */
		public void put(String key, String value) {
			if (!map.containsKey(key))
				// TODO should this be a set or a list (ie. should the dependancies need to
				// remove the same number as were put in?)
				map.put(key, new HashSet<>());
			map.get(key).add(value);
		}

		/**
		 * Remove the key value pair from this set.
		 * <p>
		 * Note: this object supports inserting multiple values to same key.
		 * 
		 * @param key   Module name.
		 * @param value pblock string with replaced '+' for ' '.
		 * @return True if found and removed an entry. False otherwise.
		 */
		public boolean remove(String key, String value) {
			if (map.get(key) != null) {
				boolean ret = map.get(key).remove(value);
				if (map.get(key).isEmpty())
					map.remove(key);
				return ret;
			}
			return false;
		}

		/**
		 * @param key Module name.
		 * @see Map#containsKey(Object)
		 */
		public boolean containsKey(String key) {
			return map.containsKey(key);
		}

		/**
		 * @param key   Module name.
		 * @param value pblock string with replaced '+' for ' '.
		 * @return True if contains key and collection mapped to key contains value.
		 *         False otherwise.
		 */
		public boolean contains(String key, String value) {
			return map.containsKey(key) && map.get(key).contains(value);
		}

		/**
		 * @see Map#isEmpty()
		 */
		public boolean isEmpty() {
			return map.isEmpty();
		}
	}
}