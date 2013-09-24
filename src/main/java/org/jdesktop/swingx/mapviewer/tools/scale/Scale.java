package org.jdesktop.swingx.mapviewer.tools.scale;

/**
 * @author fgotusso <fgotusso@swissms.ch>
 */
public enum Scale {
    METER(1, "m"),
    KILOMETER(1000, "km"),
    FOOT(0.3048, "ft"),
    MILE(1609.344, "mi");

    private final double factor;
    private final String symbol;

    private Scale(double factor, String symbol) {
        this.factor = factor;
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    public static double transform(double value, Scale unit, Scale destiny) {
        return value * unit.factor / destiny.factor;
    }
}
