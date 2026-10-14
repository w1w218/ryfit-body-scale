# 本地体脂秤（bodyscale）

一个本地化的蓝牙体脂秤 Android 应用。从已倒闭厂商的 `base.apk`
（`com.chronocloud.bodyscale`，时代脉搏蓝牙体脂秤）逆向出 BLE 协议后重写，
**不依赖任何服务器**：无登录、无推送、无定位、无第三方 SDK。

## 功能

- **扫描连接**：按 BLE 服务 UUID `0000ffe0` 扫描并连接体脂秤（特征 `0000ffe1`）
- **测量**：实时显示体重（D2 帧），测量完成后解析完整身体数据（D0 两帧）并自动保存
- **历史查询**：本地 SQLite 保存全部测量记录，按时间倒序展示
- **用户信息**：性别 / 年龄 / 身高 / 目标体重本地保存，连接后写入体脂秤（C0 命令，游客槽位 09）

## 构建

环境：JDK 17+（本机用 GraalVM 21）、Android SDK（build-tools 33/34、platforms android-33/34）。

```powershell
$env:ANDROID_HOME = 'F:\wyw\android\sdk'
$env:JAVA_HOME = 'F:\software\graalvm-jdk-21.0.2+13.1'
& 'F:\wyw\rea\_tools\gradle-8.7\bin\gradle.bat' -p . assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`（约 3.3 MB）。
也可以用 Android Studio 打开本项目直接构建运行。

安装到手机：`adb install app/build/outputs/apk/debug/app-debug.apk`

## 使用

1. 打开 App，进入「设置」，填写性别 / 年龄 / 身高 / 目标体重并保存
2. 回到主页，开启体脂秤，点击「扫描连接」（首次需授予蓝牙/定位权限）
3. 上秤测量，体重实时显示；测量结束自动保存完整身体数据
4. 「历史记录」查看全部测量结果

## 逆向协议摘要（来源：base.apk）

- 链路：BLE 服务 `0000ffe0-0000-1000-8000-00805f9b34fb`，
  特征 `0000ffe1-0000-1000-8000-00805f9b34fb`（同一特征写 + notify）
- 命令：hex 字节流。`C0 槽位 身高系数 年龄 性别` 写入用户信息，
  其中身高系数 = `(身高-100)/0.5`，槽位 `09` 为游客位（单用户用）
- 时间同步：收到 `F8` 帧后回 `FAF8 + (Unix秒-946656000)` 的 4 字节 hex 逆序
- 实时重量 `D2`：`weight = hex(das[2]+das[3]) / 10`（kg）
- 完整测量 `D0` 两帧（每帧回 `FA D0 xx` ACK）：
  - 第一帧（das[2]==1）：时间戳 = `das[7..4]` 逆序 + 946656000；
    `weight=das[8,9]/10`、`fat=das[10,11]/10`、`water=das[12,13]/10`；BMI 本地计算
  - 第二帧：`muscle=das[4,5]/10`、`bone=das[6,7]/10`、`metabolism=das[8,9]`、
    `skinfat=das[10,11]/10`、`offalfat=das[12]`、`bodyage=das[13]`
- 公式：基础代谢 = `370 + 21.6*(1-fat/100)*weight`；
  健康评分 = `0.2*(100-4*内脏脂肪) + 0.3*BMI分 + 0.5*(85-(身体年龄-年龄)*2)`
- 本地存储：SQLite 表 `query_measurement_data`（字段沿用原 App）

## 已知限制 / 待验证

- 协议层经 JVM 单测验证（16 个用例通过），**BLE 真机联通尚未验证**（需要实际设备）
- 未实现原 App 的称内历史读取（B5/C1/D1 流程）与多用户槽位管理（单用户用槽位 09）
- 扫描按服务 UUID 过滤；若个别设备名称不标准可能导致漏扫
