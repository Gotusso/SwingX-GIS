package org.jdesktop.swingx.mapviewer.compound;

import org.jdesktop.swingx.mapviewer.Tile;
import org.jdesktop.swingx.mapviewer.TileFactory;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.SoftReference;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.LinkedList;

/**
 * @author fgotusso <fgotusso@swissms.ch>
 */
public class CompoundTile extends Tile {
    private static float DEFAULT_FONT_SIZE = 12f;

    private final int tileSize;
    private final Tile baseTile;
    private LinkedList<Tile> layers;

    private Image loadingImage;
    private boolean showLoadingPercent;

    private  SoftReference<BufferedImage> buffer;
    private boolean dirtyFlag;

    public CompoundTile(int tileSize, final Tile baseTile) {
        super(baseTile.getX(), baseTile.getY(), baseTile.getZoom());

        this.tileSize = tileSize;
        this.baseTile = baseTile;

        layers = new LinkedList<Tile>();
        buffer = new SoftReference<BufferedImage>(null);
        dirtyFlag = true;

        final PropertyChangeListener loadedListener = new PropertyChangeListener() {

            @Override
            public void propertyChange(final PropertyChangeEvent evt) {
                setDirty(true);
                firePropertyChange(TileFactory.LOADED_PROPERTY, Boolean.FALSE, Boolean.TRUE);
            }
        };
        final PropertyChangeListener errorListener = new PropertyChangeListener() {

            @Override
            public void propertyChange(final PropertyChangeEvent evt) {
                setDirty(true);
                firePropertyChange(TileFactory.LOADING_ERROR_PROPERTY, Boolean.FALSE, Boolean.TRUE);
            }
        };

        baseTile.addPropertyChangeListener(TileFactory.LOADED_PROPERTY, loadedListener);
        baseTile.addPropertyChangeListener(TileFactory.UNRECOVERABLE_ERROR_PROPERTY, errorListener);

        // Base tile is concurrent and could be already loaded, so check it to
        // ensure a valid state
        if (baseTile.getError() != null) {
            errorListener.propertyChange(null);
        }

        loadedListener.propertyChange(null);
    }

    public synchronized void setLayers(final Collection<Tile> layers) {
        this.layers.clear();

        for (final Tile layer : layers) {
            layer.addPropertyChangeListener(TileFactory.LOADED_PROPERTY, new PropertyChangeListener() {

                @Override
                public void propertyChange(final PropertyChangeEvent evt) {
                // Set the dirty flag to rebuild the tile
                setDirty(true);
                // And fire again LOADED_PROPERTY to force a repaint
                firePropertyChange(TileFactory.LOADED_PROPERTY, Boolean.FALSE, Boolean.TRUE);
                }
            });
            layer.addPropertyChangeListener(TileFactory.UNRECOVERABLE_ERROR_PROPERTY, new PropertyChangeListener() {

                @Override
                public void propertyChange(final PropertyChangeEvent evt) {
                // Set the dirty flag to rebuild the tile
                setDirty(true);
                // And rethrow the error
                firePropertyChange(TileFactory.LOADING_ERROR_PROPERTY, Boolean.FALSE, Boolean.TRUE);
                }
            });

            this.layers.add(layer);
        }

        setDirty(true);
    }

    protected synchronized void setDirty(final boolean flag) {
        dirtyFlag = flag;
    }

    protected synchronized boolean isDirty() {
        return dirtyFlag;
    }

    @Override
    public synchronized void setLoaded(boolean loaded) {
        return;
    }

    /**
     * @inheritDoc
     */
    @Override
    public synchronized boolean isLoaded() {
        return true;
    }

