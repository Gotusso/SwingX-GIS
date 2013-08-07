package org.jdesktop.swingx.mapviewer.compound;

import java.awt.Image;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.jdesktop.swingx.mapviewer.AbstractTileFactory;
import org.jdesktop.swingx.mapviewer.Tile;
import org.jdesktop.swingx.mapviewer.TileFactory;
import org.jdesktop.swingx.mapviewer.TileFactoryInfo;
import org.jdesktop.swingx.mapviewer.util.GeoUtil;
import org.jdesktop.swingx.mapviewer.util.LeastRecentlyUsedCache;


/**
 * @author fgotusso <fgotusso@swissms.ch>
 */
public class CompoundTileFactory extends AbstractTileFactory {

    private static class CompoundTileKey {
        private final int x;
        private final int y;
        private final int zoom;
        private final int state;

        public CompoundTileKey(final int x, final int y, final int zoom, final List<TileFactory> layers) {
            this.x = x;
            this.y = y;
            this.zoom = zoom;
            state = layers.hashCode();
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + state;
            result = prime * result + x;
            result = prime * result + y;
            result = prime * result + zoom;
            return result;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final CompoundTileKey other = (CompoundTileKey) obj;
            if (state != other.state) {
                return false;
            }
            if (x != other.x) {
                return false;
            }
            if (y != other.y) {
                return false;
            }
            if (zoom != other.zoom) {
                return false;
            }
            return true;
        }
    }

    private final LeastRecentlyUsedCache<CompoundTileKey, Tile> cache;
    private final TileFactory baseFactory;
    private final List<TileFactory> layers;
    private boolean showLoadingPercent;
    private Image loadingImage;

    public CompoundTileFactory(final TileFactory baseFactory) {
        super(baseFactory.getInfo());
        this.baseFactory = baseFactory;
        cache = new LeastRecentlyUsedCache<CompoundTileKey, Tile>();
        layers = new LinkedList<TileFactory>();
    }

    /**
     * @inheritDoc
     */
    @Override
    public synchronized Tile getTile(final int x, final int y, final int zoom) {

        final int numTilesWide = (int) getMapSize(zoom).getWidth();

        // Adjust the tile bounds
        int tileX = x;
        if (tileX < 0) {
            tileX = numTilesWide - (Math.abs(tileX) % numTilesWide);
        }
        tileX = tileX % numTilesWide;
        final int tileY = y;

        // Look-up the cache
        final CompoundTileKey key = new CompoundTileKey(tileX, tileY, zoom, getLayerFactories());

        Tile tile = cache.get(key);
        if (tile != null) {
            return tile;
        }

        TileFactoryInfo info = getInfo();

        // If we haven't the tile, generate it
        if (GeoUtil.isValidTile(tileX, tileY, zoom, info)) {
            final Tile base = baseFactory.getTile(tileX, tileY, zoom);
            CompoundTile compoundTile = new CompoundTile(info.getTileSize(zoom), base);
            compoundTile.setLoadingImage(loadingImage);
            compoundTile.setShowLoadingPercent(getShowLoadingPercent());

            final ArrayList<Tile> layers = new ArrayList<Tile>(this.layers.size());
            for (final TileFactory factory : this.layers) {
                final Tile layer = factory.getTile(x, y, zoom);
                layers.add(layer);
            }
            compoundTile.setLayers(layers);

            tile = compoundTile;
        }
        else {
            tile = new Tile(tileX, tileY, zoom);
        }

        // And save it
        cache.put(key, tile);

        return tile;
    }

    /**
     * @inheritDoc
     */
    @Override
    protected void startLoading(final Tile tile) {
        // Do nothing
    }

    public void setLayerFactories(final TileFactory... factories) {
        setLayerFactories(Arrays.asList(factories));
    }

    public void setLayerFactories(final Collection<TileFactory> factories) {
        if (layers != factories) {
            layers.clear();
            layers.addAll(factories);
        }
    }

    public List<TileFactory> getLayerFactories() {
        return layers;
    }

    public synchronized void clearCache() {
        cache.clear();
    }

    public void setLoadingImage(final Image image) {
        loadingImage = image;
    }

    public Image getLoadingImage() {
        return loadingImage;
    }

    public void setShowLoadingPercent(boolean show) {
        showLoadingPercent = show;
    }

    public boolean getShowLoadingPercent() {
        return showLoadingPercent;
    }
}
