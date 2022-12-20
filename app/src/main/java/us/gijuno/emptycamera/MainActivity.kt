package us.gijuno.emptycamera

import android.Manifest
import android.R
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUI()
        setContent {
            val context = LocalContext.current
            MakeFullScreen(context)
            val permission by remember { mutableStateOf(checkPermissions()) }
            if (permission) {
                CameraView(context = context)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window,
            window.decorView.findViewById(R.id.content)).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    @SuppressLint("RestrictedApi")
    @Composable
    fun CameraView(context: Context) {
        val lifecycleOwner = LocalLifecycleOwner.current
        val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
        var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
        var preview by remember { mutableStateOf<Preview?>(null) }
        val executor = ContextCompat.getMainExecutor(context)
        val cameraProvider = cameraProviderFuture.get()
        DisposableEffect("cameraDispose") {
            onDispose {
                cameraProvider.shutdown()
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                cameraProviderFuture.addListener({
                    context.findActivity()!!.requestedOrientation =
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .apply {
                            setAnalyzer(executor, FaceAnalyzer())
                        }
                    imageCapture = ImageCapture.Builder()
                        .setTargetRotation(previewView.display.rotation)
                        .build()

                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build()

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        imageCapture,
                        preview
                    )
                }, executor)
                preview = androidx.camera.core.Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                previewView
            }
        )
    }

    private val permissions = arrayOf(
        Manifest.permission.CAMERA
    )
    private val MULTIPLE_PERMISSIONS = 100

    private fun checkPermissions(): Boolean {
        var result: Int
        val permissionList: MutableList<String> = ArrayList()
        for (pm in permissions) {
            result = ContextCompat.checkSelfPermission(this, pm)
            Log.d("permission result", result.toString())
            if (result != PackageManager.PERMISSION_GRANTED) {
                permissionList.add(pm)
            }
        }
        if (permissionList.isNotEmpty()) {
            Log.d("permission list", "is not empty")
            ActivityCompat.requestPermissions(this,
                permissionList.toTypedArray(),
                MULTIPLE_PERMISSIONS)
            return false
        }
        return true
    }



    private fun showNoPermissionToastAndFinish() {
        val toast = Toast.makeText(this,
            "권한 요청에 동의 해주셔야 이용 가능합니다. 설정에서 권한 허용 하시기 바랍니다.",
            Toast.LENGTH_SHORT)
        toast.show()
        finish()
    }
}


private class FaceAnalyzer : ImageAnalysis.Analyzer {
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(image: ImageProxy) {
        val imagePic = image.image
        imagePic?.close()
    }
}

@Composable
fun MakeFullScreen(context: Context) {
    enableFullScreen(context)
    DisposableEffect(1) {
        onDispose {
            disableFullScreen(context)
        }
    }
}

fun enableFullScreen(context: Context) {
    context.findActivity().let { activity ->
        activity?.window?.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }
}

fun disableFullScreen(context: Context) {
    context.findActivity().let { activity ->
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
