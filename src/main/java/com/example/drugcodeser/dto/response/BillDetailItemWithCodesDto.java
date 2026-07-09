package com.example.drugcodeser.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 单据明细项（含码关联关系列表）
 */
@Data
@Schema(description = "单据明细项（含码关联关系）")
public class BillDetailItemWithCodesDto {

    @Schema(description = "批准文号")
    private String approveNo;

    @Schema(description = "药品企业基础信息ID")
    private String drugEntBaseInfoId;

    @Schema(description = "有效期至")
    private String expiredDate;

    @Schema(description = "最小包装数量")
    private String minPkgCount;

    @Schema(description = "最小制剂数量")
    private String minPreparationsCount;

    @Schema(description = "药品名称")
    private String physicName;

    @Schema(description = "药品类型")
    private String physicType;

    @Schema(description = "药品类型名称")
    private String physicTypeName;

    @Schema(description = "制剂单位")
    private String preparationsUnit;

    @Schema(description = "产品标识码")
    private String prodCode;

    @Schema(description = "生产日期")
    private String produceDate;

    @Schema(description = "生产企业名称")
    private String produceEntName;

    @Schema(description = "生产批号")
    private String productBatchNo;

    @Schema(description = "产品编码")
    private String productCode;

    @Schema(description = "包装规格")
    private String tempPkgSpec;

    @Schema(description = "匹配的码关联关系列表（仅含查询码及其下级码）")
    private List<CodeRelationFilteredResponse.CodeRelationItem> codeRelationList;
}
