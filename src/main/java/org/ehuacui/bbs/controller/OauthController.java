package org.ehuacui.bbs.controller;

import org.ehuacui.bbs.common.BaseController;
import org.ehuacui.bbs.common.Constants;
import org.ehuacui.bbs.model.Role;
import org.ehuacui.bbs.model.User;
import org.ehuacui.bbs.model.UserRole;
import org.ehuacui.bbs.service.IRoleService;
import org.ehuacui.bbs.service.IUserRoleService;
import org.ehuacui.bbs.service.IUserService;
import org.ehuacui.bbs.utils.DateUtil;
import org.ehuacui.bbs.utils.ResourceUtil;
import org.ehuacui.bbs.utils.StringUtil;
import org.ehuacui.bbs.utils.WebUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by ehuacui.
 * Copyright (c) 2016, All Rights Reserved.
 * http://www.ehuacui.org
 */
@Controller
@RequestMapping("/oauth")
public class OauthController extends BaseController {

    @Autowired
    private IUserService userService;
    @Autowired
    private IRoleService roleService;
    @Autowired
    private IUserRoleService userRoleService;

    private static final String STATE = "thirdlogin_state";

    /**
     * github登录
     */
    @RequestMapping(value = "/githublogin", method = RequestMethod.GET)
    public String githublogin(HttpServletResponse response) {
        String state = StringUtil.randomString(6);
        WebUtil.setCookie(response, STATE, state, 5 * 60, "/", ResourceUtil.getWebConfigValueByKey("cookie.domain"), true);
        StringBuffer sb = new StringBuffer();
        sb.append("https://github.com/login/oauth/authorize")
                .append("?")
                .append("client_id")
                .append("=")
                .append(ResourceUtil.getWebConfigValueByKey("github.client_id"))
                .append("&")
                .append("state")
                .append("=")
                .append(state)
                .append("&")
                .append("scope")
                .append("=")
                .append("user");
        return redirect(sb.toString());
    }

    /**
     * github登录成功后回调
     */
    @RequestMapping(value = "/githubcallback", method = RequestMethod.GET)
    public String githubcallback(@RequestParam("code") String code, @RequestParam("state") String state,
                                 @RequestParam("callback") String callback,
                                 HttpServletRequest request, HttpServletResponse response) {
        String cookieState = WebUtil.getCookie(request, STATE);
        if (state.equalsIgnoreCase(cookieState)) {
//            请求access_token
            Map<String, String> map1 = new HashMap<String, String>();
            map1.put("client_id", ResourceUtil.getWebConfigValueByKey("github.client_id"));
            map1.put("client_secret", ResourceUtil.getWebConfigValueByKey("github.client_secret"));
            map1.put("code", code);
            Map<String, String> headers = new HashMap<String, String>();
            headers.put("Accept", "application/json");
            //String resp1 = HttpKit.post("https://github.com/login/oauth/access_token", map1, "", headers);
            String resp1 = "";
            Map respMap1 = StringUtil.parseToMap(resp1);
            //access_token, scope, token_type
            String github_access_token = (String) respMap1.get("access_token");
            //获取用户信息
            Map<String, String> map2 = new HashMap<String, String>();
            map2.put("access_token", github_access_token);
            //String resp2 = HttpKit.get("https://api.github.com/user", map2);
            String resp2 = "";
            Map respMap2 = StringUtil.parseToMap(resp2);
            Double githubId = (Double) respMap2.get("id");
            String login = (String) respMap2.get("login");
            String avatar_url = (String) respMap2.get("avatar_url");
            String email = (String) respMap2.get("email");
            String html_url = (String) respMap2.get("html_url");

            Date now = new Date();
            String access_token = StringUtil.getUUID();
            User user = userService.findByThirdId(String.valueOf(githubId));
            boolean flag = true;
            if (user == null) {
                user = new User();
                user.setInTime(now);
                user.setAccessToken(access_token);
                user.setScore(0);
                user.setThirdId(String.valueOf(githubId));
                user.setIsBlock(false);
                user.setChannel(Constants.LoginEnum.Github.name());
                user.setReceiveMsg(true);//邮箱接收社区消息
                flag = false;
            }
            user.setNickname(login);
            user.setAvatar(avatar_url);
            user.setEmail(email);
            user.setUrl(html_url);
            user.setExpireTime(DateUtil.getDateAfter(now, 30));//30天后过期,要重新认证
            if (flag) {
                userService.update(user);
            } else {
                userService.save(user);
                //新注册的用户角色都是普通用户
                Role role = roleService.findByName("user");
                if (role != null) {
                    UserRole userRole = new UserRole();
                    userRole.setUid(user.getId());
                    userRole.setRid(role.getId());
                    userRoleService.save(userRole);
                }
            }
            WebUtil.setCookie(response, Constants.USER_ACCESS_TOKEN,
                    StringUtil.getEncryptionToken(user.getAccessToken()),
                    30 * 24 * 60 * 60, "/",
                    ResourceUtil.getWebConfigValueByKey("cookie.domain"),
                    true);
            if (StringUtil.notBlank(callback)) {
                try {
                    callback = URLDecoder.decode(callback, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    callback = null;
                }
            }
            return redirect(StringUtil.notBlank(callback) ? callback : "/");
        } else {
            return redirect("/");
        }
    }

}
