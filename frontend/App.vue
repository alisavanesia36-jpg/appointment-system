<script>
	import { isLoggedIn, getCurrentUser, setCurrentUser } from '@/utils/auth.js'
	import { getMe } from '@/api/user.js'

	export default {
		onLaunch: function() {
			console.log('App Launch')
			this.ensureAuthState()
		},
		onShow: function() {
			console.log('App Show')
		},
		onHide: function() {
			console.log('App Hide')
		},
		methods: {
			async ensureAuthState() {
				if (!isLoggedIn()) return
				const cached = getCurrentUser()
				if (cached) return
				try {
					const user = await getMe()
					if (user) {
						setCurrentUser(user)
					}
				} catch (e) {
					console.warn('[App Launch] 恢复当前用户失败', e && e.message)
				}
			}
		}
	}
</script>

<style>
page {
	background-color: #f5f7fa;
}
</style>
