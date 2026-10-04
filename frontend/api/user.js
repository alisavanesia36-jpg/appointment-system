import http from '@/utils/request.js'

export function getMe() {
	return http.get('/users/me')
}

export function updateMe(data) {
	return http.put('/users/me', data)
}

export default {
	getMe,
	updateMe
}
