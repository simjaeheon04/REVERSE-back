package com.reverse.nsu.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setSuperAdminEmail() {
        ReflectionTestUtils.setField(emailService, "superAdminEmail", "admin@example.test");
    }

    @Test
    void auditMailFailureDoesNotPropagateToCommittedAdminOperation() {
        doThrow(new MailSendException("test mail failure"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> emailService.sendAuditLog("admin01", "면접 스케줄 배정"))
                .doesNotThrowAnyException();

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void auditMailIsStillSentWhenMailServerIsAvailable() {
        emailService.sendAuditLog("admin01", "면접 스케줄 배정");

        verify(mailSender).send(any(SimpleMailMessage.class));
    }
}
