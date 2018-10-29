package org.grobid.core.engines;

import nu.xom.Attribute;
import nu.xom.Element;
import org.apache.commons.io.FileUtils;
import org.grobid.core.GrobidModels;
import org.grobid.core.analyzers.SoftwareAnalyzer;
import org.grobid.core.data.SoftwareComponent;
import org.grobid.core.data.SoftwareEntity;
import org.grobid.core.data.BiblioItem;
import org.grobid.core.document.Document;
import org.grobid.core.document.DocumentPiece;
import org.grobid.core.document.DocumentSource;
import org.grobid.core.document.xml.XmlBuilderUtils;
import org.grobid.core.engines.config.GrobidAnalysisConfig;
import org.grobid.core.engines.label.SoftwareTaggingLabels;
import org.grobid.core.engines.label.SegmentationLabels;
import org.grobid.core.engines.label.TaggingLabel;
import org.grobid.core.engines.label.TaggingLabels;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.features.FeaturesVectorSoftware;
import org.grobid.core.layout.BoundingBox;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.layout.LayoutTokenization;
import org.grobid.core.lexicon.SoftwareLexicon;
import org.grobid.core.sax.TextChunkSaxHandler;
import org.grobid.core.tokenization.TaggingTokenCluster;
import org.grobid.core.tokenization.TaggingTokenClusteror;
import org.grobid.core.utilities.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.apache.commons.lang3.StringUtils.*;
import static org.grobid.core.document.xml.XmlBuilderUtils.teiElement;

/**
 * Software entity disambiguator. Once software mentions are recognized and grouped
 * into and entity (software name with recognized attributes), we use entity-fishing
 * service to disambiguate the software against Wikidata, as well as the attribute 
 * values (currently only creator).
 *
 * @author Patrice
 */
public class SoftwareDisambiguator {
    private static final Logger LOGGER = LoggerFactory.getLogger(SoftwareDisambiguator.class);

    private static String nerd_host = null;
    private static String nerd_port = null;

    public static SoftwareDisambiguator getInstance() {
        if (instance == null) {
            getNewInstance();
        }
        return instance;
    }

    /**
     * Create a new instance.
     */
    private static synchronized void getNewInstance() {
        instance = new SoftwareDisambiguator();
    }

    private SoftwareDisambiguator() {
        try {
            nerd_host = SoftwareProperties.get("grobid.software.entity-fishing.host");
            nerd_port = SoftwareProperties.get("grobid.software.entity-fishing.port");
        } catch(Exception e) {
            LOGGER.error("Cannot read properties for disambiguation service", e);
        }
    }

    /**
     * Disambiguate against Wikidata a list of raw entities extracted from text 
     * represented as a list of tokens. The tokens will be used as disambiguisation 
     * context, as well the other local raw softwares. 
     * 
     * @return list of disambiguated software entities
     */
    public disambiguate(List<SoftwareEntity> entities, List<LayoutToken> tokens) {
        for(SoftwareEntity entity : entities) {
            // get the software components
            SoftwareComponent softwareName = entity.getSoftwareName();
            SoftwareComponent softwareCreator = entity.getCreator();

            int start = softwareName.getOffsetStart();
            int end = softwareName.getOffsetEnd();

            if (softwareCreator.getOffsetStart() < start)
                start = softwareCreator.getOffsetStart();
            if (end < softwareCreator.getOffsetEnd()) 
                end = softwareCreator.getOffsetEnd();

            start = start - CONTEXT_WINDOW;
            if (start < 0)
                start = 0;

            end = end + CONTEXT_WINDOW;
            if (end > tokens.size()-1)
                end = tokens.size()-1;

            // apply a window for the contextual text
            List<LayoutToken> subtokens = tokens.sublist(start, end);
            List<SoftwareComponent> components = new ArrayList<SoftwareComponent>();
            String json = null;
            try {
                json = runNerd(components, subtokens, "en");
            } cacth(RuntimeException e) {

            }

        }


        return entities;
    }

    private static String RESOURCEPATH = "disambiguate";

    private static int CONTEXT_WINDOW = 15;

    /**
     * Call entity fishing disambiguation service on server.
     *
     * To be Moved in a Worker !
     *
     * @return the resulting disambiguated context in JSON or null
     */
    public String runNerd(List<SoftwareComponent> components, List<LayoutToken> subtokens, String lang) throws RuntimeException {
        StringBuffer output = new StringBuffer();
        try {
            URL url = null;
            if ( (nerd_port != null) && (nerd_port.length() > 0) )
                url = new URL("http://" + nerd_host + ":" + nerd_port + "/service/" + RESOURCEPATH);
            else
                url = new URL("http://" + nerd_host + "/service/" + RESOURCEPATH);

System.out.println("Calling: " + url.toString());
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpPost post = new HttpPost(url.toString());
            //post.addHeader("Content-Type", "application/json");
            //post.addHeader("Accept", "application/json");            

            // we create the query structure
            // context as an JSON array of strings
            JsonStringEncoder encoder = JsonStringEncoder.getInstance();
            StringBuffer buffer = new StringBuffer();
            buffer.append("{\"language\":{\"lang\":\"" + lang + "\"}");
            //buffer.append(",\"nbest\": 0");
            // we ask for French and German language correspondences in the result
            //buffer.append(", \"resultLanguages\":[ \"de\", \"fr\"]");
            buffer.append(", \"termVector\":[");
            boolean first = true;
            for(Keyterm term : toDisambiguate) {
                byte[] encodedTerm = encoder.quoteAsUTF8(term.classes);
                String outputTerm  = new String(encodedTerm); 
                if (!first)
                    buffer.append(", ");
                else
                    first = false;
                buffer.append("{ \"term\":\""+outputTerm+"\",\"score\":"+term.val+" }");
            }
            buffer.append("]}");


            //params.add(new BasicNameValuePair("query", buffer.toString()));

            StringBody stringBody = new StringBody(buffer.toString(), ContentType.MULTIPART_FORM_DATA);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
            builder.addPart("query", stringBody);
            HttpEntity entity = builder.build();

            //node.put("vector", buffer.toString());
            //byte[] postDataBytes = buffer.toString().getBytes("UTF-8");

            CloseableHttpResponse response = null;
            Scanner in = null;
            try {
                //post.setEntity(new UrlEncodedFormEntity(params));
                post.setEntity(entity);
                response = httpClient.execute(post);
                // Systemout.println(response.getStatusLine());

                int code = response.getStatusLine().getStatusCode();
                if (code != 200) {
                    LOGGER.error("Failed annotating text segment: HTTP error code : " + code);
                    return null;
                }

                HttpEntity entityResp = response.getEntity();
                in = new Scanner(entityResp.getContent());
                while (in.hasNext()) {
                    output.append(in.next());
                    output.append(" ");
                }
                EntityUtils.consume(entityResp);
            } finally {
                in.close();
                response.close();
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return output.toString().trim();
    }

}
