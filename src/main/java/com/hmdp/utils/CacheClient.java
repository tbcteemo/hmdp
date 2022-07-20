package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@Slf4j
@Component
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //无参构造方法
    public CacheClient(){}

    //有参构造方法
    public CacheClient(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //获取简易互斥锁的方法
    private boolean tryLock(String lockKey){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(lockKey,"1",10,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //释放简易锁
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    //set方法
    public void set(String key, Object value, Long time, TimeUnit timeUnit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,timeUnit);
    }

    //逻辑过期的set方法
    public <T> void setWithLogicalExpireTime(String key,T value,Long time,TimeUnit timeunit){
        RedisData<T> redisData = new RedisData<>();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeunit.toSeconds(time)));
        //写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(value));
    }

    // 根据ID查询店铺（返回空值防穿透版本）的方法queryByIdWIthPassThrough
    public <R,ID> R queryByIdWithPassThrough(
            String keyPrefix , ID id, Class<R> type , Function<ID,R> dbFallBack,
            Long time , TimeUnit unit){
        String key = keyPrefix + id;
//        1.查询Redis中是否存在；
        String json = stringRedisTemplate.opsForValue().get(key);
//        2.Redis中如果存在，则直接返回；若不存在，则查数据库。
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json,type);
        }

//        在Redis中，如果是空值也会被isNotBlank方法判定为false，进入执行查数据库的流程。所以此处要加入空值判断。
        if (json != null){
            return null;
        }
        R r = dbFallBack.apply(id);
        if (r == null) {
//            Cache和DB都没有的数据，则做一个空值到Redis中，防止缓存穿透。
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        this.set(key,r,time,unit);
        return r;
    }


    //TODO 根据ID查询店铺（使用逻辑过期法解决缓存击穿版本）的方法queryByIdWithMutex
    public <R,ID> R queryByIdWithLogicalExpire(
            String keyPrefix , ID id, Class<R> type,Function<ID,R> dbFallBack,
            Long time, TimeUnit timeUnit){
//        1.从Redis中获取缓存
        String key = keyPrefix + id;
//        2.判断在Redis中是否存在，不存在则返回null，存在则进入过期判断
        String json = stringRedisTemplate.opsForValue().get(key);
//        为什么Redis中不存在就直接返回null，而不是查数据库呢？因为在缓存击穿中，热点key往往是在预热中手动存入Redis的，如果没有说明就不是热点key。
//        如果不存在就直接进行查库，那就是直接被击穿了，做这些方法就失去了意义。
//        3.不存在，查询未命中，返回null
        if (StrUtil.isBlank(json)) {
            return null;
        }
//        4.命中，需要先把json反序列化
        RedisData<R> redisData = JSONUtil.toBean(json,RedisData.class);
        R r = redisData.getData();
        LocalDateTime expireTime = redisData.getExpireTime();
//        5.判断是否过期
//        5.1 没有过期，直接返回对象
        if (expireTime.isAfter(LocalDateTime.now())){
            return r;
        }
//        5.2 已经过期，则进入缓存重建。
//        6.缓存重建
//        6.1获取简易互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean getLock = tryLock(lockKey);
//        6.2 判断锁是否获取成功
        if (getLock == true){
//            6.3 成功，则开启独立线程，实现缓存重建
            SimpleThreadPool.CACHE_REBUILD_EXECUTOR.submit(() -> {
                //重建缓存
                try {
                    R r1 = dbFallBack.apply(id);
                    this.setWithLogicalExpireTime(key,r1,time,timeUnit);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        return r;
    }
}
