package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        1.获取session
        HttpSession session = request.getSession();

//        2.从session中获取用户
        Object user = session.getAttribute("User");
//        3.判断用户是否存在于session中
        if (user == null){
            response.setStatus(401);
            return false;
        }
//        4.判定之后的操作，存在则放行，不存在则拦截；
        UserHolder.saveUser((UserDTO) user);
//        5.放行的操作，将用户的信息保存到TreadLocal传递给Controller
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
