package top;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.util.MessageGenerator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import parser.Args;
import parser.ArgsContainer;
import util.DesignUtils;
import worker.Merger;
import directive.*;
import worker.FileSys;

import java.io.File;
import java.io.IOException;

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
			MessageGenerator.briefErrorAndExit("Use force (-f) to overwrite.\nExiting.\n");
	}

	/**
	 * Execute instructions provided by directive.
	 * 
	 * @param directive Instructions to execute.
	 */
	private void runDirective(Directive directive, Merger merger) {
		if (directive.isSubBuilder()) {
			// TODO check for cached hierarchial solutions
			File cached_dcp = Merger.findModuleInCache(directive, args);
			if (cached_dcp != null) {
				directive.setDCP(cached_dcp);
			} else {
				Merger sub_merge = runBuilder(directive.getSubBuilder());
				directive.setDCP(sub_merge.getFinalDCP());
			}
			merger.merge(directive, args);

		} else if (directive.isMerge()) {
			merger.merge(directive, args);

		} else if (directive.isWrite()) {
			checkFileCollision(directive);

			// TODO allow for default intermediate dcp names
			if (directive.getDCP() == null)
				System.err.println("Default intermediate dcp naming has not been implemented yet.");

			String output_dcp = directive.getDCP().getAbsolutePath();
			printIfVerbose("Output dcp to '" + output_dcp + "'");
			merger.writeCheckpoint(output_dcp);
		} else {
			MessageGenerator
					.briefErrorAndExit("Unrecognized directive '" + directive.getType().toString() + "'.\nExiting.");
		}
	}

	public Merger runBuilder(DirectiveBuilder directive_builder) {
		Merger merger = null;
		DirectiveHeader head = directive_builder.getHeader();

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
		// this currently just executes everything
		// it checks for cached designs, but doesn't check for cached partial
		// hierarchial designs
		for (Directive step : directive_builder.getDirectives())
			runDirective(step, merger);

		File iii_dir = (head.getParent() == null) ? head.getIII() : head.getParent().getIII();
		if (head.getModuleName() == null)
			MessageGenerator.briefErrorAndExit("No name specified for module being built.");

		File out_dcp = new File(iii_dir,
				Merger.MODULE_CACHE + "/" + head.getModuleName() + "/" + head.getModuleName() + ".dcp");
		merger.setFinalDCP(out_dcp);
		merger.writeCheckpoint(out_dcp);
		merger.placeAndRoute(out_dcp, directive_builder.getHeader(), args);

		// Get dcp of last directive if it was a write
		File write_dcp = null;
		for (Directive directive : directive_builder.getDirectives()) {
			if (directive.isWrite())
				write_dcp = directive.getDCP();
			else
				write_dcp = null;
		}
		if (write_dcp != null) {
			// if last directive was a write, write placed and routed module to this final
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

				// Files.copy(out_dcp, write_dcp);
				// Files.copy(new File(out_dcp.getParentFile(),
				// out_dcp.getName().replace(".dcp", ".edf")),
				// new File(write_dcp.getParentFile(), write_dcp.getName().replace(".dcp",
				// ".edf")));
			} catch (IOException ioe) {
				printIfVerbose("Failed to copy checkpoint and edif from cache to '" + write_dcp.getParent() + "'");
			}
		}

		return merger;
	}

	public void runTemplateBuilder(DirectiveBuilder directive_builder) {
		for (DirectiveWriter.TemplateBuilder template_builder : directive_builder.getTemplateBuilders())
			template_builder.writeTemplate();
		for (Directive dir : directive_builder.getDirectives())
			if (dir.isSubBuilder())
				runTemplateBuilder(dir.getSubBuilder());
	}

	public void start(String[] cmd_line_args) {
		args = new ArgsContainer(cmd_line_args);
		FileSys fsys = new FileSys(args.verbose());
		File xml_directives = fsys.getExistingFile(args.getOneArg(Args.Tag.XML_DIRECTIVES), true);

		DirectiveBuilder directive_builder = new DirectiveBuilder();
		directive_builder.parse(xml_directives, args.verbose());
		runTemplateBuilder(directive_builder);
		if (!directive_builder.getDirectives().isEmpty())
			runBuilder(directive_builder);

		MessageGenerator.briefMessage("\nFinished.");
	}

	public static void main(String[] args) {
		ShellBuilder builder = new ShellBuilder();
		builder.start(args);
	}
}
