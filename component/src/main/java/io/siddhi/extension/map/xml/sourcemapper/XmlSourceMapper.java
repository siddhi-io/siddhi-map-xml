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
import org.apache.log4j.Logger;
import org.jaxen.JaxenException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
                                "Used to specify the enclosing element in case of sending multiple events in same "
                                        + "XML message. WSO2 DAS will treat the child element of given enclosing "
                                        + "element as events and execute xpath expressions on child elements. If "
                                        + "enclosing.element is not provided multiple event scenario is disregarded "
                                        + "and xpaths will be evaluated with respect to root element.",
                        type = {DataType.STRING},
                        optional = true,
                        defaultValue = "Root element"),
                @Parameter(name = "enclosing.element.as.event",
                        description =
                                "This can either have value true or false. By default it will be false. "
                                        + "This attribute specifies whether the given enclosing element, it self is "
                                        + "the event. The user will be able to handle events which doesn't have "
                                        + "multiple attributes or when the enclosing event doesnt contain any "
                                        + "children.",
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
                        defaultValue = "True")
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
                        syntax = "@source(type='inMemory', topic='stock', @map(type='xml', namespaces = "
                                + "\"dt=urn:schemas-microsoft-com:datatypes\", enclosing.element=\"//portfolio\", "
                                + "@attributes(symbol = \"company/symbol\", price = \"price\", volume = \"volume\")))"
                                + "\ndefine stream FooStream (symbol string, price float, volume long);",
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
                                + "</portfolio>")
        }
)
public class XmlSourceMapper extends SourceMapper {

    private static final Logger log = Logger.getLogger(XmlSourceMapper.class);
    private static final String PARENT_SELECTOR_XPATH = "enclosing.element";
    private static final String NAMESPACES = "namespaces";
    private static final String EVENTS_PARENT_ELEMENT = "events";
    private static final String EVENT_ELEMENT = "event";
    private static final String FAIL_ON_UNKNOWN_ATTRIBUTE = "fail.on.missing.attribute";
    private static final String ENCLOSING_ELEMENT_AS_EVENT = "enclosing.element.as.event";
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
            result = convertToEvents(eventObject, failedEvents);
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
                    if (!iterator.hasNext()) {
                        if (enclosingElementAsEvent) {
                            try {
                                Event event = buildEvent(((OMElement) enclosingNode));
                                eventList.add(event);
                            } catch (MappingFailedException e) {
                                failedEvents.add(new ErroneousEvent((OMElement) enclosingNode, e.getMessage()));
                            }
                        }
                    } else {
                        while (iterator.hasNext()) {
                            OMElement eventOMElement = (OMElement) iterator.next();
                            try {
                                Event event = buildEvent(eventOMElement);
                                eventList.add(event);
                            } catch (MappingFailedException e) {
                                failedEvents.add(new ErroneousEvent(eventOMElement, e.getMessage()));
                            }
                        }
                    }
                }
            } else {    //input XML string has only one event in it.
                try {
                    Event event = buildEvent(rootOMElement);
                    eventList.add(event);
                } catch (MappingFailedException e) {
                    failedEvents.add(new ErroneousEvent(rootOMElement, e.getMessage()));
                }
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
}
