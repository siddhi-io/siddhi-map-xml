/*
 * Copyright (c)  2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package io.siddhi.extension.map.xml.sinkmapper;

import io.siddhi.annotation.Example;
import io.siddhi.annotation.Extension;
import io.siddhi.annotation.Parameter;
import io.siddhi.annotation.util.DataType;
import io.siddhi.core.config.SiddhiAppContext;
import io.siddhi.core.event.Event;
import io.siddhi.core.exception.SiddhiAppCreationException;
import io.siddhi.core.stream.output.sink.SinkListener;
import io.siddhi.core.stream.output.sink.SinkMapper;
import io.siddhi.core.util.config.ConfigReader;
import io.siddhi.core.util.transport.Option;
import io.siddhi.core.util.transport.OptionHolder;
import io.siddhi.core.util.transport.TemplateBuilder;
import io.siddhi.query.api.definition.StreamDefinition;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Mapper class to convert a Siddhi message to a XML message. User can provide a XML template or else we will be
 * using a predefined XML message format. In case of null elements xsi:nil="true" will be used. In some instances
 * coding best practices have been compensated for performance concerns.
 */
@Extension(
        name = "xml",
        namespace = "sinkMapper",
        description = "This mapper converts Siddhi output events to XML before they are published via transports " +
                "that publish in XML format. Users can either send a pre-defined XML format or a custom XML message "
                + "containing event data.",
        parameters = {
                @Parameter(name = "validate.xml",
                        description = "This parameter specifies whether the XML messages generated should be " +
                                "validated or not. If this parameter is set to true, messages that do not adhere " +
                                "to proper XML standards are dropped. ",
                        type = {DataType.BOOL},
                        optional = true,
                        defaultValue = "false"),
                @Parameter(name = "enclosing.element",
                        description =
                                "When an enclosing element is specified, the child elements (e.g., the immediate " +
                                        "child elements) of that element are considered as events. This is useful " +
                                        "when you need to send multiple events in a single XML message. When an " +
                                        "enclosing element is not specified, one XML message per every event will be "
                                        + "emitted without enclosing.",
                        type = {DataType.STRING},
                        optional = true,
                        defaultValue = "None in custom mapping and &lt;events&gt; in default mapping")
        },
        examples = {
                @Example(
                        syntax = "@sink(type='inMemory', topic='stock', @map(type='xml'))\n"
                                + "define stream FooStream (symbol string, price float, volume long);\n",
                        description = "Above configuration will do a default XML input mapping which will "
                                + "generate below output\n"
                                + "<events>\n"
                                + "    <event>\n"
                                + "        <symbol>WSO2</symbol>\n"
                                + "        <price>55.6</price>\n"
                                + "        <volume>100</volume>\n"
                                + "    </event>\n"
                                + "</events>\n"),
                @Example(
                        syntax = "@sink(type='inMemory', topic='{{symbol}}', @map(type='xml', enclosing"
                                + ".element='<portfolio>', validate.xml='true', @payload( "
                                + "\"<StockData><Symbol>{{symbol}}</Symbol><Price>{{price}}</Price></StockData>\")))\n"
                                + "define stream BarStream (symbol string, price float, volume long);",
                        description = "Above configuration will perform a custom XML mapping. Inside @payload you can"
                                + " specify the custom template that you want to send the messages out and addd "
                                + "placeholders to places where you need to add event attributes."
                                + "Above config will produce below output XML message\n"
                                + "<portfolio>\n"
                                + "    <StockData>\n"
                                + "        <Symbol>WSO2</Symbol>\n"
                                + "        <Price>55.6</Price>\n"
                                + "    </StockData>\n"
                                + "</portfolio>")
        }
)
public class XMLSinkMapper extends SinkMapper {
    private static final Logger log = LogManager.getLogger(XMLSinkMapper.class);

    private static final String EVENTS_PARENT_OPENING_TAG = "<events>";
    private static final String EVENTS_PARENT_CLOSING_TAG = "</events>";
    private static final String EVENT_PARENT_OPENING_TAG = "<event>";
    private static final String EVENT_PARENT_CLOSING_TAG = "</event>";
    private static final String OPTION_ENCLOSING_ELEMENT = "enclosing.element";
    private static final String OPTION_VALIDATE_XML = "validate.xml";
    private static final String NS_XSI_NIL_ENABLE = " xsi:nil=\"true\"/";

