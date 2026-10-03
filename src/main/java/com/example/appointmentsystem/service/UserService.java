package com.example.appointmentsystem.service;

import com.example.appointmentsystem.entity.User;
import com.example.appointmentsystem.entity.Role;
import com.example.appointmentsystem.exception.BusinessException;
import com.example.appointmentsystem.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }


    // 新增用户
    public User save(User user) {
        if (user.getRole() == null) {
            user.setRole(Role.USER);
        }
        // 明文密码 -> BCrypt 加密 -> 入库
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }


    // 查询所有用户
    public List<User> findAll() {
        return userRepository.findAll();
    }


    // 根据ID查询用户
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }
    // 删除用户
    public void deleteById(Long id) {
        userRepository.deleteById(id);
    }
    // 修改用户
    public User update(Long id, User user) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        existingUser.setUsername(user.getUsername());
        existingUser.setPhone(user.getPhone());

        // 仅当传入新密码时才重新加密；否则保持数据库原密码
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            existingUser.setPassword(passwordEncoder.encode(user.getPassword()));
        }

        return userRepository.save(existingUser);
    }
}
