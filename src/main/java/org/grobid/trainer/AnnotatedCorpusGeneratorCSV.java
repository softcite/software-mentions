package org.grobid.trainer;

import org.grobid.core.analyzers.SoftwareAnalyzer;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.utilities.ArticleUtilities;
import org.grobid.trainer.SoftciteAnnotation.AnnotationType;

import org.grobid.core.analyzers.GrobidAnalyzer;
import org.grobid.core.data.BibDataSet;
import org.grobid.core.data.BiblioItem;
import org.grobid.core.document.Document;
import org.grobid.core.document.DocumentPiece;
import org.grobid.core.document.DocumentSource;
import org.grobid.core.document.xml.XmlBuilderUtils;
import org.grobid.core.document.TEIFormatter;
import org.grobid.core.GrobidModels;
import org.grobid.core.engines.Engine;
import org.grobid.core.engines.SoftwareParser;
import org.grobid.core.engines.FullTextParser;
import org.grobid.core.engines.config.GrobidAnalysisConfig;
import org.grobid.core.engines.label.SegmentationLabels;
import org.grobid.core.engines.label.TaggingLabel;
import org.grobid.core.engines.label.TaggingLabels;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.lang.Language;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.layout.LayoutTokenization;
import org.grobid.core.tokenization.TaggingTokenCluster;
import org.grobid.core.tokenization.TaggingTokenClusteror;
import org.grobid.core.lexicon.FastMatcher;
import org.grobid.core.main.LibraryLoader;
import org.grobid.core.utilities.*;
import org.grobid.core.utilities.SoftwareConfiguration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import java.text.NumberFormat;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import org.apache.commons.io.*;
import org.apache.commons.csv.*;
import org.apache.commons.lang3.tuple.Pair;

import org.semanticweb.yars.turtle.*;
import org.semanticweb.yars.nx.*;

import java.net.URI;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nu.xom.*;
import static org.grobid.core.document.xml.XmlBuilderUtils.teiElement;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * This class aims at converting annotations in .csv format from the original 
 * softcite dataset into annotated XML files (at document level) usable for training 
 * text mining tools and readable by humans. We convert into MUC conferences 
 * ENAMEX-style annotations (simpler than TEI for named-entities). 
 *
 * We need in particular to re-align the content of the original document which
 * has been annotated (e.g. a PMC article) with the "quotes" and strings available
 * in the .csv stuff. This is not always straightforward because: 
 * 
 * - the strings in the csv files has been cut and paste directly from the PDF 
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
 * annotations rather than the semantic web framework which is too disconnected 
 * from the actual linguistic and layout material. 
 *
 * Once the corpus is an XML format, we can use the consistency scripts under 
 * scripts/ to analyse, review and correct the annotations in a simple manner.
 *
 * Example command line:
 * mvn exec:java -Dexec.mainClass=org.grobid.trainer.AnnotatedCorpusGeneratorCSV 
 * -Dexec.args="/home/lopez/tools/softcite-dataset/pdf/ /home/lopez/tools/softcite-dataset/data/csv_dataset/ resources/dataset/software/corpus/"
 *
 *
 * @author Patrice
 */
public class AnnotatedCorpusGeneratorCSV {
    private static final Logger logger = LoggerFactory.getLogger(AnnotatedCorpusGeneratorCSV.class);

    static Charset UTF_8 = Charset.forName("UTF-8"); // StandardCharsets.UTF_8

    // TBD: use counter classes and a map
    private int totalMentions = 0;
    
    // count mismatches between mention and quotes
    private int unmatchedSoftwareMentions = 0;
    private int unmatchedVersionNumberMentions = 0;
    private int unmatchedVersionDateMentions = 0;
    private int unmatchedCreatorMentions = 0;
    private int unmatchedUrlMentions = 0;

    // count mismatches between mention and actual PDF content
    //private int unmatchedFullPDFMentions = 0;
    private int unmatchedFullSoftwarePDFMentions = 0;
    private int unmatchedFullVersionNumberPDFMentions = 0;
    private int unmatchedFullVersionDatePDFMentions = 0;
    private int unmatchedFullCreatorPDFMentions = 0;
    private int unmatchedFullUrlPDFMentions = 0;

    private int totalSoftwareMentions = 0;
    private int totalVersionNumberMentions = 0;
    private int totalVersionDateMentions = 0;
    private int totalCreatorMentions = 0;
    private int totalUrlMentions = 0;

    private int totalContexts = 0;
    private int unmatchedContexts = 0;

    public static String SOFTWARE_LABEL = "software";
    public static String VERSION_NUMBER_LABEL = "version-number";
    public static String VERSION_DATE_LABEL = "version-date";
    public static String CREATOR_LABEL = "creator";
    public static String URL_LABEL = "url";

    public static List<String> fields = Arrays.asList(SOFTWARE_LABEL, VERSION_NUMBER_LABEL, VERSION_DATE_LABEL, CREATOR_LABEL, URL_LABEL);  
    private ArticleUtilities articleUtilities = new ArticleUtilities();

    private FieldNormalizer fieldNormalizer = new FieldNormalizer();
    private SoftwareConfiguration configuration;

    public AnnotatedCorpusGeneratorCSV(SoftwareConfiguration conf) {
        this.configuration = conf;
    }

