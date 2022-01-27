/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.siddhi.extension.map.xml.sourcemapper;

import io.siddhi.annotation.Example;
import io.siddhi.annotation.Extension;
import io.siddhi.annotation.Parameter;
import io.siddhi.annotation.util.DataType;
import io.siddhi.core.config.SiddhiAppContext;
import io.siddhi.core.event.Event;
import io.siddhi.core.exception.MappingFailedException;
import io.siddhi.core.exception.SiddhiAppRuntimeException;
import io.siddhi.core.stream.input.source.AttributeMapping;
import io.siddhi.core.stream.input.source.InputEventHandler;
import io.siddhi.core.stream.input.source.SourceMapper;
import io.siddhi.core.util.AttributeConverter;
import io.siddhi.core.util.config.ConfigReader;
import io.siddhi.core.util.error.handler.model.ErroneousEvent;
import io.siddhi.core.util.transport.OptionHolder;
import io.siddhi.query.api.definition.Attribute;
import io.siddhi.query.api.definition.StreamDefinition;
import io.siddhi.query.api.exception.SiddhiAppValidationException;
import org.apache.axiom.om.DeferredParsingException;
import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.axiom.om.xpath.AXIOMXPath;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jaxen.JaxenException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

/**
 * This mapper converts XML string input to {@link io.siddhi.core.event.ComplexEventChunk}. This extension
 * accepts optional xpath expressions to select specific attributes from the stream.
 */
