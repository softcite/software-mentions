package org.grobid.core.lexicon;

import org.grobid.core.analyzers.SoftwareAnalyzer;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.exceptions.GrobidResourceException;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.Pair;
import org.grobid.core.utilities.OffsetPosition;
import org.grobid.core.utilities.LayoutTokensUtil;
import org.grobid.core.utilities.Utilities;
import org.grobid.core.lexicon.FastMatcher;
import org.grobid.core.layout.LayoutToken;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Class for managing the lexical resources for software entities.
 *
 * @author Patrice
 */
public class SoftwareLexicon {

    // NER base types
    public enum Software_Type {
        UNKNOWN("UNKNOWN"),
        SOFTWARE("SOFTWARE"),
        ENVIRONMENT("ENVIRONMENT"),
        COMPONENT("COMPONENT"),
        IMPLICIT("IMPLICIT"),
        LANGUAGE("LANGUAGE");

        private String name;

        private Software_Type(String name) {
            this.name = name;
        }

        public String getName() {
            return name.toLowerCase();
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

    private List<String> englishStopwords = null;

    private List<String> blacklistSoftwareNames = null;

    // a map to store information on programming languages:
    // name of the programming language (as Wikipedia English page title), Wikipedia EN URL, Wikidata ID
    private Map<String, Pair<String,String>> programmingLanguages = null;

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
        File file = new File("resources/lexicon/wikidata-software.txt");
        file = new File(file.getAbsolutePath());
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

            //dis = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(file)), "UTF-8"));
            dis = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
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
        file = new File(file.getAbsolutePath());
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
        file = new File(file.getAbsolutePath());
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
        file = new File(file.getAbsolutePath());
        File fileExtra = new File("resources/lexicon/softwareRelated.txt.types");
        fileExtra = new File(fileExtra.getAbsolutePath());
        if (!file.exists()) {
            throw new GrobidResourceException("Cannot initialize software subtype dictionary, because file '" + 
                file.getAbsolutePath() + "' does not exists.");
        }
        if (!fileExtra.exists()) {
            throw new GrobidResourceException("Cannot initialize software subtype dictionary, because file '" + 
                fileExtra.getAbsolutePath() + "' does not exists.");
        }
        if (!file.canRead()) {
            throw new GrobidResourceException("Cannot initialize software subtype dictionary, because cannot read file '" + 
                file.getAbsolutePath() + "'.");
        }
        if (!fileExtra.canRead()) {
            throw new GrobidResourceException("Cannot initialize software subtype dictionary, because cannot read file '" + 
                fileExtra.getAbsolutePath() + "'.");
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

            dis = new BufferedReader(new InputStreamReader(new FileInputStream(fileExtra), "UTF-8"));
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

        // a list of stopwords for English for conservative checks with software names
        englishStopwords = new ArrayList<>();
        file = new File("resources/lexicon/stopwords_en.txt");
        file = new File(file.getAbsolutePath());
        if (!file.exists()) {
            throw new GrobidResourceException("Cannot initialize English stopwords, because file '" + 
                file.getAbsolutePath() + "' does not exists.");
        }
        if (!file.canRead()) {
            throw new GrobidResourceException("Cannot initialize English stopwords, because cannot read file '" + 
                file.getAbsolutePath() + "'.");
        }
        // read the file
        try {
            dis = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String l = null;
            while ((l = dis.readLine()) != null) {
                if (l.length() == 0) continue;
                englishStopwords.add(l.trim());
            }
        } catch (FileNotFoundException e) {
            throw new GrobidException("English stopwords file not found.", e);
        } catch (IOException e) {
            throw new GrobidException("Cannot read English stopwords file.", e);
        } finally {
            try {
                if (dis != null)
                    dis.close();
            } catch(Exception e) {
                throw new GrobidResourceException("Cannot close IO stream.", e);
            }
        }

        programmingLanguages = new TreeMap<>();
        file = new File("resources/lexicon/programming-languages.csv");
        file = new File(file.getAbsolutePath());
        if (!file.exists()) {
            throw new GrobidResourceException("Cannot initialize programming language map, because file '" + 
                file.getAbsolutePath() + "' does not exists.");
        }
        if (!file.canRead()) {
            throw new GrobidResourceException("Cannot initialize programming language map, because cannot read file '" + 
                file.getAbsolutePath() + "'.");
        }
        // read the file
        try {
            dis = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String l = null;
            while ((l = dis.readLine()) != null) {
                if (l.length() == 0) continue;
                String[] pieces = l.split(",");
                if (pieces.length != 3)
                    continue;
                String languageName = pieces[0];
                Pair<String,String> wikiInfo = new Pair<>(pieces[1],pieces[2]);
                programmingLanguages.put(languageName, wikiInfo);

                // all uppercase variant 
                String languageNameUpperCase = languageName.toUpperCase();
                if (programmingLanguages.get(languageNameUpperCase) == null)
                    programmingLanguages.put(languageNameUpperCase, wikiInfo);
            }
        } catch (FileNotFoundException e) {
            throw new GrobidException("programming language file not found.", e);
        } catch (IOException e) {
            throw new GrobidException("Cannot read programming language file.", e);
        } finally {
            try {
                if (dis != null)
                    dis.close();
            } catch(Exception e) {
                throw new GrobidResourceException("Cannot close IO stream.", e);
            }
        }

        // a list of invalid software names for conservative checks
        blacklistSoftwareNames = new ArrayList<>();
        file = new File("resources/lexicon/software_name_blacklist.txt");
        file = new File(file.getAbsolutePath());
        if (!file.exists()) {
            throw new GrobidResourceException("Cannot initialize software name blacklist, because file '" + 
                file.getAbsolutePath() + "' does not exists.");
        }
        if (!file.canRead()) {
            throw new GrobidResourceException("Cannot initialize software name blacklist, because cannot read file '" + 
                file.getAbsolutePath() + "'.");
        }
        // read the file
        try {
            dis = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String l = null;
            while ((l = dis.readLine()) != null) {
                if (l.length() == 0) continue;
                if (l.trim().startsWith("#")) continue;
                blacklistSoftwareNames.add(l.trim());
            }
        } catch (FileNotFoundException e) {
            throw new GrobidException("Software name blacklist file not found.", e);
        } catch (IOException e) {
            throw new GrobidException("Cannot read software name blacklist file.", e);
        } finally {
            try {
                if (dis != null)
                    dis.close();
            } catch(Exception e) {
                throw new GrobidResourceException("Cannot close IO stream.", e);
            }
        }

        /*file = new File("resources/lexicon/covid_blacklist.txt");
        file = new File(file.getAbsolutePath());
        if (!file.exists()) {
            throw new GrobidResourceException("Cannot initialize covid domain software name blacklist, because file '" + 
                file.getAbsolutePath() + "' does not exists.");
        }
        if (!file.canRead()) {
            throw new GrobidResourceException("Cannot initialize covid domain software name blacklist, because cannot read file '" + 
                file.getAbsolutePath() + "'.");
        }
        // read the file
        try {
            dis = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String l = null;
            while ((l = dis.readLine()) != null) {
                if (l.length() == 0) continue;
                blacklistSoftwareNames.add(l.trim());
            }
        } catch (FileNotFoundException e) {
            throw new GrobidException("Blacklist file not found.", e);
        } catch (IOException e) {
            throw new GrobidException("Cannot read covid domain software name blacklist file.", e);
        } finally {
            try {
                if (dis != null)
                    dis.close();
            } catch(Exception e) {
                throw new GrobidResourceException("Cannot close IO stream.", e);
            }
        }*/
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

    // to use the url pattern in grobid-core after merging branch update_header
    static public final Pattern urlPattern = Pattern
        .compile("(?i)(https?|ftp)\\s?:\\s?//\\s?[-A-Z0-9+&@#/%=~_:.]*[-A-Z0-9+&@#/%=~_]");
        //.compile("(?i)(https?|ftp)\\s?:\\s?//\\s?[-A-Z0-9+&@#/%?=~_()|!:,.;]*[-A-Z0-9+&@#/%=~_()|]");

    // to use the same method in grobid-core Utilities.java after merging branch update_header
    public static List<OffsetPosition> convertStringOffsetToTokenOffset(
        List<OffsetPosition> stringPosition, List<LayoutToken> tokens) {
        List<OffsetPosition> result = new ArrayList<OffsetPosition>();
        int indexText = 0;
        int indexToken = 0;
        OffsetPosition currentPosition = null;
        LayoutToken token = null;
        for(OffsetPosition pos : stringPosition) {
            while(indexToken < tokens.size()) {

                token = tokens.get(indexToken);
                if (token.getText() == null) {
                    indexToken++;
                    continue;
                }
                
                if (indexText >= pos.start) {
                    // we have a start
                    currentPosition = new OffsetPosition(indexToken, indexToken);
                    // we need an end
                    boolean found = false;
                    while(indexToken < tokens.size()) {
                        token = tokens.get(indexToken);

                        if (token.getText() == null) {
                            indexToken++;
                            continue;
                        }

                        if (indexText+token.getText().length() >= pos.end) {
                            // we have an end
                            currentPosition.end = indexToken;
                            result.add(currentPosition);
                            found = true;
                            break;
                        }
                        indexToken++;
                        indexText += token.getText().length();
                    }
                    if (found) {
                        indexToken++;
                        indexText += token.getText().length();
                        break;
                    } else {
                        currentPosition.end = indexToken-1;
                        result.add(currentPosition);
                    }
                }
                indexToken++;
                indexText += token.getText().length();
            }
        }
        return result;
    }

    public List<OffsetPosition> tokenPositionsUrlVectorLabeled(List<Pair<String, String>> pairs) {
        List<LayoutToken> tokens = new ArrayList<LayoutToken>();
        for(Pair<String, String> thePair : pairs) {
            tokens.add(new LayoutToken(thePair.getA()));
        }
        String text = LayoutTokensUtil.toText(tokens);
        List<OffsetPosition> textResult = new ArrayList<OffsetPosition>();
        Matcher urlMatcher = urlPattern.matcher(text);
        while (urlMatcher.find()) {  
            //System.out.println(urlMatcher.start() + " / " + urlMatcher.end() + " / " + text.substring(urlMatcher.start(), urlMatcher.end()));                 
            textResult.add(new OffsetPosition(urlMatcher.start(), urlMatcher.end()));
        }
        return convertStringOffsetToTokenOffset(textResult, tokens);
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

    public boolean isEnglishStopword(String value) {
        if (this.englishStopwords == null || value == null)
            return false;
        if (value.length() == 1) 
            value = value.toLowerCase();
        return this.englishStopwords.contains(value);
    }

    /**
     * If known programming language, we return wikipedia-en URL and Wikidata ID as Pair of String, 
     * if available. 
     * If the programming language is unknonw, we return null.
     **/
    public Pair<String, String> getProgrammingLanguageWikiInfo(String rawProgrammingLanguageString) {
        return programmingLanguages.get(rawProgrammingLanguageString);
    }

    public boolean isInSoftwareNameBlacklist(String value) {
        if (this.blacklistSoftwareNames == null || value == null)
            return false;
        return this.blacklistSoftwareNames.contains(value);
    }

}
