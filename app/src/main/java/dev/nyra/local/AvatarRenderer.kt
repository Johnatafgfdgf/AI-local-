package dev.nyra.local

import android.content.Context
import android.opengl.Matrix
import android.view.Choreographer
import android.view.SurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.View
import com.google.android.filament.utils.Float3
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sin

data class AvatarPerformance(
    val speaking: Boolean = false,
    val thinking: Boolean = false,
    val happy: Boolean = false,
    val energy: Float = 0.55f,
    val viseme: String? = null
)

@Composable
fun NativeAvatarView(file: File, performance: AvatarPerformance, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val surface = remember(file.absolutePath) { NyraAvatarSurface(context) }
    LaunchedEffect(file.absolutePath) { surface.load(file) }
    LaunchedEffect(performance) { surface.performance = performance }
    DisposableEffect(surface) { onDispose { surface.release() } }
    Box(modifier) {
        AndroidView(factory = { surface }, modifier = Modifier.fillMaxSize(), update = { it.performance = performance })
    }
}

private class NyraAvatarSurface(context: Context) : SurfaceView(context), Choreographer.FrameCallback {
    companion object { init { Utils.init() } }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val viewer = ModelViewer(this)
    private val choreographer = Choreographer.getInstance()
    private var loadJob: Job? = null
    private var released = false
    private var loadGeneration = 0
    private var modelPath: String? = null
    private var rig: VrmRig? = null
    private var startNanos = 0L
    @Volatile var performance: AvatarPerformance = AvatarPerformance()

    init {
        setOnTouchListener(viewer)
        isFocusable = true
        viewer.view.antiAliasing = View.AntiAliasing.FXAA
        viewer.view.dynamicResolutionOptions = viewer.view.dynamicResolutionOptions.apply {
            enabled = true
            quality = View.QualityLevel.MEDIUM
        }
        viewer.view.renderQuality = viewer.view.renderQuality.apply { hdrColorBuffer = View.QualityLevel.MEDIUM }
    }

