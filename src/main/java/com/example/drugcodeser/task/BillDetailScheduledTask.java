package com.example.drugcodeser.task;

import com.example.drugcodeser.dto.request.SearchBillRequest;
import com.example.drugcodeser.dto.response.BillDetailWithCodeRelationsResponse;
import com.example.drugcodeser.entity.BillDetailCache;
import com.example.drugcodeser.repository.BillDetailCacheRepository;
import com.example.drugcodeser.service.BillDetailCacheService;
import com.example.drugcodeser.service.DrugCodeService;
import com.taobao.api.response.AlibabaAlihealthDrugKytWesSearchbillResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 定时任务：定期刷新单据详情缓存
 */
@Slf4j
@Component
public class BillDetailScheduledTask {

    @Autowired
    private DrugCodeService drugCodeService;

    @Autowired
    private BillDetailCacheService cacheService;

    @Autowired
    private BillDetailCacheRepository mysqlRepo;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 每30分钟执行一次：查询近24小时内的单据并缓存
     */
    @Scheduled(fixedDelay = 30 * 60 * 1000, initialDelay = 60 * 1000)
    public void refreshBillDetailCache() {
        log.info("=== 定时任务开始：刷新单据详情缓存 ===");
        try {
            String today = LocalDate.now().format(DATE_FMT);
            String yesterday = LocalDate.now().minusDays(1).format(DATE_FMT);

            // 1. 查询近期的入库单据
            List<String> allBillCodes = new ArrayList<>();
            allBillCodes.addAll(fetchBillCodes(yesterday, today, "A")); // 出库
            allBillCodes.addAll(fetchBillCodes(yesterday, today, "B")); // 入库

            if (allBillCodes.isEmpty()) {
                log.info("未查到近期单据，任务结束");
                return;
            }

            // 去重
            List<String> uniqueCodes = allBillCodes.stream().distinct().collect(Collectors.toList());
            log.info("共查到 {} 个单据，去重后 {} 个", allBillCodes.size(), uniqueCodes.size());

            // 2. 过滤已缓存的
            List<BillDetailCache> existingCaches = mysqlRepo.findByBillCodeIn(uniqueCodes);
            Set<String> cachedCodes = existingCaches.stream()
                    .map(BillDetailCache::getBillCode)
                    .collect(Collectors.toSet());

            List<String> toFetch = uniqueCodes.stream()
                    .filter(code -> !cachedCodes.contains(code))
                    .collect(Collectors.toList());

            log.info("已缓存 {} 个，需拉取 {} 个", cachedCodes.size(), toFetch.size());

            // 3. 逐个拉取并缓存
            int success = 0;
            int fail = 0;
            for (String billCode : toFetch) {
                try {
                    log.debug("拉取单据详情: {}", billCode);
                    BillDetailWithCodeRelationsResponse response = drugCodeService
                            .searchBillDetailWithCodeRelations(billCode);
                    if (response != null && response.isSuccess()) {
                        cacheService.saveToCache(response);
                        success++;
                    } else {
                        fail++;
                    }
                } catch (Exception e) {
                    log.error("单据 {} 拉取失败: {}", billCode, e.getMessage());
                    fail++;
                }
            }

            log.info("=== 定时任务完成：成功 {} 个，失败 {} 个 ===", success, fail);
        } catch (Exception e) {
            log.error("定时任务异常: {}", e.getMessage(), e);
        }
    }

    private List<String> fetchBillCodes(String beginDate, String endDate, String billType) {
        List<String> codes = new ArrayList<>();
        try {
            SearchBillRequest request = new SearchBillRequest();
            request.setBeginDate(beginDate);
            request.setEndDate(endDate);
            request.setBillType(billType);
            request.setCurPage(1L);
            request.setPageSize(100L);

            AlibabaAlihealthDrugKytWesSearchbillResponse response = drugCodeService.searchBill(request);
            if (response.isSuccess() && response.getResult() != null
                    && response.getResult().getModel() != null) {
                List<AlibabaAlihealthDrugKytWesSearchbillResponse.BillChkInOutDo> resultList =
                        response.getResult().getModel().getResultList();
                if (resultList != null) {
                    for (AlibabaAlihealthDrugKytWesSearchbillResponse.BillChkInOutDo bill : resultList) {
                        if (bill.getBillCode() != null) {
                            codes.add(bill.getBillCode());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查询{}类型单据失败: {}", billType, e.getMessage());
        }
        return codes;
    }
}
