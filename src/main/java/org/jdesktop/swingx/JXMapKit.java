/*
 * JXMapKit.java
 *
 * Created on November 19, 2006, 3:52 AM
 */

package org.jdesktop.swingx;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.jdesktop.swingx.mapviewer.GeoPosition;
import org.jdesktop.swingx.mapviewer.TileFactory;
import org.jdesktop.swingx.mapviewer.Waypoint;
import org.jdesktop.swingx.mapviewer.WaypointPainter;
import org.jdesktop.swingx.mapviewer.bmng.CylindricalProjectionTileFactory;
import org.jdesktop.swingx.mapviewer.compound.CompoundTileFactory;
import org.jdesktop.swingx.mapviewer.empty.EmptyTileFactory;
import org.jdesktop.swingx.mapviewer.openstreetmap.OpenStreetMapTileFactory;
import org.jdesktop.swingx.mapviewer.tools.search.MapSearchBox;
import org.jdesktop.swingx.mapviewer.tools.search.NomatimGeocoding;
import org.jdesktop.swingx.painter.AbstractPainter;
import org.jdesktop.swingx.painter.CompoundPainter;
import org.jdesktop.swingx.painter.Painter;

/**
 * <p>
 * The JXMapKit is a pair of JXMapViewers preconfigured to be easy to use with
 * common features built in. This includes zoom buttons, a zoom slider, and a
 * mini-map in the lower right corner showing an overview of the map. Each
 * feature can be turned off using an appropriate <CODE>is<I>X</I>visible</CODE>
 * property. For example, to turn off the minimap call
 *
 * <PRE>
 * <CODE>jxMapKit.setMiniMapVisible(false);</CODE>
 * </PRE>
 *
 * </p>
 *
 * <p>
 * The JXMapViewer is preconfigured to connect to maps.swinglabs.org which
 * serves up global satellite imagery from NASA's <a
 * href="http://earthobservatory.nasa.gov/Newsroom/BlueMarble/">Blue Marble
 * NG</a> image collection.
 * </p>
 *
 * @author joshy
 */
public class JXMapKit extends JXPanel {
    private boolean miniMapVisible = true;
    private boolean zoomSliderVisible = true;
    private boolean zoomButtonsVisible = true;
    private final boolean sliderReversed = false;

    private boolean addressLocationShown = false;
    private boolean dataProviderCreditShown = false;

    private JXMapViewer mainMap;
    private JXMapViewer miniMap;
    private JPanel leftControlPanel;
    private JPanel rightControlPanel;
    private JButton zoomInButton;
    private JButton zoomOutButton;
    private JSlider zoomSlider;

    public enum DefaultProviders {
        SwingLabsBlueMarble, OpenStreetMaps, Custom
    };

    private DefaultProviders defaultProvider = DefaultProviders.OpenStreetMaps;

