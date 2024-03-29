package com.maxim.shacamera.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.maxim.shacamera.R
import com.maxim.shacamera.core.App
import com.maxim.shacamera.core.sl.ProvideViewModel
import com.maxim.shacamera.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), ProvideViewModel, CheckPermission {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = viewModel(MainViewModel::class.java)

        viewModel.observe(this) {
            it.show(supportFragmentManager, R.id.container)
        }

        if (checkPermissions())
            viewModel.init(savedInstanceState == null)
    }

    override fun checkPermissions(): Boolean {
        val permissionList = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2)
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        if (ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionList.add(Manifest.permission.RECORD_AUDIO)
        }

        if (permissionList.isNotEmpty()) {
            requestPermissions(
                permissionList.toTypedArray(), 1
            )
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1) {
            grantResults.forEach {
                if (it != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Please grant all permissions", Toast.LENGTH_LONG).show()
                    finish()
                }
                viewModel.init(true)
            }
        }
    }

    override fun <T : ViewModel> viewModel(clasz: Class<T>): T {
        return (application as App).viewModel(clasz)
    }
}

interface CheckPermission {
    fun checkPermissions(): Boolean
}