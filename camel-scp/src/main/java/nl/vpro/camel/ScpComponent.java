package nl.vpro.camel;

import java.util.Map;
import org.apache.camel.Endpoint;
import org.apache.camel.support.DefaultComponent;

/**
 * Represents the component that manages {@link ScpEndpoint}.
 */
public class ScpComponent extends DefaultComponent {

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Endpoint endpoint = new ScpEndpoint(getCamelContext(), uri, remaining, this);
        setProperties(endpoint, parameters);
        return endpoint;
    }
}
