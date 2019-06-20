package top;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.util.MessageGenerator;

import parser.Parser;
import util.DesignUtils;

import java.util.List;
import java.util.Map;

public class ShellBuilder {

    Map<String, List<String>> arg_map = null;


    public ShellBuilder(){
        
    }

    /**
     * Get the first argument from the command line with given key.
     * 
     * @param key Argument to search for.
     * @return First input argument or null if no arguments found.
     */
    String getOneArg(String key) {
        List<String> list = (arg_map == null) ? null : arg_map.get(key);
        return (list == null) ? null : list.get(0);
    }

    /**
     * True if force was part of the command line args.
     * 
     * @return
     */
    public boolean force() {
        return arg_map.containsKey("force");
    }

    /**
     * True if verbose or extra_verbose was part of the command line args and quiet
     * wasn't.
     * 
     * @return
     */
    public boolean verbose() {
        return !quiet() && (arg_map.containsKey("verbose") || arg_map.containsKey("extra_verbose"));
    }

    /**
     * True if extra_verbose was part of the command line args and quiet wasn't.
     * 
     * @return
     */
    public boolean extraVerbose() {
        return !quiet() && arg_map.containsKey("extra_verbose");
    }

    /**
     * True if quiet was part of the command line args.
     * 
     * @return
     */
    public boolean quiet() {
        return arg_map.containsKey("quiet");
    }

    /**
     * Prints the string if verbose was part of the command line args.
     * 
     * @param s String to be printed.
     */
    private void printIfVerbose(String s) {
        if (verbose())
            MessageGenerator.briefMessage(s);
    }

    public void start(String[] args){
        Parser parser = new Parser();
        arg_map = parser.mapArgs(args);

        Design design = DesignUtils.safeReadCheckpoint("/nfs/ug/homes-1/b/brkicisi/Documents/2019_summer/ILA/tutorial_2/tutorial_2.runs/impl_1/tut_2_dsgn_wrapper_routed.dcp", true);
    }

    public static void main(String[] args) {
        ShellBuilder builder = new ShellBuilder();
        builder.start(args);
    }
}