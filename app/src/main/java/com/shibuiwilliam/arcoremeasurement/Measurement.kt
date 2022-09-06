package com.shibuiwilliam.arcoremeasurement

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.*
import com.google.ar.core.exceptions.FatalException
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.*
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import com.shibuiwilliam.arcoremeasurement.TwoDCoverter.calculateWorld2CameraMatrix
import com.shibuiwilliam.arcoremeasurement.TwoDCoverter.world2Screen
import java.io.IOException
import java.util.*
import com.google.ar.sceneform.rendering.Color as arColor


class Measurement : AppCompatActivity(), Scene.OnUpdateListener {

    companion object {
        private const val MIN_OPENGL_VERSION = 3.0
        private val TAG: String = Measurement::class.java.simpleName
    }

    private var arFragment: ArFragment? = null

    private var cubeRenderable: ModelRenderable? = null

    var placed = false

    private val placedAnchors = ArrayList<Anchor>()
    private val placedAnchorNodes = ArrayList<AnchorNode>()

    private val multipleDistances = Array(
        Constants.maxNumMultiplePoints
    ) { Array<TextView?>(Constants.maxNumMultiplePoints) { null } }

    private lateinit var initCM: String

    private lateinit var clearButton: Button

    private var mediateMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!checkIsSupportedDeviceOrFinish(this)) {
            Toast.makeText(applicationContext, "Device not supported", Toast.LENGTH_LONG)
                .show()
        }

        setContentView(R.layout.activity_measurement)

        arFragment = supportFragmentManager.findFragmentById(R.id.sceneform_fragment) as ArFragment?

        arFragment?.arSceneView?.scene?.addOnUpdateListener(this@Measurement::onUpdateFrame)

        initCM = resources.getString(R.string.initCM)

        initRenderable()
        clearButton()

        //수직에 plane 그려지는거 막기
        val config = arFragment?.arSceneView?.session?.config
        config?.planeFindingMode = Config.PlaneFindingMode.DISABLED
        arFragment?.arSceneView?.session?.configure(config)


        //plane 그려지는 속도 향상
        arFragment?.planeDiscoveryController?.hide()
        arFragment?.planeDiscoveryController?.setInstructionView(null)

        findViewById<Button>(R.id.get_location).setOnClickListener {
//            //pose 위치 실시간 정보
//            Toast.makeText(this@Measurement, placedAnchorNodes[0].anchor?.pose.toString(), Toast.LENGTH_SHORT).show()
            getAnchor2D(placedAnchorNodes[0].anchor?.pose!!)
            getAnchor2D(placedAnchorNodes[1].anchor?.pose!!)
            getAnchor2D(placedAnchorNodes[2].anchor?.pose!!)
            getAnchor2D(placedAnchorNodes[3].anchor?.pose!!)
        }


