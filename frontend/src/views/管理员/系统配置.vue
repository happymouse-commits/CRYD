<template>
  <div class="config-page">
    <div class="page-header">
      <h2>⚙️ 系统配置</h2>
      <div class="header-actions">
        <el-button @click="loadConfig" :loading="loading">
          <el-icon><Refresh /></el-icon> 重新加载
        </el-button>
        <el-button type="primary" @click="saveConfig" :loading="saving">
          <el-icon><Check /></el-icon> 保存配置
        </el-button>
      </div>
    </div>

    <!-- 大模型 API 配置 -->
    <el-card shadow="hover" class="config-card">
      <template #header>
        <div class="card-header">
          <el-icon><Connection /></el-icon>
          <span>大模型 API 参数</span>
          <el-tag size="small" type="warning">核心配置</el-tag>
        </div>
      </template>

      <el-form :model="llmConfig" label-width="130px">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="API 地址">
              <el-input v-model="llmConfig.apiUrl" placeholder="https://api.openai.com/v1" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="API 密钥">
              <el-input v-model="llmConfig.apiKey" type="password" show-password placeholder="sk-..." />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="默认模型">
              <el-select v-model="llmConfig.model" style="width: 100%">
                <el-option label="讯飞星火 Ultra (默认)" value="spark-ultra" />
                <el-option label="讯飞星火 Max" value="spark-max" />
                <el-option label="DeepSeek-V3" value="deepseek-chat" />
                <el-option label="GLM-4" value="glm-4" />
                <el-option label="Qwen-Max" value="qwen-max" />
                <el-option label="GPT-4o" value="gpt-4o" />
                <el-option label="自定义模型" value="custom" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider />

        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="Temperature">
              <el-slider v-model="llmConfig.temperature" :min="0" :max="2" :step="0.1" show-input />
              <template #extra>
                <span class="param-hint">越高越有创意，越低越严谨</span>
              </template>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="Max Tokens">
              <el-input-number v-model="llmConfig.maxTokens" :min="256" :max="32768" :step="256" style="width:100%" />
              <template #extra>
                <span class="param-hint">单次回复最大输出长度</span>
              </template>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="Top-P">
              <el-slider v-model="llmConfig.topP" :min="0" :max="1" :step="0.05" show-input />
              <template #extra>
                <span class="param-hint">核采样概率阈值</span>
              </template>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="请求超时(秒)">
              <el-input-number v-model="llmConfig.timeout" :min="10" :max="300" :step="10" style="width:100%" />
              <template #extra>
                <span class="param-hint">API 请求超时时间</span>
              </template>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <!-- 智能体调度策略 -->
    <el-card shadow="hover" class="config-card">
      <template #header>
        <div class="card-header">
          <el-icon><Rank /></el-icon>
          <span>智能体调度策略</span>
          <el-tag size="small" type="success">高级</el-tag>
        </div>
      </template>

      <el-form :model="agentConfig" label-width="140px">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="最大并发智能体数">
              <el-input-number v-model="agentConfig.maxConcurrent" :min="1" :max="20" style="width:100%" />
              <template #extra>
                <span class="param-hint">同时运行智能体上限</span>
              </template>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="调度策略">
              <el-select v-model="agentConfig.schedulingStrategy" style="width:100%">
                <el-option label="优先级队列" value="priority" />
                <el-option label="先到先服务" value="fifo" />
                <el-option label="负载均衡" value="load_balance" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="空闲超时(秒)">
              <el-input-number v-model="agentConfig.idleTimeout" :min="30" :max="600" :step="10" style="width:100%" />
              <template #extra>
                <span class="param-hint">空闲智能体自动释放</span>
              </template>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider />

        <!-- 智能体优先级 -->
        <div class="agent-priority-section">
          <p class="section-label">智能体优先级排序（拖拽排序，越靠上优先级越高）：</p>
          <el-table :data="agentConfig.priorities" row-key="key" stripe size="small">
            <el-table-column label="排序" width="60">
              <template #default="{ $index }">
                <el-tag size="small" :type="$index === 0 ? 'danger' : $index < 3 ? 'warning' : 'info'">
                  P{{ $index + 1 }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="emoji" label="" width="40" />
            <el-table-column prop="name" label="智能体名称" />
            <el-table-column prop="desc" label="功能描述" min-width="200" />
            <el-table-column label="优先级调整" width="180">
              <template #default="{ $index }">
                <el-button-group>
                  <el-button size="small" :disabled="$index === 0" @click="moveUp($index)">
                    <el-icon><Top /></el-icon>
                  </el-button>
                  <el-button size="small" :disabled="$index === agentConfig.priorities.length - 1" @click="moveDown($index)">
                    <el-icon><Bottom /></el-icon>
                  </el-button>
                </el-button-group>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-form>
    </el-card>

    <!-- 资源生成阈值 -->
    <el-card shadow="hover" class="config-card">
      <template #header>
        <div class="card-header">
          <el-icon><TrendCharts /></el-icon>
          <span>资源生成阈值设置</span>
          <el-tag size="small">限流</el-tag>
        </div>
      </template>

      <el-form :model="thresholdConfig" label-width="180px">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="每日资源生成上限（每用户）">
              <el-input-number v-model="thresholdConfig.dailyResourceLimit" :min="1" :max="200" style="width:100%" />
              <template #extra>
                <span class="param-hint">超过后自动拒绝生成请求</span>
              </template>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="每周资源生成上限（每用户）">
              <el-input-number v-model="thresholdConfig.weeklyResourceLimit" :min="5" :max="1000" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="资源质量最低分">
              <el-slider v-model="thresholdConfig.minQualityScore" :min="0" :max="100" :step="5" show-input />
              <template #extra>
                <span class="param-hint">低于此阈值不交付</span>
              </template>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider />

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="AI辅导对话上限（每用户）">
              <el-input-number v-model="thresholdConfig.maxConversationsPerUser" :min="1" :max="500" style="width:100%" />
              <template #extra>
                <span class="param-hint">每日对话次数限制</span>
              </template>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="刷题生成题目上限（每次）">
              <el-input-number v-model="thresholdConfig.maxQuestionsPerGeneration" :min="5" :max="100" style="width:100%" />
              <template #extra>
                <span class="param-hint">单次生成最多题目数</span>
              </template>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="疑难突破资源上限（每次）">
              <el-input-number v-model="thresholdConfig.maxBreakthroughResources" :min="3" :max="20" style="width:100%" />
              <template #extra>
                <span class="param-hint">单次突破最多资源数</span>
              </template>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider />

        <!-- 功能开关 -->
        <div class="feature-toggles">
          <p class="section-label">功能开关：</p>
          <el-row :gutter="16">
            <el-col :span="6" v-for="toggle in featureToggles" :key="toggle.key">
              <div class="toggle-item">
                <el-switch v-model="toggle.enabled" />
                <span class="toggle-label">{{ toggle.icon }} {{ toggle.name }}</span>
                <el-tag size="small" :type="toggle.enabled ? 'success' : 'info'">
                  {{ toggle.enabled ? '开启' : '关闭' }}
                </el-tag>
              </div>
            </el-col>
          </el-row>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Check, Connection, Rank, TrendCharts, Top, Bottom } from '@element-plus/icons-vue'
