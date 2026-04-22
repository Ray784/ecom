package com.ecommerce.iam.repository;

import com.ecommerce.iam.model.JWTToken;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JWTTokenRepository extends JpaRepository<JWTToken, String> {

  List<JWTToken> findByUserIdAndBlacklistedFalse(String userId);
}