    /**
     * Start the conversion/fusion process for generating MUC-style annotated XML documents
     * from PDF, parsed by GROBID core, and softcite dataset  
     */
    public void process(String documentPath, String csvPath, String xmlPath) throws IOException {
        
        Map<String, AnnotatedDocument> documents = new HashMap<String, AnnotatedDocument>();
        Map<String, SoftciteAnnotation> annotations = new HashMap<String, SoftciteAnnotation>();

        importCSVFiles(csvPath, documents, annotations);

        // count annotations but skipping dummy ones
        int totalAnnotations = 0;
        for (Map.Entry<String, SoftciteAnnotation> entry : annotations.entrySet()) {
            SoftciteAnnotation annotation = entry.getValue();
            if (annotation != null) {
                if (annotation.getType() == AnnotationType.DUMMY)
                    continue;
                totalAnnotations++;
            }
        }

        System.out.println("\n" + totalAnnotations + " total annotations");
        System.out.println(documents.size() + " total annotated documents");    

        // from the loadeed annotations, we rank the annotators by their number of annotations, this information
        // could be used to decide which annotation to pick when we have inter-annotator disagreement (by default 
        // the annotator having produced the most annotations could be selected)
        Map<String,Integer> annotators = new HashMap<String, Integer>();
        createAnnotatorMap(annotations, annotators);

        // we keep GROBID analysis as close as possible to the actual content
        GrobidAnalysisConfig config = new GrobidAnalysisConfig.GrobidAnalysisConfigBuilder()
                                    .consolidateHeader(0)
                                    .consolidateCitations(0)
                                    .analyzer(SoftwareAnalyzer.getInstance())
                                    .build();
        Engine engine = GrobidFactory.getInstance().getEngine();

        // for reporting unmatched mention/context 
        Map<String, Writer> unmatchMentionContextWriters = new HashMap<String, Writer>();
        for(String field : fields) {
            Writer writer = new PrintWriter(new BufferedWriter(
                new FileWriter("doc/reports/unmatched-"+field+"-mention-context.txt")));
            unmatchMentionContextWriters.put(field, writer);
        }

        // for reporting misalignments with actual PDF content
        Map<String, Writer> misalignmentPDFWriters = new HashMap<String, Writer>();
        for(String field : fields) {
            Writer writer = new PrintWriter(new BufferedWriter(
                new FileWriter("doc/reports/misalignments-"+field+"-mentions-pdf.txt")));
            misalignmentPDFWriters.put(field, writer);
        }

        // report list of values for each mention type
        Map<String, Writer> allMentionsWriters = new HashMap<String, Writer>();
        for(String field : fields) {
            Writer writer = new PrintWriter(new BufferedWriter(
                new FileWriter("doc/reports/all-mentions-"+field+".txt")));
            allMentionsWriters.put(field, writer);
        }
        // init
        Map<String, Map<String,List<String>>> mentionLists = new TreeMap<String, Map<String,List<String>>>();
        for(String field : fields) {
            mentionLists.put(field, new TreeMap<String, List<String>>());
        }

        // this is a TEI corpus file to represent all the annotated snippets, with a bit more 
        // of textual styling
        StringBuilder builderTEICorpus = new StringBuilder();
        writerCorpusHeader(builderTEICorpus);

        // go thought all annotated documents of softcite
        int m = 0;
        List<String> xmlFiles = new ArrayList<String>();
        for (Map.Entry<String, AnnotatedDocument> entry : documents.entrySet()) {
            //if (m > 20) {
            //    break;
            //}
            //m++;
            String docName = entry.getKey();
            File pdfFile = getPDF(documentPath, docName, articleUtilities, this.configuration);

            // process header with consolidation to get some nice header metadata for this document
            BiblioItem biblio = new BiblioItem();
            GrobidAnalysisConfig configHeader = new GrobidAnalysisConfig.GrobidAnalysisConfigBuilder()
                                    .startPage(0)
                                    .endPage(2)
                                    .consolidateHeader(1)
                                    .build();
            try {
                engine.processHeader(pdfFile.getPath(), configHeader, biblio);
            } catch(Exception e) {
                e.printStackTrace();
            }
            AnnotatedDocument document = entry.getValue();
            document.setBiblio(biblio);

            // process full document
            DocumentSource documentSource = null;
            try {
                // process PDF documents with GROBID
                documentSource = DocumentSource.fromPdf(pdfFile, -1, -1, false, true, false);
            } catch(Exception e) {
                e.printStackTrace();
            }                

            if (documentSource == null)
                continue;

            Document doc = engine.getParsers().getSegmentationParser().processing(documentSource, config);
            if (doc == null) {
                logger.error("The parsing of the PDF file corresponding to " + docName + " failed");
                // TBD
                continue;
            }

            List<SoftciteAnnotation> localAnnotations = document.getAnnotations();
            //System.out.println(docName + ": " + localAnnotations.size() + " annotations");

            Language lang = new Language("en", 1.0);
            
            // we consider all layout tokens to be usable for matching annotations back to the actual documents
            // except certain structures which have been ignored in the annotation guidelines
            // -> we use GROBID structuring to identify the layout tokens to exclude 

            List<Integer> toExclude = new ArrayList<Integer>();

            // add more structures via GROBID
            // header
            SortedSet<DocumentPiece> documentParts = doc.getDocumentPart(SegmentationLabels.HEADER);
            List<LayoutToken> titleTokens = null;
            if (documentParts != null) {
                Pair<String,List<LayoutToken>> headerFeatured = engine.getParsers().getHeaderParser().getSectionHeaderFeatured(doc, documentParts, true);
                String header = headerFeatured.getLeft();
                List<LayoutToken> tokenizationHeader = Document.getTokenizationParts(documentParts, doc.getTokenizations());
                String labeledResult = null;

                // alternative
                String alternativeHeader = doc.getHeaderFeatured(true, true);
                // we choose the longest header
                if (StringUtils.isNotBlank(StringUtils.trim(header))) {
                    header = alternativeHeader;
                    tokenizationHeader = doc.getTokenizationsHeader();
                } else if (StringUtils.isNotBlank(StringUtils.trim(alternativeHeader)) && alternativeHeader.length() > header.length()) {
                    header = alternativeHeader;
                    tokenizationHeader = doc.getTokenizationsHeader();
                }

                if (StringUtils.isNotBlank(StringUtils.trim(header))) {
                    labeledResult = engine.getParsers().getHeaderParser().label(header);

                    BiblioItem resHeader = new BiblioItem();
                    resHeader.generalResultMapping(doc, labeledResult, tokenizationHeader);

                    // get the LayoutToken of the abstract - all the other ones should be excluded! 
                    List<LayoutToken> abstractTokens = resHeader.getLayoutTokens(TaggingLabels.HEADER_ABSTRACT);

                    if (tokenizationHeader != null) {
                        for(LayoutToken token : tokenizationHeader) {
                            toExclude.add(token.getOffset());
                        }
                    }
                    if (abstractTokens != null) {
                        for(LayoutToken token : abstractTokens) {
                            toExclude.remove(new Integer(token.getOffset()));
                        }
                    }
                }
            }

            // similarly we need the tokens of the reference sections (to exclude them!)
            documentParts = doc.getDocumentPart(SegmentationLabels.REFERENCES);
            List<LayoutToken> tokenizationReferences = null;
            if (documentParts != null) {
                tokenizationReferences = Document.getTokenizationParts(documentParts, doc.getTokenizations());
                // we process however the bib ref, no consolidation, to better filter invalid ref. callout
                engine.getParsers().getCitationParser().
                    processingReferenceSection(doc, engine.getParsers().getReferenceSegmenterParser(), 0);
            }

            // and we need the tokens of the running header (to exclude them!)
            documentParts = doc.getDocumentPart(SegmentationLabels.HEADNOTE);
            List<LayoutToken> tokenizationHeadNotes = null;
            if (documentParts != null) {
                tokenizationHeadNotes = Document.getTokenizationParts(documentParts, doc.getTokenizations());
            }
            // and the page number (to exclude them!)
            documentParts = doc.getDocumentPart(SegmentationLabels.PAGE_NUMBER);
            List<LayoutToken> tokenizationPageNumber = null;
            if (documentParts != null) {
                tokenizationPageNumber = Document.getTokenizationParts(documentParts, doc.getTokenizations());
            }

            // we compile all the remaining indices to be excluded
            if (tokenizationReferences != null) {
                for (LayoutToken token : tokenizationReferences) {
                    toExclude.add(token.getOffset());
                }
            }
            if (tokenizationReferences != null) {
                for (LayoutToken token : tokenizationReferences) {
                    toExclude.add(token.getOffset());
                }
            }
            if (tokenizationPageNumber != null) {
                for (LayoutToken token : tokenizationPageNumber) {
                    toExclude.add(token.getOffset());
                }
            }

            // we keep track of the LayoutToken corresponding to reference callout, in order to applied
            // special filter to exclude creator and version date annotation in the bibliographical reference
            // callout
            String reseFullText = null;
            LayoutTokenization tokenizationBody = null;
            List<LayoutToken> citationCalloutTokens = new ArrayList<LayoutToken>();
            documentParts = doc.getDocumentPart(SegmentationLabels.BODY);
            List<TaggingTokenCluster> bodyClusters = null;
            if (documentParts != null) {
                // full text processing
                Pair<String, LayoutTokenization> featSeg = engine.getParsers().getFullTextParser().getBodyTextFeatured(doc, documentParts);
                if (featSeg != null) {
                    // if featSeg is null, it usually means that no body segment is found in the
                    // document segmentation
                    String bodytext = featSeg.getLeft();

                    tokenizationBody = featSeg.getRight();
                    
                    if ( (bodytext != null) && (bodytext.trim().length() > 0) ) {               
                        reseFullText = engine.getParsers().getFullTextParser().label(bodytext);
                    } else {
                        logger.debug("Fulltext model: The input to the CRF processing is empty");
                    }

                    TaggingTokenClusteror clusteror = new TaggingTokenClusteror(GrobidModels.FULLTEXT, reseFullText, 
                        tokenizationBody.getTokenization(), true);
                    bodyClusters = clusteror.cluster();
                    for (TaggingTokenCluster cluster : bodyClusters) {
                        if (cluster == null) {
                            continue;
                        }

                        TaggingLabel clusterLabel = cluster.getTaggingLabel();

                        List<LayoutToken> localTokenization = cluster.concatTokens();
                        if ((localTokenization == null) || (localTokenization.size() == 0))
                            continue;

                        if (clusterLabel.equals(TaggingLabels.CITATION_MARKER)) {
                            // we want to keep only ref. callout matched with actual ref. bib. in the doc
                            try {
                                TEIFormatter teiFormatter = new TEIFormatter(doc, engine.getParsers().getFullTextParser());
                                List<nu.xom.Node> refNodes = teiFormatter.markReferencesTEILuceneBased(localTokenization,
                                    doc.getReferenceMarkerMatcher(),
                                    false, 
                                    false);

                                // if refnodes is just a textNode, it is not a valid ref. 
                                boolean isTextNode = true;
                                for(nu.xom.Node refNode : refNodes) {
                                    if (!(refNode instanceof nu.xom.Text)) {
                                        isTextNode = false;
                                        break;
                                    }
                                }

                                if ( (refNodes != null) && (!isTextNode))
                                   citationCalloutTokens.addAll(localTokenization);
                           } catch(Exception e) {
                                e.printStackTrace();
                           }
                        }
                    }
                }
            }

            // update total number of mentions and output a list report for each type
            for(SoftciteAnnotation annotation : localAnnotations) {
                // context
                String context = annotation.getContext();
                if (context == null)
                    continue;
                if (annotation.getSoftwareMention() != null) {
                    totalSoftwareMentions++;
                    totalMentions++;
                    Map<String, List<String>> mentionList = mentionLists.get(SOFTWARE_LABEL);
                    List<String> localDocs = mentionList.get(annotation.getSoftwareMention());
                    if (localDocs == null) {
                        localDocs = new ArrayList<String>();
                    }
                    if (!localDocs.contains(document.getDocumentID()))
                       localDocs.add(document.getDocumentID());
                    mentionList.put(annotation.getSoftwareMention(), localDocs);
                }
                if (annotation.getVersionNumber() != null) {
                    totalVersionNumberMentions++;
                    totalMentions++;
                    Map<String, List<String>> mentionList = mentionLists.get(VERSION_NUMBER_LABEL);
                    List<String> localDocs = mentionList.get(annotation.getVersionNumber());
                    if (localDocs == null) {
                        localDocs = new ArrayList<String>();
                    }
                    if (!localDocs.contains(document.getDocumentID())) {
                        localDocs.add(document.getDocumentID());
                    }
                    mentionList.put(annotation.getVersionNumber(), localDocs);
                }
                if (annotation.getVersionDate() != null) {
                    totalVersionDateMentions++;
                    totalMentions++;
                    Map<String, List<String>> mentionList = mentionLists.get(VERSION_DATE_LABEL);
                    List<String> localDocs = mentionList.get(annotation.getVersionDate());
                    if (localDocs == null) {
                        localDocs = new ArrayList<String>();
                    }
                    if (!localDocs.contains(document.getDocumentID())) {
                        localDocs.add(document.getDocumentID());
                    }
                    mentionList.put(annotation.getVersionDate(), localDocs);
                }
                if (annotation.getCreator() != null) {
                    totalCreatorMentions++;
                    totalMentions++;
                    Map<String, List<String>> mentionList = mentionLists.get(CREATOR_LABEL);
                    List<String> localDocs = mentionList.get(annotation.getCreator());
                    if (localDocs == null) {
                        localDocs = new ArrayList<String>();
                    }
                    if (!localDocs.contains(document.getDocumentID())) {
                        localDocs.add(document.getDocumentID());
                    }
                    mentionList.put(annotation.getCreator(), localDocs);
                }
                if (annotation.getUrl() != null) {
                    totalUrlMentions++;
                    totalMentions++;
                    Map<String, List<String>> mentionList = mentionLists.get(URL_LABEL);
                    List<String> localDocs = mentionList.get(annotation.getUrl());
                    if (localDocs == null) {
                        localDocs = new ArrayList<String>();
                    }
                    if (!localDocs.contains(document.getDocumentID())) {
                        localDocs.add(document.getDocumentID());
                    }
                    mentionList.put(annotation.getUrl(), localDocs);
                }
            }

            // TBD: use a map...
            for(String field : fields) {
                checkMentionContextMatch(localAnnotations, field, docName, unmatchMentionContextWriters.get(field));
            }

            // now this is the key part -> we try to align annotations with actual article content
            List<LayoutToken> tokens = doc.getTokenizations();
            if (tokens != null) {
                logger.debug("Process content... ");
                System.out.println("\n______________________");
                System.out.println("______> " + document.getDocumentID());
                try {
                    alignLayoutTokenSequence(document, tokens, localAnnotations, misalignmentPDFWriters, citationCalloutTokens);
                } catch(IOException e) {
                    logger.error("Failed to write report for PDF alignment process", e);
                }
            }

            // finally, we will output an annotated training file for the whole document content, only 
            // annotations that have been matched back to the actual document content are used
            List<Annotation> inlineAnnotations = document.getInlineAnnotations();
            if ( (inlineAnnotations != null) && (inlineAnnotations.size() > 0) ) {
                Collections.sort(inlineAnnotations);
                try {
                    String xmlFile = xmlPath + File.separator + new File(pdfFile.getAbsolutePath())
                                                                .getName().replace(".pdf", ".software-mention.xml");
                    generateAnnotatedXMLDocument(xmlFile,
                                                doc, 
                                                inlineAnnotations, 
                                                entry.getKey(),
                                                toExclude);
                    insertSnippet(builderTEICorpus, doc, tokenizationBody, reseFullText, inlineAnnotations, entry.getKey(), toExclude, biblio);
                    xmlFiles.add(xmlFile);
                } catch(Exception e) {
                    logger.error("Failed to write the resulting annotated document in xml", e);
                }
            }
            
        }

        for(String field : fields) {
            Map<String,List<String>> mentionList = mentionLists.get(field);
            //mentionList.sort(String::compareToIgnoreCase);
            for (Map.Entry<String,List<String>> entry : mentionList.entrySet())  {
                allMentionsWriters.get(field).write(entry.getKey() + "\t");
                if (field.equals("version-number"))
                    allMentionsWriters.get(field).write(fieldNormalizer.normalizeVersionNumber(entry.getKey()));
                else if (field.equals("url"))
                    allMentionsWriters.get(field).write(fieldNormalizer.normalizeUrl(entry.getKey()));
                else if (field.equals("creator"))
                    allMentionsWriters.get(field).write(fieldNormalizer.normalizeCreator(entry.getKey()));
                else if (field.equals("software"))
                    allMentionsWriters.get(field).write(fieldNormalizer.normalizeSoftwareName(entry.getKey()));
                allMentionsWriters.get(field).write("\t[");

                boolean first = true;
                for(String docid : entry.getValue()) {
                    if (first) {
                        first = false;
                        allMentionsWriters.get(field).write(docid);
                    } else  
                        allMentionsWriters.get(field).write(","+docid);
                }

                allMentionsWriters.get(field).write("]\n");
            }
        }

        for(String field : fields) {
            allMentionsWriters.get(field).close();
        }

        for(String field : fields) {
            unmatchMentionContextWriters.get(field).close();
        }

        for(String field : fields) {
            misalignmentPDFWriters.get(field).close();
        }

        // the following will try to disambiguate software names from the annotations against Wikidata
        //this.softwareTermVector(annotations, "doc/reports/software-term-vector.json");

        // breakdown per articleSet (e.g. pmc, econ)
        Map<String, Integer> articleSetMap = new TreeMap<String, Integer>();
        for (Map.Entry<String, AnnotatedDocument> entry : documents.entrySet()) {
            AnnotatedDocument document = entry.getValue();
            if (document.getArticleSet() != null) {
                int nb = 0;
                if (articleSetMap.get(document.getArticleSet()) != null)
                    nb = articleSetMap.get(document.getArticleSet())+1;
                else 
                    nb = 1;
                articleSetMap.put(document.getArticleSet(), nb);
            }
        }
        // print the breakdown per document set, 
        for (Map.Entry<String, Integer> entry : articleSetMap.entrySet()) {
            String setName = entry.getKey();
            int setCount = entry.getValue();
            System.out.println(setName + ": " + setCount + " documents");
        }

        builderTEICorpus.append("</teiCorpus>\n");

        // this is a TEI corpus file to represent all the annotated snippets, with a bit more 
        // of textual styling
        //Writer writerTEICorpus = new PrintWriter(new BufferedWriter(new FileWriter("resources/dataset/software/corpus/all.tei.xml")));
        Writer writerTEICorpus = new PrintWriter(new BufferedWriter(new FileWriter("doc/reports/all.tei.xml")));
        writerTEICorpus.write(XMLUtilities.toPrettyString(builderTEICorpus.toString(), 4));
        writerTEICorpus.close();

        // computing and reporting cross-agreement for the loaded set
        CrossAgreement crossAgreement = new CrossAgreement(fields);
        //CrossAgreement.AgreementStatistics stats = crossAgreement.evaluate(documents, "econ_article"); 
        CrossAgreement.AgreementStatistics stats = crossAgreement.evaluate(documents, "pmc_article"); 
        System.out.println("\n****** Inter-Annotator Agreement (Percentage agreement) ****** PMC SET ****** \n\n" + stats.toString());

        crossAgreement = new CrossAgreement(fields);
        stats = crossAgreement.evaluate(documents, "econ_article"); 
        System.out.println("\n****** Inter-Annotator Agreement (Percentage agreement) ****** ECON SET ***** \n\n" + stats.toString());

        // some stat reporting 
        System.out.println("\ntotal number of failed article PDF download: " + articleUtilities.totalFail);
        System.out.println("total number of failed article PDF download based on DOI: " + articleUtilities.totalDOIFail + "\n");

        System.out.println("Unmatched software mentions/quotes: " + unmatchedSoftwareMentions + " out of " + 
            totalSoftwareMentions + " total software mentions (" +
            formatPourcent((double)unmatchedSoftwareMentions/totalSoftwareMentions) + ")");

        System.out.println("Unmatched version number mentions/quotes: " + unmatchedVersionNumberMentions + " out of " + 
            totalVersionNumberMentions + " total version number mentions (" +
            formatPourcent((double)unmatchedVersionNumberMentions/totalVersionNumberMentions) + ")");

        System.out.println("Unmatched version date mentions/quotes: " + unmatchedVersionDateMentions + " out of " + 
            totalVersionDateMentions + " total version date mentions (" +
            formatPourcent((double)unmatchedVersionDateMentions/totalVersionDateMentions) + ")");

        System.out.println("Unmatched creator mentions/quotes: " + unmatchedCreatorMentions + " out of " + 
            totalCreatorMentions + " total creator mentions (" +
            formatPourcent((double)unmatchedCreatorMentions/totalCreatorMentions) + ")");

        System.out.println("Unmatched url mentions/quotes: " + unmatchedUrlMentions + " out of " + totalUrlMentions + " total url mentions (" +
            formatPourcent((double)unmatchedUrlMentions/totalUrlMentions) + ")");

        int unmatchedMentions = unmatchedSoftwareMentions + unmatchedVersionNumberMentions + unmatchedVersionDateMentions +
                                unmatchedCreatorMentions + unmatchedUrlMentions;
        System.out.println("\nTotal unmatched mentions/quotes: " + unmatchedMentions + " out of " + totalMentions + " total mentions (" + 
            formatPourcent((double)unmatchedMentions/totalMentions) + ") \n");

        int unmatchedFullPDFMentions = unmatchedFullSoftwarePDFMentions + unmatchedFullVersionNumberPDFMentions + unmatchedFullVersionDatePDFMentions +
            unmatchedFullCreatorPDFMentions + unmatchedFullUrlPDFMentions;

        System.out.println("Unmatched mentions/PDF: " + unmatchedFullPDFMentions + " out of " + totalMentions + " total mentions (" +
            formatPourcent((double)unmatchedFullPDFMentions/totalMentions) + ")");

        System.out.println("Unmatched software name mentions/PDF: " + unmatchedFullSoftwarePDFMentions + " out of " + totalSoftwareMentions + 
            " total software name mentions (" + formatPourcent((double)unmatchedFullSoftwarePDFMentions/totalSoftwareMentions) + ")");
        System.out.println("Unmatched version number mentions/PDF: " + unmatchedFullVersionNumberPDFMentions + " out of " + totalVersionNumberMentions + 
            " total version number mentions (" + formatPourcent((double)unmatchedFullVersionNumberPDFMentions/totalVersionNumberMentions) + ")");
        System.out.println("Unmatched version date mentions/PDF: " + unmatchedFullVersionDatePDFMentions + " out of " + totalVersionDateMentions + 
            " total version date mentions (" + formatPourcent((double)unmatchedFullVersionDatePDFMentions/totalVersionDateMentions) + ")");
        System.out.println("Unmatched creator mentions/PDF: " + unmatchedFullCreatorPDFMentions + " out of " + totalCreatorMentions + 
            " total creator mentions (" + formatPourcent((double)unmatchedFullCreatorPDFMentions/totalCreatorMentions) + ")");
        System.out.println("Unmatched url mentions/PDF: " + unmatchedFullUrlPDFMentions + " out of " + totalUrlMentions + 
            " total url mentions (" + formatPourcent((double)unmatchedFullUrlPDFMentions/totalUrlMentions) + ")");
    }


