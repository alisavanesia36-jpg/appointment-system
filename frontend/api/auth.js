import http from '@/utils/request.js'

export function login(data) {
	return http.post('/auth/login', data)
}

export function register(data) {
	return http.post('/auth/register', data)
}

export default {
	login,
	register
}
