package org.grobid.trainer;

import java.util.*;
import java.io.*;
import java.nio.charset.Charset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.grobid.core.utilities.Pair;
import org.grobid.core.utilities.TextUtilities;

import org.dkpro.statistics.agreement.unitizing.*;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 
 * This class shares methods for calculating cross-agreement between annotators for the softcite dataset. 
 * It includes normally computation of standard error and confidence interval for the inter-annotator agreement
 * measure. 
 * 
 * We use for this purpose dkpro-statistics-agreement, https://dkpro.github.io/dkpro-statistics/ library
 * which implements all standard inter-annotator agreement measures (see the project readme/doc for more info)
 *
 * @author Patrice
 */
public class CrossAgreement {
    private static final Logger logger = LoggerFactory.getLogger(CrossAgreement.class);

    //static Charset UTF_8 = Charset.forName("UTF-8"); // StandardCharsets.UTF_8

    private List<String> fields = null;

    // record mismatches for further study
    private Map<String, List<Mismatch>> mismatches = null;

    private int certaintyThreshold = 0;

    public CrossAgreement(List<String> fields) {
        this.fields = new ArrayList<String>();
        for(String field : fields)
            this.fields.add(field);
        this.fields.add("quote");
        mismatches = new HashMap<String, List<Mismatch>>();
    }

    public static String simplifiedField(String field) { 
        if (field == null)
            return "";
        return field.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }

    public int getCertaintyThreshold() {
        return this.certaintyThreshold;
    }

    public void setCertaintyThreshold(int threshold) {
        this.certaintyThreshold = threshold;
    }

    /**
     * Evaluate inter-annotator agreement for a list of annotated documents. 
     * First step is to find documents annotated by multiple annotators.
     * If parameter articleSet is null, all the documents are considered, otherwise
     * only document belonging to this set will be considered for calculating the IAA. 
     * 
     */
    public AgreementStatistics evaluate(Map<String, AnnotatedDocument> documents, String articleSet) {
        AgreementStatistics stats = new AgreementStatistics(fields);
        int nbMultipleAnnotatedDocuments = 0;
        // go thought all annotated documents of softcite
        initReportMismatches(null);
        for (Map.Entry<String, AnnotatedDocument> entry : documents.entrySet()) {
            String docName = entry.getKey();
            mismatches = new HashMap<String, List<Mismatch>>();

            AnnotatedDocument annotatedDocument = entry.getValue();

            if (articleSet != null) {
                // check if the document belongs to the document set
                if ( (annotatedDocument.getArticleSet() != null) && 
                     (!annotatedDocument.getArticleSet().equals(articleSet)) )
                    continue;
            }

            List<SoftciteAnnotation> annotations = annotatedDocument.getAnnotations();

            // check if we have multiple annotators for the same document
            List<String> annotators = new ArrayList<String>();
            for(SoftciteAnnotation annotation : annotations) {
                String annotator = annotation.getAnnotatorID();
                if (!annotators.contains(annotator)) 
                    annotators.add(annotator);
            }

            if (annotators.size() < 2)
                continue;

            nbMultipleAnnotatedDocuments++;

            // naive pourcentage-based agreement for the document
            AgreementStatistics documentStats = this.agreementCounts(annotations, annotators);

            stats = stats.combineCounts(documentStats);

            //UnitizingAnnotationStudy study = new UnitizingAnnotationStudy(annotators.size());
            this.reportMismatches(docName, null);
        }

        // update the number of documents involved in the calculation
        stats.setNbMultipleAnnotatedDocuments(nbMultipleAnnotatedDocuments);
        
        // finally compute agreement measure
        stats.computePourcentageAgreement();

        return stats;
    }

    /**
     * Count the number of annotator agreements in a set of annotations
     */
    public AgreementStatistics agreementCounts(List<SoftciteAnnotation> annotations, List<String> annotators) {
        // we average the pourcentage IIA of every annotator pairs

        AgreementStatistics stats = new AgreementStatistics(fields);
        
        // we count agreements for each annotator pairs
        int p = 0;
        for(String annotator1 : annotators) {
            for(int i=p+1; i < annotators.size(); i++) {
                String annotator2 = annotators.get(i);
                //System.out.println(annotator1 + " / " + annotator2);
                AgreementStatistics localStats = agreementCounts(annotations, annotator1, annotator2);
                stats = stats.combineCounts(localStats);
            }
            p++;
            if (p == annotators.size())
                break;
        }

        //System.out.println("agreementCounts final result: \n" + stats.toString());

        return stats;
    }

