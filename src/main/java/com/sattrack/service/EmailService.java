package com.sattrack.service;

import com.sattrack.entity.ConjunctionAlert;
import com.sattrack.entity.Notification;
import com.sattrack.entity.PassPrediction;
import com.sattrack.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;


@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneId.of("UTC"));

    private final JavaMailSender              mailSender;
    private final UserRepository              userRepository;

    @org.springframework.beans.factory.annotation.Value("${sattrack.mail.from}")
    private String fromAddress;

    @org.springframework.beans.factory.annotation.Value("${sattrack.mail.base-url}")
    private String baseUrl;

    // ─────────────────────────────────────────────────────────────────────────
    // Pass Alert Email
    // ─────────────────────────────────────────────────────────────────────────

    @Async
    public void sendPassAlert(Long userId, PassPrediction pass, Notification notif) {
        userRepository.findById(userId).ifPresent(user -> {
            if (user.getEmail() == null) return;
            try {
                String html = buildPassEmailHtml(pass);
                send(user.getEmail(),
                        "🛰 Pass Alert: " + pass.getSatelliteName() + " at " + FMT.format(pass.getAos()),
                        html);
            } catch (Exception ex) {
                log.warn("Pass alert email failed for user {}: {}", userId, ex.getMessage());
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Conjunction Alert Email
    // ─────────────────────────────────────────────────────────────────────────

    @Async
    public void sendConjunctionAlert(Long userId, ConjunctionAlert conj, Notification notif) {
        userRepository.findById(userId).ifPresent(user -> {
            if (user.getEmail() == null) return;
            try {
                String html = buildConjunctionEmailHtml(conj);
                send(user.getEmail(),
                        "⚠ " + conj.getRiskLevel() + " Conjunction Alert — " +
                                conj.getSatelliteNameA() + " & " + conj.getSatelliteNameB(),
                        html);
            } catch (Exception ex) {
                log.warn("Conjunction email failed for user {}: {}", userId, ex.getMessage());
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HTML Templates
    // ─────────────────────────────────────────────────────────────────────────

    private String buildPassEmailHtml(PassPrediction pass) {
        long dur = pass.getDurationSeconds();
        return """
            <!DOCTYPE html><html><body style="font-family:monospace;background:#030718;color:#e2f0ff;padding:24px;">
            <div style="max-width:520px;margin:auto;border:1px solid rgba(0,200,255,.2);padding:24px;border-radius:4px;">
              <h2 style="color:#00d4ff;letter-spacing:3px;font-size:14px;margin-bottom:16px;">
                🛰 PASS ALERT — %s
              </h2>
              <table style="width:100%%;border-collapse:collapse;font-size:12px;">
                <tr><td style="color:rgba(0,200,255,.5);padding:4px 0;">AOS</td>
                    <td style="color:#fff;text-align:right;">%s</td></tr>
                <tr><td style="color:rgba(0,200,255,.5);padding:4px 0;">MAX ELEVATION</td>
                    <td style="color:#39ff14;text-align:right;">%.1f°</td></tr>
                <tr><td style="color:rgba(0,200,255,.5);padding:4px 0;">DURATION</td>
                    <td style="color:#fff;text-align:right;">%d min %02d sec</td></tr>
                <tr><td style="color:rgba(0,200,255,.5);padding:4px 0;">DIRECTION</td>
                    <td style="color:#fff;text-align:right;">%.0f° → %.0f°</td></tr>
                <tr><td style="color:rgba(0,200,255,.5);padding:4px 0;">VISIBLE</td>
                    <td style="color:%s;text-align:right;">%s</td></tr>
              </table>
              <div style="margin-top:20px;text-align:center;">
                <a href="%s/satellites/%s" style="color:#00d4ff;font-size:11px;letter-spacing:2px;">
                  VIEW IN SATTRACK →
                </a>
              </div>
            </div></body></html>
            """.formatted(
                pass.getSatelliteName(),
                FMT.format(pass.getAos()),
                pass.getMaxElevation(),
                dur / 60, dur % 60,
                pass.getAosAzimuth(), pass.getLosAzimuth(),
                pass.isVisible() ? "#39ff14" : "#888",
                pass.isVisible() ? "YES — NAKED EYE" : "NOT VISIBLE",
                baseUrl, pass.getNoradId());
    }

    private String buildConjunctionEmailHtml(ConjunctionAlert conj) {
        String riskColor = switch (conj.getRiskLevel()) {
            case CRITICAL -> "#ff1744";
            case HIGH     -> "#ff6b35";
            case MEDIUM   -> "#ffd700";
            case LOW      -> "#39ff14";
        };
        return """
            <!DOCTYPE html><html><body style="font-family:monospace;background:#030718;color:#e2f0ff;padding:24px;">
            <div style="max-width:520px;margin:auto;border:1px solid %s;padding:24px;border-radius:4px;">
              <h2 style="color:%s;letter-spacing:3px;font-size:14px;">
                ⚠ %s CONJUNCTION ALERT
              </h2>
              <p style="font-size:12px;color:rgba(200,220,255,.8);margin:12px 0;">
                Two satellites are predicted to pass dangerously close.
              </p>
              <table style="width:100%%;border-collapse:collapse;font-size:12px;">
                <tr><td style="color:rgba(0,200,255,.5);padding:4px 0;">SATELLITE A</td>
                    <td style="color:#fff;text-align:right;">%s (NORAD %s)</td></tr>
                <tr><td style="color:rgba(0,200,255,.5);padding:4px 0;">SATELLITE B</td>
                    <td style="color:#fff;text-align:right;">%s (NORAD %s)</td></tr>
                <tr><td style="color:rgba(0,200,255,.5);padding:4px 0;">TCA</td>
                    <td style="color:#fff;text-align:right;">%s</td></tr>
                <tr><td style="color:rgba(0,200,255,.5);padding:4px 0;">MISS DISTANCE</td>
                    <td style="color:%s;text-align:right;">%.3f km</td></tr>
                <tr><td style="color:rgba(0,200,255,.5);padding:4px 0;">RELATIVE SPEED</td>
                    <td style="color:#fff;text-align:right;">%.2f km/s</td></tr>
              </table>
              <div style="margin-top:20px;text-align:center;">
                <a href="%s/conjunctions" style="color:#00d4ff;font-size:11px;letter-spacing:2px;">
                  VIEW ALL ALERTS →
                </a>
              </div>
            </div></body></html>
            """.formatted(
                riskColor, riskColor, conj.getRiskLevel(),
                conj.getSatelliteNameA(), conj.getNoradIdA(),
                conj.getSatelliteNameB(), conj.getNoradIdB(),
                FMT.format(conj.getTca()),
                riskColor, conj.getMissDistanceKm(),
                conj.getRelativeSpeedKms(),
                baseUrl);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Delivery
    // ─────────────────────────────────────────────────────────────────────────

    private void send(String to, String subject, String html) throws Exception {
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
        helper.setFrom(fromAddress);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
        mailSender.send(msg);
        log.debug("Email sent to {} — {}", to, subject);
    }
}