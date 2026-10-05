<template>
  <view class="page">
    <view class="header">
      <view class="title">管理仪表盘</view>
      <view class="subtitle">预约系统运行概览</view>
    </view>

    <!-- 加载 -->
    <view v-if="loading" class="state">
      <text>加载中...</text>
    </view>

    <!-- 无权限 -->
    <view v-else-if="permissionDenied" class="state error">
      <text>无权限访问</text>
    </view>

    <!-- 错误（全部接口都失败） -->
    <view v-else-if="errorMessage" class="state error">
      <text>{{ errorMessage }}</text>
      <button class="retry-btn" @click="loadAll">重新加载</button>
    </view>

    <!-- 正常状态 -->
    <view v-else>
      <!-- 部分失败软提示（出现在 stats 卡片之前） -->
      <view v-if="failureHint" class="warning-banner">
        <text class="warning-text">{{ failureHint }}</text>
        <button class="retry-btn-small" @click="loadAll">重新加载</button>
      </view>

      <!-- 4 个核心计数：用户 / 员工 / 服务 / 预约 -->
      <view class="stats-grid">
        <view class="stat-card">
          <view class="stat-label">用户总数</view>
          <view class="stat-value">{{ stats.userCount }}</view>
        </view>
        <view class="stat-card">
          <view class="stat-label">员工总数</view>
          <view class="stat-value">{{ stats.staffCount }}</view>
        </view>
        <view class="stat-card">
          <view class="stat-label">服务总数</view>
          <view class="stat-value">{{ stats.serviceCount }}</view>
        </view>
        <view class="stat-card">
          <view class="stat-label">预约总数</view>
          <view class="stat-value">{{ stats.appointmentCount }}</view>
        </view>
      </view>

      <!-- 今日 / 未来 预约高亮 -->
      <view class="highlight-row">
        <view class="highlight-card highlight-today">
          <view class="highlight-label">今日预约</view>
          <view class="highlight-value">{{ stats.todayCount }}</view>
        </view>
        <view class="highlight-card highlight-upcoming">
          <view class="highlight-label">未来预约</view>
          <view class="highlight-value">{{ stats.upcomingCount }}</view>
        </view>
      </view>

      <!-- 状态分布：4 条进度条 + 数量 -->
      <view class="card">
        <view class="card-title">预约状态分布</view>
        <view v-if="stats.appointmentCount === 0" class="empty-row">
          暂无预约数据
        </view>
        <view v-else class="status-list">
          <view
            v-for="(item, i) in statusBars"
            :key="i"
            class="status-row"
          >
            <text class="status-name">{{ item.label }}</text>
            <view class="status-bar-wrap">
              <view
                :class="['status-bar', item.cls]"
                :style="{ width: item.pct + '%' }"
              ></view>
            </view>
            <text class="status-count">{{ item.count }}</text>
          </view>
        </view>
      </view>

      <!-- 最近 7 天趋势 -->
      <view class="card">
        <view class="card-title">最近 7 天预约趋势</view>
        <view class="trend-area">
          <view
            v-for="(d, i) in stats.weeklyTrend"
            :key="i"
            class="trend-day"
          >
            <view class="trend-bar-wrap">
              <view
                class="trend-bar"
                :style="{ height: barHeight(d.count) + 'rpx' }"
              >
                <text v-if="d.count > 0" class="trend-count">{{ d.count }}</text>
              </view>
            </view>
            <text class="trend-label">{{ d.label }}</text>
          </view>
        </view>
      </view>

      <!-- 底部说明：最后加载时间 -->
      <view class="footer-tip">
        数据基于当前登录管理员可访问的全部接口实时聚合
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getAllAppointments } from '@/api/appointment.js'
import { getAllUsers } from '@/api/user.js'
import { getServices } from '@/api/service.js'
import { getStaffList } from '@/api/staff.js'
import {
  isLoggedIn,
  isAdmin,
  logout as authLogout
} from '@/utils/auth.js'

// ==================== 状态 ====================

const stats = ref(null)
const loading = ref(false)
const errorMessage = ref('')
const permissionDenied = ref(false)
const failedEndpoints = ref([])

// ==================== 纯函数：buildDashboardStats ====================

/**
 * 把原始 4 个列表汇总成仪表盘需要的所有统计指标。
 * 入参都做容错：非数组视为空数组。
 * 日期相关计算全部基于浏览器本地时区，避免 toISOString 引起的跨天。
 */
