# Architectural Decision Records

本目錄記錄影響 BopomofoBruce 長期演進的關鍵架構決定。

ADR（Architectural Decision Record）的目的：把「當下為什麼這樣選」用未來自己看得懂的方式寫下來。三年後 / 接手者翻 commit log 想知道「為什麼是這個方案」時，這裡是第一個翻的地方。

---

## 規則

1. 每份 ADR 一個檔案，編號連續 (`NNNN-kebab-case-title.md`)。檔名沿用業界 well-known 的 ADR 編號慣例（不掛日期前綴），但每份 ADR 內部 metadata bullet 須明列 `日期：YYYY-MM-DD`。
2. 狀態欄走以下流轉：
   - `Proposed`：草稿、提案中、未拍板
   - `Accepted`：已採用、規範後續開發
   - `Rejected`：寫了但最後沒採用（保留供後人理解）
   - `Superseded by ADR-XXXX`：被新 ADR 取代
3. **已 `Accepted` 的 ADR 禁止編輯內容**。觀點若改變，開新 ADR 並把舊 ADR 狀態改為 `Superseded by ADR-XXXX`，舊內文保留不動。
4. 用 ADR template ([`template.md`](template.md)) 起手；五段必備：Context / Decision / Consequences / Alternatives considered / References。
5. ADR 是給「未來的 Bruce」看的，不是給 reviewer 看的。寫具體的 trade-off、誠實寫負面後果，不要寫「我們很棒所以選 X」這種無內容話。

---

## 目前已決定

- [ADR-0001](0001-libchewing-decoder-backend.md) — decoder 後端選 libchewing（不自寫 HMM）
- [ADR-0002](0002-eight-modules-not-thirty.md) — 8 個 Gradle module 而非 30
- [ADR-0003](0003-compose-only-no-xml.md) — Compose-only UI（IME view 不寫 XML layout）
- [ADR-0004](0004-no-hilt-manual-di.md) — 手動 DI，不引入 Hilt
- [ADR-0005](0005-no-cloud-sync-v1.md) — v1 不做雲端同步

---

## 流程

1. 出現一個會影響「跨 module / 跨 wave / 跨 v1-v2」的決定 → 開 ADR
2. 複製 `template.md` → `NNNN-<kebab-title>.md`，編號接續最後一份
3. 寫 `Proposed` 草稿、在 PR 內走 review
4. Merge 前狀態改 `Accepted`
5. 在 README 這份「目前已決定」清單加一行
