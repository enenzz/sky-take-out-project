package com.sky.aspect;

import com.sky.annotation.AutoFill;
import com.sky.context.BaseContext;
import com.sky.enumeration.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static com.sky.constant.AutoFillConstant.*;

/**
 * 自定义切面，实现公共字段填充处理逻辑
 */
@Aspect
@Component
@Slf4j
public class AutoFillAspect {

    /**
     * 切入点
     */
    @Pointcut("execution(* com.sky.mapper.*.*(..)) && @annotation(com.sky.annotation.AutoFill)")
    public void autoFillPointCut(){}

    /**
     * 前置通知，再切入点前进行赋值
     */
    @Before("autoFillPointCut()")
    public void autoFill(JoinPoint joinPoint) throws Exception {
        log.info("开始公共字段填充...");

        //获取数据库的操作类型
        MethodSignature signature = (MethodSignature)joinPoint.getSignature(); //获取方法签名 该类型的可用方法更多
        AutoFill autoFill = signature.getMethod().getAnnotation(AutoFill.class); //获取方法上的注解
        OperationType operationType = autoFill.value(); //获得数据库的操作类型

        //获取当前被拦截方法的实体对象
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return;
        }
        Object entity = args[0]; //约定第一个参数为实体对象
        Class entityClass = entity.getClass(); //实体对象的class对象

        //准备赋值的数据
        LocalDateTime now = LocalDateTime.now(); //当前操作时间
        Long currentId = BaseContext.getCurrentId(); //当前操作人的id

        //获取该实体对象的方法
        Method setCreateTime = entityClass.getDeclaredMethod(SET_CREATE_TIME, LocalDateTime.class);
        Method setUpdateTime = entityClass.getDeclaredMethod(SET_UPDATE_TIME, LocalDateTime.class);
        Method setCreateUser = entityClass.getDeclaredMethod(SET_CREATE_USER, Long.class);
        Method setUpdateUser = entityClass.getDeclaredMethod(SET_UPDATE_USER, Long.class);

        //利用反射根据数据库操作类型分别进行进行赋值
        if (operationType == OperationType.INSERT) {
            //插入操作
            setCreateTime.invoke(entity, now);
            setUpdateTime.invoke(entity, now);
            setCreateUser.invoke(entity, currentId);
            setUpdateUser.invoke(entity, currentId);
        } else if (operationType == OperationType.UPDATE) {
            //修改操作
            setUpdateTime.invoke(entity, now);
            setUpdateUser.invoke(entity, currentId);
        }


    }
}
