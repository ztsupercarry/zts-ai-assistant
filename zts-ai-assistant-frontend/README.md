# ZTS AI Assistant（前端）

基于 **Vue 3 + Vite + TypeScript + Axios** 的 AI 应用前端，包含主页与两个聊天室应用：

| 页面 | 路由 | 说明 | 后端接口 |
| --- | --- | --- | --- |
| 主页 | `/` | 切换不同应用 | — |
| AI 旅行规划助手 | `/travel` | 聊天室风格，进入页面自动生成会话 ID，SSE 实时逐字返回 | `GET /ai/travel_app/chat/sse?message=&chatId=` |
| AI 超级智能体 | `/manus` | 聊天室风格，SSE 实时逐字返回 | `GET /ai/manus/chat?message=` |

接口地址前缀：`http://localhost:8123/api`（即完整地址为 `http://localhost:8123/api/ai/travel_app/chat/sse`）。

## 快速开始

```bash
# 1. 安装依赖（脚手架生成的 package-lock.json 里还没有 axios，用 npm install 会自动补装并同步 lock）
#    注意：先不要用 npm ci
npm install

# 2. 启动开发服务器（默认 http://localhost:5173）
npm run dev

# 3. 打包 / 类型检查
npm run build
```

> 开发环境默认通过 Vite 代理访问后端：前端请求 `/api/**` → 代理到 `http://localhost:8123/**`，
> 因此**不需要**后端额外配置 CORS。如果后端地址不是 8123，改 `vite.config.ts` 里的 `server.proxy` 即可。

## 目录结构

```
src/
├─ api/
│  ├─ request.ts          # axios 实例（baseURL / 超时 / 统一错误文案）+ resolveApiUrl
│  └─ ai.ts               # doChatWithTravelAppSse / doChatWithManus 两个流式接口
├─ components/
│  ├─ ChatRoom.vue        # 通用聊天室：消息列表 + 输入框 + 昵称 / 分段模式等配置
│  └─ ChatMessageItem.vue # 单条消息气泡（用户靠右 / AI 靠左，支持 step/final/notice 样式）
├─ views/
│  ├─ HomeView.vue        # 主页：应用切换卡片
│  ├─ TravelAppView.vue   # 页面 1：AI 旅行规划助手
│  ├─ ManusAppView.vue    # 页面 2：AI 超级智能体
│  └─ NotFoundView.vue    # 404
├─ utils/
│  ├─ sse.ts              # fetch + ReadableStream 的 SSE 客户端与解析器
│  ├─ agentStream.ts      # 智能体 Step 分段器（每步一个气泡）
│  ├─ id.ts               # 会话 ID / 消息 ID 生成
│  └─ format.ts           # 时间格式化
├─ types/chat.ts          # ChatMessage / ChatStreamPayload 等类型
├─ router/index.ts        # 路由表
├─ assets/main.css        # 全局样式与设计变量
├─ App.vue                # 顶栏 + RouterView
└─ main.ts                # 应用入口
```

## 关键实现

### 1. 会话 ID（chatId）

进入旅行规划页面时会自动生成一个聊天室 ID（`crypto.randomUUID()`，兜底时间戳 + 随机串），
每次请求都带上它，后端按它区分会话、维护多轮上下文。该 ID 只在请求参数里使用，不在界面上展示；
点「新建会话」会清空聊天记录并重新生成 ID。Manus 接口不接收 chatId，因此该页面不生成。

### 2. SSE 实时输出

`src/api/ai.ts` 把后端的 SSE 流接到聊天室组件上：

```ts
export async function doChatWithTravelAppSse(payload: ChatStreamPayload): Promise<void> {
  await chat('/ai/travel_app/chat/sse', { message: payload.message, chatId: payload.chatId }, payload)
}
```

`src/utils/sse.ts` 负责真正的流式读取：`fetch` → `response.body.getReader()` →
按 SSE 规范切分 `data:` 帧 → 每收到一段就回调一次 `onMessage`，聊天室把增量追加到
AI 气泡上，实现「边生成边显示」。

解析器同时兼容了几种常见返回格式，避免后端换实现就白屏：

- 纯文本帧：`data:你好\n\n`
- `data:[DONE]` / `event: done` 结束标记
- JSON 帧：自动从 `content` / `text` / `data` / `delta` / `message` / `result` / `output` / `v` 等字段里递归取文本
- 心跳注释行（`:` 开头）与 `\r\n` 换行，以及 **`\r\n` 恰好被拆到两个数据包** 的边界情况（会把末尾孤立的 `\r` 留到下一轮再解析，避免劈帧丢数据）
- 只有空白字符的增量（例如 markdown 段落之间的换行）会原样保留

### 3. 智能体每步一个气泡（Manus）

超级智能体的回复不是一个整体，而是「多轮工具调用」的集合。后端
`BaseAgent.runStream()` **每执行完一步就 send 一条消息**，一条消息就是一个 SSE 事件：

```
Step 1: 工具searchWeb返回的结果：{...}
Step 2: 工具writeFile返回的结果：File written successfully to: ...
Step 3: 思考完成 - 无需行动
Step 4: 工具doTerminate返回的结果：任务结束
```

前端用 `src/utils/agentStream.ts` 里的 `createAgentStreamSplitter()` 把事件流切分成气泡：

