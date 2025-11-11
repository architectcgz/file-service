import { defineStore } from 'pinia'
import { adminApi } from '@/api/admin'
import type { AxiosError } from 'axios'

interface AuthState {
  isLoggedIn: boolean
  username: string
  loginTime: string | null
}

interface LoginResult {
  success: boolean
  message: string
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    isLoggedIn: localStorage.getItem('isLoggedIn') === 'true',
    username: localStorage.getItem('username') || '',
    loginTime: localStorage.getItem('loginTime') || null
  }),

  actions: {
    async login(username: string, password: string): Promise<LoginResult> {
      try {
        const response = await adminApi.login(username, password)
        if (response.success) {
          this.isLoggedIn = true
          this.username = username
          this.loginTime = new Date().toISOString()
          localStorage.setItem('isLoggedIn', 'true')
          localStorage.setItem('username', username)
          localStorage.setItem('loginTime', this.loginTime)
          return { success: true, message: response.message }
        } else {
          return { success: false, message: response.message }
        }
      } catch (error) {
        const axiosError = error as AxiosError<{ message?: string }>
        return {
          success: false,
          message: axiosError.response?.data?.message || '登录失败，请检查网络连接'
        }
      }
    },

    async logout(): Promise<void> {
      try {
        await adminApi.logout()
      } catch (error) {
        console.error('登出失败:', error)
      } finally {
        this.isLoggedIn = false
        this.username = ''
        this.loginTime = null
        localStorage.removeItem('isLoggedIn')
        localStorage.removeItem('username')
        localStorage.removeItem('loginTime')
      }
    },

    async checkStatus(): Promise<boolean> {
      try {
        const response = await adminApi.getStatus()
        if (response.success && response.data?.isLoggedIn) {
          this.isLoggedIn = true
          this.username = response.data.username || ''
          this.loginTime = response.data.loginTime || null
          localStorage.setItem('isLoggedIn', 'true')
          if (this.username) localStorage.setItem('username', this.username)
          if (this.loginTime) localStorage.setItem('loginTime', this.loginTime)
          return true
        } else {
          this.logout()
          return false
        }
      } catch (error) {
        this.logout()
        return false
      }
    }
  }
})

