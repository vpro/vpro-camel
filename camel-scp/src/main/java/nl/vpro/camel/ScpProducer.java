package nl.vpro.camel;

import java.io.*;
import java.nio.file.Files;
import lombok.extern.slf4j.Slf4j;
import nl.vpro.logging.LoggerOutputStream;
import nl.vpro.util.*;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.apache.commons.io.IOUtils;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * The Scp producer.
 */
@Slf4j
public class ScpProducer extends DefaultProducer {

    private static final OutputStream STDOUT = LoggerOutputStream.debug(log, true);
    private static final OutputStream STDERR = LoggerOutputStream.error(log, true);

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
        exchange.getIn().setHeader(Exchange.FILE_NAME_PRODUCED, send(inputStream, fileName));
    }



    private String send(@NonNull final InputStream inputStream, @NonNull final String fileName) throws IOException {
        final File sourceFile = File.createTempFile(ScpProducer.class.getName(), "tmp");
        try (OutputStream outputStream = new FileOutputStream(sourceFile)) {
            IOUtils.copy(inputStream, outputStream);
        }
        try {
            final String remoteHostName = endpoint.getRemoteHostName();
            final String remoteUser = endpoint.getRemoteUser();
            final String remotePath = endpoint.getRemotePath();
            final int port = endpoint.getPort();

            final String produced = remotePath + "/"+ fileName;
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
                remoteUser + "@" + remoteHostName + ":" + produced // destination,
            );
            if (exitCode != 0) {
                throw new Ssh.SshException(exitCode, "Failed to send input stream to  " + remoteHostName + ":" + remotePath + " and port " + port);
            }
            return produced;
        }
        finally {
            Files.delete(sourceFile.toPath());
        }
    }


}


