package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ShopTypeMapper shopTypeMapper;

    @Override
    public Result getAllInList() {
        String key = RedisConstants.CACHE_SHOP_KEY + "all";
        String shopTypeListString = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopTypeListString)){
            return Result.ok(JSONUtil.toList(shopTypeListString, ShopType.class));
        }
        List<ShopType> shopTypes = shopTypeMapper.selectList(null);
        if (shopTypes == null){
            return Result.fail("商户列表为空，请核查数据库！");
        }
        stringRedisTemplate.opsForValue().set(key,StrUtil.toString(shopTypes));
        return Result.ok(shopTypes);
    }
}
