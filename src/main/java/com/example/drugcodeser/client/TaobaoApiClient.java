
package com.example.drugcodeser.client;

import com.example.drugcodeser.config.TaobaoApiConfig;
import com.taobao.api.DefaultTaobaoClient;
import com.taobao.api.TaobaoClient;
import com.taobao.api.TaobaoRequest;
import com.taobao.api.TaobaoResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TaobaoApiClient {

    private final TaobaoClient taobaoClient;

    public TaobaoApiClient(TaobaoApiConfig config) {
        this.taobaoClient = new DefaultTaobaoClient(
                config.getServerUrl(),
                config.getAppKey(),
                config.getAppSecret(),
                config.getFormat(),
                config.getConnectTimeout(),
                config.getReadTimeout(),
                config.getSignMethod()
        );
        log.info("TaobaoClient initialized with serverUrl: {}, appKey: {}", 
                config.getServerUrl(), config.getAppKey());
    }

    public <T extends TaobaoResponse> T execute(TaobaoRequest<T> request) {
        try {
            log.info("Executing API: {}", request.getApiMethodName());
            return taobaoClient.execute(request);
        } catch (Exception e) {
            log.error("API调用失败: {}", e.getMessage(), e);
            throw new RuntimeException("API调用失败: " + e.getMessage(), e);
        }
    }

    public <T extends TaobaoResponse> T execute(TaobaoRequest<T> request, String session) {
        try {
            log.info("Executing API: {} with session", request.getApiMethodName());
            return taobaoClient.execute(request, session);
        } catch (Exception e) {
            log.error("API调用失败: {}", e.getMessage(), e);
            throw new RuntimeException("API调用失败: " + e.getMessage(), e);
        }
    }
}
