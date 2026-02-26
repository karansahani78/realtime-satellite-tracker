package com.sattrack.util;

import java.time.Instant;

/**
 * Converts ECI (Earth-Centered Inertial) coordinates to geodetic (lat/lon/alt)
 * and computes observer-relative angles (azimuth, elevation, range).
 *
 * WHY two coordinate systems?
 * - SGP4 outputs ECI (non-rotating frame fixed to stars) which is mathematically
 *   convenient for orbit propagation.
 * - Users need geodetic (lat/lon/alt) which rotates with the Earth and is what
 *   maps display.
 * - The conversion requires knowing Earth's rotation angle at a specific time
 *   (Greenwich Sidereal Time).
 */
public final class CoordinateConverter {

    private static final double RE = 6378.137;          // km, WGS-84 equatorial radius
    private static final double F  = 1.0 / 298.257223563; // flattening
    private static final double E2 = 2 * F - F * F;    // eccentricity squared
    private static final double TWO_PI = 2.0 * Math.PI;

    private CoordinateConverter() {}

    /**
     * Convert ECI position to geodetic coordinates.
     *
     * Uses Bowring's iterative method for accurate altitude calculation.
     * Convergence typically achieved in 2-3 iterations for LEO satellites.
     *
     * @param state  ECI state from SGP4
     * @param epoch  UTC time (needed to compute GMST for frame rotation)
     * @return geodetic coordinates (degrees, km)
     */
    public static GeodeticCoords eciToGeodetic(Sgp4Propagator.EciState state, Instant epoch) {
        double gmst = computeGmst(epoch);

        // Rotate from ECI to ECEF using GMST
        double cosTheta = Math.cos(gmst);
        double sinTheta = Math.sin(gmst);
        double xecef =  state.x() * cosTheta + state.y() * sinTheta;
        double yecef = -state.x() * sinTheta + state.y() * cosTheta;
        double zecef =  state.z();

        // ECEF → geodetic (iterative Bowring's method)
        double p = Math.sqrt(xecef * xecef + yecef * yecef);
        double lon = Math.atan2(yecef, xecef);

        // Initial estimate
        double lat = Math.atan2(zecef, p * (1.0 - E2));
        double latPrev;
        int iter = 0;
        do {
            latPrev = lat;
            double sinLat = Math.sin(lat);
            double N = RE / Math.sqrt(1.0 - E2 * sinLat * sinLat);
            lat = Math.atan2(zecef + E2 * N * sinLat, p);
        } while (Math.abs(lat - latPrev) > 1e-10 && ++iter < 10);

        double sinLat = Math.sin(lat);
        double N = RE / Math.sqrt(1.0 - E2 * sinLat * sinLat);
        double alt = (p / Math.cos(lat)) - N;   // km above WGS-84 ellipsoid

        return new GeodeticCoords(
                Math.toDegrees(lat),
                Math.toDegrees(lon),
                alt,
                state.speed()
        );
    }

    /**
     * Compute topocentric look angles from observer to satellite.
     * Returns azimuth (0=N, 90=E), elevation, and slant range.
     */
    public static LookAngles computeLookAngles(
            GeodeticCoords satellite,
            double obsLatDeg,
            double obsLonDeg,
            double obsAltKm) {

        double obsLat = Math.toRadians(obsLatDeg);
        double obsLon = Math.toRadians(obsLonDeg);
        double satLat = Math.toRadians(satellite.latitudeDeg());
        double satLon = Math.toRadians(satellite.longitudeDeg());

        double dLon = satLon - obsLon;
        double cosLat = Math.cos(satLat);

        // South/East components in km
        double dSouth = (RE + obsAltKm) * (satLat - obsLat);
        double dEast  = (RE + obsAltKm) * Math.cos(obsLat) * dLon;
        double dUp    = satellite.altitudeKm() - obsAltKm;

        double range = Math.sqrt(dSouth * dSouth + dEast * dEast + dUp * dUp);
        double el = Math.atan2(dUp, Math.sqrt(dSouth * dSouth + dEast * dEast));
        double az = Math.atan2(dEast, -dSouth);
        if (az < 0) az += TWO_PI;

        return new LookAngles(
                Math.toDegrees(az),
                Math.toDegrees(el),
                range
        );
    }

    /**
     * Compute Greenwich Mean Sidereal Time (GMST) in radians.
     *
     * Uses the IAU 1982 formula which is accurate to 0.1 arcseconds for
     * dates near J2000. Sufficient precision for SGP4 which already has
     * errors on the order of km after several days.
     */
    public static double computeGmst(Instant time) {
        // Julian date
        double jd = toJulianDate(time);
        double T = (jd - 2451545.0) / 36525.0;  // Julian centuries from J2000.0

        // IAU 1982 GMST formula (in degrees)
        double gmst = 280.46061837 + 360.98564736629 * (jd - 2451545.0)
                + T * T * (0.000387933 - T / 38710000.0);

        return Math.toRadians(gmst % 360.0);
    }

    private static double toJulianDate(Instant instant) {
        // Unix epoch (1970-01-01) = JD 2440587.5
        return 2440587.5 + instant.getEpochSecond() / 86400.0
                + instant.getNano() / 86400.0e9;
    }

    // --- Value objects ---

    public record GeodeticCoords(
            double latitudeDeg,
            double longitudeDeg,
            double altitudeKm,
            double speedKmPerS
    ) {}

    public record LookAngles(
            double azimuthDeg,
            double elevationDeg,
            double rangeKm
    ) {}
}
