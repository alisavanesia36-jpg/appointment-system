package com.example.appointmentsystem.service;

import com.example.appointmentsystem.dto.LoginResponseDTO;
import com.example.appointmentsystem.dto.UserCreateDTO;
import com.example.appointmentsystem.entity.User;
import com.example.appointmentsystem.exception.BusinessException;
import com.example.appointmentsystem.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserService userService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       UserService userService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userService = userService;
    }

    public LoginResponseDTO login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException("密码错误");
        }

        String token = jwtService.generateToken(user);
        return new LoginResponseDTO(token);
    }

    public User register(UserCreateDTO dto) {
        return userService.createUser(dto);
    }
}
