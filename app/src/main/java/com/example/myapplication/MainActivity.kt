package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
import android.media.SoundPool
import android.os.Bundle
import android.util.Log
import android.view.View // [新增] 用于控制按钮显示/隐藏
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.database.AppDatabase
import com.example.myapplication.database.ExerciseRecord
import com.example.myapplication.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val exerciseCounter = ExerciseCounter()
    private lateinit var soundPool: SoundPool
    private var soundIdCount = 0
    private var isSoundLoaded = false

    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private val database by lazy { AppDatabase.getDatabase(this) }

    // [新增] 训练状态标志位
    private var isTraining = false

    private val poseDetector = PoseDetection.getClient(
        AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
            .build()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        soundPool = SoundPool.Builder().setMaxStreams(2).build()
        soundIdCount = soundPool.load(this, R.raw.count, 1)
        soundPool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0) isSoundLoaded = true
        }

        requestPermissionLauncher.launch(Manifest.permission.CAMERA)

        setupButtons()
        // 初始化 UI 状态
        updateUI()
    }

    private fun setupButtons() {
        // 1. 切换摄像头
        binding.btnSwitchCamera.setOnClickListener {
            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            startCamera()
        }

        // 2. 减号 (仅在训练中有效，或者你想随时都能调也行)
        binding.btnDecrease.setOnClickListener {
            if (exerciseCounter.counter > 0) {
                exerciseCounter.counter--
                updateUI()
            }
        }

        // 3. 加号
        binding.btnIncrease.setOnClickListener {
            exerciseCounter.counter++
            updateUI()
        }

        // 4. 【修复】重置按钮
        binding.btnReset.setOnClickListener {
            // 核心重置逻辑
            exerciseCounter.reset()
            resetScoreUI()
            updateUI()

            // 震动或提示，让用户知道点到了
            Toast.makeText(this, "计数已归零", Toast.LENGTH_SHORT).show()
        }

        // 5. 开始训练
        binding.btnStartTraining.setOnClickListener {
            exerciseCounter.reset()
            resetScoreUI()

            isTraining = true // 开启训练模式

            // 切换按钮显示
            binding.btnStartTraining.visibility = View.GONE
            binding.btnStopTraining.visibility = View.VISIBLE
            binding.spinnerExercise.isEnabled = false // 锁定下拉框

            Toast.makeText(this, "训练开始！", Toast.LENGTH_SHORT).show()
            updateUI()
        }

        // 6. 结束并保存
        binding.btnStopTraining.setOnClickListener {
            isTraining = false // 结束训练模式

            val count = exerciseCounter.counter
            val exerciseType = binding.spinnerExercise.selectedItem.toString()
            val scoreStr = binding.tvScore.text.toString()
            val score = if (scoreStr == "--") 0 else (scoreStr.toIntOrNull() ?: 0)

            if (count > 0) {
                saveRecordToLocalDb(exerciseType, count, score)
                saveRecordToBackend(exerciseType, count)
                Toast.makeText(this, "训练结束，数据已保存", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "未检测到动作，不保存", Toast.LENGTH_SHORT).show()
            }

            // 清理现场
            exerciseCounter.reset()
            resetScoreUI()
            updateUI()

            // 恢复按钮状态
            binding.btnStopTraining.visibility = View.GONE
            binding.btnStartTraining.visibility = View.VISIBLE
            binding.spinnerExercise.isEnabled = true // 解锁下拉框
        }

        // 7. 查看历史
        try {
            binding.btnHistory.setOnClickListener {
                val intent = Intent(this, HistoryActivity::class.java)
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "History Button Error", e)
        }
    }

    private fun saveRecordToLocalDb(type: String, count: Int, score: Int) {
        lifecycleScope.launch {
            try {
                val record = ExerciseRecord(
                    type = type,
                    count = count,
                    score = score,
                    timestamp = System.currentTimeMillis()
                )
                database.exerciseDao().insert(record)
            } catch (e: Exception) {
                Log.e("MainActivity", "本地保存失败", e)
            }
        }
    }

    private fun resetScoreUI() {
        binding.tvScore.text = "--"
        binding.tvFeedback.text = if(isTraining) "动作进行中" else "准备开始"
        binding.tvFeedback.setBackgroundColor(Color.parseColor("#80000000"))
        binding.tvFeedback.setTextColor(Color.WHITE)
        val background = binding.tvScore.background as? GradientDrawable
        background?.setColor(Color.GRAY)
    }

    private fun saveRecordToBackend(type: String, count: Int) {
        // (保持原有的网络请求代码不变)
        Thread {
            try {
                // TODO: 替换为真实地址
                val url = URL("http://YOUR_SERVER_IP:8080/api/save_record")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.doOutput = true
                val jsonParam = JSONObject()
                jsonParam.put("type", type)
                jsonParam.put("count", count)
                val os = OutputStreamWriter(conn.outputStream)
                os.write(jsonParam.toString())
                os.flush()
                os.close()
                conn.responseCode // 触发请求
                conn.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    // ... (requestPermissionLauncher 和 startCamera 保持不变) ...
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) startCamera() else Toast.makeText(this, "需相机权限", Toast.LENGTH_LONG).show()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                processImage(imageProxy)
            }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (exc: Exception) {
                Log.e("GoodGym", "相机绑定失败", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            poseDetector.process(image)
                .addOnSuccessListener { pose ->
                    val allLandmarks = pose.allPoseLandmarks
                    if (allLandmarks.isNotEmpty()) {
                        val points = MutableList(33) { PointF(0f, 0f) }
                        for (landmark in allLandmarks) {
                            points[landmark.landmarkType] = PointF(landmark.position.x, landmark.position.y)
                        }

                        runOnUiThread {
                            // 1. 始终绘制骨架，方便用户摆姿势
                            binding.overlayView.setLandmarks(points, imageProxy.height, imageProxy.width)
                            binding.overlayView.invalidate()

                            // 2. [关键修改] 只有在训练状态下才进行计数逻辑
                            if (isTraining) {
                                val selectedExercise = binding.spinnerExercise.selectedItem.toString()
                                // 传入数据进行计算
                                val result = exerciseCounter.process(points, selectedExercise)

                                if (result != null) {
                                    if (result.isRepCompleted && isSoundLoaded) {
                                        soundPool.play(soundIdCount, 1f, 1f, 0, 0, 1f)
                                    }
                                    if (result.count > 0) {
                                        binding.tvScore.text = result.score.toString()
                                        val bg = binding.tvScore.background as? GradientDrawable
                                        bg?.setColor(result.scoreColor)
                                    } else {
                                        binding.tvScore.text = "--"
                                    }

                                    binding.tvFeedback.text = result.feedbackText
                                    if (result.scoreColor == Color.RED) {
                                        binding.tvFeedback.setTextColor(Color.RED)
                                        binding.tvFeedback.setBackgroundColor(Color.argb(230, 255, 255, 255))
                                    } else {
                                        binding.tvFeedback.setTextColor(Color.WHITE)
                                        binding.tvFeedback.setBackgroundColor(Color.parseColor("#80000000"))
                                    }
                                }
                                updateUI() // 更新计数显示
                            } else {
                                // 非训练状态，显示提示
                                binding.tvStage.text = "等待开始..."
                                binding.tvStage.setTextColor(Color.GRAY)
                                binding.tvFeedback.text = "请点击开始训练"
                            }
                        }
                    } else {
                        runOnUiThread {
                            binding.overlayView.setLandmarks(emptyList(), 0, 0)
                            binding.overlayView.invalidate()
                        }
                    }
                }
                .addOnCompleteListener { imageProxy.close() }
                .addOnFailureListener { imageProxy.close() }
        } else {
            imageProxy.close()
        }
    }

    private fun updateUI() {
        binding.tvCount.text = exerciseCounter.counter.toString()
        if (isTraining) {
            when (exerciseCounter.stage) {
                "up" -> {
                    binding.tvStage.text = "UP"
                    binding.tvStage.setTextColor(Color.BLUE)
                }
                "down" -> {
                    binding.tvStage.text = "DOWN"
                    binding.tvStage.setTextColor(Color.RED)
                }
                else -> {
                    binding.tvStage.text = "Training..."
                    binding.tvStage.setTextColor(Color.GREEN)
                }
            }
        } else {
            binding.tvStage.text = "Stopped"
            binding.tvStage.setTextColor(Color.GRAY)
        }
    }
}