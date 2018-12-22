package org.grobid.core.engines;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.*;
import java.util.regex.*;

import org.grobid.core.utilities.OffsetPosition;
import org.grobid.core.lang.Language;
import org.grobid.core.utilities.LanguageUtilities;
import org.grobid.core.utilities.TextUtilities;
import org.grobid.trainer.LabelStat;
import org.grobid.core.analyzers.GrobidAnalyzer;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.utilities.UnicodeUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import smile.data.*;
import smile.data.parser.*;
import smile.regression.*;
import com.thoughtworks.xstream.*;

/**
 * A machine learning model for scoring annotation confidence with clues external 
 * from the annotators that produce them. 
 */
public class ConfidenceScorer extends ScorerModel {
	private static final Logger LOGGER = LoggerFactory.getLogger(ConfidenceScorer.class);

	// ranker model files
	private static String MODEL_PATH = "resources/model/scorer";

	public ConfidenceScorer() {
		super();

		//model = MLModel.GRADIENT_TREE_BOOST;
		model = MLModel.RANDOM_FOREST;
		
		GenericScorerFeatureVector feature = new SoftwareScorerFeatureVector();
		arffParser.setResponseIndex(feature.getNumFeatures()-1);
	}

	public double getProbability(double tf_idf, 
								 double dice) throws Exception {
		if (forest == null) {
			// load model
			File modelFile = new File(MODEL_PATH+"-software.model"); 
			if (!modelFile.exists()) {
                logger.debug("Invalid model file for software scorer.");
			}
			InputStream xml = new FileInputStream(modelFile);
			if (model == MLModel.RANDOM_FOREST)
				forest = (RandomForest)xstream.fromXML(xml);
			else
				forest = (GradientTreeBoost)xstream.fromXML(xml);
			if (attributeDataset != null) 
				attributes = attributeDataset.attributes();
			else {
				StringBuilder arffBuilder = new StringBuilder();
				GenericSelectionFeatureVector feat = new SoftwareScorerFeatureVector();
				arffBuilder.append(feat.getArffHeader()).append("\n");
				arffBuilder.append(feat.printVector());
				String arff = arffBuilder.toString();
				attributeDataset = arffParser.parse(IOUtils.toInputStream(arff, "UTF-8"));
				attributes = attributeDataset.attributes();
				attributeDataset = null;
			}
			logger.info("Model for software scorer loaded: " + 
				MODEL_PATH+"-software.model");
		}

		GenericScorerFeatureVector feature = getNewFeature();
		feature.tf_idf = tf_idf;
		feature.dice = dice;
		double[] features = feature.toVector(attributes);
		
		smile.math.Math.setSeed(7);
		final double score = forest.predict(features);

		/*LOGGER.debug("Software confidence scorer: " +
				"tf/idf : "+ tf_idf+", " +
				"dice : "+dice+", "
		);*/

		return score;
	}

	public void saveModel() throws Exception {
		logger.info("saving model");
		// save the model with XStream
		String xml = xstream.toXML(forest);
		File modelFile = new File(MODEL_PATH+"-software.model"); 
		if (!modelFile.exists()) {
            logger.debug("Invalid file for saving software scorer model.");
		}
		FileUtils.writeStringToFile(modelFile, xml, "UTF-8");
		LOGGER.debug("Model saved under " + modelFile.getPath());
	}

	public void loadModel() throws Exception {
		logger.info("loading model");
		// load model
		File modelFile = new File(MODEL_PATH+"-software.model"); 
		if (!modelFile.exists()) {
        	LOGGER.debug("Model file for software scorer does not exist.");
        	throw new GrobidResourceException("Model file for software scorer does not exist.");
		}
		String xml = FileUtils.readFileToString(modelFile, "UTF-8");
		if (model == MLModel.RANDOM_FOREST)
			forest = (RandomForest)xstream.fromXML(xml);
		else
			forest = (GradientTreeBoost)xstream.fromXML(xml);
		LOGGER.debug("Model for software scorer loaded.");
	}

	public void trainModel() throws Exception {
		if (attributeDataset == null) {
			LOGGER.debug("Training data for software scorer has not been loaded or prepared");
			throw new NerdResourceException("Training data for nerd selector has not been loaded or prepared");
		}
		logger.info("building model");
		double[][] x = attributeDataset.toArray(new double[attributeDataset.size()][]);
		double[] y = attributeDataset.toArray(new double[attributeDataset.size()]);
		
		long start = System.currentTimeMillis();
		smile.math.Math.setSeed(7);
		if (model == MLModel.RANDOM_FOREST)
			forest = new RandomForest(attributeDataset.attributes(), x, y, 200);
		else {
			//nb trees: 500, maxNodes: 6, srinkage: 0.05, subsample: 0.7
			forest = new GradientTreeBoost(attributeDataset.attributes(), x, y, 
				GradientTreeBoost.Loss.LeastAbsoluteDeviation, 500, 6, 0.05, 0.7);
		}
        System.out.println("Software scorer model created in " + 
			(System.currentTimeMillis() - start) / (1000.00) + " seconds");
	}

	public void train(ArticleTrainingSample articles, File file) throws Exception {
		if (articles.size() == 0) {
			return;
		}
		StringBuilder arffBuilder = new StringBuilder();
		GenericScorerFeatureVector feat = new SoftwareScorerFeatureVector();
		arffBuilder.append(feat.getArffHeader()).append("\n");
		FileUtils.writeStringToFile(file, arffBuilder.toString(), StandardCharsets.UTF_8);
		int nbArticle = 0;
		positives = 1;
		negatives = 0;
		
		

		//arffDataset = arffBuilder.toString();
		arffDataset = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
		//System.out.println(arffDataset);
		attributeDataset = arffParser.parse(IOUtils.toInputStream(arffDataset, StandardCharsets.UTF_8));
		System.out.println("Training data saved under " + file.getPath());
	}

	/**
	 * Evaluate the selector with a set of articles, given an existing ranker for preprocessing.
	 * Boolean parameter `full` indicates if only the selector is evaluated or if the full end-to-end
	 * process is evaluated with additional overlap pruning.
	 */
	public LabelStat evaluate(ArticleTrainingSample testSet, boolean full) throws Exception {	
		List<LabelStat> stats = new ArrayList<LabelStat>();
		int n = 0;
		for (Article article : testSet.getSample()) {
			System.out.println("Evaluating on article " + (n+1) + " / " + testSet.getSample().size());
			
			
			
			n++;
		}
		return EvaluationUtil.evaluate(testSet, stats);
	}

}
