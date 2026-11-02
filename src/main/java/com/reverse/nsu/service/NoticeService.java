package com.reverse.nsu.service;

import com.reverse.nsu.dto.NoticeAdminRequestDto;
import com.reverse.nsu.dto.NoticeAdminResponseDto;
import com.reverse.nsu.dto.NoticeListResponseDto;
import com.reverse.nsu.dto.NoticeResponseDto;
import com.reverse.nsu.entity.Post;
import com.reverse.nsu.entity.PostAttached;
import com.reverse.nsu.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class NoticeService {

    private final PostRepository postRepository;
    private final BoardRepository boardRepository;
    private final PostAttachedRepository postAttachedRepository;
    private final UsersRepository usersRepository;
    private final PostLikeRepository postLikeRepository;
    // [추가] 대댓글 및 댓글 삭제를 위한 리포지토리 주입
    private final CommentRepository commentRepository;

    private Integer noticeBoardId;

    private Integer getNoticeBoardId() {
        if (noticeBoardId == null) {
            noticeBoardId = boardRepository.findByBoardName("공지사항")
                    .orElseThrow(() -> new RuntimeException("공지사항 게시판이 존재하지 않습니다."))
                    .getBoardId();
        }
        return noticeBoardId;
    }

    /**
     * 전체 공지사항 목록 조회
     */
    public Page<NoticeListResponseDto> getAll(String category, boolean isLoggedIn, int page) {
        Pageable pageable = PageRequest.of(page, 6);
        Integer boardId = getNoticeBoardId();
        Page<Post> posts;

        if (category != null && !category.trim().isEmpty() && !category.equals("전체")) {
            posts = isLoggedIn ?
                    postRepository.findAllByBoardIdAndPostCategoryOrderByCreatedDateDesc(boardId, category, pageable) :
                    postRepository.findAllByBoardIdAndPostCategoryAndIsExternalTrueOrderByCreatedDateDesc(boardId, category, pageable);
        } else {
            posts = isLoggedIn ?
                    postRepository.findAllByBoardIdOrderByCreatedDateDesc(boardId, pageable) :
                    postRepository.findAllByBoardIdAndIsExternalTrueOrderByCreatedDateDesc(boardId, pageable);
        }
        return posts.map(NoticeListResponseDto::new);
    }

    /**
     * 공지사항 상세 조회
     */
    public NoticeResponseDto getOne(Integer postId, boolean isLoggedIn) {
        Post post = postRepository.findById(postId)
                .filter(p -> p.getBoardId().equals(getNoticeBoardId()))
                .orElseThrow(() -> new IllegalArgumentException("NOT_FOUND"));

        if (!isLoggedIn && !post.getIsExternal()) {
            throw new SecurityException("FORBIDDEN");
        }

        List<String> imageUrls = post.getImageUrlList();
        return NoticeResponseDto.from(post, imageUrls);
    }

    /**
     * 공지사항 작성
     */
    @Transactional
    public NoticeAdminResponseDto create(NoticeAdminRequestDto dto, String userId) {
        validateDto(dto);
        Post post = Post.createPost(dto, userId, getNoticeBoardId());
        Post savedPost = postRepository.save(post);

        if (dto.getImageUrls() != null && !dto.getImageUrls().isEmpty()) {
            saveAttachedImages(savedPost, userId, dto.getImageUrls());
        }

        List<String> responseUrls = dto.getImageUrls() != null ? dto.getImageUrls() : Collections.emptyList();
        return new NoticeAdminResponseDto(savedPost, responseUrls);
    }

    /**
     * 공지사항 수정
     */
    @Transactional
    public NoticeAdminResponseDto update(NoticeAdminRequestDto dto, String userId) {
        validateDto(dto);

        Post post = postRepository.findById(dto.getPostId())
                .filter(p -> p.getBoardId().equals(getNoticeBoardId()))
                .orElseThrow(() -> new RuntimeException("NOT_FOUND"));

        if (hasAdminPrivilege(userId) || post.getUserId().equals(userId)) {
            post.update(dto);
            if (dto.isImageUrlsProvided() && dto.getImageUrls() != null) {
                postAttachedRepository.deleteAllByPost(post);
                if (!dto.getImageUrls().isEmpty()) {
                    saveAttachedImages(post, userId, dto.getImageUrls());
                }
            }
        } else {
            throw new RuntimeException("수정 권한이 없습니다.");
        }

        List<String> responseUrls = dto.isImageUrlsProvided() && dto.getImageUrls() != null
                ? dto.getImageUrls()
                : postAttachedRepository.findAllByPost(post).stream()
                        .map(PostAttached::getAttachedUrl)
                        .toList();
        return new NoticeAdminResponseDto(post, responseUrls);
    }

    /**
     * 공지사항 삭제 (핵심: 자식 데이터부터 삭제)
     */
    @Transactional
    public Post delete(Integer postId, String userId) {
        Post post = postRepository.findById(postId)
                .filter(p -> p.getBoardId().equals(getNoticeBoardId()))
                .orElseThrow(() -> new RuntimeException("NOT_FOUND"));

        if (hasAdminPrivilege(userId) || post.getUserId().equals(userId)) {

            // 1. 좋아요 데이터 삭제
            postLikeRepository.deleteAllByPost(post);

            // 2. 이미지 데이터 삭제
            postAttachedRepository.deleteAllByPost(post);

            // 3. 댓글 및 대댓글 삭제
            // Comment 엔티티의 cascade = ALL 설정으로 인해 대댓글 -> 댓글 순으로 삭제됨
            commentRepository.deleteAllByPost(post);

            // 4. 마지막으로 게시글 본체 삭제
            postRepository.delete(post);

        } else {
            throw new RuntimeException("삭제 권한이 없습니다.");
        }
        return post;
    }

    private void validateDto(NoticeAdminRequestDto dto) {
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty() ||
                dto.getContent() == null || dto.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("MISSING_REQUIRED_FIELDS");
        }
    }

    private void saveAttachedImages(Post post, String userId, List<String> urls) {
        urls.forEach(url ->
                postAttachedRepository.save(PostAttached.create(post, userId, url))
        );
    }

    private boolean hasAdminPrivilege(String userId) {
        return usersRepository.findById(userId)
                .map(user -> user.getRole().getRoleId() == 1 || user.getRole().getRoleId() == 2)
                .orElse(false);
    }
}
