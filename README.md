# Drug Code Ser

阿里健康药品追溯码接口服务，基于淘宝开放平台 SDK，实现药品追溯码相关业务查询。

## 技术栈

- **Spring Boot** 2.7.18
- **JDK** 11+
- **Spring Data JPA** + **MySQL** 8.0
- **Spring Data Redis** (Lettuce 连接池)
- **淘宝 SDK** taobao-sdk-java-auto (本地 jar)
- **Fastjson** 1.2.83
- **Lombok** 1.18.38
- **Knife4j / Swagger** (springdoc-openapi-ui)
- **Maven Wrapper** (mvnw)

## API 接口

### 1. 查询上游出库单明细

查询单据详情，包含药品信息和追溯码信息。

```
GET  /api/drug-code/upbill-detail?billCode=xxx&fromRefUserId=xxx
POST /api/drug-code/upbill-detail
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| billCode | String | 是 | 单据号 |
| fromRefUserId | String | 否 | 来源用户ID |
| toRefUserId | String | 否 | 目标用户ID |
| agentRefEntId | String | 否 | 代理企业标识 |

### 2. 按时间段批量查询入出库单

根据时间段分页查询入出库单据列表。

```
POST /api/drug-code/search-bill
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| beginDate | String | 是 | 开始日期 (yyyy-MM-dd) |
| endDate | String | 是 | 结束日期 (yyyy-MM-dd) |
| billCode | String | 否 | 单据号 |
| billType | String | 否 | A-出库 / B-入库 |
| curPage | Long | 否 | 页码（默认1） |
| pageSize | Long | 否 | 每页条数（默认20） |
| partnerIdSend | String | 否 | 发货企业ID |
| partnerIdRecv | String | 否 | 收货企业ID |
| uploadTimeBegin | String | 否 | 上传开始时间 |
| uploadTimeEnd | String | 否 | 上传结束时间 |

### 3. 查询单据详情（含药品追溯码）

根据单据号查询单据详情，包含药品信息和追溯码列表。

```
POST /api/drug-code/search-bill-detail
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| billCode | String | 是 | 单据号 |
| showCode | String | 是 | 1-显示追溯码 / 0-不显示 |

### 4. 查询单据详情（含码关联关系）

根据单据号查询单据详情，并将每个追溯码替换为其过滤版关联关系（仅含查询码及其下级码）。

```
GET  /api/drug-code/search-bill-detail-with-relations?billCode=xxx
POST /api/drug-code/search-bill-detail-with-relations
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| billCode | String | 是 | 单据号 |
| showCode | String | 否 | 是否显示追溯码，1=显示 0=不显示（默认1） |

### 5. 查询码关联关系

通过追溯码查询码关联关系，包含上下级包装关系、药品信息、生产信息等。

```
GET  /api/drug-code/code-relation?code=xxx
POST /api/drug-code/code-relation
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| code | String | 是 | 追溯码 |
| desRefEntId | String | 否 | 目标企业ID |

### 6. 查询码关联关系（过滤版）

通过追溯码查询码关联关系，仅返回查询码及其下级码，不包含同级码和上级码。

```
GET  /api/drug-code/code-relation-filtered?code=xxx
POST /api/drug-code/code-relation-filtered
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| code | String | 是 | 追溯码 |
| desRefEntId | String | 否 | 目标企业ID |

### 7. 查询单据详情（含码关联关系，优先缓存）

优先从 Redis/MySQL 缓存查询，缓存未命中时调用上游接口并回写缓存。