@Extension(
        name = "xml",
        namespace = "sourceMapper",
        description = "This mapper converts XML input to Siddhi event. Transports which accepts XML messages "
                + "can utilize this extension to convert the incoming XML message to Siddhi event. Users can either "
                + "send a pre-defined XML format where event conversion will happen without any configs or can use "
                + "xpath to map from a custom XML message.",
        parameters = {
                @Parameter(name = "namespaces",
                        description =
                                "Used to provide namespaces used in the incoming XML message beforehand to "
                                        + "configure xpath expressions. User can provide a comma separated list. "
                                        + "If these are not provided xpath evaluations will fail",
                        type = {DataType.STRING},
                        optional = true,
                        defaultValue = "None"),
                @Parameter(name = "enclosing.element",
                        description =
                                "Used when custom mapping is enabled to specify the XPath in case of sending " +
                                        "multiple events (if XPath consist of multiple elements, or consists of " +
                                        "child elements.) using the same XML content or when the event is not in " +
                                        "root node.\n" +
                                        "XML mapper will treat the child element/s of given enclosing " +
                                        "element as event/s, when `enclosing.element.as.event` is set to `false`, " +
                                        "and execute xpath expressions available in the attribute mappings on " +
                                        "child elements.\n " +
                                        "If enclosing.element is not provided XPaths in the attribute mappings " +
                                        "will be evaluated with respect to root element.\n" +
                                        "When enclosing element is used and custom mapping is enabled, " +
                                        "the attribute mapping XPaths should be relative to child elements",
                        type = {DataType.STRING},
                        optional = true,
                        defaultValue = "Root element"),
                @Parameter(name = "enclosing.element.as.event",
                        description =
                                "This can either have value true or false. " +
                                        "XML mapper will treat the child element/s of given enclosing " +
                                        "element as event/s, when `enclosing.element.as.event` is set to `false`, " +
                                        "and attribute mapping should be defined with with respect to the Xpath " +
                                        "element's child elements.\n " +
                                        "When `enclosing.element.as.event` is set to `true`, the elements " +
                                        "(NOT child elements) retrieved from XPath itself will be treated as the " +
                                        "event/s and attribute mapping should be defined with with respect to " +
                                        "the Xpath element.\n" +
                                        "When `enable.streaming.xml` is set to `true` the " +
                                        "`enclosing.element.as.event` value will be set to `true` by default",
                        type = {DataType.BOOL},
                        optional = true,
                        defaultValue = "False"),
                @Parameter(name = "fail.on.missing.attribute",
                        description = "This can either have value true or false. By default it will be true. This "
                                + "attribute allows user to handle unknown attributes. By default if an xpath "
                                + "execution fails or returns null DAS will drop that message. However setting "
                                + "this property to false will prompt DAS to send and event with null value to "
                                + "Siddhi where user can handle it accordingly(ie. Assign a default value)",
                        type = {DataType.BOOL},
                        optional = true,
                        defaultValue = "True"),
                @Parameter(name = "enable.streaming.xml.content",
                        description = "This will be used when the XML content is streamed without sending the XML " +
                                "element/ event as a whole. eg: When streaming XML file line by line.",
                        type = {DataType.BOOL},
                        optional = true,
                        defaultValue = "false"),
                @Parameter(name = "extract.leaf.node.data",
                        description = "This parameter will enable the event to contain the child element values.\n" +
                                "This can be used when the given XPath node contains several child elements and also " +
                                "when the child elements have child elements as well.\n" +
                                "If there are multiple child elements, the event count for one node will get " +
                                "multiplied by the number of child nodes.",
                        type = {DataType.BOOL},
                        optional = true,
                        defaultValue = "false")
        },
        examples = {
                @Example(
                        syntax = "@source(type='inMemory', topic='stock', @map(type='xml'))\n"
                                + "define stream FooStream (symbol string, price float, volume long);\n",
                        description = "Above configuration will do a default XML input mapping. Expected "
                                + "input will look like below."
                                + "<events>\n"
                                + "    <event>\n"
                                + "        <symbol>WSO2</symbol>\n"
                                + "        <price>55.6</price>\n"
                                + "        <volume>100</volume>\n"
                                + "    </event>\n"
                                + "</events>\n"),
                @Example(
                        syntax = "@map(type='xml', enclosing.element=\"/events/wrapper/event\", \n" +
                                "     enclosing.element.as.event=\"true\", \n" +
                                "     @attributes(volume = \"volume\", price = \"price\", symbol = \"symbol\"))\n" +
                                "define stream FooStream (symbol string, price float, volume long);\n",
                        description = "Above configuration will do a custom mapping and and get the <event> element " +
                                "as a whole event in the given XPath. The attribute mapping has to be done with " +
                                "respect to the element mapped in the XPath. Expected input will look like below" +
                                "<events><wrapper>\n" +
                                "    <event>\n" +
                                "        <symbol>WSO2</symbol>\n" +
                                "        <price>55.6</price>\n" +
                                "        <volume>100</volume>\n" +
                                "    </event>\n" +
                                "    <event>\n" +
                                "        <symbol>WSO2</symbol>\n" +
                                "        <price>55.6</price>\n" +
                                "        <volume>100</volume>\n" +
                                "    </event>\n" +
                                "</wrapper></events>\n"),
                @Example(
                        syntax = "@map(type='xml', enclosing.element='/events/event',\n" +
                                "      @attributes(symbol='symbol', price='price', volume='volume'))\n" +
                                "define stream FooStream (symbol string, price float, volume long);\n",
                        description = "Above configuration will do a custom mapping and and get the <event> element " +
                                "as a whole event in the given XPath. The attribute mapping has to be done with " +
                                "respect to the element mapped in the XPath. Expected input will look like below."
                                + "<events>\n"
                                + "    <event>\n"
                                + "        <symbol>WSO2</symbol>\n"
                                + "        <price>55.6</price>\n"
                                + "        <volume>100</volume>\n"
                                + "    </event>\n"
                                + "</events>\n"),
                @Example(
                        syntax = "@source(type='inMemory', topic='stock', \n" +
                                "  @map(type='xml', namespaces = \"dt=urn:schemas-microsoft-com:datatypes\", \n" +
                                "       enclosing.element=\"//portfolio\", \n" +
                                "       @attributes(symbol = \"company/symbol\", price = \"price\", \n" +
                                "                   volume = \"volume\")))\n" +
                                "define stream FooStream (symbol string, price float, volume long);",
                        description = "Above configuration will perform a custom XML mapping. In the custom "
                                + "mapping user can add xpath expressions representing each event attribute using "
                                + "@attribute annotation. Expected "
                                + "input will look like below.\n"
                                + "<portfolio xmlns:dt=\"urn:schemas-microsoft-com:datatypes\">\n"
                                + "    <stock exchange=\"nasdaq\">\n"
                                + "        <volume>100</volume>\n"
                                + "        <company>\n"
                                + "           <symbol>WSO2</symbol>\n"
                                + "        </company>\n"
                                + "        <price dt:type=\"number\">55.6</price>\n"
                                + "    </stock>\n"
                                + "</portfolio>"),
                @Example(
                        syntax = "@map(type='xml', \n" +
                                "      enclosing.element=\"/root/child\",\n" +
                                "      enable.streaming.xml.content=\"true\",\n" +
                                "      enclosing.element.as.event=\"true\",\n" +
                                "      extract.leaf.node.data=\"true\",\n" +
                                "      fail.on.missing.attribute=\"false\",\n" +
                                "      @attributes(id = \"/child/@id\", timestamp = \"/child/@timestamp\", \n" +
                                "                  key = \"/child/detail/@key\", \n" +
                                "                  value = \"/child/detail/@value\"))\n" +
                                "define stream FooStream (id string, timestamp string, key string, value string);\n",
                        description = "Above configuration will do a custom mapping and and get the <child> element " +
                                "as a whole event in the given XPath when the XML content received in a steaming " +
                                "manner (eg: when a file is read line by line and sent to the XML mapper to map).\n" +
                                "The attribute mapping has to be done with respect to the element mapped in the " +
                                "XPath which is <child>\n" +
                                "In here, the leaf node data is mapped to the <child> event as well. \n" +
                                "Expected input will look like below.\n" +
                                "<root>\n" +
                                "   <bounds minlat=\"53.4281\" minlon=\"-2.4142\" maxlat=\"54.0097\" " +
                                "maxlon=\"-0.9762\"/>\n" +
                                "   <child id=\"413229\" timestamp=\"2014-09-10T14:12:48Z\"/>\n" +
                                "   <child id=\"414427\" timestamp=\"2018-01-24T23:16:10Z\"/>\n" +
                                "   <child id=\"673959\" timestamp=\"2019-10-20T12:07:13Z\">\n" +
                                "       <extra id=\"1234\" timestamp=\"2014-09-11T10:36:37Z\"/>\n" +
                                "       <detail key=\"company\" value=\"wso2\"/>\n" +
                                "       <extra id=\"0987\" timestamp=\"2014-09-11T10:36:37Z\"/>\n" +
                                "       <detail key=\"country\" value=\"Sri Lanka\"/>\n" +
                                "   </child>\n" +
                                "   .\n" +
                                "   .\n" +
                                "</root>\n"),
                @Example(
                        syntax = "@map(type='xml', \n" +
                                "      enclosing.element=\"/root/child\",\n" +
                                "      enable.streaming.xml.content=\"true\",\n" +
                                "      enclosing.element.as.event=\"true\",\n" +
                                "      fail.on.missing.attribute=\"false\",\n" +
                                "      @attributes(id = \"/child/@id\", timestamp = \"/child/@timestamp\"))\n" +
                                "define stream FooStream (id string, timestamp string, key string, value string);\n",
                        description = "Above configuration will do a custom mapping and and get the <child> element " +
                                "as a whole event in the given XPath when the XML content received in a steaming " +
                                "manner (eg: when a file is read line by line and sent to the XML mapper to map).\n" +
                                "The attribute mapping has to be done with respect to the element mapped in the " +
                                "XPath which is <child>\n" +
                                "Expected input will look like below.\n" +
                                "<root>\n" +
                                "   <bounds minlat=\"53.4281\" minlon=\"-2.4142\" maxlat=\"54.0097\" " +
                                "maxlon=\"-0.9762\"/>\n" +
                                "   <child id=\"413229\" timestamp=\"2014-09-10T14:12:48Z\"/>\n" +
                                "   <child id=\"414427\" timestamp=\"2018-01-24T23:16:10Z\"/>\n" +
                                "   <child id=\"673959\" timestamp=\"2019-10-20T12:07:13Z\">\n" +
                                "       <extra id=\"1234\" timestamp=\"2014-09-11T10:36:37Z\"/>\n" +
                                "       <detail key=\"company\" value=\"wso2\"/>\n" +
                                "       <extra id=\"0987\" timestamp=\"2014-09-11T10:36:37Z\"/>\n" +
                                "       <detail key=\"country\" value=\"Sri Lanka\"/>\n" +
                                "   </child>\n" +
                                "   .\n" +
                                "   .\n" +
                                "</root>\n")
        }
)
public class XmlSourceMapper extends SourceMapper {

