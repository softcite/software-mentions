package org.grobid.core.engines.label;

import org.grobid.core.GrobidModels;

public class SoftwareTaggingLabels extends TaggingLabels {
	
	private SoftwareTaggingLabels() {
    	super();
    }
	
	public final static String OBJECT_LABEL = "<object>";
    public final static String OTHER_LABEL = "<other>";
    
    public static final TaggingLabel OBJECT = new TaggingLabelImpl(GrobidModels.SOFTWARE, OBJECT_LABEL);
    public static final TaggingLabel OTHER = new TaggingLabelImpl(GrobidModels.SOFTWARE, OTHER_LABEL);
    
    static {
    	register(OBJECT);
    	register(OTHER);
    }

}
