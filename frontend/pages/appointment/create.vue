<template>
  <view class="page">
    <view class="header">
      <view class="title">确认预约</view>
      <view class="subtitle">选择日期与可预约时间</view>
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
      <view class="card-title">预约日期</view>

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
    </view>

    <view class="card">
      <view class="card-title">可预约时间</view>

      <view v-if="loadingSlots" class="slots-state">
        <text>正在加载可预约时间...</text>
      </view>

      <view v-else-if="errorSlots" class="slots-state slots-error">
        <text>{{ errorSlots }}</text>
        <button class="retry-btn" @click="loadAvailableSlots">重新加载</button>
      </view>

      <view v-else-if="availableSlots.length === 0" class="slots-state">
        <text>当天没有可预约时段，请选择其他日期</text>
      </view>

      <view v-else class="slot-grid">
        <view
          v-for="slot in availableSlots"
          :key="slot"
          :class="['slot-btn', selectedSlot === slot ? 'slot-btn-active' : '']"
          @click="onSelectSlot(slot)"
        >
          {{ slot }}
        </view>
      </view>
    </view>

    <view v-if="timeHint" class="hint-bar">
      {{ timeHint }}
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
import { createAppointment, getAvailableSlots } from '@/api/appointment.js'
import { getCurrentUser } from '@/utils/auth.js'

const serviceId = ref(null)
const serviceName = ref('')
const staffId = ref(null)
const staffName = ref('')

const dateValue = ref('')
const selectedSlot = ref('')
const availableSlots = ref([])
const loadingSlots = ref(false)
const errorSlots = ref('')
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
    !!selectedSlot.value
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

function showToast(msg) {
  uni.showToast({
    title: msg,
    icon: 'none'
  })
}

async function loadAvailableSlots() {
  if (!staffId.value || !serviceId.value || !dateValue.value) return

  loadingSlots.value = true
  errorSlots.value = ''
  availableSlots.value = []
  selectedSlot.value = ''

  try {
    const list = await getAvailableSlots(
      Number(staffId.value),
      Number(serviceId.value),
      dateValue.value
    )
    availableSlots.value = Array.isArray(list) ? list : []
  } catch (err) {
    console.error('[appointment/create] 加载可用时间失败：', err)
    errorSlots.value = (err && err.message) || '可用时间加载失败，请稍后重试'
  } finally {
    loadingSlots.value = false
  }
}

function onDateChange(e) {
  dateValue.value = e.detail.value
  errorMessage.value = ''
  loadAvailableSlots()
}

function onSelectSlot(slot) {
  selectedSlot.value = slot
  errorMessage.value = ''
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

  // 后端是最后防线 —— 这里仍调原 createAppointment，请求体结构不变
  const appointmentTime = `${dateValue.value}T${selectedSlot.value}:00`

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
    return
  }

  // 默认日期 = 今天，并立刻加载可用时间段
  dateValue.value = formatDate(new Date())
  loadAvailableSlots()
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

.slots-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60rpx 0;
  color: #999;
  font-size: 26rpx;
  text-align: center;
}

.slots-error {
  color: #d9534f;
}

.retry-btn {
  margin-top: 20rpx;
  padding: 0 36rpx;
  font-size: 26rpx;
  line-height: 64rpx;
  background: #fff;
  color: #333;
  border: 1rpx solid #ddd;
  border-radius: 12rpx;
}

.slot-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.slot-btn {
  flex: 0 0 calc((100% - 48rpx) / 4);
  box-sizing: border-box;
  text-align: center;
  padding: 18rpx 0;
  background: #fff;
  color: #3b82f6;
  border: 2rpx solid #bfdbfe;
  border-radius: 12rpx;
  font-size: 28rpx;
  font-weight: 500;
  line-height: 1;
}

.slot-btn-active {
  background: #3b82f6;
  color: #ffffff;
  border-color: #3b82f6;
}

.hint-bar {
  margin: 0 32rpx 24rpx;
  padding: 16rpx 24rpx;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 24rpx;
  border-radius: 12rpx;
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