    private StreamDefinition streamDefinition;
    private String enclosingElement = null;
    private boolean xmlValidationEnabled = false;
    private DocumentBuilder builder;
    private String endingElement = "";

    @Override
    public String[] getSupportedDynamicOptions() {
        return new String[0];
    }

    @Override
    public Class[] getOutputEventClasses() {
        return new Class[]{String.class};
    }

    /**
     * Initialize the mapper and the mapping configurations.
     *
     * @param streamDefinition          The stream definition
     * @param optionHolder              Option holder containing static and dynamic options
     * @param payloadTemplateBuilderMap Unmapped list of payload for reference
     * @param mapperConfigReader        Reader to get mapper related configurations provided by user
     * @param siddhiAppContext          Context object of Siddhi application
     */
    @Override
    public void init(StreamDefinition streamDefinition, OptionHolder optionHolder,
                     Map<String, TemplateBuilder> payloadTemplateBuilderMap, ConfigReader mapperConfigReader,
                     SiddhiAppContext siddhiAppContext) {
        this.streamDefinition = streamDefinition;
        enclosingElement = optionHolder.getOrCreateOption(OPTION_ENCLOSING_ELEMENT, null).getValue();
        if (enclosingElement != null) {
            endingElement = getClosingElement(enclosingElement);
        }
        Option validateXmlOption = optionHolder.getOrCreateOption(OPTION_VALIDATE_XML, null);
        if (validateXmlOption != null) {
            xmlValidationEnabled = Boolean.parseBoolean(validateXmlOption.getValue());
            if (xmlValidationEnabled) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setValidating(false);
                //We only need to check the parsability of xml we are generating. Hence setting validating to false.
                factory.setNamespaceAware(true);
                try {
                    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                    factory.setXIncludeAware(false);
                    factory.setExpandEntityReferences(false);
                    builder = factory.newDocumentBuilder();
                } catch (ParserConfigurationException e) {
                    throw new SiddhiAppCreationException("Error occurred when initializing XML validator", e);
                }
            }
        }

