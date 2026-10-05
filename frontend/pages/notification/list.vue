<template>
  <view class="page">
    <view class="header">
      <view class="title-row">
        <view class="title">消息通知</view>
        <view
          v-if="notifications.length > 0"
          class="mark-all"
          :class="{ disabled: markingAll }"
          @click="onMarkAllRead"
        >
          {{ markingAll ? '处理中...' : '全部已读' }}
        </view>
      </view>
      <view class="subtitle">查看预约提醒与系统消息</view>
    </view>

    <view v-if="loading" class="state">
      <text>加载中...</text>
    </view>

    <view v-else-if="errorMessage" class="state error">
      <text>{{ errorMessage }}</text>
      <button class="retry-btn" @click="loadAll">重新加载</button>
    </view>

    <view v-else-if="notifications.length === 0" class="state">
      <text class="empty-text">暂无消息</text>
    </view>

    <view v-else class="notification-list">
      <view
        v-for="item in notifications"
        :key="item.id"
        :class="['notification-card', { unread: !item.isRead }]"
        @click="onItemClick(item)"
      >
        <view class="card-top">
          <view class="card-title">
            <text v-if="!item.isRead" class="unread-dot">·</text>
            {{ item.title || '通知' }}
          </view>
          <view class="card-time">{{ formatDateTime(item.createdAt) }}</view>
        </view>
        <view class="card-content">{{ item.content || '' }}</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import {
  listNotifications,
  markNotificationRead
} from '@/api/notification.js'

const notifications = ref([])
const loading = ref(false)
const errorMessage = ref('')
const markingAll = ref(false)
const markingId = ref(null)

function pad(n) {
  return String(n).padStart(2, '0')
}

function formatDateTime(value) {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function loadAll() {
  loading.value = true
  errorMessage.value = ''
  try {
    const list = await listNotifications()
    notifications.value = Array.isArray(list) ? list : []
  } catch (error) {
    console.error('加载通知失败：', error)
    notifications.value = []
    errorMessage.value = error && error.message ? error.message : '通知加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function onItemClick(item) {
  if (!item || !item.id) return
  if (item.isRead) return
  markingId.value = item.id
  try {
    await markNotificationRead(item.id)
    item.isRead = true
  } catch (error) {
    const msg = error && error.message ? error.message : '标记失败，请稍后重试'
    uni.showToast({ title: msg, icon: 'none' })
  } finally {
    markingId.value = null
  }
}

async function onMarkAllRead() {
  if (markingAll.value) return
  markingAll.value = true
  try {
    // 拉取接口：PUT /notifications/read-all 由后端返回 { updated: N }
    const { markAllNotificationsRead } = await import('@/api/notification.js')
    const r = await markAllNotificationsRead()
    notifications.value = notifications.value.map((n) => ({ ...n, isRead: true }))
    uni.showToast({
      title: typeof r?.updated === 'number' ? `已读 ${r.updated} 条` : '已全部已读',
      icon: 'success'
    })
  } catch (error) {
    const msg = error && error.message ? error.message : '操作失败，请稍后重试'
    uni.showToast({ title: msg, icon: 'none' })
  } finally {
    markingAll.value = false
  }
}

onLoad(() => {
  loadAll()
})

onShow(() => {
  // 回到页面时刷新，便于反映其它入口造成的变化（例如首页已读后再次进入）
  if (!loading.value) {
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
  margin-bottom: 32rpx;
}

.title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.title {
  font-size: 44rpx;
  font-weight: 700;
  color: #222;
}

.mark-all {
  padding: 12rpx 24rpx;
  font-size: 26rpx;
  color: #3b82f6;
  background: #eff6ff;
  border-radius: 999rpx;
  font-weight: 500;
}

.mark-all.disabled {
  color: #93c5fd;
  background: #f3f4f6;
}

.subtitle {
  margin-top: 12rpx;
  font-size: 26rpx;
  color: #888;
}

.notification-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.notification-card {
  background: #fff;
  border-radius: 20rpx;
  padding: 28rpx;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.05);
  border-left: 8rpx solid transparent;
}

.notification-card.unread {
  border-left-color: #3b82f6;
  background: #f8faff;
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 16rpx;
  border-bottom: 2rpx solid #f3f4f6;
}

.card-title {
  flex: 1;
  min-width: 0;
  font-size: 30rpx;
  font-weight: 600;
  color: #222;
  display: flex;
  align-items: center;
}

.unread-dot {
  color: #ef4444;
  font-size: 40rpx;
  line-height: 30rpx;
  margin-right: 10rpx;
  font-weight: 700;
}

.card-time {
  flex-shrink: 0;
  margin-left: 16rpx;
  font-size: 22rpx;
  color: #9ca3af;
}

.card-content {
  margin-top: 16rpx;
  font-size: 28rpx;
  color: #4b5563;
  line-height: 1.6;
  white-space: pre-wrap;
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

.empty-text {
  color: #9ca3af;
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