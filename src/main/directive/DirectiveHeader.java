package main.directive;

import java.io.File;
import java.util.Arrays;

import org.w3c.dom.Element;

import com.xilinx.rapidwright.util.MessageGenerator;

import main.parser.XMLParser;
import main.parser.XMLParser.TAG;
import main.directive.Directive.FILE;
import main.worker.FileSys;

/**
 * Contains data common to a group of sibling {@link Directive directives}.
 */
public class DirectiveHeader {
	FileSys fsys = null;
	boolean verbose = false;
	boolean refresh = false;
	boolean hand_placer = false;
	boolean buffer_inputs = false;
	File initial_dcp = null;
	File top_level_synth = null;
	String module_name = null;
	DirectiveHeader parent = null;

	public DirectiveHeader(boolean verbose) {
		this.verbose = verbose;
	}

	public DirectiveHeader(DirectiveHeader parent, boolean verbose) {
		this.parent = parent;
		this.verbose = verbose;
	}

	/**
	 * Adds all children and attributes of header (an element rooted at a header
	 * tag) to this object.
	 * 
	 * @param elem An element whose root is a header tag.
	 */
	/**
	 * Parses an element describing a header.
	 * <p>
	 * Sets fields of this object to value from first found child of the field's
	 * type overwriting any previously set fields.
	 * 
	 * @param elem Element to parse.
	 */
	public void addHeader(Element elem) {
		fsys = new FileSys(verbose);
		// set fsys roots
		String text = XMLParser.getFirst(elem, HEADER.iii_dir);
		if (text != null)
			fsys.setDirRoot(FileSys.FILE_ROOT.III, text);
		else if (parent != null)
			fsys.setDirRoot(FileSys.FILE_ROOT.III, parent.fsys().getRoot(FileSys.FILE_ROOT.III));
		else
			fsys.setDirRoot(FileSys.FILE_ROOT.III, (String) null);

		text = XMLParser.getFirst(elem, HEADER.ooc_dir);
		if (text != null)
			fsys.setDirRoot(FileSys.FILE_ROOT.OOC, text);
		else if (parent != null)
			fsys.setDirRoot(FileSys.FILE_ROOT.OOC, parent.fsys().getRoot(FileSys.FILE_ROOT.OOC));

		text = XMLParser.getFirst(elem, HEADER.out_dir);
		if (text != null)
			fsys.setDirRoot(FileSys.FILE_ROOT.OUT, text);
		else if (parent != null)
			fsys.setDirRoot(FileSys.FILE_ROOT.OUT, parent.fsys().getRoot(FileSys.FILE_ROOT.OUT));

		// this must be processed after fsys roots are set.
		initial_dcp = Directive.getFirstFile(elem, HEADER.initial, fsys, false);
		if (initial_dcp == null || !initial_dcp.exists())
			initial_dcp = null;

		top_level_synth = Directive.getFirstFile(elem, HEADER.synth, fsys, false);
		if (top_level_synth == null || !top_level_synth.exists())
			top_level_synth = null;

		refresh = XMLParser.getFirstBool(elem, HEADER.refresh);

		hand_placer = XMLParser.getFirstBool(elem, HEADER.hand_placer);

		buffer_inputs = XMLParser.getFirstBool(elem, HEADER.buffer_inputs);

		module_name = XMLParser.getFirst(elem, HEADER.module_name);
		if (module_name != null && module_name.indexOf(' ') > 0) {
			printIfVerbose("Replacing spaces with underscores in input module_name '" + module_name + "'.");
			module_name = module_name.replace(" ", "_");
		}
	}

	private void printIfVerbose(String msg) {
		if (verbose)
			MessageGenerator.briefMessage(msg);
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	/**
	 * @return iii dir from this header; else from parent; else null.
	 */
	public File getIII() {
		return (fsys == null) ? null : fsys.getRoot(FileSys.FILE_ROOT.III);
	}

	public String getModuleName() {
		return module_name;
	}

	/**
	 * @return file sys from this header; else from parent; else new file sys.
	 */
	public FileSys fsys() {
		if (fsys == null) {
			if (parent == null)
				fsys = new FileSys(verbose);
			else
				fsys = parent.fsys();
		}
		return fsys;
	}

	/**
	 * Indicates to ShellBuilder that this build should be a normal dcp (not an out
	 * of context dcp which is default).
	 */
	public boolean isBufferedInputs() {
		return buffer_inputs;
	}

	/**
	 * @return refresh from this header or from parent
	 */
	public boolean isRefresh() {
		if (parent == null)
			return refresh;
		return refresh || parent.isRefresh();
	}

	/**
	 * @return hand_placer from this header or from parent
	 */
	public boolean isHandPlacer() {
		if (parent == null)
			return hand_placer;
		return hand_placer || parent.isHandPlacer();
	}

	public boolean isVerbose() {
		return verbose;
	}

	public File getInitial() {
		return initial_dcp;
	}

	/**
	 * @return hand_placer from this header; else from parent; else null.
	 */
	public File getTopLevelSynth() {
		if (top_level_synth != null)
			return top_level_synth;
		if (parent != null)
			return parent.getTopLevelSynth();
		return null;
	}

	public DirectiveHeader getParent() {
		return parent;
	}

	/**
	 * Recognized tags for {@link DirectiveHeader} (extends {@link TAG}).
	 */
	public static class HEADER extends TAG {
		public static final TAG iii_dir = new TAG("iii_dir");
		public static final TAG ooc_dir = new TAG("ooc_dir");
		public static final TAG out_dir = new TAG("out_dir");
		public static final FILE initial = new FILE("initial");
		public static final FILE synth = new FILE("synth");
		public static final TAG module_name = new TAG("module_name");
		public static final TAG refresh = new TAG("refresh");
		public static final TAG hand_placer = new TAG("hand_placer");
		public static final TAG buffer_inputs = new TAG("buffer_inputs");

		HEADER() {
			super("header",
					Arrays.asList(iii_dir, ooc_dir, out_dir, initial, synth, module_name, refresh, buffer_inputs),
					Arrays.asList());
		}
	}

	public static final HEADER header = new HEADER();
}