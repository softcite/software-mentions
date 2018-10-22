package org.grobid.trainer;

import org.grobid.core.analyzers.SoftwareAnalyzer;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.utilities.ArticleUtilities;

import java.util.ArrayList;
import java.util.List;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.apache.commons.io.FileUtils;

import org.semanticweb.yars.turtle.*;
import org.semanticweb.yars.nx.*;

import java.net.URI;

/**
 * This class aims at converting annotations in .ttl format from the original 
 * softcite dataset into annotated XML files (at document level) usable for training 
 * text mining tools and readable by humans. We convert into MUC conferences 
 * ENAMEX-style annotations (simpler than TEI for named-entities). 
 *
 * We need in particular to re-align the content of the original document which
 * has been annotated (e.g. a PMC article) with the "quotes" and strings available
 * in the .ttl stuff. This is not always straightforward because: 
 * 
 * - the strings in the ttl files has been cut and paste directly from the PDF 
 *   document, which is more noisy than what we can get from GROBID PDF parsing 
 *   pipeline,
 * - some annotations (like bibliographical reference, creators), refers to 
 *   unlocated information present in the document and we need some global document
 *   analysis to try to related the annotations with the right document 
 *   content.
 *
 * Just as a reference, I mention here that, from the text mining point of view,
 * a standard XML annotations framework like (MUC's ENAMEX or TEI style annotations) 
 * should be preferably used for reliable, constrained, readable and complete corpus 
 * annotations rather than the heavy and painful semantic web framework which 
 * is too disconnected from the actual linguistic and layout material. 
 *
 * Once the corpus is an XML format, we can use the consistency scripts under 
 * scripts/ to analyse, review and correct the annotations in a simple manner.
 *
 * Example command line:
 * mvn exec:java -Dexec.mainClass=org.grobid.trainer.AnnotatedCorpusGeneratorTurtle 
 * -Dexec.args="/home/lopez/tools/softcite-dataset/pdf/ /home/lopez/tools/softcite-dataset/data/ resources/dataset/software/corpus/"
 *
 *
 * @author Patrice
 */
public class AnnotatedCorpusGeneratorTurtle {

    static public Charset UTF_8 = Charset.forName("UTF-8"); // StandardCharsets.UTF_8

    private ArticleUtilities articleUtilities = new ArticleUtilities();

    /**
     * Start the conversion/fusion process for generating MUC-style annotated XML documents
     * from PDF, parsed by GROBID core, and softcite dataset  
     */
    public void process(String documentPath, String ttlPath, String xmlPath) {
        // process is driven by what's available in the softcite dataset
        File softciteRoot = new File(ttlPath);
        // if the given root is the softcite repo root, we go down to data/ (otherwise we assume we are already under data/)
        // todo

        File corpusRootDir = softciteRoot;

        File[] subDir = corpusRootDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return dir.isDirectory();
            }
        });

        if ( (subDir == null) || (subDir.length == 0) ) {
            System.out.println("We found no .ttl file to process");
            return;
        }

        for(int n=0; n < subDir.length; n++) {
            File corpusDir = subDir[n];


            File[] refFiles = corpusDir.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.toLowerCase().endsWith(".ttl") && (name.indexOf('%') == -1);
                }
            });

            if (refFiles == null) {
                System.out.println("We found no .ttl file to process");
                return;
            }

            for(int i=0; i < refFiles.length; i++) {
                File theTTLfile = refFiles[i];
                String name = theTTLfile.getName();
                System.out.println(name);

                // parse the .ttl files with the NxParser (https://github.com/nxparser/nxparser)
                TurtleParser nxp = new TurtleParser();

                try {

                    String turtleString = FileUtils.readFileToString(theTTLfile, "UTF-8");
                    System.out.println(turtleString);
                    InputStream is = new ByteArrayInputStream(turtleString.getBytes(UTF_8));

                    //InputStream is = new FileInputStream(theTTLfile);
                    nxp.parse(is, UTF_8, new URI("http://base.uri/"));

                    for (Node[] nx : nxp) {
                        // prints the subject, eg. <http://example.org/>
                        System.out.println(nx[0]);
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }


    /**
     * Access a PDF in a directory:
     * - if present, return the path of the PDF
     * - if not present download the OA PDF, store it in the repo and 
     *   return the path of the local downloaded PDF
     *
     * If PDF not available, return null
     */
    private File getPDF(String pathPDFs, String identifier) {
        File inRepo = new File(pathPDFs + File.separator + identifier + ".pdf");
        if (!inRepo.exists()) {
            File notInRepo = articleUtilities.getPDFDoc(identifier);
            if (notInRepo == null) {
                return null;
            } else {
                // we save the file in the repo of local PDFs
                try {
                    Files.copy(notInRepo.toPath(), inRepo.toPath(), StandardCopyOption.ATOMIC_MOVE);
                } catch(Exception e) {
                    e.printStackTrace();
                    return null;
                }
                return inRepo;
            }
        } else 
            return inRepo;
    }


    /**
     * Command line execution.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
       
        // we are expecting three arguments, absolute path to the original PDF 
        // documents, absolute path to the softcite data in ttl and abolute path
        // where to put the generated XML files

        if (args.length != 3) {
            System.err.println("Usage: command [absolute path to the original PDFs] [absolute path to the softcite root data in ttl] [output for the generated XML files]");
            System.exit(-1);
        }

        String documentPath = args[0];
        File f = new File(documentPath);
        if (!f.exists() || !f.isDirectory()) {
            System.err.println("path to PDFs directory does not exist or is invalid: " + documentPath);
            System.exit(-1);
        }

        String ttlPath = args[1];
        f = new File(ttlPath);
        if (!f.exists() || !f.isDirectory()) {
            System.err.println("path to softcite data directory does not exist or is invalid: " + ttlPath);
            System.exit(-1);
        }

        String xmlPath = args[2];
        f = new File(xmlPath);
        if (!f.exists() || !f.isDirectory()) {
            System.out.println("XML output directory path does not exist, so it will be created");
            new File(xmlPath).mkdirs();
        }       

        AnnotatedCorpusGeneratorTurtle converter = new AnnotatedCorpusGeneratorTurtle();
        converter.process(documentPath, ttlPath, xmlPath);
    }
}