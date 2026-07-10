package com.example.drugcodeser.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.drugcodeser.client.TaobaoApiClient;
import com.example.drugcodeser.config.TaobaoApiConfig;
import com.example.drugcodeser.dto.request.QueryCodeRelationRequest;
import com.example.drugcodeser.dto.request.SearchBillDetailRequest;
import com.example.drugcodeser.dto.request.SearchBillRequest;
import com.example.drugcodeser.dto.request.UpbillDetailWithCodeRequest;
import com.example.drugcodeser.dto.response.BillDetailItemWithCodesDto;
import com.example.drugcodeser.dto.response.BillDetailWithCodeRelationsResponse;
import com.example.drugcodeser.dto.response.CodeRelationFilteredResponse;
import com.taobao.api.request.AlibabaAlihealthDrugCodeKytWesLicenseTokenGetRequest;
import com.taobao.api.request.AlibabaAlihealthDrugCodeKytWesQuerycoderelationRequest;
import com.taobao.api.request.AlibabaAlihealthDrugKytWesSearchbillDetailRequest;
import com.taobao.api.request.AlibabaAlihealthDrugKytWesSearchbillRequest;
import com.taobao.api.request.AlibabaAlihealthDrugKytWesUpbillDetailwithcodeRequest;
import com.taobao.api.response.AlibabaAlihealthDrugCodeKytWesLicenseTokenGetResponse;
import com.taobao.api.response.AlibabaAlihealthDrugCodeKytWesQuerycoderelationResponse;
import com.taobao.api.response.AlibabaAlihealthDrugKytWesSearchbillDetailResponse;
import com.taobao.api.response.AlibabaAlihealthDrugKytWesSearchbillResponse;
import com.taobao.api.response.AlibabaAlihealthDrugKytWesUpbillDetailwithcodeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DrugCodeService {

    private final TaobaoApiClient taobaoApiClient;
    private final TaobaoApiConfig taobaoApiConfig;

    public DrugCodeService(TaobaoApiClient taobaoApiClient, TaobaoApiConfig taobaoApiConfig) {
        this.taobaoApiClient = taobaoApiClient;
        this.taobaoApiConfig = taobaoApiConfig;
    }

    // ==================== 业务接口 ====================

    /**
     * 查询单据详情 + 码关联关系聚合 —— 将每个追溯码的关联关系按药品匹配并入单据明细
     */
    public BillDetailWithCodeRelationsResponse searchBillDetailWithCodeRelations(SearchBillDetailRequest request) {
        // 先查单据详情
        AlibabaAlihealthDrugKytWesSearchbillDetailResponse billResponse = searchBillDetail(request);
        AlibabaAlihealthDrugKytWesSearchbillDetailResponse.BillInOutDetailDto model = billResponse.getResult().getModel();
        if (model == null) {
            throw new RuntimeException("单据详情无数据");
        }

        List<String> codes = model.getCodes();
        log.info("单据 {} 共有 {} 个追溯码，开始查询每个码的关联关系", model.getBillCode(),
                codes != null ? codes.size() : 0);

        // 多线程查询每个追溯码的过滤版关联关系，带限流控制
        Map<String, List<CodeRelationFilteredResponse.CodeRelationItem>> codeRelationMap = new LinkedHashMap<>();
        if (codes != null && !codes.isEmpty()) {
            int total = codes.size();
            AtomicInteger done = new AtomicInteger(0);
            Semaphore apiSemaphore = new Semaphore(3);
            AtomicLong lastApiCallTime = new AtomicLong(0);
            long minIntervalMs = 300;
            ExecutorService executor = Executors.newFixedThreadPool(10);
            try {
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (String code : codes) {
                    futures.add(CompletableFuture.runAsync(() -> {
                        int current = done.incrementAndGet();
                        log.debug("正在查询码关联关系 [{}/{}]: {}", current, total, code);
                        try {
                            apiSemaphore.acquire();
                            try {
                                // 限流：确保两次请求至少间隔 minIntervalMs
                                long now;
                                long lastCall;
                                do {
                                    now = System.currentTimeMillis();
                                    lastCall = lastApiCallTime.get();
                                    long waitMs = lastCall + minIntervalMs - now;
                                    if (waitMs > 0) {
                                        Thread.sleep(waitMs);
                                    }
                                } while (!lastApiCallTime.compareAndSet(lastCall,
                                        Math.max(System.currentTimeMillis(), lastCall + minIntervalMs)));

                                // 带重试的 API 调用（限流时最多重试3次）
                                CodeRelationFilteredResponse filtered = null;
                                for (int attempt = 0; attempt < 3; attempt++) {
                                    try {
                                        QueryCodeRelationRequest codeReq = new QueryCodeRelationRequest();
                                        codeReq.setCode(code);
                                        filtered = queryCodeRelationFiltered(codeReq);
                                        break;
                                    } catch (Exception e) {
                                        boolean isLimit = e.getMessage() != null
                                                && e.getMessage().contains("LIMIT");
                                        if (isLimit && attempt < 2) {
                                            log.warn("追溯码 {} 触发限流，第{}次重试...", code, attempt + 1);
                                            Thread.sleep(1000L * (attempt + 1));
                                        } else {
                                            throw e;
                                        }
                                    }
                                }
                                if (filtered != null && filtered.getCodeRelationList() != null
                                        && !filtered.getCodeRelationList().isEmpty()) {
                                    String matchKey = (filtered.getProdId() != null ? filtered.getProdId() : "")
                                            + "#" + (filtered.getProduceBatchNo() != null ? filtered.getProduceBatchNo() : "");
                                    synchronized (codeRelationMap) {
                                        codeRelationMap.computeIfAbsent(matchKey, k -> new ArrayList<>())
                                                .addAll(filtered.getCodeRelationList());
                                    }
                                }
                            } finally {
                                apiSemaphore.release();
                            }
                        } catch (Exception e) {
                            log.error("追溯码 {} 关联关系查询失败，跳过: {}", code, e.getMessage());
                        }
                    }, executor));
                }
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } finally {
                executor.shutdown();
            }
        }
        log.info("码关联关系分组完成，共 {} 个匹配组", codeRelationMap.size());

        // 构建响应：将 codeRelationList 按匹配规则并入 billChkInOutDetailList 的每一项
        List<BillDetailItemWithCodesDto> detailItems = new ArrayList<>();
        List<AlibabaAlihealthDrugKytWesSearchbillDetailResponse.Billchkinoutdetaillistdtolist> rawDetailList =
                model.getBillChkInOutDetailListDTOList();
        if (rawDetailList != null) {
            for (AlibabaAlihealthDrugKytWesSearchbillDetailResponse.Billchkinoutdetaillistdtolist rawItem : rawDetailList) {
                BillDetailItemWithCodesDto item = new BillDetailItemWithCodesDto();
                item.setApproveNo(rawItem.getApproveNo());
                item.setDrugEntBaseInfoId(rawItem.getDrugEntBaseInfoId());
                item.setExpiredDate(rawItem.getExpiredDate());
                item.setMinPkgCount(rawItem.getMinPkgCount());
                item.setMinPreparationsCount(rawItem.getMinPreparationsCount());
                item.setPhysicName(rawItem.getPhysicName());
                item.setPhysicType(rawItem.getPhysicType());
                item.setPhysicTypeName(rawItem.getPhysicTypeName());
                item.setPreparationsUnit(rawItem.getPreparationsUnit());
                item.setProdCode(rawItem.getProdCode());
                item.setProduceDate(rawItem.getProduceDate());
                item.setProduceEntName(rawItem.getProduceEntName());
                item.setProductBatchNo(rawItem.getProductBatchNo());
                item.setProductCode(rawItem.getProductCode());
                item.setTempPkgSpec(rawItem.getTempPkgSpec());

                // 按 drugEntBaseInfoId + productBatchNo 匹配 codeRelationList
                String itemKey = (rawItem.getDrugEntBaseInfoId() != null ? rawItem.getDrugEntBaseInfoId() : "")
                        + "#" + (rawItem.getProductBatchNo() != null ? rawItem.getProductBatchNo() : "");
                item.setCodeRelationList(codeRelationMap.getOrDefault(itemKey, new ArrayList<>()));
                detailItems.add(item);
            }
        }

        BillDetailWithCodeRelationsResponse.BillDetailModel billModel = new BillDetailWithCodeRelationsResponse.BillDetailModel();
        billModel.setBillCode(model.getBillCode());
        billModel.setBillOutId(model.getBillOutId());
        billModel.setBillTime(model.getBillTime());
        billModel.setBillType(model.getBillType());
        billModel.setBillTypeName(model.getBillTypeName());
        billModel.setFromEntName(model.getFromEntName());
        billModel.setFromUserId(model.getFromUserId());
        billModel.setToEntName(model.getToEntName());
        billModel.setToUserId(model.getToUserId());
        billModel.setModDate(model.getModDate());
        billModel.setProcessDate(model.getProcessDate());
        billModel.setBillChkInOutDetailList(detailItems);

        BillDetailWithCodeRelationsResponse.Result result = new BillDetailWithCodeRelationsResponse.Result();
        result.setModel(billModel);
        result.setMsgCode("SUCCESS");
        result.setMsgInfo("调用成功");
        result.setResponseSuccess(true);

        BillDetailWithCodeRelationsResponse response = new BillDetailWithCodeRelationsResponse();
        response.setSuccess(true);
        response.setResult(result);

        log.info("单据 {} 聚合完成，明细项: {} 条", model.getBillCode(), detailItems.size());
        return response;
    }

    /**
     * 查询单据详情 + 码关联关系聚合（仅需单据号的便捷方法）
     */
    public BillDetailWithCodeRelationsResponse searchBillDetailWithCodeRelations(String billCode) {
        SearchBillDetailRequest request = new SearchBillDetailRequest();
        request.setBillCode(billCode);
        request.setShowCode("1");
        return searchBillDetailWithCodeRelations(request);
    }

    /**
     * 查询单据详情（含药品信息和追溯码）
     */
    public AlibabaAlihealthDrugKytWesSearchbillDetailResponse searchBillDetail(SearchBillDetailRequest request) {
        log.info("查询单据详情，单据号: {}, showCode: {}", request.getBillCode(), request.getShowCode());

        boolean retried = false;
        String licenseToken;
        while (true) {
            licenseToken = getLicenseToken();

            AlibabaAlihealthDrugKytWesSearchbillDetailRequest taobaoRequest =
                    new AlibabaAlihealthDrugKytWesSearchbillDetailRequest();
            taobaoRequest.setRefEntId(taobaoApiConfig.getRefEntId());
            taobaoRequest.setLicenseToken(licenseToken);
            taobaoRequest.setBillCode(request.getBillCode());
            taobaoRequest.setShowCode(request.getShowCode());

            String authRefUserId = taobaoApiConfig.getAuthRefUserId();
            if (authRefUserId != null && !authRefUserId.isEmpty()) {
                taobaoRequest.setAuthRefUserId(authRefUserId);
            }

            AlibabaAlihealthDrugKytWesSearchbillDetailResponse response =
                    taobaoApiClient.execute(taobaoRequest);

            log.debug("单据详情API完整响应: {}", response.getBody());

            if (response.isSuccess()) {
                if (response.getResult() != null && Boolean.TRUE.equals(response.getResult().getResponseSuccess())) {
                    log.debug("查询成功，消息码: {}, 消息: {}",
                            response.getResult().getMsgCode(),
                            response.getResult().getMsgInfo());
                    if (response.getResult().getModel() != null) {
                        log.debug("model详情 - 单据号: {}, 发货企业: {}, 收货企业: {}, 药品明细数: {}, 追溯码数: {}",
                                response.getResult().getModel().getBillCode(),
                                response.getResult().getModel().getFromEntName(),
                                response.getResult().getModel().getToEntName(),
                                response.getResult().getModel().getBillChkInOutDetailListDTOList() != null ?
                                        response.getResult().getModel().getBillChkInOutDetailListDTOList().size() : 0,
                                response.getResult().getModel().getCodes() != null ?
                                        response.getResult().getModel().getCodes().size() : 0);
                    } else {
                        log.warn("model为空，无业务数据返回");
                    }
                    return response;
                } else {
                    String bizMsg = response.getResult() != null ?
                            response.getResult().getMsgCode() + ": " + response.getResult().getMsgInfo() : "未知业务错误";
                    log.error("业务失败: {}", bizMsg);
                    throw new RuntimeException("业务失败: " + bizMsg);
                }
            } else {
                String errorMsg = getErrorMessage(response);
                if (!retried && isTokenInvalidError(errorMsg)) {
                    log.warn("检测到Token无效，清除缓存并重试一次...");
                    clearCachedToken();
                    retried = true;
                    continue;
                }
                log.error("API调用失败: {}", errorMsg);
                throw new RuntimeException("API调用失败: " + errorMsg);
            }
        }
    }

    /**
     * 按时间段批量查询入出库单信息
     */
    public AlibabaAlihealthDrugKytWesSearchbillResponse searchBill(SearchBillRequest request) {
        log.info("批量查询单据，开始日期: {}, 结束日期: {}", request.getBeginDate(), request.getEndDate());

        boolean retried = false;
        String licenseToken;
        while (true) {
            licenseToken = getLicenseToken();

            AlibabaAlihealthDrugKytWesSearchbillRequest taobaoRequest =
                    new AlibabaAlihealthDrugKytWesSearchbillRequest();
            taobaoRequest.setRefEntId(taobaoApiConfig.getRefEntId());
            taobaoRequest.setLicenseToken(licenseToken);
            taobaoRequest.setAuthRefUserId(taobaoApiConfig.getAuthRefUserId());
            taobaoRequest.setBeginDate(request.getBeginDate());
            taobaoRequest.setEndDate(request.getEndDate());

            if (request.getBillCode() != null && !request.getBillCode().isEmpty()) {
                taobaoRequest.setBillCode(request.getBillCode());
            }
            if (request.getBillType() != null && !request.getBillType().isEmpty()) {
                taobaoRequest.setBillType(request.getBillType());
            }
            taobaoRequest.setCurPage(request.getCurPage() != null ? request.getCurPage() : 1L);
            taobaoRequest.setPageSize(request.getPageSize() != null ? request.getPageSize() : 20L);
            if (request.getPartnerIdSend() != null && !request.getPartnerIdSend().isEmpty()) {
                taobaoRequest.setPartnerIdSend(request.getPartnerIdSend());
            }
            if (request.getPartnerIdRecv() != null && !request.getPartnerIdRecv().isEmpty()) {
                taobaoRequest.setPartnerIdRecv(request.getPartnerIdRecv());
            }
            if (request.getUploadTimeBegin() != null && !request.getUploadTimeBegin().isEmpty()) {
                taobaoRequest.setUploadTimeBegin(request.getUploadTimeBegin());
            }
            if (request.getUploadTimeEnd() != null && !request.getUploadTimeEnd().isEmpty()) {
                taobaoRequest.setUploadTimeEnd(request.getUploadTimeEnd());
            }

            AlibabaAlihealthDrugKytWesSearchbillResponse response =
                    taobaoApiClient.execute(taobaoRequest);

            log.debug("批量查询API完整响应: {}", response.getBody());

            if (response.isSuccess()) {
                if (response.getResult() != null && Boolean.TRUE.equals(response.getResult().getSuccess())) {
                    log.debug("批量查询成功，消息码: {}, 消息: {}",
                            response.getResult().getMsgCode(),
                            response.getResult().getMsgInfo());
                    if (response.getResult().getModel() != null) {
                        log.debug("总数: {}, 当前页条数: {}",
                                response.getResult().getModel().getTotalNum(),
                                response.getResult().getModel().getResultList() != null ?
                                        response.getResult().getModel().getResultList().size() : 0);
                    } else {
                        log.warn("model为空，无业务数据返回");
                    }
                    return response;
                } else {
                    String bizMsg = response.getResult() != null ?
                            response.getResult().getMsgCode() + ": " + response.getResult().getMsgInfo() : "未知业务错误";
                    log.error("业务失败: {}", bizMsg);
                    throw new RuntimeException("业务失败: " + bizMsg);
                }
            } else {
                String errorMsg = getErrorMessage(response);
                if (!retried && isTokenInvalidError(errorMsg)) {
                    log.warn("检测到Token无效，清除缓存并重试一次...");
                    clearCachedToken();
                    retried = true;
                    continue;
                }
                log.error("API调用失败: {}", errorMsg);
                throw new RuntimeException("API调用失败: " + errorMsg);
            }
        }
    }

    /**
     * 查询上游出库单明细（带追溯码信息）
     */
    public AlibabaAlihealthDrugKytWesUpbillDetailwithcodeResponse getUpbillDetailWithCode(
            UpbillDetailWithCodeRequest request) {
        log.info("查询上游出库单明细，单据号: {}", request.getBillCode());

        boolean retried = false;
        String licenseToken;
        while (true) {
            licenseToken = getLicenseToken();

            AlibabaAlihealthDrugKytWesUpbillDetailwithcodeRequest taobaoRequest =
                    new AlibabaAlihealthDrugKytWesUpbillDetailwithcodeRequest();
            taobaoRequest.setBillCode(request.getBillCode());
            taobaoRequest.setRefEntId(taobaoApiConfig.getRefEntId());
            taobaoRequest.setLicenseToken(licenseToken);

            String fromRefUserId = request.getFromRefUserId() != null ?
                    request.getFromRefUserId() : taobaoApiConfig.getFromRefUserId();
            if (fromRefUserId != null && !fromRefUserId.isEmpty()) {
                taobaoRequest.setFromRefUserId(fromRefUserId);
            }

            String toRefUserId = request.getToRefUserId() != null ?
                    request.getToRefUserId() : taobaoApiConfig.getToRefUserId();
            if (toRefUserId != null && !toRefUserId.isEmpty()) {
                taobaoRequest.setToRefUserId(toRefUserId);
            }

            String agentRefEntId = request.getAgentRefEntId() != null ?
                    request.getAgentRefEntId() : taobaoApiConfig.getAgentRefEntId();
            if (agentRefEntId != null && !agentRefEntId.isEmpty()) {
                taobaoRequest.setAgentRefEntId(agentRefEntId);
            }

            AlibabaAlihealthDrugKytWesUpbillDetailwithcodeResponse response =
                    taobaoApiClient.execute(taobaoRequest);

            log.debug("上游出库单API完整响应: {}", response.getBody());

            if (response.isSuccess()) {
                if (response.getResult() != null && Boolean.TRUE.equals(response.getResult().getSuccess())) {
                    log.debug("查询成功，消息码: {}, 消息: {}",
                            response.getResult().getMsgCode(),
                            response.getResult().getMsgInfo());
                    return response;
                } else {
                    String bizMsg = response.getResult() != null ?
                            response.getResult().getMsgCode() + ": " + response.getResult().getMsgInfo() : "未知业务错误";
                    log.error("业务失败: {}", bizMsg);
                    throw new RuntimeException("业务失败: " + bizMsg);
                }
            } else {
                String errorMsg = getErrorMessage(response);
                if (!retried && isTokenInvalidError(errorMsg)) {
                    log.warn("检测到Token无效，清除缓存并重试一次...");
                    clearCachedToken();
                    retried = true;
                    continue;
                }
                log.error("API调用失败: {}", errorMsg);
                throw new RuntimeException("API调用失败: " + errorMsg);
            }
        }
    }

    // ==================== 码关联关系查询 ====================

    /**
     * 通过追溯码查询码关联关系
     */
    public AlibabaAlihealthDrugCodeKytWesQuerycoderelationResponse queryCodeRelation(QueryCodeRelationRequest request) {
        log.info("查询码关联关系，追溯码: {}", request.getCode());

        boolean retried = false;
        String licenseToken;
        while (true) {
            licenseToken = getLicenseToken();

            AlibabaAlihealthDrugCodeKytWesQuerycoderelationRequest taobaoRequest =
                    new AlibabaAlihealthDrugCodeKytWesQuerycoderelationRequest();
            taobaoRequest.setRefEntId(taobaoApiConfig.getRefEntId());
            taobaoRequest.setLicenseToken(licenseToken);
            taobaoRequest.setCode(request.getCode());
            String desRefEntId = (request.getDesRefEntId() != null && !request.getDesRefEntId().isEmpty())
                    ? request.getDesRefEntId() : taobaoApiConfig.getRefEntId();
            taobaoRequest.setDesRefEntId(desRefEntId);

            AlibabaAlihealthDrugCodeKytWesQuerycoderelationResponse response =
                    taobaoApiClient.execute(taobaoRequest);

            log.debug("码关联关系API完整响应: {}", response.getBody());

            if (response.isSuccess()) {
                if (response.getResult() != null && Boolean.TRUE.equals(response.getResult().getResponseSuccess())) {
                    log.debug("查询成功，消息码: {}, 消息: {}",
                            response.getResult().getMsgCode(),
                            response.getResult().getMsgInfo());
                    if (response.getResult().getModelList() != null) {
                        log.debug("关联关系数: {}", response.getResult().getModelList().size());
                    } else {
                        log.warn("modelList为空，无关联数据返回");
                    }
                    return response;
                } else {
                    String bizMsg = response.getResult() != null ?
                            response.getResult().getMsgCode() + ": " + response.getResult().getMsgInfo() : "未知业务错误";
                    log.error("业务失败: {}", bizMsg);
                    throw new RuntimeException("业务失败: " + bizMsg);
                }
            } else {
                String errorMsg = getErrorMessage(response);
                if (!retried && isTokenInvalidError(errorMsg)) {
                    log.warn("检测到Token无效，清除缓存并重试一次...");
                    clearCachedToken();
                    retried = true;
                    continue;
                }
                log.error("API调用失败: {}", errorMsg);
                throw new RuntimeException("API调用失败: " + errorMsg);
            }
        }
    }

    /**
     * 查询码关联关系（过滤版）—— 仅返回查询码及其下级码，不包含同级码和上级码
     */
    public CodeRelationFilteredResponse queryCodeRelationFiltered(QueryCodeRelationRequest request) {
        AlibabaAlihealthDrugCodeKytWesQuerycoderelationResponse response = queryCodeRelation(request);
        AlibabaAlihealthDrugCodeKytWesQuerycoderelationResponse.ResultModel result = response.getResult();
        if (result == null || result.getModelList() == null || result.getModelList().isEmpty()) {
            throw new RuntimeException("码关联关系查询无结果");
        }

        String queryCode = request.getCode();
        List<AlibabaAlihealthDrugCodeKytWesQuerycoderelationResponse.CodeRelationDto> modelList = result.getModelList();

        // 在 modelList 中找到匹配查询码的 CodeRelationDto
        AlibabaAlihealthDrugCodeKytWesQuerycoderelationResponse.CodeRelationDto matchedDto = null;
        for (AlibabaAlihealthDrugCodeKytWesQuerycoderelationResponse.CodeRelationDto dto : modelList) {
            if (queryCode.equals(dto.getCode())) {
                matchedDto = dto;
                break;
            }
        }
        if (matchedDto == null) {
            throw new RuntimeException("未找到追溯码 " + queryCode + " 的关联关系");
        }

        List<AlibabaAlihealthDrugCodeKytWesQuerycoderelationResponse.CodeInfo> rawList = matchedDto.getCodeRelationList();
        log.debug("原始关联关系数: {}", rawList != null ? rawList.size() : 0);

        // 过滤：只保留查询码本身 + 以查询码为父级的子码
        List<CodeRelationFilteredResponse.CodeRelationItem> filteredList = new ArrayList<>();
        if (rawList != null) {
            for (AlibabaAlihealthDrugCodeKytWesQuerycoderelationResponse.CodeInfo info : rawList) {
                if (queryCode.equals(info.getCode()) || queryCode.equals(info.getParentCode())) {
                    CodeRelationFilteredResponse.CodeRelationItem item = new CodeRelationFilteredResponse.CodeRelationItem();
                    item.setCode(info.getCode());
                    item.setCodeLevel(info.getCodeLevel());
                    item.setCodePackLevel(info.getCodePackLevel());
                    item.setParentCode(info.getParentCode());
                    item.setStatus(info.getStatus());
                    filteredList.add(item);
                }
            }
        }
        log.debug("过滤后关联关系数: {} (仅含查询码及其下级码)", filteredList.size());

        CodeRelationFilteredResponse resultResponse = new CodeRelationFilteredResponse();
        resultResponse.setQueryCode(queryCode);
        resultResponse.setIsSmallest(matchedDto.getIsSmallest());
        resultResponse.setCodeRelationList(filteredList);
        resultResponse.setBaseInfos(matchedDto.getBaseInfosDTO());
        resultResponse.setCodeActiveInfo(matchedDto.getCodeActiveInfoDTO());
        resultResponse.setProduceInfoList(matchedDto.getProduceInfoList());
        resultResponse.setPkgInfo(matchedDto.getPkgInfoDTO());

        // 从 baseInfos.baseInfoList[0] 提取 prodId 和 produceBatchNo，用于后续与单据明细匹配
        AlibabaAlihealthDrugCodeKytWesQuerycoderelationResponse.BaseInfosDto baseInfosDto = matchedDto.getBaseInfosDTO();
        if (baseInfosDto != null && baseInfosDto.getBaseInfoList() != null && !baseInfosDto.getBaseInfoList().isEmpty()) {
            AlibabaAlihealthDrugCodeKytWesQuerycoderelationResponse.BaseInfoDto firstBaseInfo = baseInfosDto.getBaseInfoList().get(0);
            resultResponse.setProdId(firstBaseInfo.getProdId());
            resultResponse.setProduceBatchNo(firstBaseInfo.getProduceBatchNo());
        }

        return resultResponse;
    }

    // ==================== Token 缓存管理 ====================

    private final Map<String, String> tokenCache = new ConcurrentHashMap<>();
    private final Map<String, Long> tokenExpireCache = new ConcurrentHashMap<>();

    /**
     * 获取 licenseToken，优先使用缓存（有效期1天）
     */
    private synchronized String getLicenseToken() {
        long now = System.currentTimeMillis();
        String refEntId = taobaoApiConfig.getRefEntId();
        String cachedToken = tokenCache.get(refEntId);
        Long expireTime = tokenExpireCache.get(refEntId);
        if (cachedToken != null && expireTime != null && now < expireTime) {
            log.debug("使用缓存的licenseToken: {}", cachedToken);
            return cachedToken;
        }

        return fetchAndCacheToken();
    }

    /**
     * 清除 token 缓存（token 失效时调用）
     */
    private void clearCachedToken() {
        String refEntId = taobaoApiConfig.getRefEntId();
        log.info("清除licenseToken缓存: refEntId={}, token={}", refEntId, tokenCache.get(refEntId));
        tokenCache.remove(refEntId);
        tokenExpireCache.remove(refEntId);
    }

    /**
     * 调用 API 获取新的 token 并缓存
     */
    private synchronized String fetchAndCacheToken() {
        long now = System.currentTimeMillis();
        String refEntId = taobaoApiConfig.getRefEntId();

        log.info("调用 license.token.get 获取新token...");
        log.debug("参数 - refEntId: {}, license: {}", refEntId, taobaoApiConfig.getLicense());

        AlibabaAlihealthDrugCodeKytWesLicenseTokenGetRequest request =
                new AlibabaAlihealthDrugCodeKytWesLicenseTokenGetRequest();
        request.setRefEntId(refEntId);
        request.setLicense(taobaoApiConfig.getLicense());

        AlibabaAlihealthDrugCodeKytWesLicenseTokenGetResponse response =
                taobaoApiClient.execute(request);

        log.debug("licenseToken API 完整响应: {}", response.getBody());

        if (response.isSuccess() && response.getResult() != null
                && response.getResult().getModel() != null) {
            AlibabaAlihealthDrugCodeKytWesLicenseTokenGetResponse.TokenInfo tokenInfo =
                    response.getResult().getModel();
            String licenseToken = tokenInfo.getLicenseToken();
            log.debug("licenseToken 获取成功: {}", licenseToken);

            tokenCache.put(refEntId, licenseToken);
            tokenExpireCache.put(refEntId, now + taobaoApiConfig.getLicenseTokenCacheSeconds() * 1000L);
            return licenseToken;
        } else {
            String errorMsg = getErrorMessage(response);
            log.error("licenseToken 获取失败: {}", errorMsg);
            throw new RuntimeException("获取licenseToken失败: " + errorMsg);
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 判断错误是否由 token 失效引起
     */
    private boolean isTokenInvalidError(String errorMsg) {
        if (errorMsg == null) return false;
        String lower = errorMsg.toLowerCase();
        return lower.contains("licensetoken") || lower.contains("license_token")
                || (lower.contains("token") && (lower.contains("不正确") || lower.contains("invalid")
                || lower.contains("expire") || lower.contains("过期")));
    }

    /**
     * 从API响应中提取错误信息（兼容 error_response 格式）
     */
    private String getErrorMessage(Object response) {
        try {
            String body = (String) response.getClass().getMethod("getBody").invoke(response);
            if (body != null && !body.isEmpty()) {
                JSONObject json = JSON.parseObject(body);
                // 处理顶层 error_response
                JSONObject errorResponse = json.getJSONObject("error_response");
                if (errorResponse != null) {
                    String code = errorResponse.getString("code");
                    String msg = errorResponse.getString("msg");
                    String subMsg = errorResponse.getString("sub_msg");
                    StringBuilder sb = new StringBuilder("[code:").append(code).append("] ").append(msg);
                    if (subMsg != null && !subMsg.isEmpty()) {
                        sb.append(" - ").append(subMsg);
                    }
                    return sb.toString();
                }
                // 处理业务层 result 中的错误信息
                for (String key : json.keySet()) {
                    if (key.endsWith("_response")) {
                        JSONObject bizResponse = json.getJSONObject(key);
                        if (bizResponse != null) {
                            JSONObject result = bizResponse.getJSONObject("result");
                            if (result != null) {
                                String msgCode = result.getString("msg_code");
                                String msgInfo = result.getString("msg_info");
                                if (msgCode != null && !"SUCCESS".equals(msgCode)) {
                                    return msgCode + ": " + (msgInfo != null ? msgInfo : "无详细信息");
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // 兜底：尝试从 SDK 标准字段获取
        try {
            String subMsg = (String) response.getClass().getMethod("getSubMsg").invoke(response);
            String msg = (String) response.getClass().getMethod("getMsg").invoke(response);
            String subCode = (String) response.getClass().getMethod("getSubCode").invoke(response);
            String errorCode = (String) response.getClass().getMethod("getErrorCode").invoke(response);
            String code = subCode != null ? subCode : errorCode;
            String message = subMsg != null ? subMsg : msg;
            return "[" + (code != null ? code : "unknown") + "] " + (message != null ? message : "未知错误");
        } catch (Exception e) {
            return "无法解析错误信息, response body: " + response;
        }
    }
}
