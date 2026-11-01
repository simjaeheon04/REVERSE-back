package com.reverse.nsu.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationInterviewScheduleRequestDto {
    @NotNull(message = "지원서 ID는 필수입니다.")
    private Integer applicationId; // 지원서 ID

    @NotNull(message = "면접 슬롯 ID는 필수입니다.")
    private Integer slotId;        // 선택한 면접 타임슬롯 ID
}
