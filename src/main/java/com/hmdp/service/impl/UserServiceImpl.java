package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    public User createUserWithPhone(String phone){
        User user = new User();
        user.setPhone(phone);
        user.setCreateTime(LocalDateTime.now());
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        long userid = user.getId();
        return user;
    }

    @Override
    public Result sendCode(String phone, HttpSession session){
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误！");
        }
//        生成验证码
        String code = RandomUtil.randomNumbers(6);
//        保存到session
        session.setAttribute("code",code);
//        模拟发送验证码
        log.debug("发送验证码成功，验证码：｛" + code + "}");
//        返回成功与否的结果
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginFormDTO,HttpSession session){
//        1.手机号验证；
        String phone = loginFormDTO.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号错误");
        }
//        2.校验验证码：
        Object cacheCode = session.getAttribute("code");
        String code = loginFormDTO.getCode();
        if (cacheCode == null || !cacheCode.toString().equals(code)){
            return Result.fail("验证码错误");
        }

//        3.根据手机号查询用户
        User user = query().eq("phone",phone).one();
        if (user == null){
            user = createUserWithPhone(phone);
        }

//        4.保存用户到session
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        return Result.ok();

    }
}
