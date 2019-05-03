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

package io.siddhi.extension.map.xml.sinkmapper;

import io.siddhi.core.SiddhiAppRuntime;
import io.siddhi.core.SiddhiManager;
import io.siddhi.core.stream.input.InputHandler;
import io.siddhi.core.stream.output.sink.InMemorySink;
import io.siddhi.core.util.transport.InMemoryBroker;
import io.siddhi.query.api.SiddhiApp;
import io.siddhi.query.api.annotation.Annotation;
import io.siddhi.query.api.definition.Attribute;
import io.siddhi.query.api.definition.StreamDefinition;
import io.siddhi.query.api.execution.query.Query;
import io.siddhi.query.api.execution.query.input.stream.InputStream;
import io.siddhi.query.api.execution.query.selection.Selector;
import io.siddhi.query.api.expression.Variable;
import org.apache.log4j.Logger;
import org.testng.AssertJUnit;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class XMLSinkMapperWithSiddhiQueryAPITestCase {

    private static final Logger log = Logger.getLogger(XMLSinkMapperWithSiddhiQueryAPITestCase.class);
    private AtomicInteger wso2Count = new AtomicInteger(0);
    private AtomicInteger ibmCount = new AtomicInteger(0);

    @BeforeMethod
    public void init() {
        wso2Count.set(0);
        ibmCount.set(0);
    }

    //    from FooStream
    //    select symbol,price,volume
    //    publish inMemory options ("topic", "{{symbol}}")
    //    map xml
    @Test
    public void testXMLSinkmapperWithDefaultMapping() throws InterruptedException {
        log.info("Test default xml mapping with Siddhi Query API");
        List<Object> onMessageList = new ArrayList<Object>();

        InMemoryBroker.Subscriber subscriberWSO2 = new InMemoryBroker.Subscriber() {
            @Override
            public void onMessage(Object msg) {
                wso2Count.incrementAndGet();
                onMessageList.add(msg);
            }

            @Override
            public String getTopic() {
                return "WSO2";
            }
        };

        InMemoryBroker.Subscriber subscriberIBM = new InMemoryBroker.Subscriber() {
            @Override
            public void onMessage(Object msg) {
                ibmCount.incrementAndGet();
                onMessageList.add(msg);
            }

            @Override
            public String getTopic() {
                return "IBM";
            }
        };

        //subscribe to "inMemory" broker per topic
        InMemoryBroker.subscribe(subscriberWSO2);
        InMemoryBroker.subscribe(subscriberIBM);

        StreamDefinition streamDefinition = StreamDefinition.id("FooStream")
                .attribute("symbol", Attribute.Type.STRING)
                .attribute("price", Attribute.Type.FLOAT)
                .attribute("volume", Attribute.Type.INT);

        StreamDefinition outputDefinition = StreamDefinition.id("BarStream")
                .attribute("symbol", Attribute.Type.STRING)
                .attribute("price", Attribute.Type.FLOAT)
                .attribute("volume", Attribute.Type.INT)
                .annotation(Annotation.annotation("sink")
                        .element("type", "inMemory")
                        .element("topic", "{{symbol}}")
                        .annotation(Annotation.annotation("map")
                                .element("type", "xml")));

        Query query = Query.query();
        query.from(
                InputStream.stream("FooStream")
        );
        query.select(
                Selector.selector().select(new Variable("symbol")).select(new Variable(
                        "price")).select(new Variable("volume"))
        );
        query.insertInto("BarStream");

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("sink:inMemory", InMemorySink.class);
        SiddhiApp executionPlan = new SiddhiApp("ep1");
        executionPlan.defineStream(streamDefinition);
        executionPlan.defineStream(outputDefinition);
        executionPlan.addQuery(query);
        SiddhiAppRuntime executionPlanRuntime = siddhiManager.createSiddhiAppRuntime(executionPlan);
        InputHandler stockStream = executionPlanRuntime.getInputHandler("FooStream");

        executionPlanRuntime.start();
        stockStream.send(new Object[]{"WSO2", 55.6f, 100L});
        stockStream.send(new Object[]{"IBM", 75.6f, 100L});
        stockStream.send(new Object[]{"WSO2", 57.6f, 100L});
        Thread.sleep(100);

        //assert event count
        AssertJUnit.assertEquals("Incorrect number of events consumed!", 2, wso2Count.get());
        AssertJUnit.assertEquals("Incorrect number of events consumed!", 1, ibmCount.get());
        //assert default mapping
        AssertJUnit.assertEquals("Incorrect mapping!", "<events><event><symbol>WSO2</symbol>" +
                "<price>55.6</price><volume>100</volume></event></events>", onMessageList.get(0).toString());
        AssertJUnit.assertEquals("Incorrect mapping!", "<events><event><symbol>IBM</symbol>" +
                "<price>75.6</price><volume>100</volume></event></events>", onMessageList.get(1).toString());
        AssertJUnit.assertEquals("Incorrect mapping!", "<events><event><symbol>WSO2</symbol>" +
                "<price>57.6</price><volume>100</volume></event></events>", onMessageList.get(2).toString());
        executionPlanRuntime.shutdown();

        //unsubscribe from "inMemory" broker per topic
        InMemoryBroker.unsubscribe(subscriberWSO2);
        InMemoryBroker.unsubscribe(subscriberIBM);
    }

    //    from FooStream
    //    select symbol,price
    //    publish inMemory options ("topic", "{{symbol}}")
    //    map xml custom
    @Test
    public void testXMLSinkmapperWithCustomMapping() throws InterruptedException {
        log.info("Test custom xml mapping with Siddhi Query API");
        List<Object> onMessageList = new ArrayList<Object>();

        InMemoryBroker.Subscriber subscriberWSO2 = new InMemoryBroker.Subscriber() {
            @Override
            public void onMessage(Object msg) {
                wso2Count.incrementAndGet();
                onMessageList.add(msg);
            }

            @Override
            public String getTopic() {
                return "WSO2";
            }
        };

        InMemoryBroker.Subscriber subscriberIBM = new InMemoryBroker.Subscriber() {
            @Override
            public void onMessage(Object msg) {
                ibmCount.incrementAndGet();
                onMessageList.add(msg);
            }

            @Override
            public String getTopic() {
                return "IBM";
            }
        };

        //subscribe to "inMemory" broker per topic
        InMemoryBroker.subscribe(subscriberWSO2);
        InMemoryBroker.subscribe(subscriberIBM);

        StreamDefinition streamDefinition = StreamDefinition.id("FooStream")
                .attribute("symbol", Attribute.Type.STRING)
                .attribute("price", Attribute.Type.FLOAT)
                .attribute("volume", Attribute.Type.INT);

        StreamDefinition outputDefinition = StreamDefinition.id("BarStream")
                .attribute("symbol", Attribute.Type.STRING)
                .attribute("price", Attribute.Type.FLOAT)
                .attribute("volume", Attribute.Type.INT)
                .annotation(Annotation.annotation("sink")
                        .element("type", "inMemory")
                        .element("topic", "{{symbol}}")
                        .annotation(Annotation.annotation("map")
                                .element("type", "xml")
                                .annotation(Annotation.annotation("payload")
                                        .element("<StockData><Symbol>{{symbol}}</Symbol><Price>" +
                                                "{{price}}</Price></StockData>"))));

        Query query = Query.query();
        query.from(
                InputStream.stream("FooStream")
        );
        query.select(
                Selector.selector().select(new Variable("symbol")).select(new Variable(
                        "price")).select(new Variable("volume"))
        );
        query.insertInto("BarStream");

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("sink:inMemory", InMemorySink.class);
        SiddhiApp executionPlan = new SiddhiApp("ep1");
        executionPlan.defineStream(streamDefinition);
        executionPlan.defineStream(outputDefinition);
        executionPlan.addQuery(query);
        SiddhiAppRuntime executionPlanRuntime = siddhiManager.createSiddhiAppRuntime(executionPlan);
        InputHandler stockStream = executionPlanRuntime.getInputHandler("FooStream");

        executionPlanRuntime.start();
        stockStream.send(new Object[]{"WSO2", 55.6f, 100L});
        stockStream.send(new Object[]{"IBM", 75.6f, 100L});
        stockStream.send(new Object[]{"WSO2", 57.6f, 100L});
        Thread.sleep(100);

        //assert event count
        AssertJUnit.assertEquals("Incorrect number of events consumed!", 2, wso2Count.get());
        AssertJUnit.assertEquals("Incorrect number of events consumed!", 1, ibmCount.get());
        //assert custom xml
        AssertJUnit.assertEquals("Incorrect mapping!", "<StockData><Symbol>WSO2</Symbol>" +
                "<Price>55.6</Price></StockData>", onMessageList.get(0).toString());
        AssertJUnit.assertEquals("Incorrect mapping!", "<StockData><Symbol>IBM</Symbol>" +
                "<Price>75.6</Price></StockData>", onMessageList.get(1).toString());
        AssertJUnit.assertEquals("Incorrect mapping!", "<StockData><Symbol>WSO2</Symbol>" +
                "<Price>57.6</Price></StockData>", onMessageList.get(2).toString());
        executionPlanRuntime.shutdown();

        //unsubscribe from "inMemory" broker per topic
        InMemoryBroker.unsubscribe(subscriberWSO2);
        InMemoryBroker.unsubscribe(subscriberIBM);
    }

    @Test
    public void testXMLOutputCustomMappingWithXMLPrefix() throws InterruptedException {
        log.info("Test custom xml mapping with SiddhiQL. Here, XML prefix and a suffix is being provided for mapping.");
        List<Object> onMessageList = new ArrayList<Object>();

        InMemoryBroker.Subscriber subscriberWSO2 = new InMemoryBroker.Subscriber() {
            @Override
            public void onMessage(Object msg) {
                wso2Count.incrementAndGet();
                onMessageList.add(msg);
            }

            @Override
            public String getTopic() {
                return "WSO2";
            }
        };

        InMemoryBroker.Subscriber subscriberIBM = new InMemoryBroker.Subscriber() {
            @Override
            public void onMessage(Object msg) {
                ibmCount.incrementAndGet();
                onMessageList.add(msg);
            }

            @Override
            public String getTopic() {
                return "IBM";
            }
        };

        //subscribe to "inMemory" broker per topic
        InMemoryBroker.subscribe(subscriberWSO2);
        InMemoryBroker.subscribe(subscriberIBM);

        StreamDefinition streamDefinition = StreamDefinition.id("FooStream")
                .attribute("symbol", Attribute.Type.STRING)
                .attribute("price", Attribute.Type.FLOAT)
                .attribute("volume", Attribute.Type.INT);

        StreamDefinition outputDefinition = StreamDefinition.id("BarStream")
                .attribute("symbol", Attribute.Type.STRING)
                .attribute("price", Attribute.Type.FLOAT)
                .attribute("volume", Attribute.Type.INT)
                .annotation(Annotation.annotation("sink")
                        .element("type", "inMemory")
                        .element("topic", "{{symbol}}")
                        .annotation(Annotation.annotation("map")
                                .element("type", "xml")
                                .element("enclosing.element", "<portfolio>")
                                .annotation(Annotation.annotation("payload")
                                        .element("<StockData><Symbol>{{symbol}}</Symbol><Price>{{price}}" +
                                                "</Price></StockData>"))));

        Query query = Query.query();
        query.from(
                InputStream.stream("FooStream")
        );
        query.select(
                Selector.selector().select(new Variable("symbol")).select(new Variable(
                        "price")).select(new Variable("volume"))
        );
        query.insertInto("BarStream");

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("sink:inMemory", InMemorySink.class);
        SiddhiApp executionPlan = new SiddhiApp("ep1");
        executionPlan.defineStream(streamDefinition);
        executionPlan.defineStream(outputDefinition);
        executionPlan.addQuery(query);
        SiddhiAppRuntime executionPlanRuntime = siddhiManager.createSiddhiAppRuntime(executionPlan);
        InputHandler stockStream = executionPlanRuntime.getInputHandler("FooStream");

        executionPlanRuntime.start();
        stockStream.send(new Object[]{"WSO2", 55.6f, 100L});
        stockStream.send(new Object[]{"IBM", 75.6f, 100L});
        stockStream.send(new Object[]{"WSO2", 57.6f, 100L});
        Thread.sleep(100);

        //assert event count
        AssertJUnit.assertEquals("Incorrect number of events consumed!", 2, wso2Count.get());
        AssertJUnit.assertEquals("Incorrect number of events consumed!", 1, ibmCount.get());
        //assert custom xml
        AssertJUnit.assertEquals("Incorrect mapping!", "<portfolio><StockData><Symbol>WSO2</Symbol>" +
                "<Price>55.6</Price></StockData></portfolio>", onMessageList.get(0).toString());
        AssertJUnit.assertEquals("Incorrect mapping!", "<portfolio><StockData><Symbol>IBM</Symbol>" +
                "<Price>75.6</Price></StockData></portfolio>", onMessageList.get(1).toString());
        AssertJUnit.assertEquals("Incorrect mapping!", "<portfolio><StockData><Symbol>WSO2</Symbol>" +
                "<Price>57.6</Price></StockData></portfolio>", onMessageList.get(2).toString());
        executionPlanRuntime.shutdown();

        //unsubscribe from "inMemory" broker per topic
        InMemoryBroker.unsubscribe(subscriberWSO2);
        InMemoryBroker.unsubscribe(subscriberIBM);
    }

    @Test
    public void testXMLOutputCustomMappingWithXMLValidation() throws InterruptedException {
        log.info("Test custom xml mapping where XML validation is enabled.");
        List<Object> onMessageList = new ArrayList<Object>();

        InMemoryBroker.Subscriber subscriberWSO2 = new InMemoryBroker.Subscriber() {
            @Override
            public void onMessage(Object msg) {
                wso2Count.incrementAndGet();
                onMessageList.add(msg);
            }

            @Override
            public String getTopic() {
                return "WSO2";
            }
        };

        InMemoryBroker.Subscriber subscriberIBM = new InMemoryBroker.Subscriber() {
            @Override
            public void onMessage(Object msg) {
                ibmCount.incrementAndGet();
                onMessageList.add(msg);
            }

            @Override
            public String getTopic() {
                return "IBM";
            }
        };

        //subscribe to "inMemory" broker per topic
        InMemoryBroker.subscribe(subscriberWSO2);
        InMemoryBroker.subscribe(subscriberIBM);

        StreamDefinition streamDefinition = StreamDefinition.id("FooStream")
                .attribute("symbol", Attribute.Type.STRING)
                .attribute("price", Attribute.Type.FLOAT)
                .attribute("volume", Attribute.Type.INT);

        StreamDefinition outputDefinition = StreamDefinition.id("BarStream")
                .attribute("symbol", Attribute.Type.STRING)
                .attribute("price", Attribute.Type.FLOAT)
                .attribute("volume", Attribute.Type.INT)
                .annotation(Annotation.annotation("sink")
                        .element("type", "inMemory")
                        .element("topic", "{{symbol}}")
                        .annotation(Annotation.annotation("map")
                                .element("type", "xml")
                                .element("enclosing.element", "<portfolio>")
                                .element("validate.xml", "true")
                                .annotation(Annotation.annotation("payload")
                                        .element("<StockData><Symbol>{{symbol}}</Symbol><Price>{{price}}" +
                                                "</Price></StockData>"))));

        Query query = Query.query();
        query.from(
                InputStream.stream("FooStream")
        );
        query.select(
                Selector.selector().select(new Variable("symbol")).select(new Variable(
                        "price")).select(new Variable("volume"))
        );
        query.insertInto("BarStream");

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("sink:inMemory", InMemorySink.class);
        SiddhiApp executionPlan = new SiddhiApp("ep1");
        executionPlan.defineStream(streamDefinition);
        executionPlan.defineStream(outputDefinition);
        executionPlan.addQuery(query);
        SiddhiAppRuntime executionPlanRuntime = siddhiManager.createSiddhiAppRuntime(executionPlan);
        InputHandler stockStream = executionPlanRuntime.getInputHandler("FooStream");

        executionPlanRuntime.start();
        stockStream.send(new Object[]{"WSO2", 55.6f, 100L});
        stockStream.send(new Object[]{"IBM", 75.6f, 100L});
        stockStream.send(new Object[]{"WSO2", 57.6f, 100L});
        Thread.sleep(100);

        //assert event count
        AssertJUnit.assertEquals("Incorrect number of events consumed!", 2, wso2Count.get());
        AssertJUnit.assertEquals("Incorrect number of events consumed!", 1, ibmCount.get());
        //assert custom xml
        AssertJUnit.assertEquals("Incorrect mapping!", "<portfolio><StockData><Symbol>WSO2</Symbol>" +
                "<Price>55.6</Price></StockData></portfolio>", onMessageList.get(0).toString());
        AssertJUnit.assertEquals("Incorrect mapping!", "<portfolio><StockData><Symbol>IBM</Symbol>" +
                "<Price>75.6</Price></StockData></portfolio>", onMessageList.get(1).toString());
        AssertJUnit.assertEquals("Incorrect mapping!", "<portfolio><StockData><Symbol>WSO2</Symbol>" +
                "<Price>57.6</Price></StockData></portfolio>", onMessageList.get(2).toString());
        executionPlanRuntime.shutdown();

        //unsubscribe from "inMemory" broker per topic
        InMemoryBroker.unsubscribe(subscriberWSO2);
        InMemoryBroker.unsubscribe(subscriberIBM);
    }

    @Test
    public void negativeTestXMLOutputCustomMappingWithXMLValidation() throws InterruptedException {
        log.info("Negative test case for testing whether events are dropped when XML validation is enabled" +
                " and a malformed XML event is generated as a result of output mapping.");
        List<Object> onMessageList = new ArrayList<Object>();

        InMemoryBroker.Subscriber subscriberWSO2 = new InMemoryBroker.Subscriber() {
            @Override
            public void onMessage(Object msg) {
                wso2Count.incrementAndGet();
                onMessageList.add(msg);
            }

            @Override
            public String getTopic() {
                return "WSO2";
            }
        };

        InMemoryBroker.Subscriber subscriberIBM = new InMemoryBroker.Subscriber() {
            @Override
            public void onMessage(Object msg) {
                ibmCount.incrementAndGet();
                onMessageList.add(msg);
            }

            @Override
            public String getTopic() {
                return "IBM";
            }
        };

        //subscribe to "inMemory" broker per topic
        InMemoryBroker.subscribe(subscriberWSO2);
        InMemoryBroker.subscribe(subscriberIBM);

        StreamDefinition streamDefinition = StreamDefinition.id("FooStream")
                .attribute("symbol", Attribute.Type.STRING)
                .attribute("price", Attribute.Type.FLOAT)
                .attribute("volume", Attribute.Type.INT);

        StreamDefinition outputDefinition = StreamDefinition.id("BarStream")
                .attribute("symbol", Attribute.Type.STRING)
                .attribute("price", Attribute.Type.FLOAT)
                .attribute("volume", Attribute.Type.INT)
                .annotation(Annotation.annotation("sink")
                        .element("type", "inMemory")
                        .element("topic", "{{symbol}}")
                        .annotation(Annotation.annotation("map")
                                .element("type", "xml")
                                .element("enclosing.element", "<portfolio")
                                .element("validate.xml", "true")
                                .annotation(Annotation.annotation("payload")
                                        .element("<StockData><Symbol>{{symbol}}</Symbol><Price>" +
                                                "{{price}}</Price></StockData>"))));

        Query query = Query.query();
        query.from(
                InputStream.stream("FooStream")
        );
        query.select(
                Selector.selector().select(new Variable("symbol")).select(new Variable(
                        "price")).select(new Variable("volume"))
        );
        query.insertInto("BarStream");

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("sink:inMemory", InMemorySink.class);
        SiddhiApp executionPlan = new SiddhiApp("ep1");
        executionPlan.defineStream(streamDefinition);
        executionPlan.defineStream(outputDefinition);
        executionPlan.addQuery(query);
        SiddhiAppRuntime executionPlanRuntime = siddhiManager.createSiddhiAppRuntime(executionPlan);
        InputHandler stockStream = executionPlanRuntime.getInputHandler("FooStream");

        executionPlanRuntime.start();
        stockStream.send(new Object[]{"WSO2", 55.6f, 100L});
        stockStream.send(new Object[]{"IBM", 75.6f, 100L});
        stockStream.send(new Object[]{"WSO2", 57.6f, 100L});
        Thread.sleep(100);

        //assert event count
        AssertJUnit.assertEquals("Incorrect number of events consumed!", 0, wso2Count.get());
        AssertJUnit.assertEquals("Incorrect number of events consumed!", 0, ibmCount.get());
        executionPlanRuntime.shutdown();

        //unsubscribe from "inMemory" broker per topic
        InMemoryBroker.unsubscribe(subscriberWSO2);
        InMemoryBroker.unsubscribe(subscriberIBM);
    }
}
