package main.worker;

import java.io.File;

import com.xilinx.rapidwright.util.FileTools;
import com.xilinx.rapidwright.util.MessageGenerator;

import main.directive.Directive;
import main.directive.DirectiveHeader;
import main.parser.ArgsContainer;
import main.tcl.TCLEnum;
import main.tcl.TCLScript;

/**
 * Generates constraints files for each hier cell under a specified root.
 */
public class XDCWriter {
	public static final String CONSTRAINTS_FILE = "constraints.xdc";
	private String options = null;
	private ArgsContainer args = null;

	/**
	 * Initialize default options for tcl scripts.
	 * 
	 * @param args Arguments from command line.
	 */
	public XDCWriter(ArgsContainer args) {
		options = args.options();
		this.args = args;
	}

	/**
	 * Initialize default options for tcl scripts.
	 * 
	 * @param args  Arguments from command line.
	 * @param force Force overwrite even if not specified in args.
	 */
	public XDCWriter(ArgsContainer args, boolean force) {
		if (force)
			options = args.options("f");
		else
			options = args.options();
		this.args = args;
	}

	/**
	 * Generates constraints files for each hier cell from a root directive.
	 * 
	 * @param directive Root cell to generate constraints for. Recursively generate
	 *                  constraints for all children.
	 */
	public void writeAllHierXDC(Directive directive) {
		if (directive.isWrite() || directive.isOnlyWires())
			return;
		String tcl_script_name = (directive.getIII() == null ? "" : directive.getIII().getAbsolutePath() + "/")
				+ "write_all_hier_xdc.tcl";
		TCLScript script = new TCLScript((String) null, null, options, tcl_script_name);

		// String proj_filename =
		// "/nfs/ug/thesis/thesis0/pc2019/Igi/shell/pieces/tutorial2/tutorial2.xpr";
		// String proj_filename =
		// "/nfs/ug/thesis/thesis0/pc2019/Igi/shell/pieces/tutorial2-direct-connections/tutorial2.xpr";
		String proj_filename = "/nfs/ug/thesis/thesis0/pc2019/Igi/shell/pieces/simple_test/simple_test.xpr";

		script.addCustomCmd("open_project -read_only " + proj_filename);
		script.addCustomCmd("open_run synth_1 -name synth_1");

		int num_xdc_lines = generateWriteLines(script, proj_filename, directive, null);

		// ensure directory exists in moduleCache
		File impl_dir = findOrMakeCacheDir(directive.getHeader(), args);
		File constr_file = new File(impl_dir, CONSTRAINTS_FILE);

		// Don't write constraints again if constraints file is newer than open project
		if (!constr_file.isFile() || FileTools.isFileNewer(proj_filename, constr_file.getAbsolutePath())) {
			script.add(TCLEnum.WRITE_XDC, options, null, constr_file.getAbsolutePath());
			num_xdc_lines++;
		}

		if (num_xdc_lines > 0)
			script.run();
	}

	private int generateWriteLines(TCLScript script, String proj_filename, Directive directive, String parent_path) {
		int num_lines = 0;

		if (directive.isWrite() || directive.isOnlyWires())
			return num_lines;

		// ensure directory exists in moduleCache
		File impl_dir = findOrMakeCacheDir(directive, args);
		File constr_file = new File(impl_dir, CONSTRAINTS_FILE);
		String hier_cell_name = (parent_path == null ? "" : parent_path + "/") + directive.getInstName();

		// Don't write constraints again if constraints file is newer than open project
		if (!constr_file.isFile() || FileTools.isFileNewer(proj_filename, constr_file.getAbsolutePath())) {
			addWriteCmd(script, hier_cell_name, constr_file.getAbsolutePath());
			num_lines++;
		}

		if (!directive.isSubBuilder())
			return num_lines;

		for (Directive dir : directive.getSubBuilder().getDirectives())
			num_lines += generateWriteLines(script, proj_filename, dir, hier_cell_name);
		return num_lines;
	}

	private void addWriteCmd(TCLScript script, String hier_cell_name, String filename) {
		String custom_opts = "-cell " + hier_cell_name + " -no_fixed_only ";
		script.add(TCLEnum.WRITE_XDC, options, custom_opts, filename);
	}

	public static File findOrMakeCacheDir(Directive directive, ArgsContainer args) {
		File cache_dir = new File(directive.getIII(), Merger.MODULE_CACHE);
		if (!cache_dir.isDirectory()) {
			printIfVerbose("\nCreating cache directory at '" + cache_dir.getAbsolutePath() + "'.", args.verbose());
			cache_dir.mkdirs();
		}

		String module_name = Merger.getModuleName(directive, args);
		if (module_name == null)
			return null;

		File mod_dir = new File(cache_dir, module_name);
		if (!mod_dir.isDirectory()) {
			printIfVerbose("\nAdding directory for module '" + module_name + "' to cache.", args.verbose());
			mod_dir.mkdir();
		}
		String pblock = directive.getPBlockStr();
		File impl_dir;
		if (pblock != null) {
			impl_dir = new File(mod_dir, Merger.getPblockPath(pblock));
		} else {
			printIfVerbose("\nNo pblock specified for module '" + module_name + "'.", args.verbose());
			impl_dir = mod_dir;
		}

		if (!impl_dir.isDirectory()) {
			printIfVerbose("\nAdding directory for pblock '" + pblock + "' in cache.", args.verbose());
			impl_dir.mkdir();
		}
		return impl_dir;
	}

	public static File findOrMakeCacheDir(DirectiveHeader head, ArgsContainer args) {
		File cache_dir = new File(head.getIII(), Merger.MODULE_CACHE);
		if (!cache_dir.isDirectory()) {
			printIfVerbose("\nCreating cache directory at '" + cache_dir.getAbsolutePath() + "'.", args.verbose());
			cache_dir.mkdirs();
		}
		String module_name = head.getModuleName();
		if (module_name == null)
			return null;

		File mod_dir = new File(cache_dir, module_name);
		if (!mod_dir.isDirectory()) {
			printIfVerbose("\nAdding directory for module '" + module_name + "' to cache.", args.verbose());
			mod_dir.mkdir();
		}
		return mod_dir;
	}

	private static void printIfVerbose(String msg, boolean verbose) {
		if (verbose)
			MessageGenerator.briefMessage(msg);
	}
}