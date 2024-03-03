package com.maxim.shacamera.camera.presentation

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.graphics.drawable.toBitmap
import com.maxim.shacamera.R
import com.maxim.shacamera.core.presentation.BaseFragment
import com.maxim.shacamera.databinding.FragmentCameraBinding
import java.io.File
import java.io.FileOutputStream

class CameraFragment : BaseFragment<FragmentCameraBinding, CameraViewModel>(), ManageCamera {
    override val viewModelClass = CameraViewModel::class.java
    override fun bind(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentCameraBinding.inflate(inflater, container, false)

    private var backgroundThread: HandlerThread? = null
    private var handler: Handler? = null

    private val myCameras = mutableListOf<CameraService>()
    private var cameraId = ""

    private var cameraManager: CameraManager? = null

    var captureRequestBuilder: CaptureRequest.Builder? = null
    var cameraCaptureSession: CameraCaptureSession? = null

    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            viewModel.openCamera(this@CameraFragment)
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            val bitmap = binding.textureView.bitmap!!
            val width = (binding.textureView.width / viewModel.bitmapZoom()).toInt()
            val height = (binding.textureView.height / viewModel.bitmapZoom()).toInt()
            val newBitmap = Bitmap.createBitmap(
                bitmap,
                bitmap.width / 2 - width / 2,
                bitmap.height / 2 - height / 2,
                width,
                height
            )
            binding.imageView.setImageBitmap(newBitmap)
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) =
            Unit

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = false
    }


    private val zoomListener = object : View.OnTouchListener {

        @RequiresApi(Build.VERSION_CODES.S)
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            return viewModel.handleZoom(
                cameraManager!!.getCameraCharacteristics(cameraId),
                event!!,
                listOf(binding.textureView.width, binding.textureView.height).min(),
                binding.zoomValueTextView,
                captureRequestBuilder!!,
                cameraCaptureSession!!,
                handler!!
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraManager = requireActivity().getSystemService(Context.CAMERA_SERVICE) as CameraManager
        myCameras.clear()
        myCameras.addAll(cameraManager!!.cameraIdList.map { id ->
            CameraService.Base(id, cameraManager!!, this)
        })

        binding.photoButton.setOnClickListener {
            viewModel.makePhoto(this)
        }

        binding.changeCameraButton.setOnClickListener {
            viewModel.changeCamera(this, cameraId.toInt())
        }

        binding.textureView.setOnTouchListener(zoomListener)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume(this)

        if (!binding.textureView.isAvailable)
            binding.textureView.surfaceTextureListener = textureListener
        else {
            viewModel.openCamera(this)
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.onPause(this)
    }

    override fun closeCamera(id: Int) {
        myCameras[id].closeCamera()
    }

    override fun openCamera(id: Int) {
        myCameras[id].openCamera(handler!!)
        //todo need?
        cameraId = myCameras[id].cameraId()
    }

    override fun makePhoto() {
        val bitmap = binding.imageView.drawable.toBitmap()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis())
        }
        val contentResolver = requireContext().contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            values.put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "Pictures/" + getString(R.string.app_name)
            )
            values.put(MediaStore.Images.Media.IS_PENDING, true)
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                val outputStream = contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    outputStream.close()
                }
                values.put(MediaStore.Images.Media.IS_PENDING, false)
                contentResolver.update(uri, values, null, null)
                Toast.makeText(requireContext(), "Saved", Toast.LENGTH_LONG).show()
            }
        } else {
            val imageFileFolder = File(
                Environment.getExternalStorageDirectory().toString() + '/' + getString(
                    R.string.app_name
                )
            )
            if (!imageFileFolder.exists()) {
                imageFileFolder.mkdir()
            }
            val imageName = "${System.currentTimeMillis()}.jpg"
            val imageFile = File(imageFileFolder, imageName)
            val outputStream = FileOutputStream(imageFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream.close()
            values.put(MediaStore.Images.Media.DATA, imageFile.absolutePath)
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            Toast.makeText(requireContext(), "Saved", Toast.LENGTH_LONG).show()
        }
    }

    override fun startBackgroundThread() {
        backgroundThread = HandlerThread("camera-background")
        backgroundThread!!.start()
        handler = Handler(backgroundThread!!.looper)
    }

    override fun stopBackgroundThread() {
        backgroundThread!!.quitSafely()
        try {
            backgroundThread!!.join()
            backgroundThread = null
            handler = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun createCameraPreviewSession() {
        if (myCameras[0].isOpen())
            myCameras[0].createCameraPreviewSession(
                binding.textureView, handler!!, ::captureRequestBuilder, ::cameraCaptureSession
            )
        else if (myCameras[1].isOpen())
            myCameras[1].createCameraPreviewSession(
                binding.textureView, handler!!, ::captureRequestBuilder, ::cameraCaptureSession
            )
    }
}

interface ManageCamera {
    fun closeCamera(id: Int)
    fun openCamera(id: Int)
    fun makePhoto()
    fun startBackgroundThread()
    fun stopBackgroundThread()
    fun createCameraPreviewSession()
}