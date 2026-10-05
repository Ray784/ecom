package com.ecommerce.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.common.dto.CartDTO;
import com.ecommerce.common.dto.CartItemDTO;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.order.client.ProductCatalogClient;
import com.ecommerce.order.client.ProductCatalogClient.ProductSnapshot;
import com.ecommerce.order.model.Cart;
import com.ecommerce.order.model.CartItem;
import com.ecommerce.order.repository.CartRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-10T10:00:00Z");
  private static final String USER = "user-1";

  @Mock private CartRepository cartRepository;
  @Mock private ProductCatalogClient productCatalogClient;

  private CartService cartService;

  @BeforeEach
  void setUp() {
    cartService =
        new CartService(
            cartRepository, productCatalogClient, 5, Duration.ofMinutes(60),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private void stubSaveReturnsArgument() {
    when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  private static Cart cartWith(String sku, int quantity, String price, Instant lastUpdated) {
    Cart cart = new Cart(USER);
    cart.addItem(new CartItem(sku, quantity, new BigDecimal(price)));
    cart.setLastUpdated(lastUpdated);
    return cart;
  }

  private static void assertError(Runnable call, MappedError expected) {
    assertThatThrownBy(call::run)
        .isInstanceOf(ApplicationBaseException.class)
        .extracting(e -> ((ApplicationBaseException) e).getMappedError())
        .isEqualTo(expected);
  }

  @Test
  void addToCartWithExplicitPriceCreatesCartWithoutProductLookup() {
    when(cartRepository.findById(USER)).thenReturn(Optional.empty());
    stubSaveReturnsArgument();

    CartDTO cart = cartService.addToCart(USER, new CartItemDTO("SKU-1", 2, new BigDecimal("9.5")));

    assertThat(cart.getUserId()).isEqualTo(USER);
    assertThat(cart.getItems()).hasSize(1);
    assertThat(cart.getItems().get(0).getUnitPrice()).isEqualByComparingTo("9.50");
    assertThat(cart.getSubtotal()).isEqualByComparingTo("19.00");
    verify(productCatalogClient, never()).fetchProduct(anyString());
  }

  @Test
  void addToCartWithoutPriceUsesProductServicePrice() {
    when(cartRepository.findById(USER)).thenReturn(Optional.empty());
    when(productCatalogClient.fetchProduct("SKU-1"))
        .thenReturn(new ProductSnapshot("SKU-1", new BigDecimal("12.34"), 10));
    stubSaveReturnsArgument();

    CartDTO cart = cartService.addToCart(USER, new CartItemDTO("SKU-1", 1, null));

    assertThat(cart.getItems().get(0).getUnitPrice()).isEqualByComparingTo("12.34");
  }

  @Test
  void addToCartWithoutPriceAndUnknownStockSkipsStockCheck() {
    when(cartRepository.findById(USER)).thenReturn(Optional.empty());
    when(productCatalogClient.fetchProduct("SKU-1"))
        .thenReturn(new ProductSnapshot("SKU-1", BigDecimal.ONE, null));
    stubSaveReturnsArgument();

    assertThat(cartService.addToCart(USER, new CartItemDTO("SKU-1", 3, null)).getItems())
        .hasSize(1);
  }

  @Test
  void addToCartRejectsQuantityAboveReportedStock() {
    when(cartRepository.findById(USER))
        .thenReturn(Optional.of(cartWith("SKU-1", 2, "1.00", NOW)));
    when(productCatalogClient.fetchProduct("SKU-1"))
        .thenReturn(new ProductSnapshot("SKU-1", BigDecimal.ONE, 2));

    assertError(() -> cartService.addToCart(USER, new CartItemDTO("SKU-1", 1, null)),
        MappedError.ORDER_4004);
    verify(cartRepository, never()).save(any());
  }

  @Test
  void addToCartMergesSameSkuAndRefreshesPrice() {
    when(cartRepository.findById(USER))
        .thenReturn(Optional.of(cartWith("SKU-1", 2, "1.00", NOW)));
    stubSaveReturnsArgument();

    CartDTO cart = cartService.addToCart(USER, new CartItemDTO("SKU-1", 1, new BigDecimal("2")));

    assertThat(cart.getItems()).hasSize(1);
    assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(3);
    assertThat(cart.getSubtotal()).isEqualByComparingTo("6.00");
  }

  @Test
  void addToCartRejectsNonPositiveQuantity() {
    assertError(() -> cartService.addToCart(USER, new CartItemDTO("SKU-1", 0, BigDecimal.ONE)),
        MappedError.ORDER_4006);
    assertError(() -> cartService.addToCart(USER, new CartItemDTO("SKU-1", null, BigDecimal.ONE)),
        MappedError.ORDER_4006);
  }

  @Test
  void addToCartRejectsMissingSkuAndNegativePrice() {
    assertError(() -> cartService.addToCart(USER, new CartItemDTO(" ", 1, BigDecimal.ONE)),
        MappedError.ORDER_4006);
    assertError(() -> cartService.addToCart(USER, null), MappedError.ORDER_4006);
    assertError(
        () -> cartService.addToCart(USER, new CartItemDTO("SKU-1", 1, new BigDecimal("-1"))),
        MappedError.ORDER_4006);
  }

  @Test
  void addToCartRejectsExceedingMaxItems() {
    when(cartRepository.findById(USER))
        .thenReturn(Optional.of(cartWith("SKU-1", 4, "1.00", NOW)));

    assertError(() -> cartService.addToCart(USER, new CartItemDTO("SKU-2", 2, BigDecimal.ONE)),
        MappedError.ORDER_4009);
  }

  @Test
  void addToCartAllowsReachingMaxItemsExactly() {
    when(cartRepository.findById(USER))
        .thenReturn(Optional.of(cartWith("SKU-1", 4, "1.00", NOW)));
    stubSaveReturnsArgument();

    assertThat(cartService.addToCart(USER, new CartItemDTO("SKU-2", 1, BigDecimal.ONE)).getItems())
        .hasSize(2);
  }

  @Test
  void addToCartStartsFreshCartWhenExistingOneExpired() {
    Cart expired = cartWith("OLD", 4, "1.00", NOW.minus(Duration.ofHours(2)));
    when(cartRepository.findById(USER)).thenReturn(Optional.of(expired));
    stubSaveReturnsArgument();

    CartDTO cart = cartService.addToCart(USER, new CartItemDTO("SKU-2", 2, BigDecimal.ONE));

    verify(cartRepository).delete(expired);
    assertThat(cart.getItems()).extracting(CartItemDTO::getSku).containsExactly("SKU-2");
  }

  @Test
  void getCartReturnsActiveCart() {
    when(cartRepository.findById(USER))
        .thenReturn(Optional.of(cartWith("SKU-1", 2, "3.00", NOW)));

    CartDTO cart = cartService.getCart(USER);

    assertThat(cart.getSubtotal()).isEqualByComparingTo("6.00");
  }

  @Test
  void getCartThrowsWhenMissing() {
    when(cartRepository.findById(USER)).thenReturn(Optional.empty());

    assertError(() -> cartService.getCart(USER), MappedError.ORDER_4005);
  }

  @Test
  void getCartThrowsWhenEmpty() {
    when(cartRepository.findById(USER)).thenReturn(Optional.of(new Cart(USER)));

    assertError(() -> cartService.getCart(USER), MappedError.ORDER_4005);
  }

  @Test
  void getCartThrowsAndDeletesWhenExpired() {
    Cart expired = cartWith("SKU-1", 1, "1.00", NOW.minus(Duration.ofMinutes(61)));
    when(cartRepository.findById(USER)).thenReturn(Optional.of(expired));

    assertError(() -> cartService.getCart(USER), MappedError.ORDER_4005);
    verify(cartRepository).delete(expired);
  }

  @Test
  void clearCartDeletesExistingCartAndIgnoresMissingOne() {
    Cart cart = cartWith("SKU-1", 1, "1.00", NOW);
    when(cartRepository.findById(USER)).thenReturn(Optional.of(cart));
    when(cartRepository.findById("other")).thenReturn(Optional.empty());

    cartService.clearCart(USER);
    cartService.clearCart("other");

    verify(cartRepository).delete(cart);
  }
}
