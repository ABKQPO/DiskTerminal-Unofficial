# DiskTerminal-Unofficial 1.7.10 Port Design

## English

### Goal

Port the Minecraft 1.12 DiskTerminal mod to Minecraft 1.7.10 as `DiskTerminal-Unofficial`, with mod id `disk_terminal`. The mandatory runtime dependency is `Applied-Energistics-2-Unofficial`. The port must preserve the terminal, wireless terminal, storage-cell management, storage-bus management, subnet overview, partition editing, search, filtering, priority editing, world highlight, and quick partition workflows.

The port must remove the 1.12 integrations for Mekanism Energistics, ECO AE Extension, Crazy AE, and JEI. JEI behavior is replaced with NotEnoughItems where 1.7.10 APIs allow equivalent behavior.

### Source Repositories

All required source repositories were found under `E:\Github`:

- `DiskTerminal`
- `DiskTerminal-Unofficial`
- `Applied-Energistics-2-Unofficial`
- `WirelessCraftingTerminal`
- `AE2FluidCraft-Rework`
- `ThaumicEnergistics`
- `NotEnoughItems`
- `GT5-Unofficial`
- `Programmable-Hatches-Mod`

`GT5-Unofficial` and `Programmable-Hatches-Mod` are optional scan targets. DiskTerminal will only add dedicated integration for them if source review finds a concrete AE storage, external inventory, partition, GUI, wireless terminal, or NEI-facing integration point. If no such point exists, no integration stub will be added.

### Architecture

The port is organized around four boundaries.

Core AE2 integration owns the terminal part, wireless terminal item, containers, network access, ME drive and ME chest discovery, storage-bus discovery, priority edits, cell insertion and ejection, temporary cell area, subnet discovery, and server-side actions. This layer depends on AE2-Unofficial and must not contain NEI or Thaumic-specific behavior.

Stack type adaptation owns all `IAEStack` behavior. It serializes, deserializes, matches, displays, renders, names, and partitions stacks through AE2's registered stack types. GUI, packets, and container handlers must not assume that every stack is an `ItemStack`.

GUI and interaction code owns tabs, widgets, partition slots, virtual content slots, buttons, tooltips, and highlight interaction. It consumes generic storage data and generic stack representations from the stack type adaptation layer.

Optional integrations isolate mod-specific APIs. Thaumic Energistics, AE2FluidCraft-Rework, WirelessCraftingTerminal, and NotEnoughItems must not be loaded or displayed when the corresponding mod is absent.

### Registered Stack Type Compatibility

AE2-Unofficial provides `appeng.api.storage.data.AEStackTypeRegistry`. The design must be registry-driven:

- Use `AEStackTypeRegistry.getAllTypes()` when scanning or aggregating all storage channels.
- Use `AEStackTypeRegistry.getSortedTypes()` where stable GUI ordering is needed.
- Use `IAEStackType.createList()` rather than hard-coded item or fluid list creation.
- Use `IAEStack.getStackType()` for stack identity and routing.
- Use `IAEStackType.loadStackFromNBT()` and type ids for generic NBT restoration.
- Use `IAEStackType.isContainerItemForType()`, `getStackFromContainerItem()`, and `convertStackFromItem()` for generic ghost ingredient and partition conversion.

Item, fluid, and essentia logic may still have narrow display adapters where their APIs require it, but the default path must work for any stack type registered into AE2's registry. Thaumic Energistics registers `AEEssentiaStackType.ESSENTIA_STACK_TYPE` during preInit, so essentia support must naturally appear through the registry when the mod is loaded.

### Stack Features

The stack type adaptation layer must support:

- `IAEItemStack`
- `IAEFluidStack`
- `thaumicenergistics.common.storage.AEEssentiaStackType`
- Future registered `IAEStackType` implementations when possible

It must cover:

