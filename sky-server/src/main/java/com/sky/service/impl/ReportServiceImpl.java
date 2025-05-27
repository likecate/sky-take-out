package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;

    /**
     * 统计指定时间区间内的营业额数据
     * @param begin
     * @param end
     * @return
     */
    @Override
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        // 当前集合用于存放从begin到end范围内的每天的日期
        List<LocalDate> dateList = new ArrayList<>();

        dateList.add(begin);

        while (!begin.equals(end)) {
            // 计算指定日期的后一天对应的日期
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        List<Double> turoverList = new ArrayList<>();

        for (LocalDate date : dateList) {
            // 查询date日期对应的营业额数据（已完成的订单金额合计）
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN); // 获取当天开始时间
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX); // 获取当天结束时间

            // select sum(amount) from orders where order_time > ? and order_time < ? and status = 5
            Map map = new HashMap();
            map.put("begin", beginTime);
            map.put("end", endTime);
            map.put("status", Orders.COMPLETED);
            Double turnover = orderMapper.sumByMap(map);
            // 没有营业额会返回空
            turnover = turnover == null ? 0.0 : turnover;
            turoverList.add(turnover);
        }

        // 封装返回结果
        return TurnoverReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .turnoverList(StringUtils.join(turoverList, ","))
                .build();
    }

    /**
     * 统计指定时间区间内的用户数据
     * @param begin
     * @param end
     * @return
     */
    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();

        dateList.add(begin);

        while (!begin.equals(end)) {
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        // 存放每天总用户数量 select count(id) from user where create_time < ?
        List<Integer> totalUserList = new ArrayList<>();
        // 存放每天新增用户数量 select count(id) from user where create_time < ? and creat_time > ?
        List<Integer> newUserList = new ArrayList<>();

        Integer totalUser = 0; // 为了减少sql语句
        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);

            Map map = new HashMap();
            map.put("end", endTime);
            if (totalUserList.isEmpty()) {
                // 没有用户不会返回null
                totalUser = userMapper.countByMap(map);
            }
            map.put("begin", beginTime);
            Integer newUser = userMapper.countByMap(map);
            if (!totalUserList.isEmpty()){
                totalUser += newUser;
            }

            totalUserList.add(totalUser);
            newUserList.add(newUser);
        }


        return UserReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .totalUserList(StringUtils.join(totalUserList, ","))
                .newUserList(StringUtils.join(newUserList, ","))
                .build();
    }
}






















