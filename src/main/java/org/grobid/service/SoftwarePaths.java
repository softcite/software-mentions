package org.grobid.service;

/**
 * This interface only contains the path extensions for accessing the software entity recognition service.
 *
 * @author Patrice
 *
 */
public interface SoftwarePaths {
    /**
     * path extension for software service.
     */
    public static final String PATH_SOFTWARE = "/";
    
    /**
     * path extension for extracting software entities from a text.
     */
    public static final String PATH_SOFTWARE_TEXT= "processSoftwareText";

    /**
     * path extension for extracting software entities from an TEI file 
	 * (for instance produced by GROBID or Pub2TEI).
     */
    public static final String PATH_SOFTWARE_XML= "processSoftwareTEI";

    /**
     * path extension for extracting software entities from a PDF file.
     */
    public static final String PATH_SOFTWARE_PDF= "processSoftwarePDF";

    /**
     * path extension for annotating a PDF file with the recognized software entities.
     */
    public static final String PATH_ANNOTATE_SOFTWARE_PDF= "annotateSoftwarePDF";
}
