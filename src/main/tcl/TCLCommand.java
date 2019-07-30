package main.tcl;

import main.tcl.TCLEnum;

import com.xilinx.rapidwright.util.FileTools;

/**
 * Stores a single tcl command.
 */
class TCLCommand {
	TCLEnum tcl_cmd = null;
	String custom_options = null;
	String options = null;
	String filename = null;
	String custom_cmd = null;

	TCLCommand(TCLEnum tcl_cmd, String filename) {
		this(tcl_cmd, null, filename);
	}

	TCLCommand(TCLEnum tcl_cmd, String options, String filename) {
		this(tcl_cmd, options, null, filename);
	}

	TCLCommand(TCLEnum tcl_cmd, String options, String custom_opts, String filename) {
		this.tcl_cmd = tcl_cmd;
		this.custom_options = custom_opts;
		this.options = options;
		if (tcl_cmd.ext() != null)
			this.filename = FileTools.removeFileExtension(filename) + tcl_cmd.ext();
		else
			this.filename = null;
	}

	TCLCommand(String custom_cmd) {
		this.custom_cmd = custom_cmd;
	}

	/**
	 * @return A string that executes this command in a tcl script.
	 */
	@Override
	public String toString() {
		if (custom_cmd != null)
			return custom_cmd;

		String custom_opts = (custom_options == null ? "" : " " + custom_options);
		if (filename == null || tcl_cmd.ext() == null)
			return tcl_cmd.cmd(options) + custom_opts;
		return tcl_cmd.cmd(options) + custom_opts + " " + filename;
	}
}