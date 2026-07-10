package com.example.drugcodeser.service;

import com.alibaba.fastjson.JSON;
import com.example.drugcodeser.dto.response.BillDetailWithCodeRelationsResponse;
import com.example.drugcodeser.entity.BillDetailCache;
import com.example.drugcodeser.repository.BillDetailCacheRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 单据详情缓存服务：Redis（快速查询）→ MySQL（持久化）→ API（兜底）
 */
@Slf4j
@Service
public class BillDetailCacheService {

    private static final String REDIS_PREFIX = "bill:detail:";
    private static final Duration REDIS_TTL = Duration.ofHours(24);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private BillDetailCacheRepository mysqlRepo;

    @Autowired
    private DrugCodeService drugCodeService;

    /**
     * 查询单据详情（含码关联关系），优先走缓存
     */
    public BillDetailWithCodeRelationsResponse getBillDetailWithCache(String billCode) {
        // 1. 查 Redis
        String redisKey = REDIS_PREFIX + billCode;
        try {
            Object cached = redisTemplate.opsForValue().get(redisKey);
            if (cached != null) {
                log.debug("Redis 命中: {}", billCode);
                // Redis 存的是 JSON 字符串，需要反序列化
                if (cached instanceof String) {
                    return JSON.parseObject((String) cached, BillDetailWithCodeRelationsResponse.class);
                }
            }
        } catch (Exception e) {
            log.warn("Redis 查询异常: {}, 回退到 MySQL 查询", e.getMessage());
        }

        // 2. 查 MySQL
        try {
            BillDetailCache cache = mysqlRepo.findByBillCode(billCode);
            if (cache != null && cache.getDetailJson() != null) {
                log.debug("MySQL 命中: {}", billCode);
                BillDetailWithCodeRelationsResponse response = buildResponseFromCache(cache);
                // 回写 Redis
                saveToRedis(redisKey, response);
                return response;
            }
        } catch (Exception e) {
            log.warn("MySQL 查询异常: {}, 回退到 API 查询", e.getMessage());
        }

        // 3. 兜底：调用 API
        log.info("缓存未命中，调用 API: {}", billCode);
        BillDetailWithCodeRelationsResponse response = drugCodeService.searchBillDetailWithCodeRelations(billCode);
        if (response != null && response.isSuccess()) {
            saveToCache(response);
        }
        return response;
    }

    /**
     * 批量保存到 MySQL + Redis
     */
    public void saveToCache(BillDetailWithCodeRelationsResponse response) {
        if (response == null || response.getResult() == null
                || response.getResult().getModel() == null) {
            return;
        }
        BillDetailWithCodeRelationsResponse.BillDetailModel model = response.getResult().getModel();
        String billCode = model.getBillCode();
        if (billCode == null) {
            return;
        }

        try {
            // 1. MySQL 持久化
            BillDetailCache cache = Optional.ofNullable(mysqlRepo.findByBillCode(billCode))
                    .orElse(new BillDetailCache());

            cache.setBillCode(billCode);
            cache.setBillOutId(model.getBillOutId());
            cache.setBillTime(model.getBillTime());
            cache.setBillType(model.getBillType());
            cache.setBillTypeName(model.getBillTypeName());
            cache.setFromEntName(model.getFromEntName());
            cache.setFromUserId(model.getFromUserId());
            cache.setToEntName(model.getToEntName());
            cache.setToUserId(model.getToUserId());
            cache.setModDate(model.getModDate());
            cache.setProcessDate(model.getProcessDate());
            cache.setDetailJson(JSON.toJSONString(model.getBillChkInOutDetailList()));
            mysqlRepo.save(cache);
            log.debug("MySQL 保存成功: {}", billCode);

            // 2. Redis 缓存
            String redisKey = REDIS_PREFIX + billCode;
            saveToRedis(redisKey, response);
        } catch (Exception e) {
            log.error("缓存保存失败: {}", billCode, e);
        }
    }

    private void saveToRedis(String redisKey, BillDetailWithCodeRelationsResponse response) {
        try {
            redisTemplate.opsForValue().set(redisKey, JSON.toJSONString(response), REDIS_TTL);
            log.debug("Redis 写入成功: {}", redisKey);
        } catch (Exception e) {
            log.warn("Redis 写入失败: {}", redisKey, e.getMessage());
        }
    }

    /**
     * 从 MySQL 实体重构完整响应
     */
    private BillDetailWithCodeRelationsResponse buildResponseFromCache(BillDetailCache cache) {
        BillDetailWithCodeRelationsResponse.BillDetailModel model = new BillDetailWithCodeRelationsResponse.BillDetailModel();
        model.setBillCode(cache.getBillCode());
        model.setBillOutId(cache.getBillOutId());
        model.setBillTime(cache.getBillTime());
        model.setBillType(cache.getBillType());
        model.setBillTypeName(cache.getBillTypeName());
        model.setFromEntName(cache.getFromEntName());
        model.setFromUserId(cache.getFromUserId());
        model.setToEntName(cache.getToEntName());
        model.setToUserId(cache.getToUserId());
        model.setModDate(cache.getModDate());
        model.setProcessDate(cache.getProcessDate());
        if (cache.getDetailJson() != null) {
            List<com.example.drugcodeser.dto.response.BillDetailItemWithCodesDto> items =
                    JSON.parseArray(cache.getDetailJson(),
                            com.example.drugcodeser.dto.response.BillDetailItemWithCodesDto.class);
            model.setBillChkInOutDetailList(items);
        }

        BillDetailWithCodeRelationsResponse.Result result = new BillDetailWithCodeRelationsResponse.Result();
        result.setModel(model);
        result.setMsgCode("SUCCESS");
        result.setMsgInfo("调用成功");
        result.setResponseSuccess(true);

        BillDetailWithCodeRelationsResponse response = new BillDetailWithCodeRelationsResponse();
        response.setSuccess(true);
        response.setResult(result);
        return response;
    }
}
