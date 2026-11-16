package com.reverse.nsu.controller;

import com.reverse.nsu.dto.ApplicationRequestDto;
import com.reverse.nsu.exception.InterviewRequestException;
import com.reverse.nsu.exception.InterviewSlotConflictException;
import com.reverse.nsu.service.RecruitmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // 로그 추가
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/recruit/apply")
@RequiredArgsConstructor
@Slf4j // 로그 추가
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class RecruitmentApplyController {

    private final RecruitmentService recruitmentService;

    @PostMapping
    public ResponseEntity<?> submit(@Valid @RequestBody ApplicationRequestDto dto) {
        log.info(">>>> [지원 신청] 학번: {}, 성함: {}", dto.getStudentNumber(), dto.getApplicantName());

        try {
            recruitmentService.submitApplication(dto);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "신청이 완료되었습니다. 화이팅!"
            ));
        } catch (InterviewRequestException | InterviewSlotConflictException e) {
            throw e;
        } catch (IllegalStateException | IllegalArgumentException e) {
            // 중복 신청, 기간 만료 등 예상 가능한 예외 처리
            log.warn(">>>> [신청 거절] 사유: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            // 예상치 못한 시스템 에러 처리
            log.error(">>>> [시스템 오류] ", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
            ));
        }
    }
}