- NBT serialization and deserialization
- Packet-safe type identifiers
- Matching and equality for partition filters
- Content preview rows
- Display `ItemStack` conversion
- Localized display names
- Tooltip data
- GUI rendering with GL state protection
- Creative cell and creative fluid cell partition editing following AE2 Cell Workbench rules

Unknown stack types must not crash the GUI or server. They should preserve data where possible and show a clear unknown-type fallback when rendering is impossible.

### GUI Design

Tabs use the vanilla creative inventory tab visual style. DiskTerminal's atlas remains available for non-tab buttons and mod-specific icons.

Required rendering rules:

- Unselected tab `ItemStack` icons render 2 pixels lower.
- Non-tab button `ItemStack` icons render 1 pixel to the right.
- Item and block stacks rendered on buttons and tabs must preserve normal lighting.
- `IAEStack` rendering must restore GL color, lighting, blend, depth, matrix, and z state after each draw.
- Tab hitboxes must match the rendered positions.
- Block coordinate labels must display localized block names, not raw translation keys.

### NEI and Ghost Ingredients

All JEI-facing code, comments, user-visible text, and docs must be renamed or rewritten. Internal generic drag/drop and partition concepts use `GhostIngredient`. Classes that directly call NotEnoughItems APIs use `NEI`.

NEI integration must provide:

- Drag or hover based partition marking into cell partition slots.
- Drag or hover based partition marking into storage-bus partition slots.
- Quick partition from NEI-visible ingredients.
- Recipe and usage lookup with R/U on virtual displayed stacks.
- No visible NEI controls or behavior when NotEnoughItems is absent.

All ghost ingredient conversion must route through registered AE stack types first, then through narrow adapters only when required.

### Optional Mod Integrations

`AE2FluidCraft-Rework` provides fluid storage behavior. Fluid cells and fluid storage buses must appear only when the mod and relevant AE2 support are present.

`ThaumicEnergistics` provides essentia stack type support. Essentia cells, essentia storage buses, essentia display, and essentia partition editing must appear only when the mod is present.

`WirelessCraftingTerminal` is used as the reference for wireless access, binding, power, and range behavior where it intersects DiskTerminal's wireless terminal.

`GT5-Unofficial` and `Programmable-Hatches-Mod` are reviewed for real DiskTerminal integration points. No placeholder integration is added without a concrete feature.

### Removed Integrations

The port removes:

- Mekanism Energistics
- ECO AE Extension
- Crazy AE
- JEI

Remaining references to these integrations in Java names, comments, configs, README files, lang files, and GUI text are migration bugs unless they are historical notes in this design document.

### Resources and Localization

Textures needed by the port are copied from the 1.12 project and adapted for 1.7.10. Model JSON files from 1.12 are not blindly copied as runtime models because Minecraft 1.7.10 uses different item and part rendering mechanisms.

Documentation and data text must be bilingual:

- English README
- Chinese README
- English lang file
- Chinese lang file
- Any user-facing migration or feature documentation in both English and Chinese

### Verification

No unit tests are allowed for this task. Verification is limited to IDEA MCP executing Gradle tasks:

- `spotlessApply`
- `compileJava`

The following are not allowed:

- `test`
- `check`
- `spotlessCheck`
- `runClient`
- `runServer`

### Code Quality Rules

The implementation must:

- Read and write files as UTF-8 without BOM.
- Use imports instead of fully qualified class references.
- Avoid package-private and `final` classes, except records.
- Keep all comments in English.
- Avoid dashed or equals-sign comment separators.
- Prefer clear boundaries over tightly coupled feature checks.
- Avoid empty wrapper methods and placeholder integration classes.
- Use modern Java syntax and library APIs where the configured toolchain supports them.

## 中文

### 目标

