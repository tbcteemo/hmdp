package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SimpleThreadPool;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IShopService shopService;

    @Resource
    private ShopMapper shopMapper;

    @Override
    public Result queryById(Long id) {
        //使用单独的缓存防穿透见方法queryByIdWithPassThrough
        //此处为使用简易互斥锁解决缓存击穿
        Shop shop = queryByIdWithMutex(id);
        if (shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result updateShopWithCache(Shop shop) {
        Long id = shop.getId();
        if (id == null){
            return Result.fail("id不能为空");
        }
//        1. 更新数据库
//         注意：小知识点，使用MybatisPlus时，service和mapper都有updateById这个方法。
//         但是，前者返回的是boolean操作是否成功，后者返回的是int，操作了多少条。
        updateById(shop);
//        2.删除缓存；
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
//        3.返回成功信息；
        return Result.ok("更新成功");
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

    /** 根据id查询店铺（防缓存穿透版本） */
    public Shop queryByIdWithPassThrough(Long id){
        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        1.查询Redis中是否存在；
        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        2.Redis中如果存在，则直接返回；若不存在，则查数据库。
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

//        在Redis中，如果是空值也会被isNotBlank方法判定为false，进入执行查数据库的流程。所以此处要加入空值判断。
        if (shopJson != null){
            return null;
        }
        Shop shop = shopService.getById(id);
        if (shop == null) {
//            Cache和DB都没有的数据，则做一个空值到Redis中，防止缓存穿透。
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    /** 简易互斥锁解决缓存击穿的根据id查询方法 */
    public Shop queryByIdWithMutex(Long id){
        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        1.查询Redis中是否存在；
        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        2.Redis中如果存在，则直接返回；若不存在，则查数据库。
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
//        在Redis中，如果是null也会被isNotBlank方法判定为false，进入执行查数据库的流程。所以此处要加入空值判断。
        if (shopJson != null){
            return null;
        }

        //接下来是获取锁，然后进行重建缓存。
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean getLock = tryLock(lockKey);
            if (getLock == false) {
                Thread.sleep(50);
                return queryByIdWithMutex(id);
            }
            shop = getById(id);
            //如果没有这个数据，就返回一个空值到Redis里面，防止后面缓存穿透。
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //数据库有，则放到redis中
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        }catch (InterruptedException e){
            throw new RuntimeException(e);
        }finally {
            //最后释放互斥锁
            unlock(lockKey);
        }
        return shop;
    }

    /** 给数据设置逻辑过期时间到Redis中,使用RedisData类实现 */
    public void saveToRedisWithLogicalExpire(Long id, Long expiredSeconds){
        Shop shop = getById(id);
        RedisData redisData = new RedisData<Shop>();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expiredSeconds));
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));
    }

    /** 使用逻辑过期解决缓存击穿问题 */
    public Shop queryByIdWithLogicalExpire(Long id){
//        1.从Redis中获取缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        2.判断在Redis中是否存在，不存在则返回null，存在则进入过期判断
        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        为什么Redis中不存在就直接返回null，而不是查数据库呢？因为在缓存击穿中，热点key往往是在预热中手动存入Redis的，如果没有说明就不是热点key。
//        如果不存在就直接进行查库，那就是直接被击穿了，做这些方法就失去了意义。
//        3.不存在，查询未命中，返回null
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
//        4.命中，需要先把json反序列化
        RedisData<Shop> redisData = JSONUtil.toBean(shopJson,RedisData.class);
        Shop shop = redisData.getData();
        LocalDateTime expireTime = redisData.getExpireTime();
//        5.判断是否过期
//        5.1 没有过期，直接返回对象
        if (expireTime.isAfter(LocalDateTime.now())){
            return shop;
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
                    this.saveToRedisWithLogicalExpire(id, 1800L);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        return shop;
    }

}

