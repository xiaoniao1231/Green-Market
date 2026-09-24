package org.web03.service;

import org.web03.pojo.Order.OrderCreateRequest;
import org.web03.pojo.Order.OrderCreateResult;
import org.web03.pojo.Order.OrderVO;

import java.util.Map;

public interface OrderService {

    OrderCreateResult create(OrderCreateRequest request);

    void pay(Integer orderId);

    int payBatch(String payNo);

    void cancel(Integer orderId);

    Map<String, Object> list(String status, Integer page, Integer size);

    Map<String, Object> counts();

    Map<String, Object> sellerList(String status, Integer page, Integer size);

    void ship(Integer orderId);

    OrderVO get(Integer id);

    void confirm(Integer id);

    Map<String, Object> remind(Integer id);

    Map<String, Object> logistics(Integer id);
}
