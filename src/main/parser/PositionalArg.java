package main.parser;

/**
 * Stores info relating to a positional argument.
 */
class PositionalArg {
    String name;
	boolean required;
	String help_str;

    public PositionalArg(String name, boolean required) {
        this(name, required, null);
    }

    public PositionalArg(String name, boolean required, String help_str) {
        this.name = name;
        this.required = required;
        this.help_str = help_str;
    }

	/**
	 * Stylized print this argument.
	 */
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