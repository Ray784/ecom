package com.ecommerce.user.repository;

import com.ecommerce.user.model.Address;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, Long> {

  List<Address> findByUserIdOrderByIdAsc(String userId);

  long countByUserId(String userId);
}