    /**
     * Creates a new JXMapKit
     */
    public JXMapKit() {
        initComponents();

        zoomSlider.setOpaque(false);
        try {
            final Icon minusIcon = new ImageIcon(getClass().getResource("/resources/minus.png"));
            this.zoomOutButton.setIcon(minusIcon);
            this.zoomOutButton.setText("");
            final Icon plusIcon = new ImageIcon(getClass().getResource("/resources/plus.png"));
            this.zoomInButton.setIcon(plusIcon);
            this.zoomInButton.setText("");
        }
        catch (final Throwable thr) {
            System.out.println("error: " + thr.getMessage());
            thr.printStackTrace();
        }

        setDefaultProvider(getDefaultProvider());

        mainMap.setCenterPosition(new GeoPosition(0, 0));
        miniMap.setCenterPosition(new GeoPosition(0, 0));
        mainMap.setRestrictOutsidePanning(true);
        miniMap.setRestrictOutsidePanning(true);

        rebuildMainMapOverlay();

        /*
         * // adapter to move the minimap after the main map has moved
         * MouseInputAdapter ma = new MouseInputAdapter() { public void
         * mouseReleased(MouseEvent e) {
         * miniMap.setCenterPosition(mapCenterPosition); } };
         * mainMap.addMouseMotionListener(ma); mainMap.addMouseListener(ma);
         */

        mainMap.addPropertyChangeListener("center", new PropertyChangeListener() {
            @Override
            public void propertyChange(final PropertyChangeEvent evt) {
                final Point2D mapCenter = (Point2D) evt.getNewValue();
                final TileFactory tf = mainMap.getTileFactory();
                final GeoPosition mapPos = tf.pixelToGeo(mapCenter, mainMap.getZoom());
                miniMap.setCenterPosition(mapPos);
            }
        });

        mainMap.addPropertyChangeListener("centerPosition", new PropertyChangeListener() {
            @Override
            public void propertyChange(final PropertyChangeEvent evt) {
                mapCenterPosition = (GeoPosition) evt.getNewValue();
                miniMap.setCenterPosition(mapCenterPosition);
                final Point2D pt = miniMap.getTileFactory().geoToPixel(mapCenterPosition, miniMap.getZoom());
                miniMap.setCenter(pt);
                miniMap.repaint();
            }
        });
        miniMap.addPropertyChangeListener("centerPosition", new PropertyChangeListener() {
            @Override
            public void propertyChange(final PropertyChangeEvent evt) {
                mapCenterPosition = (GeoPosition) evt.getNewValue();
                mainMap.setCenterPosition(mapCenterPosition);
                final Point2D pt = mainMap.getTileFactory().geoToPixel(mapCenterPosition, mainMap.getZoom());
                mainMap.setCenter(pt);
                mainMap.repaint();
            }
        });
        mainMap.addPropertyChangeListener("zoom", new PropertyChangeListener() {
            @Override
            public void propertyChange(final PropertyChangeEvent evt) {
                zoomSlider.setValue(mainMap.getZoom());
                miniMap.setZoom(mainMap.getZoom() + 4);
            }
        });

        // an overlay for the mini-map which shows a rectangle representing the
        // main map
        miniMap.setOverlayPainter(new Painter<JXMapViewer>() {
            @Override
            public void paint(Graphics2D g, final JXMapViewer map, final int width, final int height) {
                // get the viewport rect of the main map
                final Rectangle mainMapBounds = mainMap.getViewportBounds();

                // convert to Point2Ds
                Point2D upperLeft2D = mainMapBounds.getLocation();
                Point2D lowerRight2D = new Point2D.Double(upperLeft2D.getX() + mainMapBounds.getWidth(), upperLeft2D
                        .getY() + mainMapBounds.getHeight());

                // convert to GeoPostions
                final GeoPosition upperLeft = mainMap.getTileFactory().pixelToGeo(upperLeft2D, mainMap.getZoom());
                final GeoPosition lowerRight = mainMap.getTileFactory().pixelToGeo(lowerRight2D, mainMap.getZoom());

                // convert to Point2Ds on the mini-map
                upperLeft2D = map.getTileFactory().geoToPixel(upperLeft, map.getZoom());
                lowerRight2D = map.getTileFactory().geoToPixel(lowerRight, map.getZoom());

                g = (Graphics2D) g.create();
                final Rectangle rect = map.getViewportBounds();
                // p("rect = " + rect);
                g.translate(-rect.x, -rect.y);
                final Point2D centerpos = map.getTileFactory().geoToPixel(mapCenterPosition, map.getZoom());
                // p("center pos = " + centerpos);
                g.setPaint(Color.RED);
                // g.drawRect((int)centerpos.getX()-30,(int)centerpos.getY()-30,60,60);
                g.drawRect((int) upperLeft2D.getX(), (int) upperLeft2D.getY(),
                        (int) (lowerRight2D.getX() - upperLeft2D.getX()),
                        (int) (lowerRight2D.getY() - upperLeft2D.getY()));
                g.setPaint(new Color(255, 0, 0, 50));
                g.fillRect((int) upperLeft2D.getX(), (int) upperLeft2D.getY(),
                        (int) (lowerRight2D.getX() - upperLeft2D.getX()),
                        (int) (lowerRight2D.getY() - upperLeft2D.getY()));
                // g.drawOval((int)lowerRight2D.getX(),(int)lowerRight2D.getY(),1,1);
                g.dispose();
            }
        });

        if (getDefaultProvider() == DefaultProviders.OpenStreetMaps) {
            setZoom(10);
        }
        else {
            setZoom(3);// joshy: hack, i shouldn't need this here
        }
        this.setCenterPosition(new GeoPosition(0, 0));
    }

    // private Point2D mapCenter = new Point2D.Double(0,0);
    private GeoPosition mapCenterPosition = new GeoPosition(0, 0);
    private boolean zoomChanging = false;

    /**
     * Set the current zoomlevel for the main map. The minimap will be updated
     * accordingly
     *
     * @param zoom
     *            the new zoom level
     */
    public void setZoom(final int zoom) {
        zoomChanging = true;
        mainMap.setZoom(zoom);
        miniMap.setZoom(mainMap.getZoom() + 4);
        if (sliderReversed) {
            zoomSlider.setValue(zoomSlider.getMaximum() - zoom);
        }
        else {
            zoomSlider.setValue(zoom);
        }
        zoomChanging = false;
    }

