<template>
  <view class="page">
    <view class="header">
      <view class="title">服务列表</view>
      <view class="subtitle">选择你需要的预约服务</view>
    </view>

    <view v-if="loading" class="state">
      <text>加载中...</text>
    </view>

    <view v-else-if="errorMessage" class="state error">
      <text>{{ errorMessage }}</text>
      <button class="retry-btn" @click="loadServices">重新加载</button>
    </view>

    <view v-else-if="services.length === 0" class="state">
      <text>暂无可预约服务</text>
    </view>

    <view v-else class="service-list">
      <view
        v-for="service in services"
        :key="service.id"
        class="service-card"
      >
        <view class="service-info">
          <view class="service-name">{{ service.name }}</view>

          <view class="service-meta">
            <text>时长：{{ service.duration }} 分钟</text>
          </view>
        </view>

        <view class="service-price">
          <text class="price-symbol">¥</text>
          <text class="price-value">{{ formatPrice(service.price) }}</text>
        </view>

        <view class="service-action">
          <button
            class="select-btn"
            @click="onSelectService(service)"
          >
            选择服务
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getServices } from '@/api/service.js'

const services = ref([])
const loading = ref(false)
const errorMessage = ref('')

function formatPrice(price) {
  if (price === null || price === undefined || price === '') {
    return '0.00'
  }

  const number = Number(price)

  if (Number.isNaN(number)) {
    return price
  }

  return number.toFixed(2)
}

async function loadServices() {
  loading.value = true
  errorMessage.value = ''

  try {
    const response = await getServices()

    services.value = Array.isArray(response) ? response : []
  } catch (error) {
    console.error('加载服务列表失败：', error)
    errorMessage.value = error?.message || '服务加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function onSelectService(service) {
  if (!service || service.id === undefined || service.id === null) {
    uni.showToast({
      title: '服务信息异常',
      icon: 'none'
    })
    return
  }

  uni.navigateTo({
    url: `/pages/staff/list?serviceId=${service.id}&serviceName=${encodeURIComponent(service.name || '')}`
  })
}

onLoad(() => {
  loadServices()
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

.service-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.service-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx;
  background: #fff;
  border-radius: 20rpx;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.05);
}

.service-info {
  flex: 1;
  min-width: 0;
}

.service-name {
  font-size: 32rpx;
  font-weight: 600;
  color: #222;
}

.service-meta {
  margin-top: 14rpx;
  font-size: 24rpx;
  color: #888;
}

.service-price {
  margin-left: 24rpx;
  white-space: nowrap;
  color: #222;
}

.price-symbol {
  font-size: 24rpx;
  font-weight: 600;
}

.price-value {
  font-size: 36rpx;
  font-weight: 700;
}

.service-action {
  margin-left: 24rpx;
}

.select-btn {
  padding: 0 28rpx;
  font-size: 26rpx;
  line-height: 64rpx;
  background: #3b82f6;
  color: #fff;
  border: none;
  border-radius: 12rpx;
  font-weight: 500;
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