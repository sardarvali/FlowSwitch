package com.example.gail

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class Login : AppCompatActivity() {

    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var btnLogIn: Button
    private lateinit var signupbtn: TextView
    private lateinit var mAuth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        mAuth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        username = findViewById(R.id.etUsername)
        password = findViewById(R.id.etPassword)
        btnLogIn = findViewById(R.id.btnLogIn)
        signupbtn = findViewById(R.id.tvagentlogin)

        btnLogIn.setOnClickListener {
            val email = username.text.toString().trim()
            val passwordText = password.text.toString().trim()

            if (email.isEmpty()) {
                username.error = "Email cannot be empty"
                return@setOnClickListener
            }
            if (passwordText.isEmpty()) {
                password.error = "Password cannot be empty"
                return@setOnClickListener
            }

            login(email, passwordText)
        }

        signupbtn.setOnClickListener {
            val intent = Intent(this, SIgnup::class.java)
            startActivity(intent)
        }
    }

    private fun login(email: String, password: String) {
        mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val userId = mAuth.currentUser?.uid ?: ""
                db.collection("users").document(userId).get()
                    .addOnSuccessListener { document ->
                        if (document != null) {
                            val role = document.getString("role") ?: ""
                            if (role == "agent") {
                                startActivity(Intent(this, Login_page::class.java))
                            } else {
                                startActivity(Intent(this, Mainpage::class.java))
                            }
                            finish()
                        } else {
                            Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error fetching user data: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "Login Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}