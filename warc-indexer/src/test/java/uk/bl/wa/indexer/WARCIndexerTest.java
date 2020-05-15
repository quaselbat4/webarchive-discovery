package uk.bl.wa.indexer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/*
 * #%L
 * warc-indexer
 * $Id:$
 * $HeadURL:$
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.archive.io.ArchiveReader;
import org.archive.io.ArchiveReaderFactory;
import org.archive.io.ArchiveRecord;
import org.archive.io.warc.WARCRecord;
import org.archive.util.ArchiveUtils;
import org.archive.wayback.util.url.AggressiveUrlCanonicalizer;
import org.junit.Assert;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;

import uk.bl.wa.solr.SolrFields;
import uk.bl.wa.solr.SolrRecord;
import uk.bl.wa.solr.SolrRecordFactory;
import uk.bl.wa.util.TestUtil;

import static org.junit.Assert.*;

public class WARCIndexerTest {
    private static Log log = LogFactory.getLog(WARCIndexerTest.class );

    /**
     * Check timestamp parsing is working correctly, as various forms exist in the ARCs and WARCs.
     */
    private static final String TIMESTAMP_12 = "200009200005";
    private static final String TIMESTAMP_14 = "20000920000545";
    private static final String TIMESTAMP_16 = "2000092000054543";
    private static final String TIMESTAMP_17 = "20000920000545439";

    @Test
    public void testExtractYear() {
        assertEquals("2000", WARCIndexer.extractYear(TIMESTAMP_16));
    }

    @Test
    public void testParseCrawlDate() {
        assertEquals("2000-09-20T00:05:00Z", WARCIndexer.parseCrawlDate(TIMESTAMP_12));
        assertEquals("2000-09-20T00:05:45Z", WARCIndexer.parseCrawlDate(TIMESTAMP_14));
        assertEquals("2000-09-20T00:05:45Z", WARCIndexer.parseCrawlDate(TIMESTAMP_16));
        assertEquals("2000-09-20T00:05:45Z", WARCIndexer.parseCrawlDate(TIMESTAMP_17));
    }

    @Test
    public void testHTTPSNormalising() {
        final String HTTPS = "https://example.com";
        AggressiveUrlCanonicalizer urlNormaliser = new AggressiveUrlCanonicalizer();
        assertNotSame("The AggressiveUrlCanonicalizer's behaviour should be not to collapse https into http",
                     "http://example.com", urlNormaliser.canonicalize(HTTPS));
        assertEquals("Home-brewed https -> http collapsing should work",
                     "http://example.com", HTTPS.startsWith("https://") ? "http://" + HTTPS.substring(8) : HTTPS);
    }

    /**
     * Check URL and extension parsing is robust enough.
     */

    @Test
    public void testParseExtension() {
        assertEquals("png", WARCIndexer.parseExtension("http://host/image.png"));
        assertEquals("png", WARCIndexer.parseExtension("http://host/this/that/image.parseExtension.png"));
        // FIXME Get some bad extensions from the current Solr server and check we can deal with them
        //fail("Not implemented yet!");
    }

    /**
     * Test protocol filtering is working ok.
     *
     * @throws NoSuchAlgorithmException
     * @throws IOException
     * @throws MalformedURLException
     */
    @Test
    public void testProtocolFilters() throws NoSuchAlgorithmException, MalformedURLException, IOException {
        // Check protocol excludes:
        String path = "warc.index.extract.protocol_include";
        List<String> protocols = new ArrayList<String>();
        protocols.add("http");
        protocols.add("https");
        this.testFilterBehaviour(path, protocols, 29);
        protocols.remove("http");
        this.testFilterBehaviour(path, protocols, 34);
    }

    /**
     * Test URL filtering is working ok.
     *
     * @throws NoSuchAlgorithmException
     * @throws IOException
     * @throws MalformedURLException
     */
    @Test
    public void testUrlFilters() throws NoSuchAlgorithmException, MalformedURLException, IOException {
        // Now URL excludes:
        String path = "warc.index.extract.url_exclude";
        List<String> url_excludes = new ArrayList<String>();
        this.testFilterBehaviour(path, url_excludes, 29);
        url_excludes.add("robots.txt");
        this.testFilterBehaviour(path, url_excludes, 30);

    }

    /**
     * Test reponse code filtering is working ok.
     *
     * @throws NoSuchAlgorithmException
     * @throws IOException
     * @throws MalformedURLException
     */
    @Test
    public void testResponseCodeFilters() throws NoSuchAlgorithmException, MalformedURLException, IOException {
        // Now URL excludes:
        String path = "warc.index.extract.response_include";
        List<String> response_includes = new ArrayList<String>();
        this.testFilterBehaviour(path, response_includes, 36);
        response_includes.add("2");
        this.testFilterBehaviour(path, response_includes, 29);
        response_includes.add("3");
        this.testFilterBehaviour(path, response_includes, 20);
    }

    /**
     * @throws MalformedURLException
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    @Test
    public void testExclusionFilter() throws MalformedURLException, NoSuchAlgorithmException, IOException {
        Config config = ConfigFactory.load();
        // Enable excusion:
        config = this.modifyValueAt(config, "warc.index.exclusions.enabled", true);
        // config exclusion file:
        String inputFile = this.getClass().getClassLoader()
                .getResource("exclusion_test.txt").getPath();
        File exclusions_file = new File(inputFile);
        assertEquals(true, exclusions_file.exists());
        config = this.modifyValueAt(config, "warc.index.exclusions.file",
                exclusions_file.getAbsolutePath());
        // config check interval:
        config = this.modifyValueAt(config, "warc.index.exclusions.check_interval", 600);

        // And run the trial:
        this.testFilterBehaviourWithConfig(config, 32);
    }

    /* ------------------------------------------------------------ */
    
    /*
     * Internal implementations of filter test core methods.
     */
    
    /* ------------------------------------------------------------ */

    private void testFilterBehaviour(String path, Object newValue, int expectedNullCount) throws MalformedURLException, IOException, NoSuchAlgorithmException {
        // Override the config:
        Config config = ConfigFactory.load();
        Config config2 = this.modifyValueAt(config, path, newValue);
        // And invoke:
        this.testFilterBehaviourWithConfig(config2, expectedNullCount);
    }

    private Config modifyValueAt(Config config, String path, Object newValue) {
        ConfigValue value = ConfigValueFactory.fromAnyRef(newValue);
        return config.withValue(path, value);
    }

    private void testFilterBehaviourWithConfig(Config config2, int expectedNullCount) throws MalformedURLException, IOException, NoSuchAlgorithmException {
        // Instanciate the indexer:
        WARCIndexer windex = new WARCIndexer(config2);
        windex.setCheckSolrForDuplicates(false);

        String inputFile = this.getClass().getClassLoader()
                .getResource("IAH-urls-wget.warc.gz")
                .getPath();
        System.out.println("ArchiveUtils.isGZipped: " + ArchiveUtils.isGzipped(new FileInputStream(inputFile)));
        ArchiveReader reader = ArchiveReaderFactory.get(inputFile);
        Iterator<ArchiveRecord> ir = reader.iterator();
        int recordCount = 0;
        int nullCount = 0;

        // Iterate though each record in the WARC file
        while(ir.hasNext()) {
            ArchiveRecord rec = ir.next();
            SolrRecord doc = windex.extract("", rec);
            recordCount++;
            if(doc == null) {
                nullCount++;
            }
            else {
                // System.out.println("DOC: " + doc.toXml());
            }
        }

        System.out.println("recordCount: " + recordCount);
        assertEquals(36, recordCount);

        System.out.println("nullCount: " + nullCount);
        assertEquals(expectedNullCount, nullCount);
    }

    // TODO: Construct a test-WARC with tweets that can be shared under the Apache 2.0 license
    @Test
    public void testTweetSupport() throws NoSuchAlgorithmException, IOException {
        final String TWEET_WARC = "/home/te/projects/so-me/twitter/t.warc";
        iterateTest(TWEET_WARC);
    }

    // TODO: Construct a test-WARC with Jodels that can be shared under the Apache 2.0 license
    @Test
    public void testJodelSupportFulldump() throws NoSuchAlgorithmException, IOException {
        final String TWEET_WARC = "/home/te/projects/so-me/jodel_Aarhus_20180313_152921-0.warc";
        iterateTest(TWEET_WARC);
    }

    private void iterateTest(String warc) throws NoSuchAlgorithmException, IOException {
        Iterator<ArchiveRecord> ir = getRecordIterator(warc);
        if (ir == null) {
            log.info("Skipping iterateTest as sample WARC '" + warc + "' is not available");
            return;
        }
        WARCIndexer windex = new WARCIndexer(ConfigFactory.load());
        int count = 0;
        int solrRecords = 0;
        Set<String> types = new HashSet<>();
        while (ir.hasNext()) {
            ArchiveRecord rec = ir.next();
            SolrRecord solrRecord = windex.extract("", rec);
            if (solrRecord == null) {
                if (count > 0) { // First record is probably the WARC-header, so don't warn on that
                    log.warn("No Solr document produced from record #" + count + " in '" + warc + "'");
                }
            } else {
//                System.out.println(solrRecord.containsKey(SolrFields.CONTENT_TYPE_TIKA) ? solrRecord.getField(SolrFields.CONTENT_TYPE_TIKA) : "N/A");
                if (solrRecord.containsKey(SolrFields.SOLR_TYPE)) {
                    types.add(solrRecord.getFieldValue(SolrFields.SOLR_TYPE).toString());
                }
                log.info("content: " + solrRecord.getFieldValue(SolrFields.SOLR_EXTRACTED_TEXT));
                solrRecords++;
            }
            count++;
        }
        log.info("iterateTest: Extracted " + count + " records and got " + solrRecords + " Solr records from '" +
                 warc + "' with types [" + types + "]");
    }

    private Iterator<ArchiveRecord> getRecordIterator(String warc) throws NoSuchAlgorithmException, IOException {
        URL warcURL = TestUtil.resolve(warc);
        if (warcURL == null) {
            return null;
        }

        ArchiveReader reader = ArchiveReaderFactory.get(warcURL.getPath());
        Iterator<ArchiveRecord> ir = reader.iterator();
        assertTrue("There should be records in the WARC '" + warc + "'", ir.hasNext());
        return ir;
    }

    /**
     * Tests support for overall compression (WARC with GZipped elements) as well as
     * content-compression (GZip and Brotli).
     * TODO: Test support for GZipped WARCs without the .gz-extension
     */
    @Test
    public void testCompressionSupport() throws IOException, NoSuchAlgorithmException {
        final String EXPECTED_CONTENT = "Extremely simple webpage";
        final String ENCODING = "Content-Encoding";
        List<String> WARCs = Arrays.asList(
                "transfer_compression_none.warc",
                "transfer_compression_none.warc.gz",
                "transfer_compression_gzip.warc",
                "transfer_compression_gzip.warc.gz",
                "transfer_compression_brotli.warc",
                "transfer_compression_brotli.warc.gz"
        );
        WARCIndexer windex = new WARCIndexer(ConfigFactory.load());
        windex.setCheckSolrForDuplicates(false);

        for (String warc: WARCs) {
            String warcFile = this.getClass().getClassLoader()
                    .getResource("compression/" + warc).getPath();
            ArchiveReader reader = ArchiveReaderFactory.get(warcFile);
            Iterator<ArchiveRecord> ir = reader.iterator();
            assertTrue("There should be at least 1 record in " + warc, ir.hasNext());
            while(ir.hasNext()) {
                ArchiveRecord rec = ir.next();
                if(!"response".equals(rec.getHeader().getHeaderValue("WARC-Type"))) {
                    continue;
                }
                assertTrue("The record in '" + warc + "'should be a WARC-record", rec instanceof WARCRecord);
                log.info("WARC:" + warc + " url:" + rec.getHeader().getUrl());

                SolrRecord doc = windex.extract("", rec);
                assertTrue("The field '" + SolrFields.SOLR_EXTRACTED_TEXT + "' should be present in the" +
                           " record in " + warc +". Missing content indicates missing compression support",
                           doc.containsKey(SolrFields.SOLR_EXTRACTED_TEXT)) ;
                String content = doc.getField(SolrFields.SOLR_EXTRACTED_TEXT).toString();
                if (!content.contains(EXPECTED_CONTENT)) {
                    Assert.fail("The response in " + warc + "" +
                                " did not contain the expected phrase \"" + EXPECTED_CONTENT +
                                "\". This indicates that it was compressed in an unsupported way.\n\n" + content);
                }
            }
        }
    }

    @Test
    public void testTruncatedTime() throws NoSuchAlgorithmException, IOException {
        final String WARC = "truncated_datetime.warc";
        final String RECORD_ID = "201908150102/+BV/tmv/tASHANg2c3/2MA==";
        WARCIndexer windex = new WARCIndexer(ConfigFactory.load());

        String inputFile = this.getClass().getClassLoader().getResource(WARC).getPath();
        ArchiveReader reader = ArchiveReaderFactory.get(inputFile);

        // Iterate though each record in the WARC file
        for (ArchiveRecord rec : reader) {
            SolrRecord doc = windex.extract("", rec);
            if (doc != null && doc.getField(SolrFields.ID).getValue().equals(RECORD_ID)) {
                String crawl = doc.getField(SolrFields.CRAWL_DATE).getValue().toString().replaceAll("[^0-9]", "");
                String wayback = doc.getField(SolrFields.WAYBACK_DATE).getValue().toString();
                assertEquals("crawlDate and waybackDate should designate the same instance",
                             crawl, wayback);
                break;
            }
        }
    }

    @Test
    public void testFields() throws NoSuchAlgorithmException, IOException {
        // ID of WARC record to use in test
        final String recordId = "20131021215312/jbKtN3dWzLJzaIQxTyPCiA==";
//        final String recordId = "jbKtN3dWzLJzaIQxTyPCiA==/20131021215312";
        boolean foundRecord = false;

        WARCIndexer windex = new WARCIndexer(ConfigFactory.load());
        windex.setCheckSolrForDuplicates(false);

        String inputFile = this.getClass().getClassLoader()
                .getResource("IAH-urls-wget.warc.gz").getPath();
        System.out.println("ArchiveUtils.isGZipped: " + ArchiveUtils.isGzipped(new FileInputStream(inputFile)));
        ArchiveReader reader = ArchiveReaderFactory.get(inputFile);
        Iterator<ArchiveRecord> ir = reader.iterator();

        // Iterate though each record in the WARC file
        while(ir.hasNext()) {
            ArchiveRecord rec = ir.next();
            SolrRecord doc = windex.extract("", rec);

            if(doc != null && doc.getField(SolrFields.ID).getValue().equals(recordId)) {
                foundRecord = true;

                String[] found_lhs = doc
                        .getField(SolrFields.SOLR_LINKS_HOSTS_SURTS).getValues()
                        .toArray(new String[] {});
                Arrays.sort(found_lhs);
                System.out.println(Arrays.toString(found_lhs));
                assertArrayEquals("links_hosts_surts is incorrect",
                        new String[] {
                                "(org,",
                                "(org,archive,",
                                "(org,archive,blog,",
                                "(org,archive,web,",
                                "(org,archive,web,faq,",
                                "(org,openlibrary,",
                                "(org,sloan,"
                        }, found_lhs);

                String[] found_hs = doc.getField(SolrFields.SOLR_HOST_SURT)
                        .getValues().toArray(new String[] {});
                Arrays.sort(found_hs);
                System.out.println(Arrays.toString(found_hs));
                assertArrayEquals("host_surt is incorrect",
                        new String[] {
                                "(org,",
                                "(org,archive,"
                        }, found_hs);

                assertEquals("url_path is incorrect", "/", doc.getField(SolrFields.SOLR_URL_PATH).getValue());

                assertEquals("status_code is incorrect", "200", doc.getField(SolrFields.SOLR_STATUS_CODE).getValue());

                break;
            }
        }

        assertTrue("Test record not found", foundRecord);
    }

    @Test
    public void testIPHost() throws MalformedURLException, IOException,
            NoSuchAlgorithmException {
        // Instanciate the indexer:
        Config config = ConfigFactory.load();
        List<String> r_i = new ArrayList<String>();
        r_i.add("2");
        r_i.add("4");
        config = this.modifyValueAt(config,
                "warc.index.extract.response_include", r_i);
        WARCIndexer windex = new WARCIndexer(config);
        windex.setCheckSolrForDuplicates(false);

        String inputFile = this.getClass().getClassLoader()
                .getResource("ip-host-testcase.warc.gz").getPath();
        System.out.println("ArchiveUtils.isGZipped: "
                + ArchiveUtils.isGzipped(new FileInputStream(inputFile)));
        ArchiveReader reader = ArchiveReaderFactory.get(inputFile);
        Iterator<ArchiveRecord> ir = reader.iterator();
        int recordCount = 0;
        int nullCount = 0;

        // Iterate though each record in the WARC file
        while (ir.hasNext()) {
            ArchiveRecord rec = ir.next();
            SolrRecord doc = windex.extract("", rec);
            recordCount++;
            if (doc == null) {
                nullCount++;
            } else {
                // System.out.println("DOC: " + doc.toXml());
            }
        }

        System.out.println("recordCount: " + recordCount);
        assertEquals(6, recordCount);

        System.out.println("nullCount: " + nullCount);
        assertEquals(5, nullCount);
    }

    @Test
    public void testParseURL() throws NoSuchAlgorithmException, URIException {
        WARCIndexer windex = new WARCIndexer();
        String[] testUrls = {
                "http://www.bbc.co.uk/news/business/market_data/chart?chart_primary_ticker=DJSE:INDU&chart_time_period=1_day&canvas_colour=0",
                "http://www.argos.co.uk/webapp/wcs/stores/servlet/Browse?storeId=10151&langId=110&catalogId=10001&mRR=true&c_1=1|category_ro",
                "http://www.bbc.co.uk/news/business/market_data/chart?chart_primary_ticker=MISCAM:GOLDAM&chart_time_period=1_month&canvas_co",
                "http://www.bbc.co.uk/news/business/market_data/chart?chart_primary_ticker=FX^GBP:ILS&chart_time_period=1_month&canvas_colour" };
        for (String urlString : testUrls) {
            SolrRecord solr =  SolrRecordFactory.createFactory(null).createRecord();
            URI test1 = windex.parseURL(solr, urlString);
            assertEquals(1,
                    solr.getField(SolrFields.PUBLIC_SUFFIX).getValueCount());
        }
    }
}
