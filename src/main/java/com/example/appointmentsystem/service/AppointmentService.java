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

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.example.appointmentsystem.exception.BusinessException;


@org.springframework.stereotype.Service
public class AppointmentService {

    // ==================== v2.0 新增：可注入时钟（用于单元测试控制"当前时间"） ====================
    /**
     * 默认使用系统时钟。测试可通过
     *   ReflectionTestUtils.setField(service, "clock", Clock.fixed(...))
     * 注入固定时钟，以稳定测试"过期 slot 过滤"行为，不依赖运行机器的真实时间。
     */
    private Clock clock = Clock.systemDefaultZone();

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

    // ==================== v2.0 第一阶段：可用时间段查询 ====================

    /**
     * 计算某员工在指定日期内、给定服务时长下，所有"可预约起始时间"（HH:mm 字符串列表）。
     *
     * 业务规则：
     * 1. 员工必须存在
     * 2. 服务必须存在
     * 3. 员工必须支持该服务
     * 4. 营业时间 09:00–18:00，按 service.duration 切片
     * 5. 跳过与已有非 CANCELLED 预约重叠的 slot
     * 6. 跳过早于当前时间的 slot
     * 7. excludeAppointmentId 不为空时，跳过该预约的 busy 区间（用于改期场景让原时段可选）
     *    附加校验：传入的 excludeId 必须存在，且 staffId / serviceId 必须匹配请求中的 staffId / serviceId，
     *    防止用户通过 excludeAppointmentId 排除任意他人预约
     *
     * 注意：此处只复验"岗位 / 时长"等基础规则；POST /appointments 仍会跑 save() 全部校验，
     * 前端绕过 available-slots 直接提交也会被 save() 拦截。
     */
    public List<String> findAvailableSlots(
            Long staffId,
            Long serviceId,
            String date,
            Long excludeAppointmentId
    ) {
        // 1. 员工必须存在
        staffRepository.findById(staffId)
                .orElseThrow(() ->
                        new BusinessException("员工不存在"));

        // 2. 服务必须存在
        Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() ->
                        new BusinessException("服务不存在"));

        // 3. 员工是否支持该服务
        boolean canProvide = staffServiceMappingRepository
                .existsByIdStaffIdAndIdServiceId(staffId, serviceId);
        if (!canProvide) {
            throw new BusinessException("该员工不支持此服务");
        }

        // 4. duration 来自 service —— 客户端不可信
        Integer duration = service.getDuration();
        if (duration == null || duration <= 0) {
            throw new BusinessException("服务时长不合法");
        }

        // 5. 解析日期 + 计算营业时段
        LocalDate targetDate = LocalDate.parse(date);
        LocalDateTime businessStart = targetDate.atTime(9, 0);
        LocalDateTime businessEnd = targetDate.atTime(18, 0);

        // 5b. excludeAppointmentId 校验：存在性 + staffId 匹配 + serviceId 匹配
        //     防止用户通过该参数排除他人预约绕过冲突检查
        if (excludeAppointmentId != null) {
            Appointment excludeAppt = appointmentRepository
                    .findById(excludeAppointmentId)
                    .orElseThrow(() ->
                            new BusinessException("预约不存在或不匹配"));
            if (!excludeAppt.getStaffId().equals(staffId)
                    || !excludeAppt.getServiceId().equals(serviceId)) {
                throw new BusinessException("预约不存在或不匹配");
            }
        }

        // 6. 查询当天该员工所有非 CANCELLED 预约（限定一天内，性能更优）
        List<Appointment> existing = appointmentRepository
                .findByStaffIdAndAppointmentTimeBetweenAndStatusNot(
                        staffId,
                        businessStart,
                        businessEnd,
                        AppointmentStatus.CANCELLED
                );

        // 7. 计算已有预约的 [start, end) 区间列表
        //    excludeAppointmentId 不为空时，把这条预约从 busy 列表中剔除
        List<LocalDateTime[]> busyIntervals = new ArrayList<>();
        for (Appointment a : existing) {
            if (excludeAppointmentId != null
                    && excludeAppointmentId.equals(a.getId())) {
                continue;
            }
            Service aService = serviceRepository.findById(a.getServiceId())
                    .orElseThrow(() ->
                            new BusinessException("服务不存在"));
            LocalDateTime aStart = a.getAppointmentTime();
            LocalDateTime aEnd = aStart.plusMinutes(aService.getDuration());
            busyIntervals.add(new LocalDateTime[]{aStart, aEnd});
        }

        // 8. 按 duration 切片生成 slot
        DateTimeFormatter hhmm = DateTimeFormatter.ofPattern("HH:mm");
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime lastStart = businessEnd.minusMinutes(duration);

        List<String> result = new ArrayList<>();
        for (LocalDateTime slot = businessStart;
             !slot.isAfter(lastStart);
             slot = slot.plusMinutes(duration)) {

            // 8a. 跳过已过期
            if (!slot.isAfter(now)) {
                continue;
            }

            // 8b. 跳过重叠
            LocalDateTime slotEnd = slot.plusMinutes(duration);
            boolean overlap = false;
            for (LocalDateTime[] b : busyIntervals) {
                if (slot.isBefore(b[1]) && slotEnd.isAfter(b[0])) {
                    overlap = true;
                    break;
                }
            }
            if (overlap) {
                continue;
            }

            // 8c. 通过校验 → 加入
            result.add(slot.format(hhmm));
        }

