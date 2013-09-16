package org.jdesktop.swingx.mapviewer.tools.search;

import java.util.HashMap;
import java.util.Map;

/**
 * @author fgotusso <fgotusso@swissms.ch>
 */
public abstract class AbstractGeocodingBackend implements GeocodingBackend  {
    private Map<String, String> providerParameters;

    public AbstractGeocodingBackend() {
        providerParameters = new HashMap<String, String>();
    }

    public void putProviderParameter(String key, String value) {
        providerParameters.put(key, value);
    }

    public String removeProviderParameter(String key) {
        return providerParameters.remove(key);
    }

    protected Map<String, String> getProviderParameters() {
        return providerParameters;
    }
}
