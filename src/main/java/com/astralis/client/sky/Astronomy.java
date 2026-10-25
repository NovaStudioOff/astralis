package com.astralis.client.sky;

/**
 * Éphémérides simplifiées (éléments képlériens de l'approximation JPL 1800-2050) : positions réelles
 * du Soleil, de la Lune et des planètes à la date courante, dans le repère écliptique J2000.
 */
public final class Astronomy {
    private Astronomy() {}

    public static final double OBLIQUITY = Math.toRadians(23.439281);

    // a (UA), e, I (deg), L (deg), longitude du périhélie (deg), longitude du nœud (deg) à J2000
    private static final double[][] EL = {
            {0.38709927, 0.20563593, 7.00497902, 252.25032350, 77.45779628, 48.33076593},    // 0 Mercure
            {0.72333566, 0.00677672, 3.39467605, 181.97909950, 131.60246718, 76.67984255},   // 1 Vénus
            {1.00000261, 0.01671123, -0.00001531, 100.46457166, 102.93768193, 0.0},          // 2 Terre-Lune
            {1.52371034, 0.09339410, 1.84969142, -4.55343205, -23.94362959, 49.55953891},    // 3 Mars
            {5.20288700, 0.04838624, 1.30439695, 34.39644051, 14.72847983, 100.47390909},    // 4 Jupiter
            {9.53667594, 0.05386179, 2.48599187, 49.95424423, 92.59887831, 113.66242448}};   // 5 Saturne
    // variations par siècle
    private static final double[][] RT = {
            {0.00000037, 0.00001906, -0.00594749, 149472.67411175, 0.16047689, -0.12534081},
            {0.00000390, -0.00004107, -0.00078890, 58517.81538729, 0.00268329, -0.27769418},
            {0.00000562, -0.00004392, -0.01294668, 35999.37244981, 0.32327364, 0.0},
            {0.00001847, 0.00007882, -0.00813131, 19140.30268499, 0.44441088, -0.29257343},
            {-0.00011607, -0.00013253, -0.00183714, 3034.74612775, 0.21252668, 0.20469106},
            {-0.00125060, -0.00050991, 0.00193609, 1222.49362201, -0.41897216, -0.28867794}};

    /** Planètes visibles à l'œil nu : indices dans EL. */
    public static final int[] PLANETS = {0, 1, 3, 4, 5};
    public static final String[] NAMES = {"Mercure", "Vénus", "Mars", "Jupiter", "Saturne"};
    public static final double[] MAGNITUDE = {0.0, -4.0, 0.6, -2.3, 0.7};
    public static final float[][] COLOR = {
            {0.85f, 0.82f, 0.78f}, {1.00f, 0.97f, 0.88f}, {1.00f, 0.60f, 0.42f},
            {1.00f, 0.93f, 0.80f}, {1.00f, 0.90f, 0.68f}};

    public static double julianDate(long millis) {
        return millis / 86400000.0 + 2440587.5;
    }

    public static double centuries(double jd) {
        return (jd - 2451545.0) / 36525.0;
    }

    private static double[] helio(int i, double t) {
        double a = EL[i][0] + RT[i][0] * t;
        double e = EL[i][1] + RT[i][1] * t;
        double inc = Math.toRadians(EL[i][2] + RT[i][2] * t);
        double l = EL[i][3] + RT[i][3] * t;
        double w = EL[i][4] + RT[i][4] * t;
        double om = Math.toRadians(EL[i][5] + RT[i][5] * t);

        double m = Math.toRadians(((l - w) % 360.0 + 540.0) % 360.0 - 180.0);
        double ecc = m + e * Math.sin(m);
        for (int k = 0; k < 8; k++) {
            ecc -= (ecc - e * Math.sin(ecc) - m) / (1.0 - e * Math.cos(ecc));
        }
        double xp = a * (Math.cos(ecc) - e);
        double yp = a * Math.sqrt(1.0 - e * e) * Math.sin(ecc);

        double arg = Math.toRadians(w) - om;
        double cw = Math.cos(arg), sw = Math.sin(arg);
        double co = Math.cos(om), so = Math.sin(om);
        double ci = Math.cos(inc), si = Math.sin(inc);
        double x = (cw * co - sw * so * ci) * xp + (-sw * co - cw * so * ci) * yp;
        double y = (cw * so + sw * co * ci) * xp + (-sw * so + cw * co * ci) * yp;
        double z = (sw * si) * xp + (cw * si) * yp;
        return new double[]{x, y, z};
    }

    /** Direction unitaire Terre -> Soleil (écliptique). */
    public static double[] sunDirection(double t) {
        double[] e = helio(2, t);
        return normalize(new double[]{-e[0], -e[1], -e[2]});
    }

    /** Direction unitaire géocentrique d'une planète (indice dans EL). */
    public static double[] planetDirection(int index, double t) {
        double[] p = helio(index, t);
        double[] e = helio(2, t);
        return normalize(new double[]{p[0] - e[0], p[1] - e[1], p[2] - e[2]});
    }

    /** Direction unitaire géocentrique de la Lune (série de Meeus abrégée). */
    public static double[] moonDirection(double t) {
        double lp = 218.3164477 + 481267.88123421 * t;
        double d = Math.toRadians(297.8501921 + 445267.1114034 * t);
        double m = Math.toRadians(357.5291092 + 35999.0502909 * t);
        double mp = Math.toRadians(134.9633964 + 477198.8675055 * t);
        double f = Math.toRadians(93.2720950 + 483202.0175233 * t);

        double lon = Math.toRadians(lp + 6.289 * Math.sin(mp) + 1.274 * Math.sin(2 * d - mp)
                + 0.658 * Math.sin(2 * d) + 0.214 * Math.sin(2 * mp) - 0.186 * Math.sin(m) - 0.114 * Math.sin(2 * f));
        double lat = Math.toRadians(5.128 * Math.sin(f) + 0.281 * Math.sin(mp + f)
                + 0.278 * Math.sin(mp - f) + 0.173 * Math.sin(2 * d - f));
        return new double[]{Math.cos(lat) * Math.cos(lon), Math.cos(lat) * Math.sin(lon), Math.sin(lat)};
    }

    /** Angle de rotation de la Terre (ERA) en radians : orientation réelle du globe à la date donnée. */
    public static double earthRotationAngle(double jd) {
        double era = 2.0 * Math.PI * (0.7790572732640 + 1.00273781191135448 * (jd - 2451545.0));
        return era % (2.0 * Math.PI);
    }

    // ---------------------------------------------------------------- outils vectoriels

    public static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    public static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    public static double[] scale(double[] a, double s) {
        return new double[]{a[0] * s, a[1] * s, a[2] * s};
    }

    public static double[] add(double[] a, double[] b) {
        return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    public static double[] normalize(double[] a) {
        double n = Math.sqrt(dot(a, a));
        return n < 1e-12 ? new double[]{0, 1, 0} : new double[]{a[0] / n, a[1] / n, a[2] / n};
    }
}
