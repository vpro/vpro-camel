package nl.vpro.camel;

import java.io.*;
import java.nio.file.Files;
import java.util.Objects;
import lombok.extern.log4j.Log4j2;
import nl.vpro.logging.Log4j2OutputStream;
import nl.vpro.util.*;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
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

    private final File privateKeyFile;
    private final String userHosts;

    public ScpProducer(ScpEndpoint endpoint) throws IOException {
        super(endpoint);
        this.endpoint = endpoint;
        this.privateKeyFile = getPrivateKeyFile(endpoint);
        this.userHosts = getUserHosts(endpoint);
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
            final String port = endpoint.getPort();

            int exitCode = scp.execute(
                STDOUT,
                STDERR,
                "-P",
                port,
                "-i",
                privateKeyFile.getAbsolutePath(),
                "-o",
                "ConnectTimeout " + (endpoint.getConnectTimeout() / 1000),
                "-o",
                "StrictHostKeyChecking " + endpoint.getStrictHostKeyChecking().name(),
                "-o",
                "UserKnownHostsFile " + userHosts,
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

    private static File getPrivateKeyFile(ScpEndpoint endpoint) throws IOException {
        final File privateKeyFile;

        if (StringUtils.isNotBlank(endpoint.getPrivateKeyFile())) {
            privateKeyFile = new File(endpoint.getPrivateKeyFile());
            if (!privateKeyFile.exists() || !privateKeyFile.isFile()) {
                throw new IllegalArgumentException("Private key file " + privateKeyFile.getAbsolutePath() + " does not exist or is not a file");
            }
        } else {
            byte[] privateKeyBytes = endpoint.getPrivateKeyBytes();
            if (privateKeyBytes == null){
                throw new IllegalStateException("No private key file nor private key bytes configured");
            }
            privateKeyFile = File.createTempFile(ScpProducer.class.getSimpleName(), "id");
            privateKeyFile.deleteOnExit();
        }
        return privateKeyFile;
    }

    private static String getUserHosts(ScpEndpoint endpoint) throws IOException {
          String userHosts = endpoint.getKnownHostsFile();
        if (userHosts == null) {
            if (endpoint.isUseUserKnownHostsFile()) {
                userHosts = System.getProperty("user.home") + ".ssh/known_hosts";
            } else {
                userHosts = "/dev/null";
            }
        } else if (userHosts.startsWith("classpath:")) {
            final File tmpFile = File.createTempFile(ScpProducer.class.getSimpleName() + ".userHosts", ".tmp");
            tmpFile.deleteOnExit();
            try (FileOutputStream output = new FileOutputStream(tmpFile)) {
                IOUtils.copy(Objects.requireNonNull(ScpProducer.class.getResourceAsStream("/" + userHosts.substring("classpath:".length()))), output);
            }
            userHosts = tmpFile.getAbsolutePath();
            log.info("Temporary created {}", userHosts);
        } else {
            log.info("Using {}", userHosts);
        }
        return userHosts;
    }
}


