package com.deltavoice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Activity to handle permissions for microphone and camera.
 * When all permissions are granted, shows "All set" and a Done button.
 */
class PermissionsActivity : AppCompatActivity() {

    private lateinit var titleText: TextView
    private lateinit var statusText: TextView
    private lateinit var requestButton: Button

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        titleText = findViewById(R.id.title_text)
        statusText = findViewById(R.id.status_text)
        requestButton = findViewById(R.id.request_button)

        findViewById<android.widget.ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        updatePermissionStatus()

        requestButton.setOnClickListener {
            if (allPermissionsGranted()) {
                finish()
            } else {
                requestPermissions()
            }
        }
    }

    private fun allPermissionsGranted(): Boolean {
        val hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        return hasMic && hasCamera
    }

    /**
     * Update the UI to show current permission status.
     * When all granted: title "All set", button "Done". Otherwise: "Permissions Required", "Request Permissions".
     */
    private fun updatePermissionStatus() {
        val hasMic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasCamera = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val status = buildString {
            append(if (hasMic) "Microphone: ✓ Granted" else "Microphone: ✗ Denied")
            append("\n")
            append(if (hasCamera) "Camera: ✓ Granted" else "Camera: ✗ Denied")
        }
        statusText.text = status

        if (allPermissionsGranted()) {
            titleText.text = "All set"
            requestButton.text = "Done"
        } else {
            titleText.text = "Permissions required"
            requestButton.text = "Request permissions"
        }
    }

    /**
     * Request microphone and camera permissions
     */
    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )

        ActivityCompat.requestPermissions(
            this,
            permissions,
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            updatePermissionStatus()
        }
    }
}

