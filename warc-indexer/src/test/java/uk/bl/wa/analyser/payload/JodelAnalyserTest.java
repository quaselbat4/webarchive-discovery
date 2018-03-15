package uk.bl.wa.analyser.payload;

import com.typesafe.config.ConfigFactory;
import org.junit.Assert;
import org.junit.Test;
import uk.bl.wa.solr.SolrFields;
import uk.bl.wa.solr.SolrRecord;
import uk.bl.wa.util.TestUtil;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
public class JodelAnalyserTest {
    public static final String SAMPLE1 = TestUtil.loadUTF8("jodel.json");

    @Test
    @SuppressWarnings({"deprecation", "unchecked"})
    public void testBenignJodel() {
        JodelAnalyser ja = new JodelAnalyser(ConfigFactory.load());
        SolrRecord solrRecord = new SolrRecord();
        ja.extractor.applyRules(SAMPLE1, solrRecord);
        solrRecord.makeFieldSingleStringValued(SolrFields.SOLR_EXTRACTED_TEXT);

        String content = (String)solrRecord.getField(SolrFields.SOLR_EXTRACTED_TEXT).getValue();
        assertTrue("The content field should contain primary text 'emoji'\n" + content,
                       content.contains("emoji"));
        assertTrue("The content field should contain secondary text 'Second reply'\n" + content,
                       content.contains("Second reply"));

        String images  = (String)solrRecord.getField(SolrFields.SOLR_LINKS_IMAGES).getValue();
        final String IMAGE = "5_redacted_Qh_image.jpeg";
        assertTrue("The image field should contain " + IMAGE + "\n" + content,
                       images.contains(IMAGE));

        String hashtags  = (String)solrRecord.getField(SolrFields.SOLR_KEYWORDS).getValue();
        final String TAG = "hashtag";
        assertTrue("The keywords field should contain " + TAG + "\n" + hashtags,
                       hashtags.contains(TAG));

        List<String> locations = (ArrayList<String>)solrRecord.getField(SolrFields.POSTCODE_DISTRICT).getValue();
        assertContains("The location field should contain 'Aarhus'\n" + locations,
                       "Aarhus", locations);
    }

    @Test
    public void testFallbackRule() {
        JodelAnalyser ja = new JodelAnalyser(ConfigFactory.load());
        ja.extractor.add("color", true,
                                    ".nonexisting", ".replies[].nonexisting", ".replies[].color", ".nono[].stillno");
        SolrRecord solrRecord = new SolrRecord();
        ja.extractor.applyRules(SAMPLE1, solrRecord);
        List<String> colors = (ArrayList<String>)solrRecord.getField("color").getValue();
        assertContains("The color field should contain '8ABDB0'\n" + colors,
                       "8ABDB0", colors);
    }

    private void assertContains(String message, String expectedContent, List<String> content) {
        for (String c: content) {
            if (c.contains(expectedContent)) {
                return;
            }
        }
        Assert.fail(message);
    }
}