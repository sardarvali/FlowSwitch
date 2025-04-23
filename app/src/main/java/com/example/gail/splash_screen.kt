package com.example.gail

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class splash_screen : AppCompatActivity() {

    private lateinit var brandname: TextView
    private lateinit var fireimg: ImageView
    private lateinit var mAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash_screen)

        mAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)

        brandname = findViewById(R.id.brandname)
        fireimg = findViewById(R.id.fireimg)

        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up)

        brandname.startAnimation(fadeIn)

        fadeIn.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}

            override fun onAnimationEnd(animation: Animation) {
                fireimg.visibility = View.VISIBLE
                fireimg.startAnimation(slideUp)
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })

        slideUp.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}

            override fun onAnimationEnd(animation: Animation) {
                Handler(Looper.getMainLooper()).postDelayed({
                    permissioncheck()
                }, 700)
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
    }

    private fun permissioncheck() {
        val permissionsGranted = sharedPreferences.getBoolean("PermissionsGranted", false)

        if (!permissionsGranted) {
            startActivity(Intent(this, permission_activity::class.java))
            finish()
            return
        }

        val currentUser = mAuth.currentUser

        if (currentUser != null) {
            val userEmail = currentUser.email

            if (userEmail != null) {
                firestore.collection("users").whereEqualTo("email", userEmail).get()
                    .addOnSuccessListener { documents ->
                        if (!documents.isEmpty) {
                            val document = documents.documents[0]
                            val role = document.getString("role") ?: ""

                            when (role) {
                                "agent" -> startActivity(Intent(this@splash_screen, Agent_dashboard::class.java))
                                "employee" -> startActivity(Intent(this@splash_screen, Mainpage::class.java))
                                "company" -> startActivity(Intent(this@splash_screen,
                                    company_dashboard::class.java))
                                else -> {
                                    mAuth.signOut()
                                    startActivity(Intent(this@splash_screen, Login_page::class.java))
                                }
                            }
                        } else {
                            mAuth.signOut()
                            startActivity(Intent(this@splash_screen, Login_page::class.java))
                        }
                        finish()
                    }
                    .addOnFailureListener {
                        startActivity(Intent(this@splash_screen, Login_page::class.java))
                        finish()
                    }
            } else {
                startActivity(Intent(this@splash_screen, Login_page::class.java))
                finish()
            }
        } else {
            startActivity(Intent(this@splash_screen, Login_page::class.java))
            finish()
        }
    }
}