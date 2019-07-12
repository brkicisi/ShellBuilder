package top;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.util.MessageGenerator;

import parser.Args;
import parser.ArgsContainer;
import util.DesignUtils;
import worker.Merger;
import directive.*;
import worker.FileSys;

import java.io.File;

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
			Merger sub_merge = runBuilder(directive.getSubBuilder());
			directive.setDCP(sub_merge.getFinalDCP());
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
					.briefErrorAndExit("Unrecognized directive '" + directive.getType().getStr() + "'.\nExiting.");
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
			String name = directive_builder.getHeader().getName();
			if (name == null)
				name = "top";
			printIfVerbose("Initializing initial merger base design with name '" + name + "'.");
			merger = new Merger();
		}
		// this currently just executes everything
		// it checks for cached designs, but doesn't check for cached partial hierarchial designs
		for (Directive step : directive_builder.getDirectives())
			runDirective(step, merger);

		// Get dcp of last directive if it was a write
		File out_dcp = null;
		for (Directive directive : directive_builder.getDirectives()) {
			if (directive.isWrite())
				out_dcp = directive.getDCP();
			else
				out_dcp = null;
		}
		if (out_dcp == null) {
			File iii_dir = (head.getParent() == null) ? head.getIII() : head.getParent().getIII();
			out_dcp = new File(iii_dir, "moduleCache/" + head.getName() + ".dcp");
		}
		merger.setFinalDCP(out_dcp);
		merger.writeCheckpoint(out_dcp);
		merger.placeAndRoute(out_dcp, directive_builder.getHeader(), args);

		// TODO insert this design to initial design if supplied
		if (head.getInitial() != null) {
			// Design wrapper = DesignUtils.safeReadCheckpoint(head.getInitial(), args.verbose(), head.getIII());
			// Merger merger2 = new Merger(wrapper, head, args);
			// Directive directive2 = new Directive(elem, head);
			// merger2.merge(directive2, args);
		}

		return merger;
	}

	public void start(String[] cmd_line_args) {
		args = new ArgsContainer(cmd_line_args);

		File xml_directives = new FileSys(args.verbose()).getExistingFile(args.getOneArg(Args.Tag.XML_DIRECTIVES),
				true);
		DirectiveBuilder directive_builder = new DirectiveBuilder();
		directive_builder.parse(xml_directives, args.verbose());
		runBuilder(directive_builder);

		MessageGenerator.briefMessage("\nFinished.");
	}

	public static void main(String[] args) {
		ShellBuilder builder = new ShellBuilder();
		builder.start(args);
	}
}
