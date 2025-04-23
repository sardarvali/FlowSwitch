package com.example.gail

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class permission_activity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_permission)


        val sharedPreferences: SharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        val permissionsGranted = sharedPreferences.getBoolean("PermissionsGranted", false)

        if (permissionsGranted && permisionsGrantedtoall()) {
            proceedToLogin()
        } else {
            checkandaskpermission()
        }
    }

    private fun permisionsGrantedtoall(): Boolean {
        val permissions = askingpermissions()
        return permissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun askingpermissions(): List<String> {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        permissions.add(Manifest.permission.RECORD_AUDIO)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        return permissions
    }

    private fun checkandaskpermission() {
        val permissions = askingpermissions()

        val notGrantedPermissions = permissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGrantedPermissions.isEmpty()) {
            savePermissionStatus()
            proceedToLogin()
        } else {
            requestPermissionLauncher.launch(notGrantedPermissions.toTypedArray())
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val allGranted = results.all { it.value }

            if (allGranted) {
                savePermissionStatus()
                proceedToLogin()
            } else {
                val deniedPermissions = results.filter { !it.value }.keys

                if (shouldShowRequestPermissionRationale(deniedPermissions)) {
                    Toast.makeText(this, "These permissions are necessary for the app to function properly.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Please enable permissions in app settings to continue.", Toast.LENGTH_LONG).show()
                }
            }
        }

    private fun shouldShowRequestPermissionRationale(permissions: Set<String>): Boolean {
        return permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(this, it) }
    }

    private fun savePermissionStatus() {
        val sharedPreferences: SharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putBoolean("PermissionsGranted", true)
            apply()
        }
    }

    private fun proceedToLogin() {
        val intent = Intent(this, Login_page::class.java)
        startActivity(intent)
        finish()
    }
}