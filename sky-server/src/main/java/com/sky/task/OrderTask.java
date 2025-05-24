package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务类，定时处理订单状态
 */
@Component
@Slf4j
public class OrderTask {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 定时处理支付超时订单
     */
    @Scheduled(cron = "0 * * * * ?") // 每分钟执行一次（0秒时执行）
    public void processTimeoutOrder() {
        log.info("定时处理待支付超时订单：{}", LocalDateTime.now());

        // 超过15分钟为超时
        LocalDateTime time = LocalDateTime.now().plusMinutes(-15);

        // 查询超时订单
        // select * from order where status = ? and order_time < (当前时间-规定超时时间)
        List<Orders> ordersList = orderMapper.getByStatusAndOderTimeLT(Orders.PENDING_PAYMENT, time);

        // 修改信息
        if (ordersList != null && !ordersList.isEmpty()) {
            for (Orders order : ordersList) {
                order.setStatus(Orders.CANCELLED);
                order.setCancelReason("支付超时，自动取消");
                order.setCancelTime(LocalDateTime.now());
                orderMapper.update(order);
            }
        }
    }

    /**
     * 定时处理一直派送中的订单
     */
    @Scheduled(cron = "0 0 1 * * ?")  // 每天凌晨一点触发
    public void processDeliveryOrder() {
        log.info("定时处理一直派送中的订单{}", LocalDateTime.now());

        LocalDateTime time = LocalDateTime.now().plusMinutes(-60);

        List<Orders> ordersList = orderMapper.getByStatusAndOderTimeLT(Orders.DELIVERY_IN_PROGRESS, time);

        // 修改信息
        if (ordersList != null && !ordersList.isEmpty()) {
            for (Orders order : ordersList) {
                order.setStatus(Orders.COMPLETED);
                orderMapper.update(order);
            }
        }
    }
}
