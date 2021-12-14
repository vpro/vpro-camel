package nl.vpro.camel;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class ScpComponentTest extends CamelTestSupport {
    @Produce(uri = "direct:testinput")
    protected ProducerTemplate input;

    @Test
    public void testScp() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);


        input.sendBodyAndHeader(new ByteArrayInputStream("hoi".getBytes(StandardCharsets.UTF_8)), Exchange.FILE_NAME, "test123");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:testinput")
                    .to("scp://upload-testsites.omroep.nl?remotePath=tmp&remoteUser=poms2signiant&privateKeyFile=/home/molenaar/.ssh/id_rsa") // TODO: determine parameters to pass by
                    .to("mock:result");
            }
        };
    }
}
