import http from '@/utils/request.js'

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

export default {
  createAppointment,
  getMyAppointments,
  cancelAppointment
}