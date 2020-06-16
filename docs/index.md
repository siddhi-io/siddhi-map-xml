Siddhi Map XML
===================

  [![Jenkins Build Status](https://wso2.org/jenkins/job/siddhi/job/siddhi-map-xml/badge/icon)](https://wso2.org/jenkins/job/siddhi/job/siddhi-map-xml/)
  [![GitHub Release](https://img.shields.io/github/release/siddhi-io/siddhi-map-xml.svg)](https://github.com/siddhi-io/siddhi-map-xml/releases)
  [![GitHub Release Date](https://img.shields.io/github/release-date/siddhi-io/siddhi-map-xml.svg)](https://github.com/siddhi-io/siddhi-map-xml/releases)
  [![GitHub Open Issues](https://img.shields.io/github/issues-raw/siddhi-io/siddhi-map-xml.svg)](https://github.com/siddhi-io/siddhi-map-xml/issues)
  [![GitHub Last Commit](https://img.shields.io/github/last-commit/siddhi-io/siddhi-map-xml.svg)](https://github.com/siddhi-io/siddhi-map-xml/commits/master)
  [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

The **siddhi-map-xml extension** is an extension to <a target="_blank" href="https://wso2.github.io/siddhi">Siddhi</a> that converts XML messages to/from Siddhi events.

For information on <a target="_blank" href="https://siddhi.io/">Siddhi</a> and it's features refer <a target="_blank" href="https://siddhi.io/redirect/docs.html">Siddhi Documentation</a>. 

## Download

* Versions 5.x and above with group id `io.siddhi.extension.*` from <a target="_blank" href="https://mvnrepository.com/artifact/io.siddhi.extension.map.xml/siddhi-map-xml/">here</a>.
* Versions 4.x and lower with group id `org.wso2.extension.siddhi.*` from <a target="_blank" href="https://mvnrepository.com/artifact/org.wso2.extension.siddhi.map.xml/siddhi-map-xml">here</a>.

## Latest API Docs 

Latest API Docs is <a target="_blank" href="https://siddhi-io.github.io/siddhi-map-xml/api/5.0.4">5.0.4</a>.

## Features

* <a target="_blank" href="https://siddhi-io.github.io/siddhi-map-xml/api/5.0.4/#xml-sink-mapper">xml</a> *(<a target="_blank" href="http://siddhi.io/en/v5.1/docs/query-guide/#sink-mapper">Sink Mapper</a>)*<br> <div style="padding-left: 1em;"><p><p style="word-wrap: break-word;margin: 0;">This mapper converts Siddhi output events to XML before they are published via transports that publish in XML format. Users can either send a pre-defined XML format or a custom XML message containing event data.</p></p></div>
* <a target="_blank" href="https://siddhi-io.github.io/siddhi-map-xml/api/5.0.4/#xml-source-mapper">xml</a> *(<a target="_blank" href="http://siddhi.io/en/v5.1/docs/query-guide/#source-mapper">Source Mapper</a>)*<br> <div style="padding-left: 1em;"><p><p style="word-wrap: break-word;margin: 0;">This mapper converts XML input to Siddhi event. Transports which accepts XML messages can utilize this extension to convert the incoming XML message to Siddhi event. Users can either send a pre-defined XML format where event conversion will happen without any configs or can use xpath to map from a custom XML message.</p></p></div>

## Dependencies 

There are no other dependencies needed for this extension. 

## Installation

For installing this extension on various siddhi execution environments refer Siddhi documentation section on <a target="_blank" href="https://siddhi.io/redirect/add-extensions.html">adding extensions</a>.

## Support and Contribution

* We encourage users to ask questions and get support via <a target="_blank" href="https://stackoverflow.com/questions/tagged/siddhi">StackOverflow</a>, make sure to add the `siddhi` tag to the issue for better response.

* If you find any issues related to the extension please report them on <a target="_blank" href="https://github.com/siddhi-io/siddhi-execution-string/issues">the issue tracker</a>.

* For production support and other contribution related information refer <a target="_blank" href="https://siddhi.io/community/">Siddhi Community</a> documentation.

