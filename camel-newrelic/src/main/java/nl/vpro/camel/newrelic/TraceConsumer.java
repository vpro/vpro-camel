/**
 * Copyright (C) 2014 All rights reserved
 * VPRO The Netherlands
 */
package nl.vpro.camel.newrelic;

import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;

/**
 * @author Roelof Jan Koekoek
 * @since 1.1
 */
public class TraceConsumer extends DefaultConsumer {

    private TraceEndpoint endpoint;

    public TraceConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = (TraceEndpoint)endpoint;
    }

    @Override
    public void start() throws Exception {
        if(!endpoint.getConsumers().contains(this)) {
            if(!endpoint.getConsumers().isEmpty()) {
                throw new IllegalStateException("Endpoint " + endpoint.getEndpointUri()
                    + " only allows 1 active consumer but you attempted to start a 2 nd consumer.");
            }
            endpoint.getConsumers().add(this);
            super.start();
        }
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        endpoint.getConsumers().remove(this);
    }
}
