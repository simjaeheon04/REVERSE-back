package com.reverse.nsu.service;

import com.reverse.nsu.dto.BoardPostListResponseDto;
import com.reverse.nsu.dto.BoardPostResponseDto;
import com.reverse.nsu.entity.Post;
import com.reverse.nsu.entity.PostAttached;
import com.reverse.nsu.entity.PostLike;
import com.reverse.nsu.repository.BoardRepository;
import com.reverse.nsu.repository.PostAttachedRepository;
import com.reverse.nsu.repository.PostLikeRepository;
import com.reverse.nsu.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 기본적으로 읽기 전용 설정
public class BoardService {

    private final PostRepository postRepository;
    private final BoardRepository boardRepository;
    private final PostAttachedRepository postAttachedRepository;
    private final PostLikeRepository postLikeRepository;

    // 게시판 ID가 없는 레거시 목록 API는 기존 응답 형식을 유지하면서 전체 게시판 글을 조회한다.
    public Page<BoardPostListResponseDto> getAll(int page) {
        Pageable pageable = PageRequest.of(page, 6, Sort.by(Sort.Direction.DESC, "createdDate"));
        return postRepository.findAll(pageable)
                .map(BoardPostListResponseDto::new);
    }

    // 단건 조회
    public BoardPostResponseDto getOne(Integer postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("NOT_FOUND"));

        // lazy 로딩 문제를 피하기 위해 postAttachedRepository로 직접 조회
        List<String> imageUrls = postAttachedRepository.findAllByPost(post)
                .stream()
                .map(PostAttached::getAttachedUrl)
                .collect(Collectors.toList());

        return BoardPostResponseDto.from(post, imageUrls);
    }

    // BRD07 - 좋아요 토글
    @Transactional
    public boolean toggleLike(Integer postId, String userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("NOT_FOUND"));

        // [수정] PostId(Integer) 대신 Post 객체 자체를 넘겨서 조회하도록 통일합니다.
        // PostLikeRepository에 findByUserIdAndPost(String userId, Post post) 가 있어야 합니다.
        Optional<PostLike> existing = postLikeRepository.findByUserIdAndPost(userId, post);

        if (existing.isPresent()) {
            postLikeRepository.delete(existing.get());
            post.decrementLikeCount();
            return false;
        } else {
            postLikeRepository.save(PostLike.create(post, userId));
            post.incrementLikeCount();
            return true;
        }
    }

    //카테고리 조회
    public List<com.reverse.nsu.dto.BoardCategoryResponseDto> getCategories() {
        return boardRepository.findAll().stream()
                .map(com.reverse.nsu.dto.BoardCategoryResponseDto::new)
                .collect(Collectors.toList());
    }
}
