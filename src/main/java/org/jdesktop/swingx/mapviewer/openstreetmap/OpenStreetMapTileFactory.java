package org.jdesktop.swingx.mapviewer.openstreetmap;

import org.jdesktop.swingx.mapviewer.DefaultTileFactory;
import org.jdesktop.swingx.mapviewer.TileFactoryInfo;

/**
 * @author fgotusso <fgotusso@swissms.ch>
 */
public class OpenStreetMapTileFactory extends DefaultTileFactory {
    private static final int MAX_LEVEL = 17;

    private static class OpenStreetMapTileFactoryInfo extends TileFactoryInfo {
        public OpenStreetMapTileFactoryInfo() {
            super(1, MAX_LEVEL - 2, MAX_LEVEL, 256, true, true, "http://tile.openstreetmap.org", "x", "y", "z");
        }

        @Override
        public String getTileUrl(final int x, final int y, int zoom) {
            zoom = MAX_LEVEL - zoom;
            final String url = this.baseURL + "/" + zoom + "/" + x + "/" + y + ".png";
            return url;
        }
    };

    public OpenStreetMapTileFactory() {
        // License only allows 2 threads per client
        super(new OpenStreetMapTileFactoryInfo(), 2);
    }
}
