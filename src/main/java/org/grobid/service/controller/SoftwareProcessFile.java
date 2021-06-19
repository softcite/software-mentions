package org.grobid.service.controller;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.grobid.core.data.SoftwareComponent;
import org.grobid.core.data.SoftwareEntity;
import org.grobid.core.data.BibDataSet;
import org.grobid.core.document.Document;
import org.grobid.core.engines.Engine;
import org.grobid.core.main.LibraryLoader;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.document.DocumentSource;
import org.grobid.core.engines.SoftwareParser;
import org.grobid.core.engines.config.GrobidAnalysisConfig;
import org.grobid.core.factory.GrobidPoolingFactory;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.IOUtilities;
import org.grobid.core.utilities.KeyGen;
import org.grobid.core.utilities.SoftwareConfiguration;
import org.grobid.core.utilities.ArticleUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import javax.xml.bind.DatatypeConverter;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import org.grobid.core.layout.Page;

/**
 *
 * @author Patrice
 */
@Singleton
public class SoftwareProcessFile {

    /**
     * The class Logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(SoftwareProcessFile.class);

    @Inject
    public SoftwareProcessFile() {
    }

    /**
     * Uploads the origin PDF, process it and return PDF annotations for the software mention objects in JSON.
     *
     * @param inputStream the data of origin PDF
     * @param disambiguate if true, the extracted mention will be disambiguated
     * @param addParagraphContext if true, the full paragraph where an annotation takes place is added
     * @return a response object containing the JSON annotations
     */
	public static Response processPDFAnnotation(final InputStream inputStream, 
                                                boolean disambiguate, 
                                                boolean addParagraphContext,
                                                SoftwareConfiguration configuration) {
        LOGGER.debug(methodLogIn()); 
        Response response = null;
        File originFile = null;
        SoftwareParser parser = SoftwareParser.getInstance(configuration);
        Engine engine = null;

        try {
            engine = GrobidFactory.getInstance().getEngine();
            MessageDigest md = MessageDigest.getInstance("MD5");
            DigestInputStream dis = new DigestInputStream(inputStream, md); 

            originFile = IOUtilities.writeInputFile(dis);
            byte[] digest = md.digest();

            GrobidAnalysisConfig config = new GrobidAnalysisConfig.GrobidAnalysisConfigBuilder().build();

            if (originFile == null) {
                response = Response.status(Status.INTERNAL_SERVER_ERROR).build();
            } else {
                long start = System.currentTimeMillis();
                Pair<List<SoftwareEntity>, Document> extractedEntities = 
                    parser.processPDF(originFile, disambiguate, addParagraphContext);
                long end = System.currentTimeMillis();

                Document doc = extractedEntities.getRight();
                List<SoftwareEntity> entities = extractedEntities.getLeft();
                StringBuilder json = new StringBuilder();
				json.append("{ ");
                json.append(SoftwareServiceUtil.applicationDetails(GrobidProperties.getVersion()));
                
                String md5Str = DatatypeConverter.printHexBinary(digest).toUpperCase();
                json.append(", \"md5\": \"" + md5Str + "\"");

				// page height and width
                json.append(", \"pages\":[");
				List<Page> pages = doc.getPages();
                boolean first = true;
                for(Page page : pages) {
    				if (first) 
                        first = false;
                    else
                        json.append(", ");    
    				json.append("{\"page_height\":" + page.getHeight());
    				json.append(", \"page_width\":" + page.getWidth() + "}");
                }

				json.append("], \"mentions\":[");
				first = true;
				for(SoftwareEntity entity : entities) {
					if (!first)
						json.append(", ");
					else
						first = false;
					json.append(entity.toJson());
				}
				json.append("], \"references\":[");

                List<BibDataSet> bibDataSet = doc.getBibDataSets();
                if (bibDataSet != null && bibDataSet.size()>0) {
                    SoftwareServiceUtil.serializeReferences(json, bibDataSet, entities);
                }

                json.append("], \"runtime\" :" + (end-start));
                json.append("}");

                if (json != null) {
                    response = Response
                            .ok()
                            .type("application/json")
                            .entity(json.toString())
                            .build();
                }
                else {
                    response = Response.status(Status.NO_CONTENT).build();
                }
            }
        } catch (NoSuchElementException nseExp) {
            LOGGER.error("Could not get an instance of SoftwareParser. Sending service unavailable.");
            response = Response.status(Status.SERVICE_UNAVAILABLE).build();
        } catch (Exception exp) {
            LOGGER.error("An unexpected exception occurs. ", exp);
            response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(exp.getMessage()).build();
        } finally {
            IOUtilities.removeTempFile(originFile);
        }
        LOGGER.debug(methodLogOut());
        return response;
    }

