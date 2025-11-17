<template>
  <div class="bg-white rounded-lg shadow-sm p-6 border-l-4" :class="borderColor">
    <div class="flex items-center justify-between">
      <div>
        <p class="text-sm text-gray-600 mb-1">{{ title }}</p>
        <p class="text-3xl font-bold" :class="textColor">{{ value }}</p>
      </div>
      <div class="text-4xl">{{ icon }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  title: string
  value: number
  icon: string
  color: 'blue' | 'green' | 'yellow' | 'red'
}>()

const borderColor = computed(() => {
  const colors = {
    blue: 'border-blue-500',
    green: 'border-green-500',
    yellow: 'border-yellow-500',
    red: 'border-red-500'
  }
  return colors[props.color]
})

const textColor = computed(() => {
  const colors = {
    blue: 'text-blue-600',
    green: 'text-green-600',
    yellow: 'text-yellow-600',
    red: 'text-red-600'
  }
  return colors[props.color]
})
</script>
