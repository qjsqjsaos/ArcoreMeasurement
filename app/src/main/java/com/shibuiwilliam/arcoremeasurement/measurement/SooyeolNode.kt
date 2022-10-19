package com.shibuiwilliam.arcoremeasurement.measurement

import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.ux.TransformableNode
import com.google.ar.sceneform.ux.TransformationSystem

class SooyeolNode(
    coordinator: TransformationSystem,
) : TransformableNode(coordinator) {

    override fun onUpdate(frameTime: FrameTime) {
        facingCamera()
    }

    private fun facingCamera() {
        // Buggy when dragging because TranslationController already handles it's own rotation on each update.
        if (isTransforming) return /*Prevent infinite loop*/
        val camera = scene?.camera ?: return
        val direction = Vector3.subtract(camera.worldPosition, worldPosition)
        worldRotation = Quaternion.lookRotation(direction, Vector3.zero())


//        val q1: Quaternion = localRotation
//        val q2 = Quaternion.axisAngle(Vector3(0f, 1f, 0f), -2f)
//        localRotation = Quaternion.multiply(q1, q2)
//        localPosition = localPosition

//        //이거 검토해볼것
//        val q1: Quaternion = localRotation
//        val q2 = Quaternion.axisAngle(Vector3(1f, 0f, 0f), -2f)
//        worldRotation = Quaternion.multiply(q1, q2)

//        val q1: Quaternion = localRotation
//        val q2 = Quaternion.axisAngle(Vector3(1f, 0f, 0f), 2f)
//        localRotation = Quaternion.multiply(q1, q2)
    }
}