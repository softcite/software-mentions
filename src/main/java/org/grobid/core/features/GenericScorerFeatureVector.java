package org.grobid.core.features;

import java.io.*;
import java.util.*;
import java.text.*;
import java.util.regex.*;

import smile.data.Attribute;

public class GenericScorerFeatureVector {
	public String title = "Generic scorer";

	public String string = null; // lexical feature
	public double label = 0.0; // numerical label if known
	
	// mask of features
	public boolean add_dice_coef = false; // lexical cohesion measure based on DICE coefficient
	public boolean add_tf_idf = false; // term frequency inverse document frequency, tf-idf
	public boolean add_is_software_name = false; // lexical feature

	// decision types
	public boolean target_numeric = true;
	
	// features		
	public double tf_idf = 0.0; // tf-idf
	public double dice_coef = 0.0;
	public boolean is_software_name = false;

	/**
	 *  Write header of ARFF files.
	 */
	public String getArffHeader() throws IOException {
		StringBuilder header = new StringBuilder();
		header.append("% 1. Title: " + title + " \n");
		header.append("%\n"); 
		header.append("% 2. Sources: \n"); 
		header.append("% (a) Creator: GROBID \n"); 

		DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
		Date date = new Date();
		header.append("% (c) Date: " + dateFormat.format(date) + " \n"); 

		header.append("%\n");
		header.append("@RELATION @GROBID_"+ title.replace(" ","_") +" \n");
		
		if (add_dice_coef) 
			header.append("@attribute dice_coef REAL\n");
		if (add_tf_idf)	
			header.append("@attribute tf_idf REAL\n");
		if (add_is_software_name)
			header.append("@attribute is_software_name {false, true}\n");

		header.append("@attribute entity? REAL\n\n"); // target variable for regression
		header.append("@data\n");
		return header.toString();
	}
	
	public int getNumFeatures() {
		int num = 0;
		if (add_dice_coef) 
			num++;
		if (add_tf_idf)
			num++;
		if (add_is_software_name)
			num++;
		// class
		num++;
		return num;
	}
	
	public String printVector() {
		/*if (string == null) return null;
		if (string.length() == 0) return null;*/
		boolean first = true;
		
		StringBuffer res = new StringBuffer();
		
		// token string (1)
		//res.append(string);

		if (add_dice_coef) {
			if (first) {
				res.append(dice_coef);
				first = false;
			} else 
				res.append("," + dice_coef);
		}
		// term frequency inverse document frequency, tf-idf
		if (add_tf_idf) {
			if (first) {
				res.append(tf_idf);
				first = false;	
			} else {
				res.append("," + tf_idf);
			}
		}
		if (add_is_software_name) {
			if (first) {
				if (is_software_name) 
					res.append("true");
				else 
					res.append("false");
				first = false;	
			} else {
				if (is_software_name) 
					res.append(",true");
				else 
					res.append(",false");
			}
		}

		// target variable - for training data (regression: 1.0 or 0.0 for training data)
		res.append("," + label);

		return res.toString();	
	}
	
	public double[] toVector(Attribute[] attributes) {
		double[] result = new double[this.getNumFeatures()];
		int i = 0;

		if (add_dice_coef) {
			result[i] = dice_coef;
			i++;
		}
		if (add_tf_idf) {
			result[i] = tf_idf;
			i++;
		}
		if (add_is_software_name) {
			Attribute att = attributes[i];
			double trueVal = 1.0;
			double falseVal = 0.0;
			try {
				trueVal = att.valueOf("true");
				falseVal = att.valueOf("false");
			} catch (Exception e) {
				e.printStackTrace();
			}

			if (is_software_name)
				result[i] = trueVal;
			else
				result[i] = falseVal;

			i++;
		}
		return result;
	}
}