package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {

        // 1. 处理各种业务异常（地址簿为空，购物车数据为空）, 虽然小程序也进行了判断, 但是为了代码的健壮性, 建议判断
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            // 抛出业务异常
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        // 获得当前用户id，及购物车数据
        Long userId = BaseContext.getCurrentId();

        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        if (shoppingCartList == null || shoppingCartList.isEmpty()) {
            // 抛出业务异常
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        // 2. 向订单表插入1条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setUserId(userId);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setAddress(addressBook.getDetail());

        orderMapper.insert(orders);

        // 3. 向订单明细表插入n条数据, 选择批量插入
        List<OrderDetail> orderDetails = new ArrayList<>();

        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail(); // 订单明细
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId()); // 通过返回的订单id设置订单明细关联的订单id
            orderDetails.add(orderDetail);
        }

        // 批量插入
        orderDetailMapper.insertBatch(orderDetails);

        // 4. 清空当前用户的购物车数据
        shoppingCartMapper.deleteByUserId(userId);

        // 5. 封装VO返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();

        return orderSubmitVO;
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        /*Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        //调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
        */

        // 模拟微信支付
        // 直接成功

        // 因为前端注释掉了wx.requestPayment, 不会回调PayNotifyController

        // 所以直接修改订单状态
        paySuccess(ordersPaymentDTO.getOrderNumber());

        return null;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
    }

    /**
     * C端订单分页查询
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQueryAboutUser(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        // 分页条件查询订单
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        // 构造OrderVO集合
        List<OrderVO> list = new ArrayList<>();

        if (page != null && page.getTotal() > 0) {
            for (Orders orders : page) {
                // 构造OrderVO
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);

                // 查询订单对应的订单详细数据
                List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orderVO.getId());

                // 将查询到的订单详细数据添加到orderVO
                orderVO.setOrderDetailList(orderDetails);

                // 添加OrderVO
                list.add(orderVO);
            }
        }

        return new PageResult(page.getTotal(), list);
    }

    /**
     * 查询订单详情
     * @param id
     * @return
     */
    @Override
    public OrderVO details(Long id) {
        // 根据id查询订单数据
        Orders orders = orderMapper.getById(id);

        // 查询订单对应的订单详细数据
        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(id);

        // 构造orderVO
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetails);

        return orderVO;
    }

    /**
     * C端取消订单
     * @param id
     */
    @Override
    public void userCancel(Long id) {
        // 查询订单数据
        Orders orders = orderMapper.getById(id);

        // 校验订单是否存在
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 获取订单状态
        Integer status = orders.getStatus();

        //订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        if (status > Orders.TO_BE_CONFIRMED) {
            // 如果是其他状态,抛异常
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);

        } else if (status.equals(Orders.TO_BE_CONFIRMED)) {
            // 如果是待接单,退钱

            /*//调用微信支付退款接口
            weChatPayUtil.refund(
                    ordersDB.getNumber(), //商户订单号
                    ordersDB.getNumber(), //商户退款单号
                    new BigDecimal(0.01),//退款金额，单位 元
                    new BigDecimal(0.01));//原订单金额*/

            // 跳过退款, 默认退款成功

            //支付状态修改为 退款
            orders.setPayStatus(Orders.REFUND);
        }
        // 如果是待支付就只修改状态

        // 更新订单状态、取消原因、取消时间
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());

        orderMapper.update(orders);
    }

    /**
     * 再来一单
     * @param id
     */
    @Override
    public void repetition(Long id) {
        // 获取当前用户id
        Long userId = BaseContext.getCurrentId();

        // 查看订单关联的订单明细
        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(id);

        // 构建购物车数据集合
        List<ShoppingCart> shoppingCarts = new ArrayList<>();
        for (OrderDetail orderDetail : orderDetails) {
            ShoppingCart shoppingCart = new ShoppingCart();

            BeanUtils.copyProperties(orderDetail, shoppingCart);
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());

            shoppingCarts.add(shoppingCart);
        }

        // 将订单数据批量添加到购物车表中
        shoppingCartMapper.insertBatch(shoppingCarts);
    }
}
