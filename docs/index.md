siddhi-map-xml
======================================

The **siddhi-map-xml extension** is an extension to <a target="_blank" href="https://wso2.github
.io/siddhi">Siddhi</a> that supports mapping incoming XML events to a stream at the source, and mapping a stream to XML events at the sink.

Find some useful links below:

* <a target="_blank" href="https://github.com/wso2-extensions/siddhi-map-xml">Source code</a>
* <a target="_blank" href="https://github.com/wso2-extensions/siddhi-map-xml/releases">Releases</a>
* <a target="_blank" href="https://github.com/wso2-extensions/siddhi-map-xml/issues">Issue tracker</a>

## Latest API Docs 

Latest API Docs is <a target="_blank" href="https://wso2-extensions.github.io/siddhi-map-xml/api/4.1.1">4.1.1</a>.

## How to use 

**Using the extension in <a target="_blank" href="https://github.com/wso2/product-sp">WSO2 Stream Processor</a>**

* You can use this extension in the latest <a target="_blank" href="https://github.com/wso2/product-sp/releases">WSO2 Stream Processor</a> that is a part of <a target="_blank" href="http://wso2.com/analytics?utm_source=gitanalytics&utm_campaign=gitanalytics_Jul17">WSO2 Analytics</a> offering, with editor, debugger and simulation support. 

* This extension is shipped by default with WSO2 Stream Processor, if you wish to use an alternative version of this extension you can replace the component <a target="_blank" href="https://github.com/wso2-extensions/siddhi-map-xml/releases">jar</a> that can be found in the `<STREAM_PROCESSOR_HOME>/lib` directory.

**Using the extension as a <a target="_blank" href="https://wso2.github.io/siddhi/documentation/running-as-a-java-library">java library</a>**

* This extension can be added as a maven dependency along with other Siddhi dependencies to your project.

```
     <dependency>
        <groupId>org.wso2.extension.siddhi.map.xml</groupId>
        <artifactId>siddhi-map-xml</artifactId>
        <version><version>x.x.x</version></version>
     </dependency>
```

## Jenkins Build Status

---

|  Branch | Build Status |
| :------ |:------------ | 
| master  | [![Build Status](https://wso2.org/jenkins/job/siddhi/job/siddhi-map-xml/badge/icon)](https://wso2.org/jenkins/job/siddhi/job/siddhi-map-xml/) |

---

## Features

* <a target="_blank" href="https://wso2-extensions.github.io/siddhi-map-xml/api/4.1.1/#xml-sink-mapper">xml</a> *<a target="_blank" href="https://wso2.github.io/siddhi/documentation/siddhi-4.0/#sink-mapper">(Sink Mapper)</a>*<br><div style="padding-left: 1em;"><p>This mapper converts Siddhi output events to XML before they are published via transports that publish in XML format. Users can either send a pre-defined XML format or a custom XML message containing event data.</p></div>
* <a target="_blank" href="https://wso2-extensions.github.io/siddhi-map-xml/api/4.1.1/#xml-source-mapper">xml</a> *<a target="_blank" href="https://wso2.github.io/siddhi/documentation/siddhi-4.0/#source-mapper">(Source Mapper)</a>*<br><div style="padding-left: 1em;"><p>This mapper converts XML input to Siddhi event. Transports which accepts XML messages can utilize this extension to convert the incoming XML message to Siddhi event. Users can either send a pre-defined XML format where event conversion will happen without any configs or can use xpath to map from a custom XML message.</p></div>

## How to Contribute
 
  * Please report issues at <a target="_blank" href="https://github.com/wso2-extensions/siddhi-map-xml/issues">GitHub Issue Tracker</a>.
  
  * Send your contributions as pull requests to <a target="_blank" href="https://github.com/wso2-extensions/siddhi-map-xml/tree/master">master branch</a>. 
 
## Contact us 

 * Post your questions with the <a target="_blank" href="http://stackoverflow.com/search?q=siddhi">"Siddhi"</a> tag in <a target="_blank" href="http://stackoverflow.com/search?q=siddhi">Stackoverflow</a>. 
 
 * Siddhi developers can be contacted via the mailing lists:
 
    Developers List   : [dev@wso2.org](mailto:dev@wso2.org)
    
    Architecture List : [architecture@wso2.org](mailto:architecture@wso2.org)
 
## Support 

* We are committed to ensuring support for this extension in production. Our unique approach ensures that all support leverages our open development methodology and is provided by the very same engineers who build the technology. 

* For more details and to take advantage of this unique opportunity contact us via <a target="_blank" href="http://wso2.com/support?utm_source=gitanalytics&utm_campaign=gitanalytics_Jul17">http://wso2.com/support/</a>. 
