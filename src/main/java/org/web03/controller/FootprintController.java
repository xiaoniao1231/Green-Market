package org.web03.controller;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.web03.pojo.Footprint.Footprint;
import org.web03.pojo.Result;
import org.web03.service.FootprintService;

/**
 * 浏览足迹控制器
 */

@Slf4j
@RestController
@RequestMapping("/footprints")
public class FootprintController {
    @Autowired
    private FootprintService footprintService;

    //我的足迹列表
    @GetMapping
    public Result list(@RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "100") Integer size) {
        return Result.success(footprintService.list( page, size));
    }

    //记录足迹
    @PostMapping
    public Result record(@RequestBody Footprint request) {
        footprintService.record(request);
        return Result.success();
    }

}
