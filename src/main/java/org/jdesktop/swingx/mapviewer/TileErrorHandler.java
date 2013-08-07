package org.jdesktop.swingx.mapviewer;

/**
 * @author fgotusso <fgotusso@swissms.ch>
 */
public interface TileErrorHandler {
    void tileLoadingFailed(Tile tile, byte[] data);
    void tileLoadingFailed(Tile tile, Throwable throwable);
}
