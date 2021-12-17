package nl.vpro.camel;

import java.io.*;
import java.nio.file.Files;
import lombok.extern.log4j.Log4j2;
import nl.vpro.logging.Log4j2OutputStream;
import nl.vpro.util.*;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.commons.io.IOUtils;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * The Scp producer.
 */
@Log4j2
public class ScpProducer extends DefaultProducer {

    private static final OutputStream STDOUT = Log4j2OutputStream.debug(log, true);
    private static final OutputStream STDERR = Log4j2OutputStream.error(log, true);

    private final ScpEndpoint endpoint;
    private final CommandExecutor scp = CommandExecutorImpl
        .builder()
        .executablesPaths("/local/bin/scp", "/usr/bin/scp")
        .build();



    public ScpProducer(ScpEndpoint endpoint) throws IOException {
        super(endpoint);
        this.endpoint = endpoint;

    }

    public void process(@NonNull Exchange exchange) throws Exception {
        final InputStream inputStream = exchange.getIn().getBody(InputStream.class);
        final String fileName = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
        if (fileName == null || fileName.equals("")) {
            throw new IllegalArgumentException("Filename can't be empty");
        }
        send(inputStream, fileName);
    }



    private void send(@NonNull final InputStream inputStream, @NonNull final String fileName) throws IOException {
        final File sourceFile = File.createTempFile(ScpProducer.class.getName(), "tmp");
        try (OutputStream outputStream = new FileOutputStream(sourceFile)) {
            IOUtils.copy(inputStream, outputStream);
        }
        try {
            final String remoteHostName = endpoint.getRemoteHostName();
            final String remoteUser = endpoint.getRemoteUser();
            final String remotePath = endpoint.getRemotePath();
            final int port = endpoint.getPort();

            int exitCode = scp.execute(
                STDOUT,
                STDERR,
                "-P",
                "" + port,
                "-i",
                endpoint.getActualPrivateKeyFile().getAbsolutePath(),
                "-o",
                "ConnectTimeout " + (endpoint.getConnectTimeout() / 1000),
                "-o",
                "StrictHostKeyChecking " + endpoint.getStrictHostKeyChecking().name(),
                "-o",
                "UserKnownHostsFile " + endpoint.getUserHosts(),
                sourceFile.getAbsolutePath(), // source
                remoteUser + "@" + remoteHostName + ":" + remotePath + "/" + fileName // destination,
            );
            if (exitCode != 0) {
                throw new Ssh.SshException(exitCode, "Failed to send input stream to  " + remoteHostName + ":" + remotePath + " and port " + port);
            }
        }
        finally {
            Files.delete(sourceFile.toPath());
        }
    }


}


