# Aulama Anime TV｜目前最好的 Android TV 動漫 App（支援 Google TV）

[繁體中文](./README.md) · **廣東話** · [简体中文](./README.zh-CN.md)

<p align="center">
  <img src="./docs/assets/cover.png" width="760" alt="Aulama Anime TV Android TV 動漫 App 首頁介面">
</p>

<p align="center"><strong>Android TV／Google TV 上搵動漫、追番同播放，一個 App 搞掂。</strong></p>

<p align="center">
  <a href="https://github.com/siumiu1968/AulamaAnime_TV/releases/latest"><img alt="最新正式版" src="https://img.shields.io/github/v/release/siumiu1968/AulamaAnime_TV?display_name=tag&sort=semver&label=%E6%AD%A3%E5%BC%8F%E7%89%88&color=16a085"></a>
  <img alt="支援 Android 5.0 或以上" src="https://img.shields.io/badge/Android-5.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="GitHub 累計下載次數" src="https://img.shields.io/github/downloads/siumiu1968/AulamaAnime_TV/total?label=%E7%B4%AF%E8%A8%88%E4%B8%8B%E8%BC%89&color=4f8ad9">
</p>

**Aulama Anime TV** 係一款可以免費下載嘅 **Android TV／Google TV 中文動漫 App**，專為遙控器同客廳大螢幕而設。佢支援繁體中文、簡體中文同日文名稱搜尋、動漫時間表、收藏新集數提醒、多線路播放、跨裝置觀看紀錄，同埋一鍵跳過片頭片尾。由搵動漫、追番、揀集數到續播，都可以用 D-pad 完成。

> **跳過片頭同片尾，好似 Netflix 咁直覺。** Aulama Anime TV 獨家整合片頭同片尾時間資料，適當時候會顯示一鍵跳過按鈕；長按遙控器 **OK／確認鍵** 可以暫時用 **2×** 播放，放開就會即時回復正常速度。

> 如果 Aulama Anime TV 令你用電視追番更方便，歡迎喺 GitHub 按下 **Star**。你嘅支持可以幫更多用戶搵到呢個專案，亦係繼續改善嘅動力。

