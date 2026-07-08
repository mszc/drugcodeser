
package com.example.drugcodeser.controller;

import com.example.drugcodeser.dto.request.SearchBillDetailRequest;
import com.example.drugcodeser.dto.request.SearchBillRequest;
import com.example.drugcodeser.dto.request.UpbillDetailWithCodeRequest;
import com.taobao.api.response.AlibabaAlihealthDrugKytWesSearchbillDetailResponse;
import com.taobao.api.response.AlibabaAlihealthDrugKytWesSearchbillResponse;
import com.taobao.api.response.AlibabaAlihealthDrugKytWesUpbillDetailwithcodeResponse;
import com.example.drugcodeser.service.DrugCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/drug-code")
@Tag(name = "药品追溯码接口", description = "阿里健康药品追溯码相关接口")
public class DrugCodeController {

    private final DrugCodeService drugCodeService;

    public DrugCodeController(DrugCodeService drugCodeService) {
        this.drugCodeService = drugCodeService;
    }

    @GetMapping("/upbill-detail")
    @Operation(summary = "查询单据详情（带码信息）", description = "根据单据号查询单据详情，包含药品信息和追溯码信息")
    public ResponseEntity<AlibabaAlihealthDrugKytWesUpbillDetailwithcodeResponse> getUpbillDetailWithCode(
            @Parameter(description = "单据号", required = true)
            @RequestParam String billCode,
            @Parameter(description = "来源用户ID")
            @RequestParam(required = false) String fromRefUserId,
            @Parameter(description = "目标用户ID")
            @RequestParam(required = false) String toRefUserId,
            @Parameter(description = "代理企业标识")
            @RequestParam(required = false) String agentRefEntId) {

        log.info("接收到查询单据详情请求，单据号: {}", billCode);

        UpbillDetailWithCodeRequest request = new UpbillDetailWithCodeRequest();
        request.setBillCode(billCode);
        request.setFromRefUserId(fromRefUserId);
        request.setToRefUserId(toRefUserId);
        request.setAgentRefEntId(agentRefEntId);

        AlibabaAlihealthDrugKytWesUpbillDetailwithcodeResponse response =
                drugCodeService.getUpbillDetailWithCode(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/upbill-detail")
    @Operation(summary = "查询单据详情（POST方式）", description = "根据单据号查询单据详情，包含药品信息和追溯码信息")
    public ResponseEntity<AlibabaAlihealthDrugKytWesUpbillDetailwithcodeResponse> getUpbillDetailWithCodePost(
            @Valid @RequestBody UpbillDetailWithCodeRequest request) {
        log.info("接收到查询单据详情请求（POST），单据号: {}", request.getBillCode());
        AlibabaAlihealthDrugKytWesUpbillDetailwithcodeResponse response =
                drugCodeService.getUpbillDetailWithCode(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/search-bill")
    @Operation(summary = "按时间段批量查询入出库单信息", description = "根据时间段分页查询入出库单据列表")
    public ResponseEntity<AlibabaAlihealthDrugKytWesSearchbillResponse> searchBill(
            @Valid @RequestBody SearchBillRequest request) {
        log.info("接收到批量查询请求，开始日期: {}, 结束日期: {}", request.getBeginDate(), request.getEndDate());
        AlibabaAlihealthDrugKytWesSearchbillResponse response =
                drugCodeService.searchBill(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/search-bill-detail")
    @Operation(summary = "查询单据详情（含药品追溯码）", description = "根据单据号查询单据详情，包含药品信息和追溯码")
    public ResponseEntity<AlibabaAlihealthDrugKytWesSearchbillDetailResponse> searchBillDetail(
            @Valid @RequestBody SearchBillDetailRequest request) {
        log.info("接收到单据详情查询请求，单据号: {}, showCode: {}", request.getBillCode(), request.getShowCode());
        AlibabaAlihealthDrugKytWesSearchbillDetailResponse response =
                drugCodeService.searchBillDetail(request);
        return ResponseEntity.ok(response);
    }
}
