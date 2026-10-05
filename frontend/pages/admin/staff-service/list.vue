<template>
  <view class="page">
    <view class="header">
      <view class="title">员工-服务分配</view>
      <view class="subtitle">配置每位员工可提供的服务</view>
    </view>

    <!-- 无权限 -->
    <view v-if="permissionDenied" class="state error">
      <text>无权限访问</text>
    </view>

    <!-- 页面初始化加载中 -->
    <view v-else-if="initLoading" class="state">
      <text>加载中...</text>
    </view>

    <!-- 员工列表加载失败 -->
    <view v-else-if="initError" class="state error">
      <text>{{ initError }}</text>
      <button class="retry-btn" @click="initAll">重新加载</button>
    </view>

    <!-- 没有员工 -->
    <view v-else-if="staffs.length === 0" class="state">
      <text>暂无员工，请先在员工管理中新增员工。</text>
    </view>

    <!-- 没有服务 -->
    <view v-else-if="services.length === 0" class="state">
      <text>暂无服务，请先在服务管理中新增服务。</text>
    </view>

    <!-- 正常状态 -->
    <view v-else>
      <!-- 员工选择器 -->
      <view class="picker-card">
        <view class="picker-label">员工</view>
        <view class="picker-row">
          <picker
            mode="selector"
            :range="staffs"
            range-key="name"
            :value="staffIndex"
            @change="onStaffChange"
          >
            <view class="picker-value">
              <text class="picker-name">{{ currentStaff ? currentStaff.name : '请选择' }}</text>
              <text class="picker-arrow">▼</text>
            </view>
          </picker>
          <view v-if="currentStaff" class="picker-id">#{{ currentStaff.id }}</view>
        </view>
      </view>

      <!-- 当前员工关系加载中 -->
      <view v-if="relationsLoading" class="state small-state">
        <text>加载服务关系中...</text>
      </view>

      <!-- 关系加载失败 -->
      <view v-else-if="relationsError" class="state error small-state">
        <text>{{ relationsError }}</text>
        <button class="retry-btn" @click="loadRelations">重新加载</button>
      </view>

      <!-- 该员工支持的服务 -->
      <view v-else class="service-list">
        <view
          v-for="item in services"
          :key="item.id"
          class="service-card"
        >
          <view class="card-top">
            <view class="service-name">{{ item.name || '未命名服务' }}</view>
            <view class="id-tag">#{{ item.id }}</view>
          </view>

          <view class="card-mid">
            <view class="info-line">
              <text class="info-label">时长</text>
              <text class="info-value">{{ formatDuration(item.duration) }}</text>
            </view>
            <view class="info-line">
              <text class="info-label">价格</text>
              <text class="info-value">{{ formatPrice(item.price) }}</text>
            </view>
          </view>

          <view class="card-bottom">
            <view v-if="isAssigned(item.id)" class="status-pill status-assigned">
              <text>已分配</text>
            </view>
            <view v-else class="status-pill status-unassigned">
              <text>未分配</text>
            </view>

            <button
              v-if="isAssigned(item.id)"
              class="action-btn action-unassign"
              :disabled="isOperating(currentStaff.id, item.id)"
              @click="onUnassign(item)"
            >
              {{ isOperating(currentStaff.id, item.id) ? '处理中...' : '取消分配' }}
            </button>
            <button
              v-else
              class="action-btn action-assign"
              :disabled="isOperating(currentStaff.id, item.id)"
              @click="onAssign(item)"
            >
              {{ isOperating(currentStaff.id, item.id) ? '处理中...' : '分配' }}
            </button>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import {
  getStaffList,
  getStaffServicesByStaff,
  addStaffService,
  deleteStaffService
} from '@/api/staff.js'
import { getServices } from '@/api/service.js'
import { isLoggedIn, isAdmin, logout as authLogout } from '@/utils/auth.js'

// ==================== 全局状态 ====================

const staffs = ref([])
const services = ref([])
const staffIndex = ref(0)
const currentStaffId = ref(null)
const assignedServiceIds = ref([]) // 当前员工的 serviceId 列表
const relationsLoading = ref(false)
const relationsError = ref('')
const initLoading = ref(false)
const initError = ref('')
const permissionDenied = ref(false)

// 操作锁：key = `${staffId}-${serviceId}`，正在请求中为 true
const operatingKeys = ref([])

function isOperating(staffId, serviceId) {
  return operatingKeys.value.includes(`${staffId}-${serviceId}`)
}

function markOperating(staffId, serviceId) {
  operatingKeys.value.push(`${staffId}-${serviceId}`)
}

function clearOperating(staffId, serviceId) {
  const k = `${staffId}-${serviceId}`
  operatingKeys.value = operatingKeys.value.filter(x => x !== k)
}

// ==================== 计算属性 ====================

const currentStaff = computed(() => {
  if (currentStaffId.value === null || currentStaffId.value === undefined) return null
  return staffs.value.find(s => s.id === currentStaffId.value) || null
})

function isAssigned(serviceId) {
  return assignedServiceIds.value.includes(serviceId)
}

// ==================== 工具方法 ====================

function formatDuration(value) {
  if (value === null || value === undefined || value === '') return '-'
  return `${value} 分钟`
}

function formatPrice(value) {
  if (value === null || value === undefined || value === '') return '-'
  const n = Number(value)
  if (!Number.isFinite(n)) return String(value)
  return `¥${n.toFixed(2)}`
}

// ==================== 加载员工 / 服务 ====================

