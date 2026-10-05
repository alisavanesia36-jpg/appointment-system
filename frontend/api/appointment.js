import http from '@/utils/request.js'

// ==================== 用户侧预约 API ====================

// 创建预约
// POST /appointments
// 请求体：{ userId, serviceId, staffId, appointmentTime }
// appointmentTime 格式："YYYY-MM-DDTHH:mm:ss"（LocalDateTime ISO 8601）
// 后端 Controller 接收 @RequestBody Appointment 实体，未传 status 时由 service 层兜底为 PENDING。
export function createAppointment(data) {
  return http.post('/appointments', data)
}

// 查询当前登录用户的全部预约
// GET /appointments/my
// 返回 List<Appointment>，结构：
//   { id, userId, serviceId, staffId, appointmentTime, status }
// 注意：Appointment 实体不返回 serviceName / staffName（字段是纯 Long 外键），
// 前端需要另外查询 /services 和 /staff 来补全展示名称。
export function getMyAppointments() {
  return http.get('/appointments/my')
}

// 取消预约（仅能取消自己的 PENDING / CONFIRMED 预约，后端有校验）
// PUT /appointments/{id}/cancel
export function cancelAppointment(id) {
  return http.put(`/appointments/${id}/cancel`)
}

// ==================== v2.1 第一阶段：预约改期 ====================
// 改期（仅修改 appointmentTime；serviceId/staffId/userId/status 一律由后端从数据库原值覆盖入参）
// PUT /appointments/{id}/reschedule
// 请求体：{ "appointmentTime": "YYYY-MM-DDTHH:mm:ss" }
// 后端校验：
//   - 仅 PENDING / CONFIRMED 可改期
//   - USER 只能改自己的预约；ADMIN 可以改任何人
//   - 所有预约业务校验（员工-服务关系、营业时间、当前时间、冲突）由后端完整执行
export function rescheduleAppointment(id, appointmentTime) {
  return http.put(`/appointments/${id}/reschedule`, {
    appointmentTime: appointmentTime
  })
}

// ==================== 管理员侧预约 API ====================
// 全部接口后端已经限制仅 ADMIN 访问（v1.5 第一阶段加固）。
// 前端在 /pages/admin/appointment/list.vue 中以管理员角色调用。

// 查询全部预约（仅 ADMIN）
// GET /appointments
// 返回 List<Appointment>，结构与 /appointments/my 一致。
export function getAllAppointments() {
  return http.get('/appointments')
}

// 按状态查询预约（仅 ADMIN）
// GET /appointments/status/{status}
// status 取值：PENDING / CONFIRMED / CANCELLED / COMPLETED
export function getAppointmentsByStatus(status) {
  return http.get(`/appointments/status/${status}`)
}

// 管理员确认预约（仅 ADMIN）
// PUT /appointments/{id}/confirm
// 后端校验：仅 PENDING 可被确认 → CONFIRMED
export function confirmAppointment(id) {
  return http.put(`/appointments/${id}/confirm`)
}

// 管理员完成预约（仅 ADMIN）
// PUT /appointments/{id}/complete
// 后端校验：仅 CONFIRMED 可被完成 → COMPLETED
export function completeAppointment(id) {
  return http.put(`/appointments/${id}/complete`)
}

// 管理员删除预约（仅 ADMIN）
// DELETE /appointments/{id}
export function deleteAppointment(id) {
  return http.delete(`/appointments/${id}`)
}

// ==================== v2.0 第一阶段：可用时间段查询 ====================
// 查询某员工在指定服务 + 日期下的可预约起始时间（HH:mm 字符串列表）
// GET /appointments/available-slots?staffId=X&serviceId=Y&date=YYYY-MM-DD[&excludeAppointmentId=Z]
// 注意：duration 由后端从 service 表读取，前端不可信、也不应传
// 任何已登录用户可访问
//
// excludeAppointmentId 是 v2.1 新增的可选参数：
//   - 不传：v2.0 行为完全不变（向后兼容）
//   - 传值：从 busy 列表中剔除该预约自身，让原时段可重新出现在可用时间中（改期场景）
//   - 业务校验：该预约的 staffId / serviceId 必须匹配请求参数，否则返回 400
export function getAvailableSlots(staffId, serviceId, date, excludeAppointmentId) {
  const params = { staffId, serviceId, date }
  if (excludeAppointmentId !== null && excludeAppointmentId !== undefined) {
    params.excludeAppointmentId = excludeAppointmentId
  }
  return http.get('/appointments/available-slots', params)
}

export default {
  // 用户侧
  createAppointment,
  getMyAppointments,
  cancelAppointment,
  // v2.1 改期
  rescheduleAppointment,
  // 管理员侧
  getAllAppointments,
  getAppointmentsByStatus,
  confirmAppointment,
  completeAppointment,
  deleteAppointment,
  // v2.0
  getAvailableSlots
}