将 Minecraft 1.12 的 DiskTerminal 模组移植到 Minecraft 1.7.10，项目名称为 `DiskTerminal-Unofficial`，模组 id 为 `disk_terminal`。强制运行依赖是 `Applied-Energistics-2-Unofficial`。移植后需要保留终端、无线终端、存储元件管理、存储总线管理、子网概览、分区编辑、搜索、过滤、优先级编辑、世界高亮和快速分区等流程。

移植必须移除 Mekanism Energistics、ECO AE Extension、Crazy AE 和 JEI 的 1.12 集成。JEI 行为在 1.7.10 中由 NotEnoughItems 能力替代。

### 源码仓库

需要的源码仓库都已经在 `E:\Github` 下找到：

- `DiskTerminal`
- `DiskTerminal-Unofficial`
- `Applied-Energistics-2-Unofficial`
- `WirelessCraftingTerminal`
- `AE2FluidCraft-Rework`
- `ThaumicEnergistics`
- `NotEnoughItems`
- `GT5-Unofficial`
- `Programmable-Hatches-Mod`

`GT5-Unofficial` 和 `Programmable-Hatches-Mod` 是可选扫描目标。只有在源码审查中发现明确的 AE 存储、外部库存、分区、GUI、无线终端或 NEI 相关接入点时，DiskTerminal 才会添加专门集成。如果没有明确接入点，不添加空壳集成。

### 架构

移植按四个边界组织。

核心 AE2 集成负责终端部件、无线终端物品、容器、网络访问、ME 驱动器和 ME 箱子发现、存储总线发现、优先级编辑、元件插入和弹出、临时元件区、子网发现和服务端动作。这一层依赖 AE2-Unofficial，但不包含 NEI 或 Thaumic 专用逻辑。

StackType 适配层负责所有 `IAEStack` 行为。它通过 AE2 已注册的 StackType 处理序列化、反序列化、匹配、显示、渲染、命名和分区。GUI、网络包和容器处理器不能假设所有内容都是 `ItemStack`。

GUI 与交互层负责标签、组件、分区槽、虚拟显示槽、按钮、tooltip 和高亮交互。它只消费 StackType 适配层给出的通用存储数据和通用堆栈表示。

可选集成层隔离模组专用 API。Thaumic Energistics、AE2FluidCraft-Rework、WirelessCraftingTerminal 和 NotEnoughItems 在对应模组未安装时不能加载专用功能，也不能显示对应入口。

### 已注册 StackType 自动兼容

AE2-Unofficial 提供 `appeng.api.storage.data.AEStackTypeRegistry`。设计必须由注册表驱动：

- 扫描或聚合所有存储通道时使用 `AEStackTypeRegistry.getAllTypes()`。
- GUI 需要稳定排序时使用 `AEStackTypeRegistry.getSortedTypes()`。
- 使用 `IAEStackType.createList()`，不能硬编码只创建物品或流体列表。
- 使用 `IAEStack.getStackType()` 做堆栈身份和路由。
- 使用 `IAEStackType.loadStackFromNBT()` 和类型 id 做通用 NBT 恢复。
- 使用 `IAEStackType.isContainerItemForType()`、`getStackFromContainerItem()` 和 `convertStackFromItem()` 做通用 ghost ingredient 和分区转换。

物品、流体、源质仍然可以在确实需要时保留很窄的显示适配，但默认路径必须尽可能支持所有注册到 AE2 注册表的 StackType。Thaumic Energistics 会在 preInit 注册 `AEEssentiaStackType.ESSENTIA_STACK_TYPE`，所以安装该模组时源质能力应自然通过注册表出现。

### 堆栈功能

StackType 适配层必须支持：

- `IAEItemStack`
- `IAEFluidStack`
- `thaumicenergistics.common.storage.AEEssentiaStackType`
- 未来可能注册的其他 `IAEStackType` 实现

覆盖范围包括：

- NBT 序列化和反序列化
- 网络包安全的类型标识
- 分区过滤匹配和相等判断
- 内容预览行
- 显示 `ItemStack` 转换
- 本地化显示名
- Tooltip 数据
- 带 GL 状态保护的 GUI 渲染
- 创造元件和流体创造元件分区编辑，行为遵从 AE2 元件工作台