    /**
     * Uploads a PDF by provided URL, process it and return PDF annotations for the software mention objects in JSON.
     *
     * @param url the URL of the PDF to be processed
     * @param disambiguate if true, the extracted mention will be disambiguated
     * @param addParagraphContext if true, the full paragraph where an annotation takes place is added
     * @return a response object containing the JSON annotations
     */
    /*public static Response processPDFAnnotationURL(final String url, 
                                                boolean disambiguate, 
                                                boolean addParagraphContext,
                                                SoftwareConfiguration configuration) {
        LOGGER.debug(methodLogIn()); 
        Response response = null;
        File tmpPdf = null;
        SoftwareParser parser = SoftwareParser.getInstance(configuration);
        Engine engine = null;

        try {
            engine = GrobidFactory.getInstance().getEngine();
            //MessageDigest md = MessageDigest.getInstance("MD5");
            tmpPdf = ArticleUtilities.uploadFile(url, configuration.getTmpPath(), KeyGen.getKey()+".pdf");
            //DigestInputStream dis = new DigestInputStream(inputStream, md); 

            //byte[] digest = md.digest();

            GrobidAnalysisConfig config = new GrobidAnalysisConfig.GrobidAnalysisConfigBuilder().build();

            if (tmpPdf == null) {
                response = Response.status(Status.INTERNAL_SERVER_ERROR).build();
            } else {
                long start = System.currentTimeMillis();
                Pair<List<SoftwareEntity>, Document> extractedEntities = 
                    parser.processPDF(tmpPdf, disambiguate, addParagraphContext);
                long end = System.currentTimeMillis();

                Document doc = extractedEntities.getRight();
                List<SoftwareEntity> entities = extractedEntities.getLeft();
                StringBuilder json = new StringBuilder();
                json.append("{ ");
                json.append(SoftwareServiceUtil.applicationDetails(GrobidProperties.getVersion()));
                
                //String md5Str = DatatypeConverter.printHexBinary(digest).toUpperCase();
                //json.append(", \"md5\": \"" + md5Str + "\"");

                // page height and width
                json.append(", \"pages\":[");
                List<Page> pages = doc.getPages();
                boolean first = true;
                for(Page page : pages) {
                    if (first) 
                        first = false;
                    else
                        json.append(", ");    
                    json.append("{\"page_height\":" + page.getHeight());
                    json.append(", \"page_width\":" + page.getWidth() + "}");
                }

                json.append("], \"mentions\":[");
                first = true;
                for(SoftwareEntity entity : entities) {
                    if (!first)
                        json.append(", ");
                    else
                        first = false;
                    json.append(entity.toJson());
                }
                json.append("], \"references\":[");

                List<BibDataSet> bibDataSet = doc.getBibDataSets();
                if (bibDataSet != null && bibDataSet.size()>0) {
                    SoftwareServiceUtil.serializeReferences(json, bibDataSet, entities);
                }

                json.append("], \"runtime\" :" + (end-start));
                json.append("}");

                if (json != null) {
                    response = Response
                            .ok()
                            .type("application/json")
                            .entity(json.toString())
                            .build();
                }
                else {
                    response = Response.status(Status.NO_CONTENT).build();
                }
            }
        } catch (NoSuchElementException nseExp) {
            LOGGER.error("Could not get an instance of SoftwareParser. Sending service unavailable.");
            response = Response.status(Status.SERVICE_UNAVAILABLE).build();
        } catch (Exception exp) {
            LOGGER.error("An unexpected exception occurs. ", exp);
            response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(exp.getMessage()).build();
        } finally {
            IOUtilities.removeTempFile(tmpPdf);
        }
        LOGGER.debug(methodLogOut());
        return response;
    }*/

