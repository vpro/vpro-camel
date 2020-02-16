/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.vpro.camel;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

public class FileWatcherComponentTest extends CamelTestSupport {

    private static final String filePath = "fileWatcherTest.txt";

    private static File file;

    @BeforeClass
    public static void createFile() throws Exception {
        file = new File(filePath);
        if(file.exists()) {
            throw new IllegalStateException("A test(?) file exists already at: " + file.getAbsolutePath());
        }

        file.createNewFile();
        FileWriter writer = new FileWriter(file, true);
        writer.write("Hello world");
        writer.close();
    }

    @AfterClass
    public static void deleteFile() {
        if(file.exists()) {
            file.delete();
        }
    }

    @Test
    public void testFileWatcherForAllEvents() throws Exception {
        // Started
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        List<Exchange> exchanges = mock.getExchanges();

        assertMockEndpointsSatisfied();
        assertInMessageHeader(exchanges.get(0), "fileWatchEvent", "STARTED");
        assertInMessageBodyEquals(exchanges.get(0), "Hello world");

        // Updated
        resetMocks();

        FileWriter w1 = new FileWriter(file);
        w1.write("Update");
        w1.close();

        mock.expectedMinimumMessageCount(1);
        exchanges = mock.getExchanges();

        assertMockEndpointsSatisfied();
        assertInMessageHeader(exchanges.get(0), "fileWatchEvent", "UPDATED");
        assertInMessageBodyEquals(exchanges.get(0), "Update");

        // Deleted
        resetMocks();

        file.delete();

        mock.expectedMinimumMessageCount(1);
        exchanges = mock.getExchanges();

        assertMockEndpointsSatisfied();
        assertInMessageHeader(exchanges.get(0), "fileWatchEvent", "DELETED");
        assertNull(exchanges.get(0).getIn().getBody());

        // Created
        resetMocks();

        FileWriter w2 = new FileWriter(file);
        w2.write("Created");
        w2.close();

        mock.expectedMinimumMessageCount(1);
        exchanges = mock.getExchanges();

        assertMockEndpointsSatisfied();
        assertInMessageHeader(exchanges.get(0), "fileWatchEvent", "CREATED");
        assertInMessageBodyEquals(exchanges.get(0), "Created");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("filewatcher:" + filePath)
                    .to("mock:result");
            }
        };
    }
}