    private String formatPourcent(double num) {
        NumberFormat defaultFormat = NumberFormat.getPercentInstance();
        defaultFormat.setMinimumFractionDigits(2);
        return defaultFormat.format(num);
    }

    /**
     * In this method, we check if the mentions provided by an annotation are present in the provided context.
     * If not, we report it for manual correction.
     */
    private void checkMentionContextMatch(List<SoftciteAnnotation> localAnnotations, 
                                        String field, 
                                        String documentId, 
                                        Writer writer) throws IOException, IllegalArgumentException {
        for(SoftciteAnnotation annotation : localAnnotations) {
            if (annotation.getType() == AnnotationType.DUMMY)
                continue;
            // context
            String context = annotation.getContext();
            if (context == null)
                continue;

            String simplifiedContext = CrossAgreement.simplifiedField(context);

            // mention
            String mention = null;

            switch (field) {
                case "software":
                    mention = annotation.getSoftwareMention();
                    break;
                case "version-number":
                    mention = annotation.getVersionNumber();
                    break;
                case "version-date":
                    mention = annotation.getVersionDate();
                    break;
                case "creator":
                    mention = annotation.getCreator();
                    break;
                case "url":
                    mention = annotation.getUrl();
                    break;
                default:
                    throw new IllegalArgumentException("Invalid field: " + field);
            }

            if (mention != null) {
                //System.out.println(type + " mention: " + mention);
                //System.out.println("context: " + context);

                String simplifiedMention = CrossAgreement.simplifiedField(mention);

                if (simplifiedContext.indexOf(simplifiedMention) == -1) {
                    switch (field) {
                        case "software":
                            unmatchedSoftwareMentions++;
                            break;
                        case "version-number":
                            unmatchedVersionNumberMentions++;
                            break;
                        case "version-date":
                            unmatchedVersionDateMentions++;
                            break;
                        case "creator":
                            unmatchedCreatorMentions++;
                            break;
                        case "url":
                            unmatchedUrlMentions++;
                            break;
                        default:
                            throw new IllegalArgumentException("Invalid field: " + field);
                    }
                    
                    context = context.replace("\t", " ").replace("\n", " ");
                    context = context.replaceAll(" +", " ");
                    writer.write(documentId + "\t" + annotation.getIdentifier() + "\t" + field + "\t" + 
                        mention.replace("\t", " ") + "\t" + context.replace("\t", " ") + "\t" + annotation.getPage());
                    writer.write(System.lineSeparator());
                }
            }
        }
    }

