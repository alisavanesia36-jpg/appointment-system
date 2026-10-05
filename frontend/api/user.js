import http from '@/utils/request.js'

export function getMe() {
	return http.get('/users/me')
}

export function updateMe(data) {
	return http.put('/users/me', data)
}

// 管理员查询全部用户（仅 ADMIN，后端 SecurityConfig 限制）
// GET /users
// 返回 List<User>，结构：{ id, username, phone, role }
// 用于管理员预约页面把 userId 映射到 username / phone。
export function getAllUsers() {
	return http.get('/users')
}

export default {
	getMe,
	updateMe,
	getAllUsers
}
