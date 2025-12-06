package com.sky.mapper;

import com.sky.entity.DishFlavor;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DishFlavorsMapper {

    /**
     * 向口味表批量插入数据
     * @param dishFlavors
     */
    void insertBatch(List<DishFlavor> dishFlavors);


    /**
     * 根据菜品id删除
     * @param dishId
     */
    @Delete("delete from dish_flavor where dish_id = #{dishId}")
    void deleteByDishId(Long dishId);
}
