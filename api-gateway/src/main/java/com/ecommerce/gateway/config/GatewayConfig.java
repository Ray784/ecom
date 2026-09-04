package com.ecommerce.gateway.config;

import com.ecommerce.common.util.JwtUtil;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Infrastructure beans shared by the proxy and the IAM client. */
@Configuration
public class GatewayConfig {

  /** HTTP/1.1 client so downstream calls never attempt an h2c upgrade. */
  @Bean
  public HttpClient gatewayHttpClient(GatewayProperties properties) {
    return HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(properties.getConnectTimeout())
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
  }

  @Bean
  public JwtUtil jwtUtil(GatewayProperties properties) {
    return new JwtUtil(properties.getAuth().getJwtSecret(), JwtUtil.DEFAULT_EXPIRATION_MILLIS);
  }
}
