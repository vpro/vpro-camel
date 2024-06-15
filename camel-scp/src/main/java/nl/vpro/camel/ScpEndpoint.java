package nl.vpro.camel;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import org.apache.camel.*;
import org.apache.camel.spi.*;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Represents a Scp endpoint.
 * <p>
 * This is completely inspired by the default camel-scp endpoint. It just uses the much more robust, and better tested 'scp' command line tool.
 * <p>
 * 'jsch' e.g. simply didn't support the server in our use case.
 *
 */
@UriEndpoint(
    firstVersion = "1.3.2",
    scheme = "scp",
    title = "Scp",
    syntax="scp:username@host:port/privateKeyFile",
    producerOnly = true,
    category = Category.FILE
)
@UriParams
@Getter
@Setter
@Slf4j
public class ScpEndpoint extends DefaultEndpoint {
    @UriParam(
        label = "Remote host",
        description = "Remote host where the file(s) should be transferred to"
    )
    @Metadata(required = true)
    private String remoteHostName;

    @UriParam(
        label = "Remote user",
        description = "Remote user with access to the remote host"
    )
    @Metadata(required = true)
    private String remoteUser;

    @UriParam(
        label = "Remote path",
        description = "Location on the remote host where the file(s) should be transferred to"
    )
    @Metadata(required = true)
    private String remotePath;

    @UriParam(
        label = "port",
        defaultValue = "22",
        description = "Port to connect on"
    )
    private int port = 22;

    @UriParam
    @Metadata(required = false)
    private String privateKeyFile;


    @UriParam
    @Metadata(required = false)
    private byte[] privateKeyBytes;


    @UriParam
    @Metadata(required = false, defaultValue = "no")
    private YesNo strictHostKeyChecking = YesNo.no;


    @UriParam
    @Metadata(required = false)
    private String connectTimeout = "10000";

    @UriParam
    @Metadata(required = false)
    private String knownHostsFile;


    @UriParam
    @Metadata(required = false)
    private boolean useUserKnownHostsFile = true;

    @UriParam
    @Metadata(required = false, defaultValue = "/local/bin/scp,/usr/bin/scp")
    private String scpExecutables =  "/local/bin/scp,/usr/bin/scp";

    @Getter
    @MonotonicNonNull
    private File actualPrivateKeyFile;

    @Getter
    @MonotonicNonNull
    private String userHosts;


    public ScpEndpoint(CamelContext context, String uri, String remaining, ScpComponent component) {
        super(uri, component);
        this.remoteHostName = remaining;
        setCamelContext(context);
    }


    @Override
    public Producer createProducer() throws Exception {
        if (actualPrivateKeyFile == null) {
            this.actualPrivateKeyFile = createActualPrivateKeyFile();
            this.userHosts = createUserHosts();
        }
        return new ScpProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) {
        throw new UnsupportedOperationException("This component does not support consuming from this endpoint");
    }

    @Override
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

    private File createActualPrivateKeyFile() throws IOException {
        final File privateKeyFile;

        if (StringUtils.isNotBlank(getPrivateKeyFile())) {
            if (getPrivateKeyFile().startsWith("env:")) {
                privateKeyFile = File.createTempFile(ScpProducer.class.getSimpleName(), "id");
                String propName = getPrivateKeyFile().substring(("env:".length()));
                String value = System.getProperty(propName, System.getenv(propName));
                if (value == null) {
                    throw new IllegalArgumentException("No system property found " + propName);
                }
                try (FileOutputStream outputStream = new FileOutputStream(privateKeyFile)){
                    IOUtils.write(value.getBytes(StandardCharsets.UTF_8), outputStream);
                }
                Files.setPosixFilePermissions(privateKeyFile.toPath(), PosixFilePermissions.fromString("r--------"));
                log.info("Created  private key file {} ({} bytes)", privateKeyFile, privateKeyFile.length());
            } else {
                privateKeyFile = new File(getPrivateKeyFile());
            }
            if (!privateKeyFile.exists() || !privateKeyFile.isFile()) {
                throw new IllegalArgumentException("Private key file " + privateKeyFile.getAbsolutePath() + " does not exist or is not a file");
            }
        } else {
            byte[] privateKeyBytes = getPrivateKeyBytes();
            if (privateKeyBytes == null){
                throw new IllegalStateException("No private key file nor private key bytes configured");
            }
            privateKeyFile = File.createTempFile(ScpProducer.class.getSimpleName(), "id");
            privateKeyFile.deleteOnExit();
        }
        return privateKeyFile;
    }

    private String createUserHosts() throws IOException {
        String userHosts = getKnownHostsFile();
        if (userHosts == null) {
            if (isUseUserKnownHostsFile()) {
                userHosts = System.getProperty("user.home") + "/.ssh/known_hosts";
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
