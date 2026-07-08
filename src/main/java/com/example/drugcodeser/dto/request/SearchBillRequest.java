package com.example.drugcodeser.dto.request;

import lombok.Data;

@Data
public class SearchBillRequest {

    /** 开始日期 yyyy-MM-dd（必填） */
    private String beginDate;
    /** 结束日期 yyyy-MM-dd（必填） */
    private String endDate;
    /** 单据号（可选） */
    private String billCode;
    /** 单据类型：A-出库 B-入库（可选） */
    private String billType;
    /** 页码（可选，默认1） */
    private Long curPage;
    /** 每页条数（可选，默认20，最大100） */
    private Long pageSize;
    /** 发货企业ID（可选） */
    private String partnerIdSend;
    /** 收货企业ID（可选） */
    private String partnerIdRecv;
    /** 上传时间起 yyyy-MM-dd HH:mm:ss（可选） */
    private String uploadTimeBegin;
    /** 上传时间止 yyyy-MM-dd HH:mm:ss（可选） */
    private String uploadTimeEnd;
}
