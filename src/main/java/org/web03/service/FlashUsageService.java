package org.web03.service;

import java.util.Set;


public interface FlashUsageService {

    Set<Integer> usedToday(String userId);

    boolean usedToday(String userId, Integer productId);

    boolean claim(String userId, Integer productId);

    void bindOrder(String userId, Integer productId, Integer orderId);

    void releaseByOrder(Integer orderId);
}
