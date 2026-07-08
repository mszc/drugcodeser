package com.example.drugcodeser.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.drugcodeser.client.TaobaoApiClient;
import com.example.drugcodeser.config.TaobaoApiConfig;
import com.example.drugcodeser.dto.request.SearchBillDetailRequest;
import com.example.drugcodeser.dto.request.SearchBillRequest;
import com.example.drugcodeser.dto.request.UpbillDetailWithCodeRequest;
import com.taobao.api.request.AlibabaAlihealthDrugCodeKytWesLicenseTokenGetRequest;
import com.taobao.api.request.AlibabaAlihealthDrugKytWesSearchbillDetailRequest;
import com.taobao.api.request.AlibabaAlihealthDrugKytWesSearchbillRequest;
import com.taobao.api.request.AlibabaAlihealthDrugKytWesUpbillDetailwithcodeRequest;
import com.taobao.api.response.AlibabaAlihealthDrugCodeKytWesLicenseTokenGetResponse;
import com.taobao.api.response.AlibabaAlihealthDrugKytWesSearchbillDetailResponse;
import com.taobao.api.response.AlibabaAlihealthDrugKytWesSearchbillResponse;
import com.taobao.api.response.AlibabaAlihealthDrugKytWesUpbillDetailwithcodeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

            log.info("单据详情API完整响应: {}", response.getBody());

            if (response.isSuccess()) {
                if (response.getResult() != null && Boolean.TRUE.equals(response.getResult().getResponseSuccess())) {
                    log.info("查询成功，消息码: {}, 消息: {}",
                            response.getResult().getMsgCode(),
                            response.getResult().getMsgInfo());
                    if (response.getResult().getModel() != null) {
                        log.info("model详情 - 单据号: {}, 发货企业: {}, 收货企业: {}, 药品明细数: {}, 追溯码数: {}",
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

            log.info("批量查询API完整响应: {}", response.getBody());

            if (response.isSuccess()) {
                if (response.getResult() != null && Boolean.TRUE.equals(response.getResult().getSuccess())) {
                    log.info("批量查询成功，消息码: {}, 消息: {}",
                            response.getResult().getMsgCode(),
                            response.getResult().getMsgInfo());
                    if (response.getResult().getModel() != null) {
                        log.info("总数: {}, 当前页条数: {}",
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

            log.info("上游出库单API完整响应: {}", response.getBody());

            if (response.isSuccess()) {
                if (response.getResult() != null && Boolean.TRUE.equals(response.getResult().getSuccess())) {
                    log.info("查询成功，消息码: {}, 消息: {}",
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
            log.info("使用缓存的licenseToken: {}", cachedToken);
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
        log.info("参数 - refEntId: {}, license: {}", refEntId, taobaoApiConfig.getLicense());

        AlibabaAlihealthDrugCodeKytWesLicenseTokenGetRequest request =
                new AlibabaAlihealthDrugCodeKytWesLicenseTokenGetRequest();
        request.setRefEntId(refEntId);
        request.setLicense(taobaoApiConfig.getLicense());

        AlibabaAlihealthDrugCodeKytWesLicenseTokenGetResponse response =
                taobaoApiClient.execute(request);

        log.info("licenseToken API 完整响应: {}", response.getBody());

        if (response.isSuccess() && response.getResult() != null
                && response.getResult().getModel() != null) {
            AlibabaAlihealthDrugCodeKytWesLicenseTokenGetResponse.TokenInfo tokenInfo =
                    response.getResult().getModel();
            String licenseToken = tokenInfo.getLicenseToken();
            log.info("licenseToken 获取成功: {}", licenseToken);

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
