import http from '@/utils/request.js'

// 创建预约
// POST /appointments
// 请求体：{ userId, serviceId, staffId, appointmentTime }
// appointmentTime 格式："YYYY-MM-DDTHH:mm:ss"（LocalDateTime ISO 8601）
// 后端 Controller 接收 @RequestBody Appointment 实体，并按 status=PayLoad 默认值落库。
export function createAppointment(data) {
  return http.post('/appointments', data)
}

export default {
  createAppointment
}