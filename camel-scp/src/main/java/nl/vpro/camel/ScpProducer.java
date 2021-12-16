package nl.vpro.camel;

import lombok.extern.log4j.Log4j2;

import java.io.*;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Level;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import nl.vpro.logging.Log4j2OutputStream;
import nl.vpro.util.CommandExecutor;
import nl.vpro.util.CommandExecutorImpl;
import nl.vpro.util.Ssh;

/**
 * The Scp producer.
 */
@Log4j2
public class ScpProducer extends DefaultProducer {
    private ScpEndpoint endpoint;

    private static final OutputStream STDOUT = Log4j2OutputStream.debug(log, true);
    private static final OutputStream STDERR = Log4j2OutputStream.error(log, true);

    private final CommandExecutor scp = CommandExecutorImpl
        .builder()
        .executablesPaths("/local/bin/scp", "/usr/bin/scp")
        .build();

    public ScpProducer(ScpEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void process(@NonNull Exchange exchange) throws Exception {
        final InputStream inputStream = exchange.getIn().getBody(InputStream.class);
        final String fileName = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
        // TODO: handle header = null
        send(inputStream, fileName);
    }

    private void send(@NonNull final InputStream inputStream, @NonNull final String fileName) throws IOException {
        File sourceFile = File.createTempFile(ScpProducer.class.getName(), "tmp");
        try (OutputStream outputStream = new FileOutputStream(sourceFile)) {
            IOUtils.copy(inputStream, outputStream);
        }
        try {
            final String remoteHostName = endpoint.getRemoteHostName();
            final String remoteUser = endpoint.getRemoteUser();
            final String remotePath = endpoint.getRemotePath();
            final String port = endpoint.getPort();
            final File privateKeyFile = new File(endpoint.getPrivateKeyFile());
            if (!privateKeyFile.exists() || !privateKeyFile.isFile()) {
                throw new IllegalArgumentException("Private key file " + privateKeyFile.getAbsolutePath() + " does not exist or is not a file");
            }
            if (scp.execute(
                STDOUT,
                STDERR,
                "-P",
                port,
                "-i",
                privateKeyFile.getAbsolutePath(),
                "-o",
                "ConnectTimeout 20",
                "-o",
                "StrictHostKeyChecking no",
                "-o",
                "UserKnownHostsFile /dev/null",
                sourceFile.getAbsolutePath(), // source
                remoteUser + "@" + remoteHostName + ":" + remotePath + "/" + fileName // destination,
            ) != 0) {
                throw new Ssh.SshException("Failed to send input stream to  " + remoteHostName + ":" + remotePath + " and port " + port);
            }
        }
        finally {
            sourceFile.delete();
        }
    }

}


