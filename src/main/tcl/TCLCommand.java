package tcl;

import tcl.TCLEnum;

import com.xilinx.rapidwright.util.FileTools;
/**
 * Stores a single tcl command.
 */
class TCLCommand {
    TCLEnum tcl_cmd = null;
    String options = null;
    String filename = null;
    String custom_cmd = null;

    TCLCommand(TCLEnum tcl_cmd, String filename) {
        this(tcl_cmd, null, filename);
    }

    TCLCommand(TCLEnum tcl_cmd, String options, String filename) {
        this.tcl_cmd = tcl_cmd;
        this.options = options;
        if (tcl_cmd.ext() != null)
            this.filename = FileTools.removeFileExtension(filename) + tcl_cmd.ext();
        else
            this.filename = null;
    }

    TCLCommand(String custom_cmd){
        this.custom_cmd = custom_cmd;
    }

    @Override
    public String toString() {
        if(custom_cmd != null)
            return custom_cmd;

        if (filename == null || tcl_cmd.ext() == null)
            return tcl_cmd.cmd(options);
        return tcl_cmd.cmd(options) + " " + filename;
    }
}