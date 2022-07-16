package com.hmdp.utils;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class CacheClient<T> {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //无参构造方法
    public CacheClient(){}

    //有参构造方法
    public CacheClient(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //set方法
    public void set(String key, T value, Long time, TimeUnit timeUnit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,timeUnit);
    }

    //逻辑过期的set方法
    public void setWithLogicalExpireTime(String key,T value,Long time,TimeUnit timeunit){
        RedisData<T> redisData = new RedisData<>();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeunit.toSeconds(time)));
        //写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(value));
    }

    //TODO 根据ID查询店铺（返回空值防穿透版本）的方法queryByIdWIthPassThrough



    //TODO 根据ID查询店铺（使用逻辑过期法解决缓存击穿版本）的方法queryByIdWithMutex

}