    private AgreementStatistics agreementCounts(List<SoftciteAnnotation> annotations, String annotator1, String annotator2) {
        AgreementStatistics stats = new AgreementStatistics(fields);

        // annotations of annotator 1
        List<SoftciteAnnotation> annotations1 = new ArrayList<SoftciteAnnotation>(); 

        // annotations of annotator 2
        List<SoftciteAnnotation> annotations2 = new ArrayList<SoftciteAnnotation>(); 

        for(SoftciteAnnotation annotation : annotations) {
            if ( (annotation.getAnnotatorID() != null) && annotation.getAnnotatorID().equals(annotator1) ) {
                annotations1.add(annotation);
            } else if ( (annotation.getAnnotatorID() != null) && annotation.getAnnotatorID().equals(annotator2) ) {
                annotations2.add(annotation);
            }
        }

        for(String field : fields) {
            int allAgreements = 0;
            int allAnnotations = 0;

            for (SoftciteAnnotation annotation1 : annotations1) {
                String fieldContent = annotation1.getField(field);
                if (fieldContent != null) {
                    if (matchFieldValue(field, fieldContent, annotations2))
                        allAgreements++;
                    else {
                        // all the annotations from annotator1
                        Pair<String, SoftciteAnnotation> annot1 = new Pair(annotator1, annotation1);
                        // all the annotations from annotator2
                        Pair<String, List<SoftciteAnnotation>> annot2 = new Pair(annotator2, annotations2);

                        Mismatch mismatch = new Mismatch(annot1, annot2);
                        List<Mismatch> listMismatches = mismatches.get(field);
                        if (listMismatches == null)
                            listMismatches = new ArrayList<Mismatch>();
                        listMismatches.add(mismatch);
                        mismatches.put(field, listMismatches);
                    }
                    allAnnotations++;
                }
            }

            int matches2 = 0;
            int total2 = 0;
            for (SoftciteAnnotation annotation2 : annotations2) {
                String fieldContent = annotation2.getField(field);
                if (fieldContent != null) {
                    if (matchFieldValue(field, fieldContent, annotations1))
                        allAgreements++;
                    else {
                        Pair<String, SoftciteAnnotation> annot1 = new Pair(annotator2, annotation2);
                        Pair<String, List<SoftciteAnnotation>> annot2 = new Pair(annotator1, annotations1);

                        Mismatch mismatch = new Mismatch(annot1, annot2);
                        List<Mismatch> listMismatches = mismatches.get(field);
                        if (listMismatches == null)
                            listMismatches = new ArrayList<Mismatch>();
                        listMismatches.add(mismatch);
                        mismatches.put(field, listMismatches);
                    }
                    allAnnotations++;
                }
            }

            //System.out.println(field + " " + allAgreements + " " + allAnnotations);

            //System.out.println("stats before addition: \n" + stats.toString());
            stats.addFieldNumberAgreements(field, allAgreements);
            stats.addFieldNumberSamples(field, allAnnotations);
            //System.out.println("stats after addition: \n" + stats.toString());
        }

        //System.out.println("agreementCounts result: \n" + stats.toString());

        return stats;
    }


    /**
     * Check if an attribute value pair is present in a softcite annotation
     */
    private boolean matchFieldValue(String field, String fieldContent, List<SoftciteAnnotation> annotations) {
        return matchFieldValue(field, fieldContent, annotations, true);
    }

    /**
     * Check if an attribute value pair is present in a softcite annotation
     */
    private boolean matchFieldValue(String field, String fieldContent, List<SoftciteAnnotation> annotations, boolean soft) {
        for(SoftciteAnnotation annotation : annotations) {
            String annotationFieldContent = annotation.getField(field);
            if (annotationFieldContent == null) 
                continue;
            if (soft) {
                if (simplifiedField(annotationFieldContent).equals(simplifiedField(fieldContent)))
                    return true;
            } else if (annotationFieldContent.equals(fieldContent))
                return true;
        }
        return false;
    }

    private void addMismatch(String field, Mismatch mismatch) {
        List<Mismatch> localMismatch = mismatches.get(field);
        if (localMismatch == null)
            localMismatch = new ArrayList<Mismatch>();
        localMismatch.add(mismatch);
        mismatches.put(field, localMismatch);
    }

