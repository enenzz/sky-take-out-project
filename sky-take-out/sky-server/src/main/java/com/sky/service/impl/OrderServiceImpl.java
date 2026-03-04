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
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import org.aspectj.weaver.ast.Or;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    /**
     * 提交订单
     * @return
     */
    @Transactional
    @Override
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        //处理各种异常（地址簿，订单）为空等情况
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            //抛出业务异常
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        //通过用户id获取当前购物车
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingcart = ShoppingCart.builder()
                .userId(userId)
                .build();
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingcart);
        if (shoppingCartList == null || shoppingCartList.size() == 0) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //将订单信息插入订单表中
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        //使用当前系统时间戳作为订单号
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setUserId(userId);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        String address = addressBook.getProvinceName() + addressBook.getCityName() + addressBook.getDistrictName();
        orders.setAddress(address);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());

        orderMapper.insert(orders);

        //将购物车信息插入到订单详细表中（批量插入）
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart: shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId());
            orderDetailList.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetailList);

        //清空购物车
        shoppingCartMapper.clean(userId);

        //包装vo返回
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .orderTime(orders.getOrderTime())
                .build();

        return orderSubmitVO;
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    @Override
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        //调用微信支付接口，生成预支付交易单
        /*JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );*/
        JSONObject jsonObject = new JSONObject();

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    @Override
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
     * 历史订单分页查询
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(OrdersPageQueryDTO ordersPageQueryDTO) {
        //开启分页查询
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        Long userId = BaseContext.getCurrentId();
        ordersPageQueryDTO.setUserId(userId);
        //利用PageHelper自动分页
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        //返回的result还需要订单详细信息，返回orderVO即可，因为它基础了orders
        List<OrderVO> orderVOList = new ArrayList<>();
        if (page != null && page.size() > 0) {
            for (Orders orders: page) { //page的继承了ArrayList
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                //查询订单明细
                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
                orderVO.setOrderDetailList(orderDetailList);
                orderVOList.add(orderVO);
            }
        }
        return new PageResult(page.getTotal(), orderVOList);
    }

    /**
     * 查询订单详细信息
     * @param orderId
     * @return
     */
    @Override
    public OrderVO showOrderDetail(Long orderId) {
        //1.已知订单id，在订单表中查询订单信息
        Orders orders = orderMapper.getById(orderId);

        //2.在订单详细表中查询
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orderId);

        //3.将订单详细集合封装到list中
        OrderVO orderVO = new OrderVO();

        //4.所有数据封装到OrderVo中
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);
        return orderVO;
    }

    /**
     * 取消订单
     */
    @Override
    public void cancelOrder(Long orderId) {
//        待支付和待接单状态下，用户可直接取消订单
//        商家已接单状态下，用户取消订单需电话沟通商家
//        派送中状态下，用户取消订单需电话沟通商家
//        如果在待接单状态下取消订单，需要给用户退款
//        取消订单后需要将订单状态修改为“已取消”

        //获取订单的状态
        Orders orders = orderMapper.getById(orderId);

        //处理异常情况
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (orders.getStatus() > 2) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        orders.setStatus(Orders.CANCELLED); //订单状态修改为“已取消”
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }

    /**
     * 再来一单
     * @param orderId
     */
    @Override
    public void repetition(Long orderId) {
        //通过订单明细向购物车中插入该订单的商品
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orderId);
        ShoppingCart shoppingCart = new ShoppingCart();
        for (OrderDetail orderDetail: orderDetailList) {
            Long userId = BaseContext.getCurrentId();
            BeanUtils.copyProperties(orderDetail, shoppingCart);
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCartMapper.insert(shoppingCart);
        }
    }

    /**
     * 订单搜索
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> orderVOList = new ArrayList<>();
        if (page != null && page.size() > 0) {
            for (Orders orders: page) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                //获取菜品信息
                orderVO.setOrderDishes(getOrderDishes(orders));
                orderVOList.add(orderVO);
            }
        }
        return new  PageResult(page.getTotal(), orderVOList);
    }

    /**
     * 各个状态的订单数量统计
     * @return
     */
    @Override
    public OrderStatisticsVO statistics() {
        //1.从订单表获取所有订单
        List<Orders> ordersList = orderMapper.getAll();
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        //2.待派、派送中、待接单(遍历统计)
        for (Orders orders: ordersList) {
            Integer status = orders.getStatus();
            if (status == Orders.CONFIRMED) {
                //待派
                orderStatisticsVO.setConfirmed(orderStatisticsVO.getConfirmed() + 1);
            } else if (status == Orders.DELIVERY_IN_PROGRESS) {
                //派送中
                orderStatisticsVO.setDeliveryInProgress(orderStatisticsVO.getDeliveryInProgress() + 1);
            } else if (status == Orders.TO_BE_CONFIRMED){
                //待接单
                orderStatisticsVO.setToBeConfirmed(orderStatisticsVO.getToBeConfirmed() + 1);
            }
        }
        return orderStatisticsVO;
    }


    private String getOrderDishes(Orders orders) {
        //拼接菜品信息（菜品+口味+数量/套餐+数量）
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
        StringBuilder sb = new StringBuilder();
        for (OrderDetail orderDetail: orderDetailList) {
            sb.append(orderDetail.getName());
            if (orderDetail.getDishFlavor() != null) {
                sb.append("-").append(orderDetail.getDishFlavor());
            }
            sb.append("*").append(orderDetail.getNumber()).append(";");
        }
        return sb.toString();
    }

}
