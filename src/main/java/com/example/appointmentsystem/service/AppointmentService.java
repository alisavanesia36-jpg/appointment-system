package com.example.appointmentsystem.service;

import com.example.appointmentsystem.entity.Appointment;
import com.example.appointmentsystem.entity.AppointmentStatus;
import com.example.appointmentsystem.entity.Role;
import com.example.appointmentsystem.entity.Service;
import com.example.appointmentsystem.entity.User;
import com.example.appointmentsystem.repository.AppointmentRepository;
import com.example.appointmentsystem.repository.ServiceRepository;
import com.example.appointmentsystem.repository.StaffRepository;
import com.example.appointmentsystem.repository.StaffServiceMappingRepository;
import com.example.appointmentsystem.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.example.appointmentsystem.exception.BusinessException;


@org.springframework.stereotype.Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final ServiceRepository serviceRepository;
    private final StaffServiceMappingRepository staffServiceMappingRepository;
    private final UserRepository userRepository;
    private final StaffRepository staffRepository;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            ServiceRepository serviceRepository,
            StaffServiceMappingRepository staffServiceMappingRepository,
            UserRepository userRepository,
            StaffRepository staffRepository) {

        this.appointmentRepository = appointmentRepository;
        this.serviceRepository = serviceRepository;
        this.staffServiceMappingRepository = staffServiceMappingRepository;
        this.userRepository = userRepository;
        this.staffRepository = staffRepository;
    }

    private User resolveUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户不存在"));
    }

    private boolean isAdmin(User user) {
        return user.getRole() == Role.ADMIN;
    }

    /**
     * 创建预约
     */
    public Appointment save(Appointment appointment, String username) {

        User currentUser = resolveUser(username);
        if (!isAdmin(currentUser)
                && !appointment.getUserId().equals(currentUser.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "只能创建自己的预约"
            );
        }

        // 1. 检查用户是否存在
        userRepository.findById(appointment.getUserId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "用户不存在"
                ));

        // 2. 检查服务是否存在
        Service service = serviceRepository
                .findById(appointment.getServiceId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "服务不存在"
                ));

        // 3. 检查员工是否存在
        staffRepository.findById(appointment.getStaffId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "员工不存在"
                ));

        // 4. 检查员工是否会这个服务
        boolean canProvideService =
                staffServiceMappingRepository
                        .existsByIdStaffIdAndIdServiceId(
                                appointment.getStaffId(),
                                appointment.getServiceId()
                        );

        if (!canProvideService) {
            throw new BusinessException(
                    "这个员工不会做这个服务");
        }

        // 5. 查询这个员工已有的预约
        List<Appointment> appointments =
                appointmentRepository.findByStaffIdAndStatusNot(
                        appointment.getStaffId(),
                        AppointmentStatus.CANCELLED
                );

        // 6. 计算新预约的时间范围
        int duration = service.getDuration();

        LocalDateTime newStart =
                appointment.getAppointmentTime();

        LocalDateTime newEnd =
                newStart.plusMinutes(duration);

        // 7. 检查预约时间不能早于当前时间
        if (newStart.isBefore(LocalDateTime.now())) {
            throw new BusinessException(
                    "预约时间不能早于当前时间");
        }

        // 8. 检查营业时间
        LocalDateTime businessStart =
                newStart.toLocalDate().atTime(9, 0);

        LocalDateTime businessEnd =
                newStart.toLocalDate().atTime(18, 0);

        if (newStart.isBefore(businessStart)
                || newEnd.isAfter(businessEnd)) {

            throw new BusinessException(
                    "预约时间必须在营业时间09:00-18:00内");
        }

        // 9. 检查时间是否重叠
        for (Appointment existing : appointments) {

            Service existingService =
                    serviceRepository
                            .findById(existing.getServiceId())
                            .orElseThrow(() ->
                                    new ResponseStatusException(
                                            HttpStatus.NOT_FOUND,
                                            "服务不存在"
                                    ));

            LocalDateTime existingStart =
                    existing.getAppointmentTime();

            LocalDateTime existingEnd =
                    existingStart.plusMinutes(
                            existingService.getDuration());

            boolean overlap =
                    newStart.isBefore(existingEnd)
                            && newEnd.isAfter(existingStart);

            if (overlap) {
                throw new BusinessException(
                        "这个员工在这个时间段已经有预约了");
            }
        }

        // 10. 兜底状态：前端未传 status 时，默认为 PENDING
        if (appointment.getStatus() == null) {
            appointment.setStatus(AppointmentStatus.PENDING);
        }

        return appointmentRepository.save(appointment);
    }

    /**
     * 查询全部预约
     */
    public List<Appointment> findAll() {
        return appointmentRepository.findAll();
    }

    /**
     * 查询当前登录用户的预约
     */
    public List<Appointment> findMyAppointments(String username) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        return appointmentRepository.findByUserId(currentUser.getId());
    }

    /**
     * 根据用户查询预约（仅允许查询自己的）
     */
    public List<Appointment> findByUserId(Long userId, String username) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if (!isAdmin(currentUser) && !currentUser.getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "无权访问该用户的预约"
            );
        }
        return appointmentRepository.findByUserId(userId);
    }

    /**
     * 查询用户未完成的预约
     */
    public List<Appointment> findUnfinishedByUserId(
            Long userId,
            String username) {

        User currentUser = resolveUser(username);
        if (!isAdmin(currentUser) && !currentUser.getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "无权访问该用户的预约"
            );
        }

        return appointmentRepository.findByUserIdAndStatusIn(
                userId,
                List.of(
                        AppointmentStatus.PENDING,
                        AppointmentStatus.CONFIRMED
                )
        );
    }

    /**
     * 根据状态查询预约
     */
    public List<Appointment> findByStatus(
            AppointmentStatus status) {

        return appointmentRepository.findByStatus(status);
    }

    /**
     * 根据时间范围查询预约
     */
    public List<Appointment> findByAppointmentTimeBetween(
            LocalDateTime start,
            LocalDateTime end) {

        return appointmentRepository.findByAppointmentTimeBetween(
                start,
                end
        );
    }

    /**
     * 根据 ID 查询预约
     */
    public Appointment findById(Long id, String username) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "预约不存在"
                ));

        User currentUser = resolveUser(username);
        if (!isAdmin(currentUser)
                && !appointment.getUserId().equals(currentUser.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "无权访问该预约"
            );
        }

        return appointment;
    }

    /**
     * 删除预约
     */
    public void deleteById(Long id) {
        appointmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "预约不存在"
                ));

        appointmentRepository.deleteById(id);
    }

    /**
     * 修改预约
     */
    public Appointment update(
            Long id,
            Appointment appointment,
            String username) {

        // 1. 检查原预约是否存在
        Appointment existingAppointment =
                appointmentRepository.findById(id)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "预约不存在"
                                ));

        User currentUser = resolveUser(username);
        if (!isAdmin(currentUser)
                && !existingAppointment.getUserId().equals(currentUser.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "只能修改自己的预约"
            );
        }
        if (!isAdmin(currentUser)) {
            appointment.setUserId(currentUser.getId());
        }

        // 2. 检查用户是否存在
        userRepository.findById(appointment.getUserId())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "用户不存在"
                        ));

        // 3. 检查服务是否存在
        Service service =
                serviceRepository
                        .findById(appointment.getServiceId())
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "服务不存在"
                                ));

        // 4. 检查员工是否存在
        staffRepository.findById(appointment.getStaffId())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "员工不存在"
                        ));

        // 5. 检查员工是否会这个服务
        boolean canProvideService =
                staffServiceMappingRepository
                        .existsByIdStaffIdAndIdServiceId(
                                appointment.getStaffId(),
                                appointment.getServiceId()
                        );

        if (!canProvideService) {
            throw new BusinessException(
                    "这个员工不会做这个服务");
        }

        // 6. 查询员工现有预约
        List<Appointment> appointments =
                appointmentRepository.findByStaffIdAndStatusNot(
                        appointment.getStaffId(),
                        AppointmentStatus.CANCELLED
                );

        // 7. 计算新的时间范围
        LocalDateTime newStart =
                appointment.getAppointmentTime();

        LocalDateTime newEnd =
                newStart.plusMinutes(
                        service.getDuration());

        // 8. 检查预约时间不能早于当前时间
        if (newStart.isBefore(LocalDateTime.now())) {
            throw new BusinessException(
                    "预约时间不能早于当前时间");
        }

        // 9. 检查营业时间
        LocalDateTime businessStart =
                newStart.toLocalDate().atTime(9, 0);

        LocalDateTime businessEnd =
                newStart.toLocalDate().atTime(18, 0);

        if (newStart.isBefore(businessStart)
                || newEnd.isAfter(businessEnd)) {

            throw new BusinessException(
                    "预约时间必须在营业时间09:00-18:00内");
        }

        // 10. 检查时间重叠
        for (Appointment existing : appointments) {

            // 不检查正在修改的这条预约
            if (existing.getId().equals(id)) {
                continue;
            }

            Service existingService =
                    serviceRepository
                            .findById(existing.getServiceId())
                            .orElseThrow(() ->
                                    new ResponseStatusException(
                                            HttpStatus.NOT_FOUND,
                                            "服务不存在"
                                    ));

            LocalDateTime existingStart =
                    existing.getAppointmentTime();

            LocalDateTime existingEnd =
                    existingStart.plusMinutes(
                            existingService.getDuration());

            boolean overlap =
                    newStart.isBefore(existingEnd)
                            && newEnd.isAfter(existingStart);

            if (overlap) {
                throw new BusinessException(
                        "这个员工在这个时间段已经有预约了");
            }
        }

        // 11. 更新预约信息
        existingAppointment.setUserId(
                appointment.getUserId());

        existingAppointment.setServiceId(
                appointment.getServiceId());

        existingAppointment.setStaffId(
                appointment.getStaffId());

        existingAppointment.setAppointmentTime(
                appointment.getAppointmentTime());

        return appointmentRepository.save(
                existingAppointment);
    }

    /**
     * 确认预约
     */
    public Appointment confirm(Long id) {

        Appointment appointment =
                appointmentRepository.findById(id)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "预约不存在"
                                ));

        if (appointment.getStatus()
                != AppointmentStatus.PENDING) {

            throw new BusinessException(
                    "只有待确认的预约才能确认");
        }

        appointment.setStatus(
                AppointmentStatus.CONFIRMED);

        return appointmentRepository.save(appointment);
    }

    /**
     * 取消预约（仅允许取消自己的预约）
     */
    public Appointment cancel(Long id, String username) {

        Appointment appointment =
                appointmentRepository.findById(id)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "预约不存在"
                                ));

        User currentUser = resolveUser(username);

        if (!isAdmin(currentUser)
                && !appointment.getUserId().equals(currentUser.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "只能取消自己的预约"
            );
        }

        if (appointment.getStatus()
                != AppointmentStatus.PENDING
                && appointment.getStatus()
                != AppointmentStatus.CONFIRMED) {

            throw new BusinessException(
                    "只有待确认或已确认的预约才能取消");
        }

        appointment.setStatus(
                AppointmentStatus.CANCELLED);

        return appointmentRepository.save(appointment);
    }

    /**
     * 完成预约
     */
    public Appointment complete(Long id) {

        Appointment appointment =
                appointmentRepository.findById(id)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "预约不存在"
                                ));

        if (appointment.getStatus()
                != AppointmentStatus.CONFIRMED) {

            throw new BusinessException(
                    "只有已确认的预约才能完成");
        }

        appointment.setStatus(
                AppointmentStatus.COMPLETED);

        return appointmentRepository.save(appointment);
    }
}
