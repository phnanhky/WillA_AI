package com.willa.ai.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddExpertBookingMaterialsRequest {

    private List<ExpertBookingAttachmentRequest> attachments;
    /** Link Google Drive / Docs — mỗi phần tử một URL. */
    private List<String> driveLinks;
}
