package com.shibuiwilliam.arcoremeasurement.measurement

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.ar.core.*
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.*
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import com.shibuiwilliam.arcoremeasurement.R
import com.shibuiwilliam.arcoremeasurement.TwoDCoverter.calculateWorld2CameraMatrix
import com.shibuiwilliam.arcoremeasurement.TwoDCoverter.world2Screen
import com.shibuiwilliam.arcoremeasurement.databinding.ActivityMeasurementBinding
import com.shibuiwilliam.arcoremeasurement.measurement.state.CaptureState
import com.shibuiwilliam.arcoremeasurement.measurement.state.ErrorType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*


class Measurement : AppCompatActivity(), Scene.OnUpdateListener {

    private val vm: MeasurementViewModel by viewModels()

    companion object {
        private const val MIN_OPENGL_VERSION = 3.0
        private val TAG: String = Measurement::class.java.simpleName
    }

    private var arFragment: ArFragment? = null

    private var cubeRenderable: ModelRenderable? = null

    //앵커 갯수 표시
    private var anchorCnt = 0

    //앵커 클리어 이후 딜레이 불리언
    private var isPlay = false

    private val placedAnchors = ArrayList<Anchor>()
    private val placedAnchorNodes = ArrayList<AnchorNode>()

    private lateinit var clearButton: Button

    private lateinit var binding: ActivityMeasurementBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!checkIsSupportedDeviceOrFinish(this)) {
            Toast.makeText(applicationContext, "Device not supported", Toast.LENGTH_LONG)
                .show()
        }
        binding = ActivityMeasurementBinding.inflate(layoutInflater)
        setContentView(binding.root)


        arFragment = supportFragmentManager.findFragmentById(R.id.ar_fragment) as ArFragment?

        arFragment?.arSceneView?.scene?.addOnUpdateListener(this@Measurement::onUpdate)
//        arFragment?.arSceneView?.scene?.addOnUpdateListener {
//            addWaterMark()
//        }

        clearButton()

        //수직에 plane 그려지는거 막기
        val config = arFragment?.arSceneView?.session?.config
        config?.planeFindingMode = Config.PlaneFindingMode.DISABLED
        arFragment?.arSceneView?.session?.configure(config)


        //plane 그려지는 속도 향상
        arFragment?.planeDiscoveryController?.hide()
        arFragment?.planeDiscoveryController?.setInstructionView(null)

        binding.getLocation.setOnClickListener {
//            //pose 위치 실시간 정보
//            Toast.makeText(this@Measurement, placedAnchorNodes[0].anchor?.pose.toString(), Toast.LENGTH_SHORT).show()
//            getAnchor2D(placedAnchorNodes[0].anchor?.pose!!)
//            getAnchor2D(placedAnchorNodes[1].anchor?.pose!!)
//            getAnchor2D(placedAnchorNodes[2].anchor?.pose!!)
//            getAnchor2D(placedAnchorNodes[3].anchor?.pose!!)
            takePhoto()
        }

