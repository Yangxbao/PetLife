# 🤝 参与共建「星伴」

首先，感谢你愿意花时间贡献！无论是一个错别字的修复、一句新台词，还是一个全新宠物形象，**每一份 PR 都会被认真对待**。

星伴是一个把「真实自律」变成「宠物成长」的开源 Android 应用。我们希望它保持三个特质：
**温和（永不惩罚致死）· 透明（所有加减分可解释）· 精致（有生命感的陪伴体验）**。

## 🐣 新手友好任务

不知道从哪开始？这些任务很适合第一次贡献：

| 类型 | 说明 |
|---|---|
| 💬 话术扩充 | 在 `app/src/main/assets/dialogues/*.json` 中为任意情境添加台词（保持原创、不说教） |
| 🌐 翻译 | 把界面与话术翻译成英文/日文（`values-en/` + 话术 JSON 多语言目录） |
| 🎨 表情/动作 | 在 `ui/pet/character/` 中为新表情绘制参数化脸部（有完整的 Faces 体系可参考） |
| 📝 文档 | 完善使用说明、截图、数值解释 |
| 🐛 Bug | [Issue 列表](../../issues) 中带 `good first issue` 标签的任务 |

## 🛠️ 开发环境

- Android Studio（JDK 17，compileSdk 35）
- 克隆后直接构建 debug 即可完整体验：

```bash
git clone https://github.com/Yangxbao/PetLife.git
cd PetLife
./gradlew assembleDebug testDebugUnitTest   # 构建 + 69 项单元测试
```

## 📥 提交 PR 的流程

1. Fork 本仓库，从 `main` 拉出特性分支：`git checkout -b feat/your-feature`
2. 完成改动，确保 `./gradlew testDebugUnitTest` 全部通过
3. 提交 PR，并在描述中说明：改了什么、为什么、是否影响数值系统
4. 通过 review 后合并，你会出现在贡献者列表里 🎉

### 代码约定

- 提交信息遵循 [Conventional Commits](https://www.conventionalcommits.org/zh-hans/)：`feat:` `fix:` `docs:` `test:` `refactor:`
- 业务规则（成长系数/自律评分/结算）的任何改动**必须**同步更新对应单元测试
- 宠物数值红线：**任何新机制不得引入死亡、清零或惩罚性清空**——扣分要有上限、有明细、可恢复
- 话术贡献：不复制任何受版权保护的角色台词；不说教、不出现负面词汇；一句话一个 JSON 条目
- 用户可见文本使用中文；Commit 不携带任何密钥或个人数据

## 🐛 报告 Bug

请使用 [Issue 模板](../../issues/new/choose)，尽可能附上：

- 设备型号与系统版本
- 复现步骤
- 若与时长统计相关：设置 → 🩺使用时长诊断 → 卡片截图（或 logcat 过滤 `PetLifeStats`）
- 若为闪退：设置 → 🩺使用时长诊断 → **查看崩溃日志** → 全文粘贴

## 💡 功能提议

欢迎通过 [Feature Request 模板](../../issues/new/choose) 提议。当前社区呼声较高的方向：

- 🧩 桌面小部件（宠物常驻桌面）
- 🎽 换装系统与更多宠物形象
- 🗣️ 说话时嘴部联动动画
- 🌍 多语言界面
- 🤖 可选的本地 AI 对话（BYOK 模式）

> 宏大但不必急：这些都会进入 [Roadmap](../../projects)，也期待和感兴趣的人一起做。
