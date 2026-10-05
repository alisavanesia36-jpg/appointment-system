<template>
  <view class="page">
    <view class="header">
      <view class="title">预约管理</view>
      <view class="subtitle">查看与管理全部用户预约</view>
    </view>

    <!-- 状态筛选 -->
    <view class="filter-bar">
      <view
        v-for="opt in filterOptions"
        :key="opt.value"
        :class="['filter-chip', filterStatus === opt.value ? 'filter-chip-active' : '']"
        @click="onFilter(opt.value)"
      >
        {{ opt.label }}
      </view>
    </view>

    <!-- 加载 -->
    <view v-if="loading" class="state">
      <text>加载中...</text>
    </view>

    <!-- 无权限 -->
    <view v-else-if="permissionDenied" class="state error">
      <text>无权限访问</text>
    </view>

    <!-- 错误 -->
    <view v-else-if="errorMessage" class="state error">
      <text>{{ errorMessage }}</text>
      <button class="retry-btn" @click="loadAll">重新加载</button>
    </view>

    <!-- 空 -->
    <view v-else-if="appointments.length === 0" class="state">
      <text>{{ emptyHint }}</text>
    </view>

    <!-- 列表 -->
    <view v-else class="appointment-list">
      <view
        v-for="item in appointments"
        :key="item.id"
        class="appointment-card"
      >
        <view class="card-top">
          <view class="service-name">
            {{ item.serviceName || '服务已删除' }}
          </view>
          <view :class="['status-tag', statusClass(item.status)]">
            {{ statusText(item.status) }}
          </view>
        </view>

        <view class="card-mid">
          <view class="info-line">
            <text class="info-label">预约号</text>
            <text class="info-value">#{{ item.id }}</text>
          </view>
          <view class="info-line">
            <text class="info-label">用户</text>
            <text class="info-value">{{ item.username || ('用户#' + item.userId) }}</text>
          </view>
          <view class="info-line">
            <text class="info-label">员工</text>
            <text class="info-value">{{ item.staffName || '员工已删除' }}</text>
          </view>
          <view class="info-line">
            <text class="info-label">预约时间</text>
            <text class="info-value">{{ formatDateTime(item.appointmentTime) }}</text>
          </view>
        </view>

        <view
          v-if="canConfirm(item.status) || canComplete(item.status) || canDelete(item.status)"
          class="card-bottom"
        >
          <button
            v-if="canConfirm(item.status)"
            class="action-btn action-confirm"
            :disabled="operatingId === item.id"
            @click="onConfirm(item)"
          >
            {{ operatingId === item.id ? '处理中...' : '确认预约' }}
          </button>
          <button
            v-if="canComplete(item.status)"
            class="action-btn action-complete"
            :disabled="operatingId === item.id"
            @click="onComplete(item)"
          >
            {{ operatingId === item.id ? '处理中...' : '完成预约' }}
          </button>
          <button
            v-if="canDelete(item.status)"
            class="action-btn action-delete"
            :disabled="operatingId === item.id"
            @click="onDelete(item)"
          >
            删除预约
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import {
  getAllAppointments,
  getAppointmentsByStatus,
  confirmAppointment,
  completeAppointment,
  deleteAppointment
} from '@/api/appointment.js'
import { getServices } from '@/api/service.js'
import { getStaffList } from '@/api/staff.js'
import { getAllUsers } from '@/api/user.js'
import { isLoggedIn, isAdmin, logout as authLogout } from '@/utils/auth.js'

// ==================== 状态筛选 ====================

const ALL = '__ALL__'

const filterOptions = [
  { label: '全部', value: ALL },
  { label: '待确认', value: 'PENDING' },
  { label: '已确认', value: 'CONFIRMED' },
  { label: '已取消', value: 'CANCELLED' },
  { label: '已完成', value: 'COMPLETED' }
]

const filterStatus = ref(ALL)
const appointments = ref([])
const loading = ref(false)
const errorMessage = ref('')
const operatingId = ref(null)
const permissionDenied = ref(false)

// ==================== 工具方法 ====================

