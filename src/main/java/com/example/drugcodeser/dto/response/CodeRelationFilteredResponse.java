package com.example.drugcodeser.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 码关联关系过滤后响应 - 仅返回查询码及其下级码
 */
@Data
@Schema(description = "码关联关系过滤后响应")
public class CodeRelationFilteredResponse {

    @Schema(description = "查询的追溯码")
    private String queryCode;

    @Schema(description = "查询码的包装级别（1=最小级）")
    private String codePackLevel;

    @Schema(description = "是否为最小包装")
    private String isSmallest;

    @Schema(description = "过滤后的关联关系列表（仅含查询码及其下级码）")
    private List<CodeRelationItem> codeRelationList;

    @Schema(description = "药品基础信息列表")
    private Object baseInfos;

    @Schema(description = "码激活信息")
    private Object codeActiveInfo;

    @Schema(description = "生产信息列表")
    private Object produceInfoList;

    @Schema(description = "包装信息")
    private Object pkgInfo;

    /**
     * 从 baseInfos.baseInfoList[0] 提取，用于匹配单据明细中的药品
     */
    @Schema(description = "药品ID（用于匹配）")
    private String prodId;

    @Schema(description = "生产批号（用于匹配）")
    private String produceBatchNo;

    @Data
    @Schema(description = "码关联项")
    public static class CodeRelationItem {

        @Schema(description = "追溯码", example = "86558720000505413239")
        private String code;

        @Schema(description = "码级别", example = "1")
        private String codeLevel;

        @Schema(description = "包装级别（1=最小级）", example = "2")
        private String codePackLevel;

        @Schema(description = "父级码", example = "")
        private String parentCode;

        @Schema(description = "状态（I=已激活, O=未激活）", example = "I")
        private String status;
    }
}
