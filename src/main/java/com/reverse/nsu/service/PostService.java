package com.reverse.nsu.service;

import com.reverse.nsu.dto.NoticeAdminRequestDto;
import com.reverse.nsu.entity.Post;
import com.reverse.nsu.entity.PostAttached;
import jakarta.persistence.EntityNotFoundException;
import com.reverse.nsu.repository.*; // Repository들 한 번에 관리
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UsersRepository usersRepository;
    private final PostAttachedRepository postAttachedRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommentRepository commentRepository;
    private final RoleCheckService roleCheckService;

    /**
     * 게시글 작성
     */
    @Transactional
    public Integer createPost(NoticeAdminRequestDto dto, String userId, Integer boardId) {
        validateDto(dto);
        Post post = Post.createPost(dto, userId, boardId);
        Post savedPost = postRepository.save(post);

        if (dto.getImageUrls() != null && !dto.getImageUrls().isEmpty()) {
            List<PostAttached> images = dto.getImageUrls().stream()
                    .map(url -> PostAttached.create(savedPost, userId, url))
                    .collect(Collectors.toList());
            postAttachedRepository.saveAll(images);
        }

        return savedPost.getPostId();
    }

    /**
     * 게시글 수정
     */
    @Transactional
    public void updatePost(Integer postId, NoticeAdminRequestDto dto, String userId) {
        Post post = findPostOrThrow(postId);
        if (!post.getUserId().equals(userId) && !roleCheckService.isAdmin(userId)) {
            throw new AccessDeniedException("게시글 수정 권한이 없습니다.");
        }
        validateDto(dto);
        post.update(dto);

        if (dto.isImageUrlsProvided() && dto.getImageUrls() != null) {
            // [수정] PostId(Integer)가 아닌 Post(객체)로 삭제
            postAttachedRepository.deleteAllByPost(post);
            if (!dto.getImageUrls().isEmpty()) {
                List<PostAttached> images = dto.getImageUrls().stream()
                        .map(url -> PostAttached.create(post, userId, url))
                        .collect(Collectors.toList());
                postAttachedRepository.saveAll(images);
            }
        }
    }

    /**
     * 게시글 삭제 (중요: 대댓글/댓글/좋아요 순차 삭제 반영)
     */
    @Transactional
    public void deletePost(Integer postId, String userId) {
        Post post = findPostOrThrow(postId);

        if (!post.getUserId().equals(userId) && !roleCheckService.isAdmin(userId)) {
            throw new AccessDeniedException("게시글 삭제 권한이 없습니다.");
        }

        // [순서 수정] DB 제약 조건 해결을 위해 자식 데이터부터 명시적 삭제
        // 1. 좋아요 삭제
        postLikeRepository.deleteAllByPost(post);

        // 2. 이미지 삭제
        postAttachedRepository.deleteAllByPost(post);

        // 3. 댓글 및 대댓글 삭제 (Comment 엔티티의 Cascade 설정과 협업)
        commentRepository.deleteAllByPost(post);

        // 4. 최종 게시글 삭제
        postRepository.delete(post);
    }

    /**
     * 게시글 목록 조회
     */
    @Transactional(readOnly = true)
    public Page<Post> searchPosts(Integer boardId, String category, String type, String keyword, Pageable pageable, String userId) {
        boolean isLoggedIn = (userId != null && usersRepository.existsById(userId));

        if ("전체".equals(category) || (category != null && category.trim().isEmpty())) {
            category = null;
        }

        if ((keyword == null || keyword.trim().isEmpty()) && category != null) {
            return isLoggedIn ?
                    postRepository.findAllByBoardIdAndPostCategoryOrderByCreatedDateDesc(boardId, category, pageable) :
                    postRepository.findAllByBoardIdAndPostCategoryAndIsExternalTrueOrderByCreatedDateDesc(boardId, category, pageable);
        }

        if (keyword != null && !keyword.trim().isEmpty() && keyword.trim().length() < 2) {
            throw new IllegalArgumentException("검색어는 최소 2글자 이상 입력해 주세요.");
        }

        if (keyword == null || keyword.trim().isEmpty()) {
            return isLoggedIn ?
                    postRepository.findAllByBoardIdOrderByCreatedDateDesc(boardId, pageable) :
                    postRepository.findAllByBoardIdAndIsExternalTrueOrderByCreatedDateDesc(boardId, pageable);
        }

        String searchType = (type != null) ? type : "all";
        return switch (searchType) {
            case "title" -> isLoggedIn ?
                    postRepository.findAllByBoardIdAndPostTitleContainingOrderByCreatedDateDesc(boardId, keyword, pageable) :
                    postRepository.findAllByBoardIdAndPostTitleContainingAndIsExternalTrueOrderByCreatedDateDesc(boardId, keyword, pageable);

            case "contents" -> isLoggedIn ?
                    postRepository.findAllByBoardIdAndPostContentsContainingOrderByCreatedDateDesc(boardId, keyword, pageable) :
                    postRepository.findAllByBoardIdAndPostContentsContainingAndIsExternalTrueOrderByCreatedDateDesc(boardId, keyword, pageable);

            case "author" -> isLoggedIn ?
                    postRepository.findAllByBoardIdAndUserIdContainingOrderByCreatedDateDesc(boardId, keyword, pageable) :
                    postRepository.findAllByBoardIdAndUserIdContainingAndIsExternalTrueOrderByCreatedDateDesc(boardId, keyword, pageable);

            default -> isLoggedIn ?
                    postRepository.findAllByBoardIdOrderByCreatedDateDesc(boardId, pageable) :
                    postRepository.findAllByBoardIdAndIsExternalTrueOrderByCreatedDateDesc(boardId, pageable);
        };
    }

    @Transactional(readOnly = true)
    public Post getPostById(Integer postId) {
        return findPostOrThrow(postId);
    }

    @Transactional(readOnly = true)
    public Page<Post> getMyPosts(String userId, Pageable pageable) {
        return postRepository.findAllByUserIdOrderByCreatedDateDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getMyPostStats(String userId) {
        Map<String, Object> stats = new HashMap<>();
        long postCount = postRepository.countByUserId(userId);
        Integer totalLikes = postRepository.sumPostLikeCountByUserId(userId);

        stats.put("postCount", postCount);
        stats.put("totalLikes", totalLikes != null ? totalLikes : 0);
        return stats;
    }

    private Post findPostOrThrow(Integer postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("게시글을 찾을 수 없습니다."));
    }

    private void validateDto(NoticeAdminRequestDto dto) {
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty() ||
                dto.getContent() == null || dto.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("필수 값이 누락되었습니다.");
        }
    }
}
