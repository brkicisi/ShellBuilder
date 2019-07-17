
package parser;

import parser.PositionalArg;
import parser.TokenArg;

/**
 * Define valid command line arguments here
 */
public final class Args {
	public static enum Tag {
		REFRESH("refresh"), FORCE("force"), QUIET("quiet"), VERBOSE("verbose"), EXTRA_VERBOSE("extra_verbose"),
		HELP("help"), XML_DIRECTIVES("directive_file.xml");

		String tag;

		Tag(String str) {
			tag = str;
		}

		@Override
		public String toString() {
			return tag;
		}
	}

	static final String[] HELP_SWITCH = { "-h", "--help" };

	// used {f, h, q, r, Vv}
	static final TokenArg[] TOKEN_LIST = {
			new TokenArg(Tag.REFRESH.toString(), new String[] { "-r", "--refresh" },
					"Force recompilation from first directive. Ignore any intermediate designs."),
			new TokenArg(Tag.FORCE.toString(), new String[] { "-f", "--force" },
					"Force overwrite for ALL output files. You can specify force overwrite for individual "
							+ "files in the xml building instructions."), // (intermediate files in .iii are always
																			// overwritten)
			new TokenArg(Tag.QUIET.toString(), new String[] { "-q", "--quiet" },
					"Display less progress information. Run Vivado tcl commands with '-quiet' flag."),
			new TokenArg(Tag.VERBOSE.toString(), new String[] { "-v", "--verbose" },
					"Display extra progress information (ignored if also quiet)."),
			new TokenArg(Tag.EXTRA_VERBOSE.toString(), new String[] { "-V", "--extra_verbose" },
					"Display extra progress information (ignored if also quiet). Run Vivado tcl commands with '-verbose' flag."),
			new TokenArg(Tag.HELP.toString(), HELP_SWITCH, "Print this help message and exit.") };

	static final PositionalArg[] POSITIONAL_ARGS = {
		new PositionalArg(Tag.XML_DIRECTIVES.toString(), true,
			"XML file detailing building instructions. See README.md for details.") };
}