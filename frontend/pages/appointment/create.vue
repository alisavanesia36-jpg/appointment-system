<template>
  <view class="page">
    <view class="header">
      <view class="title">确认预约</view>
      <view class="subtitle">填写预约信息后提交</view>
    </view>

    <view class="card">
      <view class="card-title">已选择服务</view>
      <view class="info-row">
        <text class="info-label">服务名称</text>
        <text class="info-value">{{ serviceName || '-' }}</text>
      </view>
    </view>

    <view class="card">
      <view class="card-title">已选择员工</view>
      <view class="info-row">
        <text class="info-label">员工姓名</text>
        <text class="info-value">{{ staffName || '-' }}</text>
      </view>
    </view>

    <view class="card">
      <view class="card-title">预约时间</view>

      <view class="form-item">
        <view class="form-label">日期</view>
        <picker
          mode="date"
          :value="dateValue"
          :start="minDate"
          :end="maxDate"
          @change="onDateChange"
        >
          <view class="picker">{{ dateValue || '请选择日期' }}</view>
        </picker>
      </view>

      <view class="form-item">
        <view class="form-label">时间</view>
        <picker
          mode="time"
          :value="timeValue"
          start="09:00"
          end="18:00"
          @change="onTimeChange"
        >
          <view class="picker">{{ timeValue || '请选择时间' }}</view>
        </picker>
      </view>

      <view v-if="timeHint" class="time-hint">{{ timeHint }}</view>
    </view>

    <view class="actions">
      <button
        class="btn-primary"
        :disabled="!canSubmit || loading"
        @click="handleSubmit"
      >
        {{ loading ? '提交中...' : '提交预约' }}
      </button>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { createAppointment } from '@/api/appointment.js'
import { getCurrentUser } from '@/utils/auth.js'

const serviceId = ref(null)
const serviceName = ref('')
const staffId = ref(null)
const staffName = ref('')

const dateValue = ref('')
const timeValue = ref('')
const loading = ref(false)
const errorMessage = ref('')

const minDate = computed(() => formatDate(new Date()))
const maxDate = computed(() => {
  const d = new Date()
  d.setDate(d.getDate() + 30)
  return formatDate(d)
})

const canSubmit = computed(() => {
  return (
    serviceId.value !== null &&
    serviceId.value !== undefined &&
    serviceId.value !== '' &&
    staffId.value !== null &&
    staffId.value !== undefined &&
    staffId.value !== '' &&
    !!dateValue.value &&
    !!timeValue.value
  )
})

const timeHint = computed(() => {
  if (!errorMessage.value) return ''
  return errorMessage.value
})

function formatDate(d) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function pad(n) {
  return String(n).padStart(2, '0')
}

function formatLocalDateTime(dateStr, timeStr) {
  // dateStr: 'YYYY-MM-DD' timeStr: 'HH:mm'
  // 输出 'YYYY-MM-DDTHH:mm:00' 供 Jackson 反序列化为 LocalDateTime
  return `${dateStr}T${timeStr}:00`
}

function onDateChange(e) {
  dateValue.value = e.detail.value
  errorMessage.value = ''
}

function onTimeChange(e) {
  timeValue.value = e.detail.value
  errorMessage.value = ''
}

function showToast(msg) {
  uni.showToast({
    title: msg,
    icon: 'none'
  })
}

async function handleSubmit() {
  if (loading.value) return
  if (!canSubmit.value) {
    showToast('请完整填写预约信息')
    return
  }

  const user = getCurrentUser()
  if (!user || user.id === undefined || user.id === null) {
    showToast('请先登录')
    setTimeout(() => {
      uni.reLaunch({ url: '/pages/login/login' })
    }, 600)
    return
  }

  // 客户端基础校验：时间不能早于当前
  const appointmentTime = formatLocalDateTime(dateValue.value, timeValue.value)
  const appointmentDate = new Date(appointmentTime)
  if (Number.isNaN(appointmentDate.getTime())) {
    showToast('时间格式错误')
    return
  }
  if (appointmentDate.getTime() < Date.now() - 60 * 1000) {
    showToast('预约时间不能早于当前时间')
    return
  }

  loading.value = true
  errorMessage.value = ''
  try {
    await createAppointment({
      userId: Number(user.id),
      serviceId: Number(serviceId.value),
      staffId: Number(staffId.value),
      appointmentTime: appointmentTime
    })

    uni.showToast({
      title: '预约成功',
      icon: 'success'
    })

    setTimeout(() => {
      uni.reLaunch({
        url: '/pages/index/index'
      })
    }, 1200)
  } catch (err) {
    console.error('[appointment/create] 提交失败：', err)
    const msg = err && err.message ? err.message : '预约失败，请稍后重试'
    errorMessage.value = msg
    showToast(msg)
  } finally {
    loading.value = false
  }
}

function decodeParam(value) {
  if (value === undefined || value === null) return ''
  try {
    return decodeURIComponent(value)
  } catch (e) {
    return String(value)
  }
}

onLoad((options) => {
  options = options || {}

  if (options.serviceId !== undefined) {
    serviceId.value = options.serviceId
  }
  serviceName.value = decodeParam(options.serviceName)

  if (options.staffId !== undefined) {
    staffId.value = options.staffId
  }
  staffName.value = decodeParam(options.staffName)

  // 如果上下文缺失，跳回服务列表
  if (
    serviceId.value === null ||
    serviceId.value === '' ||
    staffId.value === null ||
    staffId.value === ''
  ) {
    showToast('预约信息不完整')
    setTimeout(() => {
      uni.reLaunch({
        url: '/pages/service/list'
      })
    }, 800)
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

.card {
  background: #fff;
  border-radius: 20rpx;
  padding: 32rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.05);
}

.card-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #111827;
  margin-bottom: 24rpx;
}

.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 0;
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

.form-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 0;
  border-bottom: 2rpx solid #f3f4f6;
}

.form-item:last-child {
  border-bottom: none;
}

.form-label {
  font-size: 28rpx;
  color: #374151;
  font-weight: 500;
}

.picker {
  flex: 1;
  margin-left: 24rpx;
  text-align: right;
  font-size: 28rpx;
  color: #111827;
  min-height: 44rpx;
  line-height: 44rpx;
}

.time-hint {
  margin-top: 16rpx;
  font-size: 24rpx;
  color: #d9534f;
}

.actions {
  margin-top: 24rpx;
}

.btn-primary {
  height: 92rpx;
  line-height: 92rpx;
  width: 100%;
  background-color: #3b82f6;
  color: #ffffff;
  border-radius: 16rpx;
  font-size: 32rpx;
  font-weight: 600;
  border: none;
}

.btn-primary[disabled] {
  background-color: #93c5fd;
  color: #ffffff;
}
</style>