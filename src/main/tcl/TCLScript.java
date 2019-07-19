package tcl;

import tcl.TCLCommand;

import com.xilinx.rapidwright.util.FileTools;

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
     *                        only "f", "q", "v" and combinations are supported.
     * @param tcl_script_name File in which to save the script.
     */
    public TCLScript(List<TCLEnum> cmds, String input_dcp, String output_file, String options, String tcl_script_name) {
        this.tcl_script_name = (tcl_script_name == null) ? "default_output.tcl" : tcl_script_name;
        this.output_file = (output_file == null) ? "default_output.dcp" : output_file;
        this.options = (options == null) ? "" : options;

        tcl_script = new ArrayList<>();
        if (options.contains("v")) // if verbose -> don't set quiet
            tcl_script.add(new TCLCommand(TCLEnum.SOURCE_RW, null));
        else
            tcl_script.add(new TCLCommand(TCLEnum.SOURCE_RW, "q", null));
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
     * Add a single tcl command to the script. The command is inserted as a single
     * line exactly as given. This function does not change the commands Vivado
     * understands, it simply allows the user to use commands. That I haven't
     * explicitly provided support for in TCLEnum. No error check is done to ensure
     * you have input a valid tcl command here.
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
     * 
     * @return Success.
     */
    public boolean run() {
        boolean wrote = write();
        if (!wrote)
            return false;

        FileTools.runCommand(run_vivado + " " + tcl_file.getAbsolutePath(), true);
        return true;
    }
}