    private static final Logger log = LogManager.getLogger(XmlSourceMapper.class);
    private static final String PARENT_SELECTOR_XPATH = "enclosing.element";
    private static final String NAMESPACES = "namespaces";
    private static final String EVENTS_PARENT_ELEMENT = "events";
    private static final String EVENT_ELEMENT = "event";
    private static final String FAIL_ON_UNKNOWN_ATTRIBUTE = "fail.on.missing.attribute";
    private static final String ENCLOSING_ELEMENT_AS_EVENT = "enclosing.element.as.event";
    private static final String ENABLE_STREAMING_XML_CONTENT = "enable.streaming.xml.content";
    private static final String EXTRACT_LEAF_NODE_DATA = "extract.leaf.node.data";
    //Indicates whether custom mapping is enabled or not.
    private boolean isCustomMappingEnabled = false;
    private StreamDefinition streamDefinition;
    private AXIOMXPath enclosingElementSelectorPath = null;
    private Map<String, String> namespaceMap;
    private Map<String, AXIOMXPath> xPathMap = new HashMap<>();
    private boolean failOnUnknownAttribute;
    private AttributeConverter attributeConverter = new AttributeConverter();
    private List<Attribute> attributeList;
    private Map<String, Attribute.Type> attributeTypeMap = new HashMap<>();
    private Map<String, Integer> attributePositionMap = new HashMap<>();
    private List<AttributeMapping> attributeMappingList;
    private boolean enclosingElementAsEvent = false;
    private String startNodeChunkingElement;
    private String endNodeChunkingElement;
    private StringBuilder xmlBuilder;
    private String eventPayload = "";
    private boolean loopUntilEndNodeChunkingElement;
    private boolean startNodeFound;
    private boolean enableStreamingXMLContent;
    private List<String> xPathArray;
    private boolean extractLeafNodeData;
    private boolean xPathDetected;
    private int xPathArrayIndex;
    private Pattern nodeStartPattern;
    private boolean differentElementMatcherStarted;
    private int numberOfDifferentNodes;
    private boolean differentNodesDetected;

    /**
     * Initialize the mapper and the mapping configurations.
     *
     * @param streamDefinition     the  StreamDefinition
     * @param optionHolder         mapping options
     * @param attributeMappingList list of attributes mapping
     * @param configReader
     */
    @Override
    public void init(StreamDefinition streamDefinition, OptionHolder optionHolder,
                     List<AttributeMapping> attributeMappingList, ConfigReader configReader,
                     SiddhiAppContext siddhiAppContext) {
        String enclosingElementSelectorXPath;
        this.streamDefinition = streamDefinition;
        this.attributeMappingList = attributeMappingList;
        attributeList = streamDefinition.getAttributeList();
        attributeTypeMap = new HashMap<>(attributeList.size());
        attributePositionMap = new HashMap<>(attributeList.size());
        this.xmlBuilder = new StringBuilder();
        namespaceMap = new HashMap<>();
        for (Attribute attribute : attributeList) {
            attributeTypeMap.put(attribute.getName(), attribute.getType());
            attributePositionMap.put(attribute.getName(), streamDefinition.getAttributePosition(attribute.getName()));
        }
        failOnUnknownAttribute = Boolean.parseBoolean(optionHolder.validateAndGetStaticValue(FAIL_ON_UNKNOWN_ATTRIBUTE,
                "true"));

        if (attributeMappingList != null && attributeMappingList.size() > 0) {
            isCustomMappingEnabled = true;
            if (streamDefinition.getAttributeList().size() < attributeMappingList.size()) {
                throw new SiddhiAppValidationException("Stream: '" + streamDefinition.getId() + "' has "
                        + streamDefinition.getAttributeList().size() + " attributes, but " + attributeMappingList.size()
                        + " attribute mappings found. Each attribute should have one and only one mapping.");
            }

            String namespaces = optionHolder.validateAndGetStaticValue(NAMESPACES, null);
            if (namespaces != null) {
                buildNamespaceMap(namespaces);
            }

            for (AttributeMapping attributeMapping : attributeMappingList) {
                if (attributeTypeMap.containsKey(attributeMapping.getName())) {
                    AXIOMXPath axiomxPath;
                    try {
                        axiomxPath = new AXIOMXPath(attributeMapping.getMapping());
                    } catch (JaxenException e) {
                        throw new SiddhiAppValidationException("Error occurred when building XPath from: " +
                                attributeMapping.getMapping() + ", mapped to attribute: " +
                                attributeMapping.getName());
                    }
                    for (Map.Entry<String, String> entry : namespaceMap.entrySet()) {
                        try {
                            axiomxPath.addNamespace(entry.getKey(), entry.getValue());
                        } catch (JaxenException e) {
                            throw new SiddhiAppValidationException(
                                    "Error occurred when adding namespace: " + entry.getKey()
                                            + ":" + entry.getValue() + " to XPath element: "
                                            + attributeMapping.getMapping());
                        }
                    }
                    xPathMap.put(attributeMapping.getName(), axiomxPath);
                } else {
                    throw new SiddhiAppValidationException("No attribute with name " + attributeMapping.getName()
                            + " available in stream. Hence halting Execution plan deployment");
                }
            }
            enclosingElementSelectorXPath = optionHolder.validateAndGetStaticValue(PARENT_SELECTOR_XPATH, null);
            if (enclosingElementSelectorXPath != null) {
                try {
                    enclosingElementSelectorPath = new AXIOMXPath(enclosingElementSelectorXPath);
                    for (Map.Entry<String, String> entry : namespaceMap.entrySet()) {
                        try {
                            enclosingElementSelectorPath.addNamespace(entry.getKey(), entry.getValue());
                        } catch (JaxenException e) {
                            throw new SiddhiAppValidationException(
                                    "Error occurred when adding namespace: " + entry.getKey() + ":" + entry.getValue
                                            () + " to XPath element:" + enclosingElementSelectorXPath);
                        }
                    }
                } catch (JaxenException e) {
                    throw new SiddhiAppRuntimeException("Could not get XPath from expression: " +
                            enclosingElementSelectorXPath, e);
                }
            }
            enclosingElementAsEvent = Boolean.parseBoolean(
                    optionHolder.validateAndGetStaticValue(ENCLOSING_ELEMENT_AS_EVENT, "false"));
            extractLeafNodeData = Boolean.parseBoolean(
                    optionHolder.validateAndGetStaticValue(EXTRACT_LEAF_NODE_DATA, "false"));
            enableStreamingXMLContent = Boolean.parseBoolean(
                    optionHolder.validateAndGetStaticValue(ENABLE_STREAMING_XML_CONTENT, "false"));

            if (enableStreamingXMLContent) {
                loopUntilEndNodeChunkingElement = false;
                xPathDetected = false;
                startNodeFound = false;
                xPathArrayIndex = 0;
                nodeStartPattern = Pattern.compile("<[^ ]*");
                differentElementMatcherStarted = false;
                numberOfDifferentNodes = 0;
                differentNodesDetected = false;
                if (enclosingElementSelectorXPath != null) {
                    if (enclosingElementSelectorXPath.indexOf("/") == 0) {
                        enclosingElementSelectorXPath = enclosingElementSelectorXPath.substring(1);
                    }
                    xPathArray = Arrays.asList(enclosingElementSelectorXPath.split("/"));
                    startNodeChunkingElement = xPathArray.get(xPathArray.size() - 1);
                    endNodeChunkingElement = "</" + startNodeChunkingElement + ">";
                    startNodeChunkingElement = "<" + startNodeChunkingElement;
                } else {
                    throw new SiddhiAppValidationException("'enclosing.element' should be provided when " +
                            "'enable.nested.element.mapping' is set to true in Siddhi app: " +
                            siddhiAppContext.getName());
                }
            }
        }
    }

