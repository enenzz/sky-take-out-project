package com.sky.controller.admin;

import com.sky.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * 店铺管理
 */
@RestController("adminShopController")
@RequestMapping("/admin/shop")
@Slf4j
public class ShopController {

    private String shopStatus = "SHOP_STATUS";

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 设置店铺状态
     * @param status
     * @return
     */
    @PutMapping("/{status}")
    public Result setStatus(@PathVariable Integer status) {
        log.info("店铺状态设置为: {}", status == 1? "营业中": "打烊中");
        redisTemplate.opsForValue().set(shopStatus, status);
        return Result.success();
    }

    /**
     * 获取店铺状态
     * @return
     */
    @GetMapping("/status")
    public Result<Integer> getStatus() {
        Integer status = (Integer)redisTemplate.opsForValue().get(shopStatus);
        log.info("店铺状态为: {}", status == 1? "营业中": "打烊中");
        return Result.success(status);
    }
}
