package nl.vpro.camel;

import lombok.Getter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.io.FileUtils;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.util.GenericUtils;
import org.apache.sshd.scp.common.ScpTransferEventListener;
import org.apache.sshd.scp.server.ScpCommandFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.config.keys.AuthorizedKeysAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ScpComponentTest extends CamelTestSupport {
    @Produce(uri = "direct:testinput")
    protected ProducerTemplate input;
    private final String FILENAME = "test123";
    private ScpEventListener scpEventListener;
    private SshServer sshd;
    private Path scpRoot;

    @Before
    public void setup() throws IOException {
        scpEventListener = new ScpEventListener();
        scpRoot = Files.createTempDirectory("scp_tmp");
        sshd = configureSshServer(scpRoot, scpEventListener);
    }

    @After
    public void shutdown() throws IOException {
        // Cleanup files
        FileUtils.deleteDirectory(scpRoot.toFile());
    }

    @Test
    public void testScp() throws Exception {
        addDefaultRoutesBuilder();
        try {
            log.info("Start ssh-server");
            sshd.start();
            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.expectedMinimumMessageCount(1);
            input.sendBodyAndHeader(new ByteArrayInputStream("some input".getBytes(StandardCharsets.UTF_8)),
                Exchange.FILE_NAME, FILENAME);
            assertMockEndpointsSatisfied();
            // Make sure the file was correctly transferred
            assertEquals("/" + FILENAME, scpEventListener.getFile().toString());
            assertEquals(10, scpEventListener.getLength());
        } finally {
            sshd.stop();
        }
    }

    @Test(expected = CamelExecutionException.class)
    public void testIncorrectHeader() throws Exception {
        addDefaultRoutesBuilder();
        try {
            sshd.start();
            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.expectedMinimumMessageCount(1);
            input.sendBodyAndHeader(new ByteArrayInputStream("some input".getBytes(StandardCharsets.UTF_8)),
                "nonexistent", FILENAME);
        } finally {
            sshd.stop();
        }
    }

    @Test(expected = CamelExecutionException.class)
    public void testIncorrectHost() throws Exception {
        addRoutesBuilder("incorrecthost", 2222, "test", "src/main/resources/id_rsa");
        try {
            sshd.start();
            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.expectedMinimumMessageCount(1);
            input.sendBodyAndHeader(new ByteArrayInputStream("some input".getBytes(StandardCharsets.UTF_8)),
                Exchange.FILE_NAME, FILENAME);
        } finally {
            sshd.stop();
        }
    }

    @Test(expected = CamelExecutionException.class)
    public void testIncorrectPort() throws Exception {
        addRoutesBuilder("localhost", 2223, "test", "src/main/resources/id_rsa");
        try {
            sshd.start();
            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.expectedMinimumMessageCount(1);
            input.sendBodyAndHeader(new ByteArrayInputStream("some input".getBytes(StandardCharsets.UTF_8)),
                Exchange.FILE_NAME, FILENAME);
        } finally {
            sshd.stop();
        }
    }

    @Test(expected = CamelExecutionException.class)
    public void testIncorrectUser() throws Exception {
        addRoutesBuilder("localhost", 2222, "billgates", "src/main/resources/id_rsa");
        try {
            sshd.start();
            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.expectedMinimumMessageCount(1);
            input.sendBodyAndHeader(new ByteArrayInputStream("some input".getBytes(StandardCharsets.UTF_8)),
                Exchange.FILE_NAME, FILENAME);
        } finally {
            sshd.stop();
        }
    }

    @Test(expected = CamelExecutionException.class)
    public void testIncorrectKey() throws Exception {
        addRoutesBuilder("localhost", 2222, "test", "src/main/resources/wrong_id_rsa");
        try {
            sshd.start();
            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.expectedMinimumMessageCount(1);
            input.sendBodyAndHeader(new ByteArrayInputStream("some input".getBytes(StandardCharsets.UTF_8)),
                Exchange.FILE_NAME, FILENAME);
        } finally {
            sshd.stop();
        }
    }

    private void addDefaultRoutesBuilder() throws Exception {
        addRoutesBuilder("localhost", 2222, "test", "src/main/resources/id_rsa");
    }

    private void addRoutesBuilder(final String host, final int port,
                                  final String user, final String privateKeyFile) throws Exception {
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:testinput")
                    .to("scp://" + host
                        + "?remotePath=/&port=" + port
                        + "&remoteUser=" + user
                        + "&privateKeyFile=" + privateKeyFile)
                    .to("mock:result");
            }
        });
    }

    private SshServer configureSshServer(final Path scpRoot,
                                         final ScpTransferEventListener scpTransferEventListener) throws IOException {
        final SshServer sshd = SshServer.setUpDefaultServer();
        log.debug("Ssh-server filesystem-root is {}", scpRoot);
        sshd.setHost("localhost");
        sshd.setPort(2222);
        log.debug("Setup SCP-endpoint at localhost:2222");
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        ScpCommandFactory factory = new ScpCommandFactory.Builder().build();
        factory.addEventListener(scpTransferEventListener);
        sshd.setCommandFactory(factory);
        sshd.setPublickeyAuthenticator(
            new UserAuthorizedKeysAuthenticator(new File("src/main/resources/id_rsa.pub").toPath(), "test"));
        sshd.setFileSystemFactory(new VirtualFileSystemFactory(scpRoot));
        return sshd;
    }

    @Getter
    private class ScpEventListener implements ScpTransferEventListener {

        private Path file;
        private long length;

        public void startFileEvent(
            Session session, FileOperation op, Path file, long length, Set<PosixFilePermission> perms)
            throws IOException {
            log.info("Incoming file: {}", file);
            this.file = file;
            this.length = length;
        }
    }

    private static class UserAuthorizedKeysAuthenticator extends AuthorizedKeysAuthenticator {
        private final String username;

        public UserAuthorizedKeysAuthenticator(Path file, String username) {
            super(file);
            this.username = username;
        }

        @Override
        protected boolean isValidUsername(String username, ServerSession session) {
            return this.username.equals(username);
        }
    }
}
