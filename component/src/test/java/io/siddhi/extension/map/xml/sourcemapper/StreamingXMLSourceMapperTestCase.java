/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import io.siddhi.core.SiddhiAppRuntime;
import io.siddhi.core.SiddhiManager;
import io.siddhi.core.event.Event;
import io.siddhi.core.stream.output.StreamCallback;
import io.siddhi.core.util.EventPrinter;
import io.siddhi.core.util.transport.InMemoryBroker;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.testng.AssertJUnit;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test cases for siddhi-map-xml with streaming xml.
 */
public class StreamingXMLSourceMapperTestCase {
    private String dirUri;
    private static final Logger log = Logger.getLogger(StreamingXMLSourceMapperTestCase.class);
    private AtomicInteger count = new AtomicInteger();

    @BeforeClass
    public void init() {
        ClassLoader classLoader = StreamingXMLSourceMapperTestCase.class.getClassLoader();
        dirUri = classLoader.getResource("files").getFile();
    }

    @BeforeMethod
    public void doBeforeMethod() {
        count.set(0);
    }

    @Test
    public void testXmlInputMappingForMultipleEventsWithNestedXMLStructure() throws Exception {
        log.info("Test for multiple event generation with complete nested xml structure in streaming mode.");
        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "@source(type='inMemory', " +
                "topic='stock', " +
                "@map(type='xml'," +
                "enclosing.element=\"/root/node\"," +
                "fail.on.missing.attribute=\"false\"," +
                "enable.streaming.xml.content=\"true\"," +
                "extract.leaf.node.data=\"true\"," +
                "@attributes(att1 = \"/node/@att1\", value1 = \"/node/value1\", value2 = \"/node/value2\")))\n" +
                "define stream FooStream (att1 string, value1 string, value2 string); " +
                "define stream BarStream (att1 string, value1 string, value2 string); ";
        String query = "" +
                "from FooStream " +
                "select * " +
                "insert into BarStream; ";
        SiddhiManager siddhiManager = new SiddhiManager();
        SiddhiAppRuntime executionPlanRuntime = siddhiManager.createSiddhiAppRuntime(streams + query);
        executionPlanRuntime.addCallback("BarStream", new StreamCallback() {
            @Override
            public void receive(Event[] events) {
                EventPrinter.print(events);
                for (Event event : events) {
                    switch (count.incrementAndGet()) {
                        case 1:
                            org.junit.Assert.assertEquals("att1value", event.getData(0));
                            org.junit.Assert.assertEquals("some-value1", event.getData(1));
                            break;
                        case 2:
                            org.junit.Assert.assertEquals("some-value3", event.getData(1));
                            break;
                        default:
                            org.junit.Assert.fail();
                    }
                }
            }
        });
        executionPlanRuntime.start();
        InMemoryBroker.publish("stock", "<root>\n" +
                "    <node att1=\"att1value\">\n" +
                "        <value1>some-value1</value1>\n" +
                "        <value2>some-value2</value2>\n" +
                "    </node>\n" +
                "    <node>\n" +
                "        <value1>some-value3</value1>\n" +
                "        <value2>some-value4</value2>\n" +
                "    </node>\n" +
                "    <p>some-value-p</p>\n" +
                "</root>");
        //assert event count
        Thread.sleep(5000);
        AssertJUnit.assertEquals("Number of events", 2, count.get());
        executionPlanRuntime.shutdown();
        siddhiManager.shutdown();
    }

