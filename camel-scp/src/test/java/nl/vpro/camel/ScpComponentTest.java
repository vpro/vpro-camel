package nl.vpro.camel;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class ScpComponentTest extends CamelTestSupport {

    @Test
    public void testScp() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);       
        
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("scp:path")
                  .to("scp://poms2someuser@someurlhere?privateKeyFile=/test") // TODO: determine parameters to pass by
                  .to("mock:result");
            }
        };
    }
}
