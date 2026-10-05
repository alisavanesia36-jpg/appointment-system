import http from '@/utils/request.js'

// ==================== v2.2 第一阶段：站内通知 API ====================
// 继续使用 @/utils/request.js 的 H5 /backend-api 代理与 JWT Authorization 处理；
// 本文件不直接处理 token / 401 / 403 跳转。

/**
 * 查询当前登录用户的全部通知（按 createdAt 倒序）。
 * 不接受 userId 参数 —— 后端强制使用 SecurityContext 里的当前用户。
 * GET /notifications
 * 返回：Array<Notification>，结构
 *   { id, userId, title, content, type, relatedAppointmentId, isRead, createdAt }
 */
export function listNotifications() {
  return http.get('/notifications')
}

/**
 * 查询当前登录用户的未读通知数量。
 * GET /notifications/unread-count
 * 返回：{ count: number }
 */
export function getUnreadCount() {
  return http.get('/notifications/unread-count')
}

/**
 * 标记单条通知已读（仅本人可标记；非本人 403；幂等）。
 * PUT /notifications/{id}/read
 * 返回：Notification
 */
export function markNotificationRead(id) {
  return http.put(`/notifications/${id}/read`)
}

/**
 * 当前登录用户全部标记已读。
 * PUT /notifications/read-all
 * 返回：{ updated: number }
 */
export function markAllNotificationsRead() {
  return http.put('/notifications/read-all')
}

export default {
  listNotifications,
  getUnreadCount,
  markNotificationRead,
  markAllNotificationsRead
}