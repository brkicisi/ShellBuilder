package worker;

import worker.Directive.HEADER;

import java.io.File;

import com.xilinx.rapidwright.util.MessageGenerator;

import org.w3c.dom.Element;

public class DirectiveHeader {
	FileSys fsys = null;
	boolean verbose = false;
	boolean refresh = false;
	String name;

	public DirectiveHeader() {
	}

	public DirectiveHeader(boolean verbose) {
		this.verbose = verbose;
	}

	/**
	 * Adds all children and attributes of header (an element rooted at a header
	 * tag) to this object.
	 * 
	 * @param elem An element whose root is a header tag.
	 */
	public void addHeader(Element elem) {
		String text = Directive.getFirst(elem, HEADER.iii_dir);
		if (text != null)
			setDirRoot(FileSys.FILE_ROOT.III, text);

		text = Directive.getFirst(elem, HEADER.ooc_dir);
		if (text != null)
			setDirRoot(FileSys.FILE_ROOT.OOC, text);

		text = Directive.getFirst(elem, HEADER.out_dir);
		if (text != null)
			setDirRoot(FileSys.FILE_ROOT.OUT, text);
		
		refresh = Directive.getFirstBool(elem, HEADER.refresh);

		name = Directive.getFirst(elem, HEADER.name);
		if(name != null && name.indexOf(' ') > 0){
			printIfVerbose("Replacing spaces with underscores in input name '" + name + "'.");
			name = name.replace(" ", "_");
		}
	}

	private void printIfVerbose(String msg) {
		if(verbose)
			MessageGenerator.briefMessage(msg);
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	public void setDirRoot(FileSys.FILE_ROOT root, String filename) {
		if (fsys == null)
			fsys = new FileSys(verbose);
		fsys.setDirRoot(root, filename);
	}

	public File getIII() {
		return (fsys == null) ? null : fsys.getRoot(FileSys.FILE_ROOT.III);
	}

	public String getName(){
		return name;
	}

	public FileSys fsys() {
		if (fsys == null)
			fsys = new FileSys(verbose);
		return fsys;
	}
}