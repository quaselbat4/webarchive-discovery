package uk.bl.wa.util;

/*-
 * #%L
 * warc-indexer
 * %%
 * Copyright (C) 2013 - 2020 The webarchive-discovery project contributors
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

import org.junit.Assert;
import org.junit.Test;
import uk.bl.wa.solr.SolrRecord;

import java.util.*;

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
public class JSONExtractorTest {
    public static final String JODEL = TestUtil.loadUTF8("jodel.json");

    @Test
    public void baseTest() {
        CollectorCallback collector = new CollectorCallback();

        JSONExtractor extractor = new JSONExtractor();
        extractor.add("string", true, collector, ".details.color");
        extractor.add("null", true, collector, ".next");
        extractor.add("empty_array", true, collector, ".details.children");
        extractor.add("boolean", true, collector, ".readonly");
        extractor.add("int", true, collector, ".details.child_count");
        extractor.add("multi_int", true, collector, ".replies[].replier");
        extractor.applyRules(JODEL, null);

        for (String[] test: new String[][] {
                {"string", "8ABDB0"},
                {"null"},
                {"empty_array"},
                {"boolean", "false"},
                {"int", "8"},
                {"multi_int", "1", "2"}
        }) {
            List<String> contents = collector.getContents(test[0]);
            Assert.assertNotNull("There should be an entry for key '" + test[0] + "'",
                                 contents);
            Assert.assertEquals("There should be the expected number of entries for key '" + test[0] + "'",
                                test.length-1, contents.size());
            for (int i = 1 ; i < test.length ; i++) {
                Assert.assertEquals("Element #" + i + " for key '" + test[0] + "' should match the collected entry",
                                    test[i], contents.get(i-1));
            }
        }
    }

    private class CollectorCallback implements JSONExtractor.ContentCallback {
        public final Map<String, List<String>> collected = new HashMap<>();

        @Override
        public String adjust(String jsonPath, String solrField, String content, SolrRecord solrRecord) {
            List<String> contents = collected.get(solrField);
            if (contents == null) {
                contents = new ArrayList<>();
                collected.put(solrField, contents);
            }
            contents.add(content);
            return content;
        }

        public List<String> getContents(String solrField) {
            return collected.containsKey(solrField) ? collected.get(solrField) : Collections.<String>emptyList();
        }
    }
}
