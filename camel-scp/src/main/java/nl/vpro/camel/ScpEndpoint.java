package nl.vpro.camel;

import lombok.Getter;
import lombok.Setter;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.*;

/**
 * Represents a Scp endpoint.
 */
@UriEndpoint(firstVersion = "1.3.2-SNAPSHOT", scheme = "scp", title = "Scp", syntax="scp:username@host:port/privateKeyFile",
             producerOnly = true, label = "file")
@UriParams
@Getter
@Setter
public class ScpEndpoint extends DefaultEndpoint {
    @UriParam(
        label = "Remote host",
        description = "Remote host where the file(s) should be transferred to"
    )
    @Metadata(required = "true")
    private String remoteHostName;
    @UriParam(
        label = "Remote user",
        description = "Remote user with access to the remote host"
    )
    @Metadata(required = "true")
    private String remoteUser;
    @UriParam(
        label = "Remote path",
        description = "Location on the remote host where the file(s) should be transferred to"
    )
    @Metadata(required = "true")
    private String remotePath;
    @UriParam(
        label = "port",
        defaultValue = "22",
        description = "Port to connect on"
    )
    private String port = "22";
    @UriParam
    @Metadata(required = "true")
    private String privateKeyFile;

    public ScpEndpoint(String uri, String remaining, ScpComponent component) {
        super(uri, component);
        this.remoteHostName = remaining;
    }

    public Producer createProducer() throws Exception {
        return new ScpProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("This component does not support consuming from this endpoint");
    }

    public boolean isSingleton() {
        return true;
    }
}
