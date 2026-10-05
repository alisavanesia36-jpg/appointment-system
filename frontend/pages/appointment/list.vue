<template>
  <view class="page">
    <view class="header">
      <view class="title">我的预约</view>
      <view class="subtitle">查看和管理你的预约记录</view>
    </view>

    <view v-if="loading" class="state">
      <text>加载中...</text>
    </view>

    <view v-else-if="errorMessage" class="state error">
      <text>{{ errorMessage }}</text>
      <button class="retry-btn" @click="loadAll">重新加载</button>
    </view>

    <view v-else-if="appointments.length === 0" class="state">
      <text>暂无预约记录</text>
      <button class="empty-btn" @click="goToServices">去预约一个服务</button>
    </view>

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
            <text class="info-label">员工</text>
            <text class="info-value">{{ item.staffName || '员工已删除' }}</text>
          </view>
          <view class="info-line">
            <text class="info-label">预约时间</text>
            <text class="info-value">{{ formatDateTime(item.appointmentTime) }}</text>
          </view>
        </view>

        <view v-if="canCancel(item.status)" class="card-bottom">
          <button
            class="cancel-btn"
            :disabled="cancellingId === item.id"
            @click="onCancel(item)"
          >
            {{ cancellingId === item.id ? '取消中...' : '取消预约' }}
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getMyAppointments, cancelAppointment } from '@/api/appointment.js'
import { getServices } from '@/api/service.js'
import { getStaffList } from '@/api/staff.js'

const appointments = ref([])
const loading = ref(false)
const errorMessage = ref('')
const cancellingId = ref(null)

function formatDateTime(value) {
  if (!value) return '-'
  // 后端返回 "YYYY-MM-DDTHH:mm:ss" 字符串，前端按本地时区解析
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

function canCancel(status) {
  return status === 'PENDING' || status === 'CONFIRMED'
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

async function loadAll() {
  loading.value = true
  errorMessage.value = ''

  try {
    // 并发拉取：预约 / 服务 / 员工 —— 一次到位，不做循环请求
    const [myAppointments, services, staffs] = await Promise.all([
      getMyAppointments(),
      getServices(),
      getStaffList()
    ])

    const serviceMap = buildMap(services)
    const staffMap = buildMap(staffs)

    const list = Array.isArray(myAppointments) ? myAppointments : []
    // 按预约时间倒序：未来在前 / 已过期在后
    const decorated = list.map((a) => ({
      ...a,
      serviceName: serviceMap.get(a.serviceId)?.name || '',
      staffName: staffMap.get(a.staffId)?.name || ''
    }))
    decorated.sort((a, b) => {
      const ta = new Date(a.appointmentTime).getTime()
      const tb = new Date(b.appointmentTime).getTime()
      return tb - ta
    })

    appointments.value = decorated
  } catch (error) {
    console.error('加载预约列表失败：', error)
    appointments.value = []
    errorMessage.value = error?.message || '预约加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function onCancel(item) {
  if (!item || !item.id) return
  if (!canCancel(item.status)) return

  uni.showModal({
    title: '提示',
    content: `确认取消「${item.serviceName || '该'}」预约吗？`,
    success: async (res) => {
      if (!res.confirm) return

      cancellingId.value = item.id
      try {
        await cancelAppointment(item.id)
        uni.showToast({ title: '取消成功', icon: 'success' })
        // 刷新列表，让状态变为 CANCELLED
        await loadAll()
      } catch (err) {
        const msg = err && err.message ? err.message : '取消失败，请稍后重试'
        uni.showToast({ title: msg, icon: 'none' })
      } finally {
        cancellingId.value = null
      }
    }
  })
}

function goToServices() {
  uni.switchTab({
    url: '/pages/index/index',
    fail: () => {
      uni.navigateTo({ url: '/pages/index/index' })
    }
  })
}

onLoad(() => {
  loadAll()
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
  margin-bottom: 32rpx;
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
  padding: 20rpx 0 0 0;
}

.info-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12rpx 0;
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
}

.cancel-btn {
  padding: 0 28rpx;
  font-size: 26rpx;
  line-height: 64rpx;
  background: #fff;
  color: #ef4444;
  border: 2rpx solid #fecaca;
  border-radius: 12rpx;
  font-weight: 500;
}

.cancel-btn[disabled] {
  color: #fca5a5;
  border-color: #fee2e2;
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

.retry-btn,
.empty-btn {
  margin-top: 24rpx;
  padding: 0 36rpx;
  font-size: 26rpx;
  line-height: 72rpx;
  background: #fff;
  color: #333;
  border: 1rpx solid #ddd;
  border-radius: 12rpx;
}

.empty-btn {
  background: #3b82f6;
  color: #fff;
  border: none;
  font-weight: 500;
}
</style>