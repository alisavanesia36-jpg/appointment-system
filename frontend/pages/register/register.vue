<template>
	<view class="page">
		<view class="card">
			<view class="header">
				<view class="title">注册账号</view>
				<view class="subtitle">创建一个新的预约系统账号</view>
			</view>

			<view class="form">
				<view class="form-item">
					<view class="label">用户名 <text class="required">*</text></view>
					<input
						class="input"
						type="text"
						v-model="form.username"
						placeholder="请输入用户名"
						:disabled="loading"
					/>
				</view>

				<view class="form-item">
					<view class="label">密码 <text class="required">*</text></view>
					<input
						class="input"
						type="password"
						v-model="form.password"
						placeholder="请输入密码"
						:disabled="loading"
					/>
				</view>

				<view class="form-item">
					<view class="label">确认密码 <text class="required">*</text></view>
					<input
						class="input"
						type="password"
						v-model="form.confirmPassword"
						placeholder="请再次输入密码"
						:disabled="loading"
					/>
				</view>

				<view class="form-item">
					<view class="label">手机号</view>
					<input
						class="input"
						type="text"
						v-model="form.phone"
						placeholder="请输入手机号（选填）"
						:disabled="loading"
					/>
				</view>

				<button class="btn-primary" :disabled="loading" @click="handleRegister">
					{{ loading ? '注册中...' : '注册' }}
				</button>

				<view class="footer-links">
					<text class="link" @click="goLogin">已有账号？去登录</text>
				</view>
			</view>
		</view>
	</view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { register as registerApi } from '@/api/auth.js'

const loading = ref(false)

const form = reactive({
	username: '',
	password: '',
	confirmPassword: '',
	phone: ''
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
	if (!form.password) {
		showToast('请输入密码')
		return false
	}
	if (!form.confirmPassword) {
		showToast('请输入确认密码')
		return false
	}
	if (form.password !== form.confirmPassword) {
		showToast('两次输入的密码不一致')
		return false
	}
	return true
}

async function handleRegister() {
	if (loading.value) return
	if (!validate()) return

	loading.value = true
	try {
		const payload = {
			username: form.username.trim(),
			password: form.password,
			phone: form.phone && form.phone.trim() ? form.phone.trim() : ''
		}

		await registerApi(payload)

		uni.showToast({
			title: '注册成功',
			icon: 'success'
		})

		setTimeout(() => {
			uni.redirectTo({
				url: '/pages/login/login'
			})
		}, 800)
	} catch (err) {
		const msg = err && err.message ? err.message : '注册失败，请稍后重试'
		showToast(msg)
	} finally {
		loading.value = false
	}
}

function goLogin() {
	uni.navigateBack({
		fail: () => {
			uni.redirectTo({
				url: '/pages/login/login'
			})
		}
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
	gap: 24rpx;
}

.form-item {
	display: flex;
	flex-direction: column;
	gap: 12rpx;

	.label {
		font-size: 28rpx;
		color: #374151;
		font-weight: 500;

		.required {
			color: #ef4444;
			margin-left: 4rpx;
		}
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
