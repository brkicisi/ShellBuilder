package main.tcl;

import main.tcl.TCLCommand;

import com.xilinx.rapidwright.util.FileTools;
import com.xilinx.rapidwright.util.MessageGenerator;

import java.util.List;
import java.util.ArrayList;
import java.io.File;

/**
 * Stores a list of tcl commands that can be run using Vivado.
 */
public class TCLScript {
	List<TCLCommand> tcl_script = null;
	private static final String run_vivado = "vivado -mode batch -log vivado.log -journal vivado.jou -source";
	String tcl_script_name = null;
	String output_file = null;
	String options = null;
	File tcl_file = null;

	public TCLScript(String input_dcp, String output_file, String tcl_script_name) {
		this(null, input_dcp, output_file, null, tcl_script_name);
	}

	public TCLScript(String input_dcp, String output_file, String options, String tcl_script_name) {
		this(null, input_dcp, output_file, options, tcl_script_name);
	}

	public TCLScript(List<TCLEnum> cmds, String input_dcp, String output_file, String tcl_script_name) {
		this(cmds, input_dcp, output_file, null, tcl_script_name);
	}

	/**
	 * Construct a tcl script object. First sources RapidWright. Then opens
	 * checkpoint. Then executes cmds.
	 * 
	 * @param cmds            TCL commands to execute after opening checkpoint.
	 * @param input_dcp       Design checkpoint file to open.
	 * @param output_file     File to store to. Extension is automatically changed
	 *                        based on command (directory is not changed).
	 * @param options         String of options to execute commands with. Currently
	 *                        only "f", "q", "v" and combinations are currently
	 *                        supported.
	 * @param tcl_script_name File in which to save the script.
	 */
	public TCLScript(List<TCLEnum> cmds, String input_dcp, String output_file, String options, String tcl_script_name) {
		this.tcl_script_name = (tcl_script_name == null) ? "default_output.tcl" : tcl_script_name;
		this.output_file = (output_file == null) ? "default_output.dcp" : output_file;
		this.options = (options == null) ? "" : options;

		tcl_script = new ArrayList<>();
		if (options != null && options.contains("v")) // if verbose -> don't set quiet
			tcl_script.add(new TCLCommand(TCLEnum.SOURCE_RW, null));
		else
			tcl_script.add(new TCLCommand(TCLEnum.SOURCE_RW, "q", null));
		if (input_dcp != null)
			tcl_script.add(new TCLCommand(TCLEnum.OPEN_DCP, options, input_dcp));

		if (cmds != null)
			for (TCLEnum te : cmds)
				tcl_script.add(new TCLCommand(te, options, output_file));
	}

	/**
	 * Add a single command to the script using script's default options.
	 * 
	 * @param te Type of command to add.
	 */
	public void add(TCLEnum te) {
		tcl_script.add(new TCLCommand(te, options, output_file));
	}

	/**
	 * Add a single command to the script using given options.
	 * 
	 * @param te   Type of command to add.
	 * @param opts Options to use.
	 */
	public void add(TCLEnum te, String opts) {
		tcl_script.add(new TCLCommand(te, opts, output_file));
	}

	/**
	 * Add a single command to the script using given options plus user specified
	 * custom options.
	 * <p>
	 * This function does not change the options Vivado understands, it simply
	 * allows the user to use options that aren't explicitly supported in TCLEnum.
	 * No error check is done to ensure you have input a valid tcl command here.
	 * 
	 * @param te          Type of command to add.
	 * @param opts        Options to use.
	 * @param custom_opts Custom options to use exactly as given.
	 */
	public void add(TCLEnum te, String opts, String custom_opts) {
		tcl_script.add(new TCLCommand(te, opts, custom_opts, output_file));
	}

	/**
	 * Add a single command to the script using given options plus user specified
	 * custom options and file.
	 * <p>
	 * This function does not change the options Vivado understands, it simply
	 * allows the user to use options that aren't explicitly supported in TCLEnum.
	 * No error check is done to ensure you have input a valid tcl command here.
	 * 
	 * @param te          Type of command to add.
	 * @param opts        Options to use.
	 * @param custom_opts Custom options to use exactly as given.
	 * @param filename    File to read/write from/to with the command
	 */
	public void add(TCLEnum te, String opts, String custom_opts, String filename) {
		tcl_script.add(new TCLCommand(te, opts, custom_opts, filename));
	}

	/**
	 * Add a single tcl command to the script as a single line exactly as given.
	 * <p>
	 * This function does not change the commands Vivado understands, it simply
	 * allows the user to use commands that aren't explicitly supported in TCLEnum.
	 * No error check is done to ensure you have input a valid tcl command here.
	 * 
	 * @param custom_cmd Custom tcl command to insert.
	 */
	public void addCustomCmd(String custom_cmd) {
		tcl_script.add(new TCLCommand(custom_cmd));
	}

	/**
	 * Write tcl script to file.
	 * 
	 * @return Success.
	 */
	public boolean write() {
		tcl_file = new File(tcl_script_name);
		List<String> tcl_strs = new ArrayList<>();
		for (TCLCommand cmd : tcl_script)
			tcl_strs.add(cmd.toString());
		FileTools.writeLinesToTextFile(tcl_strs, tcl_file.getAbsolutePath());
		return tcl_file.exists();
	}

	/**
	 * Execute tcl script.
	 * <p>
	 * Throw an error if the process does not return 0 (success).
	 * 
	 * @return Null upon error. Else same return as from
	 *         {@link FileTools#runCommand}.
	 */
	public Integer run() {
		return run(true);
	}

	public boolean USE_DEFAULT_VIVADO_VERSION = false;

	/**
	 * Execute tcl script.
	 * <p>
	 * To run in a different version of Vivado, use {@link FileTools#runCommand} to
	 * source Vivado before calling this function.
	 * 
	 * @param throw_error Throw an error if the process does not return 0 (success).
	 * @return Null upon error in {@link #write}. Else same return as from
	 *         {@link FileTools#runCommand}.
	 */
	public Integer run(boolean throw_error) {
		boolean wrote = write();
		if (!wrote)
			return null;

		Integer ret = 0;
		if (USE_DEFAULT_VIVADO_VERSION) {
			ret = FileTools.runCommand(run_vivado + " " + tcl_file.getAbsolutePath(), true);
		} else {
			String bash_file = tcl_file.getAbsolutePath().replace(".tcl", ".sh");
			List<String> bash_lines = new ArrayList<>();
			bash_lines.add("#! /bin/bash");
			bash_lines.add("source /cad1/Xilinx/Vivado/2018.1/settings64.sh"); // TODO Vivado version
			bash_lines.add(run_vivado + " " + tcl_file.getAbsolutePath());
			FileTools.writeLinesToTextFile(bash_lines, bash_file);

			MessageGenerator.briefMessage(""); // new line
			ret = FileTools.runCommand("bash " + bash_file, true);
		}

		if (throw_error && ret != 0)
			MessageGenerator.briefErrorAndExit("Tcl script returned error code " + ret + ".");
		return ret;
	}
}