        //if @payload() is added there must be at least 1 element in it, otherwise a SiddhiParserException raised
        if (payloadTemplateBuilderMap != null && payloadTemplateBuilderMap.size() != 1) {
            throw new SiddhiAppCreationException("Xml sink-mapper does not support multiple @payload mappings, " +
                    "error at the mapper of '" + streamDefinition.getId() + "'");
        }
        if (payloadTemplateBuilderMap != null &&
                payloadTemplateBuilderMap.get(payloadTemplateBuilderMap.keySet().iterator().next()).isObjectMessage()) {
            throw new SiddhiAppCreationException("Xml sink-mapper does not support object @payload mappings, " +
                    "error at the mapper of '" + streamDefinition.getId() + "'");
        }
    }

    @Override
    public void mapAndSend(Event event, OptionHolder optionHolder,
                           Map<String, TemplateBuilder> payloadTemplateBuilderMap, SinkListener sinkListener) {
        StringBuilder sb = new StringBuilder();
        if (payloadTemplateBuilderMap != null) {   //custom mapping
            if (enclosingElement != null) {
                sb.append(enclosingElement);
            }
            sb.append(payloadTemplateBuilderMap.get(payloadTemplateBuilderMap.keySet().iterator().next()).build(event));
            sb.append(endingElement);
            if (xmlValidationEnabled) {
                try {
                    builder.parse(new ByteArrayInputStream(sb.toString().getBytes(Charset.forName("UTF-8"))));
                } catch (SAXException e) {
                    log.error("Parse error occurred when validating output XML event. " +
                            "Reason: " + e.getMessage() + "Dropping event: " + sb.toString());
                    return;
                } catch (IOException e) {
                    log.error("IO error occurred when validating output XML event. " +
                            "Reason: " + e.getMessage() + "Dropping event: " + sb.toString());
                    return;
                } finally {
                    builder.reset();
                }
            }
        } else {
            sb.append(constructDefaultMapping(event));
            if (enclosingElement != null) {
                sb.insert(0, enclosingElement);
                sb.append(endingElement);
            } else {
                sb.insert(0, EVENTS_PARENT_OPENING_TAG);
                sb.append(EVENTS_PARENT_CLOSING_TAG);
            }
        }
        sinkListener.publish(sb.toString());
    }

    /**
     * Map and publish the given {@link Event} array
     *
     * @param events                    Event object array
     * @param optionHolder              option holder containing static and dynamic options
     * @param payloadTemplateBuilderMap Unmapped payload for reference
     * @param sinkListener              output transport callback
     */
    @Override
    public void mapAndSend(Event[] events, OptionHolder optionHolder,
                           Map<String, TemplateBuilder> payloadTemplateBuilderMap, SinkListener sinkListener) {
        if (events.length < 1) {        //todo valid case?
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (payloadTemplateBuilderMap != null) {   //custom mapping
            if (enclosingElement != null) {
                sb.append(enclosingElement);
            }
            for (Event event : events) {
                sb.append(payloadTemplateBuilderMap.get(payloadTemplateBuilderMap.keySet().iterator().next()).
                        build(event));
            }
            sb.append(endingElement);
            if (xmlValidationEnabled) {
                try {
                    builder.parse(new ByteArrayInputStream(sb.toString().getBytes(Charset.forName("UTF-8"))));
                } catch (SAXException | IOException e) {
                    log.error("Error occurred when validating output XML event. " +
                            "Reason: " + e.getMessage() + "Dropping event: " + sb.toString());
                    return;
                } finally {
                    builder.reset();
                }
            }
        } else {
            for (Event event : events) {
                sb.append(constructDefaultMapping(event));
            }
            if (enclosingElement != null) {
                sb.insert(0, enclosingElement);
                sb.append(endingElement);
            } else {
                sb.insert(0, EVENTS_PARENT_OPENING_TAG);
                sb.append(EVENTS_PARENT_CLOSING_TAG);
            }
        }
        sinkListener.publish(sb.toString());
    }

    /**
     * Method to construct the ending element using enclosing element.
     * Have to strip off any namespace values and add XML ending tags.
     *
     * @param enclosingElement User provided enclosing element
     * @return Calculated closing element
     */
    private String getClosingElement(String enclosingElement) {

        String[] results = enclosingElement.split(" ");
        if (results.length == 1) {
            StringBuilder builder = new StringBuilder(enclosingElement);
            builder.insert(1, "/");
            return builder.toString();
        } else {
            StringBuilder builder = new StringBuilder(results[0]);
            builder.append(">");
            builder.insert(1, "/");
            return builder.toString();
        }
    }

    /**
     * Convert the given {@link Event} to XML string
     *
     * @param event Event object
     * @return the constructed XML string
     */
    private String constructDefaultMapping(Event event) {
        StringBuilder builder = new StringBuilder(EVENT_PARENT_OPENING_TAG);
        Object[] data = event.getData();
        for (int i = 0; i < data.length; i++) {
            String attributeName = streamDefinition.getAttributeNameArray()[i];
            builder.append("<").append(attributeName).append(">");
            if (data[i] != null) {
                builder.append(data[i].toString()).append("</").append(attributeName).append(">");
            } else {
                builder.insert(builder.toString().length() - 1, NS_XSI_NIL_ENABLE);
            }
        }
        builder.append(EVENT_PARENT_CLOSING_TAG);
        // TODO: remove if we are not going to support arbitraryDataMap
        // Get arbitrary data from event
/*        Map<String, Object> arbitraryDataMap = event.getArbitraryDataMap();
        if (arbitraryDataMap != null && !arbitraryDataMap.isEmpty()) {
            // Add arbitrary data map to the default template
            OMElement parentPropertyElement = factory.createOMElement(new QName(EVENT_ARBITRARY_DATA_MAP_TAG));

            for (Map.Entry<String, Object> entry : arbitraryDataMap.entrySet()) {
                OMElement propertyElement = factory.createOMElement(new QName(entry.getKey()));
                propertyElement.setText(entry.getValue().toString());
                parentPropertyElement.addChild(propertyElement);
            }
            compositeEventElement.getFirstElement().addChild(parentPropertyElement);
        }*/

        return builder.toString();
    }
}
