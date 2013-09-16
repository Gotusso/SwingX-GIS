package org.jdesktop.swingx.mapviewer.tools.search;

import org.jdesktop.swingx.mapviewer.GeoBounds;
import org.jdesktop.swingx.mapviewer.GeoPosition;

/**
 * @author fgotusso <fgotusso@swissms.ch>
 */
public class GeocodingResult {
    private final String displayName;
    private final GeoPosition position;
    private final GeoBounds bounds;

    public GeocodingResult(String displayName, GeoPosition position, GeoBounds bounds) {
        this.displayName = displayName;
        this.position = position;
        this.bounds = bounds;
    }

    public String getDisplayName() {
        return displayName;
    }

    public GeoPosition getPosition() {
        return position;
    }

    public GeoBounds getBounds() {
        return bounds;
    }
}
