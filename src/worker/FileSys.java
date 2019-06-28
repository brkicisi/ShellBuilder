package worker;

import com.xilinx.rapidwright.util.MessageGenerator;

import java.io.File;
import java.io.IOException;

public class FileSys {
	File pwd_dir = null;
	File iii_dir = null;
	File ooc_dir = null;
	File out_dir = null;
	boolean verbose = false;

	private void printIfVerbose(String msg) {
		if (verbose)
			MessageGenerator.briefMessage((msg == null) ? "" : msg);
	}

	/**
	 * Initialize an object to help search for files.
	 * 
	 * @param verbose Print extra messages.
	 */
	public FileSys(boolean verbose) {
		this.verbose = verbose;
		setPWD();
	}

	public void setPWD() {
		String pwd_str = System.getProperty("user.dir");
		if (pwd_str == null)
			MessageGenerator.briefError("Could not access pwd.\n");

		pwd_dir = new File(pwd_str);
		if (pwd_dir == null || !pwd_dir.isDirectory())
			MessageGenerator.briefError("Could not access/find pwd '" + pwd_str + "'.\n");
	}

	/**
	 * Sets a root directory (iii, ooc or out).
	 * 
	 * @param root     Which directory to set root for.
	 * @param filename Path to set as root.
	 */
	public void setDirRoot(FILE_ROOT root, String filename) {
		String create_dir = null;
		boolean is_iii = root == FILE_ROOT.III;
		if (filename != null) {
			if (!is_iii || filename.endsWith("/.iii"))
				create_dir = filename;
			else
				create_dir = filename + "/.iii";
		} else if (is_iii)
			create_dir = ".iii";
		File root_dir = getDir(filename, create_dir, false);
		if (root_dir == null)
			MessageGenerator.briefErrorAndExit("Could not find directory '" + filename + "'");

		switch (root) {
		case III:
			if (iii_dir != null)
				printIfVerbose("Moving 'iii' directory from '" + iii_dir.getAbsolutePath() + "'.");
			iii_dir = root_dir;
			printIfVerbose("'iii' directory is at '" + iii_dir.getAbsolutePath() + "'");
			break;
		case OOC:
			if (ooc_dir != null)
				printIfVerbose("Moving 'ooc' directory from '" + iii_dir.getAbsolutePath() + "'.");
			ooc_dir = root_dir;
			printIfVerbose("'ooc' directory is at '" + ooc_dir.getAbsolutePath() + "'");
			break;
		case OUT:
			if (out_dir != null)
				printIfVerbose("Moving 'out' directory from '" + iii_dir.getAbsolutePath() + "'.");
			out_dir = root_dir;
			printIfVerbose("'out' directory is at '" + out_dir.getAbsolutePath() + "'");
			break;
		default:
			return;
		}
		printIfVerbose(""); // line break
	}

	public File getExistingFile(String filename, boolean error_if_not_found) {
		return getFile(filename, null, error_if_not_found);
	}

	public File getFile(String filename, String create_name, boolean error_if_not_found) {
		return getFileOrDir(filename, false, create_name, error_if_not_found);
	}

	public File getDir(String filename, String create_name, boolean error_if_not_found) {
		return getFileOrDir(filename, true, create_name, error_if_not_found);
	}

