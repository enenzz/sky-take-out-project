package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class OrderTask {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 处理超时订单，每一分钟触发依次
     */
    @Scheduled(cron = "0 * * * * ?")
    public void processTimeoutOrder() {
        log.info("定时处理超时订单: {}", LocalDateTime.now());

        //1.获取订单（状态为待付款，时间超过15分钟）
        LocalDateTime time = LocalDateTime.now().plusMinutes(-15); //判断小于当前时间-15分钟
        List<Orders> ordersList = orderMapper.getByStatusAndTime(Orders.PENDING_PAYMENT, time);

        //2.修改订单状态，添加取消时间和原因
        if (ordersList != null && ordersList.size() > 0) {
            for (Orders orders: ordersList) {
                orders.setStatus(Orders.CANCELLED);
                orders.setCancelReason("订单支付超时");
                orders.setCancelTime(LocalDateTime.now());
                orderMapper.update(orders);
            }
        }
    }

    /**
     * 处理派送的订单，每天凌晨一点触发
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void processDeliverOrder() {
        log.info("定时处理派送的订单: {}", LocalDateTime.now());

        //1.获取订单（状态为派送，时间超过15分钟）
        LocalDateTime time = LocalDateTime.now().plusMinutes(-60); //判断小于今天
        List<Orders> ordersList = orderMapper.getByStatusAndTime(Orders.DELIVERY_IN_PROGRESS, time);

        //2.修改订单状态为完成
        if (ordersList != null && ordersList.size() > 0) {
            for (Orders orders: ordersList) {
                orders.setStatus(Orders.COMPLETED);
                orderMapper.update(orders);
            }
        }
    }
}
