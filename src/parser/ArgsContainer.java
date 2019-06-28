package parser;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class ArgsContainer {
	private Map<String, List<String>> arg_map = null;

	public ArgsContainer(String[] cmd_line_args){
		Parser parser = new Parser();
		arg_map = parser.mapArgs(cmd_line_args);
	}

	public ArgsContainer(Map<String, List<String>> arg_map){
		if(arg_map == null)
			this.arg_map = new HashMap<>();
		this.arg_map = arg_map;
	}

	/**
	 * Get the first argument from the command line with given key.
	 * 
	 * @param key Argument to search for.
	 * @return First input argument or null if no arguments found.
	 */
	String getOneArg(String key) {
		List<String> list = (arg_map == null) ? null : arg_map.get(key);
		return (list == null) ? null : list.get(0);
	}

	public String getOneArg(Args.Tag t) {
		return getOneArg(t.toString());
	}

	/**
	 * True if force was part of the command line args.
	 * 
	 * @return
	 */
	public boolean force() {
		return arg_map.containsKey(Args.Tag.FORCE.toString());
	}

	/**
	 * True if verbose or extra_verbose was part of the command line args and quiet
	 * wasn't.
	 * 
	 * @return
	 */
	public boolean verbose() {
		return !quiet() && (arg_map.containsKey(Args.Tag.VERBOSE.toString())
				|| arg_map.containsKey(Args.Tag.EXTRA_VERBOSE.toString()));
	}

	/**
	 * True if extra_verbose was part of the command line args and quiet wasn't.
	 * 
	 * @return
	 */
	public boolean extraVerbose() {
		return !quiet() && arg_map.containsKey(Args.Tag.EXTRA_VERBOSE.toString());
	}

	/**
	 * True if quiet was part of the command line args.
	 * 
	 * @return
	 */
	public boolean quiet() {
		return arg_map.containsKey(Args.Tag.QUIET.toString());
	}

	/**
	 * Generate options in format for TCLScript. Note only pass verbose to tcl if
	 * extra-verbose is selected.
	 * 
	 * @return String of options.
	 */
	public String options() {
		return (extraVerbose() ? "v" : "") + (quiet() ? "q" : "") + (force() ? "f" : "");
	}

	/**
	 * Generate options in format for TCLScript. Note only pass verbose to tcl if
	 * extra-verbose is selected.
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