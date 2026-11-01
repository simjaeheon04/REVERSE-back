package com.reverse.nsu.dto;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class RecruitmentResponseDto {
    private Integer recruitmentId;
    private String title;

    // [종호 님 요청 사항] 모집 기간 지정을 위한 날짜 데이터 추가
    private LocalDateTime applyStartDate; // 모집 시작일
    private LocalDateTime applyEndDate;   // 모집 종료일
    private Boolean isActive;

    // 화면정의서: 모집 기간 아닐 때 "지금은 신청 기간이 아닙니다" 문구 처리를 위한 상태값
    private Boolean isApplyPeriod;

    private PageDetails page;

    @Getter @Setter @Builder
    @NoArgsConstructor @AllArgsConstructor
    public static class PageDetails {
        private String year;     // 정의서: heroYear
        private String title;    // 정의서: heroTitle
        private String subtitle; // 정의서: heroSubTitle
        private String heroBgUrl; // 정의서: heroBgUrl
        private String heroBtnText;  // 정의서: "신청하기" 버튼 문구

        private List<IntroDetails> intros;
        private List<CardDetails> cards;     // 정의서: 모집 분야 설명 (카테고리별)
        private List<GalleryDetails> galleries; // 정의서: 분야별 동아리 활동 사진
        private List<ContactDetails> contacts;  // 정의서: 문의 사항 (SNS, Phone 등)
        private List<SlotDetails> interviewSlots; // 정의서: 면접 일정 캘린더용 데이터
    }

    @Getter @Setter @Builder
    @NoArgsConstructor @AllArgsConstructor
    public static class IntroDetails {
        private String contents;
        private Integer sortOrder;
    }

    @Getter @Setter @Builder
    @NoArgsConstructor @AllArgsConstructor
    public static class CardDetails {
        private String applyField; // 정의서: "프론트, 백엔드, 서버 보안..."
        private String title;
        private String subTitle;
        private String desc;       // 정의서: 분야별 상세 설명
        private String imageUrl;
    }

    @Getter @Setter @Builder
    @NoArgsConstructor @AllArgsConstructor
    public static class GalleryDetails {
        private String imageUrl;
        private String imageDesc;
        private String tag;        // 정의서: 활동 사진 카테고리 구분용
    }

    @Getter @Setter @Builder
    @NoArgsConstructor @AllArgsConstructor
    public static class ContactDetails {
        private String type;       // SNS, Phone, Place 등
        private String label;
        private String value;
        private String subValue;
    }

    @Getter @Setter @Builder
    @NoArgsConstructor @AllArgsConstructor
    public static class SlotDetails {
        private Integer slotId;
        private String date;
        private String time;
        private LocalTime startTime;
        private LocalTime endTime;
        private Boolean isAvailable; // 정의서: 면접 기간 외 선택 불가 로직용
    }

    // [최종 수정] 지원자 목록 및 상세 정보를 위한 내부 클래스
    @Getter @Setter @Builder
    @NoArgsConstructor @AllArgsConstructor
    public static class ApplicationDetails {
        private Integer applicationId;
        private Integer recruitmentId;
        private String applicantName;
        private String department;
        private String studentNumber;
        private String phoneNumber;

        // [수정] 엔티티와 타입을 Byte로 일치시켜 빌더 에러 해결
        private Byte grade;

        private String email;
        private String portfolioUrl;
        private List<String> categories;
        private Integer interviewSlotId;
        private LocalDate interviewDate;
        private LocalTime interviewStartTime;
        private LocalTime interviewEndTime;
        private Integer termsAgreed;
        private String status;
        private LocalDateTime createdDate;
        private LocalDateTime modifiedDate;
    }
}
