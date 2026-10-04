const TOKEN_KEY = 'token'
const CURRENT_USER_KEY = 'currentUser'

export function getToken() {
	try {
		return uni.getStorageSync(TOKEN_KEY) || ''
	} catch (e) {
		return ''
	}
}

export function setToken(token) {
	try {
		uni.setStorageSync(TOKEN_KEY, token)
		return true
	} catch (e) {
		return false
	}
}

export function removeToken() {
	try {
		uni.removeStorageSync(TOKEN_KEY)
		return true
	} catch (e) {
		return false
	}
}

export function getCurrentUser() {
	try {
		const raw = uni.getStorageSync(CURRENT_USER_KEY)
		if (!raw) return null
		if (typeof raw === 'string') {
			try {
				return JSON.parse(raw)
			} catch (e) {
				return null
			}
		}
		return raw
	} catch (e) {
		return null
	}
}

export function setCurrentUser(user) {
	try {
		if (!user) {
			removeCurrentUser()
			return false
		}
		const value = typeof user === 'string' ? user : JSON.stringify(user)
		uni.setStorageSync(CURRENT_USER_KEY, value)
		return true
	} catch (e) {
		return false
	}
}

export function removeCurrentUser() {
	try {
		uni.removeStorageSync(CURRENT_USER_KEY)
		return true
	} catch (e) {
		return false
	}
}

export function isLoggedIn() {
	const token = getToken()
	return !!token
}

export function isAdmin() {
	const user = getCurrentUser()
	if (!user || !user.role) return false
	const role = String(user.role).toUpperCase()
	return role === 'ADMIN'
}

export function logout(redirect = true) {
	removeToken()
	removeCurrentUser()
	if (redirect) {
		uni.reLaunch({
			url: '/pages/login/login'
		})
	}
	return true
}
