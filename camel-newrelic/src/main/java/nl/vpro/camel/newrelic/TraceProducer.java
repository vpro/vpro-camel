/**
 * Copyright (C) 2014 All rights reserved
 * VPRO The Netherlands
 */
package nl.vpro.camel.newrelic;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.newrelic.api.agent.NewRelic;

/**
 * @author Roelof Jan Koekoek
 * @since 1.1
 */
public class TraceProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(TraceProducer.class);

    private TraceEndpoint endpoint;

    public TraceProducer(Endpoint endpoint) {
        super(endpoint);
        this.endpoint = (TraceEndpoint)endpoint;
    }

    public void process(Exchange exchange) throws Exception {
        if(endpoint.getConsumers().isEmpty()) {
            LOG.warn("No consumers available on endpoint: {} to process: {}", endpoint, exchange);
        } else {
            for(DefaultConsumer consumer : endpoint.getConsumers()) {
                String traceId = endpoint.getEndpointKey();

                long start = System.nanoTime();
                consumer.getProcessor().process(exchange);
                long elapsedTime = System.nanoTime() - start;
                NewRelic.recordMetric(traceId, elapsedTime);

                NewRelic.incrementCounter(traceId);
            }
        }
    }
}