```
GET  /api/drug-code/search-bill-detail-with-relations-cached?billCode=xxx
POST /api/drug-code/search-bill-detail-with-relations-cached
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| billCode | String | 是 | 单据号 |

## 项目结构

```
drugcodeser/
├── pom.xml
├── mvnw.cmd                              # Maven Wrapper (Windows)
├── mvnw                                  # Maven Wrapper (Linux/Mac)
├── .mvn/wrapper/
│   ├── maven-wrapper.jar
│   └── maven-wrapper.properties
├── taobao-sdk-java-auto_xxx.jar          # 淘宝 SDK jar
├── start.ps1                              # Windows 启动脚本
└── src/main/
    ├── java/com/example/drugcodeser/
    │   ├── DrugCodeSerApplication.java    # 启动类
    │   ├── client/
    │   │   └── TaobaoApiClient.java       # 淘宝 SDK 客户端封装
    │   ├── config/
    │   │   ├── TaobaoApiConfig.java       # 淘宝 API 配置类
    │   │   └── RedisConfig.java           # Redis 配置类
    │   ├── controller/
    │   │   └── DrugCodeController.java    # REST 接口
    │   ├── dto/
    │   │   ├── request/
    │   │   │   ├── QueryCodeRelationRequest.java
    │   │   │   ├── SearchBillDetailRequest.java
    │   │   │   ├── SearchBillRequest.java
    │   │   │   └── UpbillDetailWithCodeRequest.java
    │   │   └── response/
    │   │       ├── BillDetailItemWithCodesDto.java
    │   │       ├── BillDetailWithCodeRelationsResponse.java
    │   │       └── CodeRelationFilteredResponse.java
    │   ├── entity/
    │   │   └── BillDetailCache.java       # 单据明细缓存实体
    │   ├── exception/
    │   │   └── GlobalExceptionHandler.java
    │   ├── repository/
    │   │   └── BillDetailCacheRepository.java
    │   ├── service/
    │   │   ├── DrugCodeService.java       # 业务逻辑
    │   │   └── BillDetailCacheService.java # 缓存服务
    │   └── task/
    │       └── BillDetailScheduledTask.java # 定时任务
    └── resources/
        ├── application.yml                # 配置文件
        └── logback-spring.xml             # 日志配置
```

## 核心特性

### licenseToken 自动管理

- **内存缓存**：按 `refEntId` 隔离，可配置有效期（默认 86000 秒）
- **自动获取**：首次调用或过期时自动调用 `license.token.get` 获取
- **失效重试**：业务接口返回 token 失效时，自动清除缓存、重新获取并重试一次

### 单据详情缓存系统

采用 **三级缓存** 策略：Redis → MySQL → 上游 API

- **Redis 高速缓存**：24 小时 TTL，毫秒级响应
- **MySQL 持久化缓存**：长期保存，Redis 未命中时回退查询并回写 Redis
- **上游 API 兜底**：双层缓存均未命中时调用淘宝 API 并回写两级缓存
- **缓存查询接口**：`/api/drug-code/search-bill-detail-with-relations-cached`

### 定时任务自动缓存

- 每 30 分钟自动执行一次（启动后 60 秒开始）
- 查询近 24 小时内的所有入出库单据
- 自动过滤已缓存单据，仅拉取新增单据
- 失败单据记录日志，不影响整体任务执行

### 错误处理

- 统一异常拦截（GlobalExceptionHandler）
- 兼容淘宝 API 的 `error_response` 和业务层错误两种格式
- 日志同时输出到控制台和文件（按天滚动，保留30天）

## 快速开始

### 1. 环境要求

- JDK 11+
- MySQL 8.0+
- Redis 6.0+

### 2. 配置

编辑 `src/main/resources/application.yml`：

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/drugcodeser?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
  redis:
    host: localhost
    port: 6379
    password:
    timeout: 5000ms
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0

taobao:
  api:
    server-url: https://gw.api.taobao.com/router/rest
    app-key: 你的appKey
    app-secret: 你的appSecret
    ref-ent-id: 你的企业标识
    license: 你的license
    auth-ref-user-id: 你的授权用户ID
    license-token-cache-seconds: 86000
```

### 3. 编译

使用 Maven Wrapper（推荐，无需安装 Maven）：

```bash
# Windows
mvnw.cmd clean package -DskipTests

# Linux/Mac
./mvnw clean package -DskipTests
```

或使用本地 Maven：

```bash
mvn clean package -DskipTests
```

### 4. 启动

```bash
java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED -jar target/drug-code-ser-1.0.0.jar
```

或使用 Windows 启动脚本：

```powershell
.\start.ps1
```

### 5. 查看文档

启动后访问 Swagger UI：http://localhost:8080/swagger-ui.html

## 淘宝开放平台文档

- [获取 licenseToken](https://open.taobao.com/api.htm?docId=73380&docType=2&scopeId=28314)
- [查询单据详情（含追溯码）](https://open.taobao.com/api.htm?docId=65505&docType=2&scopeId=28314)
- [批量查询入出库单](https://open.taobao.com/api.htm?docId=65764&docType=2&scopeId=28314)
- [查询码关联关系](https://open.taobao.com/api.htm?docId=65510&docType=2&scopeId=28314)

## License

Private project for enterprise use.
