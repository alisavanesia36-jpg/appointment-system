package com.example.appointmentsystem.controller;

import com.example.appointmentsystem.dto.UserCreateDTO;
import com.example.appointmentsystem.entity.User;
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


    // 新增用户
    @PostMapping
    public User create(@RequestBody UserCreateDTO dto) {
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(dto.getPassword());
        user.setPhone(dto.getPhone());
        // role 使用 User 实体默认值 "USER"
        return userService.save(user);
    }


    // 查询所有用户
    @GetMapping
    public List<User> findAll() {
        return userService.findAll();
    }


    // 根据ID查询用户
    @GetMapping("/{id}")
    public User findById(@PathVariable Long id) {
        return userService.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {

        userService.deleteById(id);

        return "删除成功";
    }

    @PutMapping("/{id}")
    public User update(
            @PathVariable Long id,
            @RequestBody User user) {

        return userService.update(id, user);
    }
}