    private void alignLayoutTokenSequence(AnnotatedDocument annotatedDocument, 
                                        List<LayoutToken> layoutTokens, 
                                        List<SoftciteAnnotation> localAnnotations, 
                                        Map<String,Writer> misalignmentPDFWriters,
                                        List<LayoutToken> citationCalloutTokens) throws IOException {
        if ( (layoutTokens == null) || (layoutTokens.size() == 0) )
            return;

        List<OffsetPosition> occupiedPositions = new ArrayList<OffsetPosition>();

        int annotationIndex = -1;
        for(SoftciteAnnotation annotation : localAnnotations) {
            if (annotation.getType() == AnnotationType.DUMMY)
                continue;
            annotationIndex++;
            if (annotation.getSoftwareMention() == null || annotation.getSoftwareMention().trim().length() == 0) {
                // there is no software name usable, we have to skip this case
                continue;
            }
            if (annotation.getContext() == null || annotation.getContext().trim().length() == 0) {
                // there is no context usable
                continue;
            }
            totalContexts++;

            boolean matchFound = false;
            boolean overlap = false;
            String softwareMention = annotation.getSoftwareMention();

            //System.out.println("new annotation / " + softwareMention);
            FastMatcher matcher = new FastMatcher();
            matcher.loadTerm(softwareMention.toLowerCase(), SoftwareAnalyzer.getInstance(), true, false); // ignoreDelimiters, not case sensitive
            // we add a couple of variants for better recall
            String softwareMentionNoHyphen = softwareMention.replace("-", "");
            if (!softwareMentionNoHyphen.equals(softwareMention)) {
                 // ignoreDelimiters, case sensitive
                matcher.loadTerm(softwareMentionNoHyphen, SoftwareAnalyzer.getInstance(), true, false);
            }
            
            // if "BlablaBli" we add also "Blabla Bli" 
            String subSegments = this.subSegment(softwareMention);
            if (!subSegments.equals(softwareMention))
                matcher.loadTerm(subSegments, SoftwareAnalyzer.getInstance(), true, false);
            if (softwareMention.indexOf("®") != -1) 
                matcher.loadTerm(softwareMention.replace("®",""), SoftwareAnalyzer.getInstance(), true, false);
            else
                matcher.loadTerm(softwareMention+"©", SoftwareAnalyzer.getInstance(), true, false);
            // case sensitive matching, ignore standard delimeters
            List<OffsetPosition> positions = matcher.matchLayoutToken(layoutTokens, true, false);
            if ( (positions == null) || (positions.size() == 0) ) {
                //unmatchedFullPDFMentions++;
                unmatchedFullSoftwarePDFMentions++;
                misalignmentPDFWriters.get(SOFTWARE_LABEL).write(annotatedDocument.getDocumentID() + "\t" + softwareMention + "\t" + "\t" + "no mention match\n");
                System.out.println("\t\t!!!!!!!!! " + softwareMention + ": failed mention, no matching found in the whole document");
                // we add all the attribute mentions as mismatches
                String versionNumber = annotation.getVersionNumber();
                if (versionNumber != null) {
                    unmatchedFullVersionNumberPDFMentions++;
                }
                String versionDate = annotation.getVersionDate();
                if (versionDate != null) {
                    unmatchedFullVersionDatePDFMentions++;
                }
                String creator = annotation.getCreator();
                if (creator != null) {
                    unmatchedFullCreatorPDFMentions++;
                }
                String url = annotation.getUrl();
                if (url != null) {
                    unmatchedFullUrlPDFMentions++;
                }
                continue;
            }

            int page = annotation.getPage();
            String quote = annotation.getContext();
            for(OffsetPosition position : positions) {
                // check if we are at the right page
                if (layoutTokens.get(position.start).getPage() != page) {
                    //System.out.println("page check failed !");
                    continue;
                }

                // check if the position already taken
                if (isOverlapping(occupiedPositions, position)) {
                    //System.out.println("overlap check failed !");
                    overlap = true;
                    continue;
                }

                // check if we are within the quote
                if (!inQuote(position, softwareMention, layoutTokens, quote)) {
                    //System.out.println("in quote check failed !");
                    continue;
                }

                annotation.setOccurence(position);
                occupiedPositions.add(position);
                matchFound = true;
                // the following boolean keeps track of possible misalignment of attributes of the software mention
                boolean failurePartAnnotation = false;

                /*System.out.print(annotatedDocument.getDocumentID() + "\t" + softwareMention + ": match position : " + position.toString() + " : ");
                for(int i=position.start; i<=position.end; i++) {
                    System.out.print(layoutTokens.get(i).getText());
                }
                System.out.println("\n");*/

                String softwareContent = LayoutTokensUtil.toText(layoutTokens.subList(position.start, position.end+1));

                boolean correspPresent = false;

                // try to get the chunk of text corresponding to the version if any
                String versionNumber = annotation.getVersionNumber();
                Annotation versionNumberInlineAnnotation = null;
                if (versionNumber != null) {
                    FastMatcher matcher2 = new FastMatcher();
                    matcher2.loadTerm(versionNumber, SoftwareAnalyzer.getInstance(), true, false); // not case sensitive
                    // case sensitive matching, ignore standard delimeters
                    List<OffsetPosition> positions2 = matcher2.matchLayoutToken(layoutTokens, true, false);
                    OffsetPosition positionVersionNumber = null;
                    if (positions2 != null) {
                        for(OffsetPosition position2 : positions2) {
                            if ((positionVersionNumber == null) && Math.abs(position2.start - position.start) < 20)
                                positionVersionNumber = position2;
                            else if (positionVersionNumber != null) {
                                if (Math.abs(position2.start - position.start) < Math.abs(positionVersionNumber.start - position.start))
                                    positionVersionNumber = position2;
                            }
                        }
                    }

                    // annotation for the version number
                    if ( (positionVersionNumber != null) && (!isOverlapping(occupiedPositions, positionVersionNumber)) ) {
                        versionNumberInlineAnnotation = new Annotation();
                        versionNumberInlineAnnotation.addAttributeValue("type", "version");
                        versionNumberInlineAnnotation.addAttributeValue("corresp", "#software-"+annotationIndex);
                        correspPresent = true;
                        versionNumberInlineAnnotation.setText(versionNumber);
                        versionNumberInlineAnnotation.setOccurence(positionVersionNumber);
                        //annotatedDocument.addInlineAnnotation(versionNumberInlineAnnotation);
                        occupiedPositions.add(positionVersionNumber);
                    } else if (positionVersionNumber == null) {
                        unmatchedFullVersionNumberPDFMentions++;
                        failurePartAnnotation = true;
                        if (positions2 == null) 
                            misalignmentPDFWriters.get(VERSION_NUMBER_LABEL).write(annotatedDocument.getDocumentID() + "\t" + "\t" + versionNumber.replace("\t", "") + "\t" + 
                                    "no mention match\n");
                        else
                            misalignmentPDFWriters.get(VERSION_NUMBER_LABEL).write(annotatedDocument.getDocumentID() + "\t" + versionNumber.replace("\t", "") + "\t" + 
                                    quote.replace("\t", "") + "\t" + "mention match but not in indicated page or quote\n");
                    } else {
                        unmatchedFullVersionNumberPDFMentions++;
                        failurePartAnnotation = true;
                        misalignmentPDFWriters.get(VERSION_NUMBER_LABEL).write(annotatedDocument.getDocumentID() + "\t" + versionNumber.replace("\t", "") + "\t" + 
                                    quote.replace("\t", "") + "\t" + "mention match but version number overlapping other annotation\n");
                    }
                }

                // annotation for the version date
                String versionDate = annotation.getVersionDate();
                Annotation versionDateInlineAnnotation = null;
                if (versionDate != null) {
                    FastMatcher matcher2 = new FastMatcher();
                    matcher2.loadTerm(versionDate, SoftwareAnalyzer.getInstance(), true, false); // not case sensitive
                    // case sensitive matching, ignore standard delimeters
                    List<OffsetPosition> positions2 = matcher2.matchLayoutToken(layoutTokens, true, false); // not case sensitive
                    OffsetPosition positionVersionDate = null;
                    if (positions2 != null) {
                        for(OffsetPosition position2 : positions2) {
                            if ((positionVersionDate == null) && Math.abs(position2.start - position.start) < 60) {
                                positionVersionDate = position2;
                            } else if (positionVersionDate != null) {
                                if (Math.abs(position2.start - position.start) < Math.abs(positionVersionDate.start - position.start)) {
                                    positionVersionDate = position2;
                                }
                            }
                        }
                    }

                    List<LayoutToken> layoutTokensVersionDate = null;
                    if (positionVersionDate != null) {
                        layoutTokensVersionDate = new ArrayList<LayoutToken>();
                        for(int i = positionVersionDate.start; i< positionVersionDate.end; i++)
                            layoutTokensVersionDate.add(layoutTokens.get(i));
                    }

                    //System.out.println("versionDate: " + versionDate);
                    // annotation for the version date
                    if ( (positionVersionDate != null) && 
                        (!isOverlapping(occupiedPositions, positionVersionDate)) &&
                        (!isOverlappingTokens(citationCalloutTokens, layoutTokensVersionDate))) {
                        versionDateInlineAnnotation = new Annotation();
                        versionDateInlineAnnotation.addAttributeValue("type", "version");
                        versionDateInlineAnnotation.addAttributeValue("corresp", "#software-"+annotationIndex);
                        correspPresent = true;
                        versionDateInlineAnnotation.setText(versionDate);
                        versionDateInlineAnnotation.setOccurence(positionVersionDate);
                        //annotatedDocument.addInlineAnnotation(versionDateInlineAnnotation);
                        occupiedPositions.add(positionVersionDate);
                    } else if (positionVersionDate == null) {
                        unmatchedFullVersionDatePDFMentions++;
                        failurePartAnnotation = true;
                        if (positions2 == null) {
                            misalignmentPDFWriters.get(VERSION_DATE_LABEL).write(annotatedDocument.getDocumentID() + "\t"+ "\t" + versionDate.replace("\t", "") + "\t" + 
                                    "no mention match\n");
                        } else {
                            misalignmentPDFWriters.get(VERSION_DATE_LABEL).write(annotatedDocument.getDocumentID() + "\t" + versionDate.replace("\t", "") + "\t" + 
                                    quote.replace("\t", "") + "\t" + "mention match but not in indicated page or quote\t" + 
                                    positions2.toString() + "\t" + position.toString() + "\n");
                        } 

                    } else {
                        unmatchedFullVersionDatePDFMentions++;
                        failurePartAnnotation = true;
                        misalignmentPDFWriters.get(VERSION_DATE_LABEL).write(annotatedDocument.getDocumentID() + "\t" + versionDate.replace("\t", "") + "\t" + 
                                quote.replace("\t", "") + "\t" + "mention match but overlap existing annotation or ref. callout\n");
                    }
                }

                // annotation for the creator
                String creator = annotation.getCreator();
                Annotation creatorInlineAnnotation = null;
                if ((creator != null) && (creator.trim().length()>0)) {
                    FastMatcher matcher2 = new FastMatcher();
                    matcher2.loadTerm(creator, SoftwareAnalyzer.getInstance(), true, false); // not case sensitive
                    // case sensitive matching, ignore standard delimeters
                    List<OffsetPosition> positions2 = matcher2.matchLayoutToken(layoutTokens, true, false); // not case sensitive
                    OffsetPosition positionCreator = null;
                    if (positions2 != null) {
                        for(OffsetPosition position2 : positions2) {
                            if ((positionCreator == null) && Math.abs(position2.start - position.start) < 60) {
                                if (!isOverlapping(occupiedPositions, position2)) 
                                    positionCreator = position2;
                            }
                            else if (positionCreator != null) {
                                if (Math.abs(position2.start - position.start) < Math.abs(positionCreator.start - position.start)) {
                                    if (!isOverlapping(occupiedPositions, position2)) 
                                        positionCreator = position2;
                                }
                            }
                        }
                    }

                    List<LayoutToken> layoutTokensCreator = null;
                    if (positionCreator != null) {
                        layoutTokensCreator = new ArrayList<LayoutToken>();
                        for(int i = positionCreator.start; i< positionCreator.end; i++)
                            layoutTokensCreator.add(layoutTokens.get(i));
                    }

//System.out.println("creator: " + creator);
                    // annotation for the creator
                    if ( (positionCreator != null) && 
                         (!isOverlapping(occupiedPositions, positionCreator)) &&
                         (!isOverlappingTokens(citationCalloutTokens, layoutTokensCreator)) ) {
                        creatorInlineAnnotation = new Annotation();
                        creatorInlineAnnotation.addAttributeValue("type", "creator");
                        creatorInlineAnnotation.addAttributeValue("corresp", "#software-"+annotationIndex);
                        correspPresent = true;
                        creatorInlineAnnotation.setText(creator);
                        creatorInlineAnnotation.setOccurence(positionCreator);
                        //annotatedDocument.addInlineAnnotation(creatorInlineAnnotation);
                        occupiedPositions.add(positionCreator);
                    } else if (positionCreator == null) {
                        unmatchedFullCreatorPDFMentions++;
                        failurePartAnnotation = true;
                        if (positions2 == null) 
                            misalignmentPDFWriters.get(CREATOR_LABEL).write(annotatedDocument.getDocumentID() + "\t" + "\t" + creator.replace("\t", "") + "\t" + 
                                    "no mention match\n");
                        else
                            misalignmentPDFWriters.get(CREATOR_LABEL).write(annotatedDocument.getDocumentID() + "\t" + creator.replace("\t", "") + "\t" + 
                                    quote.replace("\t", "") + "\t" + "mention match but not in indicated page or quote\n");
                    } else {
                        unmatchedFullCreatorPDFMentions++;
                        failurePartAnnotation = true;
                        misalignmentPDFWriters.get(CREATOR_LABEL).write(annotatedDocument.getDocumentID() + "\t" + creator.replace("\t", "") + "\t" + 
                                    quote.replace("\t", "") + "\t" + "mention match but overlap existing annotation\n");
                    }
                }
                
                // annotation for the url
                String url = annotation.getUrl();
                Annotation urlInlineAnnotation = null;
                if (url != null) {
                    FastMatcher matcher2 = new FastMatcher();
                    // remove possible trailing "/"
                    if (url.endsWith("/")) {
                        url = url.substring(0, url.length()-1);
                    }
                    matcher2.loadTerm(url, SoftwareAnalyzer.getInstance(), true, false); 
                    // not case sensitive matching, ignore standard delimeters
                    List<OffsetPosition> positions2 = matcher2.matchLayoutToken(layoutTokens, true, false); 
                    OffsetPosition positionUrl = null;
                    if (positions2 != null) {
                        for(OffsetPosition position2 : positions2) {
                            if ((positionUrl == null) && Math.abs(position2.start - position.start) < 100) {
                                positionUrl = position2;
                            } else if (positionUrl != null) {
                                if (Math.abs(position2.start - position.start) < Math.abs(positionUrl.start - position.start))
                                    positionUrl = position2;
                            }
                        }
                    }

                    // annotation for the url
                    if ( (positionUrl != null) && (!isOverlapping(occupiedPositions, positionUrl)) ) {
                        urlInlineAnnotation = new Annotation();
                        urlInlineAnnotation.addAttributeValue("type", "url");
                        urlInlineAnnotation.addAttributeValue("corresp", "#software-"+annotationIndex);
                        correspPresent = true;
                        urlInlineAnnotation.setText(url);
                        urlInlineAnnotation.setOccurence(positionUrl);
                        //annotatedDocument.addInlineAnnotation(urlInlineAnnotation);
                        occupiedPositions.add(positionUrl);
                    } else if (positionUrl == null) {
                        unmatchedFullUrlPDFMentions++;
                        failurePartAnnotation = true;
                        if (positions2 == null) 
                            misalignmentPDFWriters.get(URL_LABEL).write(annotatedDocument.getDocumentID() + "\t" + "\t" + url.replace("\t", "") + "\t" + 
                                    "no mention match\n");
                        else
                            misalignmentPDFWriters.get(URL_LABEL).write(annotatedDocument.getDocumentID() + "\t" + url.replace("\t", "") + "\t" + 
                                    quote.replace("\t", "") + "\t" + "mention match but not in indicated page or quote\n");
                    } else {
                        unmatchedFullUrlPDFMentions++;
                        failurePartAnnotation = true;
                        misalignmentPDFWriters.get(URL_LABEL).write(annotatedDocument.getDocumentID() + "\t" + url.replace("\t", "") + "\t" + 
                                    quote.replace("\t", "") + "\t" + "mention match but overlap existing annotation\n");
                    }
                }

                // create the inline annotations corresponding to this annotation
                // annotation for the software name
                // we can restrict or not actual annotations to fully matched mention
                if (!failurePartAnnotation) {
                    Annotation softwareInlineAnnotation = new Annotation();
                    softwareInlineAnnotation.addAttributeValue("type", "software");
                    if (correspPresent)
                        softwareInlineAnnotation.addAttributeValue("id", "software-"+annotationIndex);
                    softwareInlineAnnotation.setText(softwareContent);
                    softwareInlineAnnotation.setOccurence(position);
                    annotatedDocument.addInlineAnnotation(softwareInlineAnnotation);
                    
                    // we can add related components too
                    if (versionNumberInlineAnnotation != null) {
                        annotatedDocument.addInlineAnnotation(versionNumberInlineAnnotation);
                    }
                    if (versionDateInlineAnnotation != null) {
                        annotatedDocument.addInlineAnnotation(versionDateInlineAnnotation);
                    }

                    if (creatorInlineAnnotation != null) {
                        annotatedDocument.addInlineAnnotation(creatorInlineAnnotation);
                    }

                    if (urlInlineAnnotation != null) {
                        annotatedDocument.addInlineAnnotation(urlInlineAnnotation);
                    }
                }
                break;
            }
            if (!matchFound && !overlap) {
                //unmatchedFullPDFMentions++;
                unmatchedFullSoftwarePDFMentions++;
                misalignmentPDFWriters.get(SOFTWARE_LABEL).write(annotatedDocument.getDocumentID() + "\t" + softwareMention.replace("\t", "") + "\t" + 
                    quote.replace("\t", "") + "\t" + "mention match but not in indicated page or quote\n");
                System.out.println("\t\t!!!!!!!!! " + softwareMention  + ": failed mention after position check");
            }
        }
    }

