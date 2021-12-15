package nl.vpro.camel;

import lombok.Getter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.swing.filechooser.FileSystemView;

import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.sshd.common.file.nativefs.NativeFileSystemFactory;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.scp.common.ScpTransferEventListener;
import org.apache.sshd.scp.server.ScpCommandFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.config.keys.AuthorizedKeysAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.Test;

public class ScpComponentTest extends CamelTestSupport {
    @Produce(uri = "direct:testinput")
    protected ProducerTemplate input;

    private final String FILENAME = "test123";

    @Test
    public void testScp() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);


        input.sendBodyAndHeader(new ByteArrayInputStream("hoi".getBytes(StandardCharsets.UTF_8)), Exchange.FILE_NAME, FILENAME);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testScpSsh() throws Exception {
        SshServer sshd = SshServer.setUpDefaultServer();
        sshd.setHost("localhost");
        sshd.setPort(2222);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        ScpCommandFactory factory = new ScpCommandFactory.Builder().build();
        ScpEventListener ScpEventListener = new ScpEventListener();
        factory.addEventListener(ScpEventListener);
        sshd.setCommandFactory(factory);
        sshd.setPublickeyAuthenticator(new AuthorizedKeysAuthenticator(new File("src/main/resources/authorized_keys").toPath()));
        Path temp_dir = Files.createTempDirectory("scp_tmp");
        log.info("{}", temp_dir);
        sshd.setFileSystemFactory(new VirtualFileSystemFactory(temp_dir));
        try {
            log.info("Started sshd-server");
            sshd.start();
            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.expectedMinimumMessageCount(1);
            input.sendBodyAndHeader(new ByteArrayInputStream("hoi".getBytes(StandardCharsets.UTF_8)), Exchange.FILE_NAME, "test123");
            assertMockEndpointsSatisfied();
            // Make sure the file was correctly transferred
            assertEquals("/" + FILENAME, ScpEventListener.getFile().toString());
            assertEquals(3, ScpEventListener.getLength());
            recursiveDeleteOnExit(temp_dir);
        }
        finally {
            log.info("stop sshd-server");
            sshd.stop();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:testinput")
                    .to("scp://localhost?remotePath=/&port=2222&remoteUser=test&privateKeyFile=src/main/resources/id_rsa") // TODO: determine parameters to pass by
                    .to("mock:result");
            }
        };
    }

    private static void recursiveDeleteOnExit(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file,
                                             @SuppressWarnings("unused") BasicFileAttributes attrs) {
                file.toFile().deleteOnExit();
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult preVisitDirectory(Path dir,
                                                     @SuppressWarnings("unused") BasicFileAttributes attrs) {
                dir.toFile().deleteOnExit();
                return FileVisitResult.CONTINUE;
            }
        });
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
}
