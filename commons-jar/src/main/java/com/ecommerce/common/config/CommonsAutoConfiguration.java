package com.ecommerce.common.config;

import com.ecommerce.common.error.ErrorMapper;
import com.ecommerce.common.exception.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Registers the shared error handling beans in every service that depends on commons-jar.
 *
 * <p>Each bean backs off when the service already defines its own, for example through
 * component scanning of the com.ecommerce package.
 */
@AutoConfiguration
public class CommonsAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ErrorMapper errorMapper() {
    return new ErrorMapper();
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnWebApplication
  public GlobalExceptionHandler globalExceptionHandler() {
    return new GlobalExceptionHandler();
  }
}