async function initAll() {
  initError.value = ''
  initLoading.value = true

  try {
    const [staffList, serviceList] = await Promise.all([
      getStaffList(),
      getServices()
    ])

    const arrStaffs = Array.isArray(staffList) ? [...staffList] : []
    arrStaffs.sort((a, b) => (a.id || 0) - (b.id || 0))
    staffs.value = arrStaffs

    const arrServices = Array.isArray(serviceList) ? [...serviceList] : []
    arrServices.sort((a, b) => (a.id || 0) - (b.id || 0))
    services.value = arrServices

    // 默认选中第一名员工
    if (arrStaffs.length > 0) {
      staffIndex.value = 0
      currentStaffId.value = arrStaffs[0].id
      await loadRelations()
    }
  } catch (e) {
    console.error('[员工-服务分配] 初始化失败：', e)
    initError.value = e?.message || '员工列表加载失败，请稍后重试'
  } finally {
    initLoading.value = false
  }
}

// ==================== 加载当前员工的服务关系 ====================

async function loadRelations() {
  const sid = currentStaffId.value
  if (sid === null || sid === undefined) {
    assignedServiceIds.value = []
    return
  }

  relationsLoading.value = true
  relationsError.value = ''
  try {
    const list = await getStaffServicesByStaff(sid)
    // 后端返回结构：[{ id: { staffId, serviceId } }, ...]
    const ids = (Array.isArray(list) ? list : [])
      .map(m => m && m.id && m.id.serviceId)
      .filter(v => v !== null && v !== undefined)
    assignedServiceIds.value = ids
  } catch (e) {
    console.error('[员工-服务分配] 关系加载失败：', e)
    assignedServiceIds.value = []
    relationsError.value = e?.message || '员工服务关系加载失败，请稍后重试'
  } finally {
    relationsLoading.value = false
  }
}

// ==================== 切换员工 ====================

function onStaffChange(e) {
  const idx = Number(e.detail.value)
  if (Number.isNaN(idx) || idx < 0 || idx >= staffs.value.length) return
  staffIndex.value = idx
  currentStaffId.value = staffs.value[idx].id
  // 清空旧关系，重新加载新员工的关系
  assignedServiceIds.value = []
  relationsError.value = ''
  loadRelations()
}

// ==================== 分配 / 取消分配 ====================

async function onAssign(item) {
  const sid = currentStaffId.value
  if (!sid || !item || !item.id) return

  // 前端重复分配保护：已分配就不发请求
  if (isAssigned(item.id)) return
  if (isOperating(sid, item.id)) return

  markOperating(sid, item.id)
  try {
    await addStaffService(sid, item.id)
    // 更新前端状态，不重载列表
    assignedServiceIds.value = [...assignedServiceIds.value, item.id]
    uni.showToast({ title: '分配成功', icon: 'success' })
  } catch (e) {
    const msg = e?.message || '分配失败，请稍后重试'
    uni.showToast({ title: msg, icon: 'none' })
  } finally {
    clearOperating(sid, item.id)
  }
}

function onUnassign(item) {
  const sid = currentStaffId.value
  if (!sid || !item || !item.id) return
  if (!isAssigned(item.id)) return
  if (isOperating(sid, item.id)) return

  uni.showModal({
    title: '提示',
    content: '确认取消该员工的服务分配吗？',
    success: async (res) => {
      if (!res.confirm) return
      if (isOperating(sid, item.id)) return
      markOperating(sid, item.id)
      try {
        await deleteStaffService(sid, item.id)
        assignedServiceIds.value = assignedServiceIds.value.filter(x => x !== item.id)
        uni.showToast({ title: '取消分配成功', icon: 'success' })
      } catch (e) {
        const msg = e?.message || '取消分配失败，请稍后重试'
        uni.showToast({ title: msg, icon: 'none' })
      } finally {
        clearOperating(sid, item.id)
      }
    }
  })
}

// ==================== 权限校验 ====================

function guardAdmin() {
  if (!isLoggedIn()) {
    authLogout(true)
    return false
  }
  if (!isAdmin()) {
    permissionDenied.value = true
    setTimeout(() => {
      uni.reLaunch({ url: '/pages/index/index' })
    }, 1200)
    return false
  }
  return true
}

onLoad(() => {
  if (guardAdmin()) {
    initAll()
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

.picker-card {
  background: #fff;
  border-radius: 20rpx;
  padding: 24rpx 28rpx;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.05);
  margin-bottom: 24rpx;
}

.picker-label {
  font-size: 26rpx;
  color: #6b7280;
  margin-bottom: 12rpx;
}

.picker-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.picker-value {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 72rpx;
  padding: 0 20rpx;
  background: #f9fafb;
  border: 2rpx solid #e5e7eb;
  border-radius: 12rpx;
  box-sizing: border-box;
}

.picker-name {
  font-size: 30rpx;
  color: #111827;
  font-weight: 500;
}

.picker-arrow {
  font-size: 24rpx;
  color: #9ca3af;
  margin-left: 12rpx;
}

.picker-id {
  flex-shrink: 0;
  margin-left: 16rpx;
  padding: 6rpx 16rpx;
  background: #eff6ff;
  color: #1d4ed8;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 500;
}

.service-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.service-card {
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

.id-tag {
  flex-shrink: 0;
  margin-left: 16rpx;
  padding: 6rpx 16rpx;
  background: #eff6ff;
  color: #1d4ed8;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 500;
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
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  padding: 8rpx 20rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 500;
}

.status-assigned {
  background: #ecfdf5;
  color: #047857;
}

.status-unassigned {
  background: #f3f4f6;
  color: #6b7280;
}

.action-btn {
  padding: 0 28rpx;
  font-size: 26rpx;
  line-height: 64rpx;
  background: #fff;
  border-radius: 12rpx;
  font-weight: 500;
}

.action-assign {
  color: #1d4ed8;
  border: 2rpx solid #bfdbfe;
}

.action-unassign {
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

.small-state {
  padding: 60rpx 32rpx;
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