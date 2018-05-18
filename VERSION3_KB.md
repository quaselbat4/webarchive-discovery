# Upgrade notes and experiences going from version 2.0 to 3.0-alpha at the Royal Danish Library

## Overview

At the Royal Danish Library, the schema from [webarchive-discovery pull #148](https://github.com/netarchivesuite/webarchive-discovery/blob/acc57a599236cc2a56faf291c37d5b5f405e97a9/warc-indexer/src/main/solr/solr7/discovery/conf/schema.xml) (soon to be part of the main branch) was used for a full re-index of 24 billion web resources from the Danish Net Archive in 2018. The old index used the [webarchive-discovery 2.0 Solr schema](https://github.com/ukwa/webarchive-discovery/blob/2.0.x-dev-branch/warc-indexer/src/main/solr/solr/discovery/conf/schema.xml). This document captures technical differences between 2.0 and 3.0-alpha as well as observations from the upgrade.

## Search setup at the Royal Danish Library

The Royal Danish Library uses a setup with static and fully optimized sub-collections of ~900GB / 280M documents: when a sub-collection reaches this size, it is fully optimized. A new sub-collection is then created and the old sub-collection is never updated again. Solr's [alias mechanism](https://lucene.apache.org/solr/guide/7_3/collections-api.html) is used to provide unified search across the sub-collections, making them appear (nearly) as a single collection.

On the server-level, 4 machines are used, each machine has 380GB of RAM and 16 CPU cores (x2 with Hyperthreading). Storage is 25 individually mounted Samsung 930GB SSDs on each machine, 1 SSD/sub-collection. Each sub-collection is handled by a separate Solr node with 8GB heap.

## Technical differences

General changes to the processing done in webarchive-discovery is not covered here. See the [webarchive-discovery changelog](https://github.com/ukwa/webarchive-discovery/blob/master/CHANGES.md) for that. New features are reflected in new fields in the Solr index, covered below.

### stored/docValues

A general change to the Solr schema has been a switch away from `stored` fields, replacing them with `docValues`. `docValues` allows for low-overhead faceting, sorting, grouping and exporting. The price is increased retrieval time when returning documents.

_Observation:_ In the old 2.0 setup with mostly `stored` fields, the amount of fields in the returned documents has little impact on response time. Consequently the default setting was to return all possible fields. Simple document searches took ½-2 seconds. In the 3.0-aplha setup, returning all fields takes ½-1 second _per document_, increasing response time to 10 seconds for simple searches. Limiting to 5 fields relevant to the Royal Danish Library's test-GUI brought response times down in the old ½-2 second range.

_Recommendation:_ *Only request the fields that are to be used.*

If the limiting of fields is unacceptable, the schema can be updated to enable `stored` to all `docValues`-fields. This will increase index size markedly (qualified guess: 10-30%) and require a full re-index.

### New notable fields in 3.0-alpha

* `exif\_location with geo-coordinates from images
* `host\_surt` with the host name elements in reversed order using the [SURT](http://webarchivingbucket.com/techblog/?p=48) standard
* `index_time` the index time for the document
* `links_hosts_surts` outgoing links to hosts in SURT form
* `links_images` links to images shown in HTML pages
* `links_norm` outgoing links from HTML pages
* `redirect_to_norm` HTTP 3xx redirect support
* `status_code` the HTTP status code
* `type` human readable type akin to `content\_type_norm`
* `url_norm` normalised and un-ambiguated version of the URL
* `url_path` the path part of the url, sans-host
* `url_search` human-query searchable variant of the URL
* `warc_key_id` the ID specified in the WARC entry

Please see the JavaDoc for the [webarchive-discovery pull #148 schema](https://github.com/netarchivesuite/webarchive-discovery/blob/acc57a599236cc2a56faf291c37d5b5f405e97a9/warc-indexer/src/main/solr/solr7/discovery/conf/schema.xml) for further details and examples of use for the different fields.
