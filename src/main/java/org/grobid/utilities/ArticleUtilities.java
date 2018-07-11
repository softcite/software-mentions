package org.grobid.core.utilities;

import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;

import java.io.*;
import java.util.regex.*;
import java.net.URL;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
import javax.xml.parsers.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.grobid.core.utilities.KeyGen;
import org.grobid.core.utilities.TextUtilities;

/**
 *  Some convenient methods for retrieving the original PDF files from the annotated set.
 */
public class ArticleUtilities {

    private static final Logger logger = LoggerFactory.getLogger(ArticleUtilities.class);

    private static String halURL = "https://hal.archives-ouvertes.fr";
    private static String pmcURL = "https://www.ncbi.nlm.nih.gov/pmc/articles/";
    private static String arxivURL = "https://arxiv.org/pdf/1802.01021.pdf";

    enum Source {
        HAL, PMC, ARXIV;
    }

    /**
     *  Get the PDF file from an article ID.
     *  If the source is not present, we try to guess it from the identifier itself.
     *
     *  Return null if the identification fails.
     */
    public static File getPDFDoc(String identifier, Source source) {
        try {
            if (source == null) {
                source = guessDomain(identifier);
            }

            if (source == null) {
                logger.info("Cannot identify download url for " + identifier);
                return null;
            }

            String urll = null;
            switch (source) {
                case HAL:
                    urll = halURL+"/"+identifier+"/document";
                    break;
                case PMC:
                    urll = pmcURL+"/"+identifier+"/pdf/";
                    break;
                case ARXIV:
                    String localNumber = identifier.replace("arXiv:", "");
                    urll = arxivURL+"/"+localNumber+".pdf";
                    break;
            }

            if (urll == null) {
                logger.info("Cannot identify download url for " + identifier);
                return null;
            }

            File file = uploadFile(urll, 
                SoftwareProperties.getTmpPath(), 
                KeyGen.getKey()+".pdf");
            return file;
        }
        catch (Exception e) { 
            e.printStackTrace(); 
        }

        return null;
    }
    
    public static File getPDFDoc(String identifier) {
        return getPDFDoc(identifier, null);
    }

    private static Source guessDomain(String identifier) {
        if (identifier.startsWith("PMC")) {
            return Source.PMC;
        } else if (identifier.startsWith("hal-")) {
            return Source.HAL;
        } else {
            Matcher arXivMatcher = TextUtilities.arXivPattern.matcher(identifier);
            if (arXivMatcher.find()) {  
                return Source.ARXIV;
            }
        }
        return null;
    }

    private static File uploadFile(String urll, String path, String name) throws Exception {
        try {
            File pathFile = new File(path);
            if (!pathFile.exists()) {
                System.out.println("temporary path for software-mentions invalid: " + path);
                return null;
            }

            System.out.println("GET: " + urll);
            URL url = new URL(urll);

            File outFile = new File(path, name);
            FileOutputStream out = new FileOutputStream(outFile);
            // Writer out = new OutputStreamWriter(os,"UTF-8");

            // Serve the file
            InputStream in = url.openStream();
            byte[] buf = new byte[4 * 1024]; // 4K buffer
            int bytesRead;
            while ((bytesRead = in.read(buf)) != -1) {
                out.write(buf, 0, bytesRead);
            }

            out.close();
            in.close();
            return outFile;
        } 
        catch (Exception e) {
            throw new Exception("An exception occured while downloading " + urll, e);
        }
    }

}