package com.reverse.nsu.dto;

import com.reverse.nsu.entity.Post;
import lombok.Getter;
import java.util.List;

@Getter
public class BoardPostResponseDto {
    // 1. id를 postId로 변경하여 다른 DTO들과 이름을 맞춥니다.
    private final Integer postId;
    private final Integer boardId;
    private final String title;
    private final String content;
    private final String category;
    private final String userId;
    private final String createdAt;
    private final String modifiedAt;
    private final Integer commentCount;
    private final Integer likeCount;
    private final List<String> imageUrls;

    public static BoardPostResponseDto from(Post post, List<String> imageUrls) {
        return new BoardPostResponseDto(post, imageUrls);
    }

    private BoardPostResponseDto(Post post, List<String> imageUrls) {
        // 2. 매핑되는 필드도 postId로 수정합니다.
        this.postId = post.getPostId();
        this.boardId = post.getBoardId();
        this.title = post.getPostTitle();
        this.content = post.getPostContents();
        this.category = post.getPostCategory();
        this.userId = post.getUserId();
        this.createdAt = post.getCreatedDate().toLocalDate().toString();
        this.modifiedAt = post.getModifiedDate() != null
                ? post.getModifiedDate().toLocalDate().toString() : null;
        this.commentCount = post.getPostCommentCount();
        this.likeCount = post.getPostLikeCount();
        this.imageUrls = imageUrls;
    }
}
