package main.parser;

import com.xilinx.rapidwright.util.MessageGenerator;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Parses a list of command line arguments into a map that is understood by
 * {@link ArgsContainer}.
 */
public class Parser {
	Map<String, TokenArg> token_map = null;

	public Parser() {
		token_map = new HashMap<>();
		for (TokenArg t : Args.TOKEN_LIST)
			for (String op : t.options)
				token_map.put(op, t);
	}

	/**
	 * Takes in a command line list and parses it into a HashMap.
	 * <p>
	 * Note: if token has a non fixed number of arguments, it will take as many
	 * arguments as it can before running out of arguments to take or encountering
	 * another token.
	 * 
	 * @param args Command line list.
	 */
	public Map<String, List<String>> mapArgs(String[] args) {
		// If any argument is in the set of help switches, print help and exit
		for (String a : args)
			for (String sw : Args.HELP_SWITCH)
				if (a.equalsIgnoreCase(sw))
					Help.printHelp();

		Map<String, List<String>> arg_map = new HashMap<>();

		int positional_arg_counter = 0;
		for (int i = 0; i < args.length; i++) {
			// Token
			if (args[i].startsWith("-") || args[i].startsWith("--")) {
				TokenArg t = token_map.get(args[i]);
				if (t == null)
					MessageGenerator.briefErrorAndExit("Unrecognized token at position " + i + ".\n");

				ArrayList<String> a_list = new ArrayList<>();
				for (int j = 0; j < t.args.length; j++) {
					String a = null;
					try {
						a = args[i + j + 1];
					} catch (ArrayIndexOutOfBoundsException iobe) {
						if (t.args[j].required)
							MessageGenerator.briefErrorAndExit("Not enough arguments for '" + args[i] + "' at position "
									+ i + ".\n" + t.toString() + "\n");
						else
							break;
					}
					if (a.startsWith("-") || a.startsWith("--")) {
						if (t.args[j].required)
							MessageGenerator.briefErrorAndExit("Not enough arguments for '" + args[i] + "' at position "
									+ i + ".\n" + t.toString() + "\n");
						else
							break;
					}
					a_list.add(a);
					i++;
				}
				arg_map.put(t.name, a_list);
			}
			// Standard positional argument
			else {
				if (positional_arg_counter >= Args.POSITIONAL_ARGS.length)
					continue;

				ArrayList<String> a_list = new ArrayList<>();
				a_list.add(args[i]);
				arg_map.put(Args.POSITIONAL_ARGS[positional_arg_counter].name, a_list);
				positional_arg_counter++;
			}
		}
		if (positional_arg_counter < Args.POSITIONAL_ARGS.length
				&& Args.POSITIONAL_ARGS[positional_arg_counter].required)
			MessageGenerator.briefErrorAndExit("Not enough positional arguments.\n");

		return arg_map;
	}
}