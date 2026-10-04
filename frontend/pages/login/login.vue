<template>
	<view class="page">
		<view class="card">
			<view class="header">
				<view class="title">预约系统</view>
				<view class="subtitle">登录您的账号</view>
			</view>

			<view class="form">
				<view class="form-item">
					<view class="label">用户名</view>
					<input
						class="input"
						type="text"
						v-model="form.username"
						placeholder="请输入用户名"
						:disabled="loading"
					/>
				</view>

				<view class="form-item">
					<view class="label">密码</view>
					<input
						class="input"
						type="password"
						v-model="form.password"
						placeholder="请输入密码"
						:disabled="loading"
					/>
				</view>

				<button class="btn-primary" :disabled="loading" @click="handleLogin">
					{{ loading ? '登录中...' : '登录' }}
				</button>

				<view class="footer-links">
					<text class="link" @click="goRegister">没有账号？立即注册</text>
				</view>
			</view>
		</view>
	</view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { login as loginApi } from '@/api/auth.js'
import { getMe } from '@/api/user.js'
import { setToken, setCurrentUser, isAdmin } from '@/utils/auth.js'

const loading = ref(false)

const form = reactive({
	username: '',
	password: ''
})

function showToast(msg) {
	uni.showToast({
		title: msg,
		icon: 'none'
	})
}

function validate() {
	if (!form.username || !form.username.trim()) {
		showToast('请输入用户名')
		return false
	}
	if (!form.password || !form.password.trim()) {
		showToast('请输入密码')
		return false
	}
	return true
}

async function handleLogin() {
	if (loading.value) return
	if (!validate()) return

	loading.value = true
	try {
		const loginRes = await loginApi({
			username: form.username.trim(),
			password: form.password
		})

		const token = loginRes && loginRes.token ? loginRes.token : ''
		if (!token) {
			showToast('登录失败，未获取到 token')
			return
		}
		setToken(token)

		let user = null
		try {
			user = await getMe()
			if (user) {
				setCurrentUser(user)
			}
		} catch (e) {
			console.warn('获取当前用户失败', e)
		}

		showToast('登录成功')

		setTimeout(() => {
			const role = (user && user.role) ? String(user.role).toUpperCase() : ''
			// TODO: 管理员页面后续阶段创建，当前阶段先进入首页
			// if (role === 'ADMIN') {
			//   uni.reLaunch({ url: '/pages/admin/index' })
			//   return
			// }
			if (role === 'ADMIN') {
				console.info('[Phase 1] ADMIN 角色暂时进入首页，管理员页面将在后续阶段加入')
			}
			uni.reLaunch({
				url: '/pages/index/index'
			})
		}, 400)
	} catch (err) {
		const msg = err && err.message ? err.message : '登录失败，请稍后重试'
		showToast(msg)
	} finally {
		loading.value = false
	}
}

function goRegister() {
	uni.navigateTo({
		url: '/pages/register/register'
	})
}
</script>

<style lang="scss" scoped>
.page {
	min-height: 100vh;
	background-color: #f5f7fa;
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 40rpx;
	box-sizing: border-box;
}

.card {
	width: 100%;
	max-width: 640rpx;
	background: #ffffff;
	border-radius: 24rpx;
	padding: 56rpx 40rpx;
	box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.04);
}

.header {
	text-align: center;
	margin-bottom: 48rpx;

	.title {
		font-size: 44rpx;
		font-weight: 600;
		color: #1f2937;
		margin-bottom: 12rpx;
	}

	.subtitle {
		font-size: 28rpx;
		color: #6b7280;
	}
}

.form {
	display: flex;
	flex-direction: column;
	gap: 28rpx;
}

.form-item {
	display: flex;
	flex-direction: column;
	gap: 12rpx;

	.label {
		font-size: 28rpx;
		color: #374151;
		font-weight: 500;
	}

	.input {
		height: 88rpx;
		background-color: #f9fafb;
		border: 2rpx solid #e5e7eb;
		border-radius: 16rpx;
		padding: 0 24rpx;
		font-size: 30rpx;
		color: #111827;
		box-sizing: border-box;
	}
}

.btn-primary {
	height: 92rpx;
	line-height: 92rpx;
	margin-top: 16rpx;
	background-color: #3b82f6;
	color: #ffffff;
	border-radius: 16rpx;
	font-size: 32rpx;
	font-weight: 600;
	border: none;

	&[disabled] {
		background-color: #93c5fd;
		color: #ffffff;
	}
}

.footer-links {
	text-align: center;
	margin-top: 12rpx;

	.link {
		font-size: 26rpx;
		color: #3b82f6;
	}
}
</style>
