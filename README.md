# aozaink-sigillum

开发工程名为 `aozaink-sigillum`；面向玩家的发布产物名为 `molu-sigillum`，属于 Molu / 墨箓的符箓魔法玩法分类。

官方样例符箓玩法模组。基于 Aozai Ink Core 的手写识别事件实现基础符箓玩法面，用于验证 `core + input + gameplay` 的端到端链路。

拉丁语 *sigillum*：封印、符印。

## 当前状态

sigillum 已具备一条端到端可玩链路：

- 向 core 注册字集；向 Input 注册十二字说明和「刻印」菜单栏目；监听已由 Input 解析 owner 的快速吟唱事件。
- 命令：`/sigillum dev/bind/unbind/list`（需 op + dev）；`/sigillum menu`（**任何玩家、无需权限**）打开菜单。
- 共享「墨箓」菜单、默认 M 键和指定符绑定位于 Input；Sigillum 只贡献自己的字典和刻印栏目。
- 读取黄符 `CustomData`：刻印符（服务端持久范围效果）与组合符；指定符由 Input 消耗。
- **品质等级**：读符上 `aozaink:grade<格>` → `TalismanGrade` → 效果倍率（极品 100% / 良品 80% / 劣质 55% / 废符 0%）。
- **单字施法效果（v1：镇 / 封 / 退 / 引 / 火 / 雷 / 护 / 净 / 斩 / 明 / 吸 / 魄）**：`SkillCast` 出效果 + `SigillumEffectTicker`（服务端 tick 调度火 DoT 等持续效果）。不同技能读取的倍率通道不同，精确行为见 `IMPLEMENTATION.md`。视线 16 格命中、未命中落地可拾回；废符溃散。
- **修饰字（v1：强 / 续 / 广 / 穿）**：强=效果倍率 `overallM×2.0`，续=持续倍率 `1+overallM`，广=范围化（普通目标技能半径4格，明为24格照妖），穿=穿透射线（极品3/良品2/劣质1 目标，命中后降档复用）。广和穿不能同时出现，同时出现判废符。
- **组合符**：普通黄符为两个咒位 + 一个尾修；前两格可写技能字或修饰字，第三格只接受修饰字或留空。12 个技能字的 66 对 pair 都会触发**二字特殊联动**；二技能带一个尾修时继续走联动，并由 `强/续/广/穿` 作为整体外层策略。单技能可带 1~2 个修饰；`穿` 在二技能中按 pair 支持/拒绝。
- **成就/Advancement**：所有自定义 criterion 注册在 sigillum。input 只广播中性输入结果信号，sigillum 监听后决定是否触发成就。
- 使用反馈走 **action bar** 短提示，不刷聊天框。

当前剩余主要是视觉素材、提示文案和数值手感微调；多人输入链路已走服务端提交与识别。

`IMPLEMENTATION.md` 记录当前代码事实；`DESIGN.md` 记录玩法计划和未实现联动，不代表当前实现已覆盖。

## 职责

- 监听 `InkRecognizedEvent`，按识别字 + `InkSource` 触发玩法。
- 监听 input 的中性结果信号，并把它们解释为 sigillum 自己的成就条件。
- 读黄符稳定 NBT/CustomData，解释指定符/刻印符/组合符，并把符上**品质等级换算成效果倍率**施放。
- 管理 sigillum 自己的字→语义映射、实际施法与刻印；不再拥有通用绑定存档或共享菜单。

## 不负责

- 识别推理（交给 core）。
- 输入采集（交给 input 或用户自选输入模块）。
- 定义其他模组的字的含义（每个玩法自己建 `GlyphSemantics`）。
- 反向要求 Input 依赖 Sigillum。

## 依赖

依赖 `aozaink-core` 与 `aozaink-input`。Core 仍是开放接口层；Input 是官方 Molu 的通用交互与菜单层。

```groovy
dependencies {
    implementation "net.neoforged:neoforge:${neo_version}"
    implementation project(":aozaink-core")
}
```

sigillum 通过稳定物品 ID `aozaink_input:yellow_talisman` 和 `CustomData` 字段与 input 产物协作。

## 当前字表

印契拥有六组母义：定（镇/封）、行（退/引）、化（火/雷）、济（护/净）、决（明/斩）、摄（吸/魄）。这些是 Sigillum 的字权，不是其他模块可复用的公共字表；完整规则见 [`../GLYPH_OWNERSHIP.md`](../GLYPH_OWNERSHIP.md)。

|字|标签|方向|
|----|------|------|
|镇|`sigillum.suppress`, `sigillum.control`|镇压、控制|
|封|`sigillum.seal`, `sigillum.ward`|封印、防护|
|退|`sigillum.repel`, `sigillum.cleanse`|驱退、清除|
|引|`sigillum.lure`, `sigillum.pull`|引诱、牵引|
|火|`sigillum.fire`, `sigillum.element`|燃烧、元素|
|雷|`sigillum.thunder`, `sigillum.element`|雷击、元素|
|护|`sigillum.shield`, `sigillum.protect`|护盾、守护|
|净|`sigillum.purify`, `sigillum.heal`|净化、治疗|
|斩|`sigillum.execute`, `sigillum.attack`|斩杀、处决|
|明|`sigillum.reveal`, `sigillum.light`|显形、照明|
|吸|`sigillum.drain`, `sigillum.heal`|吸血、汲取|
|魄|`sigillum.soul`, `sigillum.recall`|招魄、召回|

印契尾修字：强（效果强化）、续（延时）、广（范围）、穿（穿透）。它们只属于 Sigillum；Input 统一第三格尾修格式，但未来模块必须拥有自己的尾修字。广与穿互斥，同时写在同一张普通符上判废符。

## 玩家联测路径

1. 安装 `aozaink-core`、`aozaink-input`、`aozaink-sigillum`。
2. 用黄符三格 UI 写符并点击“成符”。
3. 主手右键已成符黄符：
   - 指定符：把数字绑定到目标字。
   - 刻印符：`刻` 写在前两个咒位之一，第三格只作尾修；对方块右键创建单技能持久刻印，`刻+续/强/广` 可对已有刻印续时、加强或扩围，两个合法修饰字可连续执行已有刻印操作。
   - 组合符：2 技能字触发二字特殊联动；单技能可带 1~2 个修饰；2 技能字 + 尾修继续触发二字联动并应用尾修策略；新符不支持 3 技能字结构。
   - 废符：提示无法使用。
4. 主手拿原版纸右键展开白纸临时施写，左键写中文数字，再次右键识别；若该数字已有绑定，sigillum 显示快速吟唱结果。

## 开发命令

```text
/sigillum dev
/sigillum bind <1-9> <glyph>
/sigillum unbind <1-9>
/sigillum list
/sigillum menu            # 任何玩家可用，无需 op/dev；也可按键位（默认 M）打开
```

除 `dev` 与 `menu` 外，其余命令需要先开启 sigillum 开发模式。`menu` 不需要任何权限。

## 构建验证

改动 `aozaink-input` 或 `aozaink-sigillum` 后，至少跑：

```powershell
cd H:\projects\projectmc\aozaink
.\gradlew.bat :aozaink-input:build :aozaink-sigillum:build
```

只跑 `test` 不够；`build` 会覆盖编译、资源处理、测试和 jar 产物。发布单 jar 另跑：

```powershell
.\gradlew.bat :aozaink-all:build
```
