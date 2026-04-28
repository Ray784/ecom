package com.ecommerce.user.repository;

import com.ecommerce.user.model.UserProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, String> {

  Optional<UserProfile> findByEmailIgnoreCase(String email);
}
