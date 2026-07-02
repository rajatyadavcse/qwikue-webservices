package com.microservice.LoginService.service;

import com.microservice.LoginService.exception.ApiException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Value("${spring.application.name:identity-service}")
    private String appName;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Sends an email verification OTP to a newly created user.
     */
    public void sendEmailVerificationOtp(String toEmail, String firstName, String otp) {
        String subject = "Verify your email – " + appName;
        String body = buildVerificationEmailBody(firstName, otp);
        send(toEmail, subject, body);
    }

    /**
     * Sends a password-reset OTP to the user's verified email.
     */
    public void sendPasswordResetOtp(String toEmail, String firstName, String otp) {
        String subject = "Password reset OTP – " + appName;
        String body = buildPasswordResetEmailBody(firstName, otp);
        send(toEmail, subject, body);
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private void send(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("EmailService: sent '{}' email to {}", subject, to);
        } catch (MessagingException e) {
            log.error("EmailService: failed to build MIME message for {}: {}", to, e.getMessage());
            throw new ApiException("Failed to send email. Please try again later.", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (MailException e) {
            log.error("EmailService: mail send failure for {}: {}", to, e.getMessage());
            throw new ApiException("Failed to send email. Please try again later.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String buildVerificationEmailBody(String firstName, String otp) {
        return """
                <html>
                <body style="font-family: Arial, sans-serif; color: #333; max-width: 480px; margin: auto;">
                  <h2 style="color: #4A90E2;">Email Verification</h2>
                  <p>Hi <strong>%s</strong>,</p>
                  <p>Your account has been created. Use the OTP below to verify your email address.</p>
                  <div style="text-align:center; margin: 32px 0;">
                    <span style="font-size: 36px; font-weight: bold; letter-spacing: 8px;
                                 background: #f0f4ff; padding: 16px 32px; border-radius: 8px;
                                 display: inline-block;">%s</span>
                  </div>
                  <p style="color: #888; font-size: 13px;">This OTP is valid for <strong>5 minutes</strong>.
                  Do not share it with anyone.</p>
                  <hr style="border:none; border-top:1px solid #eee;"/>
                  <p style="color: #aaa; font-size: 12px;">If you did not request this, please ignore this email.</p>
                </body>
                </html>
                """.formatted(firstName, otp);
    }

    private String buildPasswordResetEmailBody(String firstName, String otp) {
        return """
                <html>
                <body style="font-family: Arial, sans-serif; color: #333; max-width: 480px; margin: auto;">
                  <h2 style="color: #E25C4A;">Password Reset</h2>
                  <p>Hi <strong>%s</strong>,</p>
                  <p>We received a request to reset your password. Use the OTP below.</p>
                  <div style="text-align:center; margin: 32px 0;">
                    <span style="font-size: 36px; font-weight: bold; letter-spacing: 8px;
                                 background: #fff4f0; padding: 16px 32px; border-radius: 8px;
                                 display: inline-block;">%s</span>
                  </div>
                  <p style="color: #888; font-size: 13px;">This OTP is valid for <strong>15 minutes</strong>.
                  Do not share it with anyone.</p>
                  <hr style="border:none; border-top:1px solid #eee;"/>
                  <p style="color: #aaa; font-size: 12px;">If you did not request a password reset, please ignore this email.</p>
                </body>
                </html>
                """
                .formatted(firstName, otp);
    }
}
