package com.example.gail

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class Welcome : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_welcome)
        val txtview = findViewById<TextView>(R.id.textView)
        val firstName = intent.getStringExtra("FIRST_NAME")

        txtview.text = "Welcome $firstName"

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            val intent = Intent(this, Agent_dashboard::class.java)
            startActivity(intent)
            finish()
        }, 3000)


    }
}