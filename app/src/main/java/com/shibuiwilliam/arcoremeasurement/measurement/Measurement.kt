package com.shibuiwilliam.arcoremeasurement.measurement

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.hardware.*
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.ar.core.*
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.collision.Ray
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.TransformableNode
import com.shibuiwilliam.arcoremeasurement.R
import com.shibuiwilliam.arcoremeasurement.databinding.ActivityMeasurementBinding
import com.shibuiwilliam.arcoremeasurement.databinding.PhotoFrameLayoutBinding
import kotlinx.coroutines.launch
import java.util.*


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

        //포토프레임 레이아웃 바인딩하기
        photoFrameBinding = PhotoFrameLayoutBinding.inflate(layoutInflater)

        //센서매니저를 초기화 해주고, 실기기 디바이스의 회전값을 얻기 위해
        //가속도계 센서 유형과 자기장 센서 유형을 가져온다.
        mSensorManager = getSystemService(SENSOR_SERVICE) as SensorManager?
        accelerometer = mSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = mSensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        arFragment = (supportFragmentManager.findFragmentById(R.id.ar_fragment) as CustomArFragment?)

        //플레인 렌더를 숨기고, 비활성 시켜서 arcore의 속도를 최적화 한다.
        arFragment?.planeDiscoveryController?.hide()
        arFragment?.planeDiscoveryController?.setInstructionView(null)
        arFragment?.arSceneView?.planeRenderer?.isEnabled = false
        arFragment?.arSceneView?.scene?.addOnUpdateListener(this@Measurement::onUpdate)

        binding.takePhoto.setOnClickListener {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    launch {
//                        vm.getImage()
                    }
                }
            }
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
        val layoutParams = FrameLayout.LayoutParams(
            convertPixelsToDp(width.toFloat()),
            convertPixelsToDp(height.toFloat())
        )
        //설정한 가로와 세로 값으로 레이아웃의 가로세로길이를 수정한다.
        photoFrameBinding.photoFrameLayout.layoutParams = layoutParams

        //뷰 렌더러블을 생성한다. (액자 프레임 xml로)
        ViewRenderable.builder()
            .setView(this, photoFrameBinding.photoFrameLayout)
            .build()
            .thenAccept { renderable: ViewRenderable ->
                frameRenderable = renderable.apply {
                    //그림자를 없애준다.
                    isShadowCaster = false
                    isShadowReceiver = false

                    //회전 축 가운데로 (수직, 수평 둘다)
                    verticalAlignment = ViewRenderable.VerticalAlignment.CENTER
                    horizontalAlignment = ViewRenderable.HorizontalAlignment.CENTER
                    collisionShape = null
                }
            }

    }

    //픽셀을 디피로 전환해주는 메서드
    private fun convertPixelsToDp(px: Float): Int {
        return (px / (resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)).toInt()
    }

    //권한체크 설정 메서드
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
    //세로일때
    private var nodeChangePortDegree = 0f
    //가로일때
    private var nodeChangeLandDegree = 0f

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
                    } else {
                        //노드가 있다면
                        frameNode?.apply {
                            val camera = arFragment?.arSceneView?.scene?.camera
                            val ray: Ray?
                            localRotation = if(isLandScape) {
                                //디바이스가 가로일때
                                Quaternion.axisAngle(
                                    Vector3(0f, 1f, 0f),
                                    nodeChangeLandDegree
                                )
                            } else {
                                //디바이스가 세로일때
                                Quaternion.axisAngle(
                                    Vector3(1f, 0f, 0f),
                                    nodeChangePortDegree
                                )
                            }

                            // 디바이스 화면 기준으로 노드를 생성할 위치를 가져온다.
                            val screenPoint = getScreenPoint()

                            //화면을 마주보게 만들어주는 screenPointToRay를 사용하여, x y 값을 넣어준다.
                            ray = camera?.screenPointToRay(
                                screenPoint.x, screenPoint.y
                            )

                            // ray에서 얼마나 떨어져 있는지 설정한다.
                            worldPosition = ray?.getPoint(0.8f)

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
    private fun getScreenPoint(widthRatio: Float = 2.0f, heightRatio: Float = 2.0f): Vector3 {
        val vw = findViewById<View>(android.R.id.content)
        return Vector3(vw.width / widthRatio, vw.height / heightRatio, 0f)
    }

    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var mSensorManager: SensorManager? = null
    private var mGravity: FloatArray? = null
    private var mGeomagnetic: FloatArray? = null
    private var isLandScape = false

    //각도를 계속 갱신해준다.
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) mGravity = event.values
        if (event?.sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) mGeomagnetic = event.values
        if (mGravity != null && mGeomagnetic != null) {
            val r = FloatArray(9)
            val l = FloatArray(9)
            val success = SensorManager.getRotationMatrix(r, l, mGravity, mGeomagnetic)
            if (success) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(r, orientation)
                //방위각 x값 얻기
                val azimuthX = orientation[1].toDouble()
                //경사각 z값 얻기
//                val pitchZ = orientation[0].toDouble()
                //롤각 y값 얻기
                val rollY = orientation[2].toDouble()

                //실제 디바이스가 portrait인지 landscape인지 구별해주는 코드이다.
                if (event?.sensor == accelerometer) {
                    if(kotlin.math.abs(event?.values!![1]) > kotlin.math.abs(event.values[0])) {
                        //Mainly portrait
                        if (event.values[1] > 1) {
                            //Portrait
                        } else if (event.values[1] < -1) { //Inverse portrait
                            }
                        //방위각을 각도로 변환하고,
                        //해당 각도에 x값을 넣는다.
                        nodeChangePortDegree = Math.toDegrees(azimuthX).toFloat()
                        isLandScape = false
                    }else{
                        //Mainly landscape
                        if (event.values[0] > 1) {
                            //Landscape - right side up
                        } else if (event.values[0] < -1) {
                            //Landscape - left side up
                        }
                        //롤각을 각도로 변환하고,
                        //해당 각도에 y값을 넣는다.
                        nodeChangeLandDegree = -Math.toDegrees(rollY).toFloat()
                        isLandScape = true
                    }
                }
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