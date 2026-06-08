package com.ecommerce.order.service;

import com.ecommerce.common.dto.CartDTO;
import com.ecommerce.common.dto.CartItemDTO;
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.order.client.ProductCatalogClient;
import com.ecommerce.order.client.ProductCatalogClient.ProductSnapshot;
import com.ecommerce.order.model.Cart;
import com.ecommerce.order.model.CartItem;
import com.ecommerce.order.repository.CartRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cart operations: adding priced items, reading the active cart and clearing it.
 *
 * <p>A cart whose lastUpdated timestamp is older than the configured TTL is treated as expired
 * and removed on next access.
 */
@Service
public class CartService {

  private final CartRepository cartRepository;
  private final ProductCatalogClient productCatalogClient;
  private final int maxItems;
  private final Duration cartTtl;
  private final Clock clock;

  @Autowired
  public CartService(
      CartRepository cartRepository,
      ProductCatalogClient productCatalogClient,
      @Value("${order.cart.max-items:50}") int maxItems,
      @Value("${order.cart.ttl-minutes:1440}") long cartTtlMinutes) {
    this(cartRepository, productCatalogClient, maxItems, Duration.ofMinutes(cartTtlMinutes),
        Clock.systemUTC());
  }

  /** Full constructor that allows tests to control the clock. */
  public CartService(
      CartRepository cartRepository,
      ProductCatalogClient productCatalogClient,
      int maxItems,
      Duration cartTtl,
      Clock clock) {
    this.cartRepository = cartRepository;
    this.productCatalogClient = productCatalogClient;
    this.maxItems = maxItems;
    this.cartTtl = cartTtl;
    this.clock = clock;
  }

  /**
   * Adds an item to the user's cart, merging with an existing line for the same SKU.
   *
   * <p>When the request carries no unit price the price is looked up from product-service, and
   * the requested quantity is checked against the reported stock.
   */
  @Transactional
  public CartDTO addToCart(String userId, CartItemDTO request) {
    String sku = request == null ? null : request.getSku();
    Integer quantity = request == null ? null : request.getQuantity();
    if (sku == null || sku.isBlank() || quantity == null || quantity <= 0) {
      throw CodedErrorFactory.create(MappedError.ORDER_4006, sku, String.valueOf(quantity));
    }
    BigDecimal unitPrice = request.getUnitPrice();
    if (unitPrice != null && unitPrice.signum() < 0) {
      throw CodedErrorFactory.create(MappedError.ORDER_4006, sku, String.valueOf(quantity));
    }

    Cart cart = findActiveCart(userId).orElseGet(() -> new Cart(userId));
    Optional<CartItem> existing = cart.findItem(sku);
    int newLineQuantity = quantity + existing.map(CartItem::getQuantity).orElse(0);

    if (cart.totalQuantity() + quantity > maxItems) {
      throw CodedErrorFactory.create(MappedError.ORDER_4009, String.valueOf(maxItems));
    }
    if (unitPrice == null) {
      ProductSnapshot product = productCatalogClient.fetchProduct(sku);
      if (product.currentStock() != null && product.currentStock() < newLineQuantity) {
        throw CodedErrorFactory.create(MappedError.ORDER_4004, sku);
      }
      unitPrice = product.price();
    }
    unitPrice = unitPrice.setScale(2, RoundingMode.HALF_UP);

    if (existing.isPresent()) {
      existing.get().setQuantity(newLineQuantity);
      existing.get().setUnitPrice(unitPrice);
    } else {
      cart.addItem(new CartItem(sku, quantity, unitPrice));
    }
    cart.setLastUpdated(clock.instant());
    return toDto(cartRepository.save(cart));
  }

  /** Returns the user's cart or throws ORDER_4005 when it is missing, empty or expired. */
  @Transactional
  public CartDTO getCart(String userId) {
    Cart cart =
        findActiveCart(userId)
            .filter(found -> !found.isEmpty())
            .orElseThrow(() -> CodedErrorFactory.create(MappedError.ORDER_4005, userId));
    return toDto(cart);
  }

  /** Returns the user's cart when present and not expired. Expired carts are deleted. */
  @Transactional
  public Optional<Cart> findActiveCart(String userId) {
    Optional<Cart> cart = cartRepository.findById(userId);
    if (cart.isPresent() && isExpired(cart.get())) {
      cartRepository.delete(cart.get());
      return Optional.empty();
    }
    return cart;
  }

  /** Removes the user's cart and all of its items. Missing carts are ignored. */
  @Transactional
  public void clearCart(String userId) {
    cartRepository.findById(userId).ifPresent(cartRepository::delete);
  }

  private boolean isExpired(Cart cart) {
    Instant lastUpdated = cart.getLastUpdated();
    return lastUpdated != null && lastUpdated.plus(cartTtl).isBefore(clock.instant());
  }

  /** Maps a cart entity to its external representation. */
  public static CartDTO toDto(Cart cart) {
    List<CartItemDTO> items =
        cart.getItems().stream()
            .map(item -> new CartItemDTO(item.getSku(), item.getQuantity(), item.getUnitPrice()))
            .toList();
    return new CartDTO(cart.getUserId(), items, cart.subtotal().setScale(2, RoundingMode.HALF_UP));
  }
}
