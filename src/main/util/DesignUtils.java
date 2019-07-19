package main.util;

import main.tcl.TCLEnum;
import main.tcl.TCLScript;

import com.xilinx.rapidwright.design.*;
import com.xilinx.rapidwright.util.FileTools;
import com.xilinx.rapidwright.util.MessageGenerator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Some utilities to assist with dcps and {@link com.xilinx.rapidwright.design.Design} objects
 */
public class DesignUtils {
	private static void printIfVerbose(String msg, boolean verbose) {
		if (verbose)
			MessageGenerator.briefMessage((msg == null) ? "" : msg);
	}

	/**
	 * Runs write_edif tcl command using Vivado on the given design. Should write an
	 * unencrypted edif file to the same directory as the given dcp file.
	 * 
	 * @param dcp_file Design checkpoint to write edif of.
	 */
	public static void generateEdif(String dcp_file, boolean verbose, String dir) {
		TCLScript script = new TCLScript(dcp_file, dcp_file, (verbose ? "f" : "qf"), dir + "/.generate_edif.tcl");
		script.add(TCLEnum.WRITE_EDIF);
		script.run();
	}

	public static Design safeReadCheckpoint(File f_dcp, boolean verbose, File dir) {
		return safeReadCheckpoint(f_dcp.getAbsolutePath(), verbose, dir.getAbsolutePath());
	}

	public static Design safeReadCheckpoint(String f_dcp, boolean verbose, File dir) {
		return safeReadCheckpoint(f_dcp, verbose, dir.getAbsolutePath());
	}

	public static Design safeReadCheckpoint(File f_dcp, boolean verbose, String dir) {
		return safeReadCheckpoint(f_dcp.getAbsolutePath(), verbose, dir);
	}

	/**
	 * Reads a design checkpoint file. Catches the case where the edif file is
	 * encrypted and tries to generate the required edif file using generateEdif().
	 * 
	 * @param dcp_file Design checkpoint to open.
	 * @param verbose  Print extra messages.
	 * @param dir      Temp dir to write tcl file to if required (suggested: iii
	 *                 dir).
	 * 
	 * @return A Design object representing the dcp_file.
	 */
	public static Design safeReadCheckpoint(String dcp_file, boolean verbose, String dir) {
		Design d = null;
		try {
			printIfVerbose("\nLoading design from '" + dcp_file + "'.", verbose);
			d = Design.readCheckpoint(dcp_file);
		} catch (RuntimeException e) {
			printIfVerbose("\nCouldn't open design at '" + dcp_file + "' due to encrypted edif.", verbose);
			printIfVerbose("Trying to generate unencrypted edif using vivado.\n", verbose);
			if (dir == null) {
				int end = dcp_file.lastIndexOf("/");
				dir = (end >= 0) ? "" : dcp_file.substring(0, end);
			}
			generateEdif(dcp_file, verbose, dir);
			printIfVerbose("\nTrying to open design again.\n", verbose);
			d = Design.readCheckpoint(dcp_file);
		}
		return d;
	}

	public static boolean fixEdifInDCPTop(String input_dcp, String output_dcp, String cell_name, boolean verbose) {
		return fixEdifInDCP(input_dcp, output_dcp, Arrays.asList("top"), Arrays.asList(cell_name), verbose);
	}

	public static boolean fixEdifInDCPTop(String input_dcp, String output_dcp, List<String> cell_names,
			boolean verbose) {
		int size = cell_names.size();
		List<String> top_list = new ArrayList<>(size);
		for (int i = 0; i < size; i++)
			top_list.add("top");
		return fixEdifInDCP(input_dcp, output_dcp, top_list, cell_names, verbose);
	}

	public static boolean fixEdifInDCP(String input_dcp, String output_dcp, String instance_name, String cell_name,
			boolean verbose) {
		return fixEdifInDCP(input_dcp, output_dcp, Arrays.asList(instance_name), Arrays.asList(cell_name), verbose);
	}

	public static boolean fixEdifInDCP(String input_dcp, String output_dcp, List<String> cell_names, boolean verbose) {
		return fixEdifInDCP(input_dcp, output_dcp, cell_names, cell_names, verbose);
	}

	/**
	 * Modify dcp/dsgn.edf because it won't open in Vivado without this change.
	 * <p>
	 * Note: This change was determined expirementally and is not guaranteed to be
	 * the best way to solve this issue.
	 * <p>
	 * If you are careful then you may never need this functionality.
	 * 
	 * @param input_dcp      Absolute path to dcp file containing edif file to be
	 *                       changed. Must be different from output_dcp.
	 * @param output_dcp     Absolute path to write dcp file containing changed
	 *                       edif. Must be different from input_dcp.
	 * @param instance_names List of names of cell instances.
	 * @param cell_names     List of names of cells which are wrongly named in edif.
	 * @param verbose        Print progress messages.
	 * @return Completed successfully.
	 */
	public static boolean fixEdifInDCP(String input_dcp, String output_dcp, List<String> instance_names,
			List<String> cell_names, boolean verbose) {
		if (cell_names == null)
			cell_names = Arrays.asList("design_1_wrapper");

		ZipInputStream zis = null;
		ZipOutputStream zos = null;
		byte[] buffer = new byte[1024];
		int len;

		printIfVerbose("\nStarting fix edif process.", verbose);
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

					for (int i = 0; i < instance_names.size() && i < cell_names.size(); i++) {
						String cell_name = cell_names.get(i);
						String instance_name = instance_names.get(i);
						String bad_string = "(instance " + instance_name + " (viewref " + cell_name + " (cellref "
								+ cell_name + " (libraryref work)))";
						String good_string = "(instance " + instance_name + " (viewref netlist (cellref " + cell_name
								+ " (libraryref work)))";
						int index = edif_str.lastIndexOf(bad_string);
						if (index >= 0) { // found bad_string
							printIfVerbose("Replacing bad_string.", verbose);
							edif_str = edif_str.substring(0, index) + good_string
									+ edif_str.substring(index + bad_string.length());
						} else {
							printIfVerbose("Warning: didn't find bad_string to replace. No change made to edif file.",
									verbose);
							printIfVerbose("\tbad_string = '" + bad_string + "'", verbose);
						}
					}
					byte[] buffer2 = edif_str.getBytes();
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

			printIfVerbose("Deleting old dcp file.\n", verbose);
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