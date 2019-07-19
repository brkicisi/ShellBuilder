package parser;

import com.xilinx.rapidwright.util.MessageGenerator;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;



public class Parser {

    Map<String, TokenArg> token_map = null;

    public Parser() {
        token_map = new HashMap<>();
        for(TokenArg t : Args.TOKEN_LIST)
            for(String op : t.options)
                token_map.put(op, t);
    }

    /**
     * Takes in a command line list and parses it into a HashMap.
     * 
     * @param args command line list
     */
    public Map<String, List<String>> mapArgs(String[] args) {
        // if any argument is in the set of help switches, print help and exit
        for (String a : args)
            for (String sw : Args.HELP_SWITCH)
                if (a.equalsIgnoreCase(sw))
                    Help.printHelp();

        Map<String, List<String>> arg_map = new HashMap<>();

        int positional_arg_counter = 0;
        for (int i = 0; i < args.length; i++) {
            // token
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
            // standard argument
            else {
                if (positional_arg_counter >= Args.POSITIONAL_ARGS.length)
                    continue;

                ArrayList<String> a_list = new ArrayList<>();
                a_list.add(args[i]);
                arg_map.put(Args.POSITIONAL_ARGS[positional_arg_counter].name, a_list);
                positional_arg_counter++;
            }
        }
        if (positional_arg_counter < Args.POSITIONAL_ARGS.length && Args.POSITIONAL_ARGS[positional_arg_counter].required)
            MessageGenerator.briefErrorAndExit("Not enough positional arguments.\n");
        
        return arg_map;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Hello Java");
        MessageGenerator.briefMessage("Hello RapidWright");
    }
}