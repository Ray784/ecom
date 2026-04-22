package com.ecommerce.iam.repository;

import com.ecommerce.iam.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByUsername(String username);

  Optional<User> findByUserId(String userId);

  boolean existsByUsername(String username);
}
