<template>
  <view class="page">
    <view class="header">
      <view class="title-row">
        <view class="title-left">
          <view class="title">用户管理</view>
          <view class="subtitle">管理系统中的用户账号</view>
        </view>
        <button
          class="header-btn"
          :disabled="saving || operatingId !== null"
          @click="openCreate"
        >
          + 新增用户
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
    <view v-else-if="users.length === 0" class="state">
      <text>暂无用户，点击右上角新增</text>
    </view>

    <!-- 列表 -->
    <view v-else class="user-list">
      <view
        v-for="item in users"
        :key="item.id"
        class="user-card"
      >
        <view class="card-top">
          <view class="user-name">{{ item.username || '未命名用户' }}</view>
          <view :class="['role-tag', roleClass(item.role)]">
            {{ roleText(item.role) }}
          </view>
        </view>

        <view class="card-mid">
          <view class="info-line">
            <text class="info-label">手机号</text>
            <text class="info-value">{{ item.phone || '-' }}</text>
          </view>
          <view class="info-line">
            <text class="info-label">用户ID</text>
            <text class="info-value">#{{ item.id }}</text>
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
          <!-- 当前登录用户自己不显示删除按钮（前端防御；后端无防护） -->
          <button
            v-if="!isSelf(item.id)"
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
        <view class="modal-title">{{ formMode === 'edit' ? '编辑用户' : '新增用户' }}</view>

        <view class="form-row">
          <text class="form-label">用户名</text>
          <input
            class="form-input"
            type="text"
            v-model="form.username"
            placeholder="请输入用户名"
            maxlength="50"
            :disabled="saving"
          />
        </view>

        <!-- 密码仅新增时显示，编辑不提供改密（前端策略） -->
        <view v-if="formMode === 'create'" class="form-row">
          <text class="form-label">密码</text>
          <input
            class="form-input"
            type="password"
            v-model="form.password"
            placeholder="请输入初始密码"
            :disabled="saving"
          />
        </view>

        <view class="form-row">
          <text class="form-label">手机号</text>
          <input
            class="form-input"
            type="number"
            v-model="form.phone"
            placeholder="例如 13800000000"
            maxlength="11"
            :disabled="saving"
          />
        </view>

        <!-- 角色：新增时只读显示 USER；编辑时为可改 picker -->
        <view v-if="formMode === 'create'" class="form-row">
          <text class="form-label">角色</text>
          <view class="static-value">USER（新建默认为 USER，需管理员请编辑后修改）</view>
        </view>
        <view v-else class="form-row">
          <text class="form-label">角色</text>
          <picker
            mode="selector"
            :range="roleOptions"
            range-key="label"
            :value="roleIndex"
            @change="onRoleChange"
          >
            <view class="picker-value">
              <text class="picker-text">{{ currentRoleLabel }}</text>
              <text class="picker-arrow">▼</text>
            </view>
          </picker>
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
import { ref, reactive, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import {
  getAllUsers,
  createUser,
  updateUser,
  deleteUser
} from '@/api/user.js'
import {
  isLoggedIn,
  isAdmin,
  logout as authLogout,
  getCurrentUser
} from '@/utils/auth.js'

// ==================== 列表状态 ====================

const users = ref([])
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
  username: '',
  password: '',
  phone: '',
  role: 'USER'
})

// ==================== 角色选项 ====================

const roleOptions = [
  { value: 'USER', label: '普通用户' },
  { value: 'ADMIN', label: '管理员' }
]

const roleIndex = ref(0)

const currentRoleLabel = computed(() => {
  const opt = roleOptions.find(o => o.value === form.role)
  return opt ? opt.label : '请选择'
})

function onRoleChange(e) {
  const idx = Number(e.detail.value)
  if (Number.isNaN(idx) || idx < 0 || idx >= roleOptions.length) return
  roleIndex.value = idx
  form.role = roleOptions[idx].value
}

// ==================== 工具方法 ====================

function roleText(role) {
  if (role === 'ADMIN') return '管理员'
  if (role === 'USER') return '普通用户'
  return role || '-'
}

function roleClass(role) {
  if (role === 'ADMIN') return 'role-admin'
  if (role === 'USER') return 'role-user'
  return 'role-default'
}

function isSelf(userId) {
  if (userId === null || userId === undefined) return false
  const me = getCurrentUser()
  if (!me || me.id === undefined || me.id === null) return false
  return Number(me.id) === Number(userId)
}

