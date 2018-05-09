package uk.bl.wa.util;

/*
 * #%L
 * warc-indexer
 * %%
 * Copyright (C) 2013 - 2017 The UK Web Archive
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

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import uk.bl.wa.solr.SolrRecord;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Based on a set of rules, entries in JSON are extracted and added to a SolrDocument.
 * The rules are simplified JSON-paths. Only valid elements are '.key' and '.key[]'.
 * Sample: '.users[].content', ".full_text', '.resources.images[]'.
 */
// TODO: Consider a proper JSON-framework
public class JSONExtractor {
    private static Log log = LogFactory.getLog( JSONExtractor.class );

    private final List<JSONRule> rules;

    public JSONExtractor() {
        this.rules = new ArrayList<>();
    }
    public JSONExtractor(JSONRule... rules) {
        this(Arrays.asList(rules));
    }
    public JSONExtractor(List<JSONRule> rules) {
        this.rules = rules instanceof ArrayList ? rules : new ArrayList<>(rules);
    }

    public void add(JSONRule rule) {
        rules.add(rule);
    }
    public void add(String solrField, boolean stopOnMatch, String... paths) {
        rules.add(new JSONRule(solrField, stopOnMatch, paths));
    }
    public void add(String solrField, boolean stopOnMatch, ContentCallback adjuster, String... paths) {
        rules.add(new JSONRule(solrField, stopOnMatch, adjuster, paths));
    }

    public boolean applyRules(InputStream json, SolrRecord solrRecord) throws IOException {
        return applyRules(IOUtils.toString(json, "UTF-8"), solrRecord);
    }
    public boolean applyRules(String json, SolrRecord solrRecord) {
        return applyRules(new JSONObject(json), solrRecord);
    }
    public boolean applyRules(JSONObject jodelJson, SolrRecord solrRecord) {
        boolean matched = false;
        for (JSONRule rule: rules) {
            matched |= rule.addMatching(jodelJson, solrRecord);
        }
        return matched;
    }

    public static class JSONRule {
        private final List<String> paths;
        private final List<List<String>> pathElements;
        private final String solrField;
        private final boolean stopOnMatch;
        private final ContentCallback adjuster;

        public JSONRule(String solrField, boolean stopOnMatch) {
            this(solrField, stopOnMatch, null, new ArrayList<String>());
        }

        public JSONRule(String solrField, boolean stopOnMatch, String... paths) {
            this(solrField, stopOnMatch, null, paths == null ? new ArrayList<String>() : Arrays.asList(paths));
        }

        public JSONRule(String solrField, boolean stopOnMatch, List<String> paths) {
            this(solrField, stopOnMatch, null, paths);
        }

        public JSONRule(String solrField, boolean stopOnMatch, ContentCallback adjuster, String... paths) {
            this(solrField, stopOnMatch, adjuster, paths == null ? new ArrayList<String>() : Arrays.asList(paths));
        }

        public JSONRule(String solrField, boolean stopOnMatch, ContentCallback adjuster, List<String> paths) {
            this.paths = paths instanceof ArrayList ? paths : new ArrayList<>(paths);
            this.solrField = solrField;
            this.adjuster = adjuster;
            this.stopOnMatch = stopOnMatch;
            this.pathElements = new ArrayList<>(paths.size());
            for (String path: paths) {
                pathElements.add(splitPath(path));
            }
        }

        public void addPath(String jsonPath) {
            paths.add(jsonPath);
            pathElements.add(splitPath(jsonPath));
        }

        public List<String> splitPath(String path) {
            String[] tokens = path.split("[.]");
            if (tokens.length < 2) {
                throw new RuntimeException("Invalid JSON path '" + path + "'");
            }
            String[] pruned = new String[tokens.length-1];
            System.arraycopy(tokens, 1, pruned, 0, tokens.length-1);
            return Arrays.asList(pruned);
        }

        private boolean addMatching(JSONObject json, SolrRecord solrRecord) {
            boolean matched = false;
            for (int i = 0; i < paths.size(); i++) {
                final String path = paths.get(i);
                final List<String> elements = pathElements.get(i);

                if (addMatching(path, elements, json, solrRecord)) {
                    matched = true;
                    if (stopOnMatch) {
                        break;
                    }
                }
            }
            return matched;
        }

        private boolean addMatching(String path, List<String> elements, JSONObject json, SolrRecord solrRecord) {
            List<String> contents = getMatches(json, elements, 0);
            if (contents == null) {
                return false;
            }

            boolean matched = false;
            for (String content: contents) {
                content = adjuster == null ? content : adjuster.adjust(path, solrField, content, solrRecord);
                if (content != null) {
                    if (solrRecord != null) {
                        // Normally there will always be a SolrRecord, but maybe the caller uses the JSONExtractor
                        // with custom side-effects, so we don't see the absence of a SolrRecord as an error.
                        solrRecord.addField(solrField, content);
                    }
                    matched = true;
                }
            }
            return matched;
        }

        private List<String> getMatches(JSONObject json, List<String> elements, int elementIndex) {
            final String element = elements.get(elementIndex);

            if (!json.has(elementName(element))) { // No match
                return Collections.emptyList();
            }

            // Are we at the end?
            if (elementIndex == elements.size()-1) {
                if (!isArrayPath(element)) {
                    return Collections.singletonList(json.getString(elementName(element)));
                }

                // Multi-value
                JSONArray array = json.getJSONArray(elementName(element));
                List<String> matches = new ArrayList<>();
                for (int i = 0 ; i < array.length() ; i++) {
                    matches.add(array.getString(i));
                }
                return matches;
            }

            // Recursive descend
            if (!isArrayPath(element)) {
                return getMatches(json.getJSONObject(elementName(element)), elements, elementIndex+1);
            }

            // Multi-value
            JSONArray array = json.getJSONArray(elementName(element));
            List<String> aggregated = new ArrayList<>();
            // TODO: Can this handle arrays of arrays?
            for (int i = 0 ; i < array.length() ; i++) {
                List<String> matches = getMatches(array.getJSONObject(i), elements, elementIndex+1);
                if (matches != null) {
                    aggregated.addAll(matches);
                }
            }
            return aggregated;
        }

        private boolean isArrayPath(String element) {
            return element.endsWith("[]");
        }

        // foo | foo[] -> foo
        private String elementName(String element) {
            return element.endsWith("[]") ? element.substring(0, element.length()-2) : element;
        }
    }

    /**
     * Used for performing adjustments to the content before it is added to the Solr document.
     */
    public interface ContentCallback {
        /**
         * Before the content is added to the solrField, this method is called. The response is used instead of content.
         * @param jsonPath   the jsonPath from the rules that matched.
         * @param solrField  the field that the content will be added to.
         * @param content    content at the given jsonPath.
         * @param solrRecord the record that will receive the solrField/content pair.
         * @return the modified content or null if the content should not be added to solrField.
         */
        String adjust(String jsonPath, String solrField, String content, SolrRecord solrRecord);
    }
}