    /**
     * Uploads the origin XML, process it and return the extracted software mention objects in JSON.
     *
     * @param inputStream the data of origin PDF
     * @param disambiguate if true, the extracted mention will be disambiguated
     * @param addParagraphContext if true, the full paragraph where an annotation takes place is added
     * @return a response object containing the JSON annotations
     */
    public static Response extractXML(final InputStream inputStream, 
                                        boolean disambiguate, 
                                        boolean addParagraphContext,
                                        SoftwareConfiguration configuration) {
        LOGGER.debug(methodLogIn()); 
        Response response = null;
        File originFile = null;
        SoftwareParser parser = SoftwareParser.getInstance(configuration);

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            DigestInputStream dis = new DigestInputStream(inputStream, md); 

            originFile = IOUtilities.writeInputFile(dis);
            byte[] digest = md.digest();

            if (originFile == null) {
                response = Response.status(Status.INTERNAL_SERVER_ERROR).build();
            } else {
                long start = System.currentTimeMillis();
                List<SoftwareEntity> extractedEntities = 
                    parser.processXML(originFile, disambiguate, addParagraphContext);
                long end = System.currentTimeMillis();

                StringBuilder json = new StringBuilder();
                json.append("{ ");
                json.append(SoftwareServiceUtil.applicationDetails(GrobidProperties.getVersion()));
                
                String md5Str = DatatypeConverter.printHexBinary(digest).toUpperCase();
                json.append(", \"md5\": \"" + md5Str + "\"");
                json.append(", \"mentions\":[");
                boolean first = true;
                if (extractedEntities != null) {
                    for(SoftwareEntity entity : extractedEntities) {
                        if (!first)
                            json.append(", ");
                        else
                            first = false;
                        json.append(entity.toJson());
                    }
                }
                json.append("], \"references\":[");

                /*List<BibDataSet> bibDataSet = doc.getBibDataSets();
                if (bibDataSet != null && bibDataSet.size()>0) {
                    SoftwareServiceUtil.serializeReferences(json, bibDataSet, extractedEntities);
                }*/

                json.append("], \"runtime\" :" + (end-start));
                json.append("}");

                if (json != null) {
                    response = Response
                            .ok()
                            .type("application/json")
                            .entity(json.toString())
                            .build();
                }
                else {
                    response = Response.status(Status.NO_CONTENT).build();
                }
            }

        } catch (NoSuchElementException nseExp) {
            LOGGER.error("Could not get an instance of SoftwareParser. Sending service unavailable.");
            response = Response.status(Status.SERVICE_UNAVAILABLE).build();
        } catch (Exception exp) {
            LOGGER.error("An unexpected exception occurs. ", exp);
            response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(exp.getMessage()).build();
        } finally {
            IOUtilities.removeTempFile(originFile);
        }
        LOGGER.debug(methodLogOut());
        return response;
    }

    /**
     * Check whether the result is null or empty.
     */
    public static boolean isResultOK(String result) {
        return StringUtils.isBlank(result) ? false : true;
    }

    public static String methodLogIn() {
        return ">> " + SoftwareProcessFile.class.getName() + "." + Thread.currentThread().getStackTrace()[1].getMethodName();
    }

    public static String methodLogOut() {
        return "<< " + SoftwareProcessFile.class.getName() + "." + Thread.currentThread().getStackTrace()[1].getMethodName();
    }

}