    @Test
    public void testXmlInputMappingForMultipleEventsWithNestedXMLStructure2() throws Exception {
        log.info("Test for multiple event generation with nested xml structure.");
        String query =
                "@App:name('TestSiddhiApp')" +
                        "@source(\n" +
                        "    type = 'file', \n" +
                        "    file.uri = \"file:/" + dirUri + "/test.xml\", \n" +
                        "    mode = \"line\", \n" +
                        "    tailing = \"false\", \n" +
                        "    action.after.process='keep',\n" +
                        "    @map(type='xml', \n" +
                        "        enclosing.element=\"/root/child\",\n" +
                        "        enable.streaming.xml.content=\"true\"," +
                        "        enclosing.element.as.event=\"true\"," +
                        "        extract.leaf.node.data=\"true\"," +
                        "        fail.on.missing.attribute=\"false\",\n" +
                        "        @attributes(id = \"/child/@id\", timestamp = \"/child/@timestamp\",  " +
                        "                    key = \"/child/detail/@key\", " +
                        "                    value = \"/child/detail/@value\")))\n" +
                        "define stream FooStream (id string, timestamp string, key string, value string);\n" +
                        "define stream OutputStream (id string, timestamp string, key string, value string);\n" +
                        "from FooStream\n" +
                        "insert into OutputStream;";
        SiddhiManager siddhiManager = new SiddhiManager();
        SiddhiAppRuntime executionPlanRuntime = siddhiManager.createSiddhiAppRuntime(query);
        executionPlanRuntime.addCallback("OutputStream", new StreamCallback() {
            @Override
            public void receive(Event[] events) {
                EventPrinter.print(events);
                for (Event event : events) {
                    switch (count.incrementAndGet()) {
                        case 1:
                            org.junit.Assert.assertEquals("413229", event.getData(0));
                            org.junit.Assert.assertEquals("2014-09-10T14:12:48Z", event.getData(1));
                            Assert.assertNull(event.getData(2));
                            break;
                        case 2:
                            org.junit.Assert.assertEquals("414427", event.getData(0));
                            org.junit.Assert.assertEquals("2018-01-24T23:16:10Z", event.getData(1));
                            Assert.assertNull(event.getData(2));
                            break;
                        case 3:
                            org.junit.Assert.assertEquals("673959", event.getData(0));
                            org.junit.Assert.assertEquals("2019-10-20T12:07:13Z", event.getData(1));
                            Assert.assertNull(event.getData(2));
                            break;
                        case 4:
                            org.junit.Assert.assertEquals("673959", event.getData(0));
                            org.junit.Assert.assertEquals("2019-10-20T12:07:13Z", event.getData(1));
                            org.junit.Assert.assertEquals("company", event.getData(2));
                            org.junit.Assert.assertEquals("wso2", event.getData(3));
                            break;
                    }
                }
            }
        });
        executionPlanRuntime.start();
        Thread.sleep(5000);
        AssertJUnit.assertEquals("Number of events", 10, count.get());
        executionPlanRuntime.shutdown();
        siddhiManager.shutdown();
    }

    @Test
    public void testXmlInputMappingForMultipleEventsWithNestedXMLStructure3() throws Exception {
        log.info("Test for multiple event generation with nested xml structure.");
        String query =
                "@App:name('TestSiddhiApp')" +
                        "@source(\n" +
                        "    type = 'file', \n" +
                        "    file.uri = \"file:/" + dirUri + "/test.xml\", \n" +
                        "    mode = \"line\", \n" +
                        "    tailing = \"false\", \n" +
                        "    action.after.process='keep',\n" +
                        "    @map(type='xml', \n" +
                        "        enclosing.element=\"/root/child/extra\",\n" +
                        "        enable.streaming.xml.content=\"true\"," +
                        "        extract.leaf.node.data=\"true\"," +
                        "        fail.on.missing.attribute=\"false\",\n" +
                        "        @attributes(id = \"/extra/@id\", timestamp = \"/extra/@timestamp\")))\n" +
                        "define stream FooStream (id string, timestamp string);\n" +
                        "define stream OutputStream (id string, timestamp string);\n" +
                        "from FooStream\n" +
                        "insert into OutputStream;";
        SiddhiManager siddhiManager = new SiddhiManager();
        SiddhiAppRuntime executionPlanRuntime = siddhiManager.createSiddhiAppRuntime(query);
        executionPlanRuntime.addCallback("OutputStream", new StreamCallback() {
            @Override
            public void receive(Event[] events) {
                EventPrinter.print(events);
                for (Event event : events) {
                    switch (count.incrementAndGet()) {
                        case 1:
                            org.junit.Assert.assertEquals("1234", event.getData(0));
                            org.junit.Assert.assertEquals("2014-09-11T10:36:37Z", event.getData(1));
                            break;
                        case 2:
                            org.junit.Assert.assertEquals("0987", event.getData(0));
                            org.junit.Assert.assertEquals("2014-09-11T10:36:37Z", event.getData(1));
                            break;
                        case 3:
                            org.junit.Assert.assertEquals("5678", event.getData(0));
                            org.junit.Assert.assertEquals("2014-09-11T10:36:37Z", event.getData(1));
                            break;
                        default:
                            org.junit.Assert.fail();
                    }
                }
            }
        });
        executionPlanRuntime.start();
        Thread.sleep(5000);
        AssertJUnit.assertEquals("Number of events", 3, count.get());
        executionPlanRuntime.shutdown();
        siddhiManager.shutdown();
    }
}
