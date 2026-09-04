package com.ecommerce.gateway.config;

import com.ecommerce.common.util.JwtUtil;
import com.ecommerce.gateway.routing.ServiceId;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings bound from the gateway prefix: downstream base URLs, timeouts and auth options. */
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

  private Duration connectTimeout = Duration.ofSeconds(2);
  private Duration readTimeout = Duration.ofSeconds(30);
  private Map<String, String> services = new HashMap<>();
  private final Auth auth = new Auth();

  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(Duration connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public Duration getReadTimeout() {
    return readTimeout;
  }

  public void setReadTimeout(Duration readTimeout) {
    this.readTimeout = readTimeout;
  }

  public Map<String, String> getServices() {
    return services;
  }

  public void setServices(Map<String, String> services) {
    this.services = services;
  }

  public Auth getAuth() {
    return auth;
  }

  /**
   * Returns the configured base URL of a service without a trailing slash.
   *
   * @throws IllegalStateException if the service has no configured URL
   */
  public String baseUrl(ServiceId service) {
    String url = services.get(service.key());
    if (url == null || url.isBlank()) {
      throw new IllegalStateException("No URL configured for gateway.services." + service.key());
    }
    String trimmed = url.trim();
    return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
  }

  /** Token validation options. */
  public static class Auth {

    private boolean localFallbackEnabled = true;
    private String jwtSecret = JwtUtil.DEFAULT_SECRET;

    /** When true, tokens are verified locally with JwtUtil if the IAM service cannot answer. */
    public boolean isLocalFallbackEnabled() {
      return localFallbackEnabled;
    }

    public void setLocalFallbackEnabled(boolean localFallbackEnabled) {
      this.localFallbackEnabled = localFallbackEnabled;
    }

    public String getJwtSecret() {
      return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
      this.jwtSecret = jwtSecret;
    }
  }
}
