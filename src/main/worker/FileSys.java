package main.worker;

import com.xilinx.rapidwright.util.MessageGenerator;

import java.io.File;
import java.io.IOException;

/**
 * Simple representation of the file system to simplify finding and checking
 * existance of files and directories.
 * <p>
 * Allows specification of 3 extra "root" directories (iii, ooc, out). This
 * helps shorten paths input by user by allowing them to specify up to 3
 * personalized "roots" and then specify other files relative to any
 * {@link FILE_ROOT}.
 */
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

	/**
	 * Sets the pwd by querrying the system.
	 */
	public void setPWD() {
		String pwd_str = System.getProperty("user.dir");
		if (pwd_str == null)
			MessageGenerator.briefError("Could not access pwd.\n");

		pwd_dir = new File(pwd_str);
		if (pwd_dir == null || !pwd_dir.isDirectory())
			MessageGenerator.briefError("Could not access/find pwd '" + pwd_str + "'.\n");
	}

	/**
	 * Sets a customizable root directory (iii, ooc or out).
	 * 
	 * @param root     Which root to set directory for.
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
		if (root_dir == null) {
			MessageGenerator.briefError("Could not find directory '" + filename + "'");
			return;
		}

		setDirRoot(root, root_dir, false);
	}

	/**
	 * Sets a customizable root directory (iii, ooc or out).
	 * 
	 * @param root     Which root to set directory for.
	 * @param root_dir File to set as root dir.
	 */
	public void setDirRoot(FILE_ROOT root, File root_dir) {
		setDirRoot(root, root_dir, false);
	}

	/**
	 * Sets a root directory (iii, ooc or out).
	 * 
	 * It has it's own verbose flag because the message is probably not very useful.
	 * However it is included because it may help find problems with the root
	 * directories not being what is expected.
	 * 
	 * @param root     Which root to set directory for.
	 * @param root_dir File to set as root dir.
	 * @param verbose  You probably want this to be false.
	 */
	void setDirRoot(FILE_ROOT root, File root_dir, boolean verbose) {
		if (root == null) {
			MessageGenerator.briefError("Root 'null' is not a valid root to set.");
			return;
		}
		if (root_dir == null) {
			MessageGenerator.briefError("Tried to set " + root.toString() + " to null.");
			return;
		} else if (!root_dir.isDirectory()) {
			MessageGenerator.briefError("The input was not a directory '" + root_dir.getAbsolutePath() + "'");
			return;
		}

		switch (root) {
		case III:
			if (verbose && iii_dir != null)
				printIfVerbose("Moving 'iii' directory from '" + iii_dir.getAbsolutePath() + "'.");
			iii_dir = root_dir;
			if (verbose)
				printIfVerbose("'iii' directory is at '" + iii_dir.getAbsolutePath() + "'");
			break;
		case OOC:
			if (verbose && ooc_dir != null)
				printIfVerbose("Moving 'ooc' directory from '" + iii_dir.getAbsolutePath() + "'.");
			ooc_dir = root_dir;
			if (verbose)
				printIfVerbose("'ooc' directory is at '" + ooc_dir.getAbsolutePath() + "'");
			break;
		case OUT:
			if (verbose && out_dir != null)
				printIfVerbose("Moving 'out' directory from '" + iii_dir.getAbsolutePath() + "'.");
			out_dir = root_dir;
			if (verbose)
				printIfVerbose("'out' directory is at '" + out_dir.getAbsolutePath() + "'");
			break;
		default:
			MessageGenerator
					.briefError("Location of '" + root.toString() + "' cannot be changed from within this program.");
			return;
		}
		if (verbose)
			printIfVerbose(""); // line break
	}

	/**
	 * Get path to a root directory.
	 * 
	 * @param root Which root to get directory for.
	 * @return Path to requested root.
	 */
	public File getRoot(FILE_ROOT root) {
		if (root == null) {
			MessageGenerator.briefError("Cannot get path to root 'null'");
			return null;
		}
		switch (root) {
		case III:
			return iii_dir;
		case OOC:
			return ooc_dir;
		case OUT:
			return out_dir;
		case ROOT:
		case HOME:
			return new File(root.escapeSeq());
		case PWD:
		default:
			return pwd_dir;
		}
	}

	/**
	 * Specifies roots to {@link FileSys}. Doesn't store directory paths only the
	 * escape sequences used to indicate relative to which root a path has been
	 * specified.
	 * <p>
	 * 3 system properties - pwd, root("/"), home("~/")
	 * <p>
	 * 3 user specified directories - iii, ooc, out
	 */
	public static enum FILE_ROOT {
		PWD(""), ROOT("/"), HOME("~/"), III("#iii/"), OOC("#ooc/"), OUT("#out/");
		private String escape_seq = null;

		FILE_ROOT(String seq) {
			escape_seq = seq;
		}

		String escapeSeq() {
			return escape_seq;
		}

		/**
		 * Determine relative to which root the filename has been specified.
		 * 
		 * @param filename File name to check.
		 * @return Root to which the filename is relative.
		 */
		static FILE_ROOT which(String filename) {
			if (filename == null)
				MessageGenerator.briefErrorAndExit("\nCannot determine root of null filename.\nExiting.");

			for (FILE_ROOT r : FILE_ROOT.values())
				if (filename.startsWith(r.escape_seq) && (r != PWD))
					return r;
			return PWD;
		}

		/**
		 * Set an anchor root to this relative path.
		 * <p>
		 * For example, filename = "this/is/a/file.txt" is a relative path that would be
		 * assumed relative to pwd by default. If passed to HOME.subsumePath(filename),
		 * "~/this/is/a/file.txt" would be returned.
		 * 
		 * @param filename Relative file path.
		 * @return Anchored file path.
		 */
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
	public File toFile(String filename) {
		return toFile(filename, true);
	}

	/**
	 * Creates a file object from a filename. Uses escape sequence at beginning of
	 * string if found.
	 * 
	 * @param filename         Filename to create file object from.
	 * @param use_customizable Use the customizable root directories if they have
	 *                         been set.
	 * @return File object representing file. May not actually exist in system.
	 */
	public File toFile(String filename, boolean use_customizable) {
		if (filename == null)
			return null;

		File f = null;
		// filename starts with '/' or '~/', assume it is a complete path.
		if (filename.startsWith(FILE_ROOT.ROOT.escapeSeq()) || filename.startsWith(FILE_ROOT.HOME.escapeSeq()))
			f = new File(filename);
		else if (use_customizable && (iii_dir != null) && filename.startsWith(FILE_ROOT.III.escapeSeq())) {
			if (iii_dir == null) {
				printIfVerbose("\nCannot create file relative to iii, iii_dir is not defined.");
				return null;
			}
			f = new File(iii_dir, filename.replaceFirst(FILE_ROOT.III.escapeSeq(), ""));
		} else if (use_customizable && (ooc_dir != null) && filename.startsWith(FILE_ROOT.OOC.escapeSeq())) {
			if (ooc_dir == null) {
				printIfVerbose("\nCannot create file relative to ooc, ooc_dir is not defined.");
				return null;
			}
			f = new File(ooc_dir, filename.replaceFirst(FILE_ROOT.OOC.escapeSeq(), ""));
		} else if (use_customizable && (out_dir != null) && filename.startsWith(FILE_ROOT.OUT.escapeSeq())) {
			if (out_dir == null) {
				printIfVerbose("\nCannot create file relative to out, out_dir is not defined.");
				return null;
			}
			f = new File(out_dir, filename.replaceFirst(FILE_ROOT.OUT.escapeSeq(), ""));
		} else // assume filename relative to pwd
			f = new File(pwd_dir, filename);

		return f;
	}

	/**
	 * Searches for a file that already exists.
	 * 
	 * @param filename           File to search for.
	 * @param error_if_not_found Exit with error if can't find file.
	 * @return File specified by filename or null if the file doesn't exist in the
	 *         system.
	 */
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
	 * Searches for filename.
	 * 
	 * @param filename           File/directory to search for.
	 * @param dir                True if searching for directory.
	 * @param create_name        If file/directory not found, path/name to use to
	 *                           create file/directories using toFile(filename).
	 *                           Will not try to create if create_name is null.
	 * @param error_if_not_found Exit with error if can't find file/directory.
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
}