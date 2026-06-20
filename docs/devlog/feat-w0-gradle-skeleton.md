# W0-1 devlog — Gradle 骨架

- 分支：`feat/w0-gradle-skeleton`
- PR：[#2](https://github.com/Bruce1986/BopomofoBruce/pull/2)
- 期間：2026-06-20 17:38 – 19:14 (UTC+8)

## 交付

8 個 module 空殼（`:app` `:ime` `:keyboards` `:decoder` `:decoder-native`
`:theme` `:settings` `:common`）、Gradle wrapper 8.10.2、Version Catalog、
`subprojects` 集中 ktfmt + JUnit Platform + Kotlin compilerOptions、Room
Gradle Plugin、Configuration Cache。

## Review 統計

- 本機 Codex：2 輪收斂
- Gemini：12 輪、23 inline Medium，最終「I have no feedback」
- CodeRabbit：1 輪 1 critical + 1 nitpick（後續 incremental 皆無 finding）
- Commit 數：11（含 1 hotfix）

## 主要決定 / push-back

- `subprojects {}` 集中設定（vs Convention Plugin）— 留 in-code Gemini-review
  rationale，scope 太大轉 W3 後 follow-up
- `rootProject.libs.plugins.ktfmt` — 在 subprojects scope 不能直接寫 `libs`
  （實測 Extension 'libs' does not exist），留 in-code rationale + commit SHA
- `themes.xml` 不採用 Gemini 第一次建議的 `android:Theme.Material.DayNight`
  （framework 不存在，AAPT 直接 fail）— 改採 Gemini 後續建議的
  `android:Theme.DeviceDefault.NoActionBar`
- `:common` 從 Android Library 改純 Kotlin JVM（強制與 Android API 解耦）
- Room 走官方 Room Gradle Plugin 取代手動 `ksp { arg("room.schemaLocation") }`

## 子代理協作回饋（餵回 DEVPLAN §11）

- 子代理一次性收尾全套（claim → 實作 → codex → PR → bot loop）對單一 wave-0
  skeleton 是太大的 token 預算；改成「子代理只做實作 + 本機 build」，主代理
  接手 codex / PR / bot review loop，token 友善許多
- 主代理 review loop 中 bash `2>&1 | tail -N` 會吃掉 gradle exit code，需用
  `echo "===EXIT $?===" >> log` 才能可靠判斷成敗
- Gradle 第一次 setup（下 distribution、build 8 module）約 5–10 分鐘，後續
  cc hit 後 < 1 分鐘
