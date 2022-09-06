package com.shibuiwilliam.arcoremeasurement

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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
import com.shibuiwilliam.arcoremeasurement.TwoDCoverter.calculateWorld2CameraMatrix
import com.shibuiwilliam.arcoremeasurement.TwoDCoverter.world2Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import com.google.ar.sceneform.rendering.Color as arColor


class Measurement : AppCompatActivity(), Scene.OnUpdateListener {

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

    // TODO: 이거 원래 상태로 바꿀것
    private fun clearButton() {
        clearButton = findViewById(R.id.clearButton)
        clearButton.setOnClickListener {
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
        for (i in 0 until Constants.maxNumMultiplePoints) {
            for (j in 0 until Constants.maxNumMultiplePoints) {
                if (multipleDistances[i][j] != null) {
                    multipleDistances[i][j]!!.text = if (i == j) "-" else initCM
                }
            }
        }
    }

    private var transformableNode: TransformableNode? = null

    fun onUpdateFrame(frameTime: FrameTime?) {

        lifecycleScope.launch {
            if(isPlay) {
                //1.5초 이후 실행
                delay(1500L)
                isPlay = false
            } else {
                //get the frame from the scene for shorthand
                val frame = arFragment?.arSceneView?.arFrame

                if (frame != null) {
                    //get the trackables to ensure planes are detected
                    val var3 = frame.getUpdatedTrackables(Plane::class.java).iterator()
                    while(var3.hasNext()) {
                        val plane = var3.next() as Plane

                        //If a plane has been detected & is being tracked by ARCore
                        if (plane.trackingState == TrackingState.TRACKING) {

                            //Hide the plane discovery helper animation
                            arFragment?.planeDiscoveryController?.hide()

                            //Get all added anchors to the frame
                            val iterableAnchor = frame.updatedAnchors.iterator()

                            //place the first object only if no previous anchors were added
                            if(!iterableAnchor.hasNext()) {
                                //Perform a hit test at the center of the screen to place an object without tapping
                                val hitTest = frame.hitTest(frame.screenCenter().x, frame.screenCenter().y)

                                //iterate through all hits
                                val hitTestIterator = hitTest.iterator()
                                while(hitTestIterator.hasNext()) {
                                    val hitResult = hitTestIterator.next()

                                    //placedAnchorNodes가 4개가 되었을때
                                    if(anchorCnt == 4) {
                                        hitResult.hitPose.apply {
                                            val tx = tx()
                                            val ty = ty()
                                            val tz = tz()
                                            val first = Pose.makeTranslation(tx + -.3f,ty + 0f,tz + -.47f)
                                            val second = Pose.makeTranslation(tx + .3f,ty + 0f,tz + -.47f)
                                            val third = Pose.makeTranslation(tx + -.3f,ty + 0f,tz + .3f)
                                            val four = Pose.makeTranslation(tx + .3f,ty + 0f,tz + .3f)

                                            placedAnchorNodes[0] = moveRenderable(placedAnchorNodes[0], first, 0)!!
                                            placedAnchorNodes[1] = moveRenderable(placedAnchorNodes[1], second, 1)!!
                                            placedAnchorNodes[2] = moveRenderable(placedAnchorNodes[2], third, 2)!!
                                            placedAnchorNodes[3] = moveRenderable(placedAnchorNodes[3], four, 3)!!
                                            drawLine(placedAnchorNodes[0], placedAnchorNodes[1])
                                            drawLine(placedAnchorNodes[1], placedAnchorNodes[3])
                                            drawLine(placedAnchorNodes[3], placedAnchorNodes[2])
                                            drawLine(placedAnchorNodes[2], placedAnchorNodes[0])
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
                                    transformableNode = TransformableNode(arFragment?.transformationSystem).apply {
                                        this.rotationController.isEnabled = false
                                        this.scaleController.isEnabled = false
                                        this.translationController.isEnabled = true
                                        renderable = this@Measurement.cubeRenderable
                                        setParent(anchorNode)
                                    }

                                    //Alter the real world position to ensure object renders on the table top. Not somewhere inside.
                                    transformableNode?.worldPosition = Vector3(modelAnchor.pose.tx(),
                                        modelAnchor.pose.compose(Pose.makeTranslation(0f, 0.05f, 0f)).ty(),
                                        modelAnchor.pose.tz())

                                    anchorCnt++
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
            com.google.ar.sceneform.rendering.Color(0F, 0F, 0F)
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