<template>
  <view class="page">
    <view class="header">
      <view class="title-row">
        <view class="title-left">
          <view class="title">服务管理</view>
          <view class="subtitle">维护系统中的服务列表</view>
        </view>
        <button
          class="header-btn"
          :disabled="saving || operatingId !== null"
          @click="openCreate"
        >
          + 新增服务
        </button>
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
    <view v-else-if="services.length === 0" class="state">
      <text>暂无服务，点击右上角新增</text>
    </view>

    <!-- 列表 -->
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
          <button
            class="action-btn action-edit"
            :disabled="saving || operatingId !== null"
            @click="openEdit(item)"
          >
            编辑
          </button>
          <button
            class="action-btn action-delete"
            :disabled="saving || operatingId !== null"
            @click="onDelete(item)"
          >
            {{ operatingId === item.id ? '删除中...' : '删除' }}
          </button>
        </view>
      </view>
    </view>

    <!-- 新增 / 编辑弹层 -->
    <view v-if="formVisible" class="modal-mask" @click.self="closeForm">
      <view class="modal">
        <view class="modal-title">{{ formMode === 'edit' ? '编辑服务' : '新增服务' }}</view>

        <view class="form-row">
          <text class="form-label">服务名称</text>
          <input
            class="form-input"
            type="text"
            v-model="form.name"
            placeholder="请输入服务名称"
            maxlength="50"
            :disabled="saving"
          />
        </view>

        <view class="form-row">
          <text class="form-label">时长（分钟）</text>
          <input
            class="form-input"
            type="number"
            v-model="form.duration"
            placeholder="例如 30 / 60"
            :disabled="saving"
          />
        </view>

        <view class="form-row">
          <text class="form-label">价格</text>
          <input
            class="form-input"
            type="digit"
            v-model="form.price"
            placeholder="例如 99.0"
            :disabled="saving"
          />
        </view>

        <view v-if="formError" class="form-error">{{ formError }}</view>

        <view class="modal-actions">
          <button
            class="modal-btn modal-cancel"
            :disabled="saving"
            @click="closeForm"
          >
            取消
          </button>
          <button
            class="modal-btn modal-confirm"
            :disabled="saving"
            @click="onSubmit"
          >
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import {
  getServices,
  createService,
  updateService,
  deleteService
} from '@/api/service.js'
import { isLoggedIn, isAdmin, logout as authLogout } from '@/utils/auth.js'

// ==================== 列表状态 ====================

const services = ref([])
const loading = ref(false)
const errorMessage = ref('')
const operatingId = ref(null)
const permissionDenied = ref(false)

// ==================== 表单状态 ====================

const formVisible = ref(false)
const formMode = ref('create') // 'create' | 'edit'
const saving = ref(false)
const formError = ref('')
const form = reactive({
  id: null,
  name: '',
  duration: '',
  price: ''
})

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

function resetForm() {
  form.id = null
  form.name = ''
  form.duration = ''
  form.price = ''
  formError.value = ''
}

function isBusy() {
  // 防重复点击：保存中 / 任意删除中 / 表单未关闭时不允许再触发列表内操作
  return saving.value || operatingId.value !== null || formVisible.value
}

// ==================== 表单操作 ====================

function openCreate() {
  if (isBusy()) return
  resetForm()
  formMode.value = 'create'
  formVisible.value = true
}

function openEdit(item) {
  if (!item || !item.id) return
  if (isBusy()) return
  resetForm()
  form.id = item.id
  form.name = item.name || ''
  form.duration = (item.duration === null || item.duration === undefined) ? '' : String(item.duration)
  form.price = (item.price === null || item.price === undefined) ? '' : String(item.price)
  formMode.value = 'edit'
  formVisible.value = true
}

function closeForm() {
  if (saving.value) return
  formVisible.value = false
  resetForm()
}

function validateForm() {
  if (!form.name || !String(form.name).trim()) {
    return '请输入服务名称'
  }
  if (form.duration === '' || form.duration === null || form.duration === undefined) {
    return '请输入时长'
  }
  const duration = Number(form.duration)
  if (!Number.isFinite(duration) || duration <= 0 || !Number.isInteger(duration)) {
    return '时长必须是正整数（分钟）'
  }
  if (form.price === '' || form.price === null || form.price === undefined) {
    return '请输入价格'
  }
  const price = Number(form.price)
  if (!Number.isFinite(price) || price < 0) {
    return '价格必须是非负数字'
  }
  return ''
}

