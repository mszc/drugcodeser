package com.example.drugcodeser.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 单据详情 + 码关联关系聚合响应
 */
@Data
@Schema(description = "单据详情（含码关联关系）")
public class BillDetailWithCodeRelationsResponse {

    @Schema(description = "请求是否成功")
    private boolean success;

    @Schema(description = "业务结果")
    private Result result;

    @Data
    @Schema(description = "业务结果")
    public static class Result {

        @Schema(description = "业务数据")
        private BillDetailModel model;

        @Schema(description = "消息码")
        private String msgCode;

        @Schema(description = "消息信息")
        private String msgInfo;

        @Schema(description = "响应是否成功")
        private Boolean responseSuccess;
    }

    @Data
    @Schema(description = "单据详情模型")
    public static class BillDetailModel {

        @Schema(description = "单据号")
        private String billCode;

        @Schema(description = "外部单据ID")
        private String billOutId;

        @Schema(description = "单据日期")
        private String billTime;

        @Schema(description = "单据类型")
        private String billType;

        @Schema(description = "单据类型名称")
        private String billTypeName;

        @Schema(description = "发货企业名称")
        private String fromEntName;

        @Schema(description = "发货企业用户ID")
        private String fromUserId;

        @Schema(description = "收货企业名称")
        private String toEntName;

        @Schema(description = "收货企业用户ID")
        private String toUserId;

        @Schema(description = "修改日期")
        private String modDate;

        @Schema(description = "处理日期")
        private String processDate;

        @Schema(description = "药品明细列表（含匹配的码关联关系）")
        private List<BillDetailItemWithCodesDto> billChkInOutDetailList;
    }
}
