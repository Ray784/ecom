package com.ecommerce.search.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.search.repository.ProductIndexStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Full context MVC tests so the commons GlobalExceptionHandler auto configuration is exercised. */
@SpringBootTest
@AutoConfigureMockMvc
class SearchControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ProductIndexStore store;

  @BeforeEach
  void seed() throws Exception {
    store.deleteAll();
    index("{\"sku\":\"E1\",\"name\":\"Laptop\",\"price\":900,\"categoryId\":\"ELECTRONICS\",\"imageUrl\":\"img\"}");
    index("{\"sku\":\"B1\",\"name\":\"Book\",\"price\":20,\"categoryId\":\"BOOKS\",\"currentStock\":4}");
  }

  private void index(String json) throws Exception {
    mockMvc
        .perform(post("/search/index").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void searchProducts_defaults_returnsAllSortedByName() throws Exception {
    mockMvc
        .perform(get("/search/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.results.length()").value(2))
        .andExpect(jsonPath("$.results[0].sku").value("B1"))
        .andExpect(jsonPath("$.pagination.size").value(10));
  }

  @Test
  void searchProducts_queryAliasAndCategoryAlias_filter() throws Exception {
    mockMvc
        .perform(get("/search/products").param("queryText", "laptop").param("categoryFilters", "electronics"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.results.length()").value(1))
        .andExpect(jsonPath("$.results[0].imageUrl").value("img"));
    mockMvc
        .perform(get("/search/products").param("category", "BOOKS,ELECTRONICS").param("sortField", "price").param("sortOrder", "desc"))
        .andExpect(jsonPath("$.results[0].sku").value("E1"));
  }

  @Test
  void searchProducts_shortQuery_returnsSearch4001() throws Exception {
    mockMvc
        .perform(get("/search/products").param("q", "a"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("SEARCH_4001"));
  }

  @Test
  void searchProducts_badSortField_returnsSearch4002() throws Exception {
    mockMvc
        .perform(get("/search/products").param("sortField", "rating"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("SEARCH_4002"));
  }

  @Test
  void searchProducts_badCategory_returnsSearch4003() throws Exception {
    mockMvc
        .perform(get("/search/products").param("category", "bad!"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("SEARCH_4003"));
  }

  @Test
  void searchProducts_badPage_returnsSearch4004() throws Exception {
    mockMvc
        .perform(get("/search/products").param("size", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("SEARCH_4004"));
  }

  @Test
  void indexProduct_missingSku_returnsSearch5002() throws Exception {
    mockMvc
        .perform(post("/search/index").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.errorCode").value("SEARCH_5002"));
  }
}
