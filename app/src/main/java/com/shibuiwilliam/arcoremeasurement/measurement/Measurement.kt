package com.shibuiwilliam.arcoremeasurement.measurement

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.PlaneRenderer.MATERIAL_TEXTURE
import com.google.ar.sceneform.rendering.PlaneRenderer.MATERIAL_UV_SCALE
import com.google.ar.sceneform.rendering.Texture
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.TransformableNode
import com.shibuiwilliam.arcoremeasurement.R
import com.shibuiwilliam.arcoremeasurement.databinding.ActivityMeasurementBinding
import com.shibuiwilliam.arcoremeasurement.databinding.PhotoFrameLayoutBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt


class Measurement : AppCompatActivity(), Scene.OnUpdateListener,
    SensorEventListener {


    private val vm: MeasurementViewModel by viewModels()

    companion object {
        private const val MIN_OPENGL_VERSION = 3.0
        private val TAG: String = Measurement::class.java.simpleName
    }

    private var arFragment: CustomArFragment? = null

    private var frameRenderable: ViewRenderable? = null

    private var frameNode: TransformableNode? = null

    private lateinit var binding: ActivityMeasurementBinding

    private lateinit var photoFrameBinding: PhotoFrameLayoutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!checkIsSupportedDeviceOrFinish(this)) {
            Toast.makeText(applicationContext, "Device not supported", Toast.LENGTH_LONG)
                .show()
        }
        binding = ActivityMeasurementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        photoFrameBinding = PhotoFrameLayoutBinding.inflate(layoutInflater)

        arFragment =
            (supportFragmentManager.findFragmentById(R.id.ar_fragment) as CustomArFragment?)

        arFragment?.arSceneView?.scene?.addOnUpdateListener(this@Measurement::onUpdate)

        mSensorManager = getSystemService(SENSOR_SERVICE) as SensorManager?
        accelerometer = mSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = mSensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

