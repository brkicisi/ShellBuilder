package util;

import tcl.TCLEnum;
import tcl.TCLScript;

import com.xilinx.rapidwright.design.*;
import com.xilinx.rapidwright.util.FileTools;
import com.xilinx.rapidwright.util.MessageGenerator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class DesignUtils {

    private static void printIfVerbose(boolean verbose, String msg){
        if(verbose)
            MessageGenerator.briefMessage((msg == null) ? "" : msg);
    }
    /**
     * Runs write_edif tcl command using Vivado on the given design. Should write an
     * unencrypted edif file to the same directory as the given dcp file.
     * 
     * @param dcp_file Design checkpoint to write edif of.
     */
    public static void generateEdif(String dcp_file) {
        TCLScript script = new TCLScript(dcp_file, dcp_file, "generate_edif.tcl");
        script.add(TCLEnum.WRITE_EDIF);
        script.run();
    }

    public static Design safeReadCheckpoint(File f_dcp, boolean verbose) {
        return safeReadCheckpoint(f_dcp.getAbsolutePath(), verbose);
    }

    /**
     * Reads a design checkpoint file. Catches the case where the edif file is
     * encrypted and tries to generate the required edif file using generateEdif().
     * 
     * @param dcp_file Design checkpoint to open.
     * @return A Design object.
     */
    public static Design safeReadCheckpoint(String dcp_file, boolean verbose) {
        Design d = null;
        try {
            printIfVerbose(verbose, "Loading design from '" + dcp_file + "'.");
            d = Design.readCheckpoint(dcp_file);
        } catch (RuntimeException e) {
            printIfVerbose(verbose, "\nCouldn't open design at '" + dcp_file + "' due to encrypted edif.");
            printIfVerbose(verbose, "Trying to generate unencrypted edif using vivado.\n");
            generateEdif(dcp_file);
            printIfVerbose(verbose, "Trying to open design again.\n");
            d = Design.readCheckpoint(dcp_file);
        }
        return d;
    }

    /**
     * Modify dcp/dsgn.edf because it won't open in Vivado without this change. This
     * change was determined expirementally and is not guaranteed to be the best way
     * to solve this issue.
     * 
     * @param input_dcp  Absolute path to dcp file containing edif file to be
     *                   changed.
     * @param output_dcp Absolute path to write dcp file containing changed edif.
     * @return Completed successfully.
     */
    public static boolean fixEdifInDCP(String input_dcp, String output_dcp, boolean verbose) {
        ZipInputStream zis = null;
        ZipOutputStream zos = null;
        byte[] buffer = new byte[1024];
        int len;

        printIfVerbose(verbose, "\nStarting fix edif process.");
        try {
            zis = new ZipInputStream(new FileInputStream(input_dcp));
            zos = new ZipOutputStream(new FileOutputStream(output_dcp));

            ZipEntry ze = null;
            while ((ze = zis.getNextEntry()) != null) {
                if (ze.getName().endsWith(".edf")) {
                    // fix edif and copy
                    zos.putNextEntry(ze);
                    StringBuffer sb = new StringBuffer();
                    while ((len = zis.read(buffer)) > 0)
                        sb.append(new String(buffer, 0, len));
                    String edif_str = sb.toString();

                    String bad_string = "(instance top (viewref design_1_wrapper (cellref design_1_wrapper (libraryref work)))";
                    String good_string = "(instance top (viewref netlist (cellref design_1_wrapper (libraryref work)))";
                    int index = edif_str.lastIndexOf(bad_string);
                    byte[] buffer2;
                    if (index >= 0) { // found bad_string
                        printIfVerbose(verbose, "Replacing bad_string.");
                        String fixed_str = edif_str.substring(0, index) + good_string
                                + edif_str.substring(index + bad_string.length());
                        buffer2 = fixed_str.getBytes();
                    } else {
                        printIfVerbose(verbose, "Warning: didn't find bad_string to replace. No change made to edif file.");
                        printIfVerbose(verbose, "bad_string = '" + bad_string + "'");
                        buffer2 = edif_str.getBytes();
                    }

                    zos.write(buffer2, 0, buffer2.length);
                    zos.closeEntry();
                } else {
                    // copy
                    zos.putNextEntry(ze);
                    while ((len = zis.read(buffer)) > 0)
                        zos.write(buffer, 0, len);
                    zos.closeEntry();
                }
                zis.closeEntry();
            }
            zis.close();
            zos.close();

            printIfVerbose(verbose, "Deleting old dcp file.\n");
            FileTools.deleteFile(input_dcp);

        } catch (IOException e) {
            e.printStackTrace();
            if (zis != null)
                try {
                    zis.close();
                } catch (IOException ioe) {
                }
            if (zos != null)
                try {
                    zos.close();
                } catch (IOException ioe) {
                }
            return false;
        }
        return true;
    }
}