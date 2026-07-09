package com.example.drugcodeser.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class SearchBillDetailRequest {

    @Schema(description = "单据号", required = true)
    private String billCode;

    @Schema(description = "是否显示追溯码，1=显示 0=不显示", example = "1")
    private String showCode = "1";
}
