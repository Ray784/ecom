package com.ecommerce.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.common.error.ErrorMapper;
import com.ecommerce.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class CommonsAutoConfigurationTest {

  private final WebApplicationContextRunner webRunner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CommonsAutoConfiguration.class));

  @Test
  void registersErrorMapperAndExceptionHandlerInWebApplication() {
    webRunner.run(
        context -> {
          assertThat(context).hasSingleBean(ErrorMapper.class);
          assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
        });
  }

  @Test
  void backsOffWhenServiceDefinesItsOwnBeans() {
    ErrorMapper custom = new ErrorMapper("custom");
    GlobalExceptionHandler customHandler = new GlobalExceptionHandler();
    webRunner
        .withBean(ErrorMapper.class, () -> custom)
        .withBean(GlobalExceptionHandler.class, () -> customHandler)
        .run(
            context -> {
              assertThat(context).getBean(ErrorMapper.class).isSameAs(custom);
              assertThat(context).getBean(GlobalExceptionHandler.class).isSameAs(customHandler);
            });
  }

  @Test
  void skipsExceptionHandlerOutsideWebApplication() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CommonsAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context).hasSingleBean(ErrorMapper.class);
              assertThat(context).doesNotHaveBean(GlobalExceptionHandler.class);
            });
  }
}