| 事件内容 | 渲染成 |
| --- | --- |
| `Step N: ...` | 新开一个「Step N」气泡，内容是这一步的执行结果（等宽字体，超过 420 字默认折叠） |
| `Final: ...` / `最终回复：...` | 「最终回复」气泡（高亮边框） |
| `执行结束 / 错误 ...` | 系统提示气泡 |
| 出现过步骤标记之后的无标记消息 | 视为最终回复，新开「最终回复」气泡 |
| 其它 | 追加到当前气泡 |

气泡上方的昵称由 `assistant-name` 决定：旅行规划页是「旅行规划助手」，智能体页是「超级智能体」，
缺省为「AI 助手」。而 `Step N` / `最终回复` 这个小标题显示在**气泡内部的开头**，用一条虚线分隔——
一眼就能看出这段话属于第几步。

用法：页面给聊天室加一个 `stream-mode="steps"` 即可（旅行规划助手用默认的 `text` 模式，全部增量写进同一个气泡）。

> ⚠️ **注意**：当前后端在 `ToolCallAgent.think()` 里只把模型的自然语言回复写进了日志
> （`log.info(getName() + "的思考：" + result)`），没有推送出去，所以有的任务跑完只能看到 4 个 Step 气泡、
> 看不到最终回复。这时前端会补一条「执行完成 · 智能体已执行 N 步，但没有收到最终回复」的提示。
> 想让最终回复也显示出来，需要在 `BaseAgent.runStream()` 的循环结束后补一次推送（见下面「后端配合」）。

### 4. Axios 的位置

- `src/api/request.ts` 维护全局 axios 实例（`baseURL`、超时、拦截器统一把异常转成可展示的中文文案），
  并提供 `resolveApiUrl()` 给原生 `fetch` 拼完整地址。两条链路（fetch / axios）的错误都会经过
  `resolveErrorMessage()`，所以后端没启动时看到的是中文提示，而不是 `TypeError: Failed to fetch`。
- 浏览器端 axios 基于 XHR，**无法**边收边读流式响应，所以 SSE 用 `fetch` 实现；
  但聊天室头部提供了「流式输出」开关：关闭后改为 axios 一次性取回完整 SSE 文本，
  再用同一套解析器拆分展示（`GET` + `responseType: 'text'`）。这既是浏览器不支持流式时的自动兜底，
  也方便排查「看不到逐字输出」类问题。

### 5. 聊天室交互

- 用户消息靠右、AI 消息靠左，带头像、昵称、时间；
- `Enter` 发送、`Shift + Enter` 换行（中文输入法组合中的回车不会误发送）；
- 输入框随内容自动增高，**生成过程中也能继续输入下一句**（此时发送按钮显示为「停止」，Enter 不发送、正常换行）；
- 生成中显示光标动画，可点「停止」中断（`AbortController`）；
- 出错显示中文原因并提供「重新生成」，重新发送最后一条用户消息；
- 只有滚动条停留在底部附近时才自动跟随新内容，向上翻看历史时不会被打断；
- 空状态提供推荐问题，一键发送。

## 环境变量

| 文件 | 变量 | 值 |
| --- | --- | --- |
| `.env.development` | `VITE_API_BASE_URL` | `/api`（走 Vite 代理） |
| `.env.production` | `VITE_API_BASE_URL` | `http://localhost:8123/api` |

## 后端配合（让「最终回复」气泡有内容）

前端已经支持 `Final: xxx` 这类最终回复事件，但当前后端不会推送它。在
`com.zts.ztsaiassistant.agent.BaseAgent#runStream` 的步骤循环之后补几行即可：

```java
// 在 for 循环结束后、sseEmitter.complete() 之前
// 取最后一条有内容的助手消息作为最终回复（think() 里模型不调用工具时记录的就是它）
String finalAnswer = getMessageList().stream()
        .filter(message -> message instanceof AssistantMessage)
        .map(message -> ((AssistantMessage) message).getText())
        .filter(StrUtil::isNotBlank)
        .reduce((first, last) -> last)
        .orElse("");
if (StrUtil.isNotBlank(finalAnswer)) {
    sseEmitter.send("Final: " + finalAnswer);
}
```

同时在文件头部补上 `import org.springframework.ai.chat.messages.AssistantMessage;`。
前端收到的内容形如 `Final: 任务已经完成，任务清单已写入 D:\...\个人博客_任务清单.md`，
会自动渲染成一个高亮的「最终回复」气泡。

> 想让最终回复是「对本次任务的整体总结」而不是模型最后一次思考的原文，
> 可以在循环结束后额外调一次模型做总结，再把结果作为 `Final:` 推送。

## 常见问题

**Q：页面提示「无法连接到后端服务」？**
确认 SpringBoot 已启动在 `http://localhost:8123`，且 `server.servlet.context-path` 与 `/api` 前缀一致；
浏览器 F12 的 Network 面板里看 `/api/ai/...` 请求是否返回 200。

**Q：回复要等很久才一次性出现？**
说明流被缓冲了。开发环境请确认是通过 `npm run dev` 的 Vite 代理访问（代理默认不缓冲 SSE），
并保证后端返回头是 `Content-Type: text/event-stream`；也可以在页面头部关掉「流式输出」开关对比排查。

**Q：生产环境报跨域？**
`.env.production` 里配的是直连地址，需要后端加 CORS（`@CrossOrigin` 或全局配置），
或把打包产物交给后端同域部署、把 `VITE_API_BASE_URL` 改成 `/api`。

**Q：`src/stores/counter.ts` 是什么？**
脚手架自带的示例 store，本项目未使用，可以直接删除。