    public static boolean isOverlapping(List<OffsetPosition> occupiedPositions, OffsetPosition position) {
        for(OffsetPosition occupiedPosition : occupiedPositions) {
            if (occupiedPosition.start == position.start || occupiedPosition.end == position.end)
                return true;
            if (position.start <= occupiedPosition.start && position.end > occupiedPosition.start)
                return true;
            if (position.start >= occupiedPosition.start && occupiedPosition.end > position.start)
                return true;
        }
        return false;
    }

    private boolean isOverlappingTokens(List<LayoutToken> occupiedTokens, List<LayoutToken> tokens) {
        for(LayoutToken token : tokens) {
            if (isOverlappingTokens(occupiedTokens, token))
                return true;
        }
        return false;
    }

    private boolean isOverlappingTokens(List<LayoutToken> occupiedTokens, LayoutToken token) {
//System.out.println("token: " + token);
//System.out.println("occupiedTokens: " + occupiedTokens);
/*System.out.print("occupied positions: ");
for(LayoutToken token : occupiedTokens) {
    System.out.print(token.getOffset() + " ");
}
System.out.print("\n");*/
        for(LayoutToken occupiedToken : occupiedTokens) {
            if (occupiedToken.getOffset() == token.getOffset()) {
                return true;
            }
        }
        return false;
    }

    private boolean inQuote(OffsetPosition position, String softwareName, List<LayoutToken> layoutTokens, String quote) {
        // actual left context from position
        int leftBound = Math.max(0, position.start-10);
        String leftContext = LayoutTokensUtil.toText(layoutTokens.subList(leftBound, position.start));
        String leftContextSimplified = CrossAgreement.simplifiedField(leftContext);

        // actual right context from position
        int rightBound = Math.min(position.end+11, layoutTokens.size());
        String rightContext = LayoutTokensUtil.toText(layoutTokens.subList(position.end+1, rightBound));
        String rigthContextSimplified = CrossAgreement.simplifiedField(rightContext);

        // now the quote
        String leftQuoteSimplified = null;
        String rightQuoteSimplified = null;

        // matching 
        Pattern mentionPattern = Pattern.compile(Pattern.quote(softwareName.toLowerCase()));
        Matcher mentionMatcher = mentionPattern.matcher(quote.toLowerCase());
        OffsetPosition mentionInQuote = new OffsetPosition();
        if (mentionMatcher.find()) {
            // we found a match :)
            String leftQuote = quote.substring(0, mentionMatcher.start());
            leftQuoteSimplified = CrossAgreement.simplifiedField(leftQuote);

            String rightQuote = quote.substring(mentionMatcher.end(), quote.length());
            rightQuoteSimplified = CrossAgreement.simplifiedField(rightQuote);
        } else {
            // more agressive soft matching 
            String simplifiedQuote = CrossAgreement.simplifiedField(quote);
            String softwareNameSimplified = CrossAgreement.simplifiedField(softwareName);

            int ind = simplifiedQuote.indexOf(softwareNameSimplified);
            if (ind == -1)
                return false;
            else {
                leftQuoteSimplified = simplifiedQuote.substring(0, ind);
                rightQuoteSimplified = simplifiedQuote.substring(ind+softwareNameSimplified.length(), simplifiedQuote.length());
            }
        }

        /*System.out.println("Mention: " + LayoutTokensUtil.toText(layoutTokens.subList(position.start, position.end+1)));
        System.out.println("Quote: " + quote);
        System.out.println("leftContextSimplified: " + leftContextSimplified);
        System.out.println("rigthContextSimplified: " + rigthContextSimplified);
        System.out.println("leftQuoteSimplified: " + leftQuoteSimplified);
        System.out.println("rightQuoteSimplified: " + rightQuoteSimplified);*/

        // it might need to be relaxed, with Ratcliff/Obershelp Matching
        if ( (rigthContextSimplified.startsWith(rightQuoteSimplified) || rightQuoteSimplified.startsWith(rigthContextSimplified)) && 
             (leftContextSimplified.endsWith(leftQuoteSimplified) || leftQuoteSimplified.endsWith(leftContextSimplified)) ) {
            //System.out.println("-> match !");
            return true;  
        }
        //System.out.println("-> NO match !");
        return false;
    }

    private void generateAnnotatedXMLDocument(String outputFile, 
                                              Document doc, 
                                              List<Annotation> inlineAnnotations, 
                                              String docID,
                                              List<Integer> toExclude) throws IOException, ParsingException {
        Element root = SoftwareParser.getTEIHeader(docID);
        Element textNode = teiElement("text");
        // for the moment we suppose we have english only...
        textNode.addAttribute(new Attribute("xml:lang", "http://www.w3.org/XML/1998/namespace", "en"));

        textNode.appendChild(insertAnnotations(doc.getTokenizations(), toExclude, inlineAnnotations));

        root.appendChild(textNode);
        //System.out.println(XmlBuilderUtils.toXml(root));

        // now some massage to beautify
        String xml = XmlBuilderUtils.toXml(root);
        xml = xml.replace("\n\n", "</p>\n\n<p>");
        xml = xml.replace("<p></text>", "</text>");
        xml = xml.replace("<text xml:lang=\"en\">", "\n<text xml:lang=\"en\">\n");
        xml = xml.replace("<p></p>", "");
        try {
            FileUtils.writeStringToFile(new File(outputFile), xml);
            //FileUtils.writeStringToFile(new File(outputFile), format(root));
        } catch (IOException e) {
            throw new IOException("Cannot create training data because output file can not be accessed: " + outputFile);
        } /*catch (ParsingException e) {
            throw new ParsingException("Cannot create training data because generated XML appears ill-formed");
        }*/
    }

