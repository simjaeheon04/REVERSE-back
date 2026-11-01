package com.reverse.nsu.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.super-admin-email}")
    private String superAdminEmail;

    public void sendVerificationCode(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("[REVERSE] 이메일 인증번호");
        message.setText("인증번호: " + code + "\n\n유효시간은 5분입니다.");
        mailSender.send(message);
    }

    /**
     * 관리자 작업 발생 시 최고관리자에게 변경 이력 알림 메일을 발송한다.
     *
     * @param changedBy 변경을 수행한 사용자 ID
     * @param action    변경 내용 (예: "동아리 소개 등록 - 제목: REVERSE")
     */
    public void sendAuditLog(String changedBy, String action) {
        try {
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(superAdminEmail);
            message.setSubject("[REVERSE] 관리자 변경 이력 알림");
            message.setText(
                    "변경자 ID : " + changedBy + "\n" +
                    "변경 내용 : " + action + "\n" +
                    "변경 일시 : " + timestamp
            );
            mailSender.send(message);
        } catch (Exception e) {
            log.error("관리자 감사 메일 발송에 실패했습니다. changedBy={}, action={}", changedBy, action, e);
        }
    }
}
