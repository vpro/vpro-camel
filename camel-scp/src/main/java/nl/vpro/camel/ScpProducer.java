package nl.vpro.camel;

import lombok.extern.slf4j.Slf4j;

import java.io.OutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.vpro.logging.LoggerOutputStream;
import nl.vpro.util.CommandExecutor;
import nl.vpro.util.CommandExecutorImpl;

/**
 * The Scp producer.
 */
@Slf4j
public class ScpProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(ScpProducer.class);
    private ScpEndpoint endpoint;

    private static final OutputStream STDOUT = LoggerOutputStream.debug(log);
    private static final OutputStream STDERR = LoggerOutputStream.error(log);

    private final CommandExecutor scp = CommandExecutorImpl
        .builder()
        .executablesPaths("/local/bin/scp", "/usr/bin/scp")
        .build();

    public ScpProducer(ScpEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void process(Exchange exchange) throws Exception {
        System.out.println(exchange.getIn().getBody());
        final String host = endpoint.getHost();
        final int port = endpoint.getPort();
        final String privateKeyFile = endpoint.getPrivateKeyFile();
        // TODO: configure endpoint-parameters and match 'm with scp.execute
        System.out.println(host);
        System.out.println(port);
        System.out.println(privateKeyFile);

//        scp.execute(
//            STDOUT,
//            STDERR,
//            "-i",
//            "/home/molenaar/.ssh/id_rsa", // TODO: fix dynamic path
//            "/home/molenaar/Downloads/Pajama_Sam.png", // source
//            "poms2signiant@upload-testsites.omroep.nl:tmp" // destination
//        );
    }

}
