package main.top;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.util.MessageGenerator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import main.parser.Args;
import main.parser.ArgsContainer;
import main.util.DesignUtils;
import main.worker.Merger;
import main.worker.XDCWriter;
import main.directive.*;
import main.worker.FileSys;
import main.worker.ILAAdder;

import java.io.File;
import java.io.IOException;

/**
 * Main orchestrating program for building shells.
 * 
 * @author Igi Brkic
 * @since Summer 2019
 */
public class ShellBuilder {
	ArgsContainer args = null;

	public ShellBuilder() {
	}

	/**
	 * Prints the string if verbose was part of the command line args.
	 * 
	 * @param s String to be printed.
	 */
	private void printIfVerbose(String s) {
		if (args.verbose())
			MessageGenerator.briefMessage(s);
	}

	/**
	 * Ensure that if the user has not specified to force overwrite, output dcp
	 * files don't collide with already existing files.
	 * <p>
	 * Exits with error if not force and collision.
	 * 
	 * @param directive Check output files associated with this directive.
	 */
	private void checkFileCollision(Directive directive) {
		if (args.force() || directive.isForce())
			return;

		boolean any_err = false;
		File f = directive.getDCP();
		if ((f != null) && f.exists()) {
			MessageGenerator
					.briefError("\nAn output dcp would overwrite another file at '" + f.getAbsolutePath() + "'.");
			any_err = true;
		}

		// if any would overwrite, exit
		if (any_err)
			MessageGenerator.briefErrorAndExit("Use force (-f) or <force> to overwrite.\nExiting.\n");
	}

	/**
	 * Execute instruction provided by directive.
	 * 
	 * @param directive Instruction to execute.
	 * @param merger    Merge directives into this if "merge" or "build".
	 */
	private void runDirective(Directive directive, Merger merger) {
		if (directive.isSubBuilder()) {
			// TODO uncomment this
			// check for cached hierarchial solutions
			File cached_dcp = Merger.findModuleInCache(directive, args, false);
			if (cached_dcp != null) {
				directive.setDCP(cached_dcp);
			} else {
				Merger sub_merge = runBuilder(directive.getSubBuilder());
				directive.setDCP(sub_merge.getFinalDCP());
			}
			merger.merge(directive, args);

			// TODO remove this
			// File cached_dcp = Merger.findModuleInCache(directive, args);
			// if (cached_dcp != null) {
			// directive.setDCP(cached_dcp);
			// merger.merge(directive, args);
			// } else {
			// Merger sub_merge = runBuilder(directive.getSubBuilder());
			// directive.setDCP(sub_merge.getFinalDCP());
			// merger.merge(directive, sub_merge, args);
			// }

		} else if (directive.isMerge()) {
			merger.merge(directive, args);

		} else if (directive.isWrite()) {
			checkFileCollision(directive);

			// TODO allow for default intermediate dcp names
			if (directive.getDCP() == null)
				MessageGenerator.briefErrorAndExit("Default intermediate dcp naming has not been implemented yet.");

			String output_dcp = directive.getDCP().getAbsolutePath();
			printIfVerbose("Output dcp to '" + output_dcp + "'");
			merger.writeCheckpoint(output_dcp);
		} else {
			MessageGenerator
					.briefErrorAndExit("Unrecognized directive '" + directive.getType().toString() + "'.\nExiting.");
		}
	}