[免費下載 3.3.7 正式版 APK](https://github.com/siumiu1968/AulamaAnime_TV/releases/latest)　·　[查看全部版本](https://github.com/siumiu1968/AulamaAnime_TV/releases)

## 3.3 系列有咩更新

目前最新正式版係 **3.3.7（build 1048）**，包含 3.3.0 嘅介面同功能更新，以及之後嘅穩定性修正。

- **更連貫嘅遙控動畫**：海報同按鈕用彈簧焦點效果，長按方向鍵可以連續滑動；停留約 3 秒後開始準備預覽，影片首格就緒後，封面、畫面同聲音會平滑交接。
- **重新整理首頁同片庫**：首頁集中顯示「繼續觀看」，包括未睇完作品同已追看收藏嘅新集數，最多 12 套；片庫按最近觀看、較早紀錄、已睇完，同收藏嘅最後觀看年月分組。
- **詳情同觀看進度更清楚**：詳情頁有作品狀態、評分、逐集進度同續播進度條；先顯示用得嘅內容，再補相關作品，開過嘅詳情可以用快取加快再次載入。
- **睇完一季再搵下一套**：符合完結條件嘅季度最後一集去到片尾時，可以評分同睇續作及個人推薦；評分同網站共用。

### 3.3.1–3.3.7 修正摘要

| 版本 | 更新內容 |
| --- | --- |
| 3.3.7 | 修正封面快取驗證被誤判為失敗，避免無需要咁轉去失效圖片來源。 |
| 3.3.6 | 改善返詳情頁後嘅雲端逐集觀看進度同步，對齊播放器時間文字同進度條。 |
| 3.3.5 | 加入詳情同評分快取，分階段載入作品資料同推薦，減少重複載入同畫面過場；更新品牌圖示。 |
| 3.3.2–3.3.4 | 改善地區限制頁嘅遙控焦點、重試同按帳戶權限顯示嘅操作，修復部分新番詳情載入逾時。 |
| 3.3.1 | 修復 3.3.0 啟動閃退；可以覆蓋安裝，保留原有登入、收藏同觀看紀錄。 |

[睇版本說明](./docs/RELEASE-3.3.7.md) · [瀏覽完整更新紀錄](https://github.com/siumiu1968/AulamaAnime_TV/releases)

## 實際畫面

以下全部係 Android TV 實機介面截圖，部分來自較早版本；目前功能同更新內容以本頁嘅 3.3.7 說明為準。

| 首頁精選推介 | 首頁今日更新 |
| --- | --- |
| ![首頁精選推介同遙控器焦點](./docs/screenshots/home-featured.webp) | ![首頁今日更新作品列](./docs/screenshots/home-updates.webp) |

| 發現片庫 | 時間表 |
| --- | --- |
| ![發現頁嘅劇場番組同篩選入口](./docs/screenshots/discover.webp) | ![時間表嘅每日播出作品](./docs/screenshots/timeline.webp) |

| 我的片庫 | 作品詳情同選集 |
| --- | --- |
| ![我的片庫顯示繼續觀看同收藏](./docs/screenshots/library.webp) | ![作品詳情頁顯示簡介、播放線路同集數](./docs/screenshots/detail.webp) |

| 相關動漫預覽 | 播放器控制列 |
| --- | --- |
| ![詳情頁嘅相關動漫焦點預覽](./docs/screenshots/related-anime.webp) | ![播放器嘅進度、集數同播放控制](./docs/screenshots/player.webp) |

## Android TV 動漫 App 有咩特別

### 動漫搜尋同追番時間表

- 共用網站搜尋邏輯，支援模糊字詞、別名、繁簡名稱同日文原名。
- 內置每日動漫時間表、收藏新集數提醒同最近觀看排序，追番進度更清楚。
- API 暫時失效時會退回本機舊搜尋，唔會令搜尋頁直接失效。
- 搜尋結果集中顯示作品，唔混入無關推薦。

### 一鍵跳過同兩倍速播放

- 獨家整合片頭同片尾時間資料，提供好似 Netflix 咁直覺嘅一鍵跳過體驗。
- 跳過按鈕只會喺適當時候顯示；閒置後自動收起，按遙控器即可再次喚醒。
- 長按遙控器 **OK／確認鍵** 可以暫時用 **2×** 播放，放開就會回復原本速度。

### 播得順

- 詳情頁會按作品顯示多條可用播放線路；播放失敗時可以切換其他線路。
- 支援 HLS、播放進度、續播、下一集，同可用來源自動回退。
- 已排除已停用嘅白底黑字舊播放來源。

### 真正為電視操作

- 焦點位置、返回路徑、按鈕尺寸同選集排列都針對 Android TV／Google TV D-pad。
- 長標題會按可用空間調整字級同換行，避免只顯示省略號。
- 深色介面、完整比例海報同低干擾播放控制，適合客廳觀看距離。

### 遊客都用得到

- 遊客模式可以保存本機紀錄同收藏；新版片庫嘅詳情同播放需要登入 Aulama ID。
- 登入 Aulama ID 後，可跨裝置同步收藏、觀看進度同個人資料。
- 介面會跟隨系統使用繁體中文或簡體中文，亦可喺用戶卡片切換。

### 更新唔再卡死

- 正式版同搶先版兩個更新通道，可以喺用戶卡片一鍵切換。
- 更新視窗預設聚焦「下載更新」，遙控器一打開就可操作。
- 下載設有逾時、失敗提示同重新嘗試，避免長時間停喺 0%。

## 下載

| 通道 | 適合邊類用戶 | 下載 |
| --- | --- | --- |
| 正式版 `3.3.7` | 想用目前最新正式版本 | [免費下載 APK](https://github.com/siumiu1968/AulamaAnime_TV/releases/latest) |

支援 **Android TV、Google TV、Android 電視盒子**，最低為 **Android 5.0（API 21）**。

## 安裝同基本操作

1. 喺上面下載相應通道嘅 APK。
2. 將 APK 傳到電視，容許該檔案管理器安裝未知來源應用程式。
3. 安裝後由 TV Apps 開啟 Aulama Anime TV。
4. 可以揀遊客模式，或者用 Aulama ID 登入同步資料同使用新版片庫。

| 遙控器 | 功能 |
| --- | --- |
| 上／下／左／右 | 移動焦點、瀏覽卡片同集數 |
| OK／確認 | 開啟目前項目 |
| 長按 OK／確認（播放中） | 暫時用 2× 播放；放開後回復正常速度 |
| 返回 | 關閉卡片或返上一頁 |
| 播放器方向鍵 | 喚醒控制列、選擇跳過／下一集操作 |

## 專案原則

- **TV first**：優先照顧遙控器、焦點同十呎觀看體驗。
- **資料如實呈現**：來源冇提供嘅年份、集數或狀態唔會自行作出推測。
- **安全回退**：搜尋、線路同更新服務失效時，盡量保留可用功能。
- **不託管影片**：作品資料同播放可用性會受第三方來源、網絡同地區限制影響。

## 問題回報

回報時請附上：TV 型號、Android 版本、App 版本、作品／集數、所選線路，同埋可以重現問題嘅步驟。請勿公開登入憑證或私人資料。

[建立 Issue](https://github.com/siumiu1968/AulamaAnime_TV/issues/new)　·　[查看 Releases](https://github.com/siumiu1968/AulamaAnime_TV/releases)

## 致謝

本專案參考並延伸 [peacefulprogram/sakura-animation](https://github.com/peacefulprogram/sakura-animation)，再針對 Aulama 品牌介面、中文搜尋、來源協調、播放穩定性同 Android TV 遙控操作持續調整。

---

覺得 Aulama Anime TV 幫到你？按一下 **Star** 就係最直接嘅支持，亦令更多想喺 Android TV／Google TV 睇動漫嘅用戶更容易搵到呢個專案。
