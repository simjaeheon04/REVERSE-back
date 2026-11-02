package com.reverse.nsu.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter; // [추가] 컨트롤러에서 바인딩할 때 필요할 수 있음
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter // [추가] 데이터를 주입받기 위해 Setter를 열어두는 것이 안전합니다.
@NoArgsConstructor
public class NoticeAdminRequestDto {
    private Integer postId;
    private String title;
    private String content;
    private Boolean isPinned;
    private Boolean isExternal;
    private String category;

    // [수정] null 방지를 위해 새 리스트로 초기화
    private List<String> imageUrls = new ArrayList<>();

    @JsonIgnore
    private boolean imageUrlsProvided;

    @JsonSetter("imageUrls")
    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
        this.imageUrlsProvided = true;
    }

    @JsonIgnore
    public boolean isImageUrlsProvided() {
        return imageUrlsProvided;
    }
}
