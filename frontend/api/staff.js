import http from '@/utils/request.js'

// 查询所有员工
// GET /staff
export function getStaffList() {
  return http.get('/staff')
}

// 根据ID查询员工
// GET /staff/{id}
export function getStaffById(id) {
  return http.get(`/staff/${id}`)
}

// 查询所有员工-服务对应关系
// GET /staff-services
export function getStaffServices() {
  return http.get('/staff-services')
}

// 查询某个员工会哪些服务
// GET /staff-services/staff/{staffId}
export function getStaffServicesByStaff(staffId) {
  return http.get(`/staff-services/staff/${staffId}`)
}

// 查询某个服务有哪些员工可以做
// 返回 List<StaffServiceMapping>，对象结构：
//   { id: { staffId: Number, serviceId: Number } }
// 注意：这里不会返回完整 Staff 对象，只返回 staffId，
// 前端拿到 staffId 列表后需要再去调 getStaffList() 拿员工详情。
// GET /staff-services/service/{serviceId}
export function getStaffServicesByService(serviceId) {
  return http.get(`/staff-services/service/${serviceId}`)
}

export default {
  getStaffList,
  getStaffById,
  getStaffServices,
  getStaffServicesByStaff,
  getStaffServicesByService
}