import api from '../../api'

const loading = ref(false)
const saving = ref(false)

const llmConfig = reactive({
  apiUrl: 'wss://spark-api.xf-yun.com/v4.0/chat',
  apiKey: '14f0f6dd2dcd7ae6ca62aaed68035914',
  model: 'spark-ultra',
  temperature: 0.5,
  maxTokens: 2048,
  topP: 0.9,
  timeout: 60
})

const agentConfig = reactive({
  maxConcurrent: 6,
  schedulingStrategy: 'priority',
  idleTimeout: 120,
  priorities: [
    { key: 'tutor', emoji: '👩‍🏫', name: '辅导老师', desc: 'AI辅导对话——实时交互，优先级最高' },
    { key: 'question_maker', emoji: '📝', name: '出题专家', desc: '刷题房题目生成——按需异步' },
    { key: 'breakthrough', emoji: '🔬', name: '疑难突破', desc: '多智能体协作突破——资源密集型' },
    { key: 'profile_analyzer', emoji: '📊', name: '画像分析师', desc: '学习画像构建——后台定时任务' },
    { key: 'path_planner', emoji: '🗺️', name: '路径规划师', desc: '学习路径规划——非实时' },
    { key: 'knowledge_base', emoji: '🗄️', name: '知识库管理', desc: '知识库索引——低频低优先级' },
  ]
})

