package org.jdesktop.swingx.mapviewer.tools.scale;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JComponent;

import org.jdesktop.swingx.JXMapViewer;
import org.jdesktop.swingx.mapviewer.GeoPosition;
import org.jdesktop.swingx.mapviewer.TileFactory;

/**
 * @author fgotusso <fgotusso@swissms.ch>
 */
public class MapScale extends JComponent {
    private static final int BAR_THICKNESS = 4;
    private static final Color BAR_COLOR = new Color(0, 0, 0, .8f);
    private static final int[] SCALE_FACTORS = new int[] {
        1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000
    };

    private final JXMapViewer viewer;

    public MapScale(JXMapViewer viewer) {
        this.viewer = viewer;

        PropertyChangeListener repaintListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                repaint();
            }
        };

        viewer.addPropertyChangeListener("zoom", repaintListener);
        viewer.addPropertyChangeListener("centerPosition", repaintListener);

        Dimension dimension = new Dimension(135, 35);
        setMinimumSize(dimension);
        setPreferredSize(dimension);
        setMaximumSize(dimension);

        setForeground(BAR_COLOR);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Insets insets = getInsets();
        int w = getWidth() - insets.left - insets.right;
        int h = getHeight() - insets.top - insets.bottom;

        int imperialFactor = findScaleFactor(w, Scale.MILE);
        Scale imperialUnit = Scale.MILE;

        if (imperialFactor < 0) {
            imperialFactor = findScaleFactor(w, Scale.FOOT);
            imperialUnit = Scale.FOOT;
        }

        int metricFactor = findScaleFactor(w, Scale.KILOMETER);
        Scale metricUnit = Scale.KILOMETER;

        if (metricFactor < 0) {
            metricFactor = findScaleFactor(w, Scale.METER);
            metricUnit = Scale.METER;
        }

        int imperialWidth = (int) Math.round(getFactorWidth(w, imperialFactor, imperialUnit));
        int metricWidth = (int) Math.round(getFactorWidth(w, metricFactor, metricUnit));
        int barWidth = Math.round(Math.max(metricWidth, imperialWidth));

        String imperialText = Integer.valueOf(imperialFactor).toString() + " " + imperialUnit.getSymbol();
        String metricText = Integer.valueOf(metricFactor).toString() + " " + metricUnit.getSymbol();

        Graphics2D g2 = (Graphics2D) g;
        Rectangle2D imperialTextBounds = g2.getFontMetrics().getStringBounds(imperialText, g2);
        Rectangle2D metricTextBounds = g2.getFontMetrics().getStringBounds(metricText, g2);

        g2.setColor(getForeground());

        g2.fill(new Rectangle2D.Double(
                insets.left + w - barWidth,
                insets.top + (h - BAR_THICKNESS) / 2,
                barWidth,
                BAR_THICKNESS));
        g2.fill(new Rectangle2D.Double(
                insets.left + w - imperialWidth,
                insets.top + (h - BAR_THICKNESS) / 2 - imperialTextBounds.getHeight(),
                BAR_THICKNESS,
                imperialTextBounds.getHeight()));
        g2.fill(new Rectangle2D.Double(
                insets.left + w - metricWidth,
                insets.top + (h + BAR_THICKNESS) / 2,
                BAR_THICKNESS,
                metricTextBounds.getHeight()));

        g2.drawString(
                imperialText,
                (int) (insets.left + w - imperialTextBounds.getWidth()),
                (int) (insets.top + (h - BAR_THICKNESS) / 2 - imperialTextBounds.getMaxY()));
        g2.drawString(
                metricText,
                (int) (insets.left + w - metricTextBounds.getWidth()),
                (int) (insets.top + (h + BAR_THICKNESS) / 2 + metricTextBounds.getHeight()));

    }

    private int findScaleFactor(int width, Scale scale) {
        for (int i = SCALE_FACTORS.length - 1 ; i >= 0; i--) {
            int factor = SCALE_FACTORS[i];

            double scaleWidth = getFactorWidth(width, factor, scale);
            if (scaleWidth < width) {
                return factor;
            }
        }

        return -1;
    }

    private double getFactorWidth(int width, int factor, Scale scale) {
        Rectangle2D viewportBounds = viewer.getViewportBounds();
        TileFactory factory = viewer.getTileFactory();
        int zoom = viewer.getZoom();

        GeoPosition origin = viewer.getCenterPosition();
        GeoPosition avgDestiny = factory.pixelToGeo(new Point2D.Double(viewportBounds.getCenterX() + .75f * width, viewportBounds.getCenterY()), zoom);
        double avgBearing = OrthodromicCalculation.bearing(origin, avgDestiny);
        double distance = Scale.transform(factor, scale, Scale.METER);

        GeoPosition destiny = OrthodromicCalculation.positionFrom(origin, distance, avgBearing);

        Point2D pixelOrigin = factory.geoToPixel(origin, zoom);
        Point2D pixelDestiny = factory.geoToPixel(destiny, zoom);

        return Math.abs(pixelDestiny.getX() - pixelOrigin.getX());
    }
}