    @Override
    public Class[] getSupportedInputEventClasses() {
        return new Class[]{String.class, OMElement.class, byte[].class};
    }

    /**
     * Receives an event as an XML string from {@link io.siddhi.core.stream.input.source.Source}, converts it to
     * a {@link io.siddhi.core.event.ComplexEventChunk} and send to the
     * {@link io.siddhi.core.query.output.callback.OutputCallback}.
     *
     * @param eventObject       the input event, given as an XML string
     * @param inputEventHandler input handler
     */
    @Override
    protected void mapAndProcess(Object eventObject, InputEventHandler inputEventHandler)
            throws MappingFailedException, InterruptedException {
        List<ErroneousEvent> failedEvents = new ArrayList<>(0);
        Event[] result;
        try {
            if (!enableStreamingXMLContent) {
                result = convertToEvents(eventObject, failedEvents);
            } else {
                result = convertXMLStreamToEvents(eventObject, failedEvents);
            }
            if (result.length > 0) {
                inputEventHandler.sendEvents(result);
            }
        } catch (Throwable t) { //stringToOM does not throw the exception immediately due to streaming. Hence need this.
            log.error("Exception occurred when converting XML message to Siddhi Event", t);
            failedEvents.add(new ErroneousEvent(eventObject, t,
                    "Exception occurred when converting XML message to Siddhi Event"));
        }
        if (!failedEvents.isEmpty()) {
            throw new MappingFailedException(failedEvents);
        }
    }

    @Override
    protected boolean allowNullInTransportProperties() {
        return !failOnUnknownAttribute;
    }

