package org.grobid.core.engines.label;

import org.grobid.core.GrobidModels;

public class SoftwareTaggingLabels extends TaggingLabels {
	
	private SoftwareTaggingLabels() {
    	super();
    }
	
	public final static String SOFTWARE_LABEL = "<software>";
    public final static String SOFTWARE_URL_LABEL = "<url>";
    public final static String VERSION_LABEL = "<version>";
    public final static String CREATOR_LABEL = "<creator>";
    public final static String OTHER_LABEL = "<other>";
    
    public static final TaggingLabel SOFTWARE = new TaggingLabelImpl(GrobidModels.SOFTWARE, SOFTWARE_LABEL);
    public static final TaggingLabel SOFTWARE_URL = new TaggingLabelImpl(GrobidModels.SOFTWARE, SOFTWARE_URL_LABEL);
    public static final TaggingLabel VERSION = new TaggingLabelImpl(GrobidModels.SOFTWARE, VERSION_LABEL);
    public static final TaggingLabel CREATOR = new TaggingLabelImpl(GrobidModels.SOFTWARE, CREATOR_LABEL);
    public static final TaggingLabel OTHER = new TaggingLabelImpl(GrobidModels.SOFTWARE, OTHER_LABEL);
    
    static {
    	register(SOFTWARE);
        register(SOFTWARE_URL);
        register(VERSION);
        register(CREATOR);
    	register(OTHER);
    }

}
