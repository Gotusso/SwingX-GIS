package org.jdesktop.swingx.mapviewer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

import org.jdesktop.swingx.graphics.GraphicsUtilities;
import org.jdesktop.swingx.mapviewer.util.GeoUtil;

/**
 * The <code>AbstractTileFactory</code> provides a basic implementation for the
 * TileFactory.
 */
public abstract class AbstractTileFactory extends TileFactory {

    private static final int THREAD_POOL_SIZE = 4;
    private static final Logger LOG = Logger.getLogger(AbstractTileFactory.class.getName());

    private static WeakReference<ExecutorService> service;

    /**
     * Thread pool for loading the tiles
     */
    private final BlockingQueue<Tile> tileQueue = new PriorityBlockingQueue<Tile>(20, new Comparator<Tile>() {
        @Override
        public int compare(final Tile o1, final Tile o2) {
            if (o1.getPriority() == Tile.Priority.Low && o2.getPriority() == Tile.Priority.High) {
                return 1;
            }
            if (o1.getPriority() == Tile.Priority.High && o2.getPriority() == Tile.Priority.Low) {
                return -1;
            }
            return 0;

        }

        @Override
        public boolean equals(final Object obj) {
            return obj == this;
        }
    });

    private class TileFactoryThreadPool extends ThreadPoolExecutor {
        public TileFactoryThreadPool() {
            super(THREAD_POOL_SIZE, THREAD_POOL_SIZE * 2, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
                private int count = 0;

                @Override
                public Thread newThread(final Runnable r) {
                    final Thread thread = new Thread(r, "Tile-pool-" + count++);
                    thread.setPriority(Thread.MIN_PRIORITY);
                    return thread;
                }
            });