function buildDashboardStats({ appointments, users, services, staffs } = {}) {
  const apptList = Array.isArray(appointments) ? appointments : []
  const userList = Array.isArray(users) ? users : []
  const serviceList = Array.isArray(services) ? services : []
  const staffList = Array.isArray(staffs) ? staffs : []

  // 1) 状态计数
  let pendingCount = 0
  let confirmedCount = 0
  let cancelledCount = 0
  let completedCount = 0
  for (const a of apptList) {
    const s = a && a.status
    if (s === 'PENDING') pendingCount++
    else if (s === 'CONFIRMED') confirmedCount++
    else if (s === 'CANCELLED') cancelledCount++
    else if (s === 'COMPLETED') completedCount++
  }

  // 2) 今日 / 未来
  const now = new Date()
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const tomorrowStart = new Date(todayStart.getTime() + 24 * 3600 * 1000)
  const nowMs = now.getTime()

  let todayCount = 0
  let upcomingCount = 0
  for (const a of apptList) {
    const t = a && a.appointmentTime
    if (!t) continue
    const d = new Date(t) // "YYYY-MM-DDTHH:mm:ss" 字符串会被解析为本地时间
    if (Number.isNaN(d.getTime())) continue
    const ms = d.getTime()
    // 今日：[todayStart, tomorrowStart)
    if (ms >= todayStart.getTime() && ms < tomorrowStart.getTime()) {
      todayCount++
    }
    // 未来：appointmentTime > 当前时间 且 status !== CANCELLED
    if (ms > nowMs && a.status !== 'CANCELLED') {
      upcomingCount++
    }
  }

  // 3) 最近 7 天趋势（按本地日期，从 6 天前到今天）
  const weeklyTrend = []
  for (let i = 6; i >= 0; i--) {
    const dayStart = new Date(todayStart.getTime() - i * 24 * 3600 * 1000)
    const dayEnd = new Date(dayStart.getTime() + 24 * 3600 * 1000)
    let count = 0
    for (const a of apptList) {
      const t = a && a.appointmentTime
      if (!t) continue
      const d = new Date(t)
      if (Number.isNaN(d.getTime())) continue
      const ms = d.getTime()
      if (ms >= dayStart.getTime() && ms < dayEnd.getTime()) {
        count++
      }
    }
    const m = dayStart.getMonth() + 1
    const dd = dayStart.getDate()
    const label = `${m}-${dd < 10 ? '0' + dd : dd}`
    weeklyTrend.push({
      date: `${dayStart.getFullYear()}-${m < 10 ? '0' + m : m}-${dd < 10 ? '0' + dd : dd}`,
      label,
      count
    })
  }

  return {
    userCount: userList.length,
    staffCount: staffList.length,
    serviceCount: serviceList.length,
    appointmentCount: apptList.length,
    pendingCount,
    confirmedCount,
    cancelledCount,
    completedCount,
    todayCount,
    upcomingCount,
    weeklyTrend
  }
}

// ==================== 计算属性 ====================

const statusBars = computed(() => {
  if (!stats.value) return []
  const total = Math.max(1, stats.value.appointmentCount)
  return [
    {
      label: '待确认',
      count: stats.value.pendingCount,
      pct: Math.round((stats.value.pendingCount / total) * 100),
      cls: 'bar-pending'
    },
    {
      label: '已确认',
      count: stats.value.confirmedCount,
      pct: Math.round((stats.value.confirmedCount / total) * 100),
      cls: 'bar-confirmed'
    },
    {
      label: '已取消',
      count: stats.value.cancelledCount,
      pct: Math.round((stats.value.cancelledCount / total) * 100),
      cls: 'bar-cancelled'
    },
    {
      label: '已完成',
      count: stats.value.completedCount,
      pct: Math.round((stats.value.completedCount / total) * 100),
      cls: 'bar-completed'
    }
  ]
})

const trendMax = computed(() => {
  if (!stats.value || !Array.isArray(stats.value.weeklyTrend)) return 1
  const counts = stats.value.weeklyTrend.map(d => d.count)
  return Math.max(1, ...counts)
})

const failureHint = computed(() => {
  if (failedEndpoints.value.length === 0) return ''
  if (failedEndpoints.value.length >= 4) return '' // 走 errorMessage 分支
  return `部分数据加载失败（${failedEndpoints.value.join(' / ')}），对应数字可能为 0`
})

// 动态计算柱状图高度：0 显示小刻度，正比于 max
function barHeight(count) {
  if (!count || count <= 0) return 4
  const ratio = count / trendMax.value
  return Math.max(8, Math.round(ratio * 240))
}

// ==================== 加载数据 ====================

