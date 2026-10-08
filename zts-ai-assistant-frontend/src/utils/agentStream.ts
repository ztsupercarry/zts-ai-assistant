import type { ChatBlockVariant } from '@/types/chat'

/** 一次流式事件要落到界面上的结果 */
export interface AgentStreamSegment {
  /** true = 新开一个气泡；false = 继续写入当前气泡 */
  startNewBlock: boolean
  variant: ChatBlockVariant
  /** 气泡标题 */
  label?: string
  /** 步骤序号 */
  step?: number
  /** 增量文本 */
  text: string
}

/** 「Step 3: xxx」/「步骤3：xxx」 */
const STEP_PATTERN = /^\s*(?:step|步骤)\s*(\d+)\s*[:：]\s*/i

/** 「Final: xxx」「最终回复：xxx」等最终回复前缀 */
const FINAL_PATTERN = /^\s*(?:final|final answer|最终回复|最终结果|最终答案)\s*[:：]\s*/i

/** 执行结束 / 错误提示等控制信息（不能当成最终回复） */
const NOTICE_PATTERN = /^\s*(?:执行结束|执行错误|错误|terminated|error)/i

/**
 * 智能体流式分段器（对应《AI 超级智能体》的 Manus 应用）。
 *
 * 后端 BaseAgent.runStream 每执行完一步就 send 一条消息，一条消息就是一个 SSE 事件：
 * ```
 * Step 1: 工具searchWeb返回的结果：{...}
 * Step 2: 工具writeFile返回的结果：File written successfully to: ...
 * Step 3: 思考完成 - 无需行动
 * Step 4: 工具doTerminate返回的结果：任务结束
 * ```
 * 因此这里按「一个 SSE 事件 = 一步」切分：
 * 1. 命中 Step 标记 → 新开一个「Step N」气泡；
 * 2. 命中 Final / 最终回复标记 → 新开一个「最终回复」气泡；
 * 3. 执行结束 / 错误 → 新开一个系统提示气泡；
 * 4. 已经出现过步骤标记、又收到一条没有标记的消息 → 视为最终回复（兼容后端直接推送模型回答文本）。
 */
export function createAgentStreamSplitter(): (chunk: string) => AgentStreamSegment[] {
  let seenStep = false

  return (chunk: string): AgentStreamSegment[] => {
    if (!chunk) return []

    const stepMatch = STEP_PATTERN.exec(chunk)
    if (stepMatch) {
      seenStep = true
      const stepNumber = Number(stepMatch[1])
      const hasNumber = Number.isFinite(stepNumber) && stepNumber > 0
      return [
        {
          startNewBlock: true,
          variant: 'step',
          step: hasNumber ? stepNumber : undefined,
          label: hasNumber ? `Step ${stepNumber}` : '执行步骤',
          text: chunk.slice(stepMatch[0].length),
        },
      ]
    }

    const finalMatch = FINAL_PATTERN.exec(chunk)
    if (finalMatch) {
      return [
        {
          startNewBlock: true,
          variant: 'final',
          label: '最终回复',
          text: chunk.slice(finalMatch[0].length),
        },
      ]
    }

    if (NOTICE_PATTERN.test(chunk)) {
      return [{ startNewBlock: true, variant: 'notice', label: '执行信息', text: chunk.trim() }]
    }

    if (seenStep) {
      return [{ startNewBlock: true, variant: 'final', label: '最终回复', text: chunk }]
    }

    return [{ startNewBlock: false, variant: 'text', text: chunk }]
  }
}
