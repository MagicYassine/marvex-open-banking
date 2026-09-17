package com.marvex.service;

import com.marvex.constants.EmailTemplates;
import com.marvex.enums.PaymentStatus;
import com.marvex.exception.AppException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private static final String INVOICE_TEMPLATE_PATH = "templates/invoice-email.html";

    private final JavaMailSender mailSender;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${spring.mail.username}")
    private String from;

    public void sendVerificationEmail(String to, String token) {
        String link = baseUrl + "/complete-registration?token=" + token;
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject(EmailTemplates.VERIFICATION_SUBJECT);
        msg.setText(EmailTemplates.VERIFICATION_BODY.formatted(link));
        mailSender.send(msg);
    }

    public void sendPaymentNotification(String to, String businessName, long amountPence, String status) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        String emoji = PaymentStatus.PAID.name().equals(status) ? "✅" : "❌";
        msg.setSubject(emoji + " Payment " + status.toLowerCase() + " — £" + String.format("%.2f", amountPence / 100.0));
        msg.setText("Hi " + businessName + ",\n\nA payment of £" + String.format("%.2f", amountPence / 100.0)
            + " has been " + status.toLowerCase() + ".\n\nView your dashboard: " + baseUrl + "/dashboard");
        mailSender.send(msg);
    }

    public void sendWelcomeEmail(String to, String businessName) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject("Welcome to Marvex, " + businessName + "!");
        msg.setText("Hi " + businessName + ",\n\nYour Marvex account is now active and ready to accept payments.\n\nLog in at: " + baseUrl + "/login\n\nThe Marvex Team");
        mailSender.send(msg);
    }

    public void sendPasswordResetEmail(String to, String token) {
        String link = baseUrl + "/reset-password?token=" + token;
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject(EmailTemplates.PASSWORD_RESET_SUBJECT);
        msg.setText(EmailTemplates.PASSWORD_RESET_BODY.formatted(link));
        mailSender.send(msg);
    }

    public void sendInvoiceEmail(String to, String businessName, UUID paymentId, long amountPence, String currency, Instant paidAt) {
        try {
            String safeName = HtmlUtils.htmlEscape(businessName);
            String date = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm z")
                .withZone(ZoneId.of("UTC"))
                .format(paidAt);
            String amount = String.format("%.2f", amountPence / 100.0);
            String html = loadInvoiceTemplate()
                .replace("{{businessName}}", safeName)
                .replace("{{paymentId}}", paymentId.toString())
                .replace("{{date}}", date)
                .replace("{{currency}}", currency)
                .replace("{{amount}}", amount)
                .replace("{{baseUrl}}", baseUrl);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("Invoice — " + currency + " " + amount + " from " + safeName);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("[EmailService] Failed to send invoice email - paymentId={}, error={}", paymentId, e.getMessage(), e);
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send invoice email");
        }
    }

    private String loadInvoiceTemplate() throws IOException {
        return StreamUtils.copyToString(new ClassPathResource(INVOICE_TEMPLATE_PATH).getInputStream(), StandardCharsets.UTF_8);
    }
}