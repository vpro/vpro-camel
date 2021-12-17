package nl.vpro.camel;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.Set;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.commons.io.FileUtils;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.scp.common.ScpTransferEventListener;
import org.apache.sshd.scp.server.ScpCommandFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.config.keys.AuthorizedKeysAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Log4j2
public class ScpComponentTest extends CamelTestSupport {

    @Produce("direct:testinput")
    protected ProducerTemplate input;

    private final String FILENAME = "test123";

    private ScpEventListener scpEventListener;

    private SshServer sshd;

    private Path scpRoot;

    @BeforeEach
    public void setup() throws IOException {
        scpEventListener = new ScpEventListener();
        scpRoot = Files.createTempDirectory("scp_tmp");
        sshd = configureSshServer(scpRoot, scpEventListener);
        sshd.start();

    }

    @AfterEach
    public void shutdown() throws IOException {
        // Cleanup files
        FileUtils.deleteDirectory(scpRoot.toFile());
        sshd.stop();
    }

    @Test
    public void testScp() throws Exception {
        addDefaultRoutesBuilder();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        input.sendBodyAndHeader(new ByteArrayInputStream("some input".getBytes(StandardCharsets.UTF_8)),
            Exchange.FILE_NAME, FILENAME);
        assertMockEndpointsSatisfied();
        // Make sure the file was correctly transferred
        assertEquals("/" + FILENAME, scpEventListener.getFile().toString());
        assertEquals(10, scpEventListener.getLength());
    }

    @Test
    public void testIncorrectHeader() throws Exception {
        addDefaultRoutesBuilder();

        assertThrows(CamelExecutionException.class, () -> {

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
        );
    }

    @Test
    public void testIncorrectHost() throws Exception {
        addRoutesBuilder("incorrecthost", 2222, "test", "src/test/resources/id_rsa");
        assertThrows(CamelExecutionException.class, () -> {
            try {
                sshd.start();
                MockEndpoint mock = getMockEndpoint("mock:result");
                mock.expectedMinimumMessageCount(1);
                input.sendBodyAndHeader(new ByteArrayInputStream("some input".getBytes(StandardCharsets.UTF_8)),
                    Exchange.FILE_NAME, FILENAME);
            } finally {
                sshd.stop();
            }
        });
    }

    @Test
    public void testIncorrectPort() throws Exception {
        addRoutesBuilder("localhost", 2223, "test", "src/test/resources/id_rsa");
        assertThrows(CamelExecutionException.class, () -> {

            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.expectedMinimumMessageCount(1);
            input.sendBodyAndHeader(new ByteArrayInputStream("some input".getBytes(StandardCharsets.UTF_8)),
                Exchange.FILE_NAME, FILENAME);
        });
    }

    @Test
    public void testIncorrectUser() throws Exception {
        addRoutesBuilder("localhost", 2222, "billgates", "src/test/resources/id_rsa");
        assertThrows(CamelExecutionException.class, () -> {

            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.expectedMinimumMessageCount(1);
            input.sendBodyAndHeader(new ByteArrayInputStream("some input".getBytes(StandardCharsets.UTF_8)),
                Exchange.FILE_NAME, FILENAME);
        });
    }

    @Test
    public void testIncorrectKey() throws Exception {
        addRoutesBuilder("localhost", 2222, "test", "src/test/resources/wrong_id_rsa");
        assertThrows(CamelExecutionException.class, () -> {
            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.expectedMinimumMessageCount(1);
            input.sendBodyAndHeader(new ByteArrayInputStream("some input".getBytes(StandardCharsets.UTF_8)),
                Exchange.FILE_NAME, FILENAME);
        });

    }

    private void addDefaultRoutesBuilder() throws Exception {
        addRoutesBuilder("localhost", 2222, "test", "src/test/resources/id_rsa");
    }

    private void addRoutesBuilder(final String host, final int port,
                                  final String user, final String privateKeyFile) throws Exception {

        Files.setPosixFilePermissions(Paths.get(privateKeyFile), PosixFilePermissions.fromString("r--------"));

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("direct:testinput")
                    .to("scp://" + host
                            + "?remotePath=/&port=" + port
                            + "&remoteUser=" + user
                            + "&privateKeyFile=" + privateKeyFile
                            + "&strictHostKeyChecking=no" // TODO, make test for yes
                    )
                    .to("mock:result");
            }
        });
    }

    /**
     * Setup SSH-server in memory
     * @param scpRoot Root-directory for the SSH-server (linked to local file system)
     * @param scpTransferEventListener Event listener for transferred files
     * @return SshServer
     */
    private SshServer configureSshServer(final Path scpRoot,
                                         final ScpTransferEventListener scpTransferEventListener) {
        final SshServer sshd = SshServer.setUpDefaultServer();
        log.debug("Ssh-server filesystem-root is {}", scpRoot);
        sshd.setHost("localhost");
        sshd.setPort(2222);
        log.debug("Setup SCP-endpoint at localhost:2222");
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        ScpCommandFactory factory = new ScpCommandFactory.Builder().build();
        factory.addEventListener(scpTransferEventListener);
        sshd.setCommandFactory(factory);
        // Pass key for test-purposes
        sshd.setPublickeyAuthenticator(
            new UserAuthorizedKeysAuthenticator(new File("src/test/resources/id_rsa.pub").toPath(), "test"));
        sshd.setFileSystemFactory(new VirtualFileSystemFactory(scpRoot));
        return sshd;
    }

    /**
     * Get file and length from SCP'd files
     */
    @Getter
    private static class ScpEventListener implements ScpTransferEventListener {

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

    /**
     * This class allows us to check if the user that wants to connect is the correct user
     */
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
