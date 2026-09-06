package dev.nyra.local

import android.content.Context
import android.graphics.Outline
import android.opengl.Matrix
import android.view.Choreographer
import android.view.TextureView
import android.view.ViewOutlineProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.Skybox
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
    val surface = remember(file.absolutePath) { NyraAvatarTexture(context) }
    LaunchedEffect(file.absolutePath) { surface.load(file) }
    LaunchedEffect(performance) { surface.performance = performance }
    DisposableEffect(surface) { onDispose { surface.release() } }
    Box(modifier) {
        AndroidView(factory = { surface }, modifier = Modifier.fillMaxSize(), update = { it.performance = performance })
    }
}

/**
 * TextureView is intentional here. SurfaceView lives in a separate compositor layer and, inside
 * Compose cards/drawers/scrolling containers, could visibly punch through overlays and leave stale
 * tiles. TextureView stays in the normal view hierarchy, so clipping and drawer transitions remain
 * correct.
 */
private class NyraAvatarTexture(context: Context) : TextureView(context), Choreographer.FrameCallback {
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
    private var lastRenderedNanos = 0L
    private var compactFraming: Boolean? = null
    @Volatile var performance: AvatarPerformance = AvatarPerformance()

    init {
        isOpaque = true
        setBackgroundColor(android.graphics.Color.BLACK)
        setOnTouchListener(viewer)
        isFocusable = true

        // Keep the render target inside the rounded Compose card even while drawers/scrolling move.
        val radius = 28f * resources.displayMetrics.density
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: android.view.View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), radius)
            }
        }
        clipToOutline = true

        viewer.scene.skybox = Skybox.Builder().color(0f, 0f, 0f, 1f).build(viewer.engine)
        viewer.view.antiAliasing = View.AntiAliasing.FXAA

        // Dynamic resolution was producing visible block/tile corruption on the tested phone while
        // the SurfaceView moved under Compose. The avatar is small enough that stable native res is
        // preferable; frame pacing below handles thermal cost instead.
        viewer.view.dynamicResolutionOptions = viewer.view.dynamicResolutionOptions.apply {
            enabled = false
        }
        viewer.view.renderQuality = viewer.view.renderQuality.apply {
            hdrColorBuffer = View.QualityLevel.MEDIUM
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidateOutline()
        updateFraming()
    }

    private fun updateFraming() {
        if (width <= 0 || height <= 0) return
        val compact = height.toFloat() / width.toFloat() < 0.78f
        if (compactFraming == compact) return
        compactFraming = compact

        // Chat gets a close conversational crop; the dedicated Avatar tab keeps a full-body view.
        // Focal-length zoom preserves ModelViewer's default orbit target around z=-4.
        viewer.cameraFocalLength = if (compact) 58f else 40f
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
                    // Match ModelViewer's orbit center so pinch/orbit remain intuitive.
                    viewer.transformToUnitCube(Float3(0f, -0.12f, -3.9f))
                    updateFraming()
                    rig = VrmRig(viewer).also { it.captureBindPose() }
                    startNanos = System.nanoTime()
                    lastRenderedNanos = 0L
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

        // 30 fps while idle cuts heat/battery use roughly in half; speech/thinking gets 60 fps.
        val active = performance.speaking || performance.thinking
        val minInterval = if (active) 16_000_000L else 33_000_000L
        if (lastRenderedNanos != 0L && frameTimeNanos - lastRenderedNanos < minInterval) {
            choreographer.postFrameCallback(this)
            return
        }
        lastRenderedNanos = frameTimeNanos

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
        // ModelViewer installs a detach listener and destroys its engine there. Calling destroy()
        // here as well can double-destroy native Filament resources on some Android builds.
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
    private val leftLowerArm = "J_Bip_L_LowerArm"
    private val rightLowerArm = "J_Bip_R_LowerArm"
    private val hairRoots = (1..14).map { "J_Sec_Hair1_%02d".format(it) }

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
        val animatedBones = buildList {
            add(head)
            add(neck)
            add(chest)
            add(leftUpperArm)
            add(rightUpperArm)
            add(leftLowerArm)
            add(rightLowerArm)
            addAll(hairRoots)
        }
        animatedBones.forEach { name ->
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
            // The supplied VRM faces away from Filament's default camera in bind pose.
            setEntityDelta(asset.root, rootBind!!, 0f, 180f, 0f)
            rootBind = FloatArray(16).also { tm.getTransform(rootInstance, it) }
        }

        val faceEntity = asset.getFirstEntityByName("Face")
        if (faceEntity != 0 && rm.hasComponent(faceEntity)) {
            faceInstance = rm.getInstance(faceEntity)
            if (faceInstance != 0) {
                weights = FloatArray(rm.getMorphTargetCount(faceInstance).coerceAtLeast(0))
            }
        }
    }

    fun update(t: Float, p: AvatarPerformance) {
        val energy = p.energy.coerceIn(0.15f, 1f)
        val breath = sin(t * (1.45f + energy * 0.35f))
        val micro = sin(t * 0.61f) * 0.5f + sin(t * 0.37f) * 0.5f

        pose(
            head,
            if (p.thinking) -5.5f else 1.2f * breath,
            if (p.thinking) 8.0f else 2.2f * micro,
            if (p.thinking) -3.5f else 0.7f * micro
        )
        pose(neck, 0.8f * breath, 0.8f * micro, 0f)
        pose(chest, 1.1f * breath, 0.45f * micro, 0.35f * micro)

        // VRM bind pose is a T-pose. Lowering both upper arms turns it into a neutral standing
        // pose while keeping the original rig hierarchy and skinning intact.
        val armDrift = 1.0f * sin(t * 0.73f)
        pose(leftUpperArm, 0.4f * micro, 0f, 68f + armDrift)
        pose(rightUpperArm, -0.4f * micro, 0f, -68f - armDrift)
        pose(leftLowerArm, 0f, 0f, -5f + 0.35f * breath)
        pose(rightLowerArm, 0f, 0f, 5f - 0.35f * breath)

        // A very small secondary sway prevents the long hair from looking welded in place. This is
        // intentionally conservative until full VRM spring-bone collision is calibrated on device.
        hairRoots.forEachIndexed { index, name ->
            val phase = index * 0.43f
            pose(
                name,
                0.75f * sin(t * 0.92f + phase),
                0.35f * sin(t * 0.67f + phase * 0.7f),
                0.55f * sin(t * 0.78f + phase)
            )
        }

        updateFace(t, p)
    }

    private fun updateFace(t: Float, p: AvatarPerformance) {
        if (faceInstance == 0 || weights.isEmpty()) return
        java.util.Arrays.fill(weights, 0f)

        // Slightly irregular blink timing so it does not read as a metronome.
        val blinkCycle = 4.0f + 0.45f * (0.5f + 0.5f * sin(t * 0.19f))
        val blinkPhase = t % blinkCycle
        val blinkValue = when {
            blinkPhase < 0.075f -> blinkPhase / 0.075f
            blinkPhase < 0.145f -> 1f
            blinkPhase < 0.235f -> 1f - (blinkPhase - 0.145f) / 0.09f
            else -> 0f
        }.coerceIn(0f, 1f)
        setWeight(blink, blinkValue)

        when {
            p.happy -> setWeight(joy, 0.42f)
            p.speaking -> setWeight(joy, 0.08f)
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