    /**
     * Class for carrying agreement statistics for a set of fields
     */
    public class AgreementStatistics {

        private List<String> fields = null;

        // inter-annotator agreement measures
        private Map<String, Double> fieldAgreements = null;

        // standard error associated to the measure
        private Map<String, Double> standardErrors = null;
        // confidence interval associated to the measure and the standard error 
        private Map<String, Pair<Double,Double>> confidenceIntervals = null;

        // total number of agreements used for the measure among all the samples
        private Map<String, Integer> numberAgreements = null;
        // total number of samples considered for the measure
        private Map<String, Integer> numberSamples = null;

        // total number of documents with multiple annotators
        private int nbMultipleAnnotatedDocuments = 0;

        private double allFieldAgreement = 0.0;
        private double allStandardError = 0.0;
        private Pair<Double,Double> allConfidenceInterval = null;
        private int allNumberAgreements = 0;
        private int allNumberSamples = 0;

        public AgreementStatistics(List<String> fields) {
            this.fields = fields;
            this.fieldAgreements = new TreeMap<String, Double>();
            this.standardErrors = new TreeMap<String, Double>();
            this.confidenceIntervals = new TreeMap<String, Pair<Double,Double>>();
            this.numberAgreements = new TreeMap<String, Integer>();
            this.numberSamples = new TreeMap<String, Integer>();
        }

        public AgreementStatistics(AgreementStatistics toCopy) {
            this.fields = toCopy.fields;

            if (toCopy.fieldAgreements != null) {
                this.fieldAgreements = new TreeMap<String, Double>();
                for (Map.Entry<String, Double> entry : toCopy.fieldAgreements.entrySet()) {
                    this.fieldAgreements.put(entry.getKey(), entry.getValue());
                }
            }

            if (toCopy.standardErrors != null) {
                this.standardErrors = new TreeMap<String, Double>();
                for (Map.Entry<String, Double> entry : toCopy.standardErrors.entrySet()) {
                    this.standardErrors.put(entry.getKey(), entry.getValue());
                }
            }

            if (toCopy.confidenceIntervals != null) {
                this.confidenceIntervals = new TreeMap<String, Pair<Double,Double>>();
                for (Map.Entry<String, Pair<Double,Double>> entry : toCopy.confidenceIntervals.entrySet()) {
                    this.confidenceIntervals.put(entry.getKey(), entry.getValue());
                }
            }

            if (toCopy.numberAgreements != null) {
                this.numberAgreements = new TreeMap<String, Integer>();
                for (Map.Entry<String, Integer> entry : toCopy.numberAgreements.entrySet()) {
                    this.numberAgreements.put(entry.getKey(), entry.getValue());
                }
            }

            if (toCopy.numberSamples != null) {
                this.numberSamples = new TreeMap<String, Integer>();
                for (Map.Entry<String, Integer> entry : toCopy.numberSamples.entrySet()) {
                    this.numberSamples.put(entry.getKey(), entry.getValue());
                }
            }
        }

        public void computePourcentageAgreement() {
            // for 95% confidence
            double zCrit = 1.96;

            // pourcentage of agreement over n samples and c fields: A = 1/n Sum_c (number of agreements for c)
            for (Map.Entry<String, Integer> entry : numberAgreements.entrySet()) {
                String field = entry.getKey();

                int valueNumberAgreements = entry.getValue();
                this.allNumberAgreements += valueNumberAgreements;

                int valueNumberSamples = 0;
                if (numberSamples.get(field) != null) {
                    valueNumberSamples = numberSamples.get(field);
                    this.allNumberSamples += valueNumberSamples;
                }

                if (valueNumberSamples != 0) {
                    double agreement = (double)valueNumberAgreements / valueNumberSamples;
                    this.addFieldAgreement(field, agreement);

                    // standard error of the agreement: SE(A) = square_root( A(1-A) ) / n 
                    double standardError = Math.sqrt( agreement * (1-agreement) ) / valueNumberSamples;
                    this.addFieldStandardError(field, standardError);

                    // confidence interval: ALow = A - SE(A).zCrit ; AHigh = A + SE(A).zCrit
                    // with zCrit = 1.96 for 95% confidence
                    // with zCrit = 1.645 for 90% confidence
                    
                    double alow = agreement - standardError * zCrit;
                    double ahigh = agreement + standardError * zCrit;
                    Pair<Double, Double> pair = new Pair(alow, ahigh);
                    this.addFieldConfidenceInterval(field, pair);
                } else {
                    this.addFieldAgreement(field, 0.0);
                    this.addFieldStandardError(field, 0.0);
                    this.addFieldConfidenceInterval(field, new Pair(0.0, 0.0));
                }
            }

            // global agreement pourcentage
            if (this.allNumberSamples != 0.0) {
                this.allFieldAgreement = (double)this.allNumberAgreements / this.allNumberSamples;
                this.allStandardError = Math.sqrt( allFieldAgreement * (1-allFieldAgreement) ) / allNumberSamples;
            }
            double alow = this.allFieldAgreement - this.allStandardError * zCrit;
            double ahigh = this.allFieldAgreement + this.allStandardError * zCrit;
            allConfidenceInterval = new Pair(alow, ahigh);
        }

