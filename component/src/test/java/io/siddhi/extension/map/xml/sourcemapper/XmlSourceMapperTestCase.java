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

import io.siddhi.core.SiddhiAppRuntime;
import io.siddhi.core.SiddhiManager;
import io.siddhi.core.event.Event;
import io.siddhi.core.exception.SiddhiAppCreationException;
import io.siddhi.core.stream.output.StreamCallback;
import io.siddhi.core.util.EventPrinter;
import io.siddhi.core.util.SiddhiTestHelper;
import io.siddhi.core.util.transport.InMemoryBroker;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.testng.Assert;
import org.testng.AssertJUnit;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class XmlSourceMapperTestCase {
    private static final Logger log = (Logger) LogManager.getLogger(XmlSourceMapperTestCase.class);
    private AtomicInteger count = new AtomicInteger();

    @BeforeMethod
    public void init() {
        count.set(0);
    }

    /**
     * Expected input format:
     * <events>
     * <event>
     * <symbol>WSO2</symbol>
     * <price>55.6</price>
     * <volume>100</volume>
     * </event>
     * </events>
     */
    @Test
    public void testXmlInputMappingDefault() throws Exception {
        log.info("Test case for xml input mapping with default mapping");

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "@source(type='inMemory', topic='stock', @map(type='xml')) " +
                "define stream FooStream (symbol string, price float, volume int); " +
                "define stream BarStream (symbol string, price float, volume int); ";

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
                            org.junit.Assert.assertEquals(55.689f, event.getData(1));
                            org.junit.Assert.assertEquals("", event.getData(0));
                            break;
                        case 2:
                            org.junit.Assert.assertEquals(75.0f, event.getData(1));
                            org.junit.Assert.assertEquals("IBM@#$%^*", event.getData(0));
                            break;
                        case 3:
                            org.junit.Assert.assertEquals(" ", event.getData(0));
                            break;
                        default:
                            org.junit.Assert.fail();
                    }
                }
            }
        });
        executionPlanRuntime.start();
        InMemoryBroker.publish("stock", "<events><event><symbol></symbol><price>55.689</price>" +
                "<volume>100</volume></event></events>");
        InMemoryBroker.publish("stock", "<events><event><symbol>IBM@#$%^*</symbol><price>75</price>" +
                "<volume>10</volume></event></events>");
        InMemoryBroker.publish("stock", "<events><event><symbol>WSO2</symbol><price>75</price>" +
                "<volume></volume></event></events>");
        InMemoryBroker.publish("stock", "<events><event><symbol>WSO2</symbol><price></price>" +
                "<volume>10</volume></event></events>");
        InMemoryBroker.publish("stock", "<events><event><symbol> </symbol><price>56</price>" +
                "<volume>10</volume></event></events>");
        InMemoryBroker.publish("stock", "<events><event><symbol>WSO2</symbol><price>56</price>" +
                "<volume>aa</volume></event></events>");
        InMemoryBroker.publish("stock", "<events><event><symbol>WSO2</symbol><price>bb</price>" +
                "<volume>10</volume></event></events>");
        InMemoryBroker.publish("stock", "<events><event><symbol>WSO2</symbol><price>bb</price>" +
                "<volume>10.6</volume></event></events>");

        //assert event count
        AssertJUnit.assertEquals("Number of events", 3, count.get());
        executionPlanRuntime.shutdown();
        siddhiManager.shutdown();
    }

    @Test
    public void testXmlInputMappingDefaultMultipleEvents() throws Exception {
        log.info("Test case for xml input mapping with default mapping for multiple events");

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "@source(type='inMemory', topic='stock', @map(type='xml')) " +
                "define stream FooStream (symbol string, price float, volume long); " +
                "define stream BarStream (symbol string, price float, volume long); ";

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
                            org.junit.Assert.assertEquals(55.6f, event.getData(1));
                            break;
                        case 2:
                            org.junit.Assert.assertEquals(75.6f, event.getData(1));
                            break;
                        default:
                            org.junit.Assert.fail();
                    }
                }
            }
        });
        executionPlanRuntime.start();
        InMemoryBroker.publish("stock", "<events><event><symbol>WSO2</symbol><price>55.6</price>" +
                "<volume>100</volume></event><event><symbol>IBM</symbol><price>75.6</price>" +
                "<volume>10</volume></event><event111><symbol>IBM</symbol><price>75.6</price>" +
                "<volume>10</volume></event111></events>");

        //assert event count
        AssertJUnit.assertEquals("Number of events", 2, count.get());
        executionPlanRuntime.shutdown();
        siddhiManager.shutdown();
    }

    @Test
    public void testXmlInputMappingDefaultNegative() throws Exception {
        log.info("Test case for xml input mapping with default mapping for multiple events");

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "@source(type='inMemory', topic='stock', @map(type='xml')) " +
                "define stream FooStream (symbol string, price float, volume long); " +
                "define stream BarStream (symbol string, price float, volume long); ";

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
                        default:
                            org.junit.Assert.fail();
                    }
                }
            }
        });
        executionPlanRuntime.start();
        InMemoryBroker.publish("stock", "<test><event><symbol>WSO2</symbol><price>55.6</price>" +
                "<volume>100</volume></event><event><symbol>IBM</symbol><price>75.6</price>" +
                "<volume>10</volume></event></test>");
        InMemoryBroker.publish("stock", "<events><event><symbol>WSO2</symbol><price>55.6</price>" +
                "<volume>100</volume><event><symbol>IBM</symbol><price>75.6</price>" +
                "<volume>10</volume></event></events>");
        InMemoryBroker.publish("stock", "<test><event><symbol>WSO2</symbol><price>55.6</price>" +
                "<volume>100</volume></event><event><symbol>IBM</symbol><price><v1>33</v1></price>" +
                "<volume>10</volume></event></test>");


        //assert event count
        AssertJUnit.assertEquals("Number of events", 0, count.get());
        executionPlanRuntime.shutdown();
        siddhiManager.shutdown();
    }

    @Test
    public void testXmlInputMappingDefaultMultipleEventsOnBinaryMessage() throws Exception {
        log.info("Test case for xml input mapping with default mapping for multiple events");

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "@source(type='inMemory', topic='stock', @map(type='xml')) " +
                "define stream FooStream (symbol string, price float, volume long); " +
                "define stream BarStream (symbol string, price float, volume long); ";

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
                            org.junit.Assert.assertEquals(55.6f, event.getData(1));
                            break;
                        case 2:
                            org.junit.Assert.assertEquals(75.6f, event.getData(1));
                            break;
                        default:
                            org.junit.Assert.fail();
                    }
                }
            }
        });

        String events = "<events><event><symbol>WSO2</symbol><price>55.6</price>" +
                "<volume>100</volume></event><event><symbol>IBM</symbol><price>75.6</price>" +
                "<volume>10</volume></event><event111><symbol>IBM</symbol><price>75.6</price>"
                + "<volume>10</volume></event111></events>";
        executionPlanRuntime.start();
        InMemoryBroker.publish("stock", events.getBytes(StandardCharsets.UTF_8));
        SiddhiTestHelper.waitForEvents(50, 2, count, 2000);
        //assert event count
        AssertJUnit.assertEquals("Number of events", 2, count.get());
        executionPlanRuntime.shutdown();
        siddhiManager.shutdown();
    }

    @Test
    public void testXmlInputMappingCustom1() throws Exception {
        log.info("Test case for xml input mapping with custom mapping. Here multiple events are sent in one message.");

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "@source(type='inMemory', topic='stock', @map(type='xml', namespaces = " +
                "\"dt=urn:schemas-microsoft-com:datatypes,at=urn:schemas-microsoft-com:datatypes\", " +
                "enclosing.element=\"//portfolio\", @attributes(symbol = \"symbol\"" +
                "                                           , price = \"price\"" +
                "                                           , volume = \"volume\"))) " +
                "define stream FooStream (symbol string, price float, volume long); " +
                "define stream BarStream (symbol string, price float, volume long); ";

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
                            org.junit.Assert.assertEquals(55.6f, event.getData(1));
                            break;
                        case 2:
                            org.junit.Assert.assertEquals(75.6f, event.getData(1));
                            break;
                        case 3:
                            org.junit.Assert.assertEquals("null", event.getData(0));
                            break;
                        case 4:
                            org.junit.Assert.assertEquals("", event.getData(0));
                            break;
                        default:
                            org.junit.Assert.fail();
                    }
                }
            }
        });
        executionPlanRuntime.start();
        InMemoryBroker.publish("stock", "<?xml version=\"1.0\"?>" +
                "<portfolio xmlns:dt=\"urn:schemas-microsoft-com:datatypes\">" +
                "  <stock exchange=\"nasdaq\">" +
                "    <volume>100</volume>" +
                "    <symbol>WSO2</symbol>" +
                "    <price dt:dt=\"number\">55.6</price>" +
                "  </stock>" +
                "  <stock1 exchange=\"nyse\">" +
                "    <volume>200</volume>" +
                "    <symbol>IBM</symbol>" +
                "    <price dt:dt=\"number\">75.6</price>" +
                "  </stock1>" +
                "  <stock1 exchange=\"nyse\">" +
                "    <volume1>200</volume1>" +
                "    <symbol>IBM</symbol>" +
                "    <price dt:dt=\"number\">75.6</price>" +
                "  </stock1>" +
                "  <stock exchange=\"nasdaq\">" +
                "    <volume>null</volume>" +
                "    <symbol>WSO2</symbol>" +
                "    <price dt:dt=\"number\">55.6</price>" +
                "  </stock>" +
                "  <stock exchange=\"nasdaq\">" +
                "    <volume>100</volume>" +
                "    <symbol>null</symbol>" +
                "    <price dt:dt=\"number\">55.6</price>" +
                "  </stock>" +
                "  <stock exchange=\"nasdaq\">" +
                "    <volume>100</volume>" +
                "    <symbol>WSO2</symbol>" +
                "    <price dt:dt=\"number\">null</price>" +
                "  </stock>" +
                "  <stock exchange=\"nasdaq\">" +
                "    <volume>100</volume>" +
                "    <symbol></symbol>" +
                "    <price dt:dt=\"number\">100</price>" +
                "  </stock>" +
                "  <stock exchange=\"nasdaq\">" +
                "    <volume></volume>" +
                "    <symbol>WSO2</symbol>" +
                "    <price dt:dt=\"number\">100</price>" +
                "  </stock>" +
                "  <stock exchange=\"nasdaq\">" +
                "    <volume>100</volume>" +
                "    <symbol>WSO2</symbol>" +
                "    <price dt:dt=\"number\"></price>" +
                "  </stock>" +
                "</portfolio>");
        //assert event count
        AssertJUnit.assertEquals("Number of events", 4, count.get());
        executionPlanRuntime.shutdown();
        siddhiManager.shutdown();
    }

    @Test
    public void testXmlInputMappingCustom2() throws Exception {
        log.info("Test case for xml input mapping with custom mapping. Here, only one event is sent in a message.");

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "@source(type='inMemory', topic='stock', @map(type='xml', namespaces = " +
                "\"dt=urn:schemas-microsoft-com:datatypes\", " +
                "enclosing.element=\"//portfolio\", @attributes(symbol = \"symbol/@exchange\"" +
                "                                           , price = \"price/@priceAttr\"" +
                "                                           , volume = \"volume/@volAttr\"))) " +
                "define stream FooStream (symbol string, price float, volume long); " +
                "define stream BarStream (symbol string, price float, volume long); ";

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
                            org.junit.Assert.assertEquals(55.8f, event.getData(1));
                            org.junit.Assert.assertEquals("null", event.getData(0));
                            org.junit.Assert.assertEquals(108L, event.getData(2));
                            break;
                        case 2:
                            org.junit.Assert.assertEquals(75.8f, event.getData(1));
                            org.junit.Assert.assertEquals("nasdaq", event.getData(0));
                            org.junit.Assert.assertEquals(208L, event.getData(2));
                            break;
                        default:
                            org.junit.Assert.fail();
                    }
                }
            }
        });
        executionPlanRuntime.start();
        InMemoryBroker.publish("stock", "<?xml version=\"1.0\"?>" +
                "<portfolio xmlns:dt=\"urn:schemas-microsoft-com:datatypes\">" +
                "  <stock exchange=\"nasdaq\">" +
                "    <volume volAttr=\"108\">100</volume>" +
                "    <symbol exchange=\"null\">WSO2</symbol>" +
                "    <price priceAttr=\"55.8\">55.6</price>" +
                "  </stock>" +
                "</portfolio>");
        InMemoryBroker.publish("stock", "<?xml version=\"1.0\"?>" +
                "<portfolio xmlns:dt=\"urn:schemas-microsoft-com:datatypes\">" +
                "  <stock exchange=\"nyse\">" +
                "    <volume volAttr=\"208\">200</volume>" +
                "    <symbol exchange=\"nasdaq\">IBM</symbol>" +
                "    <price priceAttr=\"75.8\">75.6</price>" +
                "  </stock>" +
                "</portfolio>");
        //assert event count
        AssertJUnit.assertEquals("Number of events", 2, count.get());
        executionPlanRuntime.shutdown();
        siddhiManager.shutdown();
    }

    @Test
    public void testXmlInputMappingCustom3() throws Exception {
        log.info("Test case for xml input mapping with custom mapping with complex xpath to extract one attribute. " +
                "Here multiple events are sent in one message.");

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "@source(type='inMemory', topic='stock', @map(type='xml', namespaces = " +
                "\"dt=urn:schemas-microsoft-com:datatypes\", " +
                "enclosing.element=\"//portfolio\", @attributes(symbol = \"company/symbol\"" +
                "                                           , price = \"price\"" +
                "                                           , volume = \"volume\"))) " +
                "define stream FooStream (symbol string, price float, volume long); " +
                "define stream BarStream (symbol string, price float, volume long); ";

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
                            org.junit.Assert.assertEquals(55.6f, event.getData(1));
                            break;
                        case 2:
                            org.junit.Assert.assertEquals(75.6f, event.getData(1));
                            break;
                        default:
                            org.junit.Assert.fail();
                    }
                }
            }
        });
        executionPlanRuntime.start();
        InMemoryBroker.publish("stock", "<?xml version=\"1.0\"?>" +
                "<portfolio xmlns:dt=\"urn:schemas-microsoft-com:datatypes\">" +
                "  <stock exchange=\"nasdaq\">" +
                "    <volume>100</volume>" +
                "    <company>" +
                "       <symbol>WSO2</symbol>" +
                "    </company>" +
                "    <price dt:dt=\"number\">55.6</price>" +
                "  </stock>" +
                "  <stock exchange=\"nyse\">" +
                "    <volume>200</volume>" +
                "    <company>" +
                "       <symbol>IBM</symbol>" +
                "    </company>" +
                "    <price dt:dt=\"number\">75.6</price>" +
                "  </stock>" +
                "  <stock exchange=\"nyse\">" +
                "    <volume></volume>" +
                "    <company>" +
                "       <symbol>IBM</symbol>" +
                "    </company>" +
                "    <price dt:dt=\"number\">75.6</price>" +
                "  </stock>" +
                "</portfolio>");
        //assert event count
        AssertJUnit.assertEquals("Number of events", 2, count.get());
        executionPlanRuntime.shutdown();
        siddhiManager.shutdown();
    }

    @Test
    public void testXmlInputMappingCustom4() throws Exception {
        log.info("Test case for xml input mapping with custom mapping where @attribute is not present");

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "@source(type='inMemory', topic='stock', @map(type='xml', namespaces = " +
                "\"dt=urn:schemas-microsoft-com:datatypes\", " +
                "enclosing.element=\"//portfolio\")) " +
                "define stream FooStream (symbol string, price float, volume long); " +
                "define stream BarStream (symbol string, price float, volume long); ";

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
                            org.junit.Assert.assertEquals(55.6f, event.getData(1));
                            break;
                        case 2:
                            org.junit.Assert.assertEquals(75.6f, event.getData(1));
                            break;
                        default:
                            org.junit.Assert.fail();
                    }
                }
            }
        });
        executionPlanRuntime.start();
        InMemoryBroker.publish("stock", "<events><event><symbol>WSO2</symbol><price>55.6</price>" +
                "<volume>100</volume></event></events>");
        InMemoryBroker.publish("stock", "<events><event><symbol>IBM</symbol><price>75.6</price>" +
                "<volume>10</volume></event></events>");
        InMemoryBroker.publish("stock", "<events><event><symbol1>IBM</symbol1><price>75.6</price>" +
                "<volume>10</volume></event></events>");

        //assert event count
        AssertJUnit.assertEquals("Number of events", 2, count.get());
        executionPlanRuntime.shutdown();
        siddhiManager.shutdown();
    }

    @Test
    public void testXmlInputMappingCustom5() throws Exception {
        log.info("Verify xml message correctly mapped without grouping element with correct xpath from root");

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "@source(type='inMemory', topic='stock', @map(type='xml', namespaces = " +
                "\"dt=urn:schemas-microsoft-com:datatypes\", " +
                "@attributes(symbol = \"//stock[1]/symbol\"" +
                "                                           , price = \"//stock[1]/price\"" +
                "                                           , volume = \"//stock[1]/volume\"))) " +
                "define stream FooStream (symbol string, price float, volume long); " +
                "define stream BarStream (symbol string, price float, volume long); ";

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
                            org.junit.Assert.assertEquals(55.6f, event.getData(1));
                            break;
                        default:
                            org.junit.Assert.fail();
                    }
                }
            }
        });
        executionPlanRuntime.start();
        InMemoryBroker.publish("stock", "<?xml version=\"1.0\"?>" +
                "<portfolio xmlns:dt=\"urn:schemas-microsoft-com:datatypes\">" +
                "  <stock exchange=\"nasdaq\">" +
                "    <volume>100</volume>" +
                "    <symbol>WSO2</symbol>" +
                "    <price dt=\"number\">55.6</price>" +
                "  </stock>" +
                "  <stock exchange=\"nyse\">" +
                "    <volume>200</volume>" +
                "    <symbol>IBM</symbol>" +
                "    <price dt=\"number\">75.6</price>" +
                "  </stock>" +
                "</portfolio>");
        //assert event count
        AssertJUnit.assertEquals("Number of events", 1, count.get());
        executionPlanRuntime.shutdown();
        siddhiManager.shutdown();
    }

    @Test
    public void testXmlInputMappingCustom6() throws Exception {
        log.info("Verify xml message being dropped due to incorrect namespace in mapping");

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "@source(type='inMemory', topic='stock', @map(type='xml', namespaces = " +
                "\"dt=urn:schemas-microsoft-com:data\", " +
                "enclosing.element=\"//portfolio\", @attributes(symbol = \"symbol\"" +
                "                                           , price = \"dt:price\"" +
                "                                           , volume = \"volume\"))) " +
                "define stream FooStream (symbol string, price float, volume long); " +
                "define stream BarStream (symbol string, price float, volume long); ";

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
                            org.junit.Assert.assertEquals(55.6f, event.getData(1));
                            break;
                        case 2:
                            org.junit.Assert.assertEquals(75.6f, event.getData(1));
                            break;
                        default:
                            org.junit.Assert.fail();
                    }
                }
            }
        });
        executionPlanRuntime.start();
        InMemoryBroker.publish("stock", "<?xml version=\"1.0\"?>" +
                "<portfolio xmlns:dt=\"urn:schemas-microsoft-com:datatypes\">" +
                "  <stock exchange=\"nasdaq\">" +
                "    <volume>100</volume>" +
                "    <symbol>WSO2</symbol>" +
                "    <dt:price dt:at=\"number\">55.6</dt:price>" +
                "  </stock>" +
                "  <stock exchange=\"nyse\">" +
                "    <volume>200</volume>" +
                "    <symbol>IBM</symbol>" +
                "    <dt:price dt:at=\"number\">75.6</dt:price>" +
                "  </stock>" +
                "</portfolio>");
        //assert event count
        AssertJUnit.assertEquals("Number of events", 0, count.get());
        executionPlanRuntime.shutdown();
        siddhiManager.shutdown();
    }

    @Test
    public void testXmlInputMappingCustom7() throws Exception {
        log.info("Verify xml message being dropped without grouping element when incorrect xpath is used from root");

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "@source(type='inMemory', topic='stock', @map(type='xml', namespaces = " +
                "\"dt=urn:schemas-microsoft-com:datatypes\", " +
                "@attributes(symbol = \"symbol\"" +
                "                                           , price = \"price\"" +
                "                                           , volume = \"volume\"))) " +
                "define stream FooStream (symbol string, price float, volume long); " +
                "define stream BarStream (symbol string, price float, volume long); ";

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
                        default:
                            org.junit.Assert.fail();
                    }
                }
            }
        });
        executionPlanRuntime.start();
        InMemoryBroker.publish("stock", "<?xml version=\"1.0\"?>" +
                "<portfolio xmlns:dt=\"urn:schemas-microsoft-com:datatypes\">" +
                "  <stock exchange=\"nasdaq\">" +
                "    <volume>100</volume>" +
                "    <symbol>WSO2</symbol>" +
                "    <price dt=\"number\">55.6</price>" +
                "  </stock>" +
                "  <stock exchange=\"nyse\">" +
                "    <volume>200</volume>" +
                "    <symbol>IBM</symbol>" +
                "    <price dt=\"number\">75.6</price>" +
                "  </stock>" +
                "</portfolio>");
        //assert event count
        AssertJUnit.assertEquals("Number of events", 0, count.get());
        executionPlanRuntime.shutdown();
        siddhiManager.shutdown();
    }

    @Test
    public void testXmlInputMappingCustom8() throws Exception {
        log.info("Verify xml message being dropped due to incorrect grouping element configuration");

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "@source(type='inMemory', topic='stock', @map(type='xml', namespaces = " +
                "\"dt=urn:schemas-microsoft-com:datatypes\", " +
                "enclosing.element=\"//portfolio11\", @attributes(symbol = \"symbol\"" +
                "                                           , price = \"price\"" +
                "                                           , volume = \"volume\"))) " +
                "define stream FooStream (symbol string, price float, volume long); " +
                "define stream BarStream (symbol string, price float, volume long); ";

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
                        default:
                            org.junit.Assert.fail();
                    }
                }
            }
        });
        executionPlanRuntime.start();
        InMemoryBroker.publish("stock", "<?xml version=\"1.0\"?>" +
                "<portfolio xmlns:dt=\"urn:schemas-microsoft-com:datatypes\">" +
                "  <stock exchange=\"nasdaq\">" +
                "    <volume>100</volume>" +
                "    <symbol>WSO2</symbol>" +
                "    <price dt:dt=\"number\">55.6</price>" +
                "  </stock>" +
                "  <stock exchange=\"nyse\">" +
                "    <volume>200</volume>" +
                "    <symbol>IBM</symbol>" +
                "    <price dt:dt=\"number\">75.6</price>" +
                "  </stock>" +
                "</portfolio>");
        //assert event count
        AssertJUnit.assertEquals("Number of events", 0, count.get());
        executionPlanRuntime.shutdown();
        siddhiManager.shutdown();
    }

    @Test(expectedExceptions = SiddhiAppCreationException.class)
    public void testXmlInputMappingCustom9() throws InterruptedException {
        log.info("Verify xml message being dropped due to non existence stream attributes");

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "@source(type='inMemory', topic='stock', @map(type='xml', namespaces = " +
                "\"dt=urn:schemas-microsoft-com:datatypes\", " +
                "enclosing.element=\"//portfolio\", @attributes(symbol1 = \"symbol\"" +
                "                                           , price = \"price\"" +
                "                                           , volume = \"volume\"))) " +
                "define stream FooStream (symbol string, price float, volume long); " +
                "define stream BarStream (symbol string, price float, volume long); ";

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
                        default:
                            org.junit.Assert.fail();
                    }
                }
            }
        });
        executionPlanRuntime.start();
        executionPlanRuntime.shutdown();
        siddhiManager.shutdown();
    }

    @Test
    public void testXmlInputMappingCustom10() throws Exception {
        log.info("Verify xml mapping when elements defined are non existent and fail.on.missing.attribute is false");

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "@source(type='inMemory', topic='stock', @map(type='xml', namespaces = " +
                "\"dt=urn:schemas-microsoft-com:datatypes\", fail.on.missing.attribute=\"false\"," +
                "enclosing.element=\"//portfolio\", @attributes(symbol = \"symbol\"" +
                "                                           , price = \"price\"" +
                "                                           , volume = \"volume\"))) " +
                "define stream FooStream (symbol string, price float, volume long); " +
                "define stream BarStream (symbol string, price float, volume long); ";

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
                            org.junit.Assert.assertEquals(55.6f, event.getData(1));
                            break;
                        case 2:
                            org.junit.Assert.assertEquals(75.6f, event.getData(1));
                            break;
                        default:
                            org.junit.Assert.fail();
                    }
                }
            }
        });
        executionPlanRuntime.start();
        InMemoryBroker.publish("stock", "<?xml version=\"1.0\"?>" +
                "<portfolio xmlns:dt=\"urn:schemas-microsoft-com:datatypes\">" +
                "  <stock exchange=\"nasdaq\">" +
                "    <volume></volume>" +
                "    <symbol>WSO2</symbol>" +
                "    <price dt:dt=\"number\">55.6</price>" +
                "  </stock>" +
                "  <stock exchange=\"nyse\">" +
                "    <volume>200</volume>" +
                "    <symbol>IBM</symbol>" +
                "    <price dt:dt=\"number\">75.6</price>" +
                "  </stock>" +
                "</portfolio>");
        //assert event count
        AssertJUnit.assertEquals("Number of events", 2, count.get());
        executionPlanRuntime.shutdown();
        siddhiManager.shutdown();
    }

    @Test
    public void testXmlInputMappingCustom11() throws Exception {
        log.info("Verify xml mapping when multiple enclosing tags are present");

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "@source(type='inMemory', topic='stock', @map(type='xml', namespaces = " +
                "\"dt=urn:schemas-microsoft-com:datatypes\", " +
                "enclosing.element=\"//portfolio\", @attributes(symbol = \"symbol\"" +
                "                                           , price = \"price\"" +
                "                                           , volume = \"volume\"))) " +
                "define stream FooStream (symbol string, price float, volume long); " +
                "define stream BarStream (symbol string, price float, volume long); ";

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
                            org.junit.Assert.assertEquals(55.6f, event.getData(1));
                            break;
                        case 2:
                            org.junit.Assert.assertEquals(75.6f, event.getData(1));
                            break;
                        default:
                            org.junit.Assert.fail();
                    }
                }
            }
        });
        executionPlanRuntime.start();
        InMemoryBroker.publish("stock", "<?xml version=\"1.0\"?>" +
                "<root>" +
                "<portfolio xmlns:dt=\"urn:schemas-microsoft-com:datatypes\">" +
                "  <stock exchange=\"nasdaq\">" +
                "    <volume>55</volume>" +
                "    <symbol>WSO2</symbol>" +
                "    <price>55.6</price>" +
                "  </stock>" +
                "</portfolio>" +
                "<portfolio xmlns:dt=\"urn:schemas-microsoft-com:datatypes\">" +
                "  <stock exchange=\"nyse\">" +
                "    <volume>200</volume>" +
                "    <symbol>IBM</symbol>" +
                "    <price>75.6</price>" +
                "  </stock>" +
                "</portfolio>" +
                "</root>");
        //assert event count
        AssertJUnit.assertEquals("Number of events", 2, count.get());
        executionPlanRuntime.shutdown();
        siddhiManager.shutdown();
    }

    @Test
    public void testXmlInputMappingCustom12() throws Exception {
        log.info("Check incoming XML event");
        UnitTestAppender appender = new UnitTestAppender("UnitTestAppender", null);
        final Logger logger = (Logger) LogManager.getRootLogger();
        logger.setLevel(Level.ALL);
        logger.addAppender(appender);
        appender.start();
        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "@source(type='inMemory', topic='stock', @map(type='xml', namespaces = " +
                "\"dt=urn:schemas-microsoft-com:datatypes\", " +
                "enclosing.element=\"//portfolio\", @attributes(symbol = \"symbol\"" +
                "                                           , price = \"price\"" +
                "                                           , volume = \"volume\"))) " +
                "define stream FooStream (symbol string, price float, volume long); " +
                "define stream BarStream (symbol string, price float, volume long); ";

        String query = "" +
                "from FooStream " +
                "select * " +
                "insert into BarStream; ";

        SiddhiManager siddhiManager = new SiddhiManager();
        SiddhiAppRuntime executionPlanRuntime = siddhiManager.createSiddhiAppRuntime(streams + query);
        executionPlanRuntime.start();
        InMemoryBroker.publish("stock", "");
        //assert event count
        AssertJUnit.assertTrue(((UnitTestAppender) logger.getAppenders().
                get("UnitTestAppender")).getMessages().contains("Hence dropping message chunk"));
        executionPlanRuntime.shutdown();
        siddhiManager.shutdown();
        logger.removeAppender(appender);
    }

    @Test
    public void testXmlInputMappingCustom13() throws Exception {
        log.info("Test case for name space format.");
        UnitTestAppender appender = new UnitTestAppender("UnitTestAppender", null);
        final Logger logger = (Logger) LogManager.getRootLogger();
        logger.setLevel(Level.ALL);
        logger.addAppender(appender);
        appender.start();
        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "@source(type='inMemory', topic='stock', @map(type='xml', namespaces = " +
                "\"dt=urn:schemas-microsoft-=com:datatypes\", " +
                "enclosing.element=\"//portfolio\", @attributes(symbol = \"symbol\"" +
                "                                           , price = \"price\"" +
                "                                           , volume = \"volume\"))) " +
                "define stream FooStream (symbol string, price float, volume long); " +
                "define stream BarStream (symbol string, price float, volume long); ";

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
                            org.junit.Assert.assertEquals(55.6f, event.getData(1));
                            break;
                        default:
                            org.junit.Assert.fail();
                    }
                }
            }
        });
        executionPlanRuntime.start();
        InMemoryBroker.publish("stock", "<?xml version=\"1.0\"?>" +
                "<portfolio xmlns:dt=\"urn:schemas-microsoft-com:datatypes\">" +
                "  <stock exchange=\"nasdaq\">" +
                "    <volume>100</volume>" +
                "    <symbol>WSO2</symbol>" +
                "    <price dt:dt=\"number\">55.6</price>" +
                "  </stock>" +
                "</portfolio>");
        //assert event count
        AssertJUnit.assertTrue(((UnitTestAppender) logger.getAppenders().
                get("UnitTestAppender")).getMessages().contains("Each namespace has to have format"));
        executionPlanRuntime.shutdown();
        siddhiManager.shutdown();
        logger.removeAppender(appender);
    }

    @Test
    public void testXmlInputMappingCustom15() throws Exception {
        log.info("Test case for If elementObj instanceof OMElement and element.getFirstElement() "
                + "!= null then check attribute's type not String .");
        UnitTestAppender appender = new UnitTestAppender("UnitTestAppender", null);
        final Logger logger = (Logger) LogManager.getRootLogger();
        logger.setLevel(Level.ALL);
        logger.addAppender(appender);
        appender.start();
        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "@source(type='inMemory', topic='stock', @map(type='xml', namespaces = " +
                "\"dt=urn:schemas-microsoftcom:datatypes\", " +
                "enclosing.element=\"//portfolio\", @attributes(symbol = \"symbol\"" +
                "                                           , price = \"price\"" +
                "                                           , volume = \"volume\"))) " +
                "define stream FooStream (symbol string, price float, volume long); " +
                "define stream BarStream (symbol string, price float, volume long); ";

        String query = "" +
                "from FooStream " +
                "select * " +
                "insert into BarStream; ";

        SiddhiManager siddhiManager = new SiddhiManager();
        SiddhiAppRuntime executionPlanRuntime = siddhiManager.createSiddhiAppRuntime(streams + query);

        executionPlanRuntime.start();
        InMemoryBroker.publish("stock", "<?xml version=\"1.0\"?>" +
                "<portfolio xmlns:dt=\"urn:schemas-microsoft-com:datatypes\">" +
                "  <stock exchange=\"nasdaq\">" +
                "    <symbol><symbol>WSO2</symbol></symbol>" +
                "    <price><price>55.6</price></price>" +
                "     <volume>100</volume>" +
                "  </stock>" +
                "</portfolio>");
        //assert event count
        executionPlanRuntime.shutdown();
        AssertJUnit.assertTrue(((UnitTestAppender) logger.getAppenders().
                get("UnitTestAppender")).getMessages().contains("a leaf element and stream definition is"
                + " not expecting a String attribute"));
        siddhiManager.shutdown();
        logger.removeAppender(appender);
    }

    @Test
    public void testXmlInputMappingCustomForEvents() throws Exception {
        log.info("Test case for if elementObj instanceof OMAttribute but some problem occurred during convert data.");
        UnitTestAppender appender = new UnitTestAppender("UnitTestAppender", null);
        final Logger logger = (Logger) LogManager.getRootLogger();
        logger.setLevel(Level.ALL);
        logger.addAppender(appender);
        appender.start();
        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "@source(type='inMemory', topic='stock', @map(type='xml', namespaces = " +
                "\"dt=urn:schemas-microsoft-com:datatypes\", " +
                "enclosing.element=\"//portfolio\", @attributes(symbol = \"symbol/@exchange\"" +
                "                                           , price = \"price/@priceAttr\"" +
                "                                           , volume = \"volume/@volAttr\"))) " +
                "define stream FooStream (symbol string, price float, volume long); " +
                "define stream BarStream (symbol string, price float, volume long); ";

        String query = "" +
                "from FooStream " +
                "select * " +
                "insert into BarStream; ";

        SiddhiManager siddhiManager = new SiddhiManager();
        SiddhiAppRuntime executionPlanRuntime = siddhiManager.createSiddhiAppRuntime(streams + query);

        executionPlanRuntime.start();
        InMemoryBroker.publish("stock", "<?xml version=\"1.0\"?>" +
                "<portfolio xmlns:dt=\"urn:schemas-microsoft-com:datatypes\">" +
                "  <stock exchange=\"nasdaq\">" +
                "    <volume volAttr=\"108\">100</volume>" +
                "    <symbol exchange=\"null\">WSO2</symbol>" +
                "    <price priceAttr=\"\">55.6</price>" +
                "  </stock>" +
                "</portfolio>");
        //assert event count
        AssertJUnit.assertTrue(((UnitTestAppender) logger.getAppenders().
                get("UnitTestAppender")).getMessages().contains("Error occurred when extracting attribute value"));
        executionPlanRuntime.shutdown();
        siddhiManager.shutdown();
        logger.removeAppender(appender);
    }

    @Test(expectedExceptions = SiddhiAppCreationException.class)
    public void testXmlInputMappingCustom16() throws InterruptedException {
        log.info("Test case for enclosing Element XPath.");

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "@source(type='inMemory', topic='stock', @map(type='xml', namespaces = " +
                "\"dt=urn:schemas-microsoft-com:datatypes,at=urn:schemas-microsoft-com:datatypes\", " +
                "enclosing.element=\"//12\", @attributes(symbol = \"symbol\"" +
                "                                           , price = \"price\"" +
                "                                           , volume = \"volume\"))) " +
                "define stream FooStream (symbol string, price float, volume long); " +
                "define stream BarStream (symbol string, price float, volume long); ";

        String query = "" +
                "from FooStream " +
                "select * " +
                "insert into BarStream; ";

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.createSiddhiAppRuntime(streams + query);
    }

    @Test
    public void testXmlInputMappingCustomOnBinaryMessage17() throws Exception {
        log.info("Verify xml mapping when multiple enclosing tags are present");

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "@source(type='inMemory', topic='stock', @map(type='xml', namespaces = " +
                "\"dt=urn:schemas-microsoft-com:datatypes\", " +
                "enclosing.element=\"//portfolio\", @attributes(symbol = \"symbol\"" +
                "                                           , price = \"price\"" +
                "                                           , volume = \"volume\"))) " +
                "define stream FooStream (symbol string, price float, volume long); " +
                "define stream BarStream (symbol string, price float, volume long); ";

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
                            org.junit.Assert.assertEquals(55.6f, event.getData(1));
                            break;
                        case 2:
                            org.junit.Assert.assertEquals(75.6f, event.getData(1));
                            break;
                        default:
                            org.junit.Assert.fail();
                    }
                }
            }
        });
        executionPlanRuntime.start();
        InMemoryBroker.publish("stock", ("<?xml version=\"1.0\"?>" +
                "<root>" +
                "<portfolio xmlns:dt=\"urn:schemas-microsoft-com:datatypes\">" +
                "  <stock exchange=\"nasdaq\">" +
                "    <volume>55</volume>" +
                "    <symbol>WSO2</symbol>" +
                "    <price>55.6</price>" +
                "  </stock>" +
                "</portfolio>" +
                "<portfolio xmlns:dt=\"urn:schemas-microsoft-com:datatypes\">" +
                "  <stock exchange=\"nyse\">" +
                "    <volume>200</volume>" +
                "    <symbol>IBM</symbol>" +
                "    <price>75.6</price>" +
                "  </stock>" +
                "</portfolio>" +
                "</root>").getBytes(StandardCharsets.UTF_8));
        //assert event count
        AssertJUnit.assertEquals("Number of events", 2, count.get());
        executionPlanRuntime.shutdown();
        siddhiManager.shutdown();
    }

    @Test
    public void testXmlInputMappingCustomOnNoChildrenInXpath() throws Exception {
        log.info("Verify xml mapping when multiple enclosing tags are present with no child elements");

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "@source(type='inMemory', topic='stock', @map(type='xml', " +
                "enclosing.element=\"//message/listNode/data\", @attributes(data = \"data\")," +
                "enclosing.element.as.event=\"true\")) " +
                "define stream FooStream (data int); " +
                "define stream BarStream (data int); ";

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
                            org.junit.Assert.assertEquals(1, event.getData(0));
                            break;
                        case 2:
                            org.junit.Assert.assertEquals(2, event.getData(0));
                            break;
                        case 3:
                            org.junit.Assert.assertEquals(3, event.getData(0));
                            break;
                        default:
                            org.junit.Assert.fail();
                    }
                }
            }
        });
        executionPlanRuntime.start();
        InMemoryBroker.publish("stock", ("<?xml version=\"1.0\"?>" +
                "<message>\n" +
                "    <listNode>\n" +
                "       <data>1</data>\n" +
                "       <data>2</data>\n" +
                "       <data>3</data>\n" +
                "    </listNode>\n" +
                "</message>").getBytes(StandardCharsets.UTF_8));
        //assert event count
        AssertJUnit.assertEquals("Number of events", 3, count.get());
        executionPlanRuntime.shutdown();
        siddhiManager.shutdown();
    }

    @Test
    public void testXmlInputMappingCustom18() throws Exception {
        log.info("testXmlInputMappingCustom18");

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "@source(type='inMemory', topic='stock', @map(type='xml', namespaces = " +
                "\"dt=urn:schemas-microsoft-com:datatypes\"," +
                "enclosing.element=\"//portfolio\", @attributes(\"symbol\"" +
                "                                           , \"price\"" +
                "                                           , \"volume\"))) " +
                "define stream FooStream (symbol string, price float, volume long); " +
                "define stream BarStream (symbol string, price float, volume long); ";

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
                            org.junit.Assert.assertEquals(55.6f, event.getData(1));
                            break;
                        case 2:
                            org.junit.Assert.assertEquals(75.6f, event.getData(1));
                            break;
                        default:
                            org.junit.Assert.fail();
                    }
                }
            }
        });
        executionPlanRuntime.start();
        InMemoryBroker.publish("stock", "<?xml version=\"1.0\"?>" +
                "<portfolio xmlns:dt=\"urn:schemas-microsoft-com:datatypes\">" +
                "  <stock exchange=\"nasdaq\">" +
                "    <volume>100</volume>" +
                "    <symbol>WSO2</symbol>" +
                "    <price dt:dt=\"number\">55.6</price>" +
                "  </stock>" +
                "  <stock exchange=\"nyse\">" +
                "    <volume>200</volume>" +
                "    <symbol>IBM</symbol>" +
                "    <price dt:dt=\"number\">75.6</price>" +
                "  </stock>" +
                "</portfolio>");
        //assert event count
        AssertJUnit.assertEquals("Number of events", 2, count.get());
        executionPlanRuntime.shutdown();
        siddhiManager.shutdown();
    }

    @Test
    public void testXmlInputMappingCustom19() throws Exception {
        log.info("testXmlInputMappingCustom19");

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "@source(type='testTrpInMemory', prop1='foo', prop2='bar', " +
                "topic='stock', @map(type='xml', namespaces = " +
                "\"dt=urn:schemas-microsoft-com:datatypes\"," +
                "enclosing.element=\"//portfolio\", @attributes(\"trp:symbol\"" +
                "                                           , \"price\"" +
                "                                           , \"volume\"))) " +
                "define stream FooStream (symbol string, price float, volume long); " +
                "define stream BarStream (symbol string, price float, volume long); ";

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
                    Assert.assertEquals("foo", event.getData(0));
                    switch (count.incrementAndGet()) {
                        case 1:
                            org.junit.Assert.assertEquals(55.6f, event.getData(1));
                            break;
                        case 2:
                            org.junit.Assert.assertEquals(75.6f, event.getData(1));
                            break;
                        default:
                            org.junit.Assert.fail();
                    }
                }
            }
        });
        executionPlanRuntime.start();
        InMemoryBroker.publish("stock", "<?xml version=\"1.0\"?>" +
                "<portfolio xmlns:dt=\"urn:schemas-microsoft-com:datatypes\">" +
                "  <stock exchange=\"nasdaq\">" +
                "    <volume>100</volume>" +
                "    <symbol>WSO2</symbol>" +
                "    <price dt:dt=\"number\">55.6</price>" +
                "  </stock>" +
                "  <stock exchange=\"nyse\">" +
                "    <volume>200</volume>" +
                "    <symbol>IBM</symbol>" +
                "    <price dt:dt=\"number\">75.6</price>" +
                "  </stock>" +
                "</portfolio>");
        //assert event count
        AssertJUnit.assertEquals("Number of events", 2, count.get());
        executionPlanRuntime.shutdown();
        siddhiManager.shutdown();
    }

    @Test
    public void testXmlInputMappingCustom20() throws Exception {
        log.info("testXmlInputMappingCustom20");

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "@source(type='testTrpInMemory', prop1='foo', prop2='bar', " +
                "topic='stock', @map(type='xml', namespaces = " +
                "\"dt=urn:schemas-microsoft-com:datatypes\"," +
                "enclosing.element=\"//portfolio\", @attributes(volume=\"volume\"" +
                "                                           , price=\"price\"" +
                "                                           , symbol=\"trp:symbol\"))) " +
                "define stream FooStream (symbol string, price float, volume long); " +
                "define stream BarStream (symbol string, price float, volume long); ";

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
                    Assert.assertEquals("foo", event.getData(0));
                    switch (count.incrementAndGet()) {
                        case 1:
                            org.junit.Assert.assertEquals(55.6f, event.getData(1));
                            break;
                        case 2:
                            org.junit.Assert.assertEquals(75.6f, event.getData(1));
                            break;
                        default:
                            org.junit.Assert.fail();
                    }
                }
            }
        });
        executionPlanRuntime.start();
        InMemoryBroker.publish("stock", "<?xml version=\"1.0\"?>" +
                "<portfolio xmlns:dt=\"urn:schemas-microsoft-com:datatypes\">" +
                "  <stock exchange=\"nasdaq\">" +
                "    <volume>100</volume>" +
                "    <symbol>WSO2</symbol>" +
                "    <price dt:dt=\"number\">55.6</price>" +
                "  </stock>" +
                "  <stock exchange=\"nyse\">" +
                "    <volume>200</volume>" +
                "    <symbol>IBM</symbol>" +
                "    <price dt:dt=\"number\">75.6</price>" +
                "  </stock>" +
                "</portfolio>");
        //assert event count
        AssertJUnit.assertEquals("Number of events", 2, count.get());
        executionPlanRuntime.shutdown();
        siddhiManager.shutdown();
    }

    @Test
    public void testXmlInputMappingForMultipleEventsWithNestedXMLStructure() throws Exception {
        log.info("Test for multiple event generation with nested xml structure.");
        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "@source(type='inMemory', " +
                "topic='stock', " +
                "@map(type='xml'," +
                "enclosing.element=\"/portfolio\"," +
                "fail.on.missing.attribute=\"false\"," +
                "extract.leaf.node.data=\"true\"," +
                "@attributes(volume = \"/stock/volume\", price = \"/stock/price\", symbol = \"/stock/symbol\", " +
                "alternateSymbolValue = \"/stock/leafNode/alternateSymbolValue\", " +
                "alternateSymbolValue2 = \"/stock/leafNode/alternateSymbolValue2\")))\n" +
                "define stream FooStream (volume int, price float, symbol string, alternateSymbolValue string, " +
                "alternateSymbolValue2 string); " +
                "define stream BarStream (volume int, price float, symbol string, alternateSymbolValue string, " +
                "alternateSymbolValue2 string); ";
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
                            org.junit.Assert.assertEquals(55.6f, event.getData(1));
                            org.junit.Assert.assertEquals("WSO22", event.getData(3));
                            break;
                        case 2:
                            org.junit.Assert.assertEquals(55.6f, event.getData(1));
                            org.junit.Assert.assertEquals("WSO44", event.getData(3));
                            break;
                        case 3:
                            org.junit.Assert.assertEquals(99.9f, event.getData(1));
                            org.junit.Assert.assertNull(event.getData(3));
                            break;
                        default:
                            org.junit.Assert.fail();
                    }
                }
            }
        });
        executionPlanRuntime.start();
        InMemoryBroker.publish("stock", "<?xml version=\"1.0\"?>" +
                "<portfolio>" +
                "  <stock>" +
                "    <volume>100</volume>" +
                "    <symbol>WSO2</symbol>" +
                "    <price>55.6</price>" +
                "    <leafNode>" +
                "         <alternateSymbolValue>WSO22</alternateSymbolValue>" +
                "         <alternateSymbolValue2>WSO33</alternateSymbolValue2>" +
                "    </leafNode>" +
                "    <leafNode>" +
                "         <alternateSymbolValue>WSO44</alternateSymbolValue>" +
                "         <alternateSymbolValue2>WSO55</alternateSymbolValue2>" +
                "    </leafNode>" +
                "  </stock>" +
                "  <stock exchange=\"nyse\">" +
                "    <volume>200</volume>" +
                "    <symbol>IBM</symbol>" +
                "    <price>99.9</price>" +
                "  </stock>" +
                "</portfolio>");
        //assert event count
        AssertJUnit.assertEquals("Number of events", 3, count.get());
        executionPlanRuntime.shutdown();
        siddhiManager.shutdown();
    }

    @Test
    public void testXmlInputMappingCustomOnNoChildrenInXpath2() throws Exception {
        log.info("Verify xml mapping when multiple enclosing tags are present with no child elements 2");

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "@source(type='inMemory', topic='stock', @map(type='xml', " +
                "enclosing.element=\"/events/wrapper/event\", enclosing.element.as.event=\"true\", " +
                "@attributes(volume = \"volume\", price = \"price\", symbol = \"symbol\"))) " +
                "define stream FooStream (volume int, price float, symbol string); " +
                "define stream BarStream (volume int, price float, symbol string); ";

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
                            org.junit.Assert.assertEquals("WSO2", event.getData(2));
                            break;
                        case 2:
                            org.junit.Assert.assertEquals("IBM", event.getData(2));
                            break;
                        default:
                            org.junit.Assert.fail();
                    }
                }
            }
        });
        executionPlanRuntime.start();
        InMemoryBroker.publish("stock", ("<?xml version=\"1.0\"?>" +
                "<events><wrapper>\n"
                + "    <event>\n"
                + "        <symbol>WSO2</symbol>\n"
                + "        <price>55.6</price>\n"
                + "        <volume>100</volume>\n"
                + "    </event>\n"
                + "    <event>\n"
                + "        <symbol>IBM</symbol>\n"
                + "        <price>66.6</price>\n"
                + "        <volume>200</volume>\n"
                + "    </event>\n"
                + "</wrapper></events>\n").getBytes(StandardCharsets.UTF_8));
        //assert event count
        AssertJUnit.assertEquals("Number of events", 2, count.get());
        executionPlanRuntime.shutdown();
        siddhiManager.shutdown();
    }
}
