package com.ecommerce.notification.repository;

import com.ecommerce.notification.model.NotificationLog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import org.springframework.stereotype.Repository;

/**
 * Thread safe in memory document store for notification logs.
 *
 * <p>Documents keep their first insertion order, so entries with the same timestamp are still
 * returned in the order they were written.
 */
@Repository
public class NotificationLogRepository {

  private final Map<String, NotificationLog> documents = new LinkedHashMap<>();

  public synchronized NotificationLog save(NotificationLog log) {
    Objects.requireNonNull(log.getId(), "id");
    documents.put(log.getId(), log);
    return log;
  }

  /** Returns the user's logs, oldest first. */
  public List<NotificationLog> findByUserId(String userId) {
    return find(log -> Objects.equals(log.getUserId(), userId));
  }

  /** Returns the order's logs, oldest first. */
  public List<NotificationLog> findByOrderId(String orderId) {
    return find(log -> Objects.equals(log.getOrderId(), orderId));
  }

  public synchronized long count() {
    return documents.size();
  }

  private List<NotificationLog> find(Predicate<NotificationLog> filter) {
    List<NotificationLog> snapshot;
    synchronized (this) {
      snapshot = new ArrayList<>(documents.values());
    }
    return snapshot.stream()
        .filter(filter)
        .sorted(Comparator.comparing(NotificationLog::getSentDate))
        .toList();
  }
}