    /**
     * @inheritDoc
     */
    @Override
    public void setLoading(final boolean isLoading) {
        return;
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean isLoading() {
        return false;
    }

    /**
     * @inheritDoc
     */
    @Override
    public Throwable getLoadingError() {
        return baseTile.getLoadingError();
    }

    /**
     * @inheritDoc
     */
    @Override
    public Throwable getUnrecoverableError() {
        return baseTile.getUnrecoverableError();
    }

    /**
     * @inheritDoc
     */
    @Override
    public synchronized BufferedImage getImage() {
        BufferedImage result = null;

        // If we have a cached value return it
        if (! isDirty()) {
            result = buffer.get();
            if (result != null) {
                return result;
            }
        }

        // We must use ARGB, not the base type. Base or layers
        // could have indexed colors
        result = new BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g2 = result.createGraphics();

        int paintedLayers = 0;
        int totalLayers = 1 + layers.size();

        final BufferedImage base = baseTile.getImage();
        if (base == null) {
            g2.setColor(Color.GRAY);
            g2.fillRect(0, 0, tileSize, tileSize);
        }
        else {
            g2.drawImage(base, 0, 0, null);
            paintedLayers++;

            // Paint all available layers
            for (final Tile layer : layers) {
                // Try to get the layer. The loading process will start if needed
                final BufferedImage overlay = layer.getImage();
                if (overlay != null) {
                    g2.drawImage(overlay, 0, 0, null);
                    paintedLayers++;
                }
            }
        }

        // If any image is still missing draw the loading image on top
        if (paintedLayers < totalLayers && (loadingImage != null || showLoadingPercent)) {
            final int backgroundColor = Color.GRAY.getRGB() << 8 | 128;
            g2.setColor(new Color(backgroundColor, true));
            g2.fillRect(0, 0, tileSize, tileSize);

            if (loadingImage != null && !showLoadingPercent) {
                final int imageX = (tileSize - getLoadingImage().getWidth(null)) / 2;
                final int imageY = (tileSize - getLoadingImage().getHeight(null)) / 2;
                g2.drawImage(loadingImage, imageX, imageY, null);
            }
            else {
                float percent = ((float) paintedLayers) / totalLayers;
                NumberFormat percentFormat = NumberFormat.getPercentInstance();
                percentFormat.setMaximumFractionDigits(1);
                String text = percentFormat.format(percent);

                g2.setFont(g2.getFont().deriveFont(DEFAULT_FONT_SIZE));
                Rectangle2D textBounds = g2.getFontMetrics().getStringBounds(text, g2);

                if (loadingImage == null) {
                    g2.setColor(Color.WHITE);
                    g2.drawString(text, Math.round((tileSize - textBounds.getWidth()) / 2), Math.round((tileSize - textBounds.getHeight()) / 2));
                }
                else {
                    int freeHeight = (int) Math.round(tileSize - getLoadingImage().getHeight(null) - textBounds.getHeight());

                    final int imageX = Math.round((tileSize - getLoadingImage().getWidth(null)) / 2);
                    final int imageY = Math.round(freeHeight / 2);
                    final int textX = (int) Math.round((tileSize - textBounds.getWidth()) / 2);
                    final int textY = Math.round(freeHeight / 2 + getLoadingImage().getHeight(null));

                    g2.drawImage(loadingImage, imageX, imageY, null);

                    g2.setColor(Color.WHITE);
                    g2.drawString(text, textX, textY);
                }
            }
        }

        g2.dispose();

        buffer = new SoftReference<BufferedImage>(result);
        setDirty(false);

        return result;
    }

    /**
     * @inheritDoc
     */
    @Override
    public Throwable getError() {
        return baseTile.getError();
    }

    /**
     * @inheritDoc
     */
    @Override
    public void setError(final Throwable error) {
        baseTile.setError(error);
    }

    /**
     * @inheritDoc
     */
    @Override
    public Priority getPriority() {
        return baseTile.getPriority();
    }

    /**
     * @inheritDoc
     */
    @Override
    public void setPriority(final Priority priority) {
        baseTile.setPriority(priority);
    }

    /**
     * @inheritDoc
     */
    @Override
    public String getURL() {
        return baseTile.getURL();
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
