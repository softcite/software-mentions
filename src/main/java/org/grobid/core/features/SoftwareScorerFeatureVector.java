package org.grobid.core.features;

import java.io.*;
import java.util.*;

import java.util.regex.*;

/**
 * Features for the confidence scores of a software component/entity.
 */
public class SoftwareScorerFeatureVector extends GenericScorerFeatureVector {
	
	public SoftwareScorerFeatureVector() {
		super();
		title = "Confidence scorer for software mentions";
		add_tf_idf = true;
		add_dice = true;
		add_is_software_name = true;
		target_numeric = true;
	}
}