    fun load(file: File) {
        if (released || !file.isFile || modelPath == file.absolutePath) return
        modelPath = file.absolutePath
        loadGeneration += 1
        val generation = loadGeneration
        loadJob?.cancel()
        loadJob = scope.launch {
            val buffer = runCatching {
                FileInputStream(file).channel.use { channel ->
                    channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size()) as ByteBuffer
                }
            }.getOrElse {
                modelPath = null
                return@launch
            }
            withContext(Dispatchers.Main) {
                if (released || generation != loadGeneration) return@withContext
                runCatching {
                    viewer.loadModelGlb(buffer)
                    viewer.transformToUnitCube(Float3(0f, -0.15f, -3.55f))
                    rig = VrmRig(viewer).also { it.captureBindPose() }
                    startNanos = System.nanoTime()
                    postFrame()
                }.onFailure {
                    modelPath = null
                    rig = null
                }
            }
        }
    }

    private fun postFrame() {
        if (!released && isAttachedToWindow) {
            choreographer.removeFrameCallback(this)
            choreographer.postFrameCallback(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postFrame()
    }

    override fun onDetachedFromWindow() {
        choreographer.removeFrameCallback(this)
        released = true
        loadJob?.cancel()
        scope.cancel()
        super.onDetachedFromWindow()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (released || !isAttachedToWindow) return
        if (startNanos == 0L) startNanos = frameTimeNanos
        val seconds = (frameTimeNanos - startNanos).coerceAtLeast(0L) / 1_000_000_000.0f
        runCatching {
            rig?.update(seconds, performance)
            viewer.animator?.updateBoneMatrices()
            viewer.render(frameTimeNanos)
        }
        choreographer.postFrameCallback(this)
    }

    fun release() {
        if (released) return
        released = true
        choreographer.removeFrameCallback(this)
        loadJob?.cancel()
        scope.cancel()
        // ModelViewer installs its own detach listener and destroys its engine there. Calling
        // destroy() here as well could double-destroy native Filament resources.
    }
}

private class VrmRig(private val viewer: ModelViewer) {
    private val tm get() = viewer.engine.transformManager
    private val rm get() = viewer.engine.renderableManager
    private val bind = HashMap<String, FloatArray>()
    private var rootBind: FloatArray? = null
    private var faceInstance = 0
    private var weights = FloatArray(0)

    private val head = "J_Bip_C_Head"
    private val neck = "J_Bip_C_Neck"
    private val chest = "J_Bip_C_Chest"
    private val leftUpperArm = "J_Bip_L_UpperArm"
    private val rightUpperArm = "J_Bip_R_UpperArm"
    private val funFace = 2
    private val joy = 3
    private val blink = 13
    private val vowelA = 39
    private val vowelI = 40
    private val vowelU = 41
    private val vowelE = 42
    private val vowelO = 43

    fun captureBindPose() {
        val asset = viewer.asset ?: return
        listOf(head, neck, chest, leftUpperArm, rightUpperArm).forEach { name ->
            val entity = asset.getFirstEntityByName(name)
            if (entity != 0) {
                val instance = tm.getInstance(entity)
                if (instance != 0) {
                    val matrix = FloatArray(16)
                    tm.getTransform(instance, matrix)
                    bind[name] = matrix
                }
            }
        }
        val rootInstance = tm.getInstance(asset.root)
        if (rootInstance != 0) {
            rootBind = FloatArray(16).also { tm.getTransform(rootInstance, it) }
            setEntityDelta(asset.root, rootBind!!, 0f, 180f, 0f)
            rootBind = FloatArray(16).also { tm.getTransform(rootInstance, it) }
        }
        val faceEntity = asset.getFirstEntityByName("Face")
        if (faceEntity != 0 && rm.hasComponent(faceEntity)) {
            faceInstance = rm.getInstance(faceEntity)
            if (faceInstance != 0) weights = FloatArray(rm.getMorphTargetCount(faceInstance).coerceAtLeast(0))
        }
    }

    fun update(t: Float, p: AvatarPerformance) {
        val energy = p.energy.coerceIn(0.15f, 1f)
        val breath = sin(t * (1.45f + energy * 0.35f))
        val micro = sin(t * 0.61f) * 0.5f + sin(t * 0.37f) * 0.5f
        pose(head, if (p.thinking) -5.5f else 1.2f * breath, if (p.thinking) 8.0f else 2.2f * micro, if (p.thinking) -3.5f else 0.7f * micro)
        pose(neck, 0.8f * breath, 0.8f * micro, 0f)
        pose(chest, 1.1f * breath, 0.45f * micro, 0f)
        pose(leftUpperArm, 0f, 0f, 1.4f * breath)
        pose(rightUpperArm, 0f, 0f, -1.4f * breath)
        updateFace(t, p)
    }

    private fun updateFace(t: Float, p: AvatarPerformance) {
        if (faceInstance == 0 || weights.isEmpty()) return
        java.util.Arrays.fill(weights, 0f)
        val blinkPhase = t % 4.35f
        val blinkValue = when {
            blinkPhase < 0.075f -> blinkPhase / 0.075f
            blinkPhase < 0.155f -> 1f
            blinkPhase < 0.245f -> 1f - (blinkPhase - 0.155f) / 0.09f
            else -> 0f
        }.coerceIn(0f, 1f)
        setWeight(blink, blinkValue)
        when {
            p.happy -> setWeight(joy, 0.42f)
            p.thinking -> setWeight(funFace, 0.12f)
        }
        if (p.speaking) {
            val openness = (0.56f + 0.18f * (0.5f + 0.5f * sin(t * 18f))).coerceIn(0f, 0.9f)
            when (p.viseme) {
                "A" -> setWeight(vowelA, openness)
                "I" -> setWeight(vowelI, openness * 0.72f)
                "U" -> setWeight(vowelU, openness * 0.76f)
                "E" -> setWeight(vowelE, openness * 0.76f)
                "O" -> setWeight(vowelO, openness * 0.8f)
                else -> setWeight(vowelA, openness * 0.35f)
            }
        }
        runCatching { rm.setMorphWeights(faceInstance, weights, 0) }
    }

    private fun setWeight(index: Int, value: Float) {
        if (index in weights.indices) weights[index] = value.coerceIn(0f, 1f)
    }

    private fun pose(name: String, x: Float, y: Float, z: Float) {
        val asset = viewer.asset ?: return
        val base = bind[name] ?: return
        val entity = asset.getFirstEntityByName(name)
        if (entity != 0) setEntityDelta(entity, base, x, y, z)
    }

    private fun setEntityDelta(entity: Int, base: FloatArray, x: Float, y: Float, z: Float) {
        val instance = tm.getInstance(entity)
        if (instance == 0) return
        val rotation = FloatArray(16)
        Matrix.setIdentityM(rotation, 0)
        Matrix.rotateM(rotation, 0, x, 1f, 0f, 0f)
        Matrix.rotateM(rotation, 0, y, 0f, 1f, 0f)
        Matrix.rotateM(rotation, 0, z, 0f, 0f, 1f)
        val out = FloatArray(16)
        Matrix.multiplyMM(out, 0, base, 0, rotation, 0)
        tm.setTransform(instance, out)
    }
}
