package com.notificationengine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Low-level email sender.
 * Only responsibility: talk to SMTP and send.
 * All retry logic is handled by NotificationProcessorService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailSenderService {

    private final JavaMailSender mailSender;

    /**
     * Sends an email. Throws exception if SMTP fails —
     * caller is responsible for catching and logging the failure.
     */
    public void send(String to, String subject, String body) {
        log.debug("Attempting to send email to: {}", to);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject != null ? subject : "(No Subject)");
        message.setText(body);

        mailSender.send(message); // throws MailException if SMTP is down
        log.info("Email successfully sent to: {}", to);
    }
}