未知 StackType 不能导致 GUI 或服务端崩溃。能够保留数据时应保留数据，无法渲染时显示清晰的未知类型兜底。

### GUI 设计

标签页使用原版创造模式物品栏标签页视觉风格。DiskTerminal 自己的 atlas 保留给非标签按钮和模组自身图标。

必须满足的渲染规则：

- 未选中标签页的 `ItemStack` 图标向下渲染 2 像素。
- 非标签按钮上的 `ItemStack` 图标向右渲染 1 像素。
- 方块和物品堆栈在按钮、标签页上的渲染必须保持正常光照。
- `IAEStack` 每次渲染后必须恢复 GL color、lighting、blend、depth、matrix 和 z 状态。
- 标签点击范围必须匹配实际渲染位置。
- 方块坐标标签显示本地化方块名称，不能显示原始翻译键。

### NEI 与 Ghost Ingredient

所有 JEI 相关代码、注释、用户可见文本和文档都需要重命名或重写。内部通用拖拽、悬停和分区概念使用 `GhostIngredient`。直接调用 NotEnoughItems API 的类使用 `NEI` 命名。

NEI 集成必须提供：

- 拖拽或悬停标记到元件分区槽。
- 拖拽或悬停标记到存储总线分区槽。
- 从 NEI 可见内容快速分区。
- 在虚拟显示堆栈上按 R/U 查询配方和用途。
- NotEnoughItems 未安装时不显示 NEI 控件，也不启用 NEI 行为。

所有 ghost ingredient 转换都优先通过已注册 AE StackType，只有必要时才使用很窄的专用适配。

### 可选模组集成

`AE2FluidCraft-Rework` 提供流体存储行为。只有在模组和相关 AE2 支持存在时，才显示流体元件和流体存储总线功能。

`ThaumicEnergistics` 提供源质 StackType。只有在模组存在时，才显示源质元件、源质存储总线、源质显示和源质分区编辑。

`WirelessCraftingTerminal` 用作无线访问、绑定、能量和范围行为的参考，在与 DiskTerminal 无线终端交叉的地方进行兼容。

`GT5-Unofficial` 和 `Programmable-Hatches-Mod` 只审查是否存在真实 DiskTerminal 接入点。没有具体功能时，不添加占位集成。

### 移除的集成

移植移除：

- Mekanism Energistics
- ECO AE Extension
- Crazy AE
- JEI

Java 命名、注释、配置、README、lang 和 GUI 文本中残留这些集成引用，都视为迁移缺陷，除非是在本文档中作为历史说明出现。

### 资源与本地化

移植需要的纹理从 1.12 项目复制并适配到 1.7.10。1.12 的模型 JSON 不直接作为运行时模型照搬，因为 Minecraft 1.7.10 使用不同的物品和部件渲染机制。

文档和资料文本必须中英双语：

- 英文 README
- 中文 README
- 英文 lang 文件
- 中文 lang 文件
- 所有用户可见的迁移或功能文档都提供中英双语

### 验证

本任务禁止单元测试。验证仅限通过 IDEA MCP 执行 Gradle 任务：

- `spotlessApply`
- `compileJava`

禁止执行：

- `test`
- `check`
- `spotlessCheck`
- `runClient`
- `runServer`

### 代码质量规则

实现必须：

- 以 UTF-8 无 BOM 读取和写入文件。
- 使用 import，禁止全限定名引用类。
- 禁止包私有类和 `final` 类，record 除外。
- 所有注释使用英语。
- 禁止使用虚线或等号分隔注释。
- 优先使用清晰边界，避免紧耦合功能判断。
- 避免空壳方法和占位集成类。
- 在当前工具链支持范围内使用现代 Java 语法和库 API。
