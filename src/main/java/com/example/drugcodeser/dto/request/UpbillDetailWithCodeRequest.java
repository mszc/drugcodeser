
package com.example.drugcodeser.dto.request;

import lombok.Data;

@Data
public class UpbillDetailWithCodeRequest {

    private String billCode;
    private String fromRefUserId;
    private String toRefUserId;
    private String agentRefEntId;
}
