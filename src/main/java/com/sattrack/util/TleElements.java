package com.sattrack.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Parsed TLE mean elements.
 *
 * All angular quantities stored in radians for direct use by SGP4.
 * Mean motion stored in rad/min (the native SGP4 unit).
 *
 * WHY a record? TLE elements are immutable after parsing; records
 * communicate this intent and provide free equals/hashCode/toString.
 */
public record TleElements(
        String noradId,
        String name,
        double inclinationRad,
        double raanRad,            // Right Ascension of the Ascending Node
        double eccentricity,
        double argOfPerigeeRad,
        double meanAnomalyRad,
        double meanMotionRadPerMin,
        double bstar,              // drag term (1/earth radii)
        Instant epoch,
        int revolutionNumber
) {

    private static final double TWO_PI = 2.0 * Math.PI;
    private static final double DEG_TO_RAD = Math.PI / 180.0;
    private static final double REVS_PER_DAY_TO_RAD_PER_MIN = TWO_PI / 1440.0;

    /**
     * Parse a standard 2-line (or 3-line with name) TLE set.
     *
     * TLE format reference: https://celestrak.org/AFSPC-IS-0006A.pdf
     * Columns are 1-indexed per the standard.
     */
    public static TleElements parse(String line0, String line1, String line2) {
        // Strip and validate
        line1 = line1.trim();
        line2 = line2.trim();
        if (line1.length() < 69 || line2.length() < 69) {
            throw new IllegalArgumentException("TLE lines must be at least 69 characters: " + line1);
        }

        String name = (line0 != null) ? line0.trim() : "UNKNOWN";
        String noradId = line1.substring(2, 7).trim();

        // BSTAR drag term: signed decimal point assumed before the last 2 digits
        // Format: SXXXXX-X where S is sign, XXXXX is mantissa, -X is exponent
        double bstar = parseBstar(line1.substring(53, 61).trim());

        // Epoch: YYDDD.DDDDDDDD
        // Year is 2-digit; years >= 57 are 1957-1999, < 57 are 2000-2056
        double epochRaw = Double.parseDouble(line1.substring(18, 32).trim());
        Instant epoch = parseEpoch(epochRaw);

        // Line 2 elements
        double inclination   = Double.parseDouble(line2.substring(8, 16).trim()) * DEG_TO_RAD;
        double raan          = Double.parseDouble(line2.substring(17, 25).trim()) * DEG_TO_RAD;
        // Eccentricity: decimal point assumed before all digits (e.g., "0000001" → 0.0000001)
        double eccentricity  = Double.parseDouble("0." + line2.substring(26, 33).trim());
        double argOfPerigee  = Double.parseDouble(line2.substring(34, 42).trim()) * DEG_TO_RAD;
        double meanAnomaly   = Double.parseDouble(line2.substring(43, 51).trim()) * DEG_TO_RAD;
        // Mean motion in revolutions per day → radians per minute
        double meanMotion    = Double.parseDouble(line2.substring(52, 63).trim()) * REVS_PER_DAY_TO_RAD_PER_MIN;
        int revolutionNumber = Integer.parseInt(line2.substring(63, 68).trim());

        return new TleElements(
                noradId, name, inclination, raan, eccentricity,
                argOfPerigee, meanAnomaly, meanMotion, bstar, epoch, revolutionNumber
        );
    }

    /**
     * Parse the SGP4 BSTAR drag term from its compressed TLE representation.
     * Format: [+/-] XXXXX [+/-] N  (e.g., " 12345-3" → 0.12345e-3 = 0.00012345)
     */
    private static double parseBstar(String raw) {
        if (raw.isBlank() || raw.equals("00000-0") || raw.equals("+00000-0")) return 0.0;
        try {
            // Normalize: space means positive
            String s = raw.replace(" ", "+");
            // Find exponent sign position
            int expSignIdx = s.lastIndexOf('-');
            if (expSignIdx < 1) expSignIdx = s.lastIndexOf('+', s.length() - 1);
            if (expSignIdx <= 0) return 0.0;

            double mantissa = Double.parseDouble(s.substring(0, expSignIdx)) * 1e-5;
            int exponent = Integer.parseInt(s.substring(expSignIdx));
            return mantissa * Math.pow(10, exponent);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Convert TLE epoch (YYDDD.DDDDDDDD) to UTC Instant.
     */
    private static Instant parseEpoch(double epochRaw) {
        int year2digit = (int) (epochRaw / 1000.0);
        int year = (year2digit >= 57) ? (1900 + year2digit) : (2000 + year2digit);
        double dayOfYear = epochRaw - year2digit * 1000.0;

        // Day 1 = Jan 1; convert to Jan 1 midnight + fractional days
        LocalDate jan1 = LocalDate.of(year, 1, 1);
        long daySeconds = (long) ((dayOfYear - 1) * 86400.0);
        long nanos = (long) (((dayOfYear - 1) * 86400.0 - daySeconds) * 1e9);

        return jan1.atStartOfDay(ZoneOffset.UTC).toInstant()
                .plusSeconds(daySeconds)
                .plusNanos(nanos);
    }
}
