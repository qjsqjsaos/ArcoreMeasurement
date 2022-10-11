package com.shibuiwilliam.arcoremeasurement.measurement

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.*
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.*
import com.google.ar.sceneform.ux.ArFragment
import com.shibuiwilliam.arcoremeasurement.R
import com.shibuiwilliam.arcoremeasurement.databinding.ActivityMeasurementBinding
import com.shibuiwilliam.arcoremeasurement.measurement.state.ErrorType
import java.util.*


class Measurement : AppCompatActivity(), Scene.OnUpdateListener {

    private val vm: MeasurementViewModel by viewModels()

    companion object {
        private const val MIN_OPENGL_VERSION = 3.0
        private val TAG: String = Measurement::class.java.simpleName
    }

    private var arFragment: ArFragment? = null

    private var cubeRenderable: ModelRenderable? = null

    private lateinit var binding: ActivityMeasurementBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!checkIsSupportedDeviceOrFinish(this)) {
            Toast.makeText(applicationContext, "Device not supported", Toast.LENGTH_LONG)
                .show()
        }
        binding = ActivityMeasurementBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cubeTestRenderable()
        arFragment =
            (supportFragmentManager.findFragmentById(R.id.ar_fragment) as ArFragment?)?.apply {
                planeDiscoveryController.hide()
                planeDiscoveryController.setInstructionView(null)
                arSceneView.planeRenderer.isEnabled = false

                //하나의 수평면만 감지하기
                val config = arFragment?.arSceneView?.session?.config
                config?.planeFindingMode = Config.PlaneFindingMode.DISABLED
                arFragment?.arSceneView?.session?.configure(config)

                arSceneView.scene?.addOnUpdateListener(this@Measurement::onUpdate)
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

    //테스트용 렌더러블 빨간 공
    private fun cubeTestRenderable() {
        MaterialFactory.makeTransparentWithColor(
            this,
            Color(android.graphics.Color.BLACK)
        )
            .thenAccept { material: Material? ->
                cubeRenderable = ShapeFactory.makeSphere(
                    0.005f,
                    Vector3.zero(),
                    material
                )
                cubeRenderable!!.isShadowCaster = false
                cubeRenderable!!.isShadowReceiver = false
            }
            .exceptionally {
                Toast.makeText(this@Measurement, "Error", Toast.LENGTH_SHORT).show()
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

    //처음 노드가 생성되고, 다음 노드로 이동하기 위해
    //처음 노드(현재 기준 전 노드)를 담기위해 각 포인트마다 노드를 저장해준다.
    private var leftTopPointBeforeAnchorNode: AnchorNode? = null
    private var rightTopPointBeforeAnchorNode: AnchorNode? = null
    private var leftDownPointBeforeAnchorNode: AnchorNode? = null
    private var rightDownPointBeforeAnchorNode: AnchorNode? = null

    override fun onUpdate(ft: FrameTime?) {
        //view에서 frame 가져오기
        val frame = arFragment?.arSceneView?.arFrame
        if (frame != null) {
            //plane이 감지되었는지 확인하는 추가적인 작업
            val var3 = frame.getUpdatedTrackables(Plane::class.java).iterator()
            while (var3.hasNext()) {
                val plane = var3.next() as Plane

                //바닥이 감지되고 arcore에서 추적 중인 경우
                if (plane.trackingState == TrackingState.TRACKING) {
                    //왼쪽 위에 렌더
                    createCircleRender(
                        widthRatio = 7f,
                        heightRatio = 12f,
                        frame = frame,
                        plane = plane,
                        nodePoint = 1
                    )
                    //오른쪽 위에 렌더
                    createCircleRender(
                        widthRatio = 1.18f,
                        heightRatio = 12f,
                        frame = frame,
                        plane = plane,
                        nodePoint = 2
                    )
                    //왼쪽 아래 렌더
                    createCircleRender(
                        widthRatio = 7f,
                        heightRatio = 1.25f,
                        frame = frame,
                        plane = plane,
                        nodePoint = 3
                    )

                    //오른쪽 아래 렌더
                    createCircleRender(
                        widthRatio = 1.18f,
                        heightRatio = 1.25f,
                        frame = frame,
                        plane = plane,
                        nodePoint = 4
                    )

                    if (
                        leftTopPointBeforeAnchorNode != null &&
                        rightTopPointBeforeAnchorNode != null &&
                        leftDownPointBeforeAnchorNode != null &&
                        rightDownPointBeforeAnchorNode != null
                    ) {
                        addLineBetweenPoints(
                            scene = arFragment?.arSceneView?.scene,
                            plane = plane,
                            from = leftTopPointBeforeAnchorNode?.worldPosition!!,
                            to = rightTopPointBeforeAnchorNode?.worldPosition!!,
                            nodePoint = 1
                        )
                        addLineBetweenPoints(
                            scene = arFragment?.arSceneView?.scene,
                            plane = plane,
                            from = rightTopPointBeforeAnchorNode?.worldPosition!!,
                            to = rightDownPointBeforeAnchorNode?.worldPosition!!,
                            nodePoint = 2
                        )
                        addLineBetweenPoints(
                            scene = arFragment?.arSceneView?.scene,
                            plane = plane,
                            from = rightDownPointBeforeAnchorNode?.worldPosition!!,
                            to = leftDownPointBeforeAnchorNode?.worldPosition!!,
                            nodePoint = 3
                        )
                        addLineBetweenPoints(
                            scene = arFragment?.arSceneView?.scene,
                            plane = plane,
                            from = leftDownPointBeforeAnchorNode?.worldPosition!!,
                            to = leftTopPointBeforeAnchorNode?.worldPosition!!,
                            nodePoint = 4
                        )
                    }
                }
            }
        }
    }

    //사각형 꼭짓점에 구렌더러블 만들기
    // 1 -> 왼쪽 위,
    // 2 -> 오른쪽 위,
    // 3 -> 왼쪽 아래,
    // 4 -> 오른쪽 아래
    private fun createCircleRender(
        widthRatio: Float,
        heightRatio: Float,
        frame: Frame,
        plane: Plane,
        nodePoint: Int
    ) {
        val screenPoint = getScreenPoint(widthRatio, heightRatio)

        val hitPoint = frame.hitTest(screenPoint.x, screenPoint.y)

        val hitIterator = hitPoint.iterator()

        while (hitIterator.hasNext()) {

            val anchorNode = putMoveRenderInPlane(plane, hitIterator)

            //전에 저장한 노드가 있다면, 지워준다.
            //후에는 현재노드를 이전노드에 다시 넣어준다.
            //물론 첫실행때는 변수에 현재노드를 넣어주기만 한다.(위에 moveRenderable은 실행하지 않음)
            //node 포인트 따라 구분지어 넣어준다.
            when (nodePoint) {
                1 -> {
                    if (leftTopPointBeforeAnchorNode != null) {
                        arFragment!!.arSceneView.scene.removeChild(leftTopPointBeforeAnchorNode)
                        leftTopPointBeforeAnchorNode?.setParent(null)
                    }
                    leftTopPointBeforeAnchorNode = anchorNode
                }
                2 -> {
                    if (rightTopPointBeforeAnchorNode != null) {
                        arFragment!!.arSceneView.scene.removeChild(rightTopPointBeforeAnchorNode)
                        rightTopPointBeforeAnchorNode?.setParent(null)
                    }
                    rightTopPointBeforeAnchorNode = anchorNode
                }
                3 -> {
                    if (leftDownPointBeforeAnchorNode != null) {
                        arFragment!!.arSceneView.scene.removeChild(leftDownPointBeforeAnchorNode)
                        leftDownPointBeforeAnchorNode?.setParent(null)
                    }
                    leftDownPointBeforeAnchorNode = anchorNode
                }
                else -> {
                    if (rightDownPointBeforeAnchorNode != null) {
                        arFragment!!.arSceneView.scene.removeChild(rightDownPointBeforeAnchorNode)
                        rightDownPointBeforeAnchorNode?.setParent(null)
                    }
                    rightDownPointBeforeAnchorNode = anchorNode
                }
            }
        }
    }

    //사각형의 꼭지점 스크린 포인트 가져오기
    private fun getScreenPoint(widthRatio: Float = 1.0f, heightRatio: Float = 1.0f): Vector3 {
        val vw = findViewById<View>(android.R.id.content)
        return Vector3(vw.width / widthRatio, vw.height / heightRatio, 0f)
    }

    //움직이는 노드 (왼쪽 위, 오른쪽 위)
    //렌더를 plane에 두기
    //이동시킬 노드를 얻기 위해 반환한다.
    private fun putMoveRenderInPlane(
        plane: Plane,
        hitTestIterator: MutableIterator<HitResult>
    ): AnchorNode {

        val hitResult = hitTestIterator.next()

        //평면에 앵커 만들기
        val modelAnchor = plane.createAnchor(hitResult.hitPose)

        //secne을 부모로 사용하여 앵커에 노드를 연결한다.
        val anchorNode = AnchorNode(modelAnchor).apply {
            isSmoothed = true
            setParent(arFragment?.arSceneView?.scene)
            renderable = cubeRenderable
            //실제 위치를 변경하여 테이블 상단에 개체가 렌더링되도록 합니다.
//            worldPosition = Vector3(
//                modelAnchor.pose.tx(),
//                modelAnchor.pose.compose(Pose.makeTranslation(0f, 0.05f, 0f)).ty(),
//                modelAnchor.pose.tz()
//            )
        }

        return anchorNode
    }

    //첫 사용 이후 저장된 라인노드들이다.
    private var beforeLeftTopToRightTopLineNode: Node? = null
    private var beforeRightTopToRightDownLineNode: Node? = null
    private var beforeRightDownToLeftDownLineNode: Node? = null
    private var beforeLeftDownToLeftTopLineNode: Node? = null

    private fun addLineBetweenPoints(scene: Scene?, plane: Plane, from: Vector3, to: Vector3, nodePoint: Int) {
        if(scene == null) return

        // prepare an anchor position
        val camQ = scene.camera.worldRotation
        val f1 = floatArrayOf(to.x, to.y, to.z)
        val f2 = floatArrayOf(camQ.x, camQ.y, camQ.z, camQ.w)
        val anchorPose = Pose(f1, f2)

        // make an ARCore Anchor
        val anchor: Anchor = plane.createAnchor(anchorPose)
        // Node that is automatically positioned in world space based on the ARCore Anchor.
        val anchorNode = AnchorNode(anchor)
        anchorNode.setParent(scene)

        // Compute a line's length
        val lineLength = Vector3.subtract(from, to).length()

        // Prepare a color
        val colorOrange = Color(android.graphics.Color.parseColor("#808080"))

        // 1. make a material by the color
        MaterialFactory.makeOpaqueWithColor(this, colorOrange)
            .thenAccept { material: Material? ->
                // 2. make a model by the material
                val model = ShapeFactory.makeCylinder(
                    0.00125f, lineLength,
                    Vector3(0f, lineLength / 2, 0f), material
                )
                model.isShadowReceiver = false
                model.isShadowCaster = false

                // 3. make node
                val lineNode = Node().apply {
                    renderable = model
                    setParent(anchorNode)

                    // 4. set rotation
                    val difference = Vector3.subtract(to, from)
                    val directionFromTopToBottom = difference.normalized()
                    val rotationFromAToB =
                        Quaternion.lookRotation(
                            directionFromTopToBottom,
                            Vector3.up()
                        )
                    worldRotation = Quaternion.multiply(
                        rotationFromAToB,
                        Quaternion.axisAngle(Vector3(1.0f, 0.0f, 0.0f), 90f)
                    )
                }

                when (nodePoint) {
                    1 -> {
                        if (beforeLeftTopToRightTopLineNode != null) {
                            arFragment!!.arSceneView.scene.removeChild(
                                beforeLeftTopToRightTopLineNode
                            )
                            beforeLeftTopToRightTopLineNode?.setParent(null)
                        }
                        beforeLeftTopToRightTopLineNode = lineNode
                    }
                    2 -> {
                        if (beforeRightTopToRightDownLineNode != null) {
                            arFragment!!.arSceneView.scene.removeChild(
                                beforeRightTopToRightDownLineNode
                            )
                            beforeRightTopToRightDownLineNode?.setParent(null)
                        }
                        beforeRightTopToRightDownLineNode = lineNode
                    }
                    3 -> {
                        if (beforeRightDownToLeftDownLineNode != null) {
                            arFragment!!.arSceneView.scene.removeChild(
                                beforeRightDownToLeftDownLineNode
                            )
                            beforeRightDownToLeftDownLineNode?.setParent(null)
                        }
                        beforeRightDownToLeftDownLineNode = lineNode
                    }
                    else -> {
                        if (beforeLeftDownToLeftTopLineNode != null) {
                            arFragment!!.arSceneView.scene.removeChild(
                                beforeLeftDownToLeftTopLineNode
                            )
                            beforeLeftDownToLeftTopLineNode?.setParent(null)
                        }
                        beforeLeftDownToLeftTopLineNode = lineNode
                    }
                }
            }
    }
}