        public void addFieldAgreement(String field, double agreement) {
            fieldAgreements.put(field, agreement);
        }

        public Double getAgreeement(String field) {
            return fieldAgreements.get(field);
        }

        public void addFieldStandardError(String field, double error) {
            standardErrors.put(field, error);
        }

        public Double getStandardError(String field) {
            return standardErrors.get(field);
        }

        public void addFieldConfidenceInterval(String field, Pair<Double,Double> interval) {
            confidenceIntervals.put(field, interval);
        }

        public Pair<Double,Double> getConfidenceInterval(String field) {
            return confidenceIntervals.get(field);
        }

        public void addFieldNumberAgreements(String field, int agreement) {
            if (this.numberAgreements.get(field) == null)
                this.numberAgreements.put(field, agreement);
            else 
                this.numberAgreements.put(field, this.numberAgreements.get(field) + agreement);
        }

        public Integer getNumberAgreeements(String field) {
            return this.numberAgreements.get(field);
        }

        public void addFieldNumberSamples(String field, int samples) {
            if (this.numberSamples.get(field) == null)
                this.numberSamples.put(field, samples);
            else 
                this.numberSamples.put(field, this.numberSamples.get(field) + samples);
        }

        public Integer getNumberSamples(String field) {
            return this.numberSamples.get(field);
        }

        public int getNbMultipleAnnotatedDocuments() {
            return this.nbMultipleAnnotatedDocuments;
        }

        public void setNbMultipleAnnotatedDocuments(int nb) {
            this.nbMultipleAnnotatedDocuments = nb;
        }

        /**
         * Combine (addition only) only counts information of two statistics object 
         * (number of agreement and number of samples).  
         */
        public AgreementStatistics combineCounts(AgreementStatistics agreement) {
            AgreementStatistics result = new AgreementStatistics(agreement);

            if (this.numberAgreements != null) {
                for (Map.Entry<String, Integer> entry : this.numberAgreements.entrySet()) {
                    String field = entry.getKey();
                    result.addFieldNumberAgreements(field, entry.getValue());
                }
            }

            if (this.numberSamples != null) { 
                for (Map.Entry<String, Integer> entry : this.numberSamples.entrySet()) {
                    String field = entry.getKey();
                    result.addFieldNumberSamples(field, entry.getValue());
                }
            }

            return result;
        }

