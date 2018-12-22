package org.grobid.core.engines;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.*;
import java.util.regex.*;
import java.text.*;

import org.grobid.core.utilities.OffsetPosition;
import org.grobid.core.data.Entity;
import org.grobid.core.lang.Language;
import org.grobid.core.utilities.LanguageUtilities;
import org.grobid.trainer.LabelStat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import smile.data.*;
import smile.data.parser.*;
import smile.regression.*;
import com.thoughtworks.xstream.*;

/**
 * Class for sharing data structures and methods to be used by the machine learning scorers
 */
public class NerdModel {
	private static final Logger LOGGER = LoggerFactory.getLogger(NerdModel.class);

	public enum MLModel {
    	RANDOM_FOREST, GRADIENT_TREE_BOOST
	}

	// default model type
	protected MLModel model = MLModel.RANDOM_FOREST;

	// regression model
	protected Regression<double[]> forest = null;

	// for serialization of the classifier
	protected XStream xstream = null;
	protected ArffParser arffParser = null;

	// data
	protected String arffDataset = null;
	protected AttributeDataset attributeDataset = null;
	protected Attribute[] attributes = null;
	protected int positives = 0; // nb of positive examples for the dataset
	protected int negatives =0; // nb of positive examples for the dataset
	
	// for balanced dataset, sampling is 1.0, 
	// for sample < 1.0, positive increases correspondingly
	protected double sampling = 1.0;

	public NerdModel() {
		xstream = new XStream();
		XStream.setupDefaultSecurity(xstream);
		Class[] classArray = new Class[] {
			GradientTreeBoost.class, RandomForest.class, 
			RegressionTree.class, NumericAttribute.class, 
			NominalAttribute.class, Attribute.class};
		xstream.allowTypes(classArray);
		arffParser = new ArffParser();
	}

	public void saveTrainingData(File file) throws Exception {
		FileUtils.writeStringToFile(file, arffDataset, StandardCharsets.UTF_8);
		System.out.println("Training data saved under " + file.getPath());
	}
	
	public void loadTrainingData(File file) throws Exception {
		attributeDataset = arffParser.parse(new FileInputStream(file));
		LOGGER.debug("Training data loaded from file " + file.getPath());
	}
	
	public void clearTrainingData() {
		//dataset = null;
		arffDataset = null;
		attributeDataset = null;
	}
}