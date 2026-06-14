# Input Data
请根据以下交通案件材料内容，生成共 {questionCount} 个复盘核查主问题。
每个主问题必须额外生成 {followUpCount} 个追问。

## 问题分布要求
| 类型 | 数量 | 说明 |
|------|------|------|
| 案情事实 (CASE_FACTS) | {projectCount} 题 | 基于案件材料中的事故经过深度提问 |
| 交通违法 (TRAFFIC_VIOLATION) | {mysqlCount} 题 | 违法行为识别、因果关系分析等 |
| 责任认定 (RESPONSIBILITY) | {redisCount} 题 | 路权归属、责任划分、加重减轻情节等 |
| 证据分析 (EVIDENCE) | {javaBasicCount} 题 | 证据完整性、证据链、证据矛盾等 |
| 法规适用 (LAW_APPLY) | {javaCollectionCount} 题 | 法律条文引用、处罚依据等 |
| 处置建议 (HANDLING) | {javaConcurrentCount} 题 | 后续调查、证据补充、处置流程等 |

## 案件材料
---案件材料开始---
{resumeText}
---案件材料结束---

## 历史核查记录
以下是该案件已经核查过的问题列表，请在生成新问题时**尽可能避免重复**（除非是需要从不同维度深度追问同一主题）：
---历史核查开始---
{historicalQuestions}
---历史核查结束---

## 输出要求
请严格按照 JSON 格式输出问题列表，直接输出 JSON 对象，不要包含任何 Markdown 代码块标签。