function formatDateTime(value) {
  if (!value) return '-'
  // 后端返回 "YYYY-MM-DDTHH:mm:ss" 字符串，按本地时区解析
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function statusText(status) {
  switch (status) {
    case 'PENDING': return '待确认'
    case 'CONFIRMED': return '已确认'
    case 'CANCELLED': return '已取消'
    case 'COMPLETED': return '已完成'
    default: return status || '-'
  }
}

function statusClass(status) {
  switch (status) {
    case 'PENDING': return 'status-pending'
    case 'CONFIRMED': return 'status-confirmed'
    case 'CANCELLED': return 'status-cancelled'
    case 'COMPLETED': return 'status-completed'
    default: return 'status-default'
  }
}

function canConfirm(status) {
  // 仅待确认可被管理员"确认"
  return status === 'PENDING'
}

function canComplete(status) {
  // 仅已确认可被管理员"完成"
  return status === 'CONFIRMED'
}

function canDelete(status) {
  // 管理员可以删除任意状态的预约（包括 null、未知状态、历史脏数据）。
  // 删除按钮的可见性不依赖 status 是否为已知枚举，仅由 item.id 存在与否决定。
  return true
}

function buildMap(list, keyField = 'id') {
  const map = new Map()
  if (!Array.isArray(list)) return map
  list.forEach((item) => {
    if (item && item[keyField] !== undefined && item[keyField] !== null) {
      map.set(item[keyField], item)
    }
  })
  return map
}

const emptyHint = computed(() => {
  switch (filterStatus.value) {
    case 'PENDING': return '暂无待确认预约'
    case 'CONFIRMED': return '暂无已确认预约'
    case 'CANCELLED': return '暂无已取消预约'
    case 'COMPLETED': return '暂无已完成预约'
    default: return '暂无预约记录'
  }
})

// ==================== 加载与操作 ====================

async function fetchAppointments() {
  // 根据筛选状态选择 API
  if (filterStatus.value === ALL) {
    return getAllAppointments()
  }
  return getAppointmentsByStatus(filterStatus.value)
}

// 安全获取用户列表：/users 仅 ADMIN 可用；如果意外拿到 403 静默 fallback 到空 Map
async function fetchUsersSafe() {
  try {
    return await getAllUsers()
  } catch (e) {
    console.warn('[管理预约] 获取用户列表失败，回退到空 Map：', e && e.message)
    return []
  }
}

async function loadAll() {
  if (permissionDenied.value) return

  loading.value = true
  errorMessage.value = ''

  try {
    // 并发拉取：当前筛选的预约 + 用户 + 服务 + 员工 —— 一次到位，不做循环请求
    const [list, services, staffs, users] = await Promise.all([
      fetchAppointments(),
      getServices().catch((e) => {
        console.warn('[管理预约] 获取服务列表失败：', e && e.message)
        return []
      }),
      getStaffList().catch((e) => {
        console.warn('[管理预约] 获取员工列表失败：', e && e.message)
        return []
      }),
      fetchUsersSafe()
    ])

    const userMap = buildMap(users)
    const serviceMap = buildMap(services)
    const staffMap = buildMap(staffs)

    const apptList = Array.isArray(list) ? list : []
    const decorated = apptList.map((a) => ({
      ...a,
      username: userMap.get(a.userId)?.username || '',
      userPhone: userMap.get(a.userId)?.phone || '',
      serviceName: serviceMap.get(a.serviceId)?.name || '',
      staffName: staffMap.get(a.staffId)?.name || ''
    }))

    // 按预约时间倒序：未来在前 / 已过期在后
    decorated.sort((a, b) => {
      const ta = new Date(a.appointmentTime).getTime()
      const tb = new Date(b.appointmentTime).getTime()
      return tb - ta
    })

    appointments.value = decorated
  } catch (error) {
    console.error('加载预约管理列表失败：', error)
    appointments.value = []
    errorMessage.value = error?.message || '预约加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function onFilter(value) {
  if (filterStatus.value === value) return
  filterStatus.value = value
  loadAll()
}

async function runWith(id, fn, successText) {
  operatingId.value = id
  try {
    await fn()
    uni.showToast({ title: successText, icon: 'success' })
    await loadAll()
  } catch (err) {
    const msg = err && err.message ? err.message : '操作失败，请稍后重试'
    uni.showToast({ title: msg, icon: 'none' })
  } finally {
    operatingId.value = null
  }
}

function onConfirm(item) {
  if (!item || !item.id) return
  runWith(item.id, () => confirmAppointment(item.id), '确认成功')
}

function onComplete(item) {
  if (!item || !item.id) return
  runWith(item.id, () => completeAppointment(item.id), '完成成功')
}

function onDelete(item) {
  if (!item || !item.id) return
  uni.showModal({
    title: '提示',
    content: '确认删除这条预约吗？',
    success: async (res) => {
      if (!res.confirm) return
      await runWith(item.id, () => deleteAppointment(item.id), '删除成功')
    }
  })
}

// ==================== 权限校验 ====================

function guardAdmin() {
  if (!isLoggedIn()) {
    // 未登录：清掉本地态并跳登录
    authLogout(true)
    return false
  }
  if (!isAdmin()) {
    // 已登录但不是 ADMIN：显示"无权限访问"，1.2s 后回到首页
    permissionDenied.value = true
    errorMessage.value = ''
    appointments.value = []
    setTimeout(() => {
      uni.reLaunch({ url: '/pages/index/index' })
    }, 1200)
    return false
  }
  return true
}

onLoad(() => {
  if (guardAdmin()) {
    loadAll()
  }
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 32rpx;
  box-sizing: border-box;
  background: #f6f7fb;
}

.header {
  margin-bottom: 24rpx;
}

.title {
  font-size: 44rpx;
  font-weight: 700;
  color: #222;
}

.subtitle {
  margin-top: 12rpx;
  font-size: 26rpx;
  color: #888;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-bottom: 24rpx;
  background: #fff;
  padding: 20rpx 24rpx;
  border-radius: 20rpx;
  box-shadow: 0 6rpx 20rpx rgba(0, 0, 0, 0.04);
}

.filter-chip {
  padding: 12rpx 26rpx;
  font-size: 26rpx;
  color: #4b5563;
  background: #f3f4f6;
  border-radius: 999rpx;
  line-height: 1;
  cursor: pointer;
}

.filter-chip-active {
  background: #3b82f6;
  color: #fff;
  font-weight: 500;
}

.appointment-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.appointment-card {
  background: #fff;
  border-radius: 20rpx;
  padding: 28rpx;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.05);
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 20rpx;
  border-bottom: 2rpx solid #f3f4f6;
}

.service-name {
  flex: 1;
  min-width: 0;
  font-size: 32rpx;
  font-weight: 600;
  color: #222;
}

.status-tag {
  flex-shrink: 0;
  margin-left: 16rpx;
  display: inline-block;
  padding: 8rpx 20rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 500;
}

.status-pending {
  background-color: #eff6ff;
  color: #1d4ed8;
}

.status-confirmed {
  background-color: #ecfdf5;
  color: #047857;
}

.status-cancelled {
  background-color: #f3f4f6;
  color: #6b7280;
}

.status-completed {
  background-color: #f5f3ff;
  color: #5b21b6;
}

.status-default {
  background-color: #f3f4f6;
  color: #6b7280;
}

.card-mid {
  padding: 16rpx 0 0 0;
}

.info-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10rpx 0;
}

.info-label {
  font-size: 26rpx;
  color: #6b7280;
}

.info-value {
  font-size: 28rpx;
  color: #111827;
  font-weight: 500;
}

.card-bottom {
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 2rpx solid #f3f4f6;
  display: flex;
  justify-content: flex-end;
  gap: 16rpx;
  flex-wrap: wrap;
}

.action-btn {
  padding: 0 28rpx;
  font-size: 26rpx;
  line-height: 64rpx;
  background: #fff;
  border-radius: 12rpx;
  font-weight: 500;
}

.action-confirm {
  color: #1d4ed8;
  border: 2rpx solid #bfdbfe;
}

.action-complete {
  color: #047857;
  border: 2rpx solid #a7f3d0;
}

.action-delete {
  color: #ef4444;
  border: 2rpx solid #fecaca;
}

.action-btn[disabled] {
  color: #cbd5e1;
  border-color: #e5e7eb;
}

.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 120rpx 32rpx;
  color: #999;
  font-size: 28rpx;
}

.state.error {
  color: #d9534f;
}

.retry-btn {
  margin-top: 24rpx;
  padding: 0 36rpx;
  font-size: 26rpx;
  line-height: 72rpx;
  background: #fff;
  color: #333;
  border: 1rpx solid #ddd;
  border-radius: 12rpx;
}
</style>
