package com.shibuiwilliam.arcoremeasurement.measurement

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.*
import com.google.ar.sceneform.*
import com.google.ar.sceneform.collision.Ray
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.*
import com.google.ar.sceneform.rendering.PlaneRenderer.MATERIAL_TEXTURE
import com.google.ar.sceneform.rendering.PlaneRenderer.MATERIAL_UV_SCALE
import com.google.ar.sceneform.rendering.RenderableDefinition.Submesh
import com.shibuiwilliam.arcoremeasurement.R
import com.shibuiwilliam.arcoremeasurement.databinding.ActivityMeasurementBinding
import java.awt.Polygon
import java.nio.FloatBuffer
import java.util.*


class Measurement : AppCompatActivity(), Scene.OnUpdateListener {


    private val vm: MeasurementViewModel by viewModels()

    companion object {
        private const val MIN_OPENGL_VERSION = 3.0
        private val TAG: String = Measurement::class.java.simpleName
    }

    private var arFragment: CustomArFragment? = null

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
            (supportFragmentManager.findFragmentById(R.id.ar_fragment) as CustomArFragment?)

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

    //테스트용 렌더러블 빨간 공
    private fun cubeTestRenderable() {
        MaterialFactory.makeTransparentWithColor(
            this,
            Color(android.graphics.Color.RED)
        )
            .thenAccept { material: Material? ->
                cubeRenderable = ShapeFactory.makeSphere(0.05f, Vector3.zero(), material)
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

    override fun onUpdate(p0: FrameTime?) {
        var camera = arFragment?.arSceneView?.scene?.camera

        var ray: Ray? = camera?.screenPointToRay(200f, 500f)

        val frame = arFragment?.arSceneView?.arFrame
        if (frame != null) {

            val planeObj = frame.getUpdatedTrackables(Plane::class.java)
            val var3 = planeObj.iterator()
            while (var3.hasNext()) {
                val plane = var3.next() as Plane
                //바닥이 감지되고 arcore에서 추적 중인 경우
                if (plane.trackingState == TrackingState.TRACKING) {

                    plane.polygon
                    plane.centerPose.
                }
            }
        }
    }

    private fun drawTwoDPolygon() {
        val frame = arFragment?.arSceneView?.arFrame
        if (frame != null) {
            val planeObj = frame.getUpdatedTrackables(Plane::class.java)
            val var3 = planeObj.iterator()
            while (var3.hasNext()) {
                val plane = var3.next() as Plane
                if (plane.trackingState != TrackingState.TRACKING) return

                val planeFloatBuffer: FloatBuffer = plane.polygon

                val pose = plane.centerPose
                val polygonList: List<PolygonPoint> = ArrayList()

                {
                    var i = 0
                    while (i < planeFloatBuffer.remaining() - 1) {
                        val transformedPoint = pose.transformPoint(
                            floatArrayOf(
                                planeFloatBuffer.get(i), 0f,
                                planeFloatBuffer.get(i + 1)
                            )
                        )
                        polygonList.add(
                            PolygonPoint(
                                transformedPoint[0],
                                transformedPoint[1],
                                transformedPoint[2]
                            )
                        )
                        i += 2
                    }
                }


                if (polygonList.size > 0) {
                    val uv0 = UvCoordinate(0, 0)
                    val vertexPerFeature = 3
                    val vertexPerFace = 3
                    val numFaces = 4
                    val numFeatures = 1
                    val polygon = Polygon(polygonList)
                    Poly2Tri.triangulate(polygon)
                    val triangles: List<DelaunayTriangle> = polygon.getTriangles()
                    val numPoints = triangles.size * vertexPerFeature
                    val indexPerFeature = numFaces * vertexPerFace
                    val numIndices = triangles.size * indexPerFeature
                    if (ptbuffer == null || ptbuffer.length < numPoints) {
                        ptbuffer = arrayOfNulls<Vertex>(numPoints)
                        indexbuffer = IntArray(numIndices)
                    }
                    var idx = 0
                    for (triangle in triangles) {
                        val vertexBase = idx * vertexPerFeature
                        ptbuffer.get(vertexBase) = Vertex.builder().setPosition(
                            Vector3(
                                triangle.points.get(0).getXf(),
                                triangle.points.get(0).getYf(),
                                triangle.points.get(0).getZf()
                            )
                        )
                            .setUvCoordinate(uv0)
                            .setNormal(Vector3(0.5f, 0.5f, 0.5f))
                            .build()
                        ptbuffer.get(vertexBase + 1) = Vertex.builder().setPosition(
                            Vector3(
                                triangle.points.get(1).getXf(),
                                triangle.points.get(1).getYf(),
                                triangle.points.get(1).getZf()
                            )
                        )
                            .setUvCoordinate(uv0)
                            .setNormal(Vector3(0.5f, 0.5f, 0.5f))
                            .build()
                        ptbuffer.get(vertexBase + 2) = Vertex.builder().setPosition(
                            Vector3(
                                triangle.points.get(2).getXf(),
                                triangle.points.get(2).getYf(),
                                triangle.points.get(2).getZf()
                            )
                        )
                            .setUvCoordinate(uv0)
                            .setNormal(Vector3(0.5f, 0.5f, 0.5f))
                            .build()
                        val featureBase = idx * indexPerFeature

                        // left 0 1 2
                        indexbuffer.get(featureBase + 2) = vertexBase
                        indexbuffer.get(featureBase) = vertexBase + 1
                        indexbuffer.get(featureBase + 1) = vertexBase + 2
                        idx++
                    }
                    val submesh = Submesh.builder()
                        .setName("pointcloud")
                        .setMaterial(materialHolder.getNow(null))
                        .setTriangleIndices(
                            IntStream.of(indexbuffer)
                                .limit(numIndices)
                                .boxed()
                                .collect(Collectors.toList())
                        )
                        .build()
                    val def = RenderableDefinition.builder()
                        .setVertices(
                            Stream.of(ptbuffer).limit(numPoints).collect(Collectors.toList())
                        )
                        .setSubmeshes(Stream.of(submesh).collect(Collectors.toList()))
                        .build()
                    ModelRenderable.builder().setSource(def).build()
                        .thenAccept { renderable: ModelRenderable ->
                            renderable.isShadowCaster = false
                            setRenderable(renderable)
                        }
                }
            }
        }
    }
}