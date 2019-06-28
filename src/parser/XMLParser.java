package parser;

import java.io.File;
import javax.xml.parsers.DocumentBuilderFactory;

import com.xilinx.rapidwright.util.MessageGenerator;

import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;

public class XMLParser {
    /**
     * Returns a 'org.w3c.dom.Document' containing a parsed XML file.
     * 
     * @param input_file File to parse
     */
    public static Document parse(File input_file) {
        if (input_file == null)
            MessageGenerator.briefErrorAndExit("Null file input to xml parser.\nExiting.\n");
        if (!input_file.isFile())
            MessageGenerator.briefErrorAndExit(
                    "The input to xml parser was not a file.\n'" + input_file.getAbsolutePath() + "'\nExiting.\n");

        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(input_file);
            doc.getDocumentElement().normalize();
            return doc;
        } catch (Exception e) {
            MessageGenerator.briefError("Error parsing xml file '" + input_file.getAbsolutePath() + "'");
            e.printStackTrace();
        }
        return null;
    }
}
