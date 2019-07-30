package main.tcl;

import com.xilinx.rapidwright.util.FileTools;

import java.io.File;

/**
 * Enum containing supported tcl commands.
 */
public enum TCLEnum {
    SOURCE_RW("source " + FileTools.getRapidWrightPath() + File.separator + FileTools.TCL_FOLDER_NAME + File.separator
            + "rapidwright.tcl", "qv", null),
	OPEN_DCP("open_checkpoint", "qv", ".dcp"), WRITE_DCP("write_checkpoint", "qvf", ".dcp"),
	READ_XDC("read_xdc", "qv", ".xdc"), WRITE_XDC("write_xdc", "qvf", ".xdc"),
    OPT("opt_design", "qv", null), PLACE("place_design", "qv", null), ROUTE("route_design", "qv", null),
    WRITE_EDIF("write_edif", "qvf", ".edf"), WRITE_LTX("write_debug_probes", "qvf", ".ltx"),
    WRITE_BITSTREAM("write_bitstream", "qvf", ".bit");

    private final String command;
    private final String allowed_options;
    private final String extension;
    private static final String force = "-force";
    private static final String quiet = "-quiet";
    private static final String verbose = "-verbose";

    TCLEnum(String command, String allowed_options, String extension) {
        this.command = command;
        this.allowed_options = allowed_options;
        this.extension = extension;
    }

    String ext() {
        return extension;
    }

    String cmd() {
        return command;
    }

    String cmd(String opts) {
        return command + getOpts(opts, allowed_options);
    }

    public static String getOpts(String opts) {
        return getOpts(opts, null);
    }

    /**
     * Convert an options string into a string of full option tags.
     * 
     * Example "fq" -> " -force -quiet"
     * 
     * @param opts            Options to include in options string. Limited by
     *                        options recognized by TCLEnum.
     * @param allowed_options Total list of valid options for the command the
     *                        options will be applied on.
     * @return A string of tcl option tags.
     */
    public static String getOpts(String opts, String allowed_options) {
        StringBuffer sb = new StringBuffer();
        if (opts != null) {
            for (int i = 0; i < opts.length(); i++) {
                if (allowed_options != null && allowed_options.indexOf(opts.charAt(i)) == -1)
                    continue;
                sb.append(getOpt(opts.charAt(i)));
            }
        }
        return sb.toString();
    }

    private static String getOpt(char ch) {
        switch (ch) {
        case 'f':
            return " " + force;
        case 'q':
            return " " + quiet;
        case 'v':
            return " " + verbose;
        default:
            return "";
        }
    }
}