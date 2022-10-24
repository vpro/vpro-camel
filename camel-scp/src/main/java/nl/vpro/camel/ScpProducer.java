package nl.vpro.camel;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Files;
import java.time.Duration;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.commons.io.IOUtils;
import org.checkerframework.checker.nullness.qual.NonNull;

import nl.vpro.logging.LoggerOutputStream;
import nl.vpro.util.*;

import static org.apache.commons.text.StringEscapeUtils.escapeXSI;

/**
 * The Scp producer.
 */
@Slf4j
public class ScpProducer extends DefaultProducer {

    private static final OutputStream STDOUT = LoggerOutputStream.debug(log, true);
    private static final OutputStream STDERR = LoggerOutputStream.error(log, true);

    private final ScpEndpoint endpoint;
    private final CommandExecutor scp;

    public ScpProducer(ScpEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        scp = CommandExecutorImpl.builder()
            .executablesPaths(endpoint.getScpExecutables().split("\\s*,\\s*"))
            .logger(log)
            .build();

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
                "ConnectTimeout " + (TimeUtils.parseDuration(endpoint.getConnectTimeout()).orElse(Duration.ofSeconds(10)).getSeconds()),
                "-o",
                "StrictHostKeyChecking " + endpoint.getStrictHostKeyChecking().name(),
                "-o",
                "UserKnownHostsFile " + endpoint.getUserHosts(),
                sourceFile.getAbsolutePath(), // source
                remoteUser + "@" + remoteHostName + ":" + escapeXSI(produced) // destination,
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


