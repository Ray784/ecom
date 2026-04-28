package com.ecommerce.user.client;

import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Looks up accounts on iam-service so a profile can be auto initialized on first access.
 *
 * <p>Disabled lookups and IAM 4xx answers both mean the account is unknown. Connection failures
 * and IAM 5xx answers are reported as USER_5002.
 */
@Component
public class IamClient {

  private final RestClient restClient;
  private final boolean enabled;

  @Autowired
  public IamClient(
      RestClient.Builder builder,
      @Value("${iam.client.base-url:http://localhost:8081}") String baseUrl,
      @Value("${iam.client.enabled:true}") boolean enabled,
      @Value("${iam.client.timeout-ms:3000}") long timeoutMillis) {
    this(builder.requestFactory(requestFactory(timeoutMillis)).baseUrl(baseUrl).build(), enabled);
  }

  IamClient(RestClient restClient, boolean enabled) {
    this.restClient = restClient;
    this.enabled = enabled;
  }

  public Optional<IamUserAccount> fetchAccount(String userId) {
    if (!enabled) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(
          restClient.get().uri("/users/{userId}", userId).retrieve().body(IamUserAccount.class));
    } catch (HttpClientErrorException e) {
      return Optional.empty();
    } catch (RestClientException e) {
      throw CodedErrorFactory.create(MappedError.USER_5002, e);
    }
  }

  private static SimpleClientHttpRequestFactory requestFactory(long timeoutMillis) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(timeoutMillis));
    factory.setReadTimeout(Duration.ofMillis(timeoutMillis));
    return factory;
  }
}
