package com.payverse.paymentapi.threeds.infrastructure.cybersource;

import Api.PayerAuthenticationApi;
import Invokers.ApiClient;
import com.cybersource.authsdk.core.ConfigException;
import com.cybersource.authsdk.core.MerchantConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CybersourceProperties.class)
public class CybersourceConfiguration {

    @Bean
    PayerAuthenticationApi payerAuthenticationApi(CybersourceProperties properties) {
        Properties sdkProperties = new Properties();
        sdkProperties.setProperty("authenticationType", "http_signature");
        sdkProperties.setProperty("merchantID", properties.merchantId());
        sdkProperties.setProperty("merchantKeyId", properties.merchantKeyId());
        sdkProperties.setProperty("merchantsecretKey", properties.merchantSecretKey());
        sdkProperties.setProperty("runEnvironment", properties.runEnvironment());
        sdkProperties.setProperty("enableLog", "false");

        try {
            return new PayerAuthenticationApi(new ApiClient(new MerchantConfig(sdkProperties)));
        } catch (ConfigException exception) {
            throw new IllegalStateException("Unable to configure Cybersource client", exception);
        }
    }
}
