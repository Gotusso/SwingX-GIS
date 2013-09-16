package org.jdesktop.swingx.mapviewer.tools.search;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.JPopupMenu;
import javax.swing.SwingWorker;

import org.jdesktop.swingx.JXMapKit;
import org.jdesktop.swingx.JXSearchField;
import org.jdesktop.swingx.mapviewer.GeoPosition;

/**
 * @author fgotusso <fgotusso@swissms.ch>
 */
public class MapSearchBox extends JXSearchField {

    public static class SearchBoxAction extends AbstractAction {

        private final JXMapKit kit;
        private final MapSearchBox searchBox;
        private final GeocodingBackend backend;

        public SearchBoxAction(final JXMapKit kit, final MapSearchBox searchBox, final GeocodingBackend backend) {
            this.kit = kit;
            this.searchBox = searchBox;
            this.backend = backend;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            final List<GeocodingResult> results = new LinkedList<GeocodingResult>();

            SwingWorker<Void, Void> sw = new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    try {
                        results.addAll(call(searchBox.getText()));
                    }
                    catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    return null;
                }

                @Override
                protected void done() {
                    SearchBoxAction.this.done(results);
                }
            };

            sw.execute();
        }


        public List<GeocodingResult> call(String query) throws Exception {
            return backend.geocode(query);
        }

        public void done(List<GeocodingResult> results) {
            JPopupMenu menu = new JPopupMenu();

            if (results == null || results.isEmpty()) {
                menu.add("");
            }
            else {
                for (final GeocodingResult result : results) {
                    menu.add(new AbstractAction(result.getDisplayName()) {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            int zoom = kit.getMainMap().getZoomFor(result.getBounds());
                            kit.setZoom(zoom);

                            kit.setCenterPosition(result.getPosition());
                        }
                    });
                }
            }

            searchBox.setFindPopupMenu(menu);


            if (searchBox.isUseNativeSearchFieldIfPossible()) {
                // Some native search fields can't handle .doClick()
                // right, so do it manually.
                menu.show(searchBox, 0, searchBox.getHeight());
            }
            else {
                searchBox.getPopupButton().doClick();
            }

        }
    }

    public MapSearchBox(final JXMapKit kit, final GeocodingBackend backend) {
        setSearchMode(SearchMode.REGULAR);
        addActionListener(new SearchBoxAction(kit, this, backend));
    }
}
