# Android 健身动作计数器

用手机摄像头记录一组健身动作。选好动作后，应用会在预览画面上叠加人体骨架，通过关节角度的变化计算次数，并在完成一次有效动作时播放提示音。训练结束后可以在手机上查看记录。

## 记录一次训练

1. 打开应用，允许使用相机。默认使用后置摄像头，也可以点「切换镜头」。调整手机位置，让动作涉及的关节出现在画面里。
2. 从下拉菜单选择动作，点「开始训练」。例如练深蹲时，让髋、膝、踝都进入画面，再开始下蹲。
3. 训练时查看次数、动作阶段、评分和文字提示。可以用 `+` 和 `−` 手动校正次数，或用「重置」清零。
4. 点「结束并保存」。次数大于零时，应用会保存动作名称、次数、结束时显示的分数和时间。点「查看历史记录」可以按从新到旧的顺序查看每一组。

| 动作 | 用来计算角度的三个关节点 |
| --- | --- |
| 深蹲、腿举 | 髋、膝、踝 |
| 俯卧撑、二头弯举、站姿推举 | 肩、肘、腕 |
| 仰卧起坐、抬腿、提膝 | 肩、髋、膝 |
| 侧平举 | 髋、肩、肘 |

## 计数和评分

CameraX 提供实时画面，ML Kit 从画面提取人体关键点，`ExerciseCounter` 根据用户所选动作计算关节角度。以深蹲为例，程序对左右膝角取平均，再对最近五帧取平均。膝角先大于 160°、再小于 110°，且与上次判定相隔超过 500 毫秒时，就会计算一次评分。

评分取决于判定时已观测到的极值角度与目标角度之间的差距。例如深蹲的目标膝角为 70°。85 分及以上显示绿色，60 至 84 分显示黄色，低于 60 分显示红色。只有绿色和黄色的动作会增加自动计数。由于深蹲在跨过下蹲阈值时就评分，慢速下蹲可能在到达最低点之前被判为低分。历史记录保存的是结束时的屏幕分数，一组内的各次评分没有单独存储。

## 在 Android Studio 中运行

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

## 仓库文件怎么读

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

如果想跟着一帧相机画面读代码，可以按以下顺序阅读。

1. 相机画面进入姿态检测器，训练按钮决定是否启用计数。[MainActivity.kt](app/src/main/java/com/example/myapplication/MainActivity.kt)
2. 检测到的关节点被画到预览画面上。[OverlayView.kt](app/src/main/java/com/example/myapplication/OverlayView.kt)
3. 计数器按所选动作计算角度，判断是否计数并返回分数和提示。[ExerciseCounter.kt](app/src/main/java/com/example/myapplication/ExerciseCounter.kt)

Room 将训练记录保存在手机上，历史页读取记录并按时间倒序显示。[数据库实现](app/src/main/java/com/example/myapplication/database/) · [历史页](app/src/main/java/com/example/myapplication/HistoryActivity.kt)

源码还预留了向服务器发送动作类型和次数的入口，默认地址包含占位符 `YOUR_SERVER_IP`。仓库未包含服务端，查看本地历史无需配置服务器。