//        collecter()
    }

    private var oldWaterMark: Node? = null

    private fun addWaterMark() {
        MaterialFactory.makeTransparentWithColor(
            this,
            Color(android.graphics.Color.RED)
        )
            .thenAccept { material: Material? ->
                cubeRenderable = ShapeFactory.makeSphere(
                    0.02f,
                    Vector3.zero(),
                    material
                )
                cubeRenderable!!.isShadowCaster = false
                cubeRenderable!!.isShadowReceiver = false
//                addNode(material, model)
            }
            .exceptionally {
                Toast.makeText(this@Measurement, "Error", Toast.LENGTH_SHORT).show()
                return@exceptionally null
            }

    }


    private fun addNode(model: ModelRenderable): Node {
        val node = Node().apply {
            setParent(arFragment?.arSceneView?.scene)
            var camera = arFragment?.arSceneView?.scene?.camera

            var ray = camera?.screenPointToRay(200f, 500f)

            // var local=arSceneView.getScene().getCamera().localPosition

            localPosition = ray?.getPoint(0.5f)
            localRotation = arFragment?.arSceneView?.scene?.camera?.localRotation
            localScale = Vector3(0.3f, 0.3f, 0.3f)

            renderable = model
        }
        return node
    }

    private fun collecter() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch(Dispatchers.Main) {
                    vm.captureState.collect {
                        when (it) {
                            is CaptureState.Success<*> -> {
                                //변환된 사진을 받아옵니다.
                                binding.result.visibility = View.VISIBLE
                                binding.linearLayout.visibility = View.GONE

                                binding.result.setImageBitmap(it.data as Bitmap)
                                binding.loadingBar.visibility = View.GONE
                            }
                            is CaptureState.Failure -> {
                                messageByType(it.type)
                                binding.loadingBar.visibility = View.GONE
                            }
                            is CaptureState.Loading -> {
                                binding.loadingBar.visibility = View.VISIBLE
                            }
                            else -> {
                            } // default
                        }
                    }
                }
            }
        }
    }

    //타입 별 message 띄우기
    private fun messageByType(type: ErrorType?) {
        val message = when (type) {
            ErrorType.NO_CONTOUR -> "윤곽이 보이지 않습니다."
            ErrorType.TOO_SMALL -> "테두리가 너무 작습니다."
            ErrorType.NO_BORDER_DETECTION -> "테두리가 감지 되지 않습니다."
            ErrorType.IS_CONVEX -> ""
            ErrorType.OPENCV_INIT_FAILED -> "초기화에 실패하였습니다."
            else -> ""
        }
        Toast.makeText(this, message + "다시 촬영해주세요.", Toast.LENGTH_SHORT).show()
    }

    private fun takePhoto() {
        val view = arFragment!!.arSceneView

        // Create a bitmap the size of the scene view.
        val bitmap: Bitmap = Bitmap.createBitmap(
            view.width, view.height,
            Bitmap.Config.ARGB_8888
        )

        // Create a handler thread to offload the processing of the image.
        val handlerThread = HandlerThread("PixelCopier")
        handlerThread.start()
        // Make the request to copy.
        PixelCopy.request(view, bitmap, { copyResult ->
            if (copyResult === PixelCopy.SUCCESS) {
                try {
                    perspective(bitmap)
                } catch (e: IOException) {
                    val toast: Toast = Toast.makeText(
                        this@Measurement, e.toString(),
                        Toast.LENGTH_LONG
                    )
                    toast.show()
                    return@request
                }
            } else {
            }
            handlerThread.quitSafely()
        }, Handler(handlerThread.looper))
    }

    private fun perspective(bitmap: Bitmap) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.getImage(bitmap)
            }
        }
    }

    //이미지 실제 사이즈 가져오기
    fun getImageWidthAndHeight(context: Context, uri: Uri): Pair<Float, Float> {
        val exif = try {
            context.contentResolver.openInputStream(uri)?.let {
                ExifInterface(it)
            } ?: return 0f to 0f
        } catch (e: FileNotFoundException) {
            return 0f to 0f
        }

        val width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).toFloat()
        val height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).toFloat()

        return when (
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270 -> height to width
            else -> width to height
        }
    }

    //2D 좌표 가져오기
    private fun getAnchor2D(pose: Pose) {
        val displayMetrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getSystemService(WindowManager::class.java).currentWindowMetrics
        } else {
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            @Suppress("DEPRECATION")
            display.getMetrics(displayMetrics)
        }
        val height = displayMetrics.heightPixels
        val width = displayMetrics.widthPixels

        val camera = arFragment!!.arSceneView.arFrame?.camera
        // Get projection matrix.

        // Get projection matrix.
        val projmtx = FloatArray(16)
        camera?.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f)

        // Get camera matrix and draw.

        // Get camera matrix and draw.
        val viewmtx = FloatArray(16)
        camera?.getViewMatrix(viewmtx, 0)

        val anchorMatrix = FloatArray(16)
        pose.toMatrix(anchorMatrix, 0)
        val world2screenMatrix: FloatArray =
            calculateWorld2CameraMatrix(anchorMatrix, viewmtx, projmtx)!!
        val anchor2d: DoubleArray = world2Screen(width, height, world2screenMatrix)!!

        anchor2d.forEach {
            //2d 화면 좌표
            Log.d("anchor2d : ", it.toString())
        }