async function loadAll() {
  if (permissionDenied.value) return

  loading.value = true
  errorMessage.value = ''
  failedEndpoints.value = []

  try {
    // 并发拉取：每个接口单独 catch，失败时记入 failedEndpoints 并返回空数组
    const [appointments, users, services, staffs] = await Promise.all([
      getAllAppointments().catch((e) => {
        console.warn('[仪表盘] 加载预约失败：', e && e.message)
        failedEndpoints.value.push('appointments')
        return []
      }),
      getAllUsers().catch((e) => {
        console.warn('[仪表盘] 加载用户失败：', e && e.message)
        failedEndpoints.value.push('users')
        return []
      }),
      getServices().catch((e) => {
        console.warn('[仪表盘] 加载服务失败：', e && e.message)
        failedEndpoints.value.push('services')
        return []
      }),
      getStaffList().catch((e) => {
        console.warn('[仪表盘] 加载员工失败：', e && e.message)
        failedEndpoints.value.push('staffs')
        return []
      })
    ])

    // 四个接口全失败才走 errorMessage
    if (failedEndpoints.value.length >= 4) {
      stats.value = null
      errorMessage.value = '仪表盘数据加载失败，请稍后重试'
    } else {
      stats.value = buildDashboardStats({ appointments, users, services, staffs })
    }
  } catch (e) {
    // Promise.all 自身抛错的极端情况
    console.error('[仪表盘] 加载异常：', e)
    stats.value = null
    errorMessage.value = e?.message || '仪表盘数据加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
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

/* ==================== 通用卡片 ==================== */

.card {
  background: #fff;
  border-radius: 20rpx;
  padding: 28rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.05);
}

.card-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #111827;
  margin-bottom: 24rpx;
}

/* ==================== 4 格核心计数 ==================== */

.stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20rpx;
  margin-bottom: 24rpx;
}

.stat-card {
  background: #fff;
  border-radius: 20rpx;
  padding: 28rpx;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.05);
}

.stat-label {
  font-size: 26rpx;
  color: #6b7280;
  margin-bottom: 12rpx;
}

.stat-value {
  font-size: 56rpx;
  font-weight: 700;
  color: #111827;
  line-height: 1.2;
}

/* ==================== 今日 / 未来 高亮 ==================== */

.highlight-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20rpx;
  margin-bottom: 24rpx;
}

.highlight-card {
  border-radius: 20rpx;
  padding: 32rpx 28rpx;
  color: #fff;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.08);
}

.highlight-today {
  background: linear-gradient(135deg, #3b82f6 0%, #60a5fa 100%);
}

.highlight-upcoming {
  background: linear-gradient(135deg, #10b981 0%, #34d399 100%);
}

.highlight-label {
  font-size: 26rpx;
  opacity: 0.9;
  margin-bottom: 12rpx;
}

.highlight-value {
  font-size: 64rpx;
  font-weight: 700;
  line-height: 1.2;
}

/* ==================== 状态分布 ==================== */

.status-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.status-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.status-name {
  flex-shrink: 0;
  width: 96rpx;
  font-size: 26rpx;
  color: #374151;
}

.status-bar-wrap {
  flex: 1;
  height: 20rpx;
  background: #f3f4f6;
  border-radius: 999rpx;
  overflow: hidden;
}

.status-bar {
  height: 100%;
  border-radius: 999rpx;
  transition: width 0.2s ease;
}

.bar-pending {
  background-color: #3b82f6;
}

.bar-confirmed {
  background-color: #10b981;
}

.bar-cancelled {
  background-color: #9ca3af;
}

.bar-completed {
  background-color: #8b5cf6;
}

.status-count {
  flex-shrink: 0;
  width: 64rpx;
  text-align: right;
  font-size: 26rpx;
  font-weight: 600;
  color: #111827;
}

.empty-row {
  font-size: 26rpx;
  color: #9ca3af;
  text-align: center;
  padding: 24rpx 0;
}

/* ==================== 最近 7 天趋势 ==================== */

.trend-area {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12rpx;
  height: 320rpx;
  padding-top: 16rpx;
}

.trend-day {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  height: 100%;
}

.trend-bar-wrap {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.trend-bar {
  width: 80%;
  min-height: 4rpx;
  background: linear-gradient(180deg, #60a5fa 0%, #3b82f6 100%);
  border-radius: 8rpx 8rpx 0 0;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 6rpx;
  box-sizing: border-box;
}

.trend-count {
  font-size: 20rpx;
  color: #fff;
  font-weight: 600;
}

.trend-label {
  margin-top: 12rpx;
  font-size: 22rpx;
  color: #6b7280;
}

/* ==================== 软提示 / 状态块 ==================== */

.warning-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff7ed;
  border: 2rpx solid #fed7aa;
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  margin-bottom: 24rpx;
}

.warning-text {
  flex: 1;
  font-size: 26rpx;
  color: #9a3412;
  margin-right: 16rpx;
}

.retry-btn-small {
  flex-shrink: 0;
  padding: 0 24rpx;
  height: 56rpx;
  line-height: 56rpx;
  font-size: 24rpx;
  background: #f97316;
  color: #fff;
  border-radius: 999rpx;
  border: none;
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

.footer-tip {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #9ca3af;
  text-align: center;
  padding: 16rpx 0;
}
</style>
