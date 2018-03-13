package uk.bl.wa.analyser.payload;

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
        JodelAnalyser ja = new JodelAnalyser(null);
        SolrRecord solrRecord = new SolrRecord();
        ja.analyse(SAMPLE1, solrRecord);

        List<String> content = (ArrayList<String>)solrRecord.getField(SolrFields.SOLR_EXTRACTED_TEXT).getValue();

        assertContains("The content field should contain primary text 'emoji'\n" + content,
                       "emoji", content);
        assertContains("The content field should contain secondary text 'Second reply'\n" + content,
                       "Second reply", content);
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