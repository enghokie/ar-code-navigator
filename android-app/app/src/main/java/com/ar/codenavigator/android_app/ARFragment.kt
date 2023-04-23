package com.ar.codenavigator.android_app

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat.NV21
import android.graphics.YuvImage
import android.media.Image
import android.os.Bundle
import android.renderscript.*
import android.util.Log
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.ar.codenavigator.utils.ClassData
import com.ar.codenavigator.utils.CodeData
import com.google.ar.core.ImageFormat
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.QuaternionEvaluator
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.*
import com.google.ar.sceneform.ux.ArFragment
import java.nio.ByteBuffer
import java.util.function.Consumer
import kotlin.math.sqrt


class ClassNode(classData: ClassData, node: Node) {
    public val classData = classData.copy(
        parentClasses = classData.parentClasses.toHashSet(),
        refClasses = classData.refClasses.toHashSet()
    )
    public val node = node
    public val animators = ArrayList<ObjectAnimator?>()
}

class ARFragment : ArFragment(), Scene.OnUpdateListener, Scene.OnPeekTouchListener {
    private val TAG = "ARFragment"

    private var _session: Session? = null
    private val _classNodes = HashMap<String, ClassNode>()
    private var _anchorNode: AnchorNode? = null

    private var _classCube: ModelRenderable? = null
    private var _parentClassCube: ModelRenderable? = null
    private var _refClassSphere: ModelRenderable? = null

    private val _classShape = Vector3(0.08f, 0.08f, 0.08f)
    private val _parentClassShape = Vector3(0.04f, 0.04f, 0.04f)
    private val _refClassRadius = 0.02f

    private val _classCubeColor = Color(1.0f, 0.1f, 0.1f, 0.01f)
    private val _parentClassCubeColor = Color(0.1f, 0.1f, 1.0f, 1.0f)
    private val _refClassSphereColor = Color(0.1f, 1.0f, 0.1f, 1.0f)

    private val _shapePadding = (_classShape.x + 0.02f) * 2
    private val _curLocalPos = Vector3(0f, 0f, 0f)

    private val _animatorDuration = (1000/*milliseconds*/ * (360 / 90)/*4 part rotations*/).toLong()
    private val _rotationAnimator = createRotationAnimator()

    private var _captureImage = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MaterialFactory.makeTransparentWithColor(context, _classCubeColor)
            .thenAccept { material ->
                _classCube = ShapeFactory.makeCube(_classShape, Vector3.zero(), material)

                _classCube!!.isShadowCaster = false
                _classCube!!.isShadowReceiver = false
            }

        MaterialFactory.makeTransparentWithColor(context, _parentClassCubeColor)
            .thenAccept { material ->
                _parentClassCube = ShapeFactory.makeCube(_parentClassShape, Vector3.zero(), material)

                _parentClassCube!!.isShadowCaster = false
                _parentClassCube!!.isShadowReceiver = false
            }

        MaterialFactory.makeOpaqueWithColor(context, _refClassSphereColor)
            .thenAccept { material ->
                _refClassSphere = ShapeFactory.makeSphere(_refClassRadius, Vector3.zero(), material)

                _refClassSphere!!.isShadowCaster = false
                _refClassSphere!!.isShadowReceiver = false
            }

