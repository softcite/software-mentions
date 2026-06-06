package org.grobid.core.engines;

import org.grobid.core.data.SoftwareComponent;
import org.grobid.core.data.SoftwareContextAttributes;
import org.grobid.core.data.SoftwareEntity;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Model-free unit tests for the document-level context propagation logic. Unlike
 * {@link SoftwareContextClassifierTest}, these do not load GROBID or the DeLFT models: they exercise
 * the pure post-processing in {@link SoftwareContextClassifier#documentPropagation(List)} and
 * {@link SoftwareEntity#mergeDocumentContextAttributes(SoftwareContextAttributes)} directly.
 *
 * Regression coverage for the NPE that was dormant while the context classifier call was commented
 * out in SoftwareParser.processTEI, and which re-surfaced once that call was re-enabled.
 */
public class SoftwareContextPropagationTest {

    private static SoftwareEntity entityWith(String rawName, SoftwareContextAttributes attributes) {
        SoftwareEntity entity = new SoftwareEntity();
        entity.setSoftwareName(new SoftwareComponent(rawName));
        entity.setMentionContextAttributes(attributes);
        return entity;
    }

    /**
     * documentPropagation() must not NPE when a mention has no context attributes at all, or has an
     * attributes object with some flags/scores left null (which happens when one of the binary
     * classifiers returns null and that attribute is therefore never set).
     */
    @Test
    public void testDocumentPropagationWithNullAndPartialAttributes() {
        // partially populated: used is set, created/shared left null (as if a classifier returned null)
        SoftwareContextAttributes partial = new SoftwareContextAttributes();
        partial.setUsed(true);
        partial.setUsedScore(0.9);

        // fully populated, different software name
        SoftwareContextAttributes full = new SoftwareContextAttributes();
        full.init();
        full.setCreated(true);
        full.setCreatedScore(0.8);

        List<SoftwareEntity> entities = new ArrayList<>();
        entities.add(entityWith("R", partial));
        entities.add(entityWith("R", null));      // mentionContextAttributes == null
        entities.add(entityWith("Python", full));

        // must not throw
        List<SoftwareEntity> result = SoftwareContextClassifier.documentPropagation(entities);
        assertThat(result, hasSize(3));

        // "R" group: the non-null "used" vote propagates; missing flags resolve to false, never null
        SoftwareContextAttributes rDoc = result.get(0).getDocumentContextAttributes();
        assertNotNull(rDoc);
        assertThat(rDoc.getUsed(), is(true));
        assertThat(rDoc.getShared(), is(false));
        assertThat(rDoc.getCreated(), is(false));
        assertEquals(Double.valueOf(0.9), rDoc.getUsedScore());

        // the null-mention entity still receives the propagated document attributes
        assertNotNull(result.get(1).getDocumentContextAttributes());

        // "Python" group keeps its created vote
        SoftwareContextAttributes pyDoc = result.get(2).getDocumentContextAttributes();
        assertNotNull(pyDoc);
        assertThat(pyDoc.getCreated(), is(true));
    }

    /**
     * mergeDocumentContextAttributes() must not NPE when the incoming attributes carry a null score
     * while the existing document score is already set.
     */
    @Test
    public void testMergeDocumentContextAttributesWithNullScore() {
        SoftwareEntity entity = new SoftwareEntity();

        SoftwareContextAttributes first = new SoftwareContextAttributes();
        first.init();
        first.setUsedScore(0.5);
        entity.mergeDocumentContextAttributes(first);

        // incoming attributes with a null usedScore — previously unboxed in the > comparison and NPE'd
        SoftwareContextAttributes second = new SoftwareContextAttributes();
        entity.mergeDocumentContextAttributes(second);

        assertEquals(Double.valueOf(0.5), entity.getDocumentContextAttributes().getUsedScore());
    }

}
