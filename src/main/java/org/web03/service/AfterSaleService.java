package org.web03.service;

import org.springframework.web.multipart.MultipartFile;
import org.web03.pojo.AfterSale.AfterSale;
import org.web03.pojo.AfterSale.AfterSaleVO;

import java.util.List;
import java.util.Map;

public interface AfterSaleService {

    List<AfterSaleVO> listAll();

    AfterSaleVO get(Integer id);

    AfterSaleVO create(AfterSaleVO request);

    String uploadImage(MultipartFile file);

    void cancel(Integer id);

    void ship(Integer id, AfterSale request);

    Map<String, Object> sellerList(String status, String afterNo, Integer page, Integer size);

    void approve(Integer id, AfterSale request);

    void refuse(Integer id, AfterSale request);

    void receive(Integer id, AfterSale request);
}