    /**
     * Returns an action which can be attached to buttons or menu items to make
     * the map zoom out
     *
     * @return a preconfigured Zoom Out action
     */
    public Action getZoomOutAction() {
        final Action act = new AbstractAction() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                setZoom(mainMap.getZoom() - 1);
            }
        };
        act.putValue(Action.NAME, "-");
        return act;
    }

    /**
     * Returns an action which can be attached to buttons or menu items to make
     * the map zoom in
     *
     * @return a preconfigured Zoom In action
     */
    public Action getZoomInAction() {
        final Action act = new AbstractAction() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                setZoom(mainMap.getZoom() + 1);
            }
        };
        act.putValue(Action.NAME, "+");
        return act;
    }

    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        mainMap = new JXMapViewer();

        setLayout(new GridBagLayout());

        mainMap.setLayout(new BorderLayout());
        mainMap.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        miniMap = new JXMapViewer();
        miniMap.setBorder(BorderFactory.createLineBorder(new Color(0, 0, 0)));
        miniMap.setMinimumSize(new Dimension(100, 100));
        miniMap.setMaximumSize(new Dimension(100, 100));
        miniMap.setPreferredSize(new Dimension(100, 100));
        miniMap.setAlignmentX(Component.RIGHT_ALIGNMENT);

        rightControlPanel = new JPanel();
        BoxLayout rightBoxLayout = new BoxLayout(rightControlPanel, BoxLayout.Y_AXIS);
        rightControlPanel.setLayout(rightBoxLayout);
        rightControlPanel.setOpaque(false);

        rightControlPanel.add(Box.createVerticalGlue());
        rightControlPanel.add(miniMap);

        JPanel zoomPanel = new JPanel();
        zoomInButton = new JButton();
        zoomOutButton = new JButton();
        zoomSlider = new JSlider();

        zoomPanel.setOpaque(false);
        zoomPanel.setLayout(new GridBagLayout());

        zoomInButton.setAction(getZoomOutAction());
        zoomInButton.setIcon(new ImageIcon(getClass().getResource("/resources/plus.png")));
        zoomInButton.setMargin(new Insets(2, 2, 2, 2));
        zoomInButton.setMaximumSize(new Dimension(20, 20));
        zoomInButton.setMinimumSize(new Dimension(20, 20));
        zoomInButton.setPreferredSize(new Dimension(20, 20));
        zoomInButton.setOpaque(false);

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = GridBagConstraints.SOUTHEAST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        zoomPanel.add(zoomInButton, gridBagConstraints);

        zoomOutButton.setAction(getZoomInAction());
        zoomOutButton.setIcon(new ImageIcon(getClass().getResource("/resources/minus.png")));
        zoomOutButton.setMargin(new Insets(2, 2, 2, 2));
        zoomOutButton.setMaximumSize(new Dimension(20, 20));
        zoomOutButton.setMinimumSize(new Dimension(20, 20));
        zoomOutButton.setOpaque(false);
        zoomOutButton.setPreferredSize(new Dimension(20, 20));
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = GridBagConstraints.SOUTHWEST;
        gridBagConstraints.weighty = 1.0;
        zoomPanel.add(zoomOutButton, gridBagConstraints);

        zoomSlider.setMajorTickSpacing(1);
        zoomSlider.setMinorTickSpacing(1);
        zoomSlider.setMaximum(15);
        zoomSlider.setMinimum(10);
        zoomSlider.setOrientation(JSlider.VERTICAL);
        zoomSlider.setPaintTicks(true);
        zoomSlider.setSnapToTicks(true);
        zoomSlider.setPreferredSize(new Dimension(35, 190));
        zoomSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(final ChangeEvent evt) {
                zoomSliderStateChanged(evt);
            }
        });
        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = GridBagConstraints.VERTICAL;
        gridBagConstraints.anchor = GridBagConstraints.NORTH;
        zoomPanel.add(zoomSlider, gridBagConstraints);
        zoomPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        zoomPanel.setMinimumSize(new Dimension(40, 215));
        zoomPanel.setMaximumSize(new Dimension(40, 215));
        zoomPanel.setPreferredSize(new Dimension(40, 215));

        leftControlPanel = new JPanel(new VerticalLayout());
        BoxLayout leftBoxLayout = new BoxLayout(leftControlPanel, BoxLayout.Y_AXIS);
        leftControlPanel.setLayout(leftBoxLayout);
        leftControlPanel.setOpaque(false);

        leftControlPanel.add(Box.createVerticalGlue());
        leftControlPanel.add(zoomPanel);

        mainMap.add(leftControlPanel, BorderLayout.WEST);
        mainMap.add(rightControlPanel, BorderLayout.EAST);

        gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.fill = GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        add(mainMap, gridBagConstraints);
    }

    private void zoomSliderStateChanged(final javax.swing.event.ChangeEvent evt) {
        if (!zoomChanging) {
            setZoom(zoomSlider.getValue());
        }
    }

    private static void p(final String str) {
        System.out.println(str);
    }

    /**
     * Indicates if the mini-map is currently visible
     *
     * @return the current value of the mini-map property
     */
    public boolean isMiniMapVisible() {
        return miniMapVisible;
    }

    /**
     * Sets if the mini-map should be visible
     *
     * @param miniMapVisible
     *            a new value for the miniMap property
     */
    public void setMiniMapVisible(final boolean miniMapVisible) {
        final boolean old = this.isMiniMapVisible();
        this.miniMapVisible = miniMapVisible;
        miniMap.setVisible(miniMapVisible);
        firePropertyChange("miniMapVisible", old, this.isMiniMapVisible());
    }

    /**
     * Indicates if the zoom slider is currently visible
     *
     * @return the current value of the zoomSliderVisible property
     */
    public boolean isZoomSliderVisible() {
        return zoomSliderVisible;
    }

    /**
     * Sets if the zoom slider should be visible
     *
     * @param zoomSliderVisible
     *            the new value of the zoomSliderVisible property
     */
    public void setZoomSliderVisible(final boolean zoomSliderVisible) {
        final boolean old = this.isZoomSliderVisible();
        this.zoomSliderVisible = zoomSliderVisible;
        zoomSlider.setVisible(zoomSliderVisible);
        firePropertyChange("zoomSliderVisible", old, this.isZoomSliderVisible());
    }

    /**
     * Indicates if the zoom buttons are visible. This is a bound property and
     * can be listed for using a PropertyChangeListener
     *
     * @return current value of the zoomButtonsVisible property
     */
    public boolean isZoomButtonsVisible() {
        return zoomButtonsVisible;
    }

    /**
     * Sets if the zoom buttons should be visible. This ia bound property.
     * Changes can be listened for using a PropertyChaneListener
     *
     * @param zoomButtonsVisible
     *            new value of the zoomButtonsVisible property
     */
    public void setZoomButtonsVisible(final boolean zoomButtonsVisible) {
        final boolean old = this.isZoomButtonsVisible();
        this.zoomButtonsVisible = zoomButtonsVisible;
        zoomInButton.setVisible(zoomButtonsVisible);
        zoomOutButton.setVisible(zoomButtonsVisible);
        firePropertyChange("zoomButtonsVisible", old, this.isZoomButtonsVisible());
    }

    /**
     * Sets the tile factory for both embedded JXMapViewer components. Calling
     * this method will also reset the center and zoom levels of both maps, as
     * well as the bounds of the zoom slider.
     *
     * @param fact
     *            the new TileFactory
     */
    public void setTileFactory(final TileFactory fact) {
        mainMap.setTileFactory(fact);
        mainMap.setZoom(fact.getInfo().getDefaultZoomLevel());
        mainMap.setCenterPosition(new GeoPosition(0, 0));
        miniMap.setTileFactory(fact);
        miniMap.setZoom(fact.getInfo().getDefaultZoomLevel() + 3);
        miniMap.setCenterPosition(new GeoPosition(0, 0));
        zoomSlider.setMinimum(fact.getInfo().getMinimumZoomLevel());
        zoomSlider.setMaximum(fact.getInfo().getMaximumZoomLevel());
    }

    public void setCenterPosition(final GeoPosition pos) {
        mainMap.setCenterPosition(pos);
        miniMap.setCenterPosition(pos);
    }

    public GeoPosition getCenterPosition() {
        return mainMap.getCenterPosition();
    }

    public GeoPosition getAddressLocation() {
        return mainMap.getAddressLocation();
    }

    public void setAddressLocation(final GeoPosition pos) {
        mainMap.setAddressLocation(pos);
    }

    /**
     * Returns a reference to the main embedded JXMapViewer component
     *
     * @return the main map
     */
    public JXMapViewer getMainMap() {
        return this.mainMap;
    }

    /**
     * Returns a reference to the mini embedded JXMapViewer component
     *
     * @return the minimap JXMapViewer component
     */
    public JXMapViewer getMiniMap() {
        return this.miniMap;
    }

    public JPanel getLeftControlPanel() {
        return leftControlPanel;
    }

    public JPanel getRightControlPanel() {
        return rightControlPanel;
    }

    /**
     * returns a reference to the zoom in button
     *
     * @return a jbutton
     */
    public JButton getZoomInButton() {
        return this.zoomInButton;
    }

    /**
     * returns a reference to the zoom out button
     *
     * @return a jbutton
     */
    public JButton getZoomOutButton() {
        return this.zoomOutButton;
    }

    /**
     * returns a reference to the zoom slider
     *
     * @return a jslider
     */
    public JSlider getZoomSlider() {
        return this.zoomSlider;
    }

    public void setAddressLocationShown(final boolean b) {
        final boolean old = isAddressLocationShown();
        this.addressLocationShown = b;
        addressLocationPainter.setVisible(b);
        firePropertyChange("addressLocationShown", old, b);
        repaint();
    }

    public boolean isAddressLocationShown() {
        return addressLocationShown;
    }

    public void setDataProviderCreditShown(final boolean b) {
        final boolean old = isDataProviderCreditShown();
        this.dataProviderCreditShown = b;
        dataProviderCreditPainter.setVisible(b);
        repaint();
        firePropertyChange("dataProviderCreditShown", old, b);
    }

    public boolean isDataProviderCreditShown() {
        return dataProviderCreditShown;
    }

    private void rebuildMainMapOverlay() {
        final CompoundPainter cp = new CompoundPainter();
        cp.setCacheable(false);

        List<Painter> painters = new ArrayList<Painter>();
        if (isDataProviderCreditShown()) {
            painters.add(dataProviderCreditPainter);
        }
        if (isAddressLocationShown()) {
            painters.add(addressLocationPainter);
        }

        cp.setPainters(painters.toArray(new AbstractPainter[]{}));
        mainMap.setOverlayPainter(cp);
    }

    public void setDefaultProvider(final DefaultProviders prov) {
        final DefaultProviders old = this.defaultProvider;
        this.defaultProvider = prov;
        if (prov == DefaultProviders.SwingLabsBlueMarble) {
            setTileFactory(new CylindricalProjectionTileFactory());
            setZoom(3);
        }
        if (prov == DefaultProviders.OpenStreetMaps) {
            setTileFactory(new OpenStreetMapTileFactory());
            setZoom(11);
        }
        firePropertyChange("defaultProvider", old, prov);
        repaint();
    }

    public DefaultProviders getDefaultProvider() {
        return this.defaultProvider;
    }

    private final AbstractPainter dataProviderCreditPainter = new AbstractPainter<JXMapViewer>(false) {
        @Override
        protected void doPaint(final Graphics2D g, final JXMapViewer map, final int width, final int height) {
            g.setPaint(Color.WHITE);
            g.drawString("data ", 50, map.getHeight() - 10);
        }
    };

    private final WaypointPainter addressLocationPainter = new WaypointPainter() {
        @Override
        public Set<Waypoint> getWaypoints() {
            final Set set = new HashSet();
            if (getAddressLocation() != null) {
                set.add(new Waypoint(getAddressLocation()));
            } else {
                set.add(new Waypoint(0, 0));
            }
            return set;
        }
    };

    public static void main(final String... args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                final JXMapKit kit = new JXMapKit();

                kit.setZoom(14);
                kit.setAddressLocation(new GeoPosition(51.5, 0));
                kit.getMainMap().setDrawTileBorders(true);
                kit.getMainMap().setRestrictOutsidePanning(true);
                kit.getMainMap().setHorizontalWrapped(false);

                CompoundTileFactory factory = new CompoundTileFactory(kit.getMainMap().getTileFactory());
                factory.setLayerFactories(new EmptyTileFactory());
                factory.setShowLoadingPercent(true);

                kit.getMainMap().setTileFactory(factory);

                final JPanel toolPanel = new JPanel(new VerticalLayout());
                toolPanel.setOpaque(false);

                final JPanel searchPanel = new JPanel(new HorizontalLayout());
                searchPanel.setOpaque(false);

                final NomatimGeocoding backend = new NomatimGeocoding();
                final MapSearchBox searchBox = new MapSearchBox(kit, backend);
                searchBox.setVisible(false);

                final JToggleButton searchButton = new JToggleButton();
                searchButton.setAction(new AbstractAction("Search") {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        searchBox.setVisible(searchButton.isSelected());
                        searchPanel.revalidate();
                    }
                });

                searchPanel.add(searchButton);
                searchPanel.add(searchBox);

                toolPanel.add(searchPanel);
                toolPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

                kit.getLeftControlPanel().add(toolPanel, 0);

                final JFrame frame = new JFrame("JXMapKit test");
                frame.add(kit);
                frame.pack();
                frame.setSize(500, 300);
                frame.setVisible(true);
            }
        });
    }
}
