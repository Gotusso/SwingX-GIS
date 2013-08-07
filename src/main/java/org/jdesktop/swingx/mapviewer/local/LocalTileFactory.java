package org.jdesktop.swingx.mapviewer.local;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;

import javax.swing.SwingUtilities;

import org.jdesktop.swingx.mapviewer.DefaultTileFactory;
import org.jdesktop.swingx.mapviewer.Tile;
import org.jdesktop.swingx.mapviewer.TileFactoryInfo;

import org.jdesktop.swingx.mapviewer.util.ImageCache;

/**
 * Tile factory to paint tile images instead of asking a server for it.
 * The requested tile uses the hash of the service to differentiate
 * between multiple requests, so if your subclass have a state don't forget
 * to implement hashcode and equals!
 *
 * @author fgotusso <fgotusso@swissms.ch>
 */
public abstract class LocalTileFactory extends DefaultTileFactory {
    public static final int DEFAULT_TILE_SIZE = 256;

    /////////////////////////////////////////////////////////////////////////////////////////////////////
    // MAGIC!!! DO NOT TOUCH THIS LEVELS! There is a nasty bug around the lib related with this levels //
    /////////////////////////////////////////////////////////////////////////////////////////////////////
    public static final int MINIMUM_ZOOM_LEVEL = 0;
    public static final int MAXIMUM_ZOOM_LEVEL = 15;
    public static final int TOTAL_ZOOM_LEVEL = 17;

    protected final BufferedImage EMPTY_IMAGE;

    protected static class ESBTileFactoryInfo extends TileFactoryInfo {
        private Object _state;

        public ESBTileFactoryInfo(final int tileSize) {
            // tile size and x/y orientation is r2l & t2b
            // Same as Open Street Maps
            super(MINIMUM_ZOOM_LEVEL, MAXIMUM_ZOOM_LEVEL, TOTAL_ZOOM_LEVEL, tileSize, true, true, "", "x", "y", "zoom");
        }

        private void setState(final Object object) {
            _state = object;
        }

        @Override
        public String getTileUrl(final int x, final int y, final int zoom) {
            // URL not needed, communication will go directly to the ESB.
            // However, swingx-ws uses it as key each requested tile, so it must
            // be unique for each tile.
            // On this way, the cache doesn't need to be clear on each state
            // changes, like when changing a filter.
            return super.getTileUrl(x, y, zoom) + "&state=" + _state.hashCode();
        }
    }

    public LocalTileFactory() {
        this(DEFAULT_TILE_SIZE);
    }

    public LocalTileFactory(final int tileSize) {
        super(new ESBTileFactoryInfo(tileSize));

        EMPTY_IMAGE = new BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_ARGB);

        // HACK: Should be immutable, but it's the only way to do this
        ((ESBTileFactoryInfo) getInfo()).setState(this);

        // By default deactivate the image cache on this layer.
        // This factory is often used with CompoundTileFactory to overlap
        // images on top of a OpenStreetMaps or google maps tiles, so
        // that factory will store and cache the final composite.
        final ImageCache cache = getImageCache();
        cache.setCompressedCacheSize(0);
        cache.setUncompressedCacheSize(0);
    }

    /**
     * @inheritDoc
     */
    @Override
    protected Runnable createTileRunner(final Tile tile) {
        return new LocalTileRunner();
    }

    public class LocalTileRunner implements Runnable {
        @Override
        public void run() {
            final BlockingQueue<Tile> queue = LocalTileFactory.this.getTileQueue();

            if (queue.isEmpty()) {
                return;
            }

            /*
             * 3 strikes and you're out. Attempt to load the url. If it fails,
             * decrement the number of tries left and try again. Log failures.
             * If I run out of try s just get out. This way, if there is some
             * kind of serious failure, I can get out and let other tiles try to
             * load.
             */
            final Tile tile = queue.remove();

            final int x = tile.getX();
            final int y = tile.getY();
            final int zoom = tile.getZoom();
            int tries = 3;

            while (!tile.isLoaded() && tries > 0) {

                try {
                    final BufferedImage image = getTileImage(x, y, zoom);

                    if (image != null) {
                        final BufferedImage i = image;
                        SwingUtilities.invokeAndWait(new Runnable() {
                            @Override
                            public void run() {
                                tile.setImage(i);
                                tile.setLoaded(true);
                            }
                        });
                    }
                    else {
                        tries--;
                    }
                }
                catch (final Throwable ex) {
                    getErrorHandler().tileLoadingFailed(tile, ex);

                    final Object oldError = tile.getError();
                    tile.setError(ex);
                    tile.firePropertyChangeOnEDT(LOADING_ERROR_PROPERTY, oldError, ex);
                    if (tries == 0) {
                        tile.firePropertyChangeOnEDT(UNRECOVERABLE_ERROR_PROPERTY, null, ex);
                    }
                    else {
                        tries--;
                    }
                }
            }

            if (! tile.isLoaded()) {
                try {
                    final BufferedImage i = getErrorImage(x, y, zoom);
                    SwingUtilities.invokeAndWait(new Runnable() {
                        @Override
                        public void run() {
                            tile.setImage(i);
                            tile.setLoaded(true);
                        }
                    });
                }
                catch (final Exception ex) {
                    getErrorHandler().tileLoadingFailed(tile, ex);
                }
            }

            tile.setLoading(false);
        }
    }

    protected abstract BufferedImage getTileImage(final int x, final int y, final int zoom) throws IOException;

    protected BufferedImage getErrorImage(final int x, final int y, final int zoom) {
        final int size = getTileSize(zoom);
        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);

        // Border
        g.drawLine(0, 0, image.getWidth(), 0);
        g.drawLine(image.getWidth() - 1, 0, image.getWidth() - 1, image.getHeight());
        g.drawLine(image.getWidth(), image.getHeight() - 1, 0, image.getHeight() - 1);
        g.drawLine(0, image.getHeight(), 0, 0);
        // Cross
        g.drawLine(0, 0, image.getWidth(), image.getHeight());
        g.drawLine(0, image.getHeight(), image.getWidth(), 0);

        g.dispose();

        return image;
    }

    @Override
    public abstract boolean equals(Object other);

    @Override
    public abstract int hashCode();
}
