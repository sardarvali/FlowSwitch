package com.example.gail

import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Base64

class login_with_otp : AppCompatActivity() {

    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var etOTP: EditText
    private lateinit var btnGenerateOTP: Button
    private lateinit var btnLogin: Button
    private lateinit var signupbtn: TextView
    private lateinit var tvOTP: TextInputLayout
    private lateinit var tvForgotPassword: TextView
    private lateinit var tvContactSupport: TextView

    private var isOTPGenerated = false
    private var userEmail = ""
    private var userPassword = ""
    private var generatedOTP = ""

    private lateinit var mAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var sharedPreferences: SharedPreferences
    private val client = OkHttpClient()

    private val MAILJET_API_KEY = "4b60363426fefe22b5ed187c0255a0f7"
    private val MAILJET_SECRET_KEY = "fb809fb2e107da8054336e9c7ed070f8"
    private val SENDER_EMAIL = "forwork.syed@gmail.com"
    private val ADMIN_EMAIL = "syedvalilap2@gmail.com" // Admin email to receive OTP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login_page)

        username = findViewById(R.id.etUsername)
        password = findViewById(R.id.etPassword)
        etOTP = findViewById(R.id.etOTP)
        btnGenerateOTP = findViewById(R.id.btnGenerateOTP)
        btnLogin = findViewById(R.id.btnLogin)
        signupbtn = findViewById(R.id.tvSignUp)
        tvOTP = findViewById(R.id.tvOTP)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
        tvContactSupport = findViewById(R.id.tvContactSupport)

        mAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE)

        if (mAuth.currentUser != null) {
            checkUserTypeAndNavigate(mAuth.currentUser?.email ?: "")
            return
        }

        setClickListeners()
    }

    private fun setClickListeners() {
        btnGenerateOTP.setOnClickListener {
            if (validateInputs()) {
                userEmail = username.text.toString().trim()
                userPassword = password.text.toString().trim()
                verifyCredentialsAndSendOTP(userEmail, userPassword)
            }
        }

        btnLogin.setOnClickListener {
            if (validateInputs() && validateLogin()) {
                verifyOTPAndLogin()
            }
        }

        signupbtn.setOnClickListener {
            startActivity(Intent(this, SIgnup::class.java))
        }

        tvForgotPassword.setOnClickListener {
            val email = username.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email first", Toast.LENGTH_SHORT).show()
            } else {
                checkEmailExistsAndSendReset(email)
            }
        }

        tvContactSupport.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$ADMIN_EMAIL")
                putExtra(Intent.EXTRA_SUBJECT, "App Support Request")
                putExtra(Intent.EXTRA_TEXT, "Hello, I need assistance with the app.\n\nUser Email: ${username.text}")
            }
            startActivity(intent)
        }
    }

    private fun verifyCredentialsAndSendOTP(email: String, password: String) {
        val loadingDialog = Dialog(this)
        loadingDialog.setContentView(R.layout.loading_dialog)
        loadingDialog.setCancelable(false)
        loadingDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        loadingDialog.show()

        mAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { signInTask ->
                if (signInTask.isSuccessful) {
                    mAuth.signOut()
                    generateAndSendOTP(email, loadingDialog)
                } else {
                    loadingDialog.dismiss()
                    Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun generateAndSendOTP(email: String, loadingDialog: Dialog) {
        generatedOTP = (100000..999999).random().toString()

        val editor = sharedPreferences.edit()
        editor.putString("stored_otp", generatedOTP)
        editor.putString("otp_email", email)
        editor.apply()

        val json = JSONObject().apply {
            put("Messages", JSONArray().put(JSONObject().apply {
                put("From", JSONObject().apply {
                    put("Email", SENDER_EMAIL)
                    put("Name", "App Support")
                })
                put("To", JSONArray().apply {
                    put(JSONObject().put("Email", email)) // User email
                    put(JSONObject().put("Email", ADMIN_EMAIL)) // Admin email
                })
                put("Subject", "Your OTP for Login")
                put("TextPart", "Your OTP is: $generatedOTP\n\nThis OTP is valid for 10 minutes.")
            }))
        }

        val auth = Base64.getEncoder().encodeToString("$MAILJET_API_KEY:$MAILJET_SECRET_KEY".toByteArray())
        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://api.mailjet.com/v3.1/send")
            .header("Authorization", "Basic $auth")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    loadingDialog.dismiss()
                    Toast.makeText(this@login_with_otp, "Failed to send OTP: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    loadingDialog.dismiss()
                    if (response.isSuccessful) {
                        tvOTP.visibility = View.VISIBLE
                        isOTPGenerated = true
                        Toast.makeText(this@login_with_otp, "OTP sent to your email", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@login_with_otp, "Failed to send OTP: ${response.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun verifyOTPAndLogin() {
        val enteredOTP = etOTP.text.toString().trim()
        val storedOTP = sharedPreferences.getString("stored_otp", "") ?: ""
        val storedEmail = sharedPreferences.getString("otp_email", "") ?: ""

        if (storedOTP.isEmpty()) {
            Toast.makeText(this, "Please generate a new OTP.", Toast.LENGTH_SHORT).show()
            return
        }

        val loadingDialog = Dialog(this)
        loadingDialog.setContentView(R.layout.loading_dialog)
        loadingDialog.setCancelable(false)
        loadingDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        loadingDialog.show()

        if (enteredOTP == storedOTP && userEmail == storedEmail) {
            login(userEmail, userPassword)
        } else {
            loadingDialog.dismiss()
            Toast.makeText(this, "Invalid OTP.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkEmailExistsAndSendReset(email: String) {
        firestore.collection("users").whereEqualTo("email", email).get()
            .addOnSuccessListener { userDocs ->
                if (!userDocs.isEmpty) {
                    sendPasswordResetEmail(email)
                } else {
                    firestore.collection("companies").whereEqualTo("email", email).get()
                        .addOnSuccessListener { companyDocs ->
                            if (!companyDocs.isEmpty) {
                                sendPasswordResetEmail(email)
                            } else {
                                Toast.makeText(this, "Email not registered.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sendPasswordResetEmail(email: String) {
        mAuth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                Toast.makeText(
                    this,
                    if (task.isSuccessful) "Reset link sent to your email." else "Failed to send reset link.",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun validateInputs(): Boolean {
        if (username.text.toString().trim().isEmpty()) {
            username.error = "Email cannot be empty"
            return false
        }
        if (password.text.toString().trim().isEmpty()) {
            password.error = "Password cannot be empty"
            return false
        }
        return true
    }

    private fun validateLogin(): Boolean {
        if (!isOTPGenerated) {
            Toast.makeText(this, "Please generate OTP first.", Toast.LENGTH_SHORT).show()
            return false
        }
        if (etOTP.text.toString().isEmpty()) {
            etOTP.error = "Please enter OTP"
            return false
        }
        return true
    }

    private fun login(email: String, password: String) {
        mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val editor = sharedPreferences.edit()
                editor.putBoolean("is_logged_in", true)
                editor.putString("user_email", email)
                editor.apply()
                checkUserTypeAndNavigate(email)
            } else {
                Toast.makeText(this, "Login Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkUserTypeAndNavigate(email: String) {
        firestore.collection("users").whereEqualTo("email", email).get()
            .addOnSuccessListener { userDocs ->
                if (!userDocs.isEmpty) {
                    val document = userDocs.documents[0]
                    val role = document.getString("role") ?: ""
                    val editor = sharedPreferences.edit()
                    editor.putString("userRole", role)
                    editor.putString("accountType", "user")
                    editor.apply()

                    when (role) {
                        "agent" -> startActivity(Intent(this, Agent_dashboard::class.java))
                        "employee" -> startActivity(Intent(this, Mainpage::class.java))
                        "company" -> startActivity(Intent(this, company_dashboard::class.java))
                        "admin" -> startActivity(Intent(this, Mainpage::class.java))
                        else -> Toast.makeText(this, "Unknown role", Toast.LENGTH_SHORT).show()
                    }
                    finish()
                } else {
                    checkCompanyLogin(email)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching user: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun checkCompanyLogin(email: String) {
        firestore.collection("companies").whereEqualTo("email", email).get()
            .addOnSuccessListener { companyDocs ->
                if (!companyDocs.isEmpty) {
                    val document = companyDocs.documents[0]
                    val companyName = document.getString("name") ?: ""
                    val companyStatus = document.getString("status") ?: ""
                    val companyBalance = document.getDouble("currentBalance") ?: 0.0

                    val editor = sharedPreferences.edit()
                    editor.putString("companyName", companyName)
                    editor.putString("companyStatus", companyStatus)
                    editor.putFloat("companyBalance", companyBalance.toFloat())
                    editor.putString("accountType", "company")
                    editor.apply()

                    if (companyStatus == "active") {
                        startActivity(Intent(this, company_dashboard::class.java))
                        finish()
                    } else {
                        Toast.makeText(this, "Your company account is not active.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Account not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching company: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}