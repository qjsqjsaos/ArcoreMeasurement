package com.shibuiwilliam.arcoremeasurement.measurement

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import com.shibuiwilliam.arcoremeasurement.measurement.state.CaptureState
import com.shibuiwilliam.arcoremeasurement.measurement.state.ErrorType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.sqrt

class MeasurementViewModel(application: Application)  : AndroidViewModel(application) {

    private val _captureState: MutableStateFlow<CaptureState> = MutableStateFlow(CaptureState.Default)
    val captureState: StateFlow<CaptureState> = _captureState


    suspend fun getImage() {
        _captureState.emit(CaptureState.Loading)
        //OpenCV 초기화에 실패시 return
        if(!OpenCVLoader.initDebug()) {
            _captureState.emit(CaptureState.Failure(ErrorType.OPENCV_INIT_FAILED))
            return
        }
        // 좌상단부터 시계 반대 방향으로 정점을 정렬한다.
        val points = arrayListOf(
            Point(),
            Point(),
            Point(),
            Point(),
        )
//            Log.d("points[0] : ", points[0].toString())
//            Log.d("points[1] : ", points[1].toString())
//            Log.d("points[2] : ", points[2].toString())
//            Log.d("points[3] : ", points[3].toString())

        points.sortBy { it.x } // x좌표 기준으로 먼저 정렬

        if (points[0].y > points[1].y) {
            val temp = points[0]
            points[0] = points[1]
            points[1] = temp
        }

        if (points[2].y < points[3].y) {
            val temp = points[2]
            points[2] = points[3]
            points[3] = temp
        }
        // 원본 영상 내 정점들
        val srcQuad = MatOfPoint2f().apply { fromList(points) }

        val maxSize = calculateMaxWidthHeight(
            tl = points[0],
            bl = points[1],
            br = points[2],
            tr = points[3]
        )
        val dw = maxSize.width
        val dh = dw * maxSize.height/maxSize.width
        val dstQuad = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(0.0, dh),
            Point(dw, dh),
            Point(dw, 0.0)
        )
        // 투시변환 매트릭스 구하기
        val perspectiveTransform = Imgproc.getPerspectiveTransform(srcQuad, dstQuad)

        // 투시변환 된 결과 영상 얻기
        val dst = Mat()
        Imgproc.warpPerspective(dst, dst, perspectiveTransform, Size(dw, dh))

        val bitmap = Bitmap.createBitmap(dst.cols(), dst.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(dst, bitmap)
        //성공시 비트맵 보내기
        _captureState.emit(CaptureState.Success(bitmap))
    }

    // 사각형 꼭짓점 정보로 사각형 최대 사이즈 구하기
    // 평면상 두 점 사이의 거리는 직각삼각형의 빗변길이 구하기와 동일
    private fun calculateMaxWidthHeight(
        tl:Point,
        tr:Point,
        br:Point,
        bl:Point,
    ): Size {
        // Calculate width
        val widthA = sqrt((tl.x - tr.x) * (tl.x - tr.x) + (tl.y - tr.y) * (tl.y - tr.y))
        val widthB = sqrt((bl.x - br.x) * (bl.x - br.x) + (bl.y - br.y) * (bl.y - br.y))
        val maxWidth = max(widthA, widthB)
        // Calculate height
        val heightA = sqrt((tl.x - bl.x) * (tl.x - bl.x) + (tl.y - bl.y) * (tl.y - bl.y))
        val heightB = sqrt((tr.x - br.x) * (tr.x - br.x) + (tr.y - br.y) * (tr.y - br.y))
        val maxHeight = max(heightA, heightB)
        return Size(maxWidth, maxHeight)
    }
}