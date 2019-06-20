package util;

import com.xilinx.rapidwright.util.MessageGenerator;
import com.xilinx.rapidwright.util.FileTools;

import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.List;
import java.util.ArrayList;

public class FileUtils {

    /**
     * Read a mapping file. Ignores blank lines and lines beginning with '#'. Strips
     * whitespace from read strings.
     * 
     * @param filename  File to read from.
     * @param separator String that separates key from value.
     * @param warn      Print a warning if overwriting the same key?
     * @return The mappings read from the file.
     */
    public static Map<String, String> readMapFile(String filename, String separator, boolean warn) {
        Map<String, String> m = new TreeMap<>();
        List<String> lines = FileTools.getLinesFromTextFile(filename);
        for (String line : lines) {
            if (line.trim().startsWith("#"))
                continue;
            if (line.trim().isEmpty())
                continue;
            String[] parts = line.split(separator);
            String ret = m.put(parts[0].trim(), parts[1].trim());
            if (warn && ret != null) {
                MessageGenerator.briefMessage("Warning: Duplicate key '" + parts[0].trim() + "'.");
                MessageGenerator.briefMessage("Overwriting '" + ret + "' with '" + parts[1].trim() + "'.");
            }
        }
        return m;
    }

    /**
     * Write a String to String map to a file.
     * 
     * @param m         Map to write to the file.
     * @param filename  File to write the map in.
     * @param separator String used to separate key from value in each line.
     * @param header    List of strings to serve as a header for the map file.
     */
    public static void writeMapFile(Map<String, String> m, String filename, String separator, List<String> header) {
        List<String> lines = (header == null) ? new ArrayList<>() : new ArrayList<>(header);
        String sep = (separator == null) ? " " : separator;
        for (Entry<String, String> e : m.entrySet())
            lines.add(e.getKey() + sep + e.getValue());
        FileTools.writeLinesToTextFile(lines, filename);
    }


}