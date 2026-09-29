package org.web03.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.web03.mapper.FlashUsageMapper;
import org.web03.service.FlashUsageService;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
public class FlashUsageServiceImpl implements FlashUsageService {

    @Autowired
    private FlashUsageMapper flashUsageMapper;

    // 今日已用秒杀价的商品 id
    @Override
    public Set<Integer> usedToday(String userId) {
        if (!StringUtils.hasLength(userId)) return Set.of();
        return new HashSet<>(flashUsageMapper.usedProductIds(userId, LocalDate.now()));
    }

    // 今日某商品是否已用
    @Override
    public boolean usedToday(String userId, Integer productId) {
        if (!StringUtils.hasLength(userId) || productId == null) return false;
        return flashUsageMapper.countUsed(userId, productId, LocalDate.now()) > 0;
    }

    // 占用资格
    @Override
    public boolean claim(String userId, Integer productId) {
        LocalDate day = LocalDate.now();
        if (flashUsageMapper.insertUse(userId, productId, day) > 0) return true;
        return flashUsageMapper.reuse(userId, productId, day) > 0;
    }

    @Override
    public void bindOrder(String userId, Integer productId, Integer orderId) {
        if (orderId == null) return;
        flashUsageMapper.bindOrderId(userId, productId, LocalDate.now(), orderId);
    }

    @Override
    public void releaseByOrder(Integer orderId) {
        if (orderId == null) return;
        int rows = flashUsageMapper.releaseByOrder(orderId);
        if (rows > 0) log.info("取消订单归还秒杀资格: 订单={} 共 {} 件", orderId, rows);
    }
}