    /**
     * Generate a clean annotated snippet based on the extracted content of PDF. 
     */ 
    private void insertSnippet(StringBuilder builderTEICorpus, 
                               Document doc, 
                               LayoutTokenization layoutTokenization,
                               String reseFullText,
                               List<Annotation> inlineAnnotations, 
                               String docID, 
                               List<Integer> toExclude,
                               BiblioItem biblio)  throws IOException, ParsingException {
        Element root = SoftwareParser.getTEIHeaderSimple(docID, biblio);
        
        Element textNode = teiElement("text");
        textNode.addAttribute(new Attribute("xml:lang", "http://www.w3.org/XML/1998/namespace", "en"));

        Element body = teiElement("body");
        textNode.appendChild(body);

        // now this is similar to GROBID core TEIFormatter toTEITextPiece, but we want to keep only paragraphs
        // with software annotations and simplify all other aspects

        TaggingLabel lastClusterLabel = null;

        List<LayoutToken> tokenizations = layoutTokenization.getTokenization();
        TaggingTokenClusteror clusteror = new TaggingTokenClusteror(GrobidModels.FULLTEXT, reseFullText, tokenizations);

        String tokenLabel = null;
        List<TaggingTokenCluster> clusters = clusteror.cluster();

        List<Element> allParagraphs = new ArrayList<>();
        Element curParagraph = null;
        Element curList = null;
        int equationIndex = 0; // current equation index position 
        for (TaggingTokenCluster cluster : clusters) {
            if (cluster == null) {
                continue;
            }

            TaggingLabel clusterLabel = cluster.getTaggingLabel();
            Engine.getCntManager().i(clusterLabel);

            if (clusterLabel.equals(TaggingLabels.PARAGRAPH)) {
                List<LayoutToken> localTokens = cluster.concatTokens();
                if ( (localTokens.size() == 0) || (inlineAnnotations.size()==0) )
                    continue;
                int pos = 0;
                int offsetFirstToken = localTokens.get(0).getOffset();
                int offsetLastToken = localTokens.get(localTokens.size()-1).getOffset();;
                if (isNewParagraph(lastClusterLabel, curParagraph)) {
                    curParagraph = teiElement("p");
                    allParagraphs.add(curParagraph);
                }
                if (isAnnotatedCluster(cluster.concatTokens(), inlineAnnotations, toExclude, doc.getTokenizations())) {
                    for (Annotation annotation : inlineAnnotations) {
                        OffsetPosition position = annotation.getOccurence();
                        
                        Map<String, String> attributes = annotation.getAttributes();
                        if (attributes == null)
                            continue;
                        
                        // following position is relative to the full document tokenization
                        int startE = position.start;
                        int endE = position.end;
                        // translate position into offset allocated to the LayoutToken object from the PDF
                        int offsetS = doc.getTokenizations().get(startE).getOffset();
                        int offsetE = doc.getTokenizations().get(endE).getOffset();

                        if ( (offsetS >= offsetFirstToken) && (offsetE <= offsetLastToken) ) {
                            Element entityElement = teiElement("rs");
                            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                                entityElement.addAttribute(new Attribute(entry.getKey(), entry.getValue()));
                            }

                            int annotationPosS = localTokens.indexOf(doc.getTokenizations().get(startE));
                            int annotationPosE = localTokens.indexOf(doc.getTokenizations().get(endE));
                            curParagraph.appendChild(this.toTextDehyphenize(localTokens.subList(pos, annotationPosS), toExclude));
                    
                            if ((annotationPosS-1 >= 0) && localTokens.get(annotationPosS-1).getText().equals(" "))
                                curParagraph.appendChild(" ");

                            //entityElement.appendChild(cleanValue(annotation.getText()));
                            // minimalist cleaning as we want to keep original string 
                            String contentAnnotationText = LayoutTokensUtil.toText(localTokens.subList(annotationPosS, annotationPosE+1));
                            contentAnnotationText = contentAnnotationText.replace("\n", " ");
                            contentAnnotationText = contentAnnotationText.replaceAll("( )+", " ");
                            entityElement.appendChild(contentAnnotationText);
                            pos = annotationPosE+1;

                            curParagraph.appendChild(entityElement);

                            if ((annotationPosE+1 < localTokens.size()) && localTokens.get(annotationPosE+1).getText().equals(" "))
                                curParagraph.appendChild(" ");
                        }
                    }
                }
                // total or remaining text
                curParagraph.appendChild(this.toTextDehyphenize(localTokens.subList(pos, localTokens.size()-1), toExclude));

            } else if (TEIFormatter.MARKER_LABELS.contains(clusterLabel)) {
                List<LayoutToken> refTokens = cluster.concatTokens();
                refTokens = TextUtilities.dehyphenize(refTokens);
                String chunkRefString = LayoutTokensUtil.toText(refTokens);

                Element parent = curParagraph != null ? curParagraph : body;
                parent.appendChild(new Text(" "));

                Element ref = null;
                String markerText = LayoutTokensUtil.toText(refTokens);
                boolean spaceEnd = false;
                markerText = markerText.replace("\n", " ");
                if (markerText.endsWith(" "))
                    spaceEnd = true;
                markerText = cleanValue(markerText);
                if (clusterLabel.equals(TaggingLabels.CITATION_MARKER)) {
                    ref = teiElement("ref");
                    ref.addAttribute(new Attribute("type", "bibr"));
                } else if (clusterLabel.equals(TaggingLabels.FIGURE_MARKER)) {
                    ref = teiElement("ref");
                    ref.addAttribute(new Attribute("type", "figure"));
                } else if (clusterLabel.equals(TaggingLabels.TABLE_MARKER)) {
                    ref = teiElement("ref");
                    ref.addAttribute(new Attribute("type", "table"));
                } else if (clusterLabel.equals(TaggingLabels.EQUATION_MARKER)) {
                    ref = teiElement("ref");
                    ref.addAttribute(new Attribute("type", "formula"));                   
                }
                if (ref != null) {
                    ref.appendChild(markerText);
                    parent.appendChild(ref);
                    if (spaceEnd)
                        parent.appendChild(" ");
                }
            }
            lastClusterLabel = cluster.getTaggingLabel();
        }

        // remove possibly empty div in the paragraph list or paragraph without <rs>
        if (allParagraphs.size() != 0) {
            for(int i = allParagraphs.size()-1; i>=0; i--) {
                Element theParagraph = allParagraphs.get(i);
                // check if the paragrpah is empty
                if ( (theParagraph.getChildElements() == null) || (theParagraph.getChildElements().size() == 0) ) {
                    allParagraphs.remove(i);
                } else {
                    // check if the paragraph does not contain at least a <rs>
                    Elements elements = theParagraph.getChildElements("rs", "http://www.tei-c.org/ns/1.0");
                    if ((elements == null) || (elements.size() == 0)) {
                        allParagraphs.remove(i);
                    }
                }
            } 
        }

        for(Element paragraph : allParagraphs)
            body.appendChild(paragraph);
        root.appendChild(textNode);

