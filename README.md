# Drug Code Ser

阿里健康药品追溯码接口服务，基于淘宝开放平台 SDK，实现药品追溯码相关业务查询。

## 技术栈

- **Spring Boot** 2.7.18
- **JDK** 11+
- **淘宝 SDK** taobao-sdk-java-auto (本地 jar)
- **Fastjson** 1.2.83
- **Lombok** 1.18.38
- **Knife4j / Swagger** (springdoc-openapi-ui)

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

## 项目结构

```
drugcodeser/
├── pom.xml
├── taobao-sdk-java-auto_xxx.jar          # 淘宝 SDK jar
├── start.ps1                              # Windows 启动脚本
└── src/main/
    ├── java/com/example/drugcodeser/
    │   ├── DrugCodeSerApplication.java    # 启动类
    │   ├── client/
    │   │   └── TaobaoApiClient.java       # 淘宝 SDK 客户端封装
    │   ├── config/
    │   │   └── TaobaoApiConfig.java       # 配置类
    │   ├── controller/
    │   │   └── DrugCodeController.java    # REST 接口
    │   ├── dto/request/
    │   │   ├── SearchBillDetailRequest.java
    │   │   ├── SearchBillRequest.java
    │   │   └── UpbillDetailWithCodeRequest.java
    │   ├── exception/
    │   │   └── GlobalExceptionHandler.java
    │   └── service/
    │       └── DrugCodeService.java        # 业务逻辑
    └── resources/
        ├── application.yml                # 配置文件
        └── logback-spring.xml             # 日志配置
```

## 核心特性

### licenseToken 自动管理

- **内存缓存**：按 `refEntId` 隔离，24小时有效期
- **自动获取**：首次调用或过期时自动调用 `license.token.get` 获取
- **失效重试**：业务接口返回 token 失效时，自动清除缓存、重新获取并重试一次

### 错误处理

- 统一异常拦截（GlobalExceptionHandler）
- 兼容淘宝 API 的 `error_response` 和业务层错误两种格式
- 日志同时输出到控制台和文件（按天滚动，保留30天）

## 快速开始

### 1. 配置

编辑 `src/main/resources/application.yml`，填入淘宝开放平台凭证：

```yaml
taobao:
  api:
    server-url: https://eco.taobao.com/router/rest
    app-key: 你的appKey
    app-secret: 你的appSecret
    ref-ent-id: 你的企业标识
    license: 你的license
    auth-ref-user-id: 你的授权用户ID
```

### 2. 编译

```bash
mvn clean package -DskipTests
```

### 3. 启动

```bash
java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED -jar target/drug-code-ser-1.0.0.jar
```

或使用 Windows 启动脚本：

```powershell
.\start.ps1
```

### 4. 查看文档

启动后访问 Swagger UI：http://localhost:8080/swagger-ui.html

## 淘宝开放平台文档

- [获取 licenseToken](https://open.taobao.com/api.htm?docId=73380&docType=2&scopeId=28314)
- [查询单据详情（含追溯码）](https://open.taobao.com/api.htm?docId=65505&docType=2&scopeId=28314)
- [批量查询入出库单](https://open.taobao.com/api.htm?docId=65764&docType=2&scopeId=28314)

## License

Private project for enterprise use.
