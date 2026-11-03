# Android 健身动作计数器

用手机摄像头记录一组健身动作。选好动作后，应用会在预览画面上叠加人体骨架，通过关节角度的变化计算次数，并在完成一次有效动作时播放提示音。训练结束后可以在手机上查看记录。

[技术栈](#技术栈) · [使用方法](#使用方法) · [技术实现](#技术实现) · [构建与运行](#构建与运行) · [项目结构](#项目结构)

## 技术栈

| 层次 | 技术与版本 | 在项目中的用途 |
| --- | --- | --- |
| 语言与平台 | Kotlin 2.0.21、Android SDK | Kotlin 编写应用逻辑。最低支持 Android 7.0（API 24），编译和目标 SDK 均为 34。 |
| 界面 | Android XML、ConstraintLayout 2.1.4、View Binding | XML 定义训练页布局，主界面通过 View Binding 访问相机预览、计数文本和训练按钮。 |
| 相机采集 | CameraX 1.3.1 | 同时提供实时预览和用于姿态检测的图像帧，支持前后摄像头切换。 |
| 姿态检测 | Google ML Kit Pose Detection Accurate 18.0.0-beta5 | 提取人体关节点，供 Kotlin 计数器按所选动作计算角度和次数。 |
| 运动反馈 | Android Canvas、SoundPool | 自定义视图在预览上绘制骨架，有效动作计数后播放提示音。 |
| 持久化 | Room 2.6.1、Kotlin 协程与 Flow | 协程写入训练记录。历史页订阅查询结果，通过 RecyclerView 展示。 |
| 测试 | JUnit 4.13.2、AndroidX Test JUnit 1.1.5 | 现有示例分别验证本地算术断言和设备上的应用包名。 |
| 构建 | Gradle 8.13、Android Gradle Plugin 8.13.2 | 用 Kotlin DSL 配置 Android 模块，通过 Gradle Wrapper 构建 APK，使用 kapt 生成 Room 的数据库访问代码。 |

库版本取自应用模块的依赖声明，插件和 Gradle 版本分别见版本目录和 Wrapper 配置。[应用依赖](app/build.gradle.kts) · [插件版本](gradle/libs.versions.toml) · [Gradle Wrapper](gradle/wrapper/gradle-wrapper.properties)

## 使用方法

1. 打开应用，允许使用相机。默认使用后置摄像头，也可以点「切换镜头」。调整手机位置，让动作涉及的关节出现在画面里。
2. 从下拉菜单选择动作，点「开始训练」。例如练深蹲时，让髋、膝、踝都进入画面，再开始下蹲。
3. 训练时查看次数、动作阶段、评分和文字提示。可以用 `+` 和 `−` 手动校正次数，或用「重置」清零。
4. 点「结束并保存」。次数大于零时，应用会保存动作名称、次数、结束时显示的分数和时间。点「查看历史记录」可以按从新到旧的顺序查看每一组。

## 技术实现

### 相机采集与骨架绘制

1. **采集。** `MainActivity.startCamera()` 用 CameraX 1.3.1 把预览和图像分析绑定到页面生命周期。预览直接显示在 `PreviewView` 上，分析任务通过单线程执行器接收 `ImageProxy`。如果检测速度跟不上摄像头，`STRATEGY_KEEP_ONLY_LATEST` 会保留最新的待分析帧，因此下文的“五帧”指实际参与计算的五帧。
2. **提取关节点。** `processImage()` 将图像和旋转角度封装为 `InputImage`，送入 ML Kit 的高精度姿态检测器（`18.0.0-beta5`，`STREAM_MODE`）。检测结果按关键点编号填入 33 个位置，每个位置保存二维坐标 `(x, y)`。用户在菜单里所选的动作决定随后使用哪几个关节。
3. **绘制和计数。** `OverlayView` 将坐标等比缩放并居中，用点标出关节，用线连接肩、肘、腕、髋、膝和踝。预览阶段就会绘制骨架。点击「开始训练」后，同一组关键点才会交给 `ExerciseCounter.process()`。没有检测到人体时，界面清空骨架并跳过该帧的计数。[相机和训练流程](app/src/main/java/com/example/myapplication/MainActivity.kt) · [骨架绘制](app/src/main/java/com/example/myapplication/OverlayView.kt)

### 动作判定与角度阈值

`calculateAngle(A, B, C)` 以中间的关节 B 为顶点，用向量 BA、BC 的叉积与点积计算夹角，结果在 0° 到 180° 之间。例如“肩、肘、腕”测量的是肘关节在画面中的弯曲程度。当前实现只使用二维坐标，也没有按关键点置信度筛选。身体侧转或关节被遮挡时，同一个动作可能得到不同的角度。

当角度满足表中的 `up` 条件，计数器会记住这个阶段。当角度随后满足同一行的评分条件，并且距上次判定**超过 500 毫秒**，才进入 `down` 并评分。两个阶段是代码里的状态名，具体对应抬起还是放下，要看所选动作。以下阈值均为源码中的严格不等式。[动作配置和状态转换](app/src/main/java/com/example/myapplication/ExerciseCounter.kt)

| 动作 | 关节点（中间为顶点） | 先进入 `up` | 随后触发评分 | 评分目标 |
| --- | --- | ---: | ---: | --- |
| 深蹲 | 髋、膝、踝 | > 160° | < 110° | 最小膝角 70° |
| 俯卧撑 | 肩、肘、腕 | > 160° | < 110° | 最小肘角 70° |
| 仰卧起坐 | 肩、髋、膝 | < 80° | > 140° | 最小髋角 50° |
| 二头弯举 | 肩、肘、腕 | < 60° | > 160° | 最小肘角 40° |
| 侧平举 | 髋、肩、肘 | > 80° | < 30° | 最大肩角 90° |
| 站姿推举 | 肩、肘、腕 | > 150° | < 90° | 最大肘角 170° |
| 抬腿 | 肩、髋、膝 | < 110° | > 160° | 最小髋角 90° |
| 提膝 | 肩、髋、膝 | < 90° | > 160° | 最小髋角 60° |
| 腿举 | 髋、膝、踝 | > 160° | < 100° | 最小膝角 70° |

前六种动作先对同一帧的左右角度取平均，再对最近最多五帧取平均，用平滑后的角度判断阶段和追踪极值。抬腿、提膝和腿举则直接使用左右两侧的原始角度，各自维护阶段，共用计时器。每帧先检查左侧，左侧触发时跳过右侧判定，因此同一帧最多加一次。评分用的极值由两侧共享，每帧取更接近目标角度的一侧更新极值。

### 评分规则与漏计示例

计数器逐帧追踪角度极值，到达评分条件时，计算该极值和目标角度的绝对差 `d`。当 `d ≤ 10°` 时得 100 分。超出 10° 的部分每度扣 2 分，舍去小数，最低为 0 分。85–100 分为绿色，60–84 分为黄色，这两档都会加一次。低于 60 分为红色，次数不变。每次判定都会更新时间并重置极值，因此红色结果也会启动下一次判定前的 500 毫秒间隔。总次数仍为零时，界面的数字评分保持 `--`，文字提示仍会更新。

- **一次得到 90 分的弯举。** 假设平滑后的肘角从 165° 降到 55°，再伸回 165°。55° 已进入弯举的 `up` 阶段，伸回时才触发评分。若这段时间记录到的最小角就是 55°，与 40° 目标相差 15°，得分为 `100 − (15 − 10) × 2 = 90`，显示绿色并计一次。若最小角为 60°，单凭这一角度还进不了 `up`，因为入口条件是严格小于 60°。
- **深蹲为什么可能漏计。** 假设已经站直进入 `up`，平滑膝角缓慢降到 109° 就触发评分。若截至此时记录的最小值也是 109°，与 70° 目标相差 39°，得分为 42，显示红色。这时阶段已切换到 `down`，即使继续下蹲到 70°，这次下蹲也不会重新评分。

界面上的动作建议由动作名称和角度差选择。例如“保持背部挺直”是一条预设提示，评分逻辑本身只比较表中对应的关节角度。[评分和提示实现](app/src/main/java/com/example/myapplication/ExerciseCounter.kt)

如果要新增动作，先在 `exercise_types` 菜单和 `configs` 中使用同一个中文名称，再为左右两侧各选三个关节点，设置两个阶段的阈值，以及评分时取最小角还是最大角。可以先画出一次动作的角度变化，检查极值出现在评分之前还是之后。上面的弯举和深蹲例子说明，这个先后顺序会直接影响计数。[菜单资源](app/src/main/res/values/strings.xml) · [动作配置](app/src/main/java/com/example/myapplication/ExerciseCounter.kt)

### 训练记录与历史查询

点击「结束并保存」时，`MainActivity` 读取当前次数和屏幕分数。次数为零时跳过保存，手动加减后的次数也会参与判断。Room 2.6.1 通过挂起函数向本地数据库 `exercise_app_db` 的 `exercise_records` 表写入**一组一条**的记录。[记录实体](app/src/main/java/com/example/myapplication/database/ExerciseRecord.kt) · [数据库操作](app/src/main/java/com/example/myapplication/database/ExerciseDao.kt)

| 字段 | 内容 |
| --- | --- |
| `id` | 自动生成的主键 |
| `type`、`count` | 结束时选中的动作名称和最终次数 |
| `score` | 结束时屏幕上的分数，显示 `--` 时存 0。每组只保存这一个分数。 |
| `timestamp` | 保存时的时间戳，单位为毫秒 |

历史页订阅 DAO 返回的 `Flow<List<ExerciseRecord>>`。查询按 `timestamp DESC` 排序，列表随数据库变化刷新。页面使用 `RecyclerView` 显示每组的动作、日期、次数和分数，空列表显示「暂无记录」。[历史页](app/src/main/java/com/example/myapplication/HistoryActivity.kt) · [列表适配器](app/src/main/java/com/example/myapplication/HistoryAdapter.kt)

结束训练还会独立发起一个 HTTP POST 请求，只发送动作类型和次数，例如 `{"type":"深蹲","count":8}`。当前地址 `http://YOUR_SERVER_IP:8080/api/save_record` 是占位符，仓库未提供服务端。页面的「数据已保存」提示在触发保存后立即显示，没有等待数据库写入或服务器响应。本地写入是否成功，可以在历史页查看。[保存和请求逻辑](app/src/main/java/com/example/myapplication/MainActivity.kt)

## 构建与运行

1. 获取源码并在 Android Studio 中打开包含 `settings.gradle.kts` 的目录。

   ```sh
   git clone https://github.com/jokicray-lang/MyApplication2.git
   ```

2. 安装 Android SDK Platform 34，为 Gradle 选择 JDK 17。项目自带 Gradle Wrapper。
3. 同步前检查根目录的 `gradle.properties`。其中的 HTTP 和 HTTPS 代理指向 `127.0.0.1:10808`。如果本机没有使用这个代理，请在本地注释掉四个启用的代理主机和端口设置，或改为自己的代理地址，然后同步 Gradle。
4. 连接有摄像头的 Android 7.0（API 24）或更高版本手机，启用 USB 调试，选择 `app` 后点击 Run。

在 Windows 上也可以从仓库根目录构建调试安装包。

```powershell
.\gradlew.bat assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 项目结构

| 路径 | 用途 |
| --- | --- |
| [app/](app/) | Android 应用模块，包含业务源码、界面布局、图标、音效和测试。 |
| [gradle/](gradle/) | 保存 Gradle Wrapper 和依赖版本目录。 |
| [.gitignore](.gitignore) | 指定 Git 应忽略的本机配置和构建产物。 |
| [build.gradle.kts](build.gradle.kts) | 声明项目使用的 Android 和 Kotlin 构建插件。 |
| [gradle.properties](gradle.properties) | 配置 Gradle 内存、AndroidX 和依赖下载代理。 |
| [gradlew](gradlew) | macOS 和 Linux 的 Gradle Wrapper 启动脚本。 |
| [gradlew.bat](gradlew.bat) | Windows 的 Gradle Wrapper 启动脚本。 |
| [settings.gradle.kts](settings.gradle.kts) | 配置插件和依赖仓库，并将 `app` 加入构建。 |