//        Toast.makeText(this@Measurement, anchor_2d.toString(), Toast.LENGTH_SHORT).show()
    }

    private fun clearButton() {
        binding.clearButton.setOnClickListener {
            clearAllAnchors()
            anchorCnt = 0
            isPlay = true
        }
    }

    private fun clearAllAnchors() {
        placedAnchors.clear()
        for (anchorNode in placedAnchorNodes) {
            arFragment!!.arSceneView.scene.removeChild(anchorNode)
            anchorNode.isEnabled = false
            anchorNode.anchor!!.detach()
            anchorNode.setParent(null)
        }
        placedAnchorNodes.clear()
    }

    private var transformableNode: TransformableNode? = null

    private fun moveRenderable(
        markAnchorNodeToMove: AnchorNode?,
        newPoseToMoveTo: Pose,
        index: Int
    ): AnchorNode? {
        //Move a renderable to a new pose
        if (markAnchorNodeToMove != null) {
            arFragment!!.arSceneView.scene.removeChild(markAnchorNodeToMove)
            placedAnchorNodes.remove(markAnchorNodeToMove)
        } else {
            return null
        }
        val session = arFragment!!.arSceneView.session
        val markAnchor = session!!.createAnchor(newPoseToMoveTo.extractTranslation())
        val newMarkAnchorNode = AnchorNode(markAnchor).apply {
            isSmoothed = true
        }
        cubeRenderable?.apply {
            isShadowCaster = false
            isShadowReceiver = false
        }

        newMarkAnchorNode.renderable = cubeRenderable
        newMarkAnchorNode.setParent(arFragment!!.arSceneView.scene)
        placedAnchorNodes.add(index, newMarkAnchorNode)

        //Delete the line if it is drawn
        removeLine(transformableNode!!)
        return newMarkAnchorNode
    }

    private fun removeLine(lineToRemove: Node) {
        arFragment!!.arSceneView.scene.removeChild(lineToRemove)
        lineToRemove.setParent(null)
    }

    private fun screenCenter(): Vector3 {
        val vw = findViewById<View>(android.R.id.content)
        return Vector3(vw.width / 2f, vw.height / 2f, 0f)
    }

    private fun checkIsSupportedDeviceOrFinish(activity: Activity): Boolean {
        val openGlVersionString =
            (Objects.requireNonNull(
                activity
                    .getSystemService(Context.ACTIVITY_SERVICE)
            ) as ActivityManager)
                .deviceConfigurationInfo
                .glEsVersion
        if (openGlVersionString.toDouble() < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES $MIN_OPENGL_VERSION later")
            Toast.makeText(
                activity,
                "Sceneform requires OpenGL ES $MIN_OPENGL_VERSION or later",
                Toast.LENGTH_LONG
            )
                .show()
            activity.finish()
            return false
        }
        return true
    }

    override fun onUpdate(ft: FrameTime?) {
        lifecycleScope.launch {
            if (isPlay) {
                //1.5초 이후 실행
                delay(1500L)
                isPlay = false
            } else {
                //get the frame from the scene for shorthand
                val frame = arFragment?.arSceneView?.arFrame

                if (frame != null) {
                    //get the trackables to ensure planes are detected
                    val var3 = frame.getUpdatedTrackables(Plane::class.java).iterator()
                    while (var3.hasNext()) {
                        val plane = var3.next() as Plane

                        //If a plane has been detected & is being tracked by ARCore
                        if (plane.trackingState == TrackingState.TRACKING) {

                            //Hide the plane discovery helper animation
                            arFragment?.planeDiscoveryController?.hide()

                            //Get all added anchors to the frame
                            val iterableAnchor = frame.updatedAnchors.iterator()

                            //place the first object only if no previous anchors were added
                            if (!iterableAnchor.hasNext()) {
                                //Perform a hit test at the center of the screen to place an object without tapping
                                val hitTest = frame.hitTest(screenCenter().x, screenCenter().y)

                                //iterate through all hits
                                val hitTestIterator = hitTest.iterator()
                                while (hitTestIterator.hasNext()) {
                                    val hitResult = hitTestIterator.next()

                                    //placedAnchorNodes가 4개가 되었을때
                                    if (anchorCnt == 4) {
                                        hitResult.hitPose.apply {
//                                            val first =
//                                                Pose.makeTranslation(
//                                                    tx() + -.6f,
//                                                    ty() + 0f,
//                                                    tz() + .6f
//                                                )
//                                            val second =
//                                                Pose.makeTranslation(
//                                                    tx() + -.6f,
//                                                    ty() + 0f,
//                                                    tz() + .6f
//                                                ) //파란색
//                                            val third =
//                                                Pose.makeTranslation(
//                                                    tx() + -.6f,
//                                                    ty() + 0f,
//                                                    tz() + .6f
//                                                )
//                                            val four =
//                                                Pose.makeTranslation(
//                                                    tx() + -.6f,
//                                                    ty() + 0f,
//                                                    tz() + .6f
//                                                )
//
//
//                                            placedAnchorNodes[0] =
//                                                moveRenderable(placedAnchorNodes[0], first, 0)!!
//                                            placedAnchorNodes[1] =
//                                                moveRenderable(placedAnchorNodes[1], second, 1)!!
//                                            placedAnchorNodes[2] =
//                                                moveRenderable(placedAnchorNodes[2], third, 2)!!
//                                            placedAnchorNodes[3] =
//                                                moveRenderable(placedAnchorNodes[3], four, 3)!!

                                            drawPoint(1, 150f, 150f, plane, this) //빨간색
                                            drawPoint(2, 950f, 150f, plane, this) //파란색
                                            drawPoint(3, 150f, 2100f, plane, this) //하얀색
                                            drawPoint(4, 950f, 2100f, plane, this) //검은색


                                        }
                                        return@launch
                                    }

                                    //Create an anchor at the plane hit
                                    val modelAnchor = plane.createAnchor(hitResult.hitPose)

                                    //Attach a node to this anchor with the scene as the parent
                                    placedAnchors.add(modelAnchor)
                                    val anchorNode = AnchorNode(modelAnchor).apply {
                                        isSmoothed = true
                                        setParent(arFragment?.arSceneView?.scene)
                                    }

                                    placedAnchorNodes.add(anchorNode)
                                    //create a new TranformableNode that will carry our object
                                    transformableNode =
                                        TransformableNode(arFragment?.transformationSystem).apply {
                                            this.rotationController.isEnabled = false
                                            this.scaleController.isEnabled = false
                                            this.translationController.isEnabled = true
                                            cubeRenderable?.apply {
                                                isShadowCaster = false
                                                isShadowReceiver = false
                                            }
                                            renderable = this@Measurement.cubeRenderable
                                            setParent(anchorNode)
                                        }

                                    //Alter the real world position to ensure object renders on the table top. Not somewhere inside.
                                    transformableNode?.worldPosition = Vector3(
                                        modelAnchor.pose.tx(),
                                        modelAnchor.pose.compose(
                                            Pose.makeTranslation(
                                                0f,
                                                0.05f,
                                                0f
                                            )
                                        ).ty(),
                                        modelAnchor.pose.tz()
                                    )

                                    anchorCnt++
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private var beforePointNode1: Node? = null
    private var beforePointNode2: Node? = null
    private var beforePointNode3: Node? = null
    private var beforePointNode4: Node? = null

    //선을 잇는 점(포인트) 생성
    private fun drawPoint(order: Int, rayParam1: Float, rayParam2: Float, plane: Plane, pose: Pose) {

        val arCamera = arFragment?.arSceneView?.scene?.camera

        val cameraPos: Vector3 = arCamera?.worldPosition!!
        val cameraForward = Vector3.add(cameraPos, arCamera.forward.scaled(1.0f))
        var forwardVector = Vector3.subtract(cameraForward, cameraPos)
        forwardVector = Vector3(forwardVector.x, 0f, forwardVector.z).normalized()

        var degreesFromCamToNegZAxis =
            Vector3.angleBetweenVectors(Vector3.forward(), forwardVector).toDouble()
        val dotProduct = Vector3.dot(Vector3.right(), forwardVector)
        if (dotProduct < 0) {
            degreesFromCamToNegZAxis = -degreesFromCamToNegZAxis
        }

//        Log.d("각도", degreesFromCamToNegZAxis.toString())

//        if node.eulerAngles.x < 0 {
//            node.eulerAngles.x = -.pi / 2
//            node.eulerAngles.z = 0
//        } else {
//            node.eulerAngles.x = .pi / 2
//            node.eulerAngles.z = -.pi
//        }
        Log.d("센터포즈", degreesFromCamToNegZAxis.toString())

        val node = Node().apply {
            var camera = arFragment?.arSceneView?.scene?.camera
            var ray =
                camera?.screenPointToRay(rayParam1, rayParam2) //x y 좌표 추후에 디바이스 가로세로 길이 구해 넣어주기
            setParent(arFragment?.arSceneView?.scene)

            //자연스럽게 움직이면 아래것을 setParent
//                    setParent(arFragment?.arSceneView?.scene)
            //고정되게 움직이게 하려면 아래것
//                    setParent(camera)
            worldPosition = ray?.getPoint(.5f)
            // TODO: 현재 렌더러블 회전하는 법 찾아서 넣을것 필요시 plane의 값에따라 조건식을 넣어도 될거 같음.
            localRotation = Quaternion.axisAngle(Vector3(pose.qx(), pose.qy(), pose.qz()), pose.qw())
        }

        when (order) {
            1 -> {
                if (beforePointNode1 != null) beforePointNode1?.setParent(null)
                beforePointNode1 = node
            }
            2 -> {
                if (beforePointNode2 != null) beforePointNode2?.setParent(null)
                beforePointNode2 = node
            }
            3 -> {
                if (beforePointNode3 != null) beforePointNode3?.setParent(null)
                beforePointNode3 = node
            }
            else -> {
                if (beforePointNode4 != null) beforePointNode4?.setParent(null)
                beforePointNode4 = node


                drawLine(beforePointNode1!!, beforePointNode2!!, 1)
                drawLine(beforePointNode2!!, beforePointNode4!!, 2)
                drawLine(beforePointNode4!!, beforePointNode3!!, 3)
                drawLine(beforePointNode3!!, beforePointNode1!!, 4)
            }
        }
    }

    var beforeLineNode1: Node? = null
    var beforeLineNode2: Node? = null
    var beforeLineNode3: Node? = null
    var beforeLineNode4: Node? = null

    // 줄 생성
    private fun drawLine(node1: Node, node2: Node, order: Int) {
        //Draw a line between two AnchorNodes (adapted from https://stackoverflow.com/a/52816504/334402)
        Log.d(TAG, "drawLine")
        val point1: Vector3 = node1.worldPosition
        val point2: Vector3 = node2.worldPosition


        //First, find the vector extending between the two points and define a look rotation
        //in terms of this Vector.
        val difference = Vector3.subtract(point1, point2)
        val directionFromTopToBottom = difference.normalized()
        val rotationFromAToB = Quaternion.lookRotation(directionFromTopToBottom, Vector3.up())
        MaterialFactory.makeOpaqueWithColor(applicationContext, Color(0f, 0f, 0f))
            .thenAccept { material: Material? ->
                /* Then, create a rectangular prism, using ShapeFactory.makeCube() and use the difference vector
               to extend to the necessary length.  */Log.d(
                TAG,
                "drawLine insie .thenAccept"
            )
                val model = ShapeFactory.makeCube(
                    Vector3(.01f, .0001f, difference.length()),
                    Vector3.zero(), material
                ).also {
                    it.isShadowCaster = false
                    it.isShadowReceiver = false
                }
                /* Last, set the world rotation of the node to the rotation calculated earlier and set the world position to
           the midpoint between the given points . */
                val node = Node().apply {
                    setParent(arFragment?.arSceneView?.scene)
                    renderable = model
                    worldPosition = Vector3.add(point1, point2).scaled(.488f)
                    worldRotation = rotationFromAToB
                }

                when (order) {
                    1 -> {
                        if (beforeLineNode1 != null) beforeLineNode1?.setParent(null)
                        beforeLineNode1 = node
                    }
                    2 -> {
                        if (beforeLineNode2 != null) beforeLineNode2?.setParent(null)
                        beforeLineNode2 = node
                    }
                    3 -> {
                        if (beforeLineNode3 != null) beforeLineNode3?.setParent(null)
                        beforeLineNode3 = node
                    }
                    else -> {
                        if (beforeLineNode4 != null) beforeLineNode4?.setParent(null)
                        beforeLineNode4 = node
                    }
                }
            }
    }
}