const thresholdConfig = reactive({
  dailyResourceLimit: 50,
  weeklyResourceLimit: 200,
  minQualityScore: 60,
  maxConversationsPerUser: 100,
  maxQuestionsPerGeneration: 20,
  maxBreakthroughResources: 10,
})

const featureToggles = reactive([
  { key: 'ai_chat', icon: '💬', name: 'AI辅导', enabled: true },
  { key: 'practice', icon: '📝', name: '刷题房', enabled: true },
  { key: 'breakthrough', icon: '🔬', name: '疑难突破', enabled: true },
  { key: 'profile', icon: '📊', name: '学习画像', enabled: true },
  { key: 'resources', icon: '📚', name: '资源生成', enabled: true },
  { key: 'learning_path', icon: '🗺️', name: '学习路径', enabled: true },
  { key: 'export', icon: '📥', name: '数据导出', enabled: true },
  { key: 'notifications', icon: '🔔', name: '通知推送', enabled: false },
])

function moveUp(index) {
  if (index <= 0) return
  const arr = agentConfig.priorities
  ;[arr[index - 1], arr[index]] = [arr[index], arr[index - 1]]
}

function moveDown(index) {
  const arr = agentConfig.priorities
  if (index >= arr.length - 1) return
  ;[arr[index], arr[index + 1]] = [arr[index + 1], arr[index]]
}

async function loadConfig() {
  loading.value = true
  try {
    const res = await api.get('/admin/config')
    const data = res.data
    if (data?.llm) Object.assign(llmConfig, data.llm)
    if (data?.agent) {
      agentConfig.maxConcurrent = data.agent.maxConcurrent ?? 6
      agentConfig.schedulingStrategy = data.agent.schedulingStrategy || 'priority'
      agentConfig.idleTimeout = data.agent.idleTimeout ?? 120
      if (data.agent.priorities) agentConfig.priorities = data.agent.priorities
    }
    if (data?.threshold) Object.assign(thresholdConfig, data.threshold)
    if (data?.toggles) {
      data.toggles.forEach(t => {
        const f = featureToggles.find(ft => ft.key === t.key)
        if (f) f.enabled = t.enabled
      })
    }
    ElMessage.success('配置已加载')
  } catch {
    ElMessage.warning('无法加载远程配置，使用默认值')
  } finally {
    loading.value = false
  }
}

async function saveConfig() {
  saving.value = true
  try {
    await api.put('/admin/config', {
      llm: { ...llmConfig },
      agent: { ...agentConfig },
      threshold: { ...thresholdConfig },
      toggles: featureToggles.map(t => ({ key: t.key, enabled: t.enabled })),
    })
    ElMessage.success('✅ 配置已保存，部分修改需重启服务生效')
  } catch {
    ElMessage.error('保存失败，请检查网络连接')
  } finally {
    saving.value = false
  }
}

onMounted(loadConfig)
</script>

<style scoped>
.config-page { padding: 0; }
.page-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 20px; flex-wrap: wrap; gap: 12px;
}
.page-header h2 { margin: 0; font-size: 20px; font-weight: 600; color: #1d2129; }
.header-actions { display: flex; gap: 10px; }

.config-card { margin-bottom: 20px; border-radius: 10px; }
.card-header {
  display: flex; align-items: center; gap: 10px;
  font-weight: 600; font-size: 15px;
}
.card-header .el-tag { margin-left: auto; }

.param-hint { color: #9ca3af; font-size: 12px; margin-top: 2px; display: block; }

.section-label { font-size: 14px; color: #374151; margin: 0 0 12px 0; font-weight: 500; }

.agent-priority-section { padding: 4px 0; }

.feature-toggles { padding: 4px 0; }
.toggle-item {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 12px; background: #f6f8fa;
  border-radius: 8px; margin-bottom: 8px;
}
.toggle-label { font-size: 14px; color: #1a1a2e; flex: 1; }
</style>