package com.willa.ai.backend.dto.request;

import lombok.Data;

@Data
public class ExpertRefundBankDetailsRequest {
    private String bankName;
    private String accountNumber;
    private String accountHolder;
}
