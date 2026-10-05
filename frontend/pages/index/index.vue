<template>
	<view class="page">
		<view class="hero-card">
			<view class="hero-title">欢迎回来</view>
			<view class="hero-subtitle">预约系统 · 用户中心</view>
		</view>

		<view class="card user-card">
			<view class="card-title">账户信息</view>

			<view class="info-row">
				<text class="info-label">用户名</text>
				<text class="info-value">{{ displayUsername }}</text>
			</view>

			<view class="info-row">
				<text class="info-label">手机号</text>
				<text class="info-value">{{ displayPhone }}</text>
			</view>

			<view class="info-row">
				<text class="info-label">角色</text>
				<view :class="['role-tag', roleClass]">{{ displayRole }}</view>
			</view>
		</view>

		<!-- 预约服务入口 -->
		<view class="card service-card" @click="goToServices">
			<view class="service-left">
				<view class="service-icon">📅</view>

				<view class="service-content">
					<view class="service-title">预约服务</view>
					<view class="service-subtitle">选择服务并进行预约</view>
				</view>
			</view>

			<view class="service-arrow">›</view>
		</view>

		<!-- 我的预约入口 -->
		<view class="card service-card" @click="goToMyAppointments">
			<view class="service-left">
				<view class="service-icon service-icon-my">📋</view>

				<view class="service-content">
					<view class="service-title">我的预约</view>
					<view class="service-subtitle">查看与管理你的预约记录</view>
				</view>
			</view>

			<view class="service-arrow">›</view>
		</view>

		<!-- 后台管理入口（仅 ADMIN 可见） -->
		<view v-if="isAdminUser" class="card service-card" @click="goToAdmin">
			<view class="service-left">
				<view class="service-icon service-icon-admin">🛠️</view>

				<view class="service-content">
					<view class="service-title">后台管理</view>
					<view class="service-subtitle">管理预约与系统数据</view>
				</view>
			</view>

			<view class="service-arrow">›</view>
		</view>

		<!-- 服务管理入口（仅 ADMIN 可见） -->
		<view v-if="isAdminUser" class="card service-card" @click="goToServiceAdmin">
			<view class="service-left">
				<view class="service-icon service-icon-service-admin">⚙️</view>

				<view class="service-content">
					<view class="service-title">服务管理</view>
					<view class="service-subtitle">维护可预约的服务列表</view>
				</view>
			</view>

			<view class="service-arrow">›</view>
		</view>

		<!-- 员工管理入口（仅 ADMIN 可见） -->
		<view v-if="isAdminUser" class="card service-card" @click="goToStaffAdmin">
			<view class="service-left">
				<view class="service-icon service-icon-staff-admin">👤</view>

				<view class="service-content">
					<view class="service-title">员工管理</view>
					<view class="service-subtitle">维护系统中的员工列表</view>
				</view>
			</view>

			<view class="service-arrow">›</view>
		</view>

		<!-- 员工-服务分配入口（仅 ADMIN 可见） -->
		<view v-if="isAdminUser" class="card service-card" @click="goToStaffServiceAdmin">
			<view class="service-left">
				<view class="service-icon service-icon-staff-service-admin">🔗</view>

				<view class="service-content">
					<view class="service-title">员工-服务分配</view>
					<view class="service-subtitle">配置每位员工可提供的服务</view>
				</view>
			</view>

			<view class="service-arrow">›</view>
		</view>

		<!-- 用户管理入口（仅 ADMIN 可见） -->
		<view v-if="isAdminUser" class="card service-card" @click="goToUserAdmin">
			<view class="service-left">
				<view class="service-icon service-icon-user-admin">👥</view>

				<view class="service-content">
					<view class="service-title">用户管理</view>
					<view class="service-subtitle">管理系统中的用户账号</view>
				</view>
			</view>

			<view class="service-arrow">›</view>
		</view>

		<view class="actions">
			<button class="btn-logout" @click="handleLogout">退出登录</button>
		</view>
	</view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getMe } from '@/api/user.js'
import {
	getCurrentUser,
	setCurrentUser,
	isLoggedIn,
	isAdmin,
	logout as authLogout
} from '@/utils/auth.js'

const user = ref(null)
const loading = ref(false)

const displayUsername = computed(() => {
	if (user.value && user.value.username) return user.value.username

	const cached = getCurrentUser()

	return cached && cached.username ? cached.username : '-'
})

const displayPhone = computed(() => {
	if (user.value && user.value.phone) return user.value.phone

	const cached = getCurrentUser()

	return cached && cached.phone ? cached.phone : '-'
})

const displayRole = computed(() => {
	let role = ''

	if (user.value && user.value.role) {
		role = user.value.role
	} else {
		const cached = getCurrentUser()
		role = cached && cached.role ? cached.role : ''
	}

	const r = String(role || '').toUpperCase()

	if (r === 'ADMIN') return '管理员'

	return '普通用户'
})

const roleClass = computed(() => {
	let role = ''

	if (user.value && user.value.role) {
		role = user.value.role
	} else {
		const cached = getCurrentUser()
		role = cached && cached.role ? cached.role : ''
	}

	const r = String(role || '').toUpperCase()

	return r === 'ADMIN' ? 'role-admin' : 'role-user'
})

// v1.6 后台管理入口控制：仅 ADMIN 显示
const isAdminUser = computed(() => {
	const u = user.value || getCurrentUser()
	if (!u || !u.role) return false
	return String(u.role).toUpperCase() === 'ADMIN'
})

