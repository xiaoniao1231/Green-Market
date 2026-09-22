package org.web03.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.web03.pojo.Address;
import org.web03.pojo.AddressRequest;
import org.web03.pojo.Result;
import org.web03.service.AddressService;

/**
 * 地址控制器
 */

@Slf4j
@RestController
@RequestMapping("/addresses")
public class AddressController {
    @Autowired
    private AddressService addressService;

    //获取地址列表
    @GetMapping
    public Result list(){
        return Result.success(addressService.list());
    }

    //添加地址
    @PostMapping
    public Result create(@RequestBody AddressRequest addressRequest){
        return Result.success(addressService.create(addressRequest));
    }

    //更新地址
    @PutMapping("/{id}")
    public Result update(@PathVariable Integer id, @RequestBody AddressRequest addressRequest){
        return Result.success(addressService.update(id, addressRequest));
    }

    //删除地址
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id){
        addressService.delete(id);
        return Result.success();
    }

    //设置默认地址
    @PutMapping("/{id}/default")
    public Result setDefault(@PathVariable Integer id){
        addressService.setDefault(id);
        return Result.success();
    }

}
