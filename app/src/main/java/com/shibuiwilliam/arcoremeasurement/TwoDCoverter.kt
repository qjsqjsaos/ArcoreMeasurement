package com.shibuiwilliam.arcoremeasurement

import android.opengl.Matrix

object TwoDCoverter {

    //wolrd에서 화면을 변활 행렬 계산 매트릭스
    fun calculateWorld2CameraMatrix(
        modelmtx: FloatArray?,
        viewmtx: FloatArray?,
        prjmtx: FloatArray?
    ): FloatArray? {
        val scaleFactor = 1.0f
        val scaleMatrix = FloatArray(16)
        val modelXscale = FloatArray(16)
        val viewXmodelXscale = FloatArray(16)
        val world2screenMatrix = FloatArray(16)
        Matrix.setIdentityM(scaleMatrix, 0)
        scaleMatrix[0] = scaleFactor
        scaleMatrix[5] = scaleFactor
        scaleMatrix[10] = scaleFactor
        Matrix.multiplyMM(modelXscale, 0, modelmtx, 0, scaleMatrix, 0)
        Matrix.multiplyMM(viewXmodelXscale, 0, viewmtx, 0, modelXscale, 0)
        Matrix.multiplyMM(world2screenMatrix, 0, prjmtx, 0, viewXmodelXscale, 0)
        return world2screenMatrix
    }

    //calculateWorld2CameraMatrix 메서드를 사용하면 3d 세계에서 2d 점으로 투영할 수 있지만,
    //이 투영중에 NDC 좌표에서 화면으로 변환해야한다. 이 아래 메서드로 말이다.
    fun world2Screen(
        screenWidth: Int,
        screenHeight: Int,
        world2cameraMatrix: FloatArray?
    ): DoubleArray? {
        val origin = floatArrayOf(0f, 0f, 0f, 1f)
        val ndcCoord = FloatArray(4)
        Matrix.multiplyMV(ndcCoord, 0, world2cameraMatrix, 0, origin, 0)
        ndcCoord[0] = ndcCoord[0] / ndcCoord[3]
        ndcCoord[1] = ndcCoord[1] / ndcCoord[3]
        val pos_2d = doubleArrayOf(0.0, 0.0)
        pos_2d[0] = screenWidth * ((ndcCoord[0] + 1.0) / 2.0)
        pos_2d[1] = screenHeight * ((1.0 - ndcCoord[1]) / 2.0)
        return pos_2d
    }

}