async function ensureUser() {
	if (!isLoggedIn()) {
		authLogout(true)
		return
	}

	const cached = getCurrentUser()

	if (cached) {
		user.value = cached
	}

	try {
		loading.value = true

		const fresh = await getMe()

		if (fresh) {
			user.value = fresh
			setCurrentUser(fresh)
		}
	} catch (err) {
		console.warn('[首页] 刷新用户信息失败', err && err.message)
	} finally {
		loading.value = false
	}
}

function goToServices() {
	uni.navigateTo({
		url: '/pages/service/list'
	})
}

function goToMyAppointments() {
	uni.navigateTo({
		url: '/pages/appointment/list'
	})
}

function goToAdmin() {
	// 前端二次校验：缓存里不是 ADMIN 直接拒绝（防止 isAdmin 与缓存不一致的边缘情况）
	if (!isAdmin()) {
		uni.showToast({ title: '无管理员权限', icon: 'none' })
		return
	}
	uni.navigateTo({
		url: '/pages/admin/appointment/list'
	})
}

function goToServiceAdmin() {
	// 前端二次校验：缓存里不是 ADMIN 直接拒绝
	if (!isAdmin()) {
		uni.showToast({ title: '无管理员权限', icon: 'none' })
		return
	}
	uni.navigateTo({
		url: '/pages/admin/service/list'
	})
}

function goToStaffAdmin() {
	// 前端二次校验：缓存里不是 ADMIN 直接拒绝
	if (!isAdmin()) {
		uni.showToast({ title: '无管理员权限', icon: 'none' })
		return
	}
	uni.navigateTo({
		url: '/pages/admin/staff/list'
	})
}

function goToStaffServiceAdmin() {
	// 前端二次校验：缓存里不是 ADMIN 直接拒绝
	if (!isAdmin()) {
		uni.showToast({ title: '无管理员权限', icon: 'none' })
		return
	}
	uni.navigateTo({
		url: '/pages/admin/staff-service/list'
	})
}

function goToUserAdmin() {
	// 前端二次校验：缓存里不是 ADMIN 直接拒绝
	if (!isAdmin()) {
		uni.showToast({ title: '无管理员权限', icon: 'none' })
		return
	}
	uni.navigateTo({
		url: '/pages/admin/user/list'
	})
}

function handleLogout() {
	uni.showModal({
		title: '提示',
		content: '确认要退出登录吗？',
		success: (res) => {
			if (res.confirm) {
				authLogout(true)
			}
		}
	})
}

onMounted(() => {
	ensureUser()
})
</script>

<style lang="scss" scoped>
.page {
	min-height: 100vh;
	background-color: #f5f7fa;
	padding: 32rpx;
	box-sizing: border-box;
}

.hero-card {
	background: linear-gradient(135deg, #3b82f6 0%, #60a5fa 100%);
	border-radius: 24rpx;
	padding: 48rpx 36rpx;
	color: #ffffff;
	margin-bottom: 28rpx;

	.hero-title {
		font-size: 44rpx;
		font-weight: 600;
		margin-bottom: 12rpx;
	}

	.hero-subtitle {
		font-size: 26rpx;
		opacity: 0.9;
	}
}

.card {
	background: #ffffff;
	border-radius: 20rpx;
	padding: 32rpx;
	margin-bottom: 28rpx;
	box-shadow: 0 6rpx 20rpx rgba(0, 0, 0, 0.03);
}

.card-title {
	font-size: 30rpx;
	font-weight: 600;
	color: #111827;
	margin-bottom: 24rpx;
}

.user-card {
	.info-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 20rpx 0;
		border-bottom: 2rpx solid #f3f4f6;

		&:last-child {
			border-bottom: none;
		}
	}

	.info-label {
		font-size: 28rpx;
		color: #6b7280;
	}

	.info-value {
		font-size: 28rpx;
		color: #111827;
		font-weight: 500;
	}
}

/* 预约服务入口 */
.service-card {
	display: flex;
	align-items: center;
	justify-content: space-between;
	cursor: pointer;

	&:active {
		opacity: 0.85;
		transform: scale(0.99);
	}
}

.service-left {
	display: flex;
	align-items: center;
	flex: 1;
}

.service-icon {
	width: 72rpx;
	height: 72rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	background-color: #eff6ff;
	border-radius: 18rpx;
	font-size: 34rpx;
	margin-right: 20rpx;
}

.service-icon-my {
	background-color: #ecfdf5;
}

.service-icon-admin {
	background-color: #fef3c7;
}

.service-icon-service-admin {
	background-color: #f5f3ff;
}

.service-icon-staff-admin {
	background-color: #fef3c7;
}

.service-icon-staff-service-admin {
	background-color: #ecfdf5;
}

.service-icon-user-admin {
	background-color: #fee2e2;
}

.service-content {
	flex: 1;
}

.service-title {
	font-size: 30rpx;
	font-weight: 600;
	color: #111827;
}

.service-subtitle {
	margin-top: 8rpx;
	font-size: 24rpx;
	color: #9ca3af;
}

.service-arrow {
	font-size: 48rpx;
	font-weight: 300;
	color: #9ca3af;
}

.role-tag {
	display: inline-block;
	padding: 8rpx 20rpx;
	border-radius: 999rpx;
	font-size: 24rpx;
	font-weight: 500;
}

.role-user {
	background-color: #eff6ff;
	color: #1d4ed8;
}

.role-admin {
	background-color: #fef3c7;
	color: #b45309;
}

.actions {
	margin-top: 16rpx;

	.btn-logout {
		height: 92rpx;
		line-height: 92rpx;
		width: 100%;
		background-color: #ffffff;
		color: #ef4444;
		border: 2rpx solid #fecaca;
		border-radius: 16rpx;
		font-size: 30rpx;
		font-weight: 500;
	}
}
</style>