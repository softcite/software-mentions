package org.grobid.trainer;

import java.io.*;
import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.sax.TEICorpusSaxHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.util.*;

/**
 * Utility for exporting the XML TEI corpus format into a more simple JSON. 
 *
 * Command for creating description:
 * ./gradlew export_corpus_json -Pinput=/home/lopez/grobid/software-mentions/doc/reports/all.tei.xml 
 * -Poutput=/home/lopez/grobid/software-mentions/doc/reports/all.json
 */
public class ExportCorpusJson {

    public File outputPath = null;
    public File inputPath = null;

    public ExportCorpusJson(File inputFile, File outputFile) {
        this.inputPath = inputFile;
        this.outputPath = outputFile;
    }

    public void convert() {
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(outputPath), "UTF8");
            writer.write("[\n");
            // get a factory for SAX parser
            SAXParserFactory spf = SAXParserFactory.newInstance();

            TEICorpusSaxHandler handler = new TEICorpusSaxHandler();
            handler.setWriter(writer);

            //get a new instance of parser
            SAXParser p = spf.newSAXParser();
            p.parse(inputPath, handler);

        } catch (Exception e) {
            throw new GrobidException("An exception occured while training GROBID.", e);
        } finally {
            try {
                if (writer != null) {
                    writer.write("]\n");
                    writer.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String args[]) throws Exception {
        if (args.length != 2) {
            System.out.println("usage: command lang outputPath");
            System.exit(-1);
        }

        File inputFile = new File(args[0]);
        if (!inputFile.exists()) {
            System.err.println("Invalid input file: " + inputFile.getPath());
            System.exit(-1);
        }

        File outputFile = new File(args[1]);
        File dataDir = outputFile.getParentFile();
        if (!dataDir.exists() || !dataDir.isDirectory()) {
            System.err.println("Invalid output path directory: " + dataDir.getPath());
            System.exit(-1);
        }

        ExportCorpusJson converter = new ExportCorpusJson(inputFile, outputFile);
        converter.convert();
    }

}
