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
        worldRotation = Quaternion.lookRotation(direction, Vector3.up())
    }
}