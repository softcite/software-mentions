package org.grobid.core.lexicon;

import org.grobid.core.analyzers.SoftwareAnalyzer;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.exceptions.GrobidResourceException;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.Pair;
import org.grobid.core.utilities.OffsetPosition;
import org.grobid.core.lexicon.FastMatcher;
import org.grobid.core.layout.LayoutToken;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Class for managing the lexical resources for software entities.
 *
 * @author Patrice
 */
public class SoftwareLexicon {

    // NER base types
    public enum Software_Type {
        UNKNOWN("UNKNOWN"),
        SOFTWARE("SOFTWARE");

        private String name;

        private Software_Type(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private static Logger LOGGER = LoggerFactory.getLogger(SoftwareLexicon.class);

    private Set<String> softwareVocabulary = null;
    private FastMatcher softwarePattern = null;

    private Map<String, Double> termIDF = null;

    // the list of Wikipedia categories where software articles belong to
    private List<String> wikipediaCategories = null;

    // the list of P31 and P279 values of the Wikidata software entities
    private List<String> propertyValues = null;

    private static volatile SoftwareLexicon instance;

    public static synchronized SoftwareLexicon getInstance() {
        if (instance == null)
            instance = new SoftwareLexicon();

        return instance;
    }

    private SoftwareLexicon() {
        // init the lexicon
        LOGGER.info("Init software lexicon");
        softwareVocabulary = new HashSet<String>();

        // Software lexicon

        //File file = new File(GrobidProperties.getGrobidHomePath()+"/../software-mentions/resources/lexicon/wikidata-softwares.txt");
        File file = new File("resources/lexicon/wikidata-software.txt.gz");
        if (!file.exists()) {
            throw new GrobidResourceException("Cannot initialize software dictionary, because file '" + 
                file.getAbsolutePath() + "' does not exists.");
        }
        if (!file.canRead()) {
            throw new GrobidResourceException("Cannot initialize software dictionary, because cannot read file '" + 
                file.getAbsolutePath() + "'.");
        }

        BufferedReader dis = null;
        // read the lexicon file
        try {
            softwarePattern = new FastMatcher(file, SoftwareAnalyzer.getInstance(), true); // case sensitive

            dis = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(file)), "UTF-8"));
            //dis = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String l = null;
            while ((l = dis.readLine()) != null) {
                if (l.length() == 0) continue;
                List<String> tokens = SoftwareAnalyzer.getInstance().tokenize(l);
                for(String token : tokens) {
                    if (token.length() > 1) {
                        // should we filter out 100% numerical tokens?
                        if (!softwareVocabulary.contains(token)) {
                            softwareVocabulary.add(token);
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            throw new GrobidException("SoftwareLexicon file not found.", e);
        } catch (IOException e) {
            throw new GrobidException("Cannot read SoftwareLexicon file.", e);
        } finally {
            try {
                if (dis != null)
                    dis.close();
            } catch(Exception e) {
                throw new GrobidResourceException("Cannot close IO stream.", e);
            }
        }

        // term idf
        file = new File("resources/lexicon/idf.label.en.txt.gz");
        if (!file.exists()) {
            throw new GrobidResourceException("Cannot initialize software dictionary, because file '" + 
                file.getAbsolutePath() + "' does not exists.");
        }
        if (!file.canRead()) {
            throw new GrobidResourceException("Cannot initialize software dictionary, because cannot read file '" + 
                file.getAbsolutePath() + "'.");
        }

        // read the idf file
        try {
            termIDF = new TreeMap<String, Double>();

            dis = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(file)), "UTF-8"));
            //dis = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String l = null;
            while ((l = dis.readLine()) != null) {
                if (l.length() == 0) continue;

                String[] pieces = l.split("\t");
                if (pieces.length != 2) {
                    LOGGER.warn("Invalid term/idf line format: " + l);
                    continue;
                }

                String term = pieces[0];
                String idfString = pieces[1];
                double idf = 0.0;
                try {
                    idf = Double.parseDouble(idfString);
                } catch(Exception e) {
                    LOGGER.warn("Invalid idf format: " + idfString);
                    continue;
                }

                termIDF.put(term, new Double(idf));
            }
        } catch (FileNotFoundException e) {
            throw new GrobidException("SoftwareLexicon file not found.", e);
        } catch (IOException e) {
            throw new GrobidException("Cannot read SoftwareLexicon file.", e);
        } finally {
            try {
                if (dis != null)
                    dis.close();
            } catch(Exception e) {
                throw new GrobidResourceException("Cannot close IO stream.", e);
            }
        }