        return result;
    }

    // ==================== v2.1 第一阶段：用户预约改期 ====================

    /**
     * 改期：仅允许修改 appointmentTime；serviceId / staffId / userId / status 全部从数据库原值覆盖入参。
     *
     * 业务规则：
     * 1. 预约必须存在
     * 2. 权限：USER 只能改自己的（与 update 一致）；ADMIN 可以改任何人的
     * 3. 状态必须为 PENDING 或 CONFIRMED；CANCELLED / COMPLETED 一律拒绝
     * 4. serviceId / staffId / userId 入参被忽略，强制使用数据库原值（防 USER 改服务/员工）
     * 5. 员工-服务关系再次校验（即便 serviceId 没改；存在可能后端分配关系改变）
     * 6. service.duration 重新读取用于计算 newEnd
     * 7. 新时间不能早于当前时间
     * 8. 新时间必须在 09:00–18:00 营业时段内
     * 9. 新预约不能与该员工其它有效预约重叠（自身排除）
     */
    public Appointment reschedule(
            Long id,
            Appointment appointment,
            String username) {

        // 1. 原预约必须存在
        Appointment existingAppointment =
                appointmentRepository.findById(id)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "预约不存在"
                                ));

        // 2. 权限：USER 只能改自己的；ADMIN 可以改任何人
        User currentUser = resolveUser(username);
        if (!isAdmin(currentUser)
                && !existingAppointment.getUserId().equals(currentUser.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "只能修改自己的预约"
            );
        }

        // 3. 状态白名单：仅 PENDING / CONFIRMED 可改期
        AppointmentStatus currentApptStatus = existingAppointment.getStatus();
        if (currentApptStatus != AppointmentStatus.PENDING
                && currentApptStatus != AppointmentStatus.CONFIRMED) {
            throw new BusinessException("该预约状态不允许修改");
        }

        // 4. 字段冻结：serviceId / staffId / userId / status 一律使用数据库原值
        //    入参中的 appointmentTime 为唯一可变字段。
        Long lockedUserId = existingAppointment.getUserId();
        Long lockedServiceId = existingAppointment.getServiceId();
        Long lockedStaffId = existingAppointment.getStaffId();
        LocalDateTime newStart = appointment.getAppointmentTime();

        // 5. service 必须存在（沿用 update() 错误文案一致）
        Service service = serviceRepository.findById(lockedServiceId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "服务不存在"
                        ));

        // 6. staff 必须存在
        staffRepository.findById(lockedStaffId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "员工不存在"
                        ));

        // 7. 员工-服务关系必须仍然有效（即便 serviceId 没改，仍可能关系被后台移除）
        boolean canProvideService = staffServiceMappingRepository
                .existsByIdStaffIdAndIdServiceId(lockedStaffId, lockedServiceId);
        if (!canProvideService) {
            throw new BusinessException("这个员工不会做这个服务");
        }

        // 8. 查询员工所有非 CANCELLED 预约
        List<Appointment> staffAppointments = appointmentRepository
                .findByStaffIdAndStatusNot(
                        lockedStaffId,
                        AppointmentStatus.CANCELLED
                );

        // 9. 计算新预约的时间范围（duration 重新从 service 读取）
        LocalDateTime newEnd = newStart.plusMinutes(service.getDuration());

        // 10. 不能早于当前时间
        if (newStart.isBefore(LocalDateTime.now(clock))) {
            throw new BusinessException("预约时间不能早于当前时间");
        }

        // 11. 营业时间 09:00–18:00
        LocalDateTime businessStart = newStart.toLocalDate().atTime(9, 0);
        LocalDateTime businessEnd = newStart.toLocalDate().atTime(18, 0);
        if (newStart.isBefore(businessStart) || newEnd.isAfter(businessEnd)) {
            throw new BusinessException(
                    "预约时间必须在营业时间09:00-18:00内");
        }

        // 12. 冲突判断：排除自身（与 update() 一致）
        for (Appointment existing : staffAppointments) {
            if (existing.getId().equals(id)) {
                continue;
            }
            Service existingService = serviceRepository
                    .findById(existing.getServiceId())
                    .orElseThrow(() ->
                            new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "服务不存在"
                            ));
            LocalDateTime existingStart = existing.getAppointmentTime();
            LocalDateTime existingEnd = existingStart.plusMinutes(
                    existingService.getDuration());
            boolean overlap = newStart.isBefore(existingEnd)
                    && newEnd.isAfter(existingStart);
            if (overlap) {
                throw new BusinessException(
                        "这个员工在这个时间段已经有预约了");
            }
        }

        // 13. 写入：仅改 appointmentTime，其余字段保持原值
        existingAppointment.setAppointmentTime(newStart);
        // 防御性重写：即便有人恶意传其他字段，也不写入
        existingAppointment.setUserId(lockedUserId);
        existingAppointment.setServiceId(lockedServiceId);
        existingAppointment.setStaffId(lockedStaffId);
        // status 保持原值（不调用 setStatus）

        return appointmentRepository.save(existingAppointment);
    }
}
