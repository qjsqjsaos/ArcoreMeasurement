package com.shibuiwilliam.arcoremeasurement

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.PixelCopy
import android.widget.*
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
import com.google.ar.sceneform.ux.TransformableNode
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!checkIsSupportedDeviceOrFinish(this)) {
            Toast.makeText(applicationContext, "Device not supported", Toast.LENGTH_LONG)
                .show()
        }

        setContentView(R.layout.activity_measurement)

        arFragment = supportFragmentManager.findFragmentById(R.id.sceneform_fragment) as ArFragment?
        arFragment?.arSceneView?.scene?.addOnUpdateListener(this::onUpdateFrame)

        initCM = resources.getString(R.string.initCM)

        initRenderable()
        clearButton()

//        arFragment!!.setOnTapArPlaneListener { hitResult: HitResult, _: Plane?, _: MotionEvent? ->
//            if (cubeRenderable == null) return@setOnTapArPlaneListener
//
//            // 앵커가 4개 이상이면 더 이상 생성하지 않는다.
//            if(placedAnchors.size < 4) {
//                tapDistanceOfMultiplePoints(hitResult)
//            }
//        }
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
//        clearButton.setOnClickListener { clearAllAnchors() }
        clearButton.setOnClickListener {
            takePhoto()
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

    //캡쳐
    private fun takePhoto() {
        val view = arFragment!!.arSceneView

        // Create a bitmap the size of the scene view.
        val bitmap = Bitmap.createBitmap(
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
                    saveBitmapToDisk(bitmap)
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
        }, android.os.Handler(Looper.myLooper()!!))
    }


    @Throws(IOException::class)
    fun saveBitmapToDisk(bitmap: Bitmap) {
        convertUri(bitmap, true)
    }

    fun convertUri(bitmap: Bitmap, isSave: Boolean): Uri?
            = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        Constants.getImageUri(this, bitmap)!!
    } else {
        Constants.getImageUriQ(this, bitmap, isSave)
    }

    //앵커 갯수
    var anchorCnt = 1

    //계속 호출되면서 앵커를 생성 (4개)
    private fun onUpdateFrame(frameTime: FrameTime?) {

        val frame = arFragment!!.arSceneView.arFrame ?: return

        // If there is no frame, just return.

        //Making sure ARCore is tracking some feature points, makes the augmentation little stable.
        if (frame.camera.trackingState === TrackingState.TRACKING) {

            val cameraPose = frame.camera.pose.extractRotation()

            //위치
            val pos: Pose?
            // TODO: 회의실에서 이거 위치 조정하기 위치 조정 후에 범위 좁혀가기
            //4개의 앵커 위치 설정
            when (anchorCnt++) {
                1 -> {
                    pos = cameraPose.compose(Pose.makeTranslation(0f,0f,-2f))
                }
                2 -> {
                    pos = cameraPose.compose(Pose.makeTranslation(0f,0f,-2f))
                }
                3 -> {
                    pos = cameraPose.compose(Pose.makeTranslation(0f,0f,-2f))
                }
                4 -> {
                    pos = cameraPose.compose(Pose.makeTranslation(0f,0f,-2f))
                }
                else -> {
                    clearAllAnchors()
                    anchorCnt = 1
                    return
                }
            }


            val anchor = arFragment!!.arSceneView.session!!.createAnchor(pos)
            placedAnchors.add(anchor)
            val anchorNode = AnchorNode(anchor).apply {
                isSmoothed = true
                setParent(arFragment!!.arSceneView.scene)
            }
            placedAnchorNodes.add(anchorNode)

            // Create the arrow node and add it to the anchor.
//            val arrow = Node()
//            arrow.setParent(anchorNode)
//            arrow.setParent(anchorNode)
//            arrow.renderable = cubeRenderable


            val node = TransformableNode(arFragment!!.transformationSystem)
                .apply {
                    this.rotationController.isEnabled = false
                    this.scaleController.isEnabled = false
                    this.translationController.isEnabled = true
                    this.renderable = renderable
                    setParent(anchorNode)
                }
            node.renderable = cubeRenderable
            arFragment!!.arSceneView.scene.addChild(anchorNode)
            node.select()
        }
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