            allowCoreThreadTimeOut(true);
            setRejectedExecutionHandler(new RejectedExecutionHandler() {
                @Override
                public void rejectedExecution(final Runnable r, final ThreadPoolExecutor executor) {
                    synchronized (AbstractTileFactory.this) {
                        // If we haven't space for more tasks in the executor, remove all
                        // the scheduled tiles so only the visible ones will be asked again
                        final RejectedExecutionException ex = new RejectedExecutionException();
                        for (final Tile tile : tileQueue) {
                            tile.setLoading(false);
                            tile.setError(ex);
                            tileMap.remove(tile.getURL());
                            tile.firePropertyChangeOnEDT(TileFactory.UNRECOVERABLE_ERROR_PROPERTY, null, ex);
                        }
                        tileQueue.clear();
                        executor.getQueue().clear();
                    }
                }
            });
        }
    }

    public static interface TileErrorHandler {
        public void tileLoadingFailed(Tile tile, byte[] data);
        public void tileLoadingFailed(Tile tile, Throwable throwable);
    }

    public class DefaultTileErrorHandler implements TileErrorHandler {
        @Override
        public void tileLoadingFailed(final Tile tile, final byte[] data) {
            LOG.log(Level.INFO, "Failed to load a tile at url: " + tile.getURL());
        }

        @Override
        public void tileLoadingFailed(final Tile tile, final Throwable throwable) {
            LOG.log(Level.SEVERE, "Failed to load a tile at url: " + tile.getURL(), throwable);
        }
    }

    /**
     * Creates a new instance of DefaultTileFactory using the spcified
     * TileFactoryInfo
     *
     * @param info
     *            a TileFactoryInfo to configure this TileFactory
     */
    public AbstractTileFactory(final TileFactoryInfo info) {
        super(info);
        errorHandler = new DefaultTileErrorHandler();
    }

    private final Map<String, Tile> tileMap = new HashMap<String, Tile>();

    private TileCache cache = new TileCache();
    private TileErrorHandler errorHandler;

    /**
     * Returns the tile that is located at the given tilePoint for this zoom.
     * For example, if getMapSize() returns 10x20 for this zoom, and the
     * tilePoint is (3,5), then the appropriate tile will be located and
     * returned.
     *
     * @param tilePoint
     * @param zoom
     * @return
     */
    @Override
    public Tile getTile(final int x, final int y, final int zoom) {
        return getTile(x, y, zoom, true);
    }

    private Tile getTile(final int tpx, final int tpy, final int zoom, final boolean eagerLoad) {
        // wrap the tiles horizontally --> mod the X with the max width
        // and use that
        int tileX = tpx;
        final int numTilesWide = (int) getMapSize(zoom).getWidth();
        if (tileX < 0) {
            tileX = numTilesWide - (Math.abs(tileX) % numTilesWide);
        }

        tileX = tileX % numTilesWide;
        final int tileY = tpy;
        final String url = getInfo().getTileUrl(tileX, tileY, zoom);

        Tile.Priority pri = Tile.Priority.High;
        if (!eagerLoad) {
            pri = Tile.Priority.Low;
        }
        Tile tile = null;
        if (!tileMap.containsKey(url)) {
            if (!GeoUtil.isValidTile(tileX, tileY, zoom, getInfo())) {
                tile = new Tile(tileX, tileY, zoom);
                tileMap.put(url, tile);
            }
            else {
                tile = new Tile(tileX, tileY, zoom, url, pri, this);
                tileMap.put(url, tile);
                startLoading(tile);
            }
        }
        else {
            tile = tileMap.get(url);
            // if its in the map but is low and isn't loaded yet
            // but we are in high mode
            if (tile.getPriority() == Tile.Priority.Low && eagerLoad && !tile.isLoaded()) {
                promote(tile);
            }
        }

        return tile;
    }

    public TileCache getTileCache() {
        return cache;
    }

    public synchronized void setTileCache(final TileCache cache) {
        this.cache = cache;
        tileMap.clear();
        tileQueue.clear();
    }

    /** ==== threaded tile loading stuff === */

    /**
     * Subclasses may override this method to provide their own executor
     * services. This method will be called each time a tile needs to be loaded.
     * Implementations should cache the ExecutorService when possible.
     *
     * @return ExecutorService to load tiles with
     */
    protected synchronized ExecutorService getService() {
        if (service == null || service.get() == null) {
            service = new WeakReference<ExecutorService>(new TileFactoryThreadPool());
        }
        return service.get();
    }

    public synchronized void shutdownService() {
        if (service != null || service.get() != null) {
            service.get().shutdownNow();
            service = null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected synchronized void startLoading(final Tile tile) {
        if (tile.isLoading()) {
            return;
        }
        try {
            tile.setLoading(true);
            tileQueue.put(tile);
            getService().submit(createTileRunner(tile));
        }
        catch (final Exception ex) {
            tile.setLoading(false);
            tileQueue.remove(tile);
            ex.printStackTrace();
        }
    }

    /**
     * Subclasses can override this if they need custom TileRunners for some
     * reason
     *
     * @return
     */
    protected Runnable createTileRunner(final Tile tile) {
        return new TileRunner();
    }

    /**
     * Increase the priority of this tile so it will be loaded sooner.
     */
    public synchronized void promote(final Tile tile) {
        if (tileQueue.contains(tile)) {
            try {
                tileQueue.remove(tile);
                tile.setPriority(Tile.Priority.High);
                tileQueue.put(tile);
            }
            catch (final Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public void setTileErrorHandler(final TileErrorHandler handler) {
        errorHandler = handler;
    }

    protected TileErrorHandler gerErrorHandler() {
        return errorHandler;
    }

    protected BlockingQueue<Tile> getTileQueue() {
        return tileQueue;
    }

    /**
     * An inner class which actually loads the tiles. Used by the thread queue.
     * Subclasses can override this if necessary.
     */
    public class TileRunner implements Runnable {
        /**
         * Gets the full URI of a tile.
         *
         * @param tile
         * @throws java.net.URISyntaxException
         * @return
         */
        protected URI getURI(final Tile tile) throws URISyntaxException {
            if (tile.getURL() == null) {
                return null;
            }
            return new URI(tile.getURL());
        }

        /**
         * implementation of the Runnable interface.
         */
        @Override
        public void run() {
            Tile nextTile = null;
            synchronized (AbstractTileFactory.this) {
                nextTile = tileQueue.poll();
                if (nextTile == null) {
                    // Runner is not needed anymore
                    return;
                }
            }

            /*
             * 3 strikes and you're out. Attempt to load the url. If it fails,
             * decrement the number of tries left and try again. Log failures.
             * If I run out of try s just get out. This way, if there is some
             * kind of serious failure, I can get out and let other tiles try to
             * load.
             */
            int trys = 3;
            final Tile tile = nextTile;

            while (!tile.isLoaded() && trys > 0) {
                try {
                    BufferedImage img = null;
                    final URI uri = getURI(tile);
                    img = cache.get(uri);
                    byte[] bimg = null;
                    if (img == null) {
                        bimg = cacheInputStream(uri.toURL());
                        img = GraphicsUtilities.loadCompatibleImage(
                                new ByteArrayInputStream(bimg));// ImageIO.read(new URL(tile.url));
                        if (img == null) {
                            errorHandler.tileLoadingFailed(tile, bimg);
                            trys--;
                        }
                        else {
                            cache.put(uri, bimg, img);
                            img = cache.get(uri);
                        }
                    }
                    if (img != null) {
                        final BufferedImage i = img;
                        SwingUtilities.invokeAndWait(new Runnable() {
                            @Override
                            public void run() {
                                tile.image = new SoftReference<BufferedImage>(i);
                                tile.setLoaded(true);
                            }
                        });
                    }
                }
                catch (final OutOfMemoryError memErr) {
                    cache.needMoreMemory();
                }
                catch (final Throwable e) {
                    errorHandler.tileLoadingFailed(tile, e);
                    final Object oldError = tile.getError();
                    tile.setError(e);
                    tile.firePropertyChangeOnEDT(TileFactory.LOADING_ERROR_PROPERTY, oldError, e);
                    if (trys == 0) {
                        tile.firePropertyChangeOnEDT(TileFactory.UNRECOVERABLE_ERROR_PROPERTY, null, e);
                    } else {
                        trys--;
                    }
                }
            }

            tile.setLoading(false);
        }

        private byte[] cacheInputStream(final URL url) throws IOException {
            final InputStream ins = url.openStream();
            final ByteArrayOutputStream bout = new ByteArrayOutputStream();
            final byte[] buf = new byte[256];
            while (true) {
                final int n = ins.read(buf);
                if (n == -1)
                    break;
                bout.write(buf, 0, n);
            }
            return bout.toByteArray();
        }
    }
}
