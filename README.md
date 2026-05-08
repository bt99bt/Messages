# Fossify Messages 中文增强版

<img alt="Logo" src="graphics/icon.webp" width="120" />

<a href="https://play.google.com/store/apps/details?id=org.fossify.messages"><img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="80" /></a>
<a href="https://f-droid.org/packages/org.fossify.messages/"><img src="https://fdroid.gitlab.io/artwork/badge/get-it-on-en.svg" alt="Get it on F-Droid" height="80" /></a>
<a href="https://apt.izzysoft.de/fdroid/index/apk/org.fossify.messages"><img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" height="80" /></a>

这是基于 [FossifyOrg/Messages](https://github.com/FossifyOrg/Messages) 的中文增强分支。原项目是一个开源、轻量、无广告的 Android SMS/MMS 短信应用，支持短信和彩信收发、会话搜索、号码屏蔽、关键词屏蔽、短信导入导出、深色主题和隐私通知设置。

本分支在保留原有功能的基础上，新增了“短信自动转发”功能模块，适合需要把特定短信自动同步到另一个手机号或业务系统 webhook 的场景。

## 本次新增功能

### 短信自动转发

在“设置 -> 自动转发”中可以管理转发规则和查看转发历史。

支持能力：

- 多条转发规则，每条规则可单独启用或停用。
- 支持“全部短信”匹配，用于全局转发所有收到的短信。
- 通过关键词匹配短信正文，关键词支持多行配置，任意命中即触发。
- 通过正则表达式匹配短信正文，并记录捕获组。
- 转发目标支持另一个手机号。
- 转发目标支持飞书自定义机器人 webhook，使用 `POST application/json` 发送富文本消息。
- 支持双卡策略：
  - 使用接收短信的 SIM 卡转发。
  - 使用系统默认短信 SIM 卡转发。
  - 指定某张 SIM 卡转发。
- 如果指定 SIM 不可用，会记录失败，不会自动切换到其他 SIM，避免产生非预期资费。
- 自动记录转发历史，可查看短信来源、命中的规则、转发目标、使用的 SIM、转发状态和失败原因。
- 历史记录保存在本机 Room 数据库中，不做云端同步。

### 飞书机器人 Webhook 富文本格式

Webhook 规则命中后，请求体使用飞书自定义机器人富文本 `post` 消息格式：

```json
{
  "msg_type": "post",
  "content": {
    "post": {
      "zh_cn": {
        "title": "短信自动转发",
        "content": [
          [
            { "tag": "text", "text": "规则：" },
            { "tag": "text", "text": "验证码转发" }
          ],
          [
            { "tag": "text", "text": "发件人：" },
            { "tag": "text", "text": "+10000000000" }
          ],
          [
            { "tag": "text", "text": "短信内容：" }
          ],
          [
            { "tag": "text", "text": "Your verification code is 123456" }
          ]
        ]
      }
    }
  }
}
```

请求体大小会限制在飞书要求的 20 KB 以内，超过限制会记录为转发失败。

## 隐私说明

原版 Fossify Messages 的隐私目标是尽量减少不必要权限。本分支为了支持 webhook 自动转发，新增了 `INTERNET` 权限。

请注意：

- 只有配置并启用 webhook 规则时，命中的短信内容才会发送到你填写的飞书机器人地址。
- 手机号转发会通过系统短信能力发送，可能产生运营商短信费用。
- 转发历史仅保存在本机，用于审计“哪些短信被转发、转发到哪里、是否成功”。

## 原有主要功能

- 收发 SMS 和 MMS。
- 支持群发短信和彩信。
- 屏蔽指定号码、未知号码和包含指定关键词的短信。
- 导入和导出短信，便于换机备份。
- 会话搜索和消息搜索。
- 通知隐私控制：可选择锁屏显示发件人、显示消息内容或不显示。
- 深色主题和颜色自定义。
- 无广告、开源、轻量。

## 构建方式

推荐使用 JDK 17 和 Android SDK 36。

```bash
./gradlew assembleFossDebug
```

构建成功后，debug APK 位于：

```text
app/build/outputs/apk/foss/debug/
```

## 项目来源

- 原项目主页：[FossifyOrg/Messages](https://github.com/FossifyOrg/Messages)
- Fossify 官网：[https://www.fossify.org](https://www.fossify.org)
- Fossify GitHub：[https://github.com/FossifyOrg](https://github.com/FossifyOrg)
- Reddit 社区：[https://www.reddit.com/r/Fossify](https://www.reddit.com/r/Fossify)
- Telegram 社区：[https://t.me/Fossify](https://t.me/Fossify)

## 截图

<div align="center">
<img alt="App image" src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png" width="30%" />
<img alt="App image" src="fastlane/metadata/android/en-US/images/phoneScreenshots/2_en-US.png" width="30%" />
<img alt="App image" src="fastlane/metadata/android/en-US/images/phoneScreenshots/3_en-US.png" width="30%" />
</div>
