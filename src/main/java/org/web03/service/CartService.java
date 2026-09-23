package org.web03.service;

import org.web03.pojo.CartItem.*;

import java.util.List;

public interface CartService {
    List<CartItemVO> list();

    void add(CartAddRequest cartAddRequest);

    void updateQty(CartUpdateRequest cartUpdateRequest);

    void delete(CartDeleteRequest cartDeleteRequest);

    void clear();

    void updateSku(CartSkuUpdateRequest cartSkuUpdateRequest);
}
