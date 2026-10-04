// 后端地址策略：
// - H5 开发环境：BASE_URL = '/backend-api'，走 Vite dev proxy 代理到后端
//     例: 前端请求 /backend-api/auth/register -> Vite 转发为 http://localhost:8080/auth/register
//     这样浏览器不会做跨域 OPTIONS 预检，避免 Spring Security 返回 401
//     注意：前缀选择 /backend-api 是为了避免与前端源码目录 /api/* 冲突导致 js 资源被误代理
// - 非 H5（App / 小程序）：使用 PROD_BACKEND_URL 绝对地址
// - 生产环境：修改 PROD_BACKEND_URL 为实际后端域名，或按需改为同源
const PROD_BACKEND_URL = 'http://localhost:8080'

let BASE_URL = PROD_BACKEND_URL

// #ifdef H5
BASE_URL = '/backend-api'
// #endif

let isRedirectingToLogin = false

function getToken() {
	try {
		return uni.getStorageSync('token')
	} catch (e) {
		return ''
	}
}

function clearAuthAndRedirect() {
	if (isRedirectingToLogin) return
	isRedirectingToLogin = true
	try {
		uni.removeStorageSync('token')
		uni.removeStorageSync('currentUser')
	} catch (e) {}
	const pages = getCurrentPages()
	const currentPage = pages.length > 0 ? pages[pages.length - 1].route : ''
	if (currentPage !== 'pages/login/login') {
		uni.reLaunch({
			url: '/pages/login/login',
			complete: () => {
				setTimeout(() => {
					isRedirectingToLogin = false
				}, 500)
			}
		})
	} else {
		setTimeout(() => {
			isRedirectingToLogin = false
		}, 500)
	}
}

function extractErrorMessage(res, defaultMsg) {
	if (res && res.data && typeof res.data === 'object') {
		if (res.data.message && typeof res.data.message === 'string') {
			return res.data.message
		}
		if (res.data.msg && typeof res.data.msg === 'string') {
			return res.data.msg
		}
		if (res.data.error && typeof res.data.error === 'string') {
			return res.data.error
		}
	}
	if (res && res.errMsg && typeof res.errMsg === 'string') {
		return res.errMsg
	}
	return defaultMsg
}

function request(options = {}) {
	return new Promise((resolve, reject) => {
		const {
			url,
			method = 'GET',
			data = {},
			header = {},
			timeout = 15000
		} = options

		const finalHeader = {
			'Content-Type': 'application/json',
			...header
		}

		const token = getToken()
		if (token) {
			finalHeader['Authorization'] = 'Bearer ' + token
		}

		const fullUrl = url.startsWith('http://') || url.startsWith('https://')
			? url
			: BASE_URL + url

		uni.request({
			url: fullUrl,
			method: method.toUpperCase(),
			data: data,
			header: finalHeader,
			timeout,
			success: (res) => {
				const statusCode = res.statusCode
				if (statusCode >= 200 && statusCode < 300) {
					resolve(res.data)
					return
				}

				const message = extractErrorMessage(res, '请求失败')

				if (statusCode === 401) {
					clearAuthAndRedirect()
					const err = new Error(message || '登录已过期，请重新登录')
					err.statusCode = statusCode
					err.data = res.data
					reject(err)
					return
				}

				if (statusCode === 403) {
					uni.showToast({
						title: message || '无权限访问',
						icon: 'none'
					})
					const err = new Error(message || '无权限访问')
					err.statusCode = statusCode
					err.data = res.data
					reject(err)
					return
				}

				if (statusCode === 404) {
					const err = new Error(message || '资源不存在')
					err.statusCode = statusCode
					err.data = res.data
					reject(err)
					return
				}

				if (statusCode === 400) {
					const err = new Error(message || '请求参数错误')
					err.statusCode = statusCode
					err.data = res.data
					reject(err)
					return
				}

				if (statusCode >= 500) {
					const err = new Error(message || '服务器错误，请稍后再试')
					err.statusCode = statusCode
					err.data = res.data
					reject(err)
					return
				}

				const err = new Error(message)
				err.statusCode = statusCode
				err.data = res.data
				reject(err)
			},
			fail: (err) => {
				let msg = '网络异常，请检查网络连接'
				if (err && err.errMsg) {
					if (err.errMsg.indexOf('timeout') !== -1) {
						msg = '请求超时，请稍后重试'
					} else if (err.errMsg.indexOf('fail') !== -1) {
						msg = '网络错误，请检查网络后重试'
					}
				}
				const error = new Error(msg)
				error.statusCode = 0
				error.original = err
				reject(error)
			}
		})
	})
}

export default {
	BASE_URL,
	request,
	get: (url, data, options = {}) => request({
		url,
		method: 'GET',
		data,
		...options
	}),
	post: (url, data, options = {}) => request({
		url,
		method: 'POST',
		data,
		...options
	}),
	put: (url, data, options = {}) => request({
		url,
		method: 'PUT',
		data,
		...options
	}),
	delete: (url, data, options = {}) => request({
		url,
		method: 'DELETE',
		data,
		...options
	})
}
