package main.util;

import main.tcl.TCLEnum;
import main.tcl.TCLScript;
import main.worker.FileSys;

import com.xilinx.rapidwright.design.*;
import com.xilinx.rapidwright.util.FileTools;
import com.xilinx.rapidwright.util.MessageGenerator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Some utilities to assist with dcps and
 * {@link com.xilinx.rapidwright.design.Design} objects
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
		TCLScript script = new TCLScript(dcp_file, dcp_file, (verbose ? "f" : "qf"), dir + "/generate_edif.tcl");
		script.add(TCLEnum.WRITE_EDIF);
		script.run(false);
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
					// if having trouble with the above statement causing exceptions,
					// try replacing it with
					// zos.putNextEntry(new ZipEntry(ze.getName()));
					StringBuilder sb = new StringBuilder();
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
					// if having trouble with the above statement causing exceptions,
					// try replacing it with
					// zos.putNextEntry(new ZipEntry(ze.getName()));
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

	/**
	 * Copy a contraints file into a dcp file and adds a line to dcp.xml to reflect
	 * this change.
	 * <p>
	 * Used to copy constraints into a dcp file.
	 * 
	 * @depricated Use tcl command read_xdc instead.
	 * 
	 * @param src_constrs Constraints file to be copied.
	 * @param dest_dcp    Dcp to add constraints to.
	 * @param verbose     Print extra messages.
	 * @param dir         Temp dir to write an intermediate step to before it is
	 *                    deleted (suggested: iii dir).
	 * @return True if constraints inserted successfully into dcp.
	 * 
	 * @deprecated Use {@link main.tcl.TCLEnum#READ_XDC read_xdc} and
	 *             {@link main.tcl.TCLEnum#WRITE_XDC write_xdc} in
	 *             {@link main.tcl.TCLScript tcl script} instead.
	 */
	public static boolean copyConstrsFileIntoDCP(File src_constrs, File dest_dcp, boolean verbose, File dir) {
		if (src_constrs == null) {
			if (dest_dcp == null)
				printIfVerbose("Neither source constraints file nor output dcp was specified for copy.", verbose);
			else {
				printIfVerbose("Source constraints file was not specified.", verbose);
				printIfVerbose("No constraints file will be copied to output dcp '" + dest_dcp.getAbsolutePath() + "'.",
						verbose);
			}
			return false;
		}
		if (dest_dcp == null) {
			printIfVerbose("Destination dcp was not specified.", verbose);
			printIfVerbose("Constraints file '" + src_constrs.getAbsolutePath() + "' will not be copied.'", verbose);
			return false;
		}
		if (!src_constrs.isFile()) {
			printIfVerbose("Source constraints file '" + src_constrs.getAbsolutePath() + "' could not be found.",
					verbose);
			printIfVerbose("No constraints file will be copied to output dcp '" + dest_dcp.getAbsolutePath() + "'.",
					verbose);
			return false;
		}
		if (!dest_dcp.isFile()) {
			printIfVerbose("Destination dcp '" + dest_dcp.getAbsolutePath() + "' could not be found.", verbose);
			printIfVerbose("Constraints file '" + src_constrs.getAbsolutePath() + "' will not be copied here.'",
					verbose);
			return false;
		}
		if (dir == null || !dir.isDirectory()) {
			printIfVerbose("Temp dir was not a directory. Using pwd instead.", verbose);
			dir = new FileSys(verbose).getRoot(FileSys.FILE_ROOT.PWD);
			if (dir == null) // error message already displayed by FileSys.setPWD() called by FileSys()
				MessageGenerator.briefErrorAndExit("Exiting.");
		} else if (dir.equals(dest_dcp.getParentFile())) {
			printIfVerbose("Temp dir is the parent directory of the dcp. Using pwd instead.", verbose);
			dir = new FileSys(verbose).getRoot(FileSys.FILE_ROOT.PWD);
			if (dir == null) // error message already displayed by FileSys.setPWD() called by FileSys()
				MessageGenerator.briefErrorAndExit("Exiting.");
			if (dir.equals(dest_dcp.getParentFile())) {
				printIfVerbose("Pwd is the parent directory of the dcp. Can't move file without a valid temp dir.",
						verbose);
				return false;
			}
		}

		// Move dcp to temp directory
		Path from = Paths.get(dest_dcp.getAbsolutePath());
		File src_dcp = new File(dir, dest_dcp.getName());
		Path to = Paths.get(src_dcp.getAbsolutePath());
		try {
			Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ioe) {
			printIfVerbose("Error while moving dcp '" + dest_dcp.getAbsolutePath() + "' to '"
					+ src_dcp.getAbsolutePath() + "'", verbose);
			return false;
		}

		// Modify file while moving it back to it's original location.
		ZipInputStream zis = null;
		ZipOutputStream zos = null;
		FileInputStream fis = null;
		byte[] buffer = new byte[1024];
		int len;

		printIfVerbose("\nStarting add constraints to dcp process.", verbose);
		try {
			String xdc_filename = null;

			zis = new ZipInputStream(new FileInputStream(src_dcp));
			ZipEntry ze = null;
			while ((ze = zis.getNextEntry()) != null)
				if (ze.getName().endsWith(".edf"))
					xdc_filename = ze.getName().replace(".edf", ".xdc");
			zis.close();
			zis = new ZipInputStream(new FileInputStream(src_dcp));
			zos = new ZipOutputStream(new FileOutputStream(dest_dcp));

			while ((ze = zis.getNextEntry()) != null) {
				if (ze.getName().endsWith("dcp.xml")) {
					// add line to dcp.xml and copy
					zos.putNextEntry(new ZipEntry(ze.getName()));
					// zos.putNextEntry(ze);
					StringBuilder sb = new StringBuilder();
					while ((len = zis.read(buffer)) > 0)
						sb.append(new String(buffer, 0, len));
					String dcpxml = sb.toString();

					int first_file = dcpxml.indexOf("<File");
					if (first_file >= 0) {
						int insert_loc = dcpxml.lastIndexOf("\n", first_file) + 1;
						if (insert_loc > 0) {
							// example
							// \t<File Type="XDC" Name="constrs.xdc" ModTime="1563396045"/>
							sb = new StringBuilder();
							sb.append(dcpxml.substring(insert_loc, first_file));
							sb.append("<File Type=\"XDC\" Name=\"");
							sb.append(xdc_filename);
							sb.append("\" ModTime=\"");
							long time = System.currentTimeMillis() / 1000;
							sb.append(time);
							sb.append("\"/>\n");
							dcpxml = dcpxml.substring(0, insert_loc) + sb.toString() + dcpxml.substring(insert_loc);
						}
					}
					byte[] buffer2 = dcpxml.getBytes();
					zos.write(buffer2, 0, buffer2.length);
					zos.closeEntry();
				} else {
					// copy
					zos.putNextEntry(new ZipEntry(ze.getName()));
					while ((len = zis.read(buffer)) > 0)
						zos.write(buffer, 0, len);
					zos.closeEntry();
				}
				zis.closeEntry();
			}

			// Add constraints
			fis = new FileInputStream(src_constrs);
			zos.putNextEntry(new ZipEntry(xdc_filename));
			while ((len = fis.read(buffer)) > 0)
				zos.write(buffer, 0, len);
			zos.closeEntry();
			fis.close();

			zis.close();
			zos.close();

			printIfVerbose("Deleting temp dcp file.\n", verbose);
			FileTools.deleteFile(src_dcp.getAbsolutePath());

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
			if (fis != null)
				try {
					fis.close();
				} catch (IOException ioe) {
				}
			return false;
		}
		return true;
	}
}