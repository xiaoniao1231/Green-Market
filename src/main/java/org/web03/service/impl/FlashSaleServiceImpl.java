package org.web03.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web03.mapper.ProductMapper;
import org.web03.service.FlashSaleService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
@Service
public class FlashSaleServiceImpl implements FlashSaleService {

    // 每日秒杀件数、候选池（上架较早的若干件）、折扣（到手价 5 折）
    private static final int FLASH_SIZE = 8;
    private static final int FLASH_POOL = 16;
    private static final BigDecimal FLASH_RATE = new BigDecimal("0.5");

    @Autowired
    private ProductMapper productMapper;

    // 当日缓存，跨天自动重算
    private volatile String cacheDay = "";
    private volatile Set<Integer> cacheIds = Set.of();

    // 当日秒杀商品 id
    @Override
    public Set<Integer> todayIds() {
        String day = LocalDate.now().toString();
        if (!day.equals(cacheDay)) {
            synchronized (this) {
                if (!day.equals(cacheDay)) {
                    // LinkedHashSet 保留选品顺序，秒杀列表与秒杀价同源
                    cacheIds = new LinkedHashSet<>(productMapper.flashIds(FLASH_POOL, FLASH_SIZE));
                    cacheDay = day;
                    log.info("当日秒杀商品: {}", cacheIds);
                }
            }
        }
        return cacheIds;
    }

    // 秒杀价：到手价 5 折；非当日秒杀商品返回 null
    @Override
    public BigDecimal flashPrice(Integer productId, BigDecimal price) {
        if (productId == null || price == null) return null;
        if (!todayIds().contains(productId)) return null;
        return price.multiply(FLASH_RATE).setScale(2, RoundingMode.HALF_UP);
    }
}
