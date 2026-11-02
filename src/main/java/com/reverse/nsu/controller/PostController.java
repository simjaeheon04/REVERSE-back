package com.reverse.nsu.controller;

import com.reverse.nsu.dto.BoardPostListResponseDto;
import com.reverse.nsu.dto.NoticeAdminRequestDto;
import com.reverse.nsu.service.PostService;
import com.reverse.nsu.service.RoleCheckService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/board")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final RoleCheckService roleCheckService;

    private String resolveUserId(HttpServletRequest request) {
        return (String) request.getAttribute("userId");
    }

    /**
     * 1. 게시글 작성 (준회원 이상)
     */
    @PostMapping("/{boardId}")
    public ResponseEntity<?> createPost(
            @PathVariable("boardId") Integer boardId,
            @RequestBody NoticeAdminRequestDto dto,
            HttpServletRequest request) {

        String userId = resolveUserId(request);
        if (userId == null) return unauthorizedResponse();
        if (!roleCheckService.isAssociateOrAbove(userId)) return forbiddenResponse();

        try {
            Integer postId = postService.createPost(dto, userId, boardId);
            return ResponseEntity.ok(Map.of("status", "success", "postId", postId, "message", "등록되었습니다."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 2. 게시글 수정 (준회원 이상, 본인 또는 관리자)
     */
    @PatchMapping("/post/{postId}")
    public ResponseEntity<?> updatePost(
            @PathVariable("postId") Integer postId,
            @RequestBody NoticeAdminRequestDto dto,
            HttpServletRequest request) {

        String userId = resolveUserId(request);
        if (userId == null) return unauthorizedResponse();
        if (!roleCheckService.isAssociateOrAbove(userId)) return forbiddenResponse();

        try {
            postService.updatePost(postId, dto, userId);
            return ResponseEntity.ok(Map.of("status", "success", "message", "수정되었습니다."));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("message", "게시글을 찾을 수 없습니다."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "게시글 수정 처리 중 오류가 발생했습니다."));
        }
    }

    /**
     * 3. 게시글 삭제 (준회원 이상, 본인 또는 관리자)
     */
    @DeleteMapping("/post/{postId}")
    public ResponseEntity<?> deletePost(
            @PathVariable("postId") Integer postId,
            HttpServletRequest request) {

        String userId = resolveUserId(request);
        if (userId == null) return unauthorizedResponse();
        if (!roleCheckService.isAssociateOrAbove(userId)) return forbiddenResponse();

        try {
            postService.deletePost(postId, userId);
            return ResponseEntity.ok(Map.of("status", "success", "message", "삭제되었습니다."));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("message", "게시글을 찾을 수 없습니다."));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "게시글 삭제 처리 중 오류가 발생했습니다."));
        }
    }

    /**
     * 4. 게시글 목록 조회 (준회원 이상)
     */
    @GetMapping("/{boardId}")
    public ResponseEntity<?> getPosts(
            @PathVariable("boardId") Integer boardId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 6) Pageable pageable,
            HttpServletRequest request) {

        String userId = resolveUserId(request);
        if (userId == null) return unauthorizedResponse();
        if (!roleCheckService.isAssociateOrAbove(userId)) return forbiddenResponse();

        Page<BoardPostListResponseDto> response = postService
                .searchPosts(boardId, category, type, keyword, pageable, userId)
                .map(BoardPostListResponseDto::new);

        return ResponseEntity.ok(response);
    }

    /**
     * 5. 내 통계 정보 조회 (준회원 이상)
     */
    @GetMapping("/my/stats")
    public ResponseEntity<?> getMyStats(HttpServletRequest request) {
        String userId = resolveUserId(request);
        if (userId == null) return unauthorizedResponse();
        if (!roleCheckService.isAssociateOrAbove(userId)) return forbiddenResponse();
        return ResponseEntity.ok(postService.getMyPostStats(userId));
    }

    /**
     * 6. 나의 게시글 목록 (준회원 이상)
     */
    @GetMapping("/my/posts")
    public ResponseEntity<?> getMyPosts(
            @PageableDefault(size = 6) Pageable pageable,
            HttpServletRequest request) {

        String userId = resolveUserId(request);
        if (userId == null) return unauthorizedResponse();
        if (!roleCheckService.isAssociateOrAbove(userId)) return forbiddenResponse();

        Page<BoardPostListResponseDto> response = postService
                .getMyPosts(userId, pageable)
                .map(BoardPostListResponseDto::new);

        return ResponseEntity.ok(response);
    }

    private ResponseEntity<?> unauthorizedResponse() {
        return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요한 서비스입니다."));
    }

    private ResponseEntity<?> forbiddenResponse() {
        return ResponseEntity.status(403).body(Map.of("message", "준회원 이상만 이용 가능한 서비스입니다."));
    }
}
