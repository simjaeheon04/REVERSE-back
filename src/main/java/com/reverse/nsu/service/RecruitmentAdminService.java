package com.reverse.nsu.service;

import com.reverse.nsu.dto.ApplicationInterviewScheduleRequestDto;
import com.reverse.nsu.dto.RecruitmentRequestDto;
import com.reverse.nsu.dto.RecruitmentResponseDto;
import com.reverse.nsu.entity.*;
import com.reverse.nsu.exception.InvalidRecruitmentRequestException;
import com.reverse.nsu.exception.InterviewSlotConflictException;
import com.reverse.nsu.repository.ApplicationInterviewScheduleRepository;
import com.reverse.nsu.repository.RecruitmentApplicationRepository;
import com.reverse.nsu.repository.RecruitmentInterviewSlotRepository;
import com.reverse.nsu.repository.RecruitmentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecruitmentAdminService {

    private final RecruitmentRepository recruitmentRepository;
    private final RecruitmentApplicationRepository applicationRepository;
    private final ApplicationInterviewScheduleRepository interviewScheduleRepository;
    private final RecruitmentInterviewSlotRepository interviewSlotRepository;
    private final EntityManager entityManager;

    /**
     * 관리자 권한 검증 (1: SUPER_ADMIN, 2: ADMIN)
     */
    public void validateAdminRole(Integer roleId) {
        if (roleId == null || roleId > 2) {
            throw new RuntimeException("접근 권한이 없습니다. 관리자 권한이 필요합니다.");
        }
    }

    /**
     * 공고 및 상세 페이지 동시 생성
     */
    @Transactional
    public Recruitment createRecruitment(Map<String, Object> request, String adminId) {
        RecruitmentPage page = RecruitmentPage.builder()
                .heroTitle((String) request.get("title"))
                .heroSubTitle("동아리 상세 정보")
                .heroBtnText("지원하기")
                .heroYear("2026")
                .heroBgUrl("")
                .updatedBy(adminId)
                .isActive(true)
                .build();

        Recruitment recruitment = Recruitment.builder()
                .title((String) request.get("title"))
                .description((String) request.get("description"))
                .applyStartDate(LocalDateTime.parse((String) request.get("applyStartDate")))
                .applyEndDate(LocalDateTime.parse((String) request.get("applyEndDate")))
                .updatedBy(adminId)
                .isActive(readIsActive(request, true))
                .build();

        recruitment.setRecruitmentPage(page);
        page.setRecruitment(recruitment);

        return recruitmentRepository.save(recruitment);
    }

    /**
     * 공고 기본 정보 수정 로직
     */
    @Transactional
    public Boolean updateRecruitment(Integer id, Map<String, Object> request, String adminId) {
        Recruitment recruitment = recruitmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 공고가 존재하지 않습니다. ID: " + id));

        Boolean requestedIsActive = readIsActive(request, null);
        recruitment.update(
                (String) request.get("title"),
                (String) request.get("description"),
                LocalDateTime.parse((String) request.get("applyStartDate")),
                LocalDateTime.parse((String) request.get("applyEndDate")),
                adminId,
                requestedIsActive
        );
        return recruitment.getIsActive();
    }

    private Boolean readIsActive(Map<String, Object> request, Boolean missingValue) {
        if (!request.containsKey("isActive")) {
            return missingValue;
        }
        Object value = request.get("isActive");
        if (value instanceof Boolean isActive) {
            return isActive;
        }
        throw new InvalidRecruitmentRequestException("isActive는 true 또는 false여야 합니다.");
    }

    /**
     * 상세 페이지 통합 수정 로직 (Hero, Intro, Card, Gallery, Contact)
     */
    @Transactional
    public void updateRecruitmentPage(Integer recruitmentId, RecruitmentRequestDto.PageUpdate dto, String adminId) {
        Recruitment recruitment = recruitmentRepository.findById(recruitmentId)
                .orElseThrow(() -> new EntityNotFoundException("공고를 찾을 수 없습니다. ID: " + recruitmentId));

        RecruitmentPage page = recruitment.getRecruitmentPage();

        // 1. Hero 섹션 업데이트
        page.setHeroYear(dto.getHeroYear());
        page.setHeroTitle(dto.getHeroTitle());
        page.setHeroSubTitle(dto.getHeroSubTitle());
        page.setHeroBtnText(dto.getHeroBtnText());
        page.setHeroBgUrl(dto.getHeroBgUrl());
        page.setUpdatedBy(adminId);

        // 2. 기존 리스트 비우기 및 즉시 반영 (중복 키 방지)
        page.getIntros().clear();
        page.getCards().clear();
        page.getGalleries().clear();
        page.getContacts().clear();
        entityManager.flush();

        // 3. 새 데이터 추가
        if (dto.getIntros() != null) {
            dto.getIntros().forEach(i -> page.getIntros().add(
                    RecruitmentPageIntro.builder().recruitmentPage(page)
                            .contents(i.getContents()).sortOrder(i.getSortOrder())
                            .updatedBy(adminId).build()));
        }

        if (dto.getCards() != null) {
            dto.getCards().forEach(c -> page.getCards().add(
                    RecruitmentPageFieldCard.builder().recruitmentPage(page)
                            .applyField(c.getApplyField()).cardTitle(c.getCardTitle())
                            .cardSubTitle(c.getCardSubTitle()).cardDesc(c.getCardDesc())
                            .imageUrl(c.getImageUrl()).sortOrder(c.getSortOrder())
                            .updatedBy(adminId).build()));
        }

        if (dto.getGalleries() != null) {
            dto.getGalleries().forEach(g -> page.getGalleries().add(
                    RecruitmentPageGallery.builder().recruitmentPage(page)
                            .imageUrl(g.getImageUrl()).imageDesc(g.getImageDesc())
                            .tag(g.getTag()).sortOrder(g.getSortOrder())
                            .updatedBy(adminId).build()));
        }

        if (dto.getContacts() != null) {
            dto.getContacts().forEach(con -> page.getContacts().add(
                    RecruitmentPageContact.builder().recruitmentPage(page)
                            .contactType(con.getContactType()).label(con.getLabel())
                            .value(con.getValue()).subValue(con.getSubValue())
                            .sortOrder(con.getSortOrder()).updatedBy(adminId).build()));
        }
    }

    /**
     * 기존 API 호환용 면접 슬롯 설정 로직.
     * 기존 슬롯을 삭제하지 않고, ID 또는 정확한 날짜/시간 매칭으로만 수정한다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void updateInterviewSlots(Integer recruitmentId, RecruitmentRequestDto.InterviewSlotUpdate dto, String adminId) {
        Recruitment recruitment = recruitmentRepository.findByIdForUpdate(recruitmentId)
                .orElseThrow(() -> new EntityNotFoundException("공고를 찾을 수 없습니다."));

        if (dto.getSlots() == null) {
            throw new IllegalArgumentException("면접 슬롯 목록은 필수입니다.");
        }

        boolean initialRegistration = interviewSlotRepository
                .findAllByRecruitment_RecruitmentId(recruitmentId).isEmpty();
        Set<Integer> requestedSlotIds = new HashSet<>();
        Set<InterviewSlotKey> requestedSlotKeys = new HashSet<>();

        for (RecruitmentRequestDto.SlotDto slotDto : dto.getSlots()) {
            validateInterviewSlot(slotDto);

            if (slotDto.getSlotId() != null) {
                if (!requestedSlotIds.add(slotDto.getSlotId())) {
                    throw new InterviewSlotConflictException("동일한 면접 슬롯 ID가 중복 요청되었습니다.");
                }
                RecruitmentInterviewSlot existingSlot = interviewSlotRepository
                        .findByIdForUpdate(slotDto.getSlotId())
                        .orElseThrow(() -> new IllegalArgumentException("해당 면접 슬롯을 찾을 수 없습니다."));
                updateExistingInterviewSlot(recruitmentId, existingSlot, slotDto, adminId);
                continue;
            }

            InterviewSlotKey slotKey = InterviewSlotKey.from(slotDto);
            if (!requestedSlotKeys.add(slotKey)) {
                throw new InterviewSlotConflictException("동일한 날짜와 시간의 면접 슬롯이 중복 요청되었습니다.");
            }

            List<RecruitmentInterviewSlot> matchingSlots = interviewSlotRepository
                    .findMatchingSlotsForUpdate(recruitmentId, slotDto.getSlotDate(),
                            slotDto.getStartTime(), slotDto.getEndTime());
            if (matchingSlots.size() > 1) {
                throw new InterviewSlotConflictException(
                        "동일한 날짜와 시간의 기존 슬롯이 여러 개 있어 안전하게 수정할 수 없습니다. slotId를 포함해 /slots/v2 API를 사용해주세요.");
            }
            if (matchingSlots.size() == 1) {
                updateExistingInterviewSlot(recruitmentId, matchingSlots.get(0), slotDto, adminId);
                continue;
            }
            if (!initialRegistration) {
                throw new InterviewSlotConflictException(
                        "기존 슬롯을 안전하게 식별할 수 없습니다. 날짜나 시간을 변경하려면 slotId를 포함해 /slots/v2 API를 사용해주세요.");
            }

            interviewSlotRepository.save(createInterviewSlot(recruitment, slotDto, adminId));
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void upsertInterviewSlots(
            Integer recruitmentId, RecruitmentRequestDto.InterviewSlotUpdate dto, String adminId) {
        Recruitment recruitment = recruitmentRepository.findByIdForUpdate(recruitmentId)
                .orElseThrow(() -> new EntityNotFoundException("공고를 찾을 수 없습니다."));

        if (dto.getSlots() == null) {
            throw new IllegalArgumentException("면접 슬롯 목록은 필수입니다.");
        }

        Set<Integer> requestedSlotIds = new HashSet<>();
        Set<InterviewSlotKey> requestedSlotKeys = new HashSet<>();
        for (RecruitmentRequestDto.SlotDto slotDto : dto.getSlots()) {
            validateInterviewSlot(slotDto);

            if (slotDto.getSlotId() == null) {
                InterviewSlotKey slotKey = InterviewSlotKey.from(slotDto);
                if (!requestedSlotKeys.add(slotKey)) {
                    throw new InterviewSlotConflictException("동일한 날짜와 시간의 면접 슬롯이 중복 요청되었습니다.");
                }

                List<RecruitmentInterviewSlot> matchingSlots = interviewSlotRepository
                        .findMatchingSlotsForUpdate(recruitmentId, slotDto.getSlotDate(),
                                slotDto.getStartTime(), slotDto.getEndTime());
                if (!matchingSlots.isEmpty()) {
                    throw new InterviewSlotConflictException(
                            "동일한 날짜와 시간의 슬롯이 이미 존재합니다. 기존 slotId를 사용해 수정해주세요.");
                }

                interviewSlotRepository.save(createInterviewSlot(recruitment, slotDto, adminId));
                continue;
            }

            if (!requestedSlotIds.add(slotDto.getSlotId())) {
                throw new IllegalArgumentException("동일한 면접 슬롯 ID가 중복 요청되었습니다.");
            }

            RecruitmentInterviewSlot existingSlot = interviewSlotRepository.findByIdForUpdate(slotDto.getSlotId())
                    .orElseThrow(() -> new IllegalArgumentException("해당 면접 슬롯을 찾을 수 없습니다."));
            updateExistingInterviewSlot(recruitmentId, existingSlot, slotDto, adminId);
        }
    }

    private RecruitmentInterviewSlot createInterviewSlot(
            Recruitment recruitment, RecruitmentRequestDto.SlotDto slotDto, String adminId) {
        return RecruitmentInterviewSlot.builder()
                .recruitment(recruitment)
                .slotDate(slotDto.getSlotDate())
                .startTime(slotDto.getStartTime())
                .endTime(slotDto.getEndTime())
                .capacity(slotDto.getCapacity())
                .isActive(slotDto.getIsActive() != null ? slotDto.getIsActive() : true)
                .updatedBy(adminId)
                .build();
    }

    private void updateExistingInterviewSlot(
            Integer recruitmentId,
            RecruitmentInterviewSlot existingSlot,
            RecruitmentRequestDto.SlotDto slotDto,
            String adminId) {
        if (!Objects.equals(existingSlot.getRecruitment().getRecruitmentId(), recruitmentId)) {
            throw new IllegalArgumentException("다른 모집공고의 면접 슬롯은 수정할 수 없습니다.");
        }

        long assignedCount = interviewScheduleRepository
                .countByInterviewSlot_SlotId(existingSlot.getSlotId());
        if (slotDto.getCapacity() < assignedCount) {
            throw new IllegalStateException("현재 배정 인원보다 슬롯 정원을 작게 설정할 수 없습니다.");
        }

        existingSlot.setSlotDate(slotDto.getSlotDate());
        existingSlot.setStartTime(slotDto.getStartTime());
        existingSlot.setEndTime(slotDto.getEndTime());
        existingSlot.setCapacity(slotDto.getCapacity());
        if (slotDto.getIsActive() != null) {
            existingSlot.setIsActive(slotDto.getIsActive());
        }
        existingSlot.setUpdatedBy(adminId);
    }

    private record InterviewSlotKey(LocalDate slotDate, LocalTime startTime, LocalTime endTime) {
        private static InterviewSlotKey from(RecruitmentRequestDto.SlotDto slotDto) {
            return new InterviewSlotKey(
                    slotDto.getSlotDate(), slotDto.getStartTime(), slotDto.getEndTime());
        }
    }

    private void validateInterviewSlot(RecruitmentRequestDto.SlotDto slotDto) {
        if (slotDto == null) {
            throw new IllegalArgumentException("면접 슬롯 정보는 필수입니다.");
        }
        if (slotDto.getSlotDate() == null) {
            throw new IllegalArgumentException("면접 날짜는 필수입니다.");
        }
        if (slotDto.getCapacity() == null || slotDto.getCapacity() <= 0) {
            throw new IllegalArgumentException("면접 슬롯 정원은 1명 이상이어야 합니다.");
        }

        boolean hasStartTime = slotDto.getStartTime() != null;
        boolean hasEndTime = slotDto.getEndTime() != null;
        if (hasStartTime != hasEndTime) {
            throw new IllegalArgumentException("면접 시작 시간과 종료 시간은 함께 입력해야 합니다.");
        }
        if (hasStartTime && !slotDto.getStartTime().isBefore(slotDto.getEndTime())) {
            throw new IllegalArgumentException("면접 시작 시간은 종료 시간보다 빨라야 합니다.");
        }
    }

    /**
     * 특정 공고의 지원자 목록 조회
     */
    @Transactional(readOnly = true)
    public List<RecruitmentResponseDto.ApplicationDetails> getApplicationsByRecruit(
            Integer recruitmentId, String name, String status) {

        List<RecruitmentApplication> applications = applicationRepository.findAllByRecruitment_RecruitmentId(recruitmentId);

        return applications.stream()
                .filter(app -> name == null || name.trim().isEmpty() || app.getApplicantName().contains(name))
                .filter(app -> status == null || status.trim().isEmpty() || app.getStatus().equals(status))
                .map(this::convertToApplicationDetails)
                .collect(Collectors.toList());
    }

    /**
     * 지원서 상태 변경
     */
    @Transactional
    public void updateApplicationStatus(Integer applicationId, String newStatus) {
        RecruitmentApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("해당 지원서를 찾을 수 없습니다. ID: " + applicationId));

        application.setStatus(newStatus);
    }

    /**
     * 지원서 상세 조회
     */
    @Transactional(readOnly = true)
    public RecruitmentResponseDto.ApplicationDetails getApplicationDetail(Integer applicationId) {
        RecruitmentApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("해당 지원서를 찾을 수 없습니다. ID: " + applicationId));

        return convertToApplicationDetails(app);
    }

    /**
     * 개별 지원자 면접 스케줄 설정
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void setInterviewSchedule(ApplicationInterviewScheduleRequestDto dto) {
        RecruitmentApplication application = applicationRepository.findById(dto.getApplicationId())
                .orElseThrow(() -> new IllegalArgumentException("해당 지원서를 찾을 수 없습니다."));

        RecruitmentInterviewSlot slot = interviewSlotRepository.findByIdForUpdate(dto.getSlotId())
                .orElseThrow(() -> new IllegalArgumentException("해당 면접 슬롯을 찾을 수 없습니다."));

        if (!Objects.equals(application.getRecruitment().getRecruitmentId(),
                slot.getRecruitment().getRecruitmentId())) {
            throw new IllegalArgumentException("지원서와 면접 슬롯의 모집공고가 일치하지 않습니다.");
        }

        if (!Boolean.TRUE.equals(slot.getIsActive())) {
            throw new IllegalStateException("비활성화된 면접 슬롯입니다.");
        }

        ApplicationInterviewSchedule currentSchedule = application.getApplicationInterviewSchedule().stream()
                .findFirst()
                .orElse(null);
        if (currentSchedule != null
                && Objects.equals(currentSchedule.getInterviewSlot().getSlotId(), slot.getSlotId())) {
            return;
        }

        if (slot.getCapacity() == null || slot.getCapacity() <= 0) {
            throw new IllegalStateException("면접 슬롯 정원이 올바르지 않습니다.");
        }

        long assignedCount = interviewScheduleRepository.countByInterviewSlot_SlotId(slot.getSlotId());
        if (assignedCount >= slot.getCapacity()) {
            throw new IllegalStateException("면접 슬롯 정원이 마감되었습니다.");
        }

        if (currentSchedule != null) {
            currentSchedule.setInterviewSlot(slot);
            return;
        }

        ApplicationInterviewSchedule schedule = ApplicationInterviewSchedule.builder()
                .application(application)
                .interviewSlot(slot)
                .build();

        application.getApplicationInterviewSchedule().add(schedule);
    }

    /**
     * 지원자 목록 엑셀 추출
     */
    @Transactional(readOnly = true)
    public ByteArrayInputStream downloadApplicationsExcel(Integer recruitmentId) throws IOException {
        List<RecruitmentApplication> applications = applicationRepository.findAllByRecruitment_RecruitmentId(recruitmentId);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("지원자 목록");

            Row headerRow = sheet.createRow(0);
            String[] columns = {"번호", "이름", "학번", "학과", "학년", "상태", "지원일시"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
            }

            int rowIdx = 1;
            for (RecruitmentApplication app : applications) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(app.getApplicationId());
                row.createCell(1).setCellValue(app.getApplicantName());
                row.createCell(2).setCellValue(app.getStudentNumber());
                row.createCell(3).setCellValue(app.getDepartment());
                row.createCell(4).setCellValue(app.getGrade());
                row.createCell(5).setCellValue(app.getStatus());
                row.createCell(6).setCellValue(app.getCreatedAt() != null ? app.getCreatedAt().toString() : "");
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private RecruitmentResponseDto.ApplicationDetails convertToApplicationDetails(RecruitmentApplication application) {
        RecruitmentInterviewSlot assignedSlot = application.getApplicationInterviewSchedule().stream()
                .findFirst()
                .map(ApplicationInterviewSchedule::getInterviewSlot)
                .orElse(null);

        return RecruitmentResponseDto.ApplicationDetails.builder()
                .applicationId(application.getApplicationId())
                .recruitmentId(application.getRecruitment().getRecruitmentId())
                .applicantName(application.getApplicantName())
                .department(application.getDepartment())
                .studentNumber(application.getStudentNumber())
                .phoneNumber(application.getPhoneNumber())
                .grade(application.getGrade())
                .email(application.getEmail())
                .portfolioUrl(application.getPortfolioUrl())
                .categories(application.getApplyFields().stream()
                        .map(ApplicationApplyField::getApplyField)
                        .toList())
                .interviewSlotId(assignedSlot != null ? assignedSlot.getSlotId() : null)
                .interviewDate(assignedSlot != null ? assignedSlot.getSlotDate() : null)
                .interviewStartTime(assignedSlot != null ? assignedSlot.getStartTime() : null)
                .interviewEndTime(assignedSlot != null ? assignedSlot.getEndTime() : null)
                .termsAgreed(application.getTermsAgreed())
                .status(application.getStatus())
                .createdDate(application.getCreatedAt())
                .modifiedDate(application.getUpdatedAt())
                .build();
    }
}