        // load the list of Wikipedia categories where software articles belong to
        file = new File("resources/lexicon/softwareVoc.txt.categories");
        if (!file.exists()) {
            throw new GrobidResourceException("Cannot initialize software category dictionary, because file '" + 
                file.getAbsolutePath() + "' does not exists.");
        }
        if (!file.canRead()) {
            throw new GrobidResourceException("Cannot initialize software category dictionary, because cannot read file '" + 
                file.getAbsolutePath() + "'.");
        }
        // read the file
        try {
            wikipediaCategories = new ArrayList<String>();

            dis = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String l = null;
            while ((l = dis.readLine()) != null) {
                if (l.length() == 0) continue;
                wikipediaCategories.add(l.trim().toLowerCase());
            }
        } catch (FileNotFoundException e) {
            throw new GrobidException("Software category dictionary file not found.", e);
        } catch (IOException e) {
            throw new GrobidException("Cannot read software category dictionary file.", e);
        } finally {
            try {
                if (dis != null)
                    dis.close();
            } catch(Exception e) {
                throw new GrobidResourceException("Cannot close IO stream.", e);
            }
        }

        // load the list of P31 and P279 values of the Wikidata software entities
        file = new File("resources/lexicon/softwareVoc.txt.types");
        if (!file.exists()) {
            throw new GrobidResourceException("Cannot initialize software subtype dictionary, because file '" + 
                file.getAbsolutePath() + "' does not exists.");
        }
        if (!file.canRead()) {
            throw new GrobidResourceException("Cannot initialize software subtype dictionary, because cannot read file '" + 
                file.getAbsolutePath() + "'.");
        }
        // read the file
        try {
            propertyValues = new ArrayList<String>();

            dis = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String l = null;
            while ((l = dis.readLine()) != null) {
                if (l.length() == 0) continue;
                propertyValues.add(l.trim().toLowerCase());
            }
        } catch (FileNotFoundException e) {
            throw new GrobidException("Software subtypes dictionary file not found.", e);
        } catch (IOException e) {
            throw new GrobidException("Cannot read software subtypes dictionary file.", e);
        } finally {
            try {
                if (dis != null)
                    dis.close();
            } catch(Exception e) {
                throw new GrobidResourceException("Cannot close IO stream.", e);
            }
        }
    }
	
	public boolean inSoftwareDictionary(String string) {
		// here a lexical look-up...
		return softwareVocabulary.contains(string);
	}

    public List<OffsetPosition> tokenPositionsSoftwareNamesVectorLabeled(List<Pair<String, String>> pairs) {
        List<OffsetPosition> results = softwarePattern.matcherPairs(pairs, true); // case sensitive
        return results;
    }

    public List<OffsetPosition> tokenPositionsSoftwareNames(List<LayoutToken> vector) {
        List<OffsetPosition> results = softwarePattern.matchLayoutToken(vector, true, true); // case sensitive
        return results;
    }

    public double getTermIDF(String term) {
        Double idf = termIDF.get(term);
        if (idf != null)
            return idf.doubleValue();
        else 
            return 0.0;
    }

    public boolean inSoftwarePropertyValues(String value) {
        return propertyValues.contains(value.toLowerCase());
    }

    public boolean inSoftwareCategories(String value) {
        return wikipediaCategories.contains(value.toLowerCase());
    }   
}