	/**
	 * Execute instructions provided in directive_builder.
	 * 
	 * @param directive_builder Instructions representing a design.
	 * @return Merged, placed and routed design.
	 */
	public Merger runBuilder(DirectiveBuilder directive_builder) {
		Merger merger = null;
		DirectiveHeader head = directive_builder.getHeader();
		if (head == null || head.getModuleName() == null)
			MessageGenerator.briefErrorAndExit("No name specified for module being built.");

		File initial = head.getInitial();
		if (initial != null) {
			printIfVerbose("Initializing merger with base design '" + initial.getAbsolutePath() + "'.");
			Design d = DesignUtils.safeReadCheckpoint(initial, args.verbose(), head.getIII());
			merger = new Merger(d, head, args);
		} else {
			String module_name = directive_builder.getHeader().getModuleName();
			if (module_name == null)
				module_name = "top";
			printIfVerbose("Initializing initial merger base design with module name '" + module_name + "'.");
			merger = new Merger();
		}

		for (Directive step : directive_builder.getDirectives())
			runDirective(step, merger);

		File iii_dir = (head.getParent() == null) ? head.getIII() : head.getParent().getIII();
		File out_dcp = new File(iii_dir,
				Merger.MODULE_CACHE + "/" + head.getModuleName() + "/" + head.getModuleName() + ".dcp");
		merger.setFinalDCP(out_dcp);
		merger.writeCheckpoint(out_dcp);

		// if (head.getParent() == null) 
		
		// ! only run if top level module?
		// TODO is it still placing and routing both here and when the design is loaded as an ooc dcp
		merger.placeAndRoute(out_dcp, head, args);

		// Get dcp of last directive if it was a write
		File write_dcp = null;
		for (Directive directive : directive_builder.getDirectives()) {
			if (directive.isWrite())
				write_dcp = directive.getDCP();
			else
				write_dcp = null;
		}
		if (write_dcp != null) {
			// If last directive was a write, write placed and routed module to this final
			// write location.
			try {
				Path from = Paths.get(out_dcp.getAbsolutePath());
				Path to = Paths.get(write_dcp.getAbsolutePath());
				Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);

				File f = new File(out_dcp.getParentFile(), out_dcp.getName().replace(".dcp", ".edf"));
				from = Paths.get(f.getAbsolutePath());
				f = new File(write_dcp.getParentFile(), write_dcp.getName().replace(".dcp", ".edf"));
				to = Paths.get(f.getAbsolutePath());
				Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
				printIfVerbose(
						"\nCopied checkpoint and edif from cache to '" + write_dcp.getAbsolutePath() + "' (/.edf)");
			} catch (IOException ioe) {
				printIfVerbose("Failed to copy checkpoint and edif from cache to '" + write_dcp.getAbsolutePath()
						+ "' (/.edf)");
			}
		}

		return merger;
	}

	/**
	 * Execute all {@link main.directive.DirectiveWriter.TemplateBuilder template
	 * builders} in this directive_builder and in all it's descendant builders.
	 * 
	 * @param directive_builder Top directive builder.
	 */
	public void runTemplateBuilder(DirectiveBuilder directive_builder) {
		for (DirectiveWriter.TemplateBuilder template_builder : directive_builder.getTemplateBuilders())
			template_builder.writeTemplate(args.force());
		for (Directive dir : directive_builder.getDirectives())
			if (dir.isSubBuilder())
				runTemplateBuilder(dir.getSubBuilder());
	}

	/**
	 * Highest level orchestrator.
	 * <p>
	 * Parses command line args. Parses xml_directives file input at command line.
	 * Run all {@link main.directive.DirectiveWriter.TemplateBuilder template
	 * builders}. Run top {@link DirectiveBuilder directive builder}.
	 * 
	 * @param cmd_line_args Command line arguments.
	 */
	public void start(String[] cmd_line_args) {
		args = new ArgsContainer(cmd_line_args);
		FileSys fsys = new FileSys(args.verbose());
		File xml_directives = fsys.getExistingFile(args.getOneArg(Args.Tag.XML_DIRECTIVES), true);
		DirectiveBuilder directive_builder = new DirectiveBuilder();
		directive_builder.parse(xml_directives, args.verbose());
		if (args.refresh())
			directive_builder.getHeader().setRefresh(true);

		// TODO remove this test
		// it added an ila to the tutorial 2 project
		// File input_dcp = new
		// File(directive_builder.getHeader().fsys().getRoot(FileSys.FILE_ROOT.OUT),
		// "xml_final_all_green_placement.dcp");
		// File output_dcp = new
		// File(directive_builder.getHeader().fsys().getRoot(FileSys.FILE_ROOT.OUT),
		// "../ila_out/tut2_with_ila.dcp");
		// ILAAdder.testAddILA(input_dcp, output_dcp, directive_builder.getHeader(),
		// args);

		runTemplateBuilder(directive_builder);

		XDCWriter xdc_writer = new XDCWriter(args, true);
		for (Directive dir : directive_builder.getDirectives())
			xdc_writer.writeAllHierXDC(dir);

		if (!directive_builder.getDirectives().isEmpty())
			runBuilder(directive_builder);

		MessageGenerator.briefMessage("\nFinished.");
	}

	/**
	 * Run a {@link ShellBuilder}.
	 * 
	 * @param args Command line arguments.
	 */
	public static void main(String[] args) {
		ShellBuilder builder = new ShellBuilder();
		builder.start(args);
	}
}