        /**
         * Combine two sets of statistics, the agreement measure and errors for a field present in 
         * the two sets will be the averaged of the two values. Counts information will be added.  
         */
        public AgreementStatistics combineAgreements(AgreementStatistics agreement) {
            AgreementStatistics result = new AgreementStatistics(agreement);
            
            if (fieldAgreements != null) {
                for (Map.Entry<String, Double> entry : fieldAgreements.entrySet()) {
                    if (result.getAgreeement(entry.getKey()) == null) {
                        result.addFieldAgreement(entry.getKey(), entry.getValue());
                    } else {
                        result.addFieldAgreement(entry.getKey(), 
                            result.getAgreeement(entry.getKey()) + entry.getValue() / 2);
                    }
                }
            }
            
            if (standardErrors != null) {
                for (Map.Entry<String, Double> entry : standardErrors.entrySet()) {
                    if (result.getStandardError(entry.getKey()) == null) {
                        result.addFieldStandardError(entry.getKey(), entry.getValue());
                    } else {
                        result.addFieldStandardError(entry.getKey(), 
                            result.getStandardError(entry.getKey()) + entry.getValue() / 2);
                    }
                }
            }

            if (confidenceIntervals != null) {
                for (Map.Entry<String, Pair<Double,Double>> entry : confidenceIntervals.entrySet()) {
                    if (result.getConfidenceInterval(entry.getKey()) == null) {
                        result.addFieldConfidenceInterval(entry.getKey(), entry.getValue());
                    } else {
                        Double low = result.getConfidenceInterval(entry.getKey()).getA() + entry.getValue().getA() / 2;
                        Double high = result.getConfidenceInterval(entry.getKey()).getB() + entry.getValue().getB() / 2;
                        Pair<Double,Double> pair = new Pair<Double,Double>(low,high);
                        result.addFieldConfidenceInterval(entry.getKey(), pair);
                    }
                }
            }

            if (numberAgreements != null) {
                for (Map.Entry<String, Integer> entry : numberAgreements.entrySet()) {
                    if (result.getNumberAgreeements(entry.getKey()) == null) {
                        result.addFieldNumberAgreements(entry.getKey(), entry.getValue());
                    } else {
                        result.addFieldNumberAgreements(entry.getKey(), 
                            result.getNumberAgreeements(entry.getKey()) + entry.getValue());
                    }
                }
            }

            if (numberSamples != null) { 
                for (Map.Entry<String, Integer> entry : numberSamples.entrySet()) {
                    if (result.getNumberSamples(entry.getKey()) == null) {
                        result.addFieldNumberSamples(entry.getKey(), entry.getValue());
                    } else {
                        result.addFieldNumberSamples(entry.getKey(), 
                            result.getNumberSamples(entry.getKey()) + entry.getValue());
                    }
                }
            }

            return result;
        }

        public void removeField(String field) {
            fieldAgreements.remove(field);
            standardErrors.remove(field);
            confidenceIntervals.remove(field);
            numberAgreements.remove(field);
            numberSamples.remove(field);
        }

        @Override
        public String toString() {
            StringBuffer buffer = new StringBuffer();

            buffer.append("number of documents annotated by multiple annotators: ");
            buffer.append(nbMultipleAnnotatedDocuments);
            buffer.append("\n\n");

            for (String field : fields) {
                boolean first = true;
                if (!hasValue(field))
                    continue;
                buffer.append(field);
                if (field.length() < 8)
                    buffer.append("\t");
                buffer.append("\t");
                if (fieldAgreements.get(field) != null) {
                    first = false;
                    buffer.append("agreement measure:\t");
                    buffer.append(TextUtilities.formatFourDecimals(fieldAgreements.get(field)));
                    buffer.append("\n");
                }
                if (standardErrors.get(field) != null) {
                    buffer.append("\t\tstandard error:\t\t");
                    buffer.append(TextUtilities.formatFourDecimals(standardErrors.get(field)));
                    buffer.append("\n");
                }
                if (confidenceIntervals.get(field) != null) {
                    buffer.append("\t\tconfidence interval:\t");
                    Pair<Double,Double> interval = confidenceIntervals.get(field);
                    buffer.append("[").append(TextUtilities.formatFourDecimals(interval.getA())).append("-")
                        .append(TextUtilities.formatFourDecimals(interval.getB())).append("]");
                    buffer.append("\n");
                }
                if ( (numberAgreements.get(field) != null) && (numberAgreements.get(field) != 0) ) {
                    if (first)
                        first = false;
                    else
                        buffer.append("\t\t");
                    buffer.append("number of agreements:\t");
                    buffer.append(numberAgreements.get(field));
                    buffer.append("\n");
                }
                if ( (numberSamples.get(field) != null) && (numberSamples.get(field) != 0) ) {
                    buffer.append("\t\tnumber of samples:\t");
                    buffer.append(numberSamples.get(field));
                    buffer.append("\n");
                }
            }

            buffer.append("\nall fields\t").append("agreement measure:\t").append(TextUtilities.formatFourDecimals(this.allFieldAgreement));
            buffer.append("\n\t\t").append("standard error:\t").append(TextUtilities.formatFourDecimals(this.allStandardError));
            buffer.append("\n\t\tconfidence interval:\t");
            if (allConfidenceInterval != null) {
                buffer.append("[").append(TextUtilities.formatFourDecimals(allConfidenceInterval.getA())).append("-")
                    .append(TextUtilities.formatFourDecimals(allConfidenceInterval.getB())).append("]");
            }
            buffer.append("\n\t\t").append("number of agreements:\t").append(this.allNumberAgreements);
            buffer.append("\n\t\t").append("number of samples:\t").append(this.allNumberSamples).append("\n");

            return buffer.toString();
        }

