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

import org.apache.camel.*;
import org.apache.camel.support.DefaultEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a filewatcher endpoint.
 */
class FileWatcherEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(FileWatcherEndpoint.class);


    FileWatcherEndpoint(CamelContext camelContext, String uri, FileWatcherComponent component) {
        super(uri, component);
        setCamelContext(camelContext);
    }

    @Override
    public Producer createProducer() {
        throw new UnsupportedOperationException("The file watcher endpoint can only create consumers, not producers");
    }

    @Override
    public Consumer createConsumer(Processor processor) {
        FileWatcherConsumer consumer = new FileWatcherConsumer(this, processor);
        LOG.info("Created {}", consumer);
        return consumer;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
