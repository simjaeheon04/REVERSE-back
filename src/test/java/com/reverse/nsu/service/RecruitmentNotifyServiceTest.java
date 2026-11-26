package com.reverse.nsu.service;

import com.reverse.nsu.entity.RecruitmentNotifyEmail;
import com.reverse.nsu.repository.RecruitmentNotifyEmailRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecruitmentNotifyServiceTest {

    @Mock
    private RecruitmentNotifyEmailRepository emailRepository;

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private RecruitmentNotifyService notifyService;

    @Test
    void recruitmentNoticeUsesNewWebsiteWithoutChangingOtherMailContent() {
        ReflectionTestUtils.setField(notifyService, "fromAddress", "sender@example.test");
        when(emailRepository.findAll()).thenReturn(List.of(
                RecruitmentNotifyEmail.builder().email("subscriber@example.test").build()));

        notifyService.notifySubscribers("2026 모집");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getTo()).containsExactly("subscriber@example.test");
        assertThat(message.getFrom()).isEqualTo("sender@example.test");
        assertThat(message.getSubject()).isEqualTo("[REVERSE] 📢 새로운 모집 공고가 등록되었습니다!");
        assertThat(message.getText()).isEqualTo(
                "안녕하세요!\n\nREVERSE에 새로운 모집 공고 [2026 모집]가 등록되었습니다.\n" +
                        "지금 바로 홈페이지에서 확인하고 지원해보세요!\n\n" +
                        "🔗 홈페이지: https://nsu-reverse.website");
    }
}
