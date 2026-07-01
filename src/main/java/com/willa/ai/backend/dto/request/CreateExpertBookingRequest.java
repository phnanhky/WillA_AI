package com.willa.ai.backend.dto.request;

import com.willa.ai.backend.entity.enums.ExpertBookingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateExpertBookingRequest {

    private Long expertId;
    private ExpertBookingType bookingType;
    private String brief;
    /** Mô tả / link ấn phẩm (có thể nhiều dòng). */
    private String publications;
    /** Link Google Drive / Docs. */
    private List<String> driveLinks;
    /** File đã upload qua /api/files/upload-document. */
    private List<ExpertBookingAttachmentRequest> attachments;
    /** Số giờ trao đổi — bắt buộc khi bookingType = HOURLY. */
    private Integer hours;
    /** Booking review gốc (tùy chọn) khi mua thêm giờ. */
    private Long parentBookingId;
}
