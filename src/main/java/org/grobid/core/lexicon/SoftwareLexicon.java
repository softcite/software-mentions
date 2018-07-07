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

/**
 * Class for managing the lexical resources for software entities.
 *
 * @author Patrice
 */
public class SoftwareLexicon {

    // NER base types
    public enum Software_Type {
        UNKNOWN("UNKNOWN"),
        OBJECT("OBJECT");

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

        File file = new File(GrobidProperties.getGrobidHomePath()+"/../grobid-software/resources/lexicon/softwareVoc.txt");
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
            softwarePattern = new FastMatcher(file, SoftwareAnalyzer.getInstance());

            dis = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF8"));
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
    }
	
	public boolean inSoftwareDictionary(String string) {
		// here a lexical look-up...
		return softwareVocabulary.contains(string);
	}

    public List<OffsetPosition> tokenPositionsSoftwareNamesVectorLabeled(List<Pair<String, String>> pairs) {
        List<OffsetPosition> results = softwarePattern.matcherPairs(pairs);
        return results;
    }

    public List<OffsetPosition> tokenPositionsSoftwareNames(List<LayoutToken> vector) {
        List<OffsetPosition> results = softwarePattern.matchLayoutToken(vector);
        return results;
    }
}
