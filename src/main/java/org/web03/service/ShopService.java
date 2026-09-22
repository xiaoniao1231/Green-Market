package org.web03.service;

import org.springframework.web.multipart.MultipartFile;
import org.web03.pojo.Shop.ShopInformation;
import org.web03.pojo.Shop.ShopRequest;

import java.util.Map;

public interface ShopService {
    ShopInformation createShop(ShopRequest shopRequest);

    Map<String, Object> uploadAvatar(MultipartFile file);

    ShopInformation getMyShop();

    ShopInformation updateShop(ShopRequest shopRequest);

    ShopInformation getPublicShop(String shopId);
}
