package com.ecommerce.review;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point of the review service that stores product reviews and aggregate ratings. */
@SpringBootApplication
public class ReviewServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ReviewServiceApplication.class, args);
  }
}