        /*
        setOnSessionInitializationListener { session ->
            val config = Config(session)
            config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
            config.lightEstimationMode = Config.LightEstimationMode.DISABLED
            config.planeFindingMode = Config.PlaneFindingMode.DISABLED
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            config.focusMode = Config.FocusMode.FIXED
            config.augmentedImageDatabase = AugmentedImageDatabase(session)
            session.configure(config)

            _session = session
        }
         */
    }

    override fun onUpdate(frameTime: FrameTime?) {
        arSceneView.arFrame?.apply{
            if (camera.trackingState == TrackingState.TRACKING) {
                planeDiscoveryController.hide()

                if (_captureImage) {
                    getCameraImage()?.let { bitmap ->
                        callback?.apply {
                            onCapturedImage(bitmap)
                        }

                        _captureImage = false
                    }
                }
            }
        }

        super.onUpdate(frameTime)
    }

    fun setupButtonCallback(button: Button) {
        button?.let { captureButton ->
            captureButton.setOnClickListener {
                //_captureImage = true
                Toast.makeText(context, "Image capturing currently unsupported", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    // https://blog.minhazav.dev/how-to-convert-yuv-420-sp-android.media.Image-to-Bitmap-or-jpeg/
    private fun toYuvImage(image: Image): YuvImage? {
        require(image.format == ImageFormat.YUV_420_888) { "Invalid image format" }
        val width = image.width
        val height = image.height

        // Order of U/V channel guaranteed, read more:
        // https://developer.android.com/reference/android/graphics/ImageFormat#YUV_420_888
        val yPlane: Image.Plane = image.planes[0]
        val uPlane: Image.Plane = image.planes[1]
        val vPlane: Image.Plane = image.planes[2]
        val yBuffer: ByteBuffer = yPlane.buffer
        val uBuffer: ByteBuffer = uPlane.buffer
        val vBuffer: ByteBuffer = vPlane.buffer

        // Full size Y channel and quarter size U+V channels.
        val numPixels = (width * height * 1.5f).toInt()
        val nv21 = ByteArray(numPixels)
        var index = 0

        // Copy Y channel.
        val yRowStride: Int = yPlane.rowStride
        val yPixelStride: Int = yPlane.pixelStride
        for (y in 0 until height) {
            for (x in 0 until width) {
                nv21[index++] = yBuffer.get(y * yRowStride + x * yPixelStride)
            }
        }

        // Copy VU data; NV21 format is expected to have YYYYVU packaging.
        // The U/V planes are guaranteed to have the same row stride and pixel stride.
        val uvRowStride: Int = uPlane.rowStride
        val uvPixelStride: Int = uPlane.pixelStride
        val uvWidth = width / 2
        val uvHeight = height / 2
        for (y in 0 until uvHeight) {
            for (x in 0 until uvWidth) {
                val bufferIndex = y * uvRowStride + x * uvPixelStride
                // V channel.
                nv21[index++] = vBuffer.get(bufferIndex)
                // U channel.
                nv21[index++] = uBuffer.get(bufferIndex)
            }
        }
        return YuvImage(
            nv21, NV21, width, height,  /* strides= */null
        )
    }

    // https://blog.minhazav.dev/how-to-convert-yuv-420-sp-android.media.Image-to-Bitmap-or-jpeg/
    private fun yuv420ToBitmap(image: Image): Bitmap? {
        if (image.format != ImageFormat.YUV_420_888)
            return null

        val rs = RenderScript.create(context)
        val script = ScriptIntrinsicYuvToRGB.create(
            rs, Element.U8_4(rs)
        )

        // Refer the logic in a section below on how to convert a YUV_420_888 image
        // to single channel flat 1D array. For sake of this example I'll abstract it
        // as a method.
        val yuvByteArray: ByteArray = toYuvImage(image)!!.yuvData
        val yuvType: Type.Builder = Type.Builder(rs, Element.U8(rs))
            .setX(yuvByteArray.size)
        val `in` = Allocation.createTyped(
            rs, yuvType.create(), Allocation.USAGE_SCRIPT
        )
        val rgbaType: Type.Builder = Type.Builder(rs, Element.RGBA_8888(rs))
            .setX(image.width)
            .setY(image.height)
        val out = Allocation.createTyped(
            rs, rgbaType.create(), Allocation.USAGE_SCRIPT
        )

        // The allocations above "should" be cached if you are going to perform
        // repeated conversion of YUV_420_888 to Bitmap.
        `in`.copyFrom(yuvByteArray)
        script.setInput(`in`)
        script.forEach(out)
        val bitmap = Bitmap.createBitmap(
            image.width, image.height, Bitmap.Config.ARGB_8888
        )
        out.copyTo(bitmap)
        return bitmap
    }

    private fun getCameraImage(): Bitmap? {
        var bitmap: Bitmap? = null
        arSceneView.arFrame?.apply {
            val camImg = acquireCameraImage()
            if (camImg.format != ImageFormat.YUV_420_888)
                return null

            bitmap = yuv420ToBitmap(camImg)
        }

        return bitmap
    }

    interface Callback {
        fun onCapturedImage(bitmap: Bitmap)
    }

    private var callback: Callback? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is Callback) {
            this.callback = context
        }
    }

    override fun onDetach() {
        super.onDetach()
        callback = null
    }

    fun renderCodeModels(id: Int, codeData: CodeData) {
        val coreCamera = arSceneView.arFrame!!.camera
        if (coreCamera.trackingState != TrackingState.TRACKING) {
            return
        }

        if (_anchorNode == null) {
            val pose =
                coreCamera.pose.compose(Pose.makeTranslation(0f, 0f, -2.0f)).extractTranslation()

            _anchorNode = AnchorNode(arSceneView.session!!.createAnchor(pose))
            _anchorNode!!.setParent(arSceneView.scene)
        }

        for ((className, classData) in codeData?.classes!!) {
            var classNode: Node? = null
            if (!_classNodes.containsKey(className)) {
                classNode = Node()
                classNode.setParent(_anchorNode)
                classNode.isEnabled = true
                classNode.localPosition = _curLocalPos

                val offsetMultiplier: Int = (_classNodes.size / 4).toInt()
                val xOffset: Float = offsetMultiplier * _shapePadding
                val zOffset: Float = 0.01f * offsetMultiplier
                _curLocalPos.set(
                    when (_classNodes.size % 4) {
                        1 -> Vector3(_shapePadding + xOffset, 0f, zOffset)
                        2 -> Vector3(xOffset, -_shapePadding, zOffset)
                        3 -> Vector3(-_shapePadding + xOffset, 0f, zOffset)
                        else -> Vector3(xOffset, _shapePadding, zOffset)
                    }
                )

                val modelNode = Node()
                modelNode.setParent(classNode)
                modelNode.renderable = _classCube

                val classAnimator = _rotationAnimator?.clone()
                classAnimator?.target = modelNode
                classAnimator?.start()

                ViewRenderable.builder()
                    .setView(context, R.layout.class_text)
                    .build()
                    .thenAccept(Consumer { renderable: ViewRenderable ->
                        val textView = renderable.view as TextView
                        textView.text = className

                        val node = Node()
                        node.setParent(classNode)
                        node.isEnabled = true
                        node.renderable = renderable
                        node.name = "label"
                    })

                _classNodes[className] = ClassNode(classData, classNode)
                _classNodes[className]!!.animators.add(classAnimator)
            }
            else {
                classNode = _classNodes[className]!!.node
            }

            if (classData.parentClasses.isNotEmpty()) {
                val firstParent = classData.parentClasses.first()
                val nodeName = "parent-${firstParent}"
                var parentClassNode = classNode!!.findByName(nodeName)
                if (parentClassNode == null) {
                    Log.w(TAG, "Setting class $className parent to $firstParent")
                    parentClassNode = Node()
                    parentClassNode.setParent(classNode)
                    parentClassNode.isEnabled = true
                    parentClassNode.renderable = _parentClassCube
                    parentClassNode.name = nodeName

                    val parentClassAnimator = _rotationAnimator?.clone()
                    parentClassAnimator?.target = parentClassNode
                    parentClassAnimator?.start()
                    _classNodes[className]!!.animators.add(parentClassAnimator)
                }

                val connectionNodeName = "$nodeName-connection"
                if (classNode!!.findByName(connectionNodeName) == null
                    && _classNodes.contains(firstParent)) {
                    val outerParentClassNode = _classNodes[firstParent]!!.node
                    createNodeConnection(classNode, outerParentClassNode,
                        connectionNodeName, _parentClassCubeColor)
                }
            }

            if (classData.refClasses.isNotEmpty()) {
                val firstRef = classData.refClasses.first()
                val nodeName = "ref-${firstRef}"
                var refClassNode = classNode!!.findByName(nodeName)
                if (refClassNode == null) {
                    refClassNode = Node()
                    refClassNode.isEnabled = true
                    refClassNode.renderable = _refClassSphere
                    refClassNode.name = nodeName
                    refClassNode.setParent(classNode)

                    val refClassAnimator = _rotationAnimator?.clone()
                    refClassAnimator?.target = refClassNode
                    refClassAnimator?.start()
                    _classNodes[className]!!.animators.add(refClassAnimator)
                }

                val connectionNodeName = "$nodeName-connection"
                if (classNode!!.findByName(connectionNodeName) == null
                    && _classNodes.contains(firstRef)) {
                    val outerRefClassNode = _classNodes[firstRef]!!.node
                    createNodeConnection(classNode, outerRefClassNode,
                        connectionNodeName, _refClassSphereColor)
                }
            }
        }
    }

    private fun createNodeConnection(node1: Node, node2: Node, nodeName: String, color: Color) {
        val point1 = node1.worldPosition
        val point2 = node2.worldPosition

        val difference = Vector3.subtract(point2, point1)
        val lookRotation = Quaternion.lookRotation(difference, Vector3.up())
        val worldRotation = Quaternion.multiply(lookRotation, Quaternion.axisAngle(Vector3.right(), -90.0f))
        MaterialFactory.makeOpaqueWithColor(context, color)
            .thenAccept { material ->
                val model = ShapeFactory.makeCylinder(.005f,  distanceBetweenVecs(point2, point1),
                    Vector3(0f, _classShape.x + (_classShape.x / 2), 0f), material)
                val node = Node()
                node.name = nodeName
                node.renderable = model
                node.worldRotation = worldRotation
                node.setParent(node1)
            }
    }

    private fun distanceBetweenVecs(to: Vector3, from: Vector3): Float {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z

        return sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
    }

    private fun createRotationAnimator(): ObjectAnimator? {
        val orientations = arrayOfNulls<Quaternion>(4)
        val baseOrientation = Quaternion.axisAngle(Vector3(1.0f, 0f, 0.0f), 0f)
        for (i in orientations.indices) {
            val angle = (i * 360 / (orientations.size - 1)).toFloat()
            val orientation = Quaternion.axisAngle(Vector3(0.0f, 1.0f, 0.0f), angle)
            orientations[i] = Quaternion.multiply(baseOrientation, orientation)
        }

        val orbitAnimation = ObjectAnimator()
        orbitAnimation.setObjectValues(orientations[0], orientations[1],
            orientations[2], orientations[3])
        orbitAnimation.setPropertyName("localRotation")
        orbitAnimation.setEvaluator(QuaternionEvaluator())
        orbitAnimation.duration = _animatorDuration
        orbitAnimation.repeatCount = ObjectAnimator.INFINITE
        orbitAnimation.repeatMode = ObjectAnimator.RESTART
        orbitAnimation.interpolator = LinearInterpolator()
        orbitAnimation.setAutoCancel(true)
        return orbitAnimation
    }
}