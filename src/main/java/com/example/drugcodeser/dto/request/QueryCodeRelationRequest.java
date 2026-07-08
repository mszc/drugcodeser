package com.example.drugcodeser.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
@Schema(description = "码关联关系查询请求")
public class QueryCodeRelationRequest {

    @NotBlank(message = "追溯码不能为空")
    @Schema(description = "追溯码", required = true, example = "87004720000000005994")
    private String code;

    @Schema(description = "目标企业ID（可选，未传则默认查询企业权限下的全量关系）")
    private String desRefEntId;
}
