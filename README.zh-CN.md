<!-- Hallmark · pre-emit critique: P5 H5 E5 S5 R5 V4 -->
<!-- Hallmark · macrostructure: product-evidence-flow · brand: Aulama dark cinema · chrome: real screenshots only · honest: pass -->

# Aulama Anime TV — Android TV / Google TV 动漫 App

[廣東話](./README.md) · [繁體中文](./README.zh-Hant.md) · **简体中文**

<p align="center">
  <img src="./docs/assets/cover.png" width="760" alt="Aulama Anime TV 标志与深色电视界面品牌封面">
</p>

<p align="center"><strong>为大屏幕而生的中文动漫搜索、选集与播放体验。</strong></p>

<p align="center">
  <a href="https://github.com/siumiu1968/AulamaAnime_TV/releases/latest"><img alt="最新正式版" src="https://img.shields.io/github/v/release/siumiu1968/AulamaAnime_TV?display_name=tag&sort=semver&label=%E6%AD%A3%E5%BC%8F%E7%89%88&color=16a085"></a>
  <a href="https://github.com/siumiu1968/AulamaAnime_TV/releases"><img alt="抢先版 3.1.0 Beta 2" src="https://img.shields.io/badge/%E6%8A%A2%E5%85%88%E7%89%88-3.1.0--beta.2-e85d9e"></a>
  <img alt="支持 Android 5.0 或更高版本" src="https://img.shields.io/badge/Android-5.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="GitHub 累计下载次数" src="https://img.shields.io/github/downloads/siumiu1968/AulamaAnime_TV/total?label=downloads&color=4f8ad9">
</p>

**Aulama Anime TV** 不是把手机界面简单放大，而是按照遥控器焦点、客厅观看距离和横向大屏幕重新设计。从搜索动画、选择集数、切换播放线路，到跳过片头片尾和续播，都可以使用 D-pad 完成。

> 如果你也认为电视追番应该更顺手，欢迎点击右上角的 **Star**。每一颗 Star，都是我们继续改善遥控操作、搜索和播放稳定性的动力。

[下载正式版](https://github.com/siumiu1968/AulamaAnime_TV/releases/latest)　·　[下载抢先版](https://github.com/siumiu1968/AulamaAnime_TV/releases/tag/v3.1.0-beta.2)　·　[查看全部版本](https://github.com/siumiu1968/AulamaAnime_TV/releases)

## 实际界面

以下均为 Android TV 模拟器或真机界面截图，没有重新绘制设备外框。

![Aulama Anime TV 首页，显示精选动画、完整海报、今日更新和遥控器导航](./docs/screenshots/home.png)

| 模糊搜索 | 作品详情与选集 |
| --- | --- |
| ![搜索页支持关键词、别名、繁简名称和日文原名](./docs/screenshots/search.png) | ![作品详情页显示作品资料、播放线路和集数](./docs/screenshots/detail.png) |

![用户卡片可切换界面语言、自动播放预览、更新通道，以及登录同步资料](./docs/screenshots/account.png)

## 核心功能

### 更完整的中文搜索

- 共用网站搜索逻辑，支持模糊词语、别名、繁简名称和日文原名。
- API 暂时失效时会回退到本机旧搜索，避免搜索功能直接中断。
- 搜索结果集中呈现作品，不混入无关推荐。

### 多线路播放与自动回退

- 主线路、备用 A、备用 B 可在详情页选择；播放失败时会提示可用备用线路。
- 支持 HLS、播放进度、续播、下一集，以及可用来源自动回退。
- 获得片头／片尾时间信息时，播放器会显示精简的跳过按钮；闲置后自动收起，按下遥控器即可再次唤醒。
- 已排除已经停用的白底黑字旧播放来源。

### 为遥控器而设计

- 焦点位置、返回路径、按钮尺寸和选集排列均针对 Android TV／Google TV D-pad。
- 长标题会根据可用空间调整字号和换行，不再只显示省略号。
- 深色界面、完整比例海报和低干扰播放控制，适合客厅观看距离。

### 游客模式与跨设备同步

- 无需登录即可搜索和播放；记录及收藏保存在本机。
- 登录 Aulama ID 后，可跨设备同步收藏、观看进度和个人资料。
- 界面会跟随系统使用繁体中文或简体中文，也可在用户卡片切换。

### 更可靠的应用更新

- 正式版和抢先版两个更新通道，可在用户卡片中一键切换。
- 更新窗口默认聚焦“下载更新”，打开后即可使用遥控器操作。
- 下载设有超时、失败提示和重试，避免长时间停留在 0%。

## 下载

| 通道 | 适合对象 | 下载 |
| --- | --- | --- |
| 正式版 `3.0.7` | 希望使用完成测试、变化较少的版本 | [前往最新正式版](https://github.com/siumiu1968/AulamaAnime_TV/releases/latest) |
| 抢先版 `3.1.0-beta.2` | 愿意提前测试新功能并反馈问题 | [下载 Beta 2](https://github.com/siumiu1968/AulamaAnime_TV/releases/tag/v3.1.0-beta.2) |

支持 **Android TV、Google TV、Android 电视盒子**，最低版本为 **Android 5.0（API 21）**。

## 安装与基本操作

1. 从上方下载对应通道的 APK。
2. 将 APK 传送到电视，允许该文件管理器安装未知来源应用。
3. 安装完成后，从 TV Apps 打开 Aulama Anime TV。
4. 可以直接使用游客模式，或登录 Aulama ID 同步数据。

| 遥控器 | 功能 |
| --- | --- |
| 上／下／左／右 | 移动焦点、浏览卡片与集数 |
| OK／确认 | 打开当前项目 |
| 返回 | 关闭卡片或返回上一页 |
| 播放器方向键 | 唤醒控制栏、选择跳过／下一集操作 |

## 项目原则

- **TV first**：优先照顾遥控器、焦点和十英尺观看体验。
- **如实呈现数据**：来源没有提供的年份、集数或状态，不会自行推测。
- **安全回退**：搜索、线路和更新服务失效时，尽量保留可用功能。
- **不托管视频**：作品资料和播放可用性会受到第三方来源、网络及地区限制影响。

## 问题反馈

反馈时请附上电视型号、Android 版本、App 版本、作品／集数、所选线路，以及可以复现问题的步骤。请勿公开登录凭证或私人信息。

[创建 Issue](https://github.com/siumiu1968/AulamaAnime_TV/issues/new)　·　[查看 Releases](https://github.com/siumiu1968/AulamaAnime_TV/releases)

## 致谢

本项目参考并扩展 [peacefulprogram/sakura-animation](https://github.com/peacefulprogram/sakura-animation)，并针对 Aulama 品牌界面、中文搜索、来源协调、播放稳定性和 Android TV 遥控操作持续调整。

---

如果 Aulama Anime TV 对你有所帮助，点击一颗 **Star** 就是最直接的支持，也能让更多希望在 Android TV／Google TV 观看动漫的用户找到这个项目。
