package com.reverse.nsu.service;

import com.reverse.nsu.entity.RecruitmentNotifyEmail;
import com.reverse.nsu.repository.RecruitmentNotifyEmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value; // @Value를 위해 추가
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class RecruitmentNotifyService {

    private final RecruitmentNotifyEmailRepository emailRepository;
    private final JavaMailSender mailSender;

    /**
     * application.yml의 spring.mail.username 값을 주입받습니다.
     * 이제 이메일이 바뀌어도 코드 수정 없이 yml 파일만 고치면 됩니다.
     */
    @Value("${spring.mail.username}")
    private String fromAddress;

    /**
     * 1. 알림 구독 등록
     */
    @Transactional
    public void subscribe(String email) {
        if (emailRepository.existsByEmail(email)) {
            throw new IllegalStateException("이미 구독 중인 이메일입니다.");
        }

        RecruitmentNotifyEmail notifyEmail = RecruitmentNotifyEmail.builder()
                .email(email)
                .isActive(true)
                .build();

        emailRepository.save(notifyEmail);
        log.info(">>>> [구독 등록 완료] 이메일: {}", email);
    }

    /**
     * 2. 알림 구독 해지
     */
    @Transactional
    public void unsubscribe(String email) {
        if (!emailRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("구독 정보가 없는 이메일입니다.");
        }
        emailRepository.deleteByEmail(email);
        log.info(">>>> [구독 해지 완료] 이메일: {}", email);
    }

    /**
     * 3. 모든 구독자에게 새 공고 알림 발송 (비동기 처리)
     */
    @Async("mailExecutor")
    @Transactional(readOnly = true)
    public void notifySubscribers(String recruitmentTitle) {
        List<RecruitmentNotifyEmail> subscribers = emailRepository.findAll();

        if (subscribers.isEmpty()) {
            log.info(">>>> [알림 스킵] 구독자가 존재하지 않습니다.");
            return;
        }

        log.info(">>>> [알림 발송 시작] 총 {}명에게 메일을 발송합니다. 공고명: {}", subscribers.size(), recruitmentTitle);

        for (RecruitmentNotifyEmail subscriber : subscribers) {
            try {
                sendEmail(subscriber.getEmail(), recruitmentTitle);
            } catch (Exception e) {
                log.error(">>>> [발송 실패] 대상: {}, 사유: {}", subscriber.getEmail(), e.getMessage());
            }
        }
        log.info(">>>> [알림 발송 종료]");
    }

    /**
     * 실제 메일 전송 로직
     */
    private void sendEmail(String toAddress, String title) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toAddress);

        // 하드코딩 대신 주입받은 변수(fromAddress)를 사용합니다.
        message.setFrom(fromAddress);

        message.setSubject("[REVERSE] 📢 새로운 모집 공고가 등록되었습니다!");
        message.setText(String.format(
                "안녕하세요!\n\nREVERSE에 새로운 모집 공고 [%s]가 등록되었습니다.\n" +
                        "지금 바로 홈페이지에서 확인하고 지원해보세요!\n\n" +
                        "🔗 홈페이지: https://nsu-reverse.website",
                title
        ));

        mailSender.send(message);
    }
}