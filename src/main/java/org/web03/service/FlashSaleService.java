package org.web03.service;

import java.math.BigDecimal;
import java.util.Set;


public interface FlashSaleService {

    Set<Integer> todayIds();

    BigDecimal flashPrice(Integer productId, BigDecimal price);
}
