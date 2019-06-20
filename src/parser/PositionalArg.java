
package parser;

/**
 * stores info relating to a positional argument
 */
class PositionalArg {
    String name;
    String help_str;
    boolean required;

    public PositionalArg(String name, boolean required) {
        this(name, required, null);
    }

    public PositionalArg(String name, boolean required, String help_str) {
        this.name = name;
        this.required = required;
        this.help_str = help_str;
    }

    @Override
    public String toString() {
        if (required)
            return "<" + name + ">";
        return "[<" + name + ">]";
    }

    public String getHelp() {
        return (help_str == null) ? "" : help_str;
    }
}