    /**
     * Converts an event from an XML string to {@link Event}
     *
     * @param eventObject The input event, given as an XML string
     * @return the constructed {@link Event} object
     */
    private Event[] convertToEvents(Object eventObject, List<ErroneousEvent> failedEvents) {
        List<Event> eventList = new ArrayList<>();
        OMElement rootOMElement;
        if (eventObject instanceof String) {
            try {
                rootOMElement = AXIOMUtil.stringToOM((String) eventObject);
            } catch (XMLStreamException | DeferredParsingException e) {
                log.warn("Error parsing incoming XML event : " + eventObject + ". Reason: " + e.getMessage()
                        + ". Hence dropping message chunk");
                failedEvents.add(new ErroneousEvent(eventObject, e,
                        "Error parsing incoming XML event : " + eventObject + ". Reason: " + e.getMessage()
                                + ". Hence dropping message chunk"));
                return new Event[0];
            }
        } else if (eventObject instanceof OMElement) {
            rootOMElement = (OMElement) eventObject;
        } else if (eventObject instanceof byte[]) {
            String events = null;
            try {
                events = new String((byte[]) eventObject, StandardCharsets.UTF_8);
                rootOMElement = AXIOMUtil.stringToOM(events);
            } catch (XMLStreamException | DeferredParsingException e) {
                log.warn("Error parsing incoming XML event : " + events + ". Reason: " + e.getMessage() +
                        ". Hence dropping message chunk");
                failedEvents.add(new ErroneousEvent(eventObject, e,
                        "Error parsing incoming XML event : " + events + ". Reason: " + e.getMessage() +
                                ". Hence dropping message chunk"));
                return new Event[0];
            }
        } else {
            log.warn("Event object is invalid. Expected String/OMElement or Byte Array, but found "
                    + eventObject.getClass().getCanonicalName());
            failedEvents.add(new ErroneousEvent(eventObject,
                    "Event object is invalid. Expected String/OMElement or Byte Array, but found "
                            + eventObject.getClass().getCanonicalName()));
            return new Event[0];
        }

        if (isCustomMappingEnabled) {   //custom mapping case
            if (enclosingElementSelectorPath != null) {  //has multiple events
                List enclosingNodeList;
                try {
                    enclosingNodeList = enclosingElementSelectorPath.selectNodes(rootOMElement);
                    if (enclosingNodeList.size() == 0) {
                        log.warn("Provided enclosing element did not match any xml node. " +
                                "Hence dropping the event :" + rootOMElement.toString());
                        failedEvents.add(new ErroneousEvent(eventObject,
                                "Provided enclosing element did not match any xml node. " +
                                        "Hence dropping the event :" + rootOMElement.toString()));
                        return new Event[0];
                    }
                } catch (JaxenException e) {
                    failedEvents.add(new ErroneousEvent(eventObject, e,
                            "Error occurred when selecting nodes from XPath: " +
                                    enclosingElementSelectorPath.toString()));
                    throw new SiddhiAppRuntimeException("Error occurred when selecting nodes from XPath: "
                            + enclosingElementSelectorPath.toString(), e);
                }
                for (Object enclosingNode : enclosingNodeList) {
                    Iterator iterator = ((OMElement) enclosingNode).getChildElements();
                    if (enclosingElementAsEvent) {
                        buildEventWithOMElementAsEvent(eventList, failedEvents, (OMElement) enclosingNode);
                    } else {
                        while (iterator.hasNext()) {
                            buildEventWithOMElementAsEvent(eventList, failedEvents, (OMElement) iterator.next());
                        }
                    }
                }
            } else {    //input XML string has only one event in it.
                buildEventWithOMElementAsEvent(eventList, failedEvents, rootOMElement);
            }
        } else {    //default mapping case
            if (EVENTS_PARENT_ELEMENT.equals(rootOMElement.getLocalName())) {
                Iterator iterator = rootOMElement.getChildrenWithName(QName.valueOf(EVENT_ELEMENT));
                while (iterator.hasNext()) {
                    boolean isMalformedEvent = false;
                    OMElement eventOMElement = (OMElement) iterator.next();
                    Event event = new Event(attributeList.size());
                    Object[] data = event.getData();
                    Iterator eventIterator = eventOMElement.getChildElements();
                    while (eventIterator.hasNext()) {
                        OMElement attrOMElement = (OMElement) eventIterator.next();
                        String attributeName = attrOMElement.getLocalName();
                        Attribute.Type type;
                        if ((type = attributeTypeMap.get(attributeName)) != null) {
                            try {
                                data[attributePositionMap.get(attributeName)] = attributeConverter.getPropertyValue(
                                        attrOMElement.getText(), type);
                            } catch (SiddhiAppRuntimeException | NumberFormatException e) {
                                log.warn("Error occurred when extracting attribute value. Cause: " + e.getMessage() +
                                        ". Hence dropping the event: " + eventOMElement.toString());
                                failedEvents.add(new ErroneousEvent(eventObject, e,
                                        "Error occurred when extracting attribute value. Cause: " +
                                                e.getMessage() + ". Hence dropping the event: " +
                                                eventOMElement.toString()));
                                isMalformedEvent = true;
                                break;
                            }
                        } else {
                            log.warn("Attribute : " + attributeName + " was not found in given stream definition. " +
                                    "Hence ignoring this attribute");
                        }
                    }
                    for (int i = 0; i < data.length; i++) {
                        if (data[i] == null && failOnUnknownAttribute) {
                            log.warn("No attribute with name: " + streamDefinition.getAttributeNameArray()[i] +
                                    " found in input event: " + eventOMElement.toString() + ". Hence dropping the" +
                                    " event.");
                            failedEvents.add(new ErroneousEvent(eventObject, "No attribute with name: " +
                                    streamDefinition.getAttributeNameArray()[i] + " found in input event: " +
                                    eventOMElement.toString() + ". Hence dropping the event."));
                            isMalformedEvent = true;
                        }
                    }
                    if (!isMalformedEvent) {
                        eventList.add(event);
                    }
                }
            } else {
                log.warn("Incoming XML message should adhere to pre-defined format" +
                        " when using default mapping. Root element name should be " + EVENTS_PARENT_ELEMENT + ". But " +
                        "found " + rootOMElement.getLocalName() + ". Hence dropping XML message : " +
                        rootOMElement.toString());
                failedEvents.add(new ErroneousEvent(eventObject, "Incoming XML message should adhere to " +
                        "pre-defined format when using default mapping. Root element name should be " +
                        EVENTS_PARENT_ELEMENT + ". But found " + rootOMElement.getLocalName() +
                        ". Hence dropping XML message : " + rootOMElement.toString()));
            }
        }
        return eventList.toArray(new Event[0]);
    }

