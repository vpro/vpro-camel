package nl.vpro.camel;

import lombok.Getter;
import lombok.Setter;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * Represents a Scp endpoint.
 */
@UriEndpoint(firstVersion = "1.3.2-SNAPSHOT", scheme = "scp", title = "Scp", syntax="scp:username@host:port/privateKeyFile",
             producerOnly = true, label = "file")
@Getter
@Setter
public class ScpEndpoint extends DefaultEndpoint {
    @UriParam(
        label = "host",
        description = "Host (either remote or local) where the file(s) should be transferred to"
    )
    @Metadata(required = "true")
    private String host;
    @UriParam(
        label = "port",
        defaultValue = "22",
        description = "Port to connect on"
    )
    private int port;
    @UriParam
    private String directoryName;
    @UriParam
    private String privateKeyFile;

    public ScpEndpoint(String uri, String remaining, ScpComponent component) {
        super(uri, component);
        this.host = remaining;
    }

    public Producer createProducer() throws Exception {
        return new ScpProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new ScpConsumer(this, processor);
    }

    public boolean isSingleton() {
        return true;
    }
}
