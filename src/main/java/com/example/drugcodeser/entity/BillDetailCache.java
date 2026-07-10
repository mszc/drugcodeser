package com.example.drugcodeser.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 单据详情缓存表（MySQL持久化）
 */
@Data
@Entity
@Table(name = "bill_detail_cache", indexes = {
        @Index(name = "uk_bill_code", columnList = "billCode", unique = true)
})
public class BillDetailCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 单据号 */
    @Column(nullable = false, length = 100)
    private String billCode;

    /** 外部单据ID */
    @Column(length = 100)
    private String billOutId;

    /** 单据日期 */
    @Column(length = 20)
    private String billTime;

    /** 单据类型 */
    @Column(length = 10)
    private String billType;

    /** 单据类型名称 */
    @Column(length = 50)
    private String billTypeName;

    /** 发货企业名称 */
    @Column(length = 200)
    private String fromEntName;

    /** 发货企业用户ID */
    @Column(length = 100)
    private String fromUserId;

    /** 收货企业名称 */
    @Column(length = 200)
    private String toEntName;

    /** 收货企业用户ID */
    @Column(length = 100)
    private String toUserId;

    /** 修改日期 */
    @Column(length = 20)
    private String modDate;

    /** 处理日期 */
    @Column(length = 20)
    private String processDate;

    /** 药品明细列表（含码关联关系）JSON */
    @Lob
    @Column(columnDefinition = "MEDIUMTEXT")
    private String detailJson;

    /** 创建时间 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
