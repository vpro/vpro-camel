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
package nl.vpro.camel.newrelic;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.*;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;

/**
 * @author Roelof Jan Koekoek
 * @since 1.1
 */
class TraceEndpoint extends DefaultEndpoint {
    private final CopyOnWriteArrayList<DefaultConsumer> consumers = new CopyOnWriteArrayList<>();

    TraceEndpoint(CamelContext context, String uri, TraceComponent component) {
        super(uri, component);
        setCamelContext(context);
    }

    @Override
    public Producer createProducer() {
        return new TraceProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) {
        return new TraceConsumer(this, processor);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public List<DefaultConsumer> getConsumers() {
        return consumers;
    }
}
