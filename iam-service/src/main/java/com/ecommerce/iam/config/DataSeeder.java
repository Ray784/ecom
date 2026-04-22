package com.ecommerce.iam.config;

import com.ecommerce.iam.model.Role;
import com.ecommerce.iam.model.User;
import com.ecommerce.iam.repository.RoleRepository;
import com.ecommerce.iam.repository.UserRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Seeds the known roles and a default administrator account on startup. */
@Component
public class DataSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

  private final RoleRepository roleRepository;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final boolean seedAdmin;
  private final String adminUsername;
  private final String adminPassword;
  private final String adminEmail;

  public DataSeeder(
      RoleRepository roleRepository,
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      @Value("${iam.seed.admin.enabled:true}") boolean seedAdmin,
      @Value("${iam.seed.admin.username:admin}") String adminUsername,
      @Value("${iam.seed.admin.password:Admin@1234}") String adminPassword,
      @Value("${iam.seed.admin.email:admin@ecommerce.local}") String adminEmail) {
    this.roleRepository = roleRepository;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.seedAdmin = seedAdmin;
    this.adminUsername = adminUsername;
    this.adminPassword = adminPassword;
    this.adminEmail = adminEmail;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    for (String name : List.of(Role.CUSTOMER, Role.ADMIN, Role.SERVICE)) {
      if (roleRepository.findByName(name).isEmpty()) {
        roleRepository.save(new Role(name));
      }
    }
    if (seedAdmin && !userRepository.existsByUsername(adminUsername)) {
      User admin =
          new User(
              UUID.randomUUID().toString(),
              adminUsername,
              adminEmail,
              "Admin",
              passwordEncoder.encode(adminPassword));
      admin.setRoles(
          Set.of(
              roleRepository.findByName(Role.ADMIN).orElseThrow(),
              roleRepository.findByName(Role.CUSTOMER).orElseThrow()));
      userRepository.save(admin);
      log.info("Seeded administrator account {}", adminUsername);
    }
  }
}
