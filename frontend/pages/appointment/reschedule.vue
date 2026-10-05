<template>
  <view class="page">
    <view class="header">
      <view class="title">改期</view>
      <view class="subtitle">为你的预约选择新的时间</view>
    </view>

    <view v-if="loadingInit" class="state">
      <text>加载中...</text>
    </view>

    <view v-else-if="initError" class="state error">
      <text>{{ initError }}</text>
      <button class="retry-btn" @click="loadAppointment">重新加载</button>
    </view>

    <template v-else>
      <view class="card">
        <view class="card-title">原预约信息</view>
        <view class="info-row">
          <text class="info-label">服务</text>
          <text class="info-value">{{ serviceName || '-' }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">员工</text>
          <text class="info-value">{{ staffName || '-' }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">原预约时间</text>
          <text class="info-value">{{ oldTimeText }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">状态</text>
          <text :class="['status-tag', statusClass(currentStatus)]">
            {{ statusText(currentStatus) }}
          </text>
        </view>
      </view>

      <view class="card">
        <view class="card-title">新预约日期</view>
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

      <view v-if="errorMessage" class="hint-bar">
        {{ errorMessage }}
      </view>

      <view class="actions">
        <button
          class="btn-primary"
          :disabled="!canSubmit || submitting"
          @click="handleSubmit"
        >
          {{ submitting ? '改期中...' : '确认改期' }}
        </button>
      </view>
    </template>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getAvailableSlots, rescheduleAppointment } from '@/api/appointment.js'
import { getServices } from '@/api/service.js'
import { getStaffList } from '@/api/staff.js'

const appointmentId = ref(null)
const serviceId = ref(null)
const serviceName = ref('')
const staffId = ref(null)
const staffName = ref('')
const oldTimeText = ref('')
const currentStatus = ref('')
const dateValue = ref('')
const selectedSlot = ref('')
const availableSlots = ref([])
const loadingSlots = ref(false)
const errorSlots = ref('')
const loadingInit = ref(false)
const initError = ref('')
const submitting = ref(false)
const errorMessage = ref('')

const minDate = computed(() => formatDate(new Date()))
const maxDate = computed(() => {
  const d = new Date()
  d.setDate(d.getDate() + 30)
  return formatDate(d)
})

const canSubmit = computed(() => {
  return (
    appointmentId.value !== null &&
    appointmentId.value !== undefined &&
    !!dateValue.value &&
    !!selectedSlot.value
  )
})

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

function formatDate(d) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function formatDateTime(value) {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function showToast(msg) {
  uni.showToast({ title: msg, icon: 'none' })
}

/**
 * 进入页面后立刻拉取原预约 + 服务/员工信息：
 *   - 调 GET /appointments/{id}（后端已校验权限：USER 只能查自己，ADMIN 查任何人）
 *   - 并发调 getServices / getStaffList 用于补全名称展示
 *   - 校验状态白名单（CANCELLED / COMPLETED 不允许改期）
 */
async function loadAppointment() {
  if (!appointmentId.value) {
    initError.value = '缺少预约 ID'
    return
  }
  loadingInit.value = true
  initError.value = ''
  errorMessage.value = ''

  try {
    const [appointmentResponse, servicesList, staffList] = await Promise.all([
      uni.request({
        url: '/backend-api/appointments/' + appointmentId.value,
        method: 'GET',
        header: buildAuthHeader()
      }),
      getServices(),
      getStaffList()
    ])

    // 兼容 http 工具的 { data: ... } 与 uni.request 的 { data: ..., status: 401 } 两种结构
    const appt = unwrap(appointmentResponse)

    if (!appt || !appt.id) {
      throw new Error('预约不存在')
    }

    // 后端已经做了权限校验。这里只检查状态白名单（CANCELLED / COMPLETED 不允许改期）
    if (appt.status !== 'PENDING' && appt.status !== 'CONFIRMED') {
      throw new Error('该预约状态不允许修改')
    }

    serviceId.value = appt.serviceId
    staffId.value = appt.staffId
    currentStatus.value = appt.status
    oldTimeText.value = formatDateTime(appt.appointmentTime)

    const serviceMap = new Map()
    if (Array.isArray(servicesList)) {
      servicesList.forEach((s) => {
        if (s && s.id !== undefined && s.id !== null) serviceMap.set(s.id, s)
      })
    }
    const staffMap = new Map()
    if (Array.isArray(staffList)) {
      staffList.forEach((s) => {
        if (s && s.id !== undefined && s.id !== null) staffMap.set(s.id, s)
      })
    }
    serviceName.value = serviceMap.get(appt.serviceId)?.name || ''
    staffName.value = staffMap.get(appt.staffId)?.name || ''

    // 默认日期 = 原预约日期（保留 local time 解析）
    if (appt.appointmentTime) {
      const d = new Date(appt.appointmentTime)
      if (!Number.isNaN(d.getTime())) {
        dateValue.value = formatDate(d)
      } else {
        dateValue.value = minDate.value
      }
    } else {
      dateValue.value = minDate.value
    }

    // 立即加载可用时间段（传 excludeAppointmentId 把当前预约排除）
    await loadAvailableSlots()
  } catch (err) {
    console.error('[reschedule] 加载预约失败：', err)
    initError.value = (err && err.message) || '加载预约失败，请稍后重试'
    availableSlots.value = []
  } finally {
    loadingInit.value = false
  }
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
      dateValue.value,
      Number(appointmentId.value)
    )
    availableSlots.value = Array.isArray(list) ? list : []
  } catch (err) {
    console.error('[reschedule] 加载可用时间失败：', err)
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
  if (submitting.value) return
  if (!canSubmit.value) {
    showToast('请选择新时段')
    return
  }

  const newTime = `${dateValue.value}T${selectedSlot.value}:00`

  submitting.value = true
  errorMessage.value = ''
  try {
    await rescheduleAppointment(appointmentId.value, newTime)

    uni.showToast({
      title: '改期成功',
      icon: 'success'
    })

    setTimeout(() => {
      uni.reLaunch({
        url: '/pages/appointment/list'
      })
    }, 1200)
  } catch (err) {
    console.error('[reschedule] 改期失败：', err)
    const msg = err && err.message ? err.message : '改期失败，请稍后重试'
    errorMessage.value = msg
    showToast(msg)
  } finally {
    submitting.value = false
  }
}

/**
 * 构造带 Authorization 的 header。
 * 直接调 uni.request 而不是走 http，是为了把 appointmentId 路径参数交给后端鉴权端点
 * GET /appointments/{id}（后端已校验权限）。
 *
 * 不重复实现 token 注入的复杂逻辑——只读取 localStorage 中的 token。
 */
function buildAuthHeader() {
  let token = ''
  try {
    token = uni.getStorageSync('token') || ''
  } catch (e) {
    token = ''
  }
  const headers = {}
  if (token) headers['Authorization'] = 'Bearer ' + token
  return headers
}

function unwrap(response) {
  // http.* 工具返回：{ data, statusCode, header, ... }
  // uni.request 直接返回：{ data, statusCode, header, ... }
  // 两者结构一致，data 字段就是业务 JSON
  if (!response) return null
  if (response.statusCode === 401) {
    throw new Error('请先登录')
  }
  if (response.statusCode === 403) {
    const msg = response.data && response.data.message
      ? response.data.message
      : '无权访问该预约'
    throw new Error(msg)
  }
  if (response.statusCode === 404) {
    const msg = response.data && response.data.message
      ? response.data.message
      : '预约不存在'
    throw new Error(msg)
  }
  if (response.statusCode >= 400) {
    const msg = response.data && response.data.message
      ? response.data.message
      : '加载预约失败'
    throw new Error(msg)
  }
  return response.data
}

onLoad((options) => {
  options = options || {}
  if (options.appointmentId !== undefined && options.appointmentId !== null) {
    appointmentId.value = options.appointmentId
  }
  if (!appointmentId.value) {
    initError.value = '缺少预约 ID'
    return
  }
  loadAppointment()
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
  padding: 16rpx 0;
  border-bottom: 2rpx solid #f3f4f6;
}

.info-row:last-child {
  border-bottom: none;
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

.status-tag {
  display: inline-block;
  padding: 6rpx 18rpx;
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

.form-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 0;
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