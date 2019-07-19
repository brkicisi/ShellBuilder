
package main.parser;

import main.parser.Args;
import main.parser.PositionalArg;
import main.parser.TokenArg;

import com.xilinx.rapidwright.util.MessageGenerator;

/**
 * Print help.
 */
public class Help {
	/**
	 * Print a single line of help.
	 * 
	 * @param arg_str  Stylized name of argument.
	 * @param help_str String indicating how to use argument.
	 * @param longest  Length of longest arg_str in help message (for formatting).
	 * @return
	 */
	static String helpLine(String arg_str, String help_str, int longest) {
		int padding = longest - arg_str.length() + 4;
		String pad_str = String.format("%1$" + padding + "s", " - ");
		return arg_str + pad_str + help_str + "\n";
	}

	/**
	 * Print help information and exit. Shows all recognized arguments both optional
	 * and positional.
	 */
	public static void printHelp() {
		int longest = 0; // needed to pad so output looks nicer.
		StringBuilder sb = new StringBuilder("USAGE: ");
		for (TokenArg a : Args.TOKEN_LIST) {
			sb.append(a.shortString());
			if (a.toString().length() > longest)
				longest = a.toString().length();
		}
		for (PositionalArg a : Args.POSITIONAL_ARGS) {
			sb.append(" " + a);
			if (a.toString().length() > longest)
				longest = a.toString().length();
		}
		sb.append("\n\n");
		for (TokenArg a : Args.TOKEN_LIST)
			sb.append(helpLine(a.toString(), a.getHelp(), longest));
		for (PositionalArg a : Args.POSITIONAL_ARGS)
			sb.append(helpLine(a.toString(), a.getHelp(), longest));
		sb.append("\n");
		MessageGenerator.briefMessageAndExit(sb.toString());
	}
}