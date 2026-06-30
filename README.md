# aozaink-sigillum

官方样例符箓玩法模组。基于 Aozai Ink Core 的手写识别事件实现基础符箓玩法面，用于验证 `core + input + gameplay` 的端到端链路。

拉丁语 *sigillum*：封印、符印。

## 当前状态

sigillum 已具备一条端到端可玩链路：

- 向 core 注册字集，监听 `InkRecognizedEvent`，处理识别反馈与白纸快速吟唱。
- 命令：`/sigillum dev/bind/unbind/list`（需 op + dev）；`/sigillum menu`（**任何玩家、无需权限**）打开菜单。
- 自绘「符咒·设置」菜单（中华风纯色块）：**快速吟唱页**（看 1-9 绑定字 + 效果、可清除；改绑请写指定符，菜单内不写字）、**黄符字典页**（12 字含义）。命令或键位（默认 **M**，可在原版控制里改）打开。
- 用 `GlyphBinding` 把中文数字 `一..九` 绑定常用字（写指定符）。
- 读取黄符 `CustomData`：指定符（绑数字）、刻印符（方块粒子反馈）、组合符。
- **品质等级**：读符上 `aozaink:grade<格>` → `TalismanGrade` → 效果倍率（极品 100% / 良品 80% / 劣质 55% / 废符 0%）。
- **单字施法效果（v1：火 / 雷 / 护 / 净）**，按品质倍率缩放：`SkillCast` 出效果 + `SigillumEffectTicker`（服务端 tick 调度火 DoT、护盾到期）。视线 16 格命中、未命中落地可拾回；废符溃散。
- 使用反馈走 **action bar** 短提示，不刷聊天框。

还没完成：镇/封/退/引 四字效果、修饰字（强/续/疾/广）全局加成、多字组合逐字缩放、刻印持续/范围周期效果、完整数值平衡、多人网络。

`DESIGN.md` 是待审核设计稿，不代表当前实现已完全覆盖。

## 职责

- 监听 `InkRecognizedEvent`，按识别字 + `InkSource` 触发玩法。
- 读黄符稳定 NBT/CustomData，解释指定符/刻印符/组合符，并把符上**品质等级换算成效果倍率**施放。
- 管理 sigillum 自己的字→语义映射、快速吟唱绑定，以及客户端菜单/键位。

## 不负责

- 识别推理（交给 core）。
- 输入采集（交给 input 或用户自选输入模块）。
- 定义其他模组的字的含义（每个玩法自己建 `GlyphSemantics`）。
- 依赖 `aozaink-input` 的 Java 类。

## 依赖

只依赖 `aozaink-core`。**不依赖 `aozaink-input`**。

```groovy
dependencies {
    implementation "net.neoforged:neoforge:${neo_version}"
    implementation project(":aozaink-core")
}
```

sigillum 通过稳定物品 ID `aozaink_input:yellow_talisman` 和 `CustomData` 字段与 input 产物协作。

## 当前字表

| 字 | 标签 | 方向 |
|----|------|------|
| 镇 | `sigillum.suppress`, `sigillum.control` | 镇压、控制 |
| 封 | `sigillum.seal`, `sigillum.ward` | 封印、防护 |
| 退 | `sigillum.repel`, `sigillum.cleanse` | 驱退、清除 |
| 引 | `sigillum.lure`, `sigillum.pull` | 引诱、牵引 |
| 净 | `sigillum.purify`, `sigillum.heal` | 净化、治疗 |
| 明 | `sigillum.reveal`, `sigillum.light` | 显形、照明 |

## 玩家联测路径

1. 安装 `aozaink-core`、`aozaink-input`、`aozaink-sigillum`。
2. 用黄符三格 UI 写符并点击“成符”。
3. 主手右键已成符黄符：
   - 指定符：把数字绑定到目标字。
   - 刻印符：对方块右键，显示刻印反馈并播放粒子。
   - 组合符：显示组合内容并播放基础粒子反馈。
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