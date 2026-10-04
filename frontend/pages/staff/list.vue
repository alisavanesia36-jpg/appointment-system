<template>
  <view class="page">
    <view class="header">
      <view class="title">选择员工</view>
      <view v-if="serviceName" class="subtitle">服务：{{ serviceName }}</view>
    </view>

    <view v-if="loading" class="state">
      <text>加载中...</text>
    </view>

    <view v-else-if="errorMessage" class="state error">
      <text>{{ errorMessage }}</text>
      <button class="retry-btn" @click="loadStaff">重新加载</button>
    </view>

    <view v-else-if="staffs.length === 0" class="state">
      <text>暂无支持该服务的员工</text>
    </view>

    <view v-else class="staff-list">
      <view
        v-for="staff in staffs"
        :key="staff.id"
        class="staff-card"
        @click="onSelectStaff(staff)"
      >
        <view class="staff-avatar">
          <text class="avatar-text">{{ avatarText(staff.name) }}</text>
        </view>

        <view class="staff-info">
          <view class="staff-name">{{ staff.name || '-' }}</view>
          <view class="staff-phone">{{ staff.phone || '手机号未填写' }}</view>
        </view>

        <view class="staff-arrow">›</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getStaffList, getStaffServicesByService } from '@/api/staff.js'

const serviceId = ref(null)
const serviceName = ref('')

const staffs = ref([])
const loading = ref(false)
const errorMessage = ref('')

function avatarText(name) {
  if (!name) return '员'
  return String(name).slice(0, 1)
}

function extractStaffIds(mappings) {
  const ids = []
  if (!Array.isArray(mappings)) return ids
  mappings.forEach((m) => {
    if (m && m.id && m.id.staffId !== undefined && m.id.staffId !== null) {
      ids.push(m.id.staffId)
    }
  })
  return ids
}

async function loadStaff() {
  if (serviceId.value === null || serviceId.value === undefined) {
    errorMessage.value = '服务信息缺失'
    return
  }

  loading.value = true
  errorMessage.value = ''
  staffs.value = []

  try {
    const mappings = await getStaffServicesByService(serviceId.value)

    const staffIds = extractStaffIds(mappings)

    if (staffIds.length === 0) {
      staffs.value = []
      return
    }

    const allStaffs = await getStaffList()

    const list = Array.isArray(allStaffs) ? allStaffs : []

    staffs.value = list.filter((s) => s && staffIds.includes(s.id))
  } catch (error) {
    console.error('加载员工列表失败：', error)
    errorMessage.value = error?.message || '员工加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function onSelectStaff(staff) {
  if (!staff || staff.id === undefined || staff.id === null) {
    uni.showToast({
      title: '员工信息异常',
      icon: 'none'
    })
    return
  }

  // 当前阶段：保留服务上下文，跳转到预约确认页
  uni.navigateTo({
    url: `/pages/appointment/create?serviceId=${serviceId.value}&serviceName=${encodeURIComponent(serviceName.value || '')}&staffId=${staff.id}&staffName=${encodeURIComponent(staff.name || '')}`
  })
}

onLoad((options) => {
  if (options && options.serviceId !== undefined) {
    serviceId.value = options.serviceId
  }
  if (options && options.serviceName) {
    try {
      serviceName.value = decodeURIComponent(options.serviceName)
    } catch (e) {
      serviceName.value = options.serviceName
    }
  }

  loadStaff()
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

.staff-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.staff-card {
  display: flex;
  align-items: center;
  padding: 28rpx;
  background: #fff;
  border-radius: 20rpx;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.05);
}

.staff-card:active {
  opacity: 0.85;
  transform: scale(0.99);
}

.staff-avatar {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background: #eff6ff;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20rpx;
  flex-shrink: 0;
}

.avatar-text {
  font-size: 32rpx;
  font-weight: 600;
  color: #3b82f6;
}

.staff-info {
  flex: 1;
  min-width: 0;
}

.staff-name {
  font-size: 32rpx;
  font-weight: 600;
  color: #222;
}

.staff-phone {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #888;
}

.staff-arrow {
  font-size: 44rpx;
  font-weight: 300;
  color: #c0c4cc;
  margin-left: 12rpx;
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

.error {
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