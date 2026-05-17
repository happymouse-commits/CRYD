<template>
  <div class="report-page">
    <div class="page-header">
      <h2>学习评估报告</h2>
      <p class="subtitle">基于你的学习数据自动生成的学习评估报告</p>
      <el-button type="primary" :loading="loading" @click="generateReport">
        <el-icon><Refresh /></el-icon> 生成最新报告
      </el-button>
    </div>

    <!-- 报告内容 -->
    <div v-if="report" class="report-content">
      <el-card shadow="hover">
        <div class="report-body" v-html="renderedReport"></div>
      </el-card>
    </div>

    <!-- 无报告时的引导 -->
    <el-empty v-else description="点击上方按钮生成你的学习评估报告" :image-size="120">
      <el-button type="primary" @click="generateReport">生成报告</el-button>
    </el-empty>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { useUserStore } from '../../store/user'
import api from '../../api'

const store = useUserStore()
const report = ref('')
const loading = ref(false)

const renderedReport = ref('')

function renderMarkdown(text) {
  if (!text) return ''
  return text
    .replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code class="$1">$2</code></pre>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\n## (.+)/g, '\n<h3>$1</h3>')
    .replace(/\n# (.+)/g, '\n<h2>$1</h2>')
    .replace(/\n- (.+)/g, '\n<li>$1</li>')
    .replace(/\n/g, '<br>')
}

async function generateReport() {
  loading.value = true
  try {
    // 获取学习进度数据
    const progressRes = await api.get('/student/progress/' + store.id)
    const courses = progressRes.data || []

    // 获取学生画像
    let profileStr = ''
    try {
      const profileRes = await api.get('/student/by-sysuser/' + store.id)
      const p = profileRes.data || {}
      if (p.knowledgeLevel != null) {
        profileStr = `知识基础: ${p.knowledgeLevel}分, 认知风格: ${p.cognitiveStyle || '未知'}, 学习偏好: ${p.learningPreference || '未知'}, 学习节奏: ${p.learningPace || '未知'}, 兴趣方向: ${p.interestDirection || '未知'}, 学习动机: ${p.studyMotivation || '未知'}, 专注力: ${p.focusLevel || '未知'}, 薄弱环节: ${p.weakAreas || '未知'}`
      }
    } catch {}

    // 构建报告数据
    let totalChapters = 0, completedChapters = 0, totalScore = 0, scoredCount = 0
    for (const c of courses) {
      for (const ch of (c.chapters || [])) {
        totalChapters++
        if (ch.status === 'completed') completedChapters++
        if (ch.score != null) { totalScore += ch.score; scoredCount++ }
      }
    }
    const avgScore = scoredCount > 0 ? Math.round(totalScore / scoredCount) : 0
    const completionRate = totalChapters > 0 ? Math.round(completedChapters / totalChapters * 100) : 0

    // 构建详细数据文本
    let courseDetails = ''
    for (const c of courses) {
      const completed = (c.chapters || []).filter(ch => ch.status === 'completed').length
      const total = (c.chapters || []).length
      courseDetails += `\n- ${c.name}: 已完成 ${completed}/${total} 关`
    }

    const prompt = `你是学习评估分析师。请根据以下学生学习数据，生成一份简洁的学习评估报告（200字以内）：

【学生画像】
${profileStr || '暂无画像数据'}

【学习统计】
- 总关卡数: ${totalChapters}
- 已完成: ${completedChapters} (${completionRate}%)
- 平均得分: ${avgScore}分

【课程详情】
${courseDetails || '暂无课程数据'}

请输出一份结构化的评估报告，包括：
## 总体评价
## 优势分析
## 改进建议
## 下一步计划`

    try {
      const chatRes = await api.post('/chat/send', {
        studentId: store.id,
        message: prompt,
        agentName: '评估分析师'
      })
      report.value = chatRes.data?.message || chatRes.data || '报告生成失败'
    } catch {
      // 如果AI不可用，生成基础报告
      report.value = `## 学习评估报告

## 总体评价
你已完成 ${completedChapters}/${totalChapters} 个关卡（${completionRate}%），平均得分 ${avgScore}分。

## 优势分析
${completionRate >= 50 ? '学习进度良好，持续保持！' : '学习正在稳步推进中。'}

## 改进建议
建议继续完成剩余关卡，巩固知识点。

## 下一步计划
按照关卡顺序逐一突破，重点关注得分较低的关卡。`
    }

    renderedReport.value = renderMarkdown(report.value)
    ElMessage.success('报告已生成')
  } catch (e) {
    ElMessage.error('报告生成失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.report-page { }
.page-header {
  display: flex; justify-content: space-between; align-items: flex-start;
  margin-bottom: 20px; flex-wrap: wrap; gap: 12px;
}
.page-header h2 { margin: 0 0 4px 0; font-size: 22px; }
.subtitle { color: #909399; font-size: 14px; margin: 0; }

.report-content { }
.report-body {
  font-size: 15px; line-height: 1.8; color: #303133;
  padding: 8px 0;
}
.report-body :deep(h2) { font-size: 20px; margin: 20px 0 12px; color: #303133; }
.report-body :deep(h3) { font-size: 17px; margin: 16px 0 8px; color: #409EFF; }
.report-body :deep(li) { margin-left: 20px; margin-bottom: 4px; }
.report-body :deep(pre) { background: #f4f4f5; padding: 12px; border-radius: 8px; overflow-x: auto; }
.report-body :deep(code) { background: #f4f4f5; padding: 2px 6px; border-radius: 4px; font-size: 13px; }
.report-body :deep(strong) { color: #303133; }
</style>
