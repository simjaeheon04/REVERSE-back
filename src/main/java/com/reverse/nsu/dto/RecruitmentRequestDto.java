package com.reverse.nsu.dto;

import com.reverse.nsu.entity.Recruitment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RecruitmentRequestDto {

    private Integer recruitmentId;
    private String title;
    private String description;
    private LocalDateTime applyStartDate;
    private LocalDateTime applyEndDate;
    private Boolean isActive;
    private String updatedBy;

    // 상세 페이지 수정용
    @Getter @Setter
    public static class PageUpdate {
        private Integer roleId;
        private String heroYear;
        private String heroTitle;
        private String heroSubTitle;
        private String heroBtnText;
        private String heroBgUrl;
        private List<IntroDto> intros;
        private List<CardDto> cards;
        private List<GalleryDto> galleries;
        private List<ContactDto> contacts;
    }

    // [신규] 면접 슬롯 수정용
    @Getter @Setter
    public static class InterviewSlotUpdate {
        private Integer roleId;
        @NotNull(message = "면접 슬롯 목록은 필수입니다.")
        private List<@Valid SlotDto> slots;
    }

    @Getter @Setter
    public static class SlotDto {
        private Integer slotId;

        @NotNull(message = "면접 날짜는 필수입니다.")
        private LocalDate slotDate;

        private LocalTime startTime;
        private LocalTime endTime;

        @NotNull(message = "면접 슬롯 정원은 필수입니다.")
        @Positive(message = "면접 슬롯 정원은 1명 이상이어야 합니다.")
        private Integer capacity;

        private Boolean isActive;
    }

    // 리스트 항목 DTO들
    @Getter @Setter public static class IntroDto { private String contents; private Integer sortOrder; }
    @Getter @Setter public static class CardDto { private String applyField; private String cardTitle; private String cardSubTitle; private String cardDesc; private String imageUrl; private Integer sortOrder; }
    @Getter @Setter public static class GalleryDto { private String imageUrl; private String imageDesc; private String tag; private Integer sortOrder; }
    @Getter @Setter public static class ContactDto { private String contactType; private String label; private String value; private String subValue; private Integer sortOrder; }

    public Recruitment toEntity() {
        return Recruitment.builder()
                .title(this.title)
                .description(this.description)
                .applyStartDate(this.applyStartDate)
                .applyEndDate(this.applyEndDate)
                .isActive(this.isActive != null ? this.isActive : true)
                .updatedBy(this.updatedBy)
                .build();
    }
}
