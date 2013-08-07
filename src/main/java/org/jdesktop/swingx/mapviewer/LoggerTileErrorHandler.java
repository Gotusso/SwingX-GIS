package org.jdesktop.swingx.mapviewer;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
* @author fgotusso <fgotusso@swissms.ch>
*/
public class LoggerTileErrorHandler implements TileErrorHandler {
    private static final Logger LOG = Logger.getLogger(LoggerTileErrorHandler.class.getName());

    @Override
    public void tileLoadingFailed(final Tile tile, final byte[] data) {
        LOG.log(Level.INFO, "Failed to load a tile at url: " + tile.getURL());
    }

    @Override
    public void tileLoadingFailed(final Tile tile, final Throwable throwable) {
        LOG.log(Level.SEVERE, "Failed to load a tile at url: " + tile.getURL(), throwable);
    }
}