	/**
	 * Searches for filename. If neither create is true then not finding the file is
	 * an error
	 * 
	 * @param filename           File/dir to search for
	 * @param dir                True if searching for directory
	 * @param create_name        Path/name to use to create file using
	 *                           toFile(filename). Will not try to create if
	 *                           create_name is null.
	 * @param error_if_not_found Exit with error if can't find file
	 * @return File that was found or created. Null if failed.
	 */
	public File getFileOrDir(String filename, boolean dir, String create_name, boolean error_if_not_found) {

		File f = null;

		// try to find file
		if (filename != null) {
			f = toFile(filename);

			if (dir ? f.isDirectory() : f.exists())
				return f;

			if (error_if_not_found)
				MessageGenerator.briefErrorAndExit(
						"Could not access/find " + (dir ? "directory" : "file") + " '" + f.getPath() + "'.\n");

			printIfVerbose("Could not access/find " + (dir ? "directory" : "file") + " '" + f.getPath() + "'.");
		} else if (error_if_not_found)
			MessageGenerator.briefErrorAndExit("Cannot find null directory.\nExiting.");

		if (create_name == null) {
			return null;
		}

		// create file
		f = toFile(create_name);

		if (dir) {
			if (!f.isDirectory()) {
				printIfVerbose("Creating new directory '" + f.getAbsolutePath() + "'.");
				if (!f.mkdirs())
					MessageGenerator.briefErrorAndExit("Failed to create directroy '" + f.getPath() + "'.\n");
			}
			return f;
		}
		if (!f.exists()) {
			try {
				printIfVerbose("Creating new file '" + f.getAbsolutePath() + "'.");
				f.createNewFile();
			} catch (IOException ioe) {
				System.err.println("IOException in : " + ioe.getMessage());
				ioe.printStackTrace();
				f = null;
			}
		}
		return f;
	}

	static enum FILE_ROOT {
		PWD(""), ROOT("/"), HOME("~"), III("#iii/"), OOC("#ooc/"), OUT("#out/");
		String escape_seq = null;

		FILE_ROOT(String seq) {
			escape_seq = seq;
		}

		String escapeSeq() {
			return escape_seq;
		}

		static FILE_ROOT which(String filename) {
			if (filename == null)
				MessageGenerator.briefErrorAndExit("\nCannot determine root of null filename.\nExiting.");
			for (FILE_ROOT r : FILE_ROOT.values())
				if (filename.startsWith(r.escape_seq) && (r != PWD))
					return r;
			return PWD;
		}

		public String subsumePath(String filename) {
			return escape_seq + filename;
		}
	}

	/**
	 * Creates a file object from a filename. Uses escape sequence at beginning of
	 * string if found.
	 * 
	 * @param filename Filename to create file object from.
	 * @return File object representing file. May not actually exist in system.
	 */
	File toFile(String filename) {
		return toFile(filename, true);
	}

	File toFile(String filename, boolean use_escapes) {
		if (filename == null)
			return null;

		File f = null;
		// filename starts with '/' or '~', assume it is a complete path.
		if (filename.startsWith(FILE_ROOT.ROOT.escapeSeq()) || filename.startsWith(FILE_ROOT.HOME.escapeSeq()))
			f = new File(filename);
		else if (use_escapes && (iii_dir != null) && filename.startsWith(FILE_ROOT.III.escapeSeq())) {
			if (iii_dir == null) {
				printIfVerbose("\nCannot create file relative to iii, iii_dir is not defined.");
				return null;
			}
			f = new File(iii_dir, filename.replaceFirst(FILE_ROOT.III.escapeSeq(), ""));
		} else if (use_escapes && (ooc_dir != null) && filename.startsWith(FILE_ROOT.OOC.escapeSeq())) {
			if (ooc_dir == null) {
				printIfVerbose("\nCannot create file relative to ooc, ooc_dir is not defined.");
				return null;
			}
			f = new File(ooc_dir, filename.replaceFirst(FILE_ROOT.OOC.escapeSeq(), ""));
		} else if (use_escapes && (out_dir != null) && filename.startsWith(FILE_ROOT.OUT.escapeSeq())) {
			if (out_dir == null) {
				printIfVerbose("\nCannot create file relative to out, out_dir is not defined.");
				return null;
			}
			f = new File(out_dir, filename.replaceFirst(FILE_ROOT.OUT.escapeSeq(), ""));
		} else // assume filename relative to pwd
			f = new File(pwd_dir, filename);

		return f;
	}
}