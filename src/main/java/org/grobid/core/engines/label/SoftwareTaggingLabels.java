package org.grobid.core.engines.label;

import org.grobid.core.GrobidModels;
import org.grobid.core.engines.SoftwareModels;

public class SoftwareTaggingLabels extends TaggingLabels {
	
	private SoftwareTaggingLabels() {
    	super();
    }
	
	public final static String SOFTWARE_LABEL = "<software>";
    public final static String SOFTWARE_URL_LABEL = "<url>";
    public final static String VERSION_LABEL = "<version>";
    public final static String CREATOR_LABEL = "<creator>";
    
    public final static String OTHER_LABEL = "<other>";

    public final static String ENVIRONMENT_LABEL = "<environment>";
    public final static String LANGUAGE_LABEL = "<language>";
    public final static String COMPONENT_LABEL = "<component>";
    public final static String IMPLICT_LABEL = "<implicit>";
    
    public static final TaggingLabel SOFTWARE = new TaggingLabelImpl(GrobidModels.SOFTWARE, SOFTWARE_LABEL);
    public static final TaggingLabel SOFTWARE_URL = new TaggingLabelImpl(GrobidModels.SOFTWARE, SOFTWARE_URL_LABEL);
    public static final TaggingLabel VERSION = new TaggingLabelImpl(GrobidModels.SOFTWARE, VERSION_LABEL);
    public static final TaggingLabel CREATOR = new TaggingLabelImpl(GrobidModels.SOFTWARE, CREATOR_LABEL);
    public static final TaggingLabel OTHER = new TaggingLabelImpl(GrobidModels.SOFTWARE, OTHER_LABEL);
    
    public static final TaggingLabel ENVIRONMENT = new TaggingLabelImpl(SoftwareModels.SOFTWARE_TYPE, ENVIRONMENT_LABEL);
    public static final TaggingLabel LANGUAGE = new TaggingLabelImpl(SoftwareModels.SOFTWARE_TYPE, LANGUAGE_LABEL);
    public static final TaggingLabel COMPONENT = new TaggingLabelImpl(SoftwareModels.SOFTWARE_TYPE, COMPONENT_LABEL);
    public static final TaggingLabel IMPLICIT = new TaggingLabelImpl(SoftwareModels.SOFTWARE_TYPE, IMPLICT_LABEL);
    public static final TaggingLabel OTHER_TYPE = new TaggingLabelImpl(SoftwareModels.SOFTWARE_TYPE, OTHER_LABEL);

    static {
    	register(SOFTWARE);
        register(SOFTWARE_URL);
        register(VERSION);
        register(CREATOR);
    	register(OTHER);
        register(OTHER_TYPE);
        register(ENVIRONMENT);
        register(LANGUAGE);
        register(COMPONENT);
        register(IMPLICIT);
    }

}