        private boolean hasValue(String field) {
            if ( ( (numberAgreements.get(field) != null) && (numberAgreements.get(field) != 0) ) ||
                 ( (numberSamples.get(field) != null) && (numberSamples.get(field) != 0) ) ||
                 (fieldAgreements.get(field) != null) ||
                 (confidenceIntervals.get(field) != null) ||
                 (standardErrors.get(field) != null) ) {
                return true;
            }
            return false;
        }

    }

    /**
     * Class for representing and keeping track of a mismatch
     */
    public class Mismatch {

        private Pair<String, SoftciteAnnotation> annotations1 = null;
        private Pair<String, List<SoftciteAnnotation>> annotations2 = null;

        public Mismatch(Pair<String, SoftciteAnnotation> annotations1, 
                        Pair<String, List<SoftciteAnnotation>> annotations2) {
            this.annotations1 = annotations1;
            this.annotations2 = annotations2;
        }

        public Pair<String, SoftciteAnnotation> getAnnotations1() {
            return annotations1;
        }

        public Pair<String, List<SoftciteAnnotation>> getAnnotations2() {
            return annotations2;
        }
    }

    /**
     *  Create a report per field for all associated mismatched, ad write
     *  the report in a file. 
     *
     */
    public void reportMismatches(String documentID, String outputPath) {
        for (Map.Entry<String, List<Mismatch>> entry : mismatches.entrySet()) {
            String field = entry.getKey();
            List<Mismatch> fieldMismatches = entry.getValue();

            // open report file for this field in append mode
            Writer writerMismatch = null;
            try {
                if ( (outputPath == null) || (outputPath.trim().length() == 0))
                    outputPath = "mismatch-"+field+".txt";
                else 
                    outputPath += "/mismatch-"+field+".txt";
                writerMismatch = new PrintWriter(new BufferedWriter(new FileWriter("mismatch-"+field+".txt", true)));

                writerMismatch.write("\n" + documentID + "\n-------------------\n");

                for(Mismatch mismatch : fieldMismatches) {
                    // first annotator
                    Pair<String, SoftciteAnnotation> annotation1 = mismatch.getAnnotations1();

                    // second annotator
                    Pair<String, List<SoftciteAnnotation>> annotations2 = mismatch.getAnnotations2();

                    writerMismatch.write(annotation1.getA() + ":\t");
                    SoftciteAnnotation annot = annotation1.getB();
                    if (annot.getField(field) != null) {
                        writerMismatch.write(annot.getField(field) + " ");
                    }

                    writerMismatch.write("\n" + annotations2.getA() + ":\t");
                    boolean first = true;
                    for(SoftciteAnnotation annotation : annotations2.getB()) {
                        if (annotation.getField(field) != null) {
                            if (first)
                                first = false;
                            else
                                writerMismatch.write(" / ");    
                            writerMismatch.write(annotation.getField(field));
                        }
                    }
                    writerMismatch.write("\n\n");
                }

            } catch(Exception e) {
                logger.error("Could not write the mismatch report for field " + field, e);
            } finally {
                try {
                    if (writerMismatch != null)
                        writerMismatch.close();
                } catch(Exception e) {
                    logger.error("Problem closing the mismatch report file", e);
                }
            }
        }
    }

    private void initReportMismatches(String outputPath) {
        for(String field : AnnotatedCorpusGeneratorCSV.fields) {
            // reinit report file for this field
            Writer writerMismatch = null;
            try {
                if ( (outputPath == null) || (outputPath.trim().length() == 0))
                    outputPath = "mismatch-"+field+".txt";
                else 
                    outputPath += "/mismatch-"+field+".txt";
                writerMismatch = new PrintWriter(new BufferedWriter(new FileWriter("mismatch-"+field+".txt")));
            } catch(Exception e) {
                logger.error("Could not write the mismatch report for field " + field, e);
            } finally {
                try {
                    if (writerMismatch != null)
                        writerMismatch.close();
                } catch(Exception e) {
                    logger.error("Problem closing the mismatch report file", e);
                }
            }
        }
    }
}