    /**
     * This method will append the streaming XML content and notifies when the given XPath is matched.
     *
     * @param eventObject  partial XML event
     * @param failedEvents failed event list to add events when mapping fail exception happens
     */
    private Event[] convertXMLStreamToEvents(Object eventObject, List<ErroneousEvent> failedEvents) {
        List<Event> eventList = new ArrayList<>();
        String currentEventPayload = "";
        if (eventObject instanceof String) {
            currentEventPayload = (String) eventObject;
        } else if (eventObject instanceof byte[]) {
            currentEventPayload = new String((byte[]) eventObject, StandardCharsets.UTF_8);
        } else {
            log.warn("Event object is invalid. Expected String Byte Array when using 'chunking.element', but found "
                    + eventObject.getClass().getCanonicalName());
            failedEvents.add(new ErroneousEvent(eventObject,
                    "Event object is invalid. Expected String/OMElement or Byte Array, but found "
                            + eventObject.getClass().getCanonicalName()));
            return new Event[0];
        }
        eventPayload += currentEventPayload.trim().replaceAll("\n", "");

        //if only one element in xPath, no need to detect xPaths
        while (xPathArray.size() != 0 && (!xPathDetected && !differentElementMatcherStarted) &&
                !eventPayload.isEmpty()) {
            int index;
            String findingNode = "<" + xPathArray.get(xPathArrayIndex);
            index = eventPayload.indexOf(findingNode);
            if (index == -1) {
                eventPayload = "";
            } else {
                while (index >= 0 && numberOfDifferentNodes == 0 && !eventPayload.isEmpty()) {
                    index = 0;
                    xPathArrayIndex++;
                    if (xPathArrayIndex == xPathArray.size()) {
                        xPathDetected = true;
                        startNodeChunkingElement = "<" + xPathArray.get(xPathArrayIndex - 1);
                        eventPayload = eventPayload.trim();
                        break;
                    }
                    eventPayload = eventPayload.substring(eventPayload.indexOf(findingNode) + findingNode.length());
                    int xPathElementIndex, nextElementIndex;
                    findingNode = "<" + xPathArray.get(xPathArrayIndex);
                    String endingNode = "</" + xPathArray.get(xPathArrayIndex - 1);
                    if (eventPayload.contains(findingNode)) {
                        xPathElementIndex = eventPayload.indexOf(findingNode);
                        nextElementIndex = eventPayload.indexOf("<");
                        if ((xPathElementIndex != -1 && nextElementIndex != -1) &&
                                xPathElementIndex <= nextElementIndex) {
                            // checks whether the next xPath element is right next to the previous xPath element
                            index = eventPayload.indexOf(findingNode);
                            // remove all other previous data
                            eventPayload = eventPayload.substring(eventPayload.indexOf(findingNode));
                        } else {
                            // There are other elements before the next xPath starting node
                            differentNodesDetected = true;
                        }
                    } else if (eventPayload.contains(endingNode)) {
                        eventPayload = "";
                        xPathArrayIndex--;
                    } else {
                        eventPayload = "";
                    }
                }
                while (differentNodesDetected && !differentElementMatcherStarted) {
                    Matcher matcher = nodeStartPattern.matcher(eventPayload);
                    if (matcher.find()) {
                        differentElementMatcherStarted = true;
                        String node = matcher.group();
                        startNodeChunkingElement = node;
                        eventPayload = eventPayload.substring(eventPayload.indexOf(node) + node.length());
                    }
                }
            }
            if (xPathDetected) {
                buildAndSetOMElementToList(eventList, failedEvents, false);
            } else if (differentElementMatcherStarted) {
                buildAndSetOMElementToList(null, null, true);
            }
        }
        if (xPathDetected) {
            buildAndSetOMElementToList(eventList, failedEvents, false);
        } else if (differentElementMatcherStarted) {
            buildAndSetOMElementToList(null, null, true);
        }
        return eventList.toArray(new Event[0]);
    }

    /**
     * This method will check whether it is need to extract data from leaf nodes or not
     *
     * @param eventList     list which the successful events that have to be added
     * @param failedEvents  failed event list to add events when mapping fail exception happens
     * @param enclosingNode validated XPath element
     */
    private void buildEventWithOMElementAsEvent(List<Event> eventList, List<ErroneousEvent> failedEvents,
                                                OMElement enclosingNode) {
        if (enableStreamingXMLContent) {
            xPathDetected = false;
            xPathArrayIndex--;
            startNodeFound = false;
            differentElementMatcherStarted = false;
            differentNodesDetected = false;
        }
        try {
            if (!extractLeafNodeData) {
                Event event = buildEvent(enclosingNode);
                eventList.add(event);
            } else {
                buildEventIncludingLeafNodeValues(enclosingNode, eventList);
            }
        } catch (MappingFailedException e) {
            failedEvents.add(new ErroneousEvent(enclosingNode, e.getMessage()));
        }
    }

    private void buildNamespaceMap(String namespace) {
        String[] namespaces = namespace.split(",");
        for (String ns : namespaces) {
            String[] splits = ns.split("=");
            if (splits.length != 2) {
                log.warn("Malformed namespace mapping found: " + ns + ". Each namespace has to have format: "
                        + "<prefix>=<uri>");
            }
            namespaceMap.put(splits[0].trim(), splits[1].trim());
        }
    }

    /**
     * This method will iterate through the parent node list of a leaf node and construct a flat event using all
     * the parent node values
     *
     * @param omElement validated XPath element
     * @param eventList list which the successful events that have to be added
     */
    private void buildEventIncludingLeafNodeValues(OMElement omElement, List<Event> eventList)
            throws MappingFailedException {
        if (omElement != null) {
            List<ValueHolder> leafNodeList = new ArrayList<>();
            iterateAndFindLeafNode("", omElement, null, leafNodeList);
            for (int i = 0; i < leafNodeList.size(); i++) {
                ValueHolder node = leafNodeList.get(i);
                Map<String, String> convertedFlatEvent = new HashMap<>();
                do {
                    Map<String, String> attributeMap = node.getAttributeMap();
                    for (Map.Entry<String, String> attributeEntry : attributeMap.entrySet()) {
                        convertedFlatEvent.put(attributeEntry.getKey(), attributeEntry.getValue());
                    }
                    Map<String, String> textValueMap = node.getTextValueMap();
                    for (Map.Entry<String, String> textValueEntry : textValueMap.entrySet()) {
                        convertedFlatEvent.put(textValueEntry.getKey(), textValueEntry.getValue());
                    }
                    node = node.getPrevValueHolder();
                } while (node != null);
                Event siddhiEvent = new Event(streamDefinition.getAttributeList().size());
                Object[] data = siddhiEvent.getData();
                for (AttributeMapping attributeMapping : attributeMappingList) {
                    String attributeName = attributeMapping.getName();
                    Attribute.Type attributeType = attributeTypeMap.get(attributeName);
                    String attributeValue = convertedFlatEvent.get(xPathMap.get(attributeName).toString());
                    if (attributeValue != null || !failOnUnknownAttribute) {
                        if (attributeType.equals(Attribute.Type.STRING)) {
                            data[attributeMapping.getPosition()] = attributeValue;
                        } else {
                            try {
                                data[attributeMapping.getPosition()] = attributeConverter.
                                        getPropertyValue(attributeValue, attributeType);
                            } catch (SiddhiAppRuntimeException | NumberFormatException e) {
                                if (failOnUnknownAttribute) {
                                    String errMsg = "Error occurred when extracting attribute value. Cause: " +
                                            e.getMessage() + ". Hence dropping the event: " + omElement.toString();
                                    log.warn(errMsg);
                                    throw new MappingFailedException(errMsg);
                                }
                            }
                        }
                    } else {
                        String errMsg = "Error occurred when selecting attribute: " + attributeName +
                                " in the input event, using the given XPath: " +
                                xPathMap.get(attributeName).toString();
                        log.warn(errMsg);
                        throw new MappingFailedException(errMsg);
                    }
                }
                eventList.add(siddhiEvent);
            }
        }
    }

