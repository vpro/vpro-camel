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

import java.io.File;
import java.io.FileWriter;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.*;

import static org.apache.camel.component.mock.MockEndpoint.assertIsSatisfied;
import static org.apache.camel.component.mock.MockEndpoint.resetMocks;
import static org.apache.camel.test.junit5.TestSupport.assertInMessageBodyEquals;
import static org.apache.camel.test.junit5.TestSupport.assertInMessageHeader;
import static org.junit.jupiter.api.Assertions.assertNull;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class FileWatcherComponentTest extends CamelTestSupport {

    private static final String filePath = "fileWatcherTest.txt";

    private static File file;

    @BeforeAll
    public static void createFile() throws Exception {
        file = new File(filePath);
        if (file.exists()) {
            throw new IllegalStateException("A test(?) file exists already at: " + file.getAbsolutePath());
        }

        file.createNewFile();
        FileWriter writer = new FileWriter(file, true);
        writer.write("Hello world");
        writer.close();
    }

    @AfterAll
    public static void deleteFile() {
        if (file.exists()) {
            file.delete();
        }
    }

    @Test
    public void testFileWatcherForAllEvents() throws Exception {
        // Started

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        List<Exchange> exchanges = mock.getExchanges();

        assertIsSatisfied(context);
        assertInMessageHeader(exchanges.get(0), "fileWatchEvent", "STARTED");
        assertInMessageBodyEquals(exchanges.get(0), "Hello world");

        // Updated
        resetMocks(context);

        FileWriter w1 = new FileWriter(file);
        w1.write("Update");
        w1.close();

        mock.expectedMinimumMessageCount(1);
        exchanges = mock.getExchanges();

        assertIsSatisfied(context);
        assertInMessageHeader(exchanges.get(0), "fileWatchEvent", "UPDATED");
        assertInMessageBodyEquals(exchanges.get(0), "Update");

        // Deleted
        resetMocks(context);

        file.delete();

        mock.expectedMinimumMessageCount(1);
        exchanges = mock.getExchanges();

        assertIsSatisfied(context);
        assertInMessageHeader(exchanges.get(0), "fileWatchEvent", "DELETED");
        assertNull(exchanges.get(0).getIn().getBody());

        // Created
        resetMocks(context);

        FileWriter w2 = new FileWriter(file);
        w2.write("Created");
        w2.close();

        mock.expectedMinimumMessageCount(1);
        exchanges = mock.getExchanges();

        assertIsSatisfied(context);
        assertInMessageHeader(exchanges.get(0), "fileWatchEvent", "CREATED");
        assertInMessageBodyEquals(exchanges.get(0), "Created");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("filewatcher:" + filePath)
                    .to("mock:result");
            }
        };
    }
}
