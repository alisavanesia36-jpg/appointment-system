import http from '../utils/request.js'

// ==================== 用户侧 / 通用 API ====================

// 获取全部服务（用户 + 管理员都能读）
// GET /services
export function getServices() {
  return http.get('/services')
}

// 根据 ID 获取单个服务（用户 + 管理员都能读）
// GET /services/{id}
export function getServiceById(id) {
  return http.get(`/services/${id}`)
}

// ==================== 管理员侧 API（仅 ADMIN，后端 v1.5 加固）====================

// 新增服务
// POST /services
// 请求体：{ name, duration, price }
export function createService(data) {
  return http.post('/services', data)
}

// 编辑服务
// PUT /services/{id}
export function updateService(id, data) {
  return http.put(`/services/${id}`, data)
}

// 删除服务
// DELETE /services/{id}
export function deleteService(id) {
  return http.delete(`/services/${id}`)
}

export default {
  // 用户侧 / 通用
  getServices,
  getServiceById,
  // 管理员侧
  createService,
  updateService,
  deleteService
}
