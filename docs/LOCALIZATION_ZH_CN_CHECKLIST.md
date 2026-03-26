# 中文化进度清单（zh-CN）

> 分支：`feature/chinese-localization`  
> 更新时间：2026-03-27

## 0) 项目准备

- [x] 已 Fork 仓库到 `gehbfarr5/matedroid`
- [x] 已创建开发分支 `feature/chinese-localization`
- [x] 已配置 `origin` / `upstream` 远程

## 1) 资源完整性（strings）

- [x] `values/strings.xml` 与 `values-zh/strings.xml` 键数量一致
- [x] `string` / `plurals` 条目类型一致
- [x] 无缺失 key、无额外 key
- [x] 无空翻译条目

### 当前统计（自动校验）

- Base：455 项（`string` 448 + `plurals` 7）
- zh：455 项（`string` 448 + `plurals` 7）
- 缺失：0
- 空值：0

## 2) 文案质量校对

- [ ] 术语统一（例如：充电、里程、能耗、统计、同步等）
- [ ] 语气统一（按钮/标题/提示语风格一致）
- [ ] 标点与中英文混排规范
- [ ] 过长句子压缩（移动端可读性）

## 3) 格式与占位符安全

- [ ] `%s` / `%d` / `%1$s` 等占位符顺序与数量一致
- [ ] `plurals` 语义自然（数量表达）
- [ ] 特殊符号转义正确（`'`、`\n`、HTML 标记）

## 4) UI 验收（真机）

- [ ] 首屏/仪表盘
- [ ] 充电列表与详情
- [ ] 行程列表与详情
- [ ] 统计页（图表、筛选器、单位）
- [ ] 设置页与权限弹窗
- [ ] 通知文案与 Deep Link 跳转

### UI 验收关注点

- [ ] 文本截断/换行异常
- [ ] 按钮溢出或遮挡
- [ ] 图表标签重叠
- [ ] 夜间模式可读性

## 5) 回归与发布准备

- [ ] Debug 构建通过
- [ ] 核心流程冒烟测试通过（登录/同步/筛选/详情）
- [ ] 更新 `CHANGELOG.md`（如需）
- [ ] 提交 PR 并附中文化截图

## 6) 可选扩展

- [ ] README 增加中文说明或提供 `README.zh-CN.md`
- [ ] 文案术语表（Glossary）沉淀到 `docs/`
- [ ] 增加本地化校验脚本并接入 CI

---

## 快速校验命令（可复用）

```bash
python3 - <<'PY'
import xml.etree.ElementTree as ET
from pathlib import Path

base = Path('app/src/main/res/values/strings.xml')
zh = Path('app/src/main/res/values-zh/strings.xml')

def names(path):
    root = ET.parse(path).getroot()
    return [c.attrib['name'] for c in root if c.tag in ('string', 'plurals', 'string-array')]

b = set(names(base))
z = set(names(zh))
print('base:', len(b), 'zh:', len(z), 'missing:', len(b-z), 'extra:', len(z-b))
print('missing_keys:', sorted(b-z)[:20])
PY
```