//        setPlaneTexture("images.png")
    }


    //plane 텍스쳐 입히기
    /**
     * Sets the plane renderer texture.
     * @param texturePath - Path to texture to use in the assets directory.
     */
    private fun setPlaneTexture(texturePath: String) {
        val sampler = Texture.Sampler.builder()
            .setMinFilter(Texture.Sampler.MinFilter.LINEAR_MIPMAP_LINEAR)
            .setMagFilter(Texture.Sampler.MagFilter.LINEAR)
            .setWrapModeR(Texture.Sampler.WrapMode.REPEAT)
            .setWrapModeS(Texture.Sampler.WrapMode.REPEAT)
            .setWrapModeT(Texture.Sampler.WrapMode.REPEAT)
            .build()
        Texture.builder().setSource { assets.open(texturePath) }
            .setSampler(sampler)
            .build().thenAccept { texture ->
                Toast.makeText(this, texture.toString(), Toast.LENGTH_SHORT).show()
                arFragment?.arSceneView?.planeRenderer?.material?.thenAccept { material ->
                    material.setTexture(MATERIAL_TEXTURE, texture)
                    material.setFloat(
                        MATERIAL_UV_SCALE,
                        10f
                    )
                }
            }.exceptionally { ex ->
                Log.e(TAG, "Failed to read an asset file", ex)
                null
            }
    }

    //화면에 xml이 그려질때 호출한다.
    //가로 세로 정보가 넘어오질 않아, 여기서 렌더러블을 구하기로함.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        createFrameRenderable()
    }

    //액자 렌더러블 만들기
    private fun createFrameRenderable() {
        //디바이스 정보
        val width = binding.linearLayout.width
        val height = binding.linearLayout.height
        Log.e(TAG, "가로 ${width}, 세로 ${height}")
        val layoutParams = FrameLayout.LayoutParams(
            convertPixelsToDp(width.toFloat()),
            convertPixelsToDp(height.toFloat())
        )
        photoFrameBinding.photoFrameLayout.layoutParams = layoutParams

        ViewRenderable.builder()
            .setView(this, photoFrameBinding.photoFrameLayout)
            .build()
            .thenAccept { renderable: ViewRenderable ->
                frameRenderable = renderable.apply {
                    isShadowCaster = false
                    isShadowReceiver = false
                }
            }

    }

    //픽셀을 디피로 전환
    private fun convertPixelsToDp(px: Float): Int {
        return (px / (resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)).toInt()
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


    //노드가 있는지 없는지
    private var hasNode = false

    //노드의 각도 설정
    //이각도는 디바이스의 위치 따라 변화하는 각도 값이다.
    private var nodeChangeDegree = 0f

    override fun onUpdate(p0: FrameTime?) {
        //view에서 frame 가져오기
        val frame = arFragment?.arSceneView?.arFrame
        if (frame != null) {

            val planeObj = frame.getUpdatedTrackables(Plane::class.java)

            //plane이 감지되었는지 확인하는 추가적인 작업
            val var3 = planeObj.iterator()
            while (var3.hasNext()) {
                val plane = var3.next() as Plane

                //바닥이 감지되고 arcore에서 추적 중인 경우
                if (plane.trackingState == TrackingState.TRACKING) {

                    //노드가 없다면
                    //렌더러블 만들고 더 이상 만들지 않기
                    if (!hasNode) {
                        createFrameNode()
                        hasNode = true
                    } else { //노드가 있다면
                        // TODO: 여기서는 위치 조정을 해주기, 단 노드를 지우지 않고, 위치 변경으로만, 그리고 다하면 회전값도 넣어주기

                        //회전값 넣어주기
                        val cp = plane.centerPose
//                        planeNode?.worldRotation = Quaternion(cp.qx(), cp.qy(), cp.qz(), cp.qw())

                        //가운데 화면 위치 가져오기
                        val screenPoint = getScreenPoint(2.0f, 1.0f)

                        val camera = arFragment?.arSceneView?.scene?.camera

                        val ray = camera?.screenPointToRay(
                            screenPoint.x, screenPoint.y
                        )

                        val cameraPos: Vector3 = camera?.worldPosition!!
                        val cameraForward =
                            Vector3.add(cameraPos, camera?.forward?.scaled(1.0f))
                        var forwardVector = Vector3.subtract(cameraForward, cameraPos)
                        forwardVector = Vector3(forwardVector.x, forwardVector.y, 0f)

                        //x축에서 고정해준다.
                        var degreesFromCamToNegXAxis =
                            Vector3.angleBetweenVectors(Vector3.forward(), forwardVector).toDouble()

                        val dotProduct = Vector3.dot(Vector3.up(), forwardVector)


                        if (dotProduct < 0) {
                            degreesFromCamToNegXAxis = -degreesFromCamToNegXAxis
                        }

//                        frameNode?.apply {
//                            worldPosition = ray!!.getPoint(.8f)
//                            localRotation = Quaternion.axisAngle(
//                                Vector3(1f, 0f, 0f),
//                                degreesFromCamToNegXAxis.toFloat() + (nodeChangeDegree + 80)
//                            )
//                            setParent(camera)
//                        }
                        val screenOrientation = resources.configuration.orientation
                        // TODO: 회전방향에 따라 아래 값을 다르게 적용시켜 보자 
                        frameNode?.apply {
                            worldPosition = ray!!.getPoint(.8f)
                            localRotation = Quaternion.axisAngle(
                                Vector3(1f, 0f, 0f),
                                nodeChangeDegree
                            )
                            setParent(camera)
                        }
                    }
                }
            }
        }
    }

    //카메라 앞에 떠있는 앵커와 그를 포함한 노드 만들기
    private fun createFrameNode() {
        val transformationSystem = arFragment!!.transformationSystem
        val node = TransformableNode(transformationSystem).apply {
            renderable = frameRenderable
            translationController?.isEnabled = false
        }
        //노드 전역 변수에 넣어주기
        frameNode = node
    }

    //사각형의 꼭지점 스크린 포인트 가져오기
    private fun getScreenPoint(widthRatio: Float = 1.0f, heightRatio: Float = 1.0f): Vector3 {
        val vw = findViewById<View>(android.R.id.content)
        return Vector3(vw.width / widthRatio, vw.height / heightRatio, 0f)
    }

    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var mSensorManager: SensorManager? = null
    private var mGravity: FloatArray? = null
    private var mGeomagnetic: FloatArray? = null
    //각도를 계속 갱신해준다.
    override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) mGravity = event.values
            if (event?.sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) mGeomagnetic = event.values;
            if (mGravity != null && mGeomagnetic != null) {
                val r = FloatArray(9)
                val l = FloatArray(9)
                val success = SensorManager.getRotationMatrix (r, l, mGravity, mGeomagnetic)
                if (success) {
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(r, orientation)
                    //방위각 x값 얻기
                    val azimuth = orientation[1].toDouble()
                    //방위각을 각도로 변환하고,
                    //해당 각도에 x값을 넣는다.
                    nodeChangeDegree = Math.toDegrees(azimuth).toFloat()
                }
            }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()
        mSensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        mSensorManager?.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onPause() {
        super.onPause()
        mSensorManager?.unregisterListener(this)
    }
}