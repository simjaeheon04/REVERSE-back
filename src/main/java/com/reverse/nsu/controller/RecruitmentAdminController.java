package com.reverse.nsu.controller;

import com.reverse.nsu.dto.ApplicationInterviewScheduleRequestDto;
import com.reverse.nsu.dto.RecruitmentRequestDto;
import com.reverse.nsu.dto.RecruitmentResponseDto;
import com.reverse.nsu.entity.Recruitment;
import com.reverse.nsu.exception.InterviewSlotConflictException;
import com.reverse.nsu.service.EmailService;
import com.reverse.nsu.service.RecruitmentAdminService;
import com.reverse.nsu.service.RecruitmentNotifyService;
import com.reverse.nsu.service.RecruitmentService;
import com.reverse.nsu.service.RoleCheckService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recruit/admin")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class RecruitmentAdminController {

    private final RecruitmentService recruitmentService;
    private final RecruitmentAdminService recruitmentAdminService;
    private final RecruitmentNotifyService notifyService;
    private final RoleCheckService roleCheckService;
    private final EmailService emailService;

    /**
     * 1. 특정 공고의 지원자 목록 조회
     */
    @GetMapping("/applications")
    public ResponseEntity<?> getApplications(
            @RequestParam Integer recruitmentId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            HttpServletRequest request) {

        String userId = resolveUserId(request);
        if (userId == null) return unauthorized();
        if (!roleCheckService.isAdmin(userId)) return forbidden();

        return ResponseEntity.ok(recruitmentAdminService.getApplicationsByRecruit(recruitmentId, name, status));
    }

    /**
     * 2. 지원서 상태 변경
     */
    @PatchMapping("/applications/status")
    public ResponseEntity<?> updateStatus(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        String userId = resolveUserId(request);
        if (userId == null) return unauthorized();
        if (!roleCheckService.isAdmin(userId)) return forbidden();

        Integer applicationId = (Integer) body.get("applicationId");
        String status = (String) body.get("status");
        recruitmentAdminService.updateApplicationStatus(applicationId, status);
        emailService.sendAuditLog(userId, "지원서 상태 변경 (applicationId: " + applicationId + ") → " + status);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "지원서 상태가 " + status + "(으)로 변경되었습니다."
        ));
    }

    /**
     * 3. 공고 생성 (상세 페이지 자동 생성 포함)
     */
    @PostMapping
    public ResponseEntity<?> create(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        String userId = resolveUserId(request);
        if (userId == null) return unauthorized();
        if (!roleCheckService.isAdmin(userId)) return forbidden();

        Recruitment recruitment = recruitmentAdminService.createRecruitment(body, userId);
        notifyService.notifySubscribers(recruitment.getTitle());
        emailService.sendAuditLog(userId, "모집 공고 등록 - 제목: " + recruitment.getTitle());

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "공고 및 상세 페이지 등록 완료",
                "recruitmentId", recruitment.getRecruitmentId(),
                "pageId", recruitment.getRecruitmentPage().getPageId(),
                "isActive", recruitment.getIsActive()
        ));
    }

    /**
     * 4. 공고 내용 수정 (기본 정보)
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable Integer id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        String userId = resolveUserId(request);
        if (userId == null) return unauthorized();
        if (!roleCheckService.isAdmin(userId)) return forbidden();

        Boolean isActive = recruitmentAdminService.updateRecruitment(id, body, userId);
        emailService.sendAuditLog(userId, "모집 공고 수정 (ID: " + id + ")");

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "공고 수정 완료",
                "recruitmentId", id,
                "isActive", isActive
        ));
    }

    /**
     * 5. 공고 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable Integer id,
            HttpServletRequest request) {

        String userId = resolveUserId(request);
        if (userId == null) return unauthorized();
        if (!roleCheckService.isAdmin(userId)) return forbidden();

        recruitmentService.delete(id);
        emailService.sendAuditLog(userId, "모집 공고 삭제 (ID: " + id + ")");
        return ResponseEntity.ok("공고 삭제 완료. ID: " + id);
    }

    /**
     * 6. 지원서 상세 조회
     */
    @GetMapping("/applications/{applicationId}")
    public ResponseEntity<?> getApplicationDetail(
            @PathVariable Integer applicationId,
            HttpServletRequest request) {

        String userId = resolveUserId(request);
        if (userId == null) return unauthorized();
        if (!roleCheckService.isAdmin(userId)) return forbidden();

        return ResponseEntity.ok(recruitmentAdminService.getApplicationDetail(applicationId));
    }

    /**
     * 7. 면접 스케줄 배정
     */
    @PostMapping("/applications/interview")
    public ResponseEntity<?> setInterview(
            @Valid @RequestBody ApplicationInterviewScheduleRequestDto body,
            HttpServletRequest request) {

        String userId = resolveUserId(request);
        if (userId == null) return unauthorized();
        if (!roleCheckService.isAdmin(userId)) return forbidden();

        recruitmentAdminService.setInterviewSchedule(body);
        emailService.sendAuditLog(userId, "면접 스케줄 배정");
        return ResponseEntity.ok(Map.of("status", "success", "message", "면접 배정 완료"));
    }

    /**
     * 8. 엑셀 다운로드
     */
    @GetMapping("/applications/excel")
    public ResponseEntity<?> downloadExcel(
            @RequestParam Integer recruitmentId,
            HttpServletRequest request) throws IOException {

        String userId = resolveUserId(request);
        if (userId == null) return unauthorized();
        if (!roleCheckService.isAdmin(userId)) return forbidden();

        ByteArrayInputStream in = recruitmentAdminService.downloadApplicationsExcel(recruitmentId);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=applications_recruit_" + recruitmentId + ".xlsx");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }

    /**
     * 9. 상세 페이지 내용 수정
     */
    @PatchMapping("/{id}/page")
    public ResponseEntity<?> updateRecruitmentPage(
            @PathVariable Integer id,
            @RequestBody RecruitmentRequestDto.PageUpdate body,
            HttpServletRequest request) {

        String userId = resolveUserId(request);
        if (userId == null) return unauthorized();
        if (!roleCheckService.isAdmin(userId)) return forbidden();

        recruitmentAdminService.updateRecruitmentPage(id, body, userId);
        emailService.sendAuditLog(userId, "모집 공고 상세 페이지 수정 (ID: " + id + ")");

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "상세 페이지 정보 수정 완료",
                "recruitmentId", id
        ));
    }

    /**
     * 10. 면접 일정 슬롯 설정
     */
    @PostMapping("/{id}/slots")
    public ResponseEntity<?> updateInterviewSlots(
            @PathVariable Integer id,
            @Valid @RequestBody RecruitmentRequestDto.InterviewSlotUpdate body,
            HttpServletRequest request) {

        String userId = resolveUserId(request);
        if (userId == null) return unauthorized();
        if (!roleCheckService.isAdmin(userId)) return forbidden();

        try {
            recruitmentAdminService.updateInterviewSlots(id, body, userId);
        } catch (InterviewSlotConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
        emailService.sendAuditLog(userId, "모집 공고 면접 슬롯 설정 (ID: " + id + ")");

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "면접 날짜 및 정원 설정 완료"
        ));
    }

    @PostMapping("/{id}/slots/v2")
    public ResponseEntity<?> upsertInterviewSlots(
            @PathVariable Integer id,
            @Valid @RequestBody RecruitmentRequestDto.InterviewSlotUpdate body,
            HttpServletRequest request) {

        String userId = resolveUserId(request);
        if (userId == null) return unauthorized();
        if (!roleCheckService.isAdmin(userId)) return forbidden();

        try {
            recruitmentAdminService.upsertInterviewSlots(id, body, userId);
        } catch (InterviewSlotConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
        emailService.sendAuditLog(userId, "모집 공고 면접 슬롯 V2 설정 (ID: " + id + ")");

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "면접 날짜 및 시간 설정 완료"
        ));
    }

    private String resolveUserId(HttpServletRequest request) {
        return (String) request.getAttribute("userId");
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
    }

    private ResponseEntity<?> forbidden() {
        return ResponseEntity.status(403).body(Map.of("message", "관리자 권한이 필요합니다."));
    }
}
