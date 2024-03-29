package com.maxim.shacamera.camera.data

import android.graphics.Rect
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureRequest.Builder
import android.os.Handler
import android.view.MotionEvent
import kotlin.math.roundToInt
import kotlin.math.sqrt

interface CameraRepository {

    fun handleZoom(
        cameraCharacteristics: CameraCharacteristics,
        event: MotionEvent,
        screenMinSize: Int,
        captureRequestBuilder: Builder,
        cameraCaptureSession: CameraCaptureSession,
        handler: Handler,
        isRecording: Boolean
    ): Pair<Float, Boolean>

    fun bitmapZoom(): Float
    fun setZoom(captureRequestBuilder: Builder): Float
    fun setCameraZoomToMax(
        cameraCharacteristics: CameraCharacteristics,
        captureRequestBuilder: Builder
    ): Float

    class Base : CameraRepository {
        private var fingerSpacing = 0f
        private var zoomLevel = 1f
        private var zoom: Rect? = null
        private var bitmapZoom = 1f

        override fun handleZoom(
            cameraCharacteristics: CameraCharacteristics,
            event: MotionEvent,
            screenMinSize: Int,
            captureRequestBuilder: Builder,
            cameraCaptureSession: CameraCaptureSession,
            handler: Handler,
            isRecording: Boolean
        ): Pair<Float, Boolean> {
            val rect =
                cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                    ?: return Pair(zoomLevel * bitmapZoom, false)
            val maxZoomLevel =
                cameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
                    ?: return Pair(zoomLevel * bitmapZoom, false)

            if (event.pointerCount == 2) {
                val currentFingerSpacing = getFingerSpacing(event)
                var delta = 0.8f //Control this value to control the zooming sensibility
                if (fingerSpacing != 0f) {
                    if (currentFingerSpacing > fingerSpacing) {
                        if ((maxZoomLevel - zoomLevel) <= delta) {
                            delta = maxZoomLevel - zoomLevel
                            if ((screenMinSize / (bitmapZoom + bitmapZoom / 25)).toInt() != 0 && !isRecording)
                                bitmapZoom += bitmapZoom / 25 //Control this value to control the bitmap zooming sensibility
                        }
                        zoomLevel += delta
                    } else if (currentFingerSpacing < fingerSpacing) {
                        if ((zoomLevel - delta) < 1f) {
                            delta = zoomLevel - 1f
                        }
                        if (bitmapZoom == 1f)
                            zoomLevel -= delta
                        else {
                            bitmapZoom -= bitmapZoom / 25 //Control this value to control the bitmap zooming sensibility
                            if (bitmapZoom < 1f)
                                bitmapZoom = 1f
                        }
                    }

                    val ratio = 1f / zoomLevel
                    val croppedWidth =
                        rect.width() - (rect.width().toFloat() * ratio).roundToInt()
                    val croppedHeight =
                        rect.height() - (rect.height().toFloat() * ratio).roundToInt()
                    zoom = Rect(
                        croppedWidth / 2, croppedHeight / 2,
                        rect.width() - croppedWidth / 2, rect.height() - croppedHeight / 2
                    )
                    captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom)
                }
                fingerSpacing = currentFingerSpacing
            } else {
                return Pair(zoomLevel * bitmapZoom, true)
            }
            cameraCaptureSession.setRepeatingRequest(
                captureRequestBuilder.build(),
                null,
                handler
            )
            return Pair(zoomLevel * bitmapZoom, true)
        }

        override fun bitmapZoom() = bitmapZoom
        override fun setZoom(captureRequestBuilder: Builder): Float {
            captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom)
            return zoomLevel * bitmapZoom
        }

        override fun setCameraZoomToMax(
            cameraCharacteristics: CameraCharacteristics,
            captureRequestBuilder: Builder
        ): Float {
            bitmapZoom = 1f
            captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoom)
            return zoomLevel
        }

        private fun getFingerSpacing(event: MotionEvent): Float {
            val x = event.getX(0) - event.getX(1)
            val y = event.getY(0) - event.getY(1)
            return sqrt((x * x + y * y).toDouble()).toFloat()
        }
    }
}