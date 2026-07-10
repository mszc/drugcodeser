package com.example.drugcodeser.repository;

import com.example.drugcodeser.entity.BillDetailCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 单据详情缓存 Repository
 */
@Repository
public interface BillDetailCacheRepository extends JpaRepository<BillDetailCache, Long> {

    /**
     * 根据单据号查询
     */
    BillDetailCache findByBillCode(String billCode);

    /**
     * 根据多个单据号查询
     */
    List<BillDetailCache> findByBillCodeIn(List<String> billCodes);
}
