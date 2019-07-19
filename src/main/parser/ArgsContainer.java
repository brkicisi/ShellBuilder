package main.parser;

import java.util.Map;
import java.util.List;

/**
 * Convenience wrapper to store and access the parsed set of command line
 * arguments.
 */
public class ArgsContainer {
	private Map<String, List<String>> arg_map = null;

	/**
	 * Initialize by {@link Parser parsing} command line arguments.
	 * 
	 * @param cmd_line_args Command line arguments to parse.
	 */
	public ArgsContainer(String[] cmd_line_args) {
		Parser parser = new Parser();
		arg_map = parser.mapArgs(cmd_line_args);
	}

	/**
	 * Get the first argument from the command line with given tag.
	 * 
	 * @param t Which tag to get first argument from.
	 * @return First input argument or null if no arguments found.
	 */
	public String getOneArg(Args.Tag t) {
		List<String> list = getArgs(t);
		return (list == null) ? null : list.get(0);
	}

	/**
	 * Get all arguments from the command line with given tag.
	 * 
	 * @param t Which tag to get arguments from.
	 * @return List of arguments input with tag t or null if no arguments found.
	 */
	public List<String> getArgs(Args.Tag t) {
		return (arg_map == null) ? null : arg_map.get(t.toString());
	}

	/**
	 * True if force was part of the command line args.
	 */
	public boolean force() {
		return arg_map.containsKey(Args.Tag.FORCE.toString());
	}

	/**
	 * True if verbose or extra_verbose was part of the command line args and quiet
	 * wasn't.
	 */
	public boolean verbose() {
		return !quiet() && (arg_map.containsKey(Args.Tag.VERBOSE.toString())
				|| arg_map.containsKey(Args.Tag.EXTRA_VERBOSE.toString()));
	}

	/**
	 * True if extra_verbose was part of the command line args and quiet wasn't.
	 */
	public boolean extraVerbose() {
		return !quiet() && arg_map.containsKey(Args.Tag.EXTRA_VERBOSE.toString());
	}

	/**
	 * True if quiet was part of the command line args.
	 */
	public boolean quiet() {
		return arg_map.containsKey(Args.Tag.QUIET.toString());
	}

	/**
	 * Generate options in format for {@link main.tcl.TCLScript TCLScript}.
	 * <p>
	 * Note only pass verbose to tcl if extra-verbose is selected.
	 * 
	 * @return String of options.
	 */
	public String options() {
		return (extraVerbose() ? "v" : "") + (quiet() ? "q" : "") + (force() ? "f" : "");
	}

	/**
	 * Generate options in format for {@link main.tcl.TCLScript TCLScript}.
	 * <p>
	 * Note only pass verbose to tcl if extra-verbose is selected.
	 * 
	 * @param addnl_opts Additional options to add to options string. Error checking
	 *                   is not done to confirm that this string does not create
	 *                   problems.
	 * @return String of options.
	 */
	public String options(String addnl_opts) {
		return options() + addnl_opts;
	}
}