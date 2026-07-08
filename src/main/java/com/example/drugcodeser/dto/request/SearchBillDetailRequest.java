package com.example.drugcodeser.dto.request;

import lombok.Data;

@Data
public class SearchBillDetailRequest {

    /** 单据号（必填） */
    private String billCode;
    /** 是否显示追溯码 1:显示 0:不显示（必填） */
    private String showCode;
}