        builderTEICorpus.append(XmlBuilderUtils.toXml(root));
        builderTEICorpus.append("\n");
    }

    private boolean isNewParagraph(TaggingLabel lastClusterLabel, Element curParagraph) {
        return (!TEIFormatter.MARKER_LABELS.contains(lastClusterLabel) && lastClusterLabel != TaggingLabels.FIGURE
                && lastClusterLabel != TaggingLabels.TABLE) || curParagraph == null;
    }

    private boolean isAnnotatedCluster(List<LayoutToken> clusterTokens, 
                                       List<Annotation> inlineAnnotations, 
                                       List<Integer> toExclude,
                                       List<LayoutToken> tokenizations) {
        for (Annotation annotation : inlineAnnotations) {
            //if (annotation.getType() == SoftciteAnnotation.AnnotationType.SOFTWARE && 
            //    annotation.getOccurence() != null) {
            OffsetPosition position = annotation.getOccurence();
            int startE = position.start;
            int endE = position.end;
            // translate position into offset at the level of the full document tokenization
            int offsetS = tokenizations.get(startE).getOffset();
            int offsetE = tokenizations.get(endE).getOffset();
            for(LayoutToken token : clusterTokens) {
                if (toExclude.contains(token.getOffset()))
                    continue;
                if ( (token.getOffset() >= offsetS) && (token.getOffset() <= offsetE) ) 
                    return true;
            }
        }
        return false;
    }

    /**
     *  Add XML annotations corresponding to entities in a piece of text, to be included in
     *  generated full training data (one file per document).
     */
    public Element insertAnnotations(List<LayoutToken> tokenizations, 
                                     List<Integer> toExclude, 
                                     List<Annotation> inlineAnnotations) {
        Element p = teiElement("p");

        int pos = 0;
        if ( (inlineAnnotations == null) || (inlineAnnotations.size() == 0) ) {
            //p.appendChild(LayoutTokensUtil.toText(tokenizations));
            p.appendChild(this.toText(tokenizations, toExclude));
            return p;
        }
        //System.out.println(inlineAnnotations.size() + " inline annotations");
        for (Annotation annotation : inlineAnnotations) {
            //if (annotation.getType() == SoftciteAnnotation.AnnotationType.SOFTWARE && 
            //    annotation.getOccurence() != null) {
            OffsetPosition position = annotation.getOccurence();
            
            Element entityElement = teiElement("rs");
            Map<String, String> attributes = annotation.getAttributes();
            if (attributes == null)
                continue;
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                entityElement.addAttribute(new Attribute(entry.getKey(), entry.getValue()));
            }

            int startE = position.start;
            int endE = position.end;

            //p.appendChild(LayoutTokensUtil.toText(tokenizations.subList(pos, startE)));
            p.appendChild(this.toText(tokenizations.subList(pos, startE), toExclude));
                
            String contentAnnotationText = LayoutTokensUtil.toText(tokenizations.subList(startE, endE+1));
            // minimialist cleaning to represent original string
            contentAnnotationText = contentAnnotationText.replace("\n", " ");
            contentAnnotationText = contentAnnotationText.replaceAll("( )+", " ");
            entityElement.appendChild(contentAnnotationText);
            pos = endE+1;

            p.appendChild(entityElement);
        }
        //p.appendChild(LayoutTokensUtil.toText(tokenizations.subList(pos, tokenizations.size())));
        p.appendChild(this.toText(tokenizations.subList(pos, tokenizations.size()), toExclude));

        return p;
    }

    private void createAnnotatorMap(Map<String, SoftciteAnnotation> annotations, Map<String, Integer> annotators) {
        if ( (annotations == null) || (annotators == null) ) {
            return;
        }

        for (Map.Entry<String, SoftciteAnnotation> entry : annotations.entrySet()) {
            SoftciteAnnotation annotation = entry.getValue();
            if (annotation != null) {
                String annotatorID = annotation.getAnnotatorID();
                Integer numberAnnotations = annotators.get(annotatorID);
                if (numberAnnotations == null)
                    numberAnnotations = new Integer(0);
                numberAnnotations += 1;
                annotators.put(annotatorID, numberAnnotations);
            }
        }
    }

    /**
     * This is an ad-hoc serialization for sequence of LayoutToken where we mute some tokens (in our case title and 
     * bibliographical references and running header), following the policy of the softcite scheme. 
     */
    private String toText(List<LayoutToken> tokens, List<Integer> toExclude) {
        StringBuilder builder = new StringBuilder();

        for(LayoutToken token : tokens) {
            if (toExclude.contains(token.getOffset())) 
                continue;
            builder.append(token.getText());
        }

        return builder.toString();
    }

    private String toTextDehyphenize(List<LayoutToken> tokens, List<Integer> toExclude) {
        List<LayoutToken> localTokens = new ArrayList<LayoutToken>();
        for(LayoutToken token : tokens) {
            if (toExclude.contains(token.getOffset())) 
                continue;
            localTokens.add(token);
        }

        return LayoutTokensUtil.normalizeDehyphenizeText(localTokens);
    }

    public static String format(nu.xom.Element root) throws ParsingException, IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        nu.xom.Serializer serializer = new nu.xom.Serializer(out);
        nu.xom.Document doc = new nu.xom.Document(root);
        serializer.setIndent(4);
        serializer.write(doc);
        return out.toString("UTF-8");
    }

    public void importCSVFiles(String csvPath, Map<String, AnnotatedDocument> documents, Map<String, SoftciteAnnotation> annotations) {
        // process is driven by what's available in the softcite dataset
        File softciteRoot = new File(csvPath);
        // if the given root is the softcite repo root, we go down to data/ and then csv_dataset 
        // (otherwise we assume we are already under data/csv_dataset)
        // todo


        File[] refFiles = softciteRoot.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".csv");
            }
        });

        if (refFiles == null) {
            logger.warn("We found no .csv file to process");
            return;
        }

        // this csv file gives the mention context for each annotation
        File softciteMentions = new File(csvPath + File.separator + "softcite_in_text_mentions.csv");
        
        // this csv file gives the attributes for each mention, including the "string" of mention
        File softciteAttributes = new File(csvPath + File.separator + "softcite_codes_applied.csv");
        
        // this csv file gives information on the bibliographical reference associated to a mention 
        File softciteReferences = new File(csvPath + File.separator + "softcite_references.csv");

        // this csv file gives information on the article set to which each article belongs to 
        File softciteArticles = new File(csvPath + File.separator + "softcite_articles.csv");

        try {
            CSVParser parser = CSVParser.parse(softciteAttributes, UTF_8, CSVFormat.RFC4180);
            // csv fields in this file are as follow
            // selection,coder,code,was_code_present,code_label
            
            // *selection* is an identifier for the text mention, normally we have one per annotation, 
            // but maybe it's a full context passage?
            // *coder* is the id of the annotator
            // *code* is the name of the annotation class/attribute (e.g. software_name, version date)
            // *was_code_present* is a boolean indicating if the annotation class appears in the 
            // "selection" (context passage probably), not sure what is the purpose of this
            // *code_label* is the raw string corresponding to the annotated chunk
            
            boolean start = true;
            for (CSVRecord csvRecord : parser) {
                if (start) {
                    start = false;
                    continue;
                }
                SoftciteAnnotation annotation = null;
                String attribute = null;
                for(int i=0; i<csvRecord.size(); i++) {
                    String value = csvRecord.get(i);
                    if ((value.trim().length() == 0) || 
                        value.trim().equals("NA") || 
                        value.trim().equals("N/A") || 
                        value.trim().equals("No Label") || 
                        value.trim().equals("none") || 
                        value.trim().equals("None"))
                        continue;
                    value = cleanValue(value);
                    if (i == 0) {
                        /*if (duplicateAnnotationIdentifiers.contains(value)) {
                            // this record must be ignored for the moment
                            i = csvRecord.size();
                            continue;
                        }*/

                        if (annotations.get(value) == null) {
                            annotation = new SoftciteAnnotation();
                            annotation.setIdentifier(value);
                            annotations.put(value, annotation);
                        } else {
                            annotation = annotations.get(value);
                        }
                    } else if (i == 1) {
                        annotation.setAnnotatorID(value);
                    } else if (i == 2) {
                        attribute = value;
                    } else if (i == 3) {
                        if (attribute.equals("software_was_used")) {
                            if (value.equals("TRUE"))
                                annotation.setIsUsed(true);
                        }
                    } else if (i == 4) {
                        if (attribute.equals("software_name")) {
                            //annotation.setSoftwareMention(value);
                            annotation.setSoftwareMention(FieldNormalizer.removeLeadingAndTrailing(value, "`’“\""));
                            //annotation.setSoftwareMention(fieldNormalizer.normalizeSoftwareName(value));
                        }
                        else if (attribute.equals("version_number")) {
                            annotation.setVersionNumber(fieldNormalizer.normalizeVersionNumber(value));
                        }
                        else if (attribute.equals("version_date"))
                            annotation.setVersionDate(value);
                        else if (attribute.equals("url"))
                            annotation.setUrl(fieldNormalizer.normalizeUrl(value));
                        else if (attribute.equals("creator")) {
                            // we filter obvious reference person names (not reliable nor consistent)
                            if (value.toLowerCase().indexOf("et al") == -1 || value.toLowerCase().indexOf("et. al") == -1) {
                                annotation.setCreator(fieldNormalizer.normalizeCreator(value));
                            }
                        }
                        else {
                            logger.warn("unexpected attribute value: " + attribute);
                        }
                    }
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }

        System.out.println(annotations.size() + " annotations from " + softciteAttributes.getName());

        int nbReferenceAnnotations = 0;
        try {
            CSVParser parser = CSVParser.parse(softciteReferences, UTF_8, CSVFormat.RFC4180);
            // reference,from_in_text_selection,article,quote,coder,page,reference_type
            //iomolecules_in_the_computer:Jmol_to_the_rescue_Herraez_2006,PMC2447781_CT23,PMC2447781,". Herraez,A. (2006) Biomolecules in the computer: Jmol to the rescue.Biochem. Mol. Biol. Educat.,34, 255–261",ctjoe,6,publication
            boolean start = true;
            for (CSVRecord csvRecord : parser) {
                if (start) {
                    start = false;
                    continue;
                }
                SoftciteAnnotation annotation = null;
                AnnotatedDocument document = null;
                for(int i=0; i<csvRecord.size(); i++) {
                    String value = csvRecord.get(i);
                    if (value.trim().length() == 0)
                        continue;
                    value = cleanValue(value);
                    if (i == 0) {
                        /*if (duplicateAnnotationIdentifiers.contains(value)) {
                            // this record must be ignored for the moment
                            i = csvRecord.size();
                            continue;
                        }*/

                        if (annotations.get(value) == null) {
                            annotation = new SoftciteAnnotation();
                            annotation.setIdentifier(value);
                            annotation.setType("reference"); 
                            annotations.put(value, annotation);
                        } else {
                            annotation = annotations.get(value);
                        }
                        nbReferenceAnnotations++;
                    } else if (i == 1) {
                        annotation.setReferedAnnotationMention(value);
                        // add back the bibliographical reference associated to the mention
                        SoftciteAnnotation referredAnnotation = annotations.get(value);
                        if (referredAnnotation == null) {
                            System.out.println("referred annotation not found: " + value);
                        }
                    } else if (i == 2) {
                        String documentID = value;
                        int ind = documentID.indexOf("_");
                        if (ind != -1)
                            documentID = documentID.substring(0,ind);
                        if (documents.get(documentID) == null) {
                            document = new AnnotatedDocument();
                            document.setDocumentID(value);
                            documents.put(value, document);
                        } else 
                            document = documents.get(documentID);
                        document.addAnnotation(annotation);
                    } else if (i == 3) {
                        annotation.setReferenceString(value);
                    } else if (i == 4) {
                        annotation.setAnnotatorID(value);
                    } else if (i == 5) {
                        int intValue = -1;
                        try {
                            intValue = Integer.parseInt(value);
                        } catch(Exception e) {
                            logger.warn("Invalid page number: " + value);
                        }
                        if (intValue != -1)
                            annotation.setPage(intValue);
                    } else if (i == 6) {
                        annotation.setRefType(value.toLowerCase());
                    }
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        }

        System.out.println(nbReferenceAnnotations + " reference annotations from " + softciteReferences.getName());

        int nbMentionAnnotations = 0;
        try {
            CSVParser parser = CSVParser.parse(softciteMentions, UTF_8, CSVFormat.RFC4180);
            // csv fields in this file are as follow
            // selection,coder,article,quote,page,mention_type,certainty,memo
            
            // *selection* is an identifier for the text mention, normally we have one per annotation, 
            // but maybe it's a full context passage?
            // *coder* is the id of the annotator
            // *article* is the nidentifier of the annotated article
            // *quote* is the full passage where something is annotated, as a raw string (a snippet)
            // *tei_quote* (new from May 1st 2019) is the quote not cut and paste from PDF but from the TEI  
            // document following a GROBID conversion
            // *page* is the PDF page number where the annotation appears
            // *mention_type* is the type of what is annotated - values listed in the doc are "software", 
            // "algorithm", "hardware" and "other", but actual values appear to be much more diverse 
            // *certainty* is an integer between 1-10 for annotator subjective certainty on the annotation
            // *meno* is a free text field for comments
            boolean start = true;
            int nbCSVlines = 0;
            for (CSVRecord csvRecord : parser) {
                nbCSVlines++;
                if (start) {
                    start = false;
                    continue;
                }
                SoftciteAnnotation annotation = null;
                AnnotatedDocument document = null;
                for(int i=0; i<csvRecord.size(); i++) {
                    String value = csvRecord.get(i);
                    if (value.trim().length() == 0)
                        continue;
                    value = cleanValue(value);
                    if (i == 0) {
                        /*if (duplicateAnnotationIdentifiers.contains(value)) {
                            // this record must be ignored for the moment
                            i = csvRecord.size();
                            continue;
                        }*/

                        if (annotations.get(value) == null) {
                            annotation = new SoftciteAnnotation();
                            annotation.setIdentifier(value);
                            annotations.put(value, annotation);                         
                        } else {
                            annotation = annotations.get(value);
                        }

                        // derive the document ID from the selection - this might not be 
                        // reliable and not genral enough in the future
                        /*int ind = value.indexOf("_");
                        if (ind == -1)
                            continue;
                        String valueDoc = value.substring(0, ind);
                        // filter out non PMC 
                        if (!valueDoc.startsWith("PMC")) 
                            continue;
                        String documentID = valueDoc;
                        if (documents.get(documentID) == null) {
                            document = new AnnotatedDocument();
                            document.setDocumentID(valueDoc);
                            documents.put(valueDoc, document);
                        } else 
                            document = documents.get(documentID);
                        document.addAnnotation(annotation); */

                    } else if (i == 1) {
                        annotation.setAnnotatorID(value);
                    } else if (i == 2) {
                        String documentID = value;
                        int ind = documentID.indexOf("_");
                        if (ind != -1)
                            documentID = documentID.substring(0,ind);
                        if (documents.get(documentID) == null) {
                            document = new AnnotatedDocument();
                            document.setDocumentID(documentID);
                            documents.put(documentID, document);
                        } else 
                            document = documents.get(documentID);
                        document.addAnnotation(annotation); 
                    } else if (i == 3) {
                        // quote (cut and paste from PDF)
                        annotation.setContext(value);
                        nbMentionAnnotations++;
                    } else if (i == 4) {
                        // tei_quote (from TEI)
                        if ( (value != null) && (value.length()>0) && (!value.equals("NA")) ) {
                            // we have a real TEI quote, then we prioritize it 
                            if (annotation.getContext() == null)
                                nbMentionAnnotations++;
                            annotation.setContext(value);
                        } 
                    } else if (i == 5) {
                        int intValue = -1;
                        try {
                            intValue = Integer.parseInt(value);
                        } catch(Exception e) {
                            logger.warn("Invalid page number: " + value);
                        }
                        if (intValue != -1)
                            annotation.setPage(intValue);
                    } else if (i == 6) {
                        annotation.setType(value.toLowerCase()); 
                    } else if (i == 7) {
                        int intValue = -1;
                        try {
                            intValue = Integer.parseInt(value);
                        } catch(Exception e) {
                            logger.warn("Invalid certainty value: " + value);
                        }
                        if (intValue != -1)
                            annotation.setCertainty(intValue);
                    } else if (i == 8) {
                        annotation.setMemo(value);
                    }
                }
            }
            System.out.println(nbCSVlines + " csv lines");
        } catch(Exception e) {
            e.printStackTrace();
        }
        System.out.println(nbMentionAnnotations + " mentions annotations from " + softciteMentions.getName());

        try {
            CSVParser parser = CSVParser.parse(softciteArticles, UTF_8, CSVFormat.RFC4180);
            // article,article_set,coder,no_selections_found
            boolean start = true;
            int nbCSVlines = 0;
            for (CSVRecord csvRecord : parser) {
                nbCSVlines++;
                if (start) {
                    start = false;
                    continue;
                }
                AnnotatedDocument document = null;
                String annotatorID = null;
                if (csvRecord.size() != 4)
                    System.out.println(" **** Warning **** softcite_articles.csv -> size of csv line " + nbCSVlines + " is " + 
                        csvRecord.size() + " (should be 4)");
                for(int i=0; i<csvRecord.size(); i++) {
                    String value = csvRecord.get(i);
                    if (value.trim().length() == 0)
                        continue;
                    value = cleanValue(value);
                    if (i == 0) {
                        String documentID = value;
                        int ind = documentID.indexOf("_");
                        if (ind != -1)
                            documentID = documentID.substring(0,ind);
                        if (documents.get(documentID) == null) {
                            // we need to create a document representation even for documents not having annotations
                            document = new AnnotatedDocument();
                            document.setDocumentID(documentID);
                            documents.put(documentID, document);
                        } else 
                            document = documents.get(documentID);
                    } else if (i == 1) {
                        String articleSet = value;
                        if (document != null)
                            document.setArticleSet(value);
                    } else if (i == 2) {
                        annotatorID = value;
                    } else if (i == 3) {
                        // we need to record that no selection is found for the particular annotator (full negative case)
                        // this is relevant toknow for IIA metrics
                        if (document != null) {
                            if (value.equals("TRUE")) {
                                // no annotation realized by the annotator, but he has review the document
                                // we add a dummy annotation for the annotator and the document, this will help to keep track
                                // of the "annotation" effort, but the dummy annotation will be ignoredin further process,
                                // except for IIA metrics
                                SoftciteAnnotation annotation = new SoftciteAnnotation();
                                annotation.setType("dummy");
                                String identifier = document.getDocumentID()+"_dummy_"+annotatorID;
                                annotation.setIdentifier(identifier);
                                annotation.setAnnotatorID(annotatorID);
                                annotations.put(identifier, annotation);   
                                document.addAnnotation(annotation);
                            } 
                        } 
                    }
                }
            }
            System.out.println(nbCSVlines + " csv lines");
            System.out.println("number of documents: " + documents.keySet().size());
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private String cleanValue(String value) {
        value = value.trim();
        value = value.replace("\n", " ");
        if (value.startsWith("\""))
            value = value.substring(1,value.length());
        if (value.endsWith("\""))
            value = value.substring(0,value.length()-1);
        value = value.replaceAll(" +", " ");
        return value.trim();
    }

    /**
     * Write the header of the TEI corpus containing all the annotated ssnippet of the collection
     */
    private void writerCorpusHeader(StringBuilder builderTEICorpus) {
        builderTEICorpus.append("<teiCorpus version=\"3.3.0\" xmlns=\"http://www.tei-c.org/ns/1.0\">\n");
        builderTEICorpus.append("\t<teiHeader>\n");
        builderTEICorpus.append("\t\t<fileDesc>\n");
        builderTEICorpus.append("\t\t\t<titleStmt>\n");
        builderTEICorpus.append("\t\t\t\t<title>Softcite annotated corpus</title>\n");
        builderTEICorpus.append("\t\t\t\t<respStmt>\n");
        builderTEICorpus.append("\t\t\t\t\t<resp>James Howinson Lab, University of Texas at Austin, School of Information</resp>\n");
        builderTEICorpus.append("\t\t\t\t</respStmt>\n");
        builderTEICorpus.append("\t\t\t</titleStmt>\n");
        builderTEICorpus.append("\t\t\t<publicationStmt>\n");
        builderTEICorpus.append("\t\t\t\t<date when=\"2019\"/>\n");
        builderTEICorpus.append("\t\t\t\t<availability>CC-BY</availability>\n");
        builderTEICorpus.append("\t\t\t</publicationStmt>\n");
        builderTEICorpus.append("\t\t\t<sourceDesc>\n");
        builderTEICorpus.append("\t\t\t\t<bibl>Softcite corpus, version 0.1, 2019</bibl>\n");
        builderTEICorpus.append("\t\t\t</sourceDesc>\n");
        builderTEICorpus.append("\t\t</fileDesc>\n");

        builderTEICorpus.append("\t\t<encodingDesc>\n");
        builderTEICorpus.append("\t\t\t<appInfo>\n");

        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ");
        df.setTimeZone(tz);
        String dateISOString = df.format(new java.util.Date());

        builderTEICorpus.append("\t\t\t\t<application version=\""+GrobidProperties.getVersion()+"\" ident=\"GROBID\" when=\""+dateISOString+"\">\n");
        builderTEICorpus.append("\t\t\t\t\t<ref target=\"https://github.com/kermitt2/grobid\">A machine learning software for extracting information from scholarly documents</ref>\n");
        builderTEICorpus.append("\t\t\t\t</application>\n");
        builderTEICorpus.append("\t\t\t</appInfo>\n");
        builderTEICorpus.append("\t\t</encodingDesc>\n");

        builderTEICorpus.append("\t</teiHeader>\n");
        // then each snippet will be in an independent <TEI> sub-structure
    }

    /**
     * Sub-segment a string in case of unregular Uppercase in the middle of a token:
     * "BlablaBliBlo" we segment to "Blabla Bli Blo"    
     */ 
    private String subSegment(String string) {
        StringBuilder builder = new StringBuilder();
        SoftwareAnalyzer analyzer = SoftwareAnalyzer.getInstance();
        List<String> tokens = analyzer.tokenize(string);
        for(String token : tokens) {
            String lastToken = token;
            int ind = 0;
            for(int i=0; i<token.length(); i++) {
                if (i>0) {
                    if (Character.isLowerCase(token.charAt(i-1)) && Character.isUpperCase(token.charAt(i))) {
                        builder.append(token.substring(ind,i));
                        builder.append(" ");
                        lastToken = token.substring(i, token.length());
                        ind = i;
                    }
                }
            }
            builder.append(lastToken);
        }

        return builder.toString();
    }

    /**
     * Access a PDF in a directory:
     * - if present, return the path of the PDF
     * - if not present download the OA PDF, store it in the repo and 
     *   return the path of the local downloaded PDF
     *
     * If PDF not available, return null
     */
    public static File getPDF(String pathPDFs, String identifier, ArticleUtilities articleUtilities, SoftwareConfiguration conf) {
        File inRepo = new File(pathPDFs + File.separator + identifier + ".pdf");
        if (!inRepo.exists()) {
            File notInRepo = articleUtilities.getPDFDoc(identifier, conf);
            if (notInRepo == null) {
                return null;
            } else {
                // we move the file in the repo of local PDFs
                try {
                    //inRepo = new File(pathPDFs + File.separator + identifier + ".pdf");
                    Files.move(notInRepo.toPath(), inRepo.toPath(), StandardCopyOption.ATOMIC_MOVE);
                    
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
     * Output the list of software names as a (huge) term vector to be disambiguated by entity fishing.
     */ 
    public static void softwareTermVector(Map<String, SoftciteAnnotation> annotations, String outputPath) {
        List<String> allSoftwareNames = new ArrayList<String>();
        //System.out.println(annotations.size());
        for (Map.Entry<String, SoftciteAnnotation> entry : annotations.entrySet()) {
            SoftciteAnnotation annotation = entry.getValue();
            if (annotation != null) {
                String softwareName = annotation.getSoftwareMention();
                if ( (softwareName != null) && (softwareName.length() > 0) ) {
                    if (!allSoftwareNames.contains(softwareName)) {
                        allSoftwareNames.add(softwareName);
                    }
                }
            }
        }
        //System.out.println(allSoftwareNames.size());
        allSoftwareNames.sort(String.CASE_INSENSITIVE_ORDER);

        JsonStringEncoder encoder = JsonStringEncoder.getInstance();
        Writer writerVector = null;

        try {
            writerVector = new PrintWriter(new BufferedWriter(new FileWriter(outputPath)));
            writerVector.write("{"+"\n\t"+"\"termVector\": [\n\t\t[");

            boolean first = true;
            for(String term : allSoftwareNames) {
                byte[] encodedTerm = encoder.quoteAsUTF8(term);
                String outputTerm  = new String(encodedTerm);
                if (first) {
                    first = false;
                } else {
                    writerVector.write(", ");
                }
                writerVector.write("{\n\t\t\t\"term\": \"" + outputTerm + "\",\n\t\t\t\"weight\": 1.0\n\t\t}");
            }

            writerVector.write("]\n\t],\n\t\"language\": {\n\t\t\"lang\": \"en\"\n\t},\n\t\"entities\": [],\n\t\"onlyNER\": false,\n\t\"" + 
                "\"nbest\": false,\n\t\"customisation\": \"generic\"\n}");
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (writerVector != null)
                    writerVector.close();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Command line execution.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args) throws Exception {
       
        // we are expecting three arguments, absolute path to the original PDF 
        // documents, absolute path to the softcite data in csv and abolute path
        // where to put the generated XML files

        if (args.length != 3) {
            System.err.println("Usage: command [absolute path to the original PDFs] [absolute path to the softcite root data in csv] [output for the generated XML files]");
            System.exit(-1);
        }

        String documentPath = args[0];
        File f = new File(documentPath);
        if (!f.exists() || !f.isDirectory()) {
            System.err.println("path to PDFs directory does not exist or is invalid: " + documentPath);
            System.exit(-1);
        }

        String csvPath = args[1];
        f = new File(csvPath);
        if (!f.exists() || !f.isDirectory()) {
            System.err.println("path to softcite data csv directory does not exist or is invalid: " + csvPath);
            System.exit(-1);
        }

        String xmlPath = args[2];
        f = new File(xmlPath);
        if (!f.exists() || !f.isDirectory()) {
            System.out.println("XML output directory path does not exist, so it will be created");
            new File(xmlPath).mkdirs();
        }       

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        SoftwareConfiguration conf = mapper.readValue(new File("resources/config/config.yml"), SoftwareConfiguration.class);

        AnnotatedCorpusGeneratorCSV converter = new AnnotatedCorpusGeneratorCSV(conf);
        try {
            converter.process(documentPath, csvPath, xmlPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.exit(0);
    }
}