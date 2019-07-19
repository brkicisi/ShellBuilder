
package parser;

import parser.PositionalArg;

import java.lang.NullPointerException;

/**
 * stores info relating to a recognized token
 */
class TokenArg {
    String name; // token name
    String help_str;
    String[] options; // str recognized to invoke the token
    PositionalArg[] args;

    public TokenArg(String name, String[] options) {
        this(name, options, null, null, null);
    }

    public TokenArg(String name, String[] options, String help_str) {
        this(name, options, null, null, help_str);
    }

    public TokenArg(String name, String[] options, String[] arg_names, boolean[] required, String help_str) {
        this.name = (name == null) ? "default" : name;
        this.help_str = help_str;
        this.options = (options == null) ? new String[] {} : options;
        // length of args is equal to shorter of arg_names and required
        int arg_len = 0;
        try {
            arg_len = arg_names.length < required.length ? arg_names.length : required.length;
        } catch (NullPointerException npe) {
        }

        if (arg_len <= 0)
            args = new PositionalArg[] {};
        else {
            args = new PositionalArg[arg_len];
            for (int i = 0; i < args.length; i++)
                args[i] = new PositionalArg(arg_names[i], required[i]);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < options.length; i++) {
            if (i != 0)
                sb.append("|");
            sb.append(options[i]);
        }
        if (args.length > 0)
            sb.append(" ");
        for (int i = 0; i < args.length; i++)
            sb.append(args[i].toString());
        sb.append("]");
        return sb.toString();
    }

    public String shortString() {
        if (options.length < 1)
            return "";
        StringBuilder sb = new StringBuilder("[");
        sb.append(options[0]);
        if (args.length > 0)
            sb.append(" ");
        for (int i = 0; i < args.length; i++)
            sb.append(args[i].toString());
        sb.append("]");
        return sb.toString();
    }

    public String getHelp() {
        return (help_str == null) ? "" : help_str;
    }
}