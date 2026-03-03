package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShoppingCartServiceImpl implements ShoppingCartService {

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 添加商品到购物车
     * @param shoppingCartDTO
     */
    @Override
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        //判断购物车中是否有该商品
        //从数据库中根据userid，dishid，setmealid，flavor来唯一确定一个菜品或套餐
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);

        //从当前线程获取到userid
        Long userId = BaseContext.getCurrentId();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);

        //若有则商品数量加1
        if (list != null && list.size() > 0) {
            shoppingCart = list.get(0);
            shoppingCart.setNumber(shoppingCart.getNumber() + 1);
            shoppingCartMapper.updateNumberById(shoppingCart);
        } else {
            //若无则将商品添加到购物车当中
            Dish dish = dishMapper.getById(shoppingCart.getDishId());
            Setmeal setmeal = setmealMapper.getById(shoppingCart.getSetmealId());
            if (dish != null) {
                //商品为菜品
                shoppingCart.setName(dish.getName());
                shoppingCart.setImage(dish.getImage());
                shoppingCart.setAmount(dish.getPrice());
            } else {
                //商品为套餐
                shoppingCart.setName(setmeal.getName());
                shoppingCart.setImage(setmeal.getImage());
                shoppingCart.setAmount(setmeal.getPrice());
            }
            shoppingCart.setNumber(1);
            shoppingCart.setCreateTime(LocalDateTime.now());
            //将购物车数据插入到数据库当中
            shoppingCartMapper.insert(shoppingCart);
        }
    }

    /**
     * 查看购物车
     */
    @Override
    public List<ShoppingCart> showShoppingCart() {
        //通过当前用户id来查询
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        return shoppingCartMapper.list(shoppingCart);
    }

    /**
     * 清空购物车
     */
    @Override
    public void cleanShoppingCart() {
        //通过当前用户id来清空
        Long userId = BaseContext.getCurrentId();
        shoppingCartMapper.clean(userId);
    }

    /**
     * 删除购物车中商品
     * @param shoppingCartDTO
     */
    @Override
    public void subShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        //删除前先判断该商品的数量是否为1
        //通过线程获得当前用户id
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        shoppingCart.setUserId(userId);

        //获取该商品详细信息
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        shoppingCart = list.get(0);
        if (list != null && list.size() > 0) {
            Integer number = shoppingCart.getNumber();
            if (number > 1) {
                //商品数量为1时，减少购物车中该商品的数量
                shoppingCart.setNumber(shoppingCart.getNumber() - 1);
                shoppingCartMapper.updateNumberById(shoppingCart);
            } else {
                shoppingCartMapper.deleteById(shoppingCart.getId());
            }
        }
    }
}
