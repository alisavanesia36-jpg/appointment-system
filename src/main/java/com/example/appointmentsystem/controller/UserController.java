package com.example.appointmentsystem.controller;

import com.example.appointmentsystem.dto.UserAdminUpdateDTO;
import com.example.appointmentsystem.dto.UserCreateDTO;
import com.example.appointmentsystem.dto.UserUpdateDTO;
import com.example.appointmentsystem.entity.User;
import com.example.appointmentsystem.security.SecurityUtils;
import com.example.appointmentsystem.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // 管理员创建用户
    @PostMapping
    public User create(@RequestBody UserCreateDTO dto) {
        return userService.createUser(dto);
    }

    // 查询所有用户（ADMIN）
    @GetMapping
    public List<User> findAll() {
        return userService.findAll();
    }

    // 查询当前登录用户
    @GetMapping("/me")
    public User me() {
        return userService.findByUsername(SecurityUtils.getCurrentUsername());
    }

    // 查询指定用户（本人或 ADMIN）
    @GetMapping("/{id}")
    public User findById(@PathVariable Long id) {
        User target = userService.findById(id);
        if (SecurityUtils.isAdmin()) {
            return target;
        }
        User current = userService.findByUsername(SecurityUtils.getCurrentUsername());
        if (!current.getId().equals(target.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该用户");
        }
        return target;
    }

    // 管理员删除用户
    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        userService.deleteById(id);
        return "删除成功";
    }

    // 管理员更新任意用户
    @PutMapping("/{id}")
    public User update(@PathVariable Long id, @RequestBody UserAdminUpdateDTO dto) {
        return userService.adminUpdate(id, dto);
    }

    // 当前用户自助更新
    @PutMapping("/me")
    public User updateMe(@RequestBody UserUpdateDTO dto) {
        return userService.updateMe(SecurityUtils.getCurrentUsername(), dto);
    }
}
