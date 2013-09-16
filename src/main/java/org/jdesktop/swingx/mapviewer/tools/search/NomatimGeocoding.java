package org.jdesktop.swingx.mapviewer.tools.search;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.jdesktop.swingx.mapviewer.GeoBounds;
import org.jdesktop.swingx.mapviewer.GeoPosition;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Wrapper for Nominatim OSM search service.
 * See http://wiki.openstreetmap.org/wiki/Nominatim for details.
 *
 * @author fgotusso <fgotusso@swissms.ch>
 */
public class NomatimGeocoding extends AbstractGeocodingBackend {

    protected static final String DISPLAY_NAME_PARAM = "display_name";
    protected static final String LATITUDE_PARAM = "lat";
    protected static final String LONGITUDE_PARAM = "lon";
    protected static final String BOUNDING_BOX_PARAM = "boundingbox";

    private String endpoint;
    private Locale locale;
    private GeoBounds view;
    private boolean bounded;
    private String email;
    private int limit;
    private String format;

    public NomatimGeocoding() {
        endpoint = "http://nominatim.openstreetmap.org/search";
        limit = 5;
        format = "xml";
    }

    public String getEndpoint() {
        return endpoint;
    }

    /**
     * Base endpoint for the service. Change this if you want to use your own Nomatim server
     * @param endpoint The base url
     */
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Locale getLocale() {
        return locale;
    }

    /**
     * Preferred language order for showing search results
     * @param locale
     */
    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    public GeoBounds getView() {
        return view;
    }

    /**
     * The preferred area to find search results
     * @param view
     */
    public void setView(GeoBounds view) {
        this.view = view;
    }

    /**
     * Restrict the results to only items contained with the bounding box.
     * Restricting the results to the bounding box also enables searching by amenity only.
     * For example a search query of just "[pub]" would normally be rejected
     * but with bounded=true will result in a list of items matching within the bounding box.
     * @return
     */
    public boolean isBounded() {
        return bounded;
    }

    public void setBounded(boolean bounded) {
        this.bounded = bounded;
    }

    public String getEmail() {
        return email;
    }

    /**
     * If you are making large numbers of request please include a valid email address
     * or alternatively include your email address as part of the User-Agent string.
     * This information will be kept confidential and only used to contact you in the
     * event of a problem, see Usage Policy for more details.
     *
     * @param email A valid email address
     */
    public void setEmail(String email) {
        this.email = email;
    }

    public int getLimit() {
        return limit;
    }

    /**
     * Limit the number of returned results
     * @param limit Maximum number of results. -1 for no limit
     */
    public void setLimit(int limit) {
        this.limit = limit;
    }

    public String getFormat() {
        return format;
    }

    /**
     * Output format [html|xml|json]
     * @param format
     */
    protected void setFormat(String format) {
        this.format = format;
    }

    @Override
    public List<GeocodingResult> geocode(String query) throws IOException {
        StringBuilder sb = new StringBuilder();
        Map<String, String> parameters = new LinkedHashMap<String, String>();

        parameters.put("q", query);
        if (locale != null) {
            parameters.put("accept-language", locale.getLanguage());
        }
        if (view != null) {
            String bounds = view.getNorthWest().getLongitude() + "," +
                            view.getNorthWest().getLatitude()  + "," +
                            view.getSouthEast().getLongitude() + "," +
                            view.getSouthEast().getLatitude();
            parameters.put("viewbox", bounds);
            parameters.put("bounded", bounded ? "1" : "0");
        }
        if (email != null) {
            parameters.put("email", email);
        }
        if (limit > 0) {
            parameters.put("limit", Integer.valueOf(limit).toString());
        }
        if (format!= null) {
            parameters.put("format", format);
        }

        parameters.putAll(getProviderParameters());

        try {
            sb.append(endpoint).append('?');
            for (Entry<String, String> entry : parameters.entrySet()) {
                sb.append(entry.getKey()).append('=').append(URLEncoder.encode(entry.getValue(), "UTF-8")).append('&');
            }
        }
        catch (UnsupportedEncodingException ex) {
            // Programmer error
            ex.printStackTrace();
        }

        URL request = new URL(sb.toString());
        URLConnection connection = request.openConnection();
        InputStream input = connection.getInputStream();

        return parse(input);
    }

    protected List<GeocodingResult> parse(InputStream input) throws IOException {
        List<GeocodingResult> results = new LinkedList<GeocodingResult>();

        try {
            final DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance();
            final DocumentBuilder builder = documentFactory.newDocumentBuilder();

            builder.setErrorHandler(new org.xml.sax.ErrorHandler() {
                @Override
                public void warning(final SAXParseException exception) throws SAXException {
                    // Print the warning, but continue with the loading
                    exception.printStackTrace();
                }

                @Override
                public void error(final SAXParseException exception) throws SAXException {
                    throw exception;
                }

                @Override
                public void fatalError(final SAXParseException exception) throws SAXException {
                    throw exception;
                }
            });

            final Document document = builder.parse(input);
            final XPath xpath = XPathFactory.newInstance().newXPath();

            XPathExpression expr = xpath.compile("/searchresults/place");

            NodeList nodes = (NodeList) expr.evaluate(document, XPathConstants.NODESET);
            for (int i = 0; i < nodes.getLength(); i++) {
                NamedNodeMap attributes = nodes.item(i).getAttributes();

                Node displayNameNode = attributes.getNamedItem(DISPLAY_NAME_PARAM);
                Node latitudeNode = attributes.getNamedItem(LATITUDE_PARAM);
                Node longitudeNode = attributes.getNamedItem(LONGITUDE_PARAM);
                Node boundingBoxNode = attributes.getNamedItem(BOUNDING_BOX_PARAM);

                String[] boundingBoxValues = boundingBoxNode.getNodeValue().split(",");

                String displayName = displayNameNode.getNodeValue();
                GeoPosition position = new GeoPosition(
                        Double.parseDouble(latitudeNode.getNodeValue()),
                        Double.parseDouble(longitudeNode.getNodeValue()));
                GeoBounds bounds = new GeoBounds(
                        Double.parseDouble(boundingBoxValues[0]),
                        Double.parseDouble(boundingBoxValues[2]),
                        Double.parseDouble(boundingBoxValues[1]),
                        Double.parseDouble(boundingBoxValues[3]));

                GeocodingResult result = new GeocodingResult(displayName, position, bounds);

                results.add(result);
            }
        }
        catch (ParserConfigurationException ex) {
            throw new IOException(ex);
        }
        catch (SAXException ex) {
            throw new IOException(ex);
        }
        catch (XPathExpressionException ex) {
            throw new IOException(ex);
        }

        return results;
    }
}
