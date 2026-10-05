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

// 管理员新增用户（仅 ADMIN）
// POST /users
// 请求体严格基于 UserCreateDTO：{ username, password, phone }
// 【硬缺口】UserCreateDTO 没有 role 字段，UserService.createUser() 强制 setRole(Role.USER)，
// 所以 POST /users 创建出来的永远是 USER；
// 需要管理员权限请先创建再通过编辑接口修改。
export function createUser(data) {
	return http.post('/users', data)
}

// 管理员编辑用户（仅 ADMIN）
// PUT /users/{id}
// 请求体基于 UserAdminUpdateDTO：{ username?, password?, phone?, role? }
// 所有字段均可选；不传的字段后端 service 视为"不变"。
// 【前端策略】编辑时不携带 password，避免误覆盖原密码；
// 如需改密，由用户本人走 PUT /users/me 或后续再加专门改密流程。
export function updateUser(id, data) {
	return http.put(`/users/${id}`, data)
}

// 管理员删除用户（仅 ADMIN）
// DELETE /users/{id}
// 后端返回 "删除成功" 字符串。
// 【硬缺口】后端 UserService.deleteById 不校验"是不是当前登录用户"，
// 前端必须在 UI 层隐藏 / 拦截"删除自己"的入口，否则后端会真的把当前 ADMIN 也删掉。
export function deleteUser(id) {
	return http.delete(`/users/${id}`)
}

export default {
	getMe,
	updateMe,
	getAllUsers,
	createUser,
	updateUser,
	deleteUser
}
