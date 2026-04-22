package com.ecommerce.iam.config;

import com.ecommerce.common.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Token signing and password hashing beans. */
@Configuration(proxyBeanMethods = false)
public class SecurityBeansConfig {

  @Bean
  public JwtUtil jwtUtil(
      @Value("${jwt.secret:" + JwtUtil.DEFAULT_SECRET + "}") String secret,
      @Value("${jwt.expiration-ms:" + JwtUtil.DEFAULT_EXPIRATION_MILLIS + "}") long expirationMillis) {
    return new JwtUtil(secret, expirationMillis);
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
