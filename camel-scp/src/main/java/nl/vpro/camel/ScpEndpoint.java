package nl.vpro.camel;

import java.util.function.BooleanSupplier;
import lombok.Getter;
import lombok.Setter;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.*;

/**
 * Represents a Scp endpoint.
 *
 * This is completely inspired by the default camel-scp endpoint. It just uses the much more robust, and better tested 'scp' command line tool.
 *
 * 'jsch' e.g. simply didn't support the server in our use case.
 *
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


    @UriParam
    @Metadata(required = "false", defaultValue = "no")
    private YesNo strictHostKeyChecking = YesNo.no;


    @UriParam
    @Metadata(required = "false")
    private int connectTimeout = 10000;



    @UriParam
    @Metadata(required = "false")
    private String knownHostsFile;

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


    /**
     * yes/no, to be compatible with https://camel.apache.org/components/3.7.x/scp-component.html
     */
    public enum YesNo implements BooleanSupplier {
        yes(true),
        no(false);

        final boolean booleanValue;

        YesNo(boolean booleanValue) {
            this.booleanValue = booleanValue;
        }

        @Override
        public boolean getAsBoolean() {
            return booleanValue;
        }
    }
}
