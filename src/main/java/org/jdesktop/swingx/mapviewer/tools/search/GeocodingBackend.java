package org.jdesktop.swingx.mapviewer.tools.search;

import java.io.IOException;
import java.util.List;

/**
 * @author fgotusso <fgotusso@swissms.ch>
 */
public interface GeocodingBackend {
    public List<GeocodingResult> geocode(String query) throws IOException;
}
