package tcl;

import com.xilinx.rapidwright.util.FileTools;

import java.io.File;

/**
 * Enum containing supported tcl commands
 */
public enum TCLEnum {
    SOURCE_RW("source " + FileTools.getRapidWrightPath() + File.separator + FileTools.TCL_FOLDER_NAME + File.separator
            + "rapidwright.tcl", "qv", null),
    OPEN_DCP("open_checkpoint", "qv", ".dcp"), WRITE_DCP("write_checkpoint", "qvf", ".dcp"),
    PLACE("place_design", "qv", null), ROUTE("route_design", "qv", null), WRITE_EDIF("write_edif", "qvf", ".edf"),
    WRITE_LTX("write_debug_probes", "qvf", ".ltx"), WRITE_BITSTREAM("write_bitstream", "qvf", ".bit");

    private final String command;
    private final String options;
    private final String extension;
    private final String force = "-force";
    private final String quiet = "-quiet";
    private final String verbose = "-verbose";

    TCLEnum(String command, String options, String extension) {
        this.command = command;
        this.options = options;
        this.extension = extension;
    }

    String ext() {
        return extension;
    }

    String cmd() {
        return command;
    }

    String cmd(String opts) {
        StringBuffer sb = new StringBuffer(command);
        if (opts != null) {
            for (int i = 0; i < opts.length(); i++) {
                if (options.indexOf(opts.charAt(i)) == -1)
                    continue;
                switch (opts.charAt(i)) {
                case 'f':
                    sb.append(" " + force);
                    break;
                case 'q':
                    sb.append(" " + quiet);
                    break;
                case 'v':
                    sb.append(" " + verbose);
                default:
                    break;
                }
            }
        }
        return sb.toString();
    }
}