# FitRecord V1 验收清单（对照设计文档 §8 的 DoD）

- 日期：2026-10-13
- 分支：`feat/fitrecord-v1`
- 交付 APK：`fitrecord-v1.0.apk`（7,733,272 B，`versionCode=1 / versionName=1.0`）
- 全量单测：**175 tests / 0 failures**（`gradlew :app:testDebugUnitTest`）
- 每条结论都标 `PASS`（有代码/命令证据）/ `MANUAL`（需真机）/ `N/A`（本版本无该需求，守护已就位）

| # | DoD 条目 | 结论 | 证据 |
|---|---|---|---|
| 1 | `assembleRelease` 成功且**已自签名** | **PASS** | `BUILD SUCCESSFUL`；`apksigner verify --print-certs` → `Signer #1 certificate DN: CN=FitRecord, OU=Dev, O=FitRecord`（非 debug key） |
| 2 | Android 8.0+ 真机安装/启动无崩溃、冷启动 ≤ 2s | **MANUAL** | 静态面已核：`minSdkVersion:'26'`、`enableEdgeToEdge()` 已接、种子幂等；真机安装与冷启动计时需在设备上验证 |
| 3 | P0–P10 + P8b 全实现，深色模式无一处硬编码颜色 | **PASS(代码) / MANUAL(观感)** | 10 屏 + 引导（无体重时 P3「完善身体参数」卡与 P1 Banner）全部接线；本轮把图表 Canvas 的固定深灰（环形中心 `0xFF202020`、雷达轴名 `0xFF606060`、热力格数字 `0xFF204030`、网格 `0x33/0x40 808080`、轴标签 `0xFF808080`）全部改为进入 `Canvas` 前捕获 `MaterialTheme.colorScheme` 的 `onSurface/onSurfaceVariant/outline` 再注入；类型编辑弹窗选中边框 `Color.Black` → `colorScheme.primary`。深色观感最终确认建议真机走一遍 |
| 4 | 增删改记录 → 日历即时更新 → 统计五图正确聚合 → 卡路里/食物等价一致 | **PASS(逻辑) / MANUAL(交互)** | `TrainingRepositoryTest`、`CalendarBuilderTest`、`StatsRepositoryTest`（14 条）、`CalorieCalculatorTest`、`FoodEquivalentTest` 全绿；渲染不崩由 `AppStartupTest` + `ViewModelFactoryTest` 兜，点击→刷新整链建议真机走一遍 |
| 5 | 自定义运动类型（MET 必填 + 参考表回填）→ 出现在选择器与图表 | **PASS** | `TypesViewModel` draft 模式 + MET 参考表回填；删除前查 `usageCount`，被引用改走归档（规格 6.4）；`ViewModelFactoryTest` 覆盖工厂构造不擦除（真机闪退回归） |
| 6 | 导出 JSON → 卸载重装 → 导入 → 记录数与卡路里完全一致 | **PASS(编解码/事务) / MANUAL(真机 SAF)** | `BackupRepositoryTest`：导出→清空→回导 2 条完全一致、重导幂等 no-op、未知类型自动创建、身体参数与每日备注随 JSON 恢复、同键重复行不过计数；严格模式在**校验阶段**拒绝脏数据（整批不进入写入），且类型/记录/参数/备注写入同处一个 `withTransaction`；真机 SAF 选择文件 + 卸载重装路径需手测（见安装说明） |
| 7 | 改实体字段升版本后旧数据**无损迁移** | **N/A(V1) / 守护 PASS** | V1 schema v1 未发生字段变更，`Migrations.ALL` 空；「升级不丢数据」有行为级守护：`SchemaGuardTest` 守护 5 把 `PRAGMA user_version` 拨到 99 再重开必须报错，且**变异检验**（真加 `fallbackToDestructiveMigration()` 立即变红）确认是真闸门。首个真实 Migration 落地时须补迁移往返测试（台账 L1 前瞻） |
| 8 | 全程无任何网络权限（Manifest 不出现 `INTERNET`） | **PASS** | `ManifestContractTest`（4 条）无 INTERNET/网络状态/外部存储/媒体权限且 `allowBackup=false`；`aapt2 dump badging` 仅见 androidx 注入的 signature 级自定义权限 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`，无 `INTERNET` |

## 本轮（M4/M5）新增功能闭环
- **P9 数据备份与恢复**：JSON 完整备份 / CSV 记录表导出，导入前预览（可导入/新增/覆盖/跳过/错误计数）、冲突策略「跳过 / 覆盖」单选、覆盖走破坏性确认、脏行不阻断整批但列出。
- **降级状态（规格 6.9 / 6.10）**：迁移失败 → 只读红色横幅 + 日历 FAB 写操作空转；数据库彻底损坏（SQLITE_NOTADB）→ 整屏恢复页，两条出路（重建空库 / 重建后从备份导入，删库 + 一次性标记 + 重启直达备份页）。

## 已知遗留（交 V1.1 / 最终全分支评审）
- 真机项 2、6 的 SAF 全流程、深色观感需人工确认（本 harness 无设备）。
- 统计页多 flow combine 上限 5（台账 T21 记录）；撕裂读已缩小未消除（V1 接受）。
- 首个真实 DB Migration 落地时补迁移往返测试（关闭台账 L1 前瞻项）。
