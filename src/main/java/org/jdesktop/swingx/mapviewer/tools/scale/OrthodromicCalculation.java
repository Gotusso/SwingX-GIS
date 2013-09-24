package org.jdesktop.swingx.mapviewer.tools.scale;

import org.jdesktop.swingx.mapviewer.GeoPosition;

/**
 * Shortest distance between two points on the surface of a sphere, measured along the surface of the sphere.
 * Note that this may be inaccurate in some projections.
 *
 * @author fgotusso <fgotusso@swissms.ch>
 */
public class OrthodromicCalculation {
    private static final int RADIUS = 6371000;

    /**
     * Calculates the distance in meters between two points
     *
     * @param p1 The origin point
     * @param p2 The destiny point
     * @return The distance in meters
     */
    public static double distanceBetween(GeoPosition p1, GeoPosition p2) {
        double dLat = Math.toRadians(p2.getLatitude() - p1.getLatitude());
        double dLon = Math.toRadians(p2.getLongitude() - p1.getLongitude());
        double lat1 = Math.toRadians(p1.getLatitude());
        double lat2 = Math.toRadians(p2.getLatitude());

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.sin(dLon / 2) * Math.sin(dLon / 2) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return RADIUS * c;
    }

    /**
     * Calculates a destiny point based on given start point, distance and bearing
     *
     * @param p1 The start point
     * @param distance The traveled distance, in meters
     * @param bearing Initial bearing, in radians
     * @return
     */
    public static GeoPosition positionFrom(GeoPosition p1, double distance, double bearing) {
        double angularDistance = distance / RADIUS;
        double lat1 = Math.toRadians(p1.getLatitude());
        double lon1 = Math.toRadians(p1.getLongitude());

        double sinLat1 = Math.sin(lat1);
        double cosLat1 = Math.cos(lat1);
        double cosAD = Math.cos(angularDistance);
        double sinAD = Math.sin(angularDistance);

        double lat2 = Math.asin(sinLat1 * cosAD + cosLat1 * sinAD * Math.cos(bearing));
        double lon2 = lon1 + Math.atan2(Math.sin(bearing) * sinAD * cosLat1, cosAD - sinLat1 * Math.sin(lat2));

        // Normalize to -180 / +180
        lon2 = (lon2 + 3 * Math.PI) % (2 * Math.PI) - Math.PI;

        return new GeoPosition(Math.toDegrees(lat2), Math.toDegrees(lon2));
    }

    /**
     * Gets the initial bearing of the shortest path between two points
     * @param p1 Initial point
     * @param p2 Destiny point
     * @return Initial bearing in radians
     */
    public static double bearing(GeoPosition p1, GeoPosition p2) {
        double dLon = Math.toRadians(p2.getLongitude() - p1.getLongitude());
        double lat1 = Math.toRadians(p1.getLatitude());
        double lat2 = Math.toRadians(p2.getLatitude());

        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);

        return Math.atan2(y, x);
    }
}
