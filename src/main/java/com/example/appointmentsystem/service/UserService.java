package com.example.appointmentsystem.service;

import com.example.appointmentsystem.dto.UserAdminUpdateDTO;
import com.example.appointmentsystem.dto.UserCreateDTO;
import com.example.appointmentsystem.dto.UserUpdateDTO;
import com.example.appointmentsystem.entity.Role;
import com.example.appointmentsystem.entity.User;
import com.example.appointmentsystem.exception.BusinessException;
import com.example.appointmentsystem.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // 创建用户（注册 / 管理员创建），默认角色 USER
    public User createUser(UserCreateDTO dto) {
        if (dto.getUsername() == null || dto.getUsername().isBlank()) {
            throw new BusinessException("用户名不能为空");
        }
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new BusinessException("密码不能为空");
        }
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new BusinessException("用户名已存在");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setPhone(dto.getPhone());
        user.setRole(Role.USER);
        return userRepository.save(user);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
    }

    public void deleteById(Long id) {
        User user = findById(id);
        userRepository.deleteById(user.getId());
    }

    // 管理员更新任意用户
    public User adminUpdate(Long id, UserAdminUpdateDTO dto) {
        User existing = findById(id);
        updateUsername(existing, dto.getUsername());
        if (dto.getPhone() != null) {
            existing.setPhone(dto.getPhone());
        }
        updatePassword(existing, dto.getPassword());
        if (dto.getRole() != null) {
            existing.setRole(dto.getRole());
        }
        return userRepository.save(existing);
    }

    // 当前用户自助更新
    public User updateMe(String username, UserUpdateDTO dto) {
        User existing = findByUsername(username);
        updateUsername(existing, dto.getUsername());
        if (dto.getPhone() != null) {
            existing.setPhone(dto.getPhone());
        }
        updatePassword(existing, dto.getPassword());
        return userRepository.save(existing);
    }

    private void updateUsername(User existing, String newUsername) {
        if (newUsername == null || newUsername.isBlank()) {
            return;
        }
        if (!existing.getUsername().equals(newUsername)
                && userRepository.existsByUsername(newUsername)) {
            throw new BusinessException("用户名已存在");
        }
        existing.setUsername(newUsername);
    }

    private void updatePassword(User existing, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            return;
        }
        existing.setPassword(passwordEncoder.encode(newPassword));
    }
}