    private Event buildEvent(OMElement eventOMElement) throws MappingFailedException {
        Event event = new Event(streamDefinition.getAttributeList().size());
        Object[] data = event.getData();
        for (AttributeMapping attributeMapping : attributeMappingList) {
            String attributeName = attributeMapping.getName();
            Attribute.Type attributeType = attributeTypeMap.get(attributeName);
            AXIOMXPath axiomXPath = xPathMap.get(attributeName);
            boolean getRootNode = false;
            if (axiomXPath != null) { //can be null in transport properties scenario
                try {
                    List selectedNodes = axiomXPath.selectNodes(eventOMElement);
                    if (selectedNodes.size() == 0) {
                        if (enclosingElementAsEvent &&
                                eventOMElement.getLocalName().equalsIgnoreCase(axiomXPath.toString()) &&
                                eventOMElement.getFirstElement() == null) {
                            getRootNode = true;
                        } else {
                            if (failOnUnknownAttribute) {
                                String errMsg = "Xpath: '" + axiomXPath.toString() +
                                        " did not yield any results. Hence dropping the event : " +
                                        eventOMElement.toString();
                                log.warn(errMsg);
                                throw new MappingFailedException(errMsg);
                            } else {
                                continue;
                            }
                        }
                    }
                    //We will by default consider the first node. We are not logging this to get rid of an if condition.
                    Object elementObj;
                    if (getRootNode) {
                        elementObj = eventOMElement;
                    } else {
                        elementObj = selectedNodes.get(0);
                    }
                    if (elementObj instanceof OMElement) {
                        OMElement element = (OMElement) elementObj;
                        if (element.getFirstElement() != null) {
                            if (attributeType.equals(Attribute.Type.STRING)) {
                                data[attributeMapping.getPosition()] = element.toString();
                            } else {
                                String errMsg = "XPath: " + axiomXPath.toString() + " did not return a leaf element " +
                                        "and stream definition is not expecting a String attribute. Hence "
                                        + "dropping the event: " + eventOMElement.toString();
                                log.warn(errMsg);
                                throw new MappingFailedException(errMsg);
                            }
                        } else {
                            String attributeValue = element.getText();
                            try {
                                data[attributeMapping.getPosition()] = attributeConverter.
                                        getPropertyValue(attributeValue, attributeType);
                            } catch (SiddhiAppRuntimeException | NumberFormatException e) {
                                if (failOnUnknownAttribute) {
                                    String errMsg = "Error occurred when extracting attribute value. Cause: " +
                                            e.getMessage() + ". Hence dropping the event: " + eventOMElement.toString();
                                    log.warn(errMsg);
                                    throw new MappingFailedException(errMsg);
                                }
                            }
                        }
                    } else if (elementObj instanceof OMAttribute) {
                        OMAttribute omAttribute = (OMAttribute) elementObj;
                        try {
                            data[attributeMapping.getPosition()] = attributeConverter.
                                    getPropertyValue(omAttribute.getAttributeValue(), attributeType);
                        } catch (SiddhiAppRuntimeException | NumberFormatException e) {
                            String errMsg = "Error occurred when extracting attribute value. Cause: " + e.getMessage() +
                                    ". Hence dropping the event: " + eventOMElement.toString();
                            log.warn(errMsg);
                            throw new MappingFailedException(errMsg);
                        }
                    }
                } catch (JaxenException e) {
                    String errMsg = "Error occurred when selecting attribute: " + attributeName
                            + " in the input event, using the given XPath: " + xPathMap.get(attributeName).toString();
                    log.warn(errMsg);
                    throw new MappingFailedException(errMsg);
                }
            }
        }
        return event;
    }

