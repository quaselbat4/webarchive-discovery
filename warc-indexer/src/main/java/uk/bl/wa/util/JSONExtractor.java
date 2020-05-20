package uk.bl.wa.util;

/*
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

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * Based on a set of rules, entries in JSON are extracted and added to a SolrDocument.
 * The rules are simplified JSON-paths. Only valid elements are {@code .key} and {@code .key[]}.
 * Sample: {@code '.users[].content', '.full_text', '.resources.images[]'}.
 */
// TODO: Consider a proper JSON-framework for matching paths
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

    /**
     * Apply the rules to the given JSON, feeding the results to the consumer.
     * @param json     JSON as UTF-8 stream.
     * @param consumer receives a {@link JSONRule#destination} and a JSON-value matching the rule.
     * @return true if any rule was matched.
     * @throws IOException if the json stream could not be read.
     */
    public boolean applyRules(InputStream json, BiConsumer<String, String> consumer) throws IOException {
        return applyRules(IOUtils.toString(json, StandardCharsets.UTF_8), consumer);
    }

    /**
     * Apply the rules to the given JSON, feeding the results to the consumer.
     * @param json     plain String JSON.
     * @param consumer receives a {@link JSONRule#destination} and a JSON-value matching the rule.
     * @return true if any rule was matched.
     */
    public boolean applyRules(String json, BiConsumer<String, String> consumer) {
        return applyRules(new JSONObject(json), consumer);
    }
    /**
     * Apply the rules to the given JSON, feeding the results to the consumer.
     * @param json     parsed JSON.
     * @param consumer receives a {@link JSONRule#destination} and a JSON-value matching the rule.
     * @return true if any rule was matched.
     */
    public boolean applyRules(JSONObject json, BiConsumer<String, String> consumer) {
        boolean matched = false;
        for (JSONRule rule: rules) {
            matched |= rule.addMatching(json, consumer);
        }
        return matched;
    }

    public static class JSONRule {
        private final List<String> paths;
        private final List<List<String>> pathElements;
        private final String destination;
        private final boolean stopOnMatch;
        private final ContentCallback adjuster;

        /**
         * Create a new rule for the given destination without any paths. This rule will never match anything,
         * unless paths are added with {@link #addPath(String)}.
         * @param destination the designation for the rule and normally the end-point for the content that matches
         *                    the rule. This could be a Solr-field or something similar.
         * @param stopOnMatch if true, processing for this rule is stopped after the first matching path.
         */
        public JSONRule(String destination, boolean stopOnMatch) {
            this(destination, stopOnMatch, null, new ArrayList<>());
        }

        /**
         * Create a new rule for the given destination.
         * @param destination the designation for the rule and normally the end-point for the content that matches
         *                    the rule. This could be a Solr-field or something similar.
         * @param stopOnMatch if true, processing for this rule is stopped after the first matching path.
         * @param paths       Simplified JSON-paths. Only valid elements are {@code .key} and {@code .key[]}.
         *                    Samples: {@code .users[].content}, {@code .full_text}, {@code .resources.images[]}.
         */
        public JSONRule(String destination, boolean stopOnMatch, String... paths) {
            this(destination, stopOnMatch, null, paths == null ? new ArrayList<>() : Arrays.asList(paths));
        }

        /**
         * Create a new rule for the given destination.
         * @param destination the designation for the rule and normally the end-point for the content that matches
         *                    the rule. This could be a Solr-field or something similar.
         * @param stopOnMatch if true, processing for this rule is stopped after the first matching path.
         * @param paths       Simplified JSON-paths. Only valid elements are {@code .key} and {@code .key[]}.
         *                    Samples: {@code .users[].content}, {@code .full_text}, {@code .resources.images[]}.
         */
        public JSONRule(String destination, boolean stopOnMatch, List<String> paths) {
            this(destination, stopOnMatch, null, paths);
        }

        /**
         * Create a new rule for the given destination.
         * @param destination the designation for the rule and normally the end-point for the content that matches
         *                    the rule. This could be a Solr-field or something similar.
         * @param stopOnMatch if true, processing for this rule is stopped after the first matching path.
         * @param adjuster    optional adjustment of content before adding the content to the consumer.
         *                    If the adjuster is null, there will be no adjustments before feeding the consumer.
         *                    If the adjuster returns null, the content is discarded and the next path is tried.
         * @param paths       Simplified JSON-paths. Only valid elements are {@code .key} and {@code .key[]}.
         *                    Samples: {@code .users[].content}, {@code .full_text}, {@code .resources.images[]}.
         */
        public JSONRule(String destination, boolean stopOnMatch, ContentCallback adjuster, String... paths) {
            this(destination, stopOnMatch, adjuster, paths == null ? new ArrayList<String>() : Arrays.asList(paths));
        }

        /**
         * Create a new rule for the given destination.
         * @param destination the designation for the rule and normally the end-point for the content that matches
         *                    the rule. This could be a Solr-field or something similar.
         * @param stopOnMatch if true, processing for this rule is stopped after the first matching path.
         * @param adjuster    optional adjustment of content before adding the content to the consumer.
         *                    If the adjuster is null, there will be no adjustments before feeding the consumer.
         *                    If the adjuster returns null, the content is discarded and the next path is tried.
         * @param paths       Simplified JSON-paths. Only valid elements are {@code .key} and {@code .key[]}.
         *                    Samples: {@code .users[].content}, {@code .full_text}, {@code .resources.images[]}.
         */
        public JSONRule(String destination, boolean stopOnMatch, ContentCallback adjuster, List<String> paths) {
            this.paths = paths instanceof ArrayList ? paths : new ArrayList<>(paths);
            this.destination = destination;
            this.adjuster = adjuster;
            this.stopOnMatch = stopOnMatch;
            this.pathElements = new ArrayList<>(paths.size());
            for (String path: paths) {
                pathElements.add(splitPath(path));
            }
        }

        /**
         * Add a path to the rule.
         * @param jsonPath Simplified JSON-path. Only valid elements are {@code .key} and {@code .key[]}.
         *                 Samples: {@code .users[].content}, {@code .full_text}, {@code .resources.images[]}.
         */
        public void addPath(String jsonPath) {
            paths.add(jsonPath);
            pathElements.add(splitPath(jsonPath));
        }

        private List<String> splitPath(String path) {
            if (!path.startsWith(".")) {
                throw new RuntimeException("Invalid JSON path (does not begin with dot '.'): '" + path + "'");
            }
            String[] tokens = path.split("[.]");
            if (tokens.length < 2) {
                throw new RuntimeException("Invalid JSON path (too short): '" + path + "'");
            }
            String[] pruned = new String[tokens.length-1];
            System.arraycopy(tokens, 1, pruned, 0, tokens.length-1);
            return Arrays.asList(pruned);
        }

        private boolean addMatching(JSONObject json, BiConsumer<String, String> consumer) {
            boolean matched = false;
            for (int i = 0; i < paths.size(); i++) {
                final String path = paths.get(i);
                final List<String> elements = pathElements.get(i);

                if (addMatching(path, elements, json, consumer)) {
                    matched = true;
                    if (stopOnMatch) {
                        break;
                    }
                }
            }
            return matched;
        }

        private boolean addMatching(
                String path, List<String> elements, JSONObject json, BiConsumer<String, String> consumer) {
            List<String> contents = getMatches(json, elements, 0);
            if (contents == null) {
                return false;
            }

            boolean matched = false;
            for (String content: contents) {
                content = adjuster == null ? content : adjuster.adjust(path, destination, content);
                if (content != null) {
                    consumer.accept(destination, content);
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
                    Object value = json.get(elementName(element));
                    return value.equals(JSONObject.NULL) || "[]".equals(value.toString()) ?
                            Collections.<String>emptyList() :
                            Collections.singletonList(value.toString());
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
         * Before the content is fed to the consumer, this method is called. The response is used instead of content.
         * @param jsonPath   the jsonPath from the rules that matched.
         * @param destination the field that the content will be added to.
         * @param content    content at the given jsonPath.
         * @return the modified content or null if the content should not be added to solrField.
         */
        String adjust(String jsonPath, String destination, String content);
    }
}
