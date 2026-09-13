# W1-C keyboards — 排程深審班（2026-09-13）

自動 PR 深審 routine 對 #10 跑的一輪：一個 reviewer 視角（配列資料正確性／測試強度／
strict 驗證／跨模組介面）＋ parent 複驗。本輪所有結論都在本機實跑驗證過
（`ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew
:keyboards:testDebugUnitTest --rerun-tasks`；一定要加 `--rerun-tasks`，否則拿到的是
build cache 的舊結果，那不是證據）。

基準與本輪後皆為 46 tests / 0 failures——本輪沒有新增測試，改的是兩條既有測試的掃描範圍。

## 一、兩條測試只掃短按，沒掃長按

`reachableChars()` 這個輔助函式的 KDoc 自己就寫明，問「使用者打不打得出這個字」時
必須連 `longPress` 一起算，否則「一旦鍵開始帶 `longPress` 就會漏報」。同一個檔案裡有
四條測試照做了，但底下兩條沒有：

### 1. `symbol keyboard characters are all full-width`（medium）

它只看 `key.action`，不看 `key.longPress?.action`。而**這個 PR 自己就給符號鍵盤加了一個
longPress**（owner 2026-09-08 裁決：破折號 `—` 掛在 `－` 的長按上）。也就是說，這條測試
要擋的「半形字元偷渡進全形標點鍵盤」，現在有一條它看不到的路。

**A/B 實測**（在一個原本沒有 longPress 的鍵上掛半形逗號 `,`）：

| | 結果 |
|---|---|
| 修正後的測試 | **FAILED**（`expected full-width punctuation, got ASCII ','`） |
| 修正前的測試（`git show HEAD:…`） | **46 tests / 0 failures** — 放行 |

目前之所以還沒出事，純粹是既有的那一個 longPress 剛好是非 ASCII 的 `—`。

> 過程中的一個教訓：第一版突變是把現有的 `—` **改成** `-`，結果修正前的測試也紅——
> 但紅的是另一條（`symbol keyboard can type an em dash` 釘死了 `—` 必須可及），不是這一條。
> 換成「新增一個帶 ASCII 的 longPress」才隔離出真正的差異。**突變要打在缺陷會現形的那個
> 形態上，不是任何會讓測試變紅的改法。**

### 2. `url keyboard has slash, dot and a dedicated dot-com custom action`（low，防假紅）

同樣只掃短按。`/` 與 `.` 今天都是短按鍵，所以這條改動**今天是行為不變的**；它防的是
未來把其中一個改成長按可及時的**假紅**——而這個鍵盤的數字本來就已經是長按可及了，
所以「短按才算數」這個隱含前提在這份配列上已經不成立。

**A/B 實測**（把 `/` 改成長按可及、短按換成一個該鍵盤未使用的字元）：

| | 結果 |
|---|---|
| 修正後的測試 | **46 tests / 0 failures** — 正確認得長按 |
| 修正前的測試 | **FAILED**（`url keyboard missing '/' or '.'`）＝假紅 |

> 這裡也踩了一次坑：第一版突變把短按換成 `~`，結果兩邊都紅——因為 `~` 這個鍵盤上已經有了，
> 撞到 `no keyboard has a duplicate Character key`。改用未使用的字元才得到乾淨的 A/B。

## 二、複驗過、**沒有**發現問題的項目

- **注音配列資料**：37 個注音符號（21 聲母＋16 韻母）＋4 個聲調，portrait 與 landscape
  兩份都齊全、無重複、無多餘，位置符合標準大千配列。
- **八份配列無重複鍵**：任一鍵盤內的 `Character`／`Zhuyin`（短按與長按合計）皆無重複——
  已有 `no keyboard has a duplicate Character key` 在守，本輪的第二個突變就是被它抓到的。
- **退化點**：`no keyboard is empty` 已擋住「某份配列整個空掉仍全綠」。
- **恆真／自我印證斷言**：逐條看過，每條「釘住 X」的測試都用寫死的字面期望值，沒有
  拿被測配列自己算出期望值再回頭比對的情形。
- **空白鍵標籤**：六份帶空白鍵的配列全部用「空白」（不是 U+3000），owner 2026-09-08 的
  裁決是**一致**落地的，不是只改了一半。
- **KDoc 裡的列寬總和**：`passwordQwerty`／`urlQwerty` 註解寫的 10/9/9.6/7.6 與
  10/9/9.6/8.8，與 JSON 實際值逐列吻合。
- **跨模組介面（W1-A／W1-B）**：`:decoder` 與 `:theme` 目前都還是單一常數的
  `Placeholder.kt`，`:ime`／`:app`／`:settings` 也還沒有任何程式碼引用 `Keyboards.*`——
  這一項現階段**無從查證**，不是「查過沒問題」。等 `:decoder` 的真實 JNI 綁定落地後
  值得補一次 smoke test。

## 三、仍開著、本輪無新證據的三項

`contentDescription`、間隔號（U+30FB）的選擇、`url_qwerty` 的空白鍵——都是 2026-09-08
就記錄在案、待 owner 裁決的設計題。本輪沒有取得任何新證據，故不重提也不推翻。