async function onSubmit() {
  const err = validateForm()
  if (err) {
    formError.value = err
    return
  }
  formError.value = ''
  saving.value = true
  try {
    const payload = {
      name: String(form.name).trim(),
      duration: Number(form.duration),
      price: Number(form.price)
    }
    if (formMode.value === 'create') {
      await createService(payload)
      uni.showToast({ title: '新增成功', icon: 'success' })
    } else {
      await updateService(form.id, payload)
      uni.showToast({ title: '修改成功', icon: 'success' })
    }
    formVisible.value = false
    resetForm()
    await loadAll()
  } catch (e) {
    const msg = e && e.message ? e.message : '保存失败，请稍后重试'
    formError.value = msg
    uni.showToast({ title: msg, icon: 'none' })
  } finally {
    saving.value = false
  }
}

// ==================== 列表操作 ====================

function onDelete(item) {
  if (!item || !item.id) return
  if (isBusy()) return

  uni.showModal({
    title: '提示',
    content: `确认删除服务「${item.name || '该'}」吗？`,
    success: async (res) => {
      if (!res.confirm) return
      if (operatingId.value !== null) return
      operatingId.value = item.id
      try {
        await deleteService(item.id)
        uni.showToast({ title: '删除成功', icon: 'success' })
        await loadAll()
      } catch (e) {
        const msg = e && e.message ? e.message : '删除失败，请稍后重试'
        uni.showToast({ title: msg, icon: 'none' })
      } finally {
        operatingId.value = null
      }
    }
  })
}

async function loadAll() {
  if (permissionDenied.value) return

  loading.value = true
  errorMessage.value = ''

  try {
    const list = await getServices()
    const arr = Array.isArray(list) ? list : []
    // 按 id 升序展示：新增项追加在最后，符合"时间顺序"直觉
    arr.sort((a, b) => (a.id || 0) - (b.id || 0))
    services.value = arr
  } catch (e) {
    console.error('加载服务列表失败：', e)
    services.value = []
    errorMessage.value = e?.message || '服务加载失败，请稍后重试'
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
    errorMessage.value = ''
    services.value = []
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

.title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.title-left {
  flex: 1;
  min-width: 0;
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

.header-btn {
  flex-shrink: 0;
  padding: 0 28rpx;
  font-size: 26rpx;
  line-height: 60rpx;
  background: #3b82f6;
  color: #fff;
  border: none;
  border-radius: 999rpx;
  font-weight: 500;
}

.header-btn[disabled] {
  background: #cbd5e1;
  color: #fff;
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
  justify-content: flex-end;
  gap: 16rpx;
}

.action-btn {
  padding: 0 28rpx;
  font-size: 26rpx;
  line-height: 64rpx;
  background: #fff;
  border-radius: 12rpx;
  font-weight: 500;
}

.action-edit {
  color: #1d4ed8;
  border: 2rpx solid #bfdbfe;
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

/* ==================== 弹层 ==================== */

.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 99;
}

.modal {
  width: 86vw;
  max-width: 640rpx;
  background: #fff;
  border-radius: 24rpx;
  padding: 36rpx 32rpx;
  box-sizing: border-box;
}

.modal-title {
  font-size: 34rpx;
  font-weight: 600;
  color: #222;
  margin-bottom: 28rpx;
}

.form-row {
  display: flex;
  flex-direction: column;
  margin-bottom: 24rpx;
}

.form-label {
  font-size: 26rpx;
  color: #6b7280;
  margin-bottom: 10rpx;
}

.form-input {
  width: 100%;
  height: 80rpx;
  padding: 0 20rpx;
  font-size: 28rpx;
  background: #f9fafb;
  border: 2rpx solid #e5e7eb;
  border-radius: 12rpx;
  box-sizing: border-box;
  color: #111827;
}

.form-input[disabled] {
  background: #f3f4f6;
  color: #9ca3af;
}

.form-error {
  font-size: 24rpx;
  color: #ef4444;
  margin-bottom: 16rpx;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 16rpx;
  margin-top: 16rpx;
}

.modal-btn {
  padding: 0 36rpx;
  font-size: 28rpx;
  line-height: 72rpx;
  border-radius: 12rpx;
  font-weight: 500;
}

.modal-cancel {
  background: #fff;
  color: #6b7280;
  border: 2rpx solid #e5e7eb;
}

.modal-confirm {
  background: #3b82f6;
  color: #fff;
  border: none;
}

.modal-btn[disabled] {
  opacity: 0.6;
  color: #fff;
}
</style>