    /**
     * This method will validate and build the OMElement from the given string after the initial XPath starting
     * node is found
     *
     * @param eventList    list which the successful events that have to be added
     * @param failedEvents failed event list to add events when mapping fail exception happens
     */
    private void buildAndSetOMElementToList(List<Event> eventList, List<ErroneousEvent> failedEvents,
                                            boolean iterateAndIgnore) {
        // while loop is added since the full xml payload can be received at once
        if (startNodeFound || eventPayload.indexOf(startNodeChunkingElement) == 0) {
            if (!startNodeFound && xmlBuilder.length() == 0) {
                // checks whether the starting element is available and do a substring to remove it after added to
                // the xml builder
                xmlBuilder.append(startNodeChunkingElement);
                eventPayload = eventPayload.substring(eventPayload.indexOf(startNodeChunkingElement) +
                        startNodeChunkingElement.length());
                startNodeFound = true;
            }

            if (!loopUntilEndNodeChunkingElement && eventPayload.contains("/>") && (eventPayload.contains("<") &&
                    (eventPayload.indexOf("/>") < eventPayload.indexOf("<")))) {
                // checking if the open element is closed without any child elements
                // also validates when there are opening elements in the payload and checking whether it is after
                // the closed element case: where the xml content will be in a single string
                xmlBuilder.append(eventPayload, 0, eventPayload.indexOf("/>") + 2);
                eventPayload = eventPayload.substring(eventPayload.indexOf("/>") + 2);
                try {
                    if (!iterateAndIgnore) {
                        buildEventWithOMElementAsEvent(eventList, failedEvents,
                                AXIOMUtil.stringToOM(xmlBuilder.toString()));
                    }
                } catch (XMLStreamException e) {
                    log.warn("Error parsing incoming XML event : " + xmlBuilder.toString() + ". Reason: " +
                            e.getMessage() + ". Hence dropping message chunk");
                    failedEvents.add(new ErroneousEvent(xmlBuilder.toString(), e,
                            "Error parsing incoming XML event : " + xmlBuilder.toString() + ". Reason: " +
                                    e.getMessage() + ". Hence dropping message chunk"));
                }
                xmlBuilder = new StringBuilder();
            } else if (!loopUntilEndNodeChunkingElement && eventPayload.contains("/>") && !eventPayload.contains("<")) {
                // checking if the open element is closed without any child elements
                // also validates when there are no opening elements
                xmlBuilder.append(eventPayload, 0, eventPayload.indexOf("/>") + 2);
                eventPayload = eventPayload.substring(eventPayload.indexOf("/>") + 2);
                try {
                    if (!iterateAndIgnore) {
                        buildEventWithOMElementAsEvent(eventList, failedEvents,
                                AXIOMUtil.stringToOM(xmlBuilder.toString()));
                    }
                } catch (XMLStreamException e) {
                    log.warn("Error parsing incoming XML event : " + xmlBuilder.toString() + ". Reason: " +
                            e.getMessage() + ". Hence dropping message chunk");
                    failedEvents.add(new ErroneousEvent(xmlBuilder.toString(), e,
                            "Error parsing incoming XML event : " + xmlBuilder.toString() + ". Reason: " +
                                    e.getMessage() + ". Hence dropping message chunk"));
                }
                xmlBuilder = new StringBuilder();
            } else if (!loopUntilEndNodeChunkingElement && !eventPayload.contains("/>") && eventPayload.contains(">")
                    && eventPayload.contains("<") && (eventPayload.indexOf(">") < eventPayload.indexOf("<"))) {
                // when there are child elements
                // validated whether the node element ends partially before the child elements start
                // case: where the xml content will be in a single string
                loopUntilEndNodeChunkingElement = true;
            } else if (!loopUntilEndNodeChunkingElement && !eventPayload.contains("/>") && eventPayload.contains(">")) {
                // when there are child elements
                // validated whether the node element ends partially before the child elements start
                loopUntilEndNodeChunkingElement = true;
            }

            if (loopUntilEndNodeChunkingElement && eventPayload.contains(endNodeChunkingElement)) {
                xmlBuilder.append(eventPayload, 0, eventPayload.indexOf(endNodeChunkingElement) +
                        endNodeChunkingElement.length());
                eventPayload = eventPayload.substring(eventPayload.indexOf(endNodeChunkingElement) +
                        endNodeChunkingElement.length());
                try {
                    if (!iterateAndIgnore) {
                        buildEventWithOMElementAsEvent(eventList, failedEvents,
                                AXIOMUtil.stringToOM(xmlBuilder.toString()));
                    }
                } catch (XMLStreamException e) {
                    log.warn("Error parsing incoming XML event : " + xmlBuilder.toString() + ". Reason: " +
                            e.getMessage() + ". Hence dropping message chunk");
                    failedEvents.add(new ErroneousEvent(xmlBuilder.toString(), e,
                            "Error parsing incoming XML event : " + xmlBuilder.toString() + ". Reason: " +
                                    e.getMessage() + ". Hence dropping message chunk"));
                }
                loopUntilEndNodeChunkingElement = false;
                xmlBuilder = new StringBuilder();
            }
        }
    }

    /**
     * This method will iterate through the child elements recursively until it find the leaf node and track each
     * parent node
     *
     * @param currentPath       the XPath of the parent node
     * @param currentNode       current node of the values thats has to be extracted and to check their are child nodes
     * @param parentValueHolder the value holder object of the parent node
     * @param leafNodeList      list of the leaf nodes
     */
    private void iterateAndFindLeafNode(String currentPath, OMElement currentNode, ValueHolder parentValueHolder,
                                        List<ValueHolder> leafNodeList) {
        ValueHolder valueHolder;
        if (parentValueHolder == null) {
            valueHolder = new ValueHolder();
        } else {
            valueHolder = new ValueHolder(parentValueHolder);
        }
        Iterator attributeIterator = currentNode.getAllAttributes();
        while (attributeIterator.hasNext()) {
            OMAttribute omAttribute = (OMAttribute) attributeIterator.next();
            valueHolder.getAttributeMap().put(currentPath + "/" + currentNode.getLocalName() + "/@" +
                    omAttribute.getLocalName(), omAttribute.getAttributeValue());
        }
        Iterator eventIterator = currentNode.getChildElements();
        String nextPath = currentPath.isEmpty()
                ? "/" + currentNode.getLocalName()
                : currentPath + "/" + currentNode.getLocalName();
        if (eventIterator.hasNext()) {
            boolean valueHolderHasChildren = false;
            while (eventIterator.hasNext()) {
                OMElement omElement = (OMElement) eventIterator.next();
                if (omElement.getFirstElement() == null && !omElement.getText().trim().isEmpty()) {
                    valueHolder.getTextValueMap().put(nextPath + "/" + omElement.getLocalName(),
                            omElement.getText().trim());
                } else {
                    valueHolderHasChildren = true;
                    iterateAndFindLeafNode(nextPath, omElement, valueHolder, leafNodeList);
                }
            }
            if (!valueHolderHasChildren) {
                leafNodeList.add(valueHolder);
            }
        } else {
            leafNodeList.add(valueHolder);
        }
    }

    /**
     * This class holds the each elements attributes and text values and its parent nodes reference
     */
    private static class ValueHolder {
        private ValueHolder prevValueHolder;
        private Map<String, String> attributeMap;
        private Map<String, String> textValueMap;

        public ValueHolder(ValueHolder prevValueHolder) {
            this.prevValueHolder = prevValueHolder;
            this.attributeMap = new HashMap<>();
            this.textValueMap = new HashMap<>();
        }

        public ValueHolder() {
            this.attributeMap = new HashMap<>();
            this.textValueMap = new HashMap<>();
        }

        public Map<String, String> getAttributeMap() {
            return attributeMap;
        }

        public ValueHolder getPrevValueHolder() {
            return prevValueHolder;
        }

        public Map<String, String> getTextValueMap() {
            return textValueMap;
        }
    }
}