function resetForm() {
  form.id = null
  form.username = ''
  form.password = ''
  form.phone = ''
  form.role = 'USER'
  roleIndex.value = 0
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
  form.username = item.username || ''
  form.phone = item.phone || ''
  const normalizedRole = (item.role === 'ADMIN' || item.role === 'USER') ? item.role : 'USER'
  form.role = normalizedRole
  const idx = roleOptions.findIndex(o => o.value === normalizedRole)
  roleIndex.value = idx >= 0 ? idx : 0
  formMode.value = 'edit'
  formVisible.value = true
}

function closeForm() {
  if (saving.value) return
  formVisible.value = false
  resetForm()
}

function validateForm() {
  const username = String(form.username || '').trim()
  if (!username) {
    return '请输入用户名'
  }
  if (formMode.value === 'create') {
    const password = String(form.password || '')
    if (!password || !password.trim()) {
      return '请输入密码'
    }
  }
  const phone = String(form.phone || '').trim()
  if (!phone) {
    return '请输入手机号'
  }
  if (!/^1[3-9]\d{9}$/.test(phone)) {
    return '手机号格式不正确'
  }
  if (formMode.value === 'edit') {
    if (form.role !== 'USER' && form.role !== 'ADMIN') {
      return '角色必须是 USER 或 ADMIN'
    }
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
    if (formMode.value === 'create') {
      // 严格按 UserCreateDTO 组装：不携带 role（DTO 无此字段）
      const payload = {
        username: String(form.username).trim(),
        password: String(form.password),
        phone: String(form.phone).trim()
      }
      await createUser(payload)
      uni.showToast({ title: '新增成功', icon: 'success' })
    } else {
      // 编辑：按 UserAdminUpdateDTO 组装：不携带 password（前端策略：默认不改密）
      const payload = {
        username: String(form.username).trim(),
        phone: String(form.phone).trim(),
        role: form.role
      }
      await updateUser(form.id, payload)
      uni.showToast({ title: '修改成功', icon: 'success' })
    }
    formVisible.value = false
    resetForm()
    await loadAll()
  } catch (e) {
    // 透传后端 message，例如：用户名已存在、用户不存在、权限不足
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
  // 二次保护：即使按钮显示判断失效，也再次确认（后端无自杀防护）
  if (isSelf(item.id)) {
    uni.showToast({ title: '不能删除当前登录用户', icon: 'none' })
    return
  }

  uni.showModal({
    title: '提示',
    content: `确认删除用户「${item.username || '该'}」吗？`,
    success: async (res) => {
      if (!res.confirm) return
      if (operatingId.value !== null) return
      // 在异步回调里再校验一次，防止 token / 缓存状态在调用间隙变化
      if (isSelf(item.id)) {
        uni.showToast({ title: '不能删除当前登录用户', icon: 'none' })
        return
      }
      operatingId.value = item.id
      try {
        await deleteUser(item.id)
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
    const list = await getAllUsers()
    const arr = Array.isArray(list) ? [...list] : []
    // 按 id 升序展示：新增项追加在最后
    arr.sort((a, b) => (a.id || 0) - (b.id || 0))
    users.value = arr
  } catch (e) {
    console.error('加载用户列表失败：', e)
    users.value = []
    errorMessage.value = e?.message || '用户加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

// ==================== 权限校验 ====================

function guardAdmin() {
  if (!isLoggedIn()) {
    // 未登录：清掉本地态并跳登录
    authLogout(true)
    return false
  }
  if (!isAdmin()) {
    // 已登录但不是 ADMIN：显示"无权限访问"，1.2s 后回到首页
    permissionDenied.value = true
    errorMessage.value = ''
    users.value = []
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

.user-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.user-card {
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

.user-name {
  flex: 1;
  min-width: 0;
  font-size: 32rpx;
  font-weight: 600;
  color: #222;
}

.role-tag {
  flex-shrink: 0;
  margin-left: 16rpx;
  display: inline-block;
  padding: 6rpx 18rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 500;
}

.role-user {
  background-color: #eff6ff;
  color: #1d4ed8;
}

.role-admin {
  background-color: #fef3c7;
  color: #b45309;
}

.role-default {
  background-color: #f3f4f6;
  color: #6b7280;
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

.static-value {
  width: 100%;
  height: 80rpx;
  line-height: 80rpx;
  padding: 0 20rpx;
  font-size: 28rpx;
  background: #f3f4f6;
  border: 2rpx solid #e5e7eb;
  border-radius: 12rpx;
  box-sizing: border-box;
  color: #6b7280;
}

.picker-value {
  width: 100%;
  height: 80rpx;
  padding: 0 20rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #f9fafb;
  border: 2rpx solid #e5e7eb;
  border-radius: 12rpx;
  box-sizing: border-box;
}

.picker-text {
  font-size: 28rpx;
  color: #111827;
}

.picker-arrow {
  font-size: 24rpx;
  color: #9ca3af;
  margin-left: 12rpx;
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
