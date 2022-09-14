package com.shibuiwilliam.arcoremeasurement.measurement.state

sealed class CaptureState{
    object Default: CaptureState()
    object Loading : CaptureState()
    data class Failure(val type: ErrorType? = ErrorType.DEFALUT) : CaptureState()
    data class Success<T>(val data : T) : CaptureState()
}

enum class ErrorType {
    NO_CONTOUR, //윤곽이 보이지 않음
    TOO_SMALL, //너무 작음
    NO_BORDER_DETECTION, //테두리가 감지 되지 않음
    IS_CONVEX, //볼록한 도형인지
    OPENCV_INIT_FAILED, //OpenCv 초기화 실패
    DEFALUT //기본값
}