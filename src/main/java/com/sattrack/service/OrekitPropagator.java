package com.sattrack.service;

import com.sattrack.entity.TleRecord;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.data.DataContext;
import org.orekit.data.DataProvidersManager;
import org.orekit.data.DirectoryCrawler;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;
import org.orekit.utils.PVCoordinates;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.Instant;

@Component
@Slf4j
public class OrekitPropagator {

    @Value("${orekit.data.path}")
    private String orekitDataPath;

    private Frame itrf;
    private OneAxisEllipsoid earth;

    // INITIALIZATION (SAFE — no static block)
    @PostConstruct
    public void initOrekit() {
        try {
            DataProvidersManager manager =
                    DataContext.getDefault().getDataProvidersManager();

            // Load orekit-data.zip from classpath
            manager.addProvider(
                    new org.orekit.data.ZipJarCrawler(
                            this.getClass().getResource("/orekit-data-main.zip")
                    )
            );

            this.itrf = FramesFactory.getITRF(IERSConventions.IERS_2010, true);

            this.earth = new OneAxisEllipsoid(
                    Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
                    Constants.WGS84_EARTH_FLATTENING,
                    itrf
            );

            log.info("Orekit initialized successfully from ZIP");

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Orekit", e);
        }
    }

    // RECORDS

    public record SatState(
            double latDeg,
            double lonDeg,
            double altKm,
            double speedKms,
            PVCoordinates pvInertial
    ) {}

    public record LookAngles(
            double elevationDeg,
            double azimuthDeg,
            double rangeKm,
            double rangeRateKms
    ) {}

    // ─────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────

    public SatState propagate(TleRecord tle, Instant at) {
        AbsoluteDate date = toAbsoluteDate(at);
        TLEPropagator propagator = buildPropagator(tle);
        SpacecraftState state = propagator.propagate(date);

        PVCoordinates pv = state.getPVCoordinates(itrf);
        GeodeticPoint gp = earth.transform(pv.getPosition(), itrf, date);

        double speed = pv.getVelocity().getNorm() / 1000.0;

        return new SatState(
                Math.toDegrees(gp.getLatitude()),
                Math.toDegrees(gp.getLongitude()),
                gp.getAltitude() / 1000.0,
                speed,
                state.getPVCoordinates()
        );
    }

    public LookAngles lookAngles(TleRecord tle, Instant at,
                                 double observerLat, double observerLon,
                                 double observerAltM) {

        AbsoluteDate date = toAbsoluteDate(at);
        TLEPropagator propagator = buildPropagator(tle);
        SpacecraftState state = propagator.propagate(date);

        PVCoordinates satPV = state.getPVCoordinates(itrf);
        Vector3D satPos = satPV.getPosition();
        Vector3D satVel = satPV.getVelocity();

        GeodeticPoint obsGP = new GeodeticPoint(
                Math.toRadians(observerLat),
                Math.toRadians(observerLon),
                observerAltM);

        Vector3D obsPos = earth.transform(obsGP);

        Vector3D diff = satPos.subtract(obsPos);
        double diffNorm = diff.getNorm();

        Vector3D obsNormal = obsPos.normalize();
        double sinEl = Vector3D.dotProduct(diff, obsNormal) / diffNorm;
        double elDeg = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, sinEl))));

        Vector3D north = northVector(obsGP);
        Vector3D east = eastVector(obsGP);

        double dNorth = Vector3D.dotProduct(diff, north) / diffNorm;
        double dEast = Vector3D.dotProduct(diff, east) / diffNorm;

        double azDeg = Math.toDegrees(Math.atan2(dEast, dNorth));
        if (azDeg < 0) azDeg += 360.0;

        Vector3D unitRange = diff.normalize();
        double rangeRateMs = Vector3D.dotProduct(satVel, unitRange);
        double rangeRateKms = rangeRateMs / 1000.0;

        return new LookAngles(elDeg, azDeg, diffNorm / 1000.0, rangeRateKms);
    }

    public double distanceBetween(TleRecord tleA, TleRecord tleB, Instant at) {
        AbsoluteDate date = toAbsoluteDate(at);

        Vector3D posA = buildPropagator(tleA)
                .propagate(date)
                .getPVCoordinates(itrf)
                .getPosition();

        Vector3D posB = buildPropagator(tleB)
                .propagate(date)
                .getPVCoordinates(itrf)
                .getPosition();

        return posA.subtract(posB).getNorm() / 1000.0;
    }

    public double relativeVelocity(TleRecord tleA, TleRecord tleB, Instant at) {
        AbsoluteDate date = toAbsoluteDate(at);

        Vector3D velA = buildPropagator(tleA)
                .propagate(date)
                .getPVCoordinates(itrf)
                .getVelocity();

        Vector3D velB = buildPropagator(tleB)
                .propagate(date)
                .getPVCoordinates(itrf)
                .getVelocity();

        return velA.subtract(velB).getNorm() / 1000.0;
    }

    // INTERNAL HELPERS

    private TLEPropagator buildPropagator(TleRecord tle) {
        return TLEPropagator.selectExtrapolator(
                new TLE(tle.getLine1(), tle.getLine2()));
    }

    private AbsoluteDate toAbsoluteDate(Instant instant) {
        return new AbsoluteDate(instant.toString(), TimeScalesFactory.getUTC());
    }

    private Vector3D northVector(GeodeticPoint gp) {
        double lat = gp.getLatitude();
        double lon = gp.getLongitude();
        return new Vector3D(
                -Math.sin(lat) * Math.cos(lon),
                -Math.sin(lat) * Math.sin(lon),
                Math.cos(lat));
    }

    private Vector3D eastVector(GeodeticPoint gp) {
        double lon = gp.getLongitude();
        return new Vector3D(
                -Math.sin(lon),
                Math.cos(lon),
                0.0);
    }
}