//        arFragment!!.setOnTapArPlaneListener { hitResult: HitResult, _: Plane?, _: MotionEvent? ->
//            if (cubeRenderable == null) return@setOnTapArPlaneListener
//
//            // 앵커가 4개 이상이면 더 이상 생성하지 않는다.
//            if(placedAnchors.size < 4) {
//                tapDistanceOfMultiplePoints(hitResult)
//            }
//        }
    }

    //2D 좌표 가져오기
    private fun getAnchor2D(pose: Pose) {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
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

    // Renderable 기본 값 세팅
    private fun initRenderable() {
        MaterialFactory.makeOpaqueWithColor(this, arColor(Color.BLACK))
            .thenAccept { material: Material? ->
                //radius는 구의 크기
                //Vector3에서 가운데 프로퍼티는 지면에서 떨어진 높이
                cubeRenderable =
                    ShapeFactory.makeSphere(0.025f, Vector3(0.0f, 0.02f, 0.0f), material)
                cubeRenderable!!.isShadowCaster = false
                cubeRenderable!!.isShadowReceiver = false
            }.exceptionally {
                val builder = AlertDialog.Builder(this)
                builder.setMessage(it.message).setTitle("Error")
                val dialog = builder.create()
                dialog.show()
                return@exceptionally null
            }
    }

    // TODO: 이거 원래 상태로 바꿀것
    private fun clearButton() {
        clearButton = findViewById(R.id.clearButton)
        clearButton.setOnClickListener {
            mediateMode = !mediateMode
            clearAllAnchors()
        }
//        clearButton.setOnClickListener {
//            takePhoto()
//        }
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
        for (i in 0 until Constants.maxNumMultiplePoints) {
            for (j in 0 until Constants.maxNumMultiplePoints) {
                if (multipleDistances[i][j] != null) {
                    multipleDistances[i][j]!!.text = if (i == j) "-" else initCM
                }
            }
        }
    }


    private var transformableNode: TransformableNode? = null
    //앵커 갯수
    var anchorCnt = 1

    private var oldFirstNodePose: Pose? = null
    private var oldSecondNodePose: Pose? = null
    private var oldThirdNodePose: Pose? = null
    private var oldFourNodePose: Pose? = null


    private fun onUpdateFrame(frameTime: FrameTime?) {
        arFragment?.let { fragment ->
            fragment.arSceneView?.let { sceneView ->
                sceneView.arFrame?.let { frame ->
                    if (placedAnchors.size < 4) {

                        if(anchorCnt == 4) anchorCnt == 1

                        val trackable = frame.getUpdatedTrackables(Plane::class.java).iterator()
                        if (trackable.hasNext()) {
                            val plane = trackable.next() as Plane
                            if (plane.trackingState == TrackingState.TRACKING) {
                                fragment.planeDiscoveryController?.hide()
                                val hitTest =
                                    frame.hitTest(frame.screenCenter().x, frame.screenCenter().y)
                                val hitTestIterator = hitTest.iterator()
                                if (hitTestIterator.hasNext()) {
                                    //전에 포즈가 있었다면
                                    val pose: Pose
                                    if(
                                        oldFirstNodePose != null ||
                                        oldSecondNodePose != null ||
                                        oldThirdNodePose != null ||
                                        oldFourNodePose != null
                                            ) {
                                        pose = when(anchorCnt++) {
                                            1 -> oldFirstNodePose!!
                                            2 -> oldSecondNodePose!!
                                            3 -> oldThirdNodePose!!
                                            else -> oldFourNodePose!!
                                        }
                                        
                                    } else {
                                        val hitResult = hitTestIterator.next()
                                        pose = hitResult.hitPose
                                    }

                                    val modelAnchor = plane.createAnchor(pose)
                                    placedAnchors.add(modelAnchor)
                                    val anchorNode = AnchorNode(modelAnchor).apply {
                                        isSmoothed = true
                                        setParent(arFragment!!.arSceneView.scene)
                                    }
                                    placedAnchorNodes.add(anchorNode)
                                    anchorNode.setParent(sceneView.scene)

                                    transformableNode = TransformableNode(arFragment!!.transformationSystem)
                                        .apply {
                                            this.rotationController.isEnabled = false
                                            this.scaleController.isEnabled = false
                                            this.translationController.isEnabled = true
                                            this.renderable = renderable
                                            setParent(anchorNode)
                                        }
                                    transformableNode!!.setParent(anchorNode)
                                    transformableNode!!.renderable = this@Measurement.cubeRenderable


                                    transformableNode!!.worldPosition = Vector3(
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
                                } else { }
                            } else { }
                        } else { }

                    } else {

                        //조정하기 버튼을 만든 후에 불리언으로 (조정하기) (앵커 따라다니게 하기) 두가지 모드를 만드는 편이 나을 듯 하다.
                        // TODO: 두 모드로 나누었으니 이어서 진행할 것 내 생각에는 어느정도 범위를 벗어날때 앵커 따라나기게 하는 모드로 변경하는등 유동적이여야 할 거 같다. 
                        if(placedAnchorNodes.isNotEmpty() && !mediateMode) {
                            val frame = arFragment!!.arSceneView.arFrame ?: return
                            if (frame.camera.trackingState === TrackingState.TRACKING) {
                                val cameraPose = frame.camera.pose.extractRotation()
                                val first = cameraPose.compose(Pose.makeTranslation(0f,0f,-1f))
                                val second = cameraPose.compose(Pose.makeTranslation(0.3f,0f,-.8f))
                                val third = cameraPose.compose(Pose.makeTranslation(0f,0.3f,-1f))
                                val four = cameraPose.compose(Pose.makeTranslation(0.3f,0.3f,-1f))
                                try {
                                    //앵커 4개 따라다니게 하기
                                    placedAnchorNodes[0] = moveRenderable(placedAnchorNodes[0], first, 0)!!
                                    oldFirstNodePose = placedAnchorNodes[0].anchor?.pose

                                    placedAnchorNodes[1] = moveRenderable(placedAnchorNodes[1], second, 1)!!
                                    oldSecondNodePose = placedAnchorNodes[1].anchor?.pose

                                    placedAnchorNodes[2] = moveRenderable(placedAnchorNodes[2], third, 2)!!
                                    oldThirdNodePose = placedAnchorNodes[2].anchor?.pose

                                    placedAnchorNodes[3] = moveRenderable(placedAnchorNodes[3], four, 3)!!
                                    oldFourNodePose = placedAnchorNodes[3].anchor?.pose

                                }catch (e: FatalException) {

                                }

                            }
                        }
                    }
                }
            }
        }
    }

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
        val frame = arFragment!!.arSceneView.arFrame
        val session = arFragment!!.arSceneView.session
        val markAnchor = session!!.createAnchor(newPoseToMoveTo.extractTranslation())
        val newMarkAnchorNode = AnchorNode(markAnchor)
        newMarkAnchorNode.renderable = cubeRenderable
        newMarkAnchorNode.setParent(arFragment!!.arSceneView.scene)
        placedAnchorNodes.add(index, newMarkAnchorNode)

        //Delete the line if it is drawn
        removeLine(transformableNode!!)
        return newMarkAnchorNode
    }

    private fun removeLine(lineToRemove: Node) {
        //remove the line
        var lineToRemove: Node? = lineToRemove
        if (lineToRemove != null) {
            arFragment!!.arSceneView.scene.removeChild(lineToRemove)
            lineToRemove.setParent(null)
            lineToRemove = null
        }
    }

    private fun Frame.screenCenter(): Vector3 {
        val vw = findViewById<View>(android.R.id.content)
        return Vector3(vw.width / 2f, vw.height / 2f, 0f)
    }

    private fun placeAnchor(
        hitResult: HitResult,
        renderable: Renderable
    ) {
        val anchor = hitResult.createAnchor()
        placedAnchors.add(anchor)

        val anchorNode = AnchorNode(anchor).apply {
            isSmoothed = true
            setParent(arFragment!!.arSceneView.scene)
        }
        placedAnchorNodes.add(anchorNode)

        val node = TransformableNode(arFragment!!.transformationSystem)
            .apply {
                this.rotationController.isEnabled = false
                this.scaleController.isEnabled = false
                this.translationController.isEnabled = true
                this.renderable = renderable
                setParent(anchorNode)
            }

        arFragment!!.arSceneView.scene.addOnUpdateListener(this)
        arFragment!!.arSceneView.scene.addChild(anchorNode)
        node.select()
    }

    private fun tapDistanceOfMultiplePoints(hitResult: HitResult) {
        if (placedAnchorNodes.size >= Constants.maxNumMultiplePoints) {
            clearAllAnchors()
        }

        MaterialFactory.makeOpaqueWithColor(this, arColor(Color.BLACK))
            .thenAccept { material: Material? ->
                //radius는 구의 크기
                //Vector3에서 가운데 프로퍼티는 지면에서 떨어진 높이
                cubeRenderable =
                    ShapeFactory.makeSphere(0.025f, Vector3(0.0f, 0.02f, 0.0f), material)
                cubeRenderable!!.isShadowCaster = false
                cubeRenderable!!.isShadowReceiver = false
                placeAnchor(hitResult, cubeRenderable!!)
            }.exceptionally {
                val builder = AlertDialog.Builder(this)
                builder.setMessage(it.message).setTitle("Error")
                val dialog = builder.create()
                dialog.show()
                return@exceptionally null
            }
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
        //앵커가 생긴 이후로 계속 호출
    }

    // 줄 생성
    private fun drawLine(node1: AnchorNode, node2: AnchorNode) {
        //Draw a line between two AnchorNodes (adapted from https://stackoverflow.com/a/52816504/334402)
        val point1: Vector3 = node1.worldPosition
        val point2: Vector3 = node2.worldPosition


        //First, find the vector extending between the two points and define a look rotation
        //in terms of this Vector.
        val difference = Vector3.subtract(point1, point2)
        val directionFromTopToBottom = difference.normalized()
        val rotationFromAToB = Quaternion.lookRotation(directionFromTopToBottom, Vector3.up())
        MaterialFactory.makeOpaqueWithColor(
            applicationContext,
            com.google.ar.sceneform.rendering.Color(0F, 255F, 244F)
        )
            .thenAccept { material: Material? ->
                val model = ShapeFactory.makeCube(
                    Vector3(.01f, .01f, difference.length()),
                    Vector3.zero(), material
                )
                val nodeForLine = Node()
                nodeForLine.setParent(node1)
                nodeForLine.renderable = model
                nodeForLine.worldPosition = Vector3.add(point1, point2).scaled(.5f)
                nodeForLine.worldRotation = rotationFromAToB
            }
    }
}