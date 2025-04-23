package com.example.gail

import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class login_api_otp : AppCompatActivity() {

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

    private lateinit var mAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var sharedPreferences: SharedPreferences
    private val userPrefs by lazy { getSharedPreferences("user_prefs", MODE_PRIVATE) }
    private val apiService = RetrofitClient.apiService

    private val ADMIN_EMAIL = "syedvalilap2@gmail.com"

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
            showSupportDialog()
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
                    // Sign out immediately as we are just verifying credentials
                    mAuth.signOut()
                    generateAndSendOTP(email, loadingDialog)
                } else {
                    loadingDialog.dismiss()
                    Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun generateAndSendOTP(email: String, loadingDialog: Dialog) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Create request body as expected by the server
                val requestBody = mapOf("email" to email)
                val response = apiService.generateOTP(requestBody)

                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    tvOTP.visibility = View.VISIBLE
                    isOTPGenerated = true
                    Toast.makeText(this@login_api_otp, response, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    val errorMessage = when (e) {
                        is retrofit2.HttpException -> {
                            val errorBody = e.response()?.errorBody()?.string() ?: "Unknown error"
                            "Server Error (${e.code()}): $errorBody"
                        }
                        is java.net.SocketTimeoutException -> "Connection Timeout"
                        is java.io.IOException -> "Network Error: ${e.message}"
                        else -> "Unknown Error: ${e.message}"
                    }
                    Toast.makeText(this@login_api_otp, "Failed to send OTP: $errorMessage", Toast.LENGTH_LONG).show()
                    // Log the error for debugging
                    Log.e("OTP_ERROR", errorMessage, e)
                }
            }
        }
    }

    private fun verifyOTPAndLogin() {
        val enteredOTP = etOTP.text.toString().trim()
        val loadingDialog = Dialog(this)
        loadingDialog.setContentView(R.layout.loading_dialog)
        loadingDialog.setCancelable(false)
        loadingDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        loadingDialog.show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Create request body for OTP verification
                val requestBody = mapOf(
                    "email" to userEmail,
                    "otp" to enteredOTP
                )

                // Call the API to verify OTP
                val response = apiService.verifyOTP(requestBody)

                // Log the response for debugging
                Log.d("OTP_VERIFICATION", "Server response: $response")

                withContext(Dispatchers.Main) {
                    // The response format might not contain "success" directly
                    // It could be a status code or different message format
                    // Check both common response patterns
                    if (response.contains("success", ignoreCase = true) ||
                        response.contains("verified", ignoreCase = true) ||
                        response.contains("200", ignoreCase = true)) {

                        Toast.makeText(this@login_api_otp, "OTP verified successfully", Toast.LENGTH_SHORT).show()
                        // OTP verification successful, proceed with login
                        login(userEmail, userPassword)
                    } else {
                        // OTP verification failed
                        Toast.makeText(this@login_api_otp, "Invalid OTP. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                    loadingDialog.dismiss()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingDialog.dismiss()
                    val errorMessage = when (e) {
                        is retrofit2.HttpException -> {
                            // If we get a 200 OK response but with error message in body
                            if (e.code() == 200) {
                                try {
                                    e.response()?.body().toString() ?: "Unknown server response"
                                } catch (ex: Exception) {
                                    "Server returned OK but with invalid response"
                                }
                            } else {
                                val errorBody = e.response()?.errorBody()?.string() ?: "Unknown error"
                                "Server Error (${e.code()}): $errorBody"
                            }
                        }
                        is java.net.SocketTimeoutException -> "Connection Timeout"
                        is java.io.IOException -> "Network Error: ${e.message}"
                        else -> "Unknown Error: ${e.message}"
                    }
                    Toast.makeText(this@login_api_otp, "OTP verification failed: $errorMessage", Toast.LENGTH_LONG).show()
                    Log.e("OTP_VERIFICATION_ERROR", errorMessage, e)
                }
            }
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

    private fun showSupportDialog() {
        val dialog = Dialog(this, R.style.RoundedDialogStyle)
        dialog.setContentView(R.layout.support_layout)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }

        val btnCall = dialog.findViewById<MaterialButton>(R.id.btnCallSupport)
        val btnEmail = dialog.findViewById<MaterialButton>(R.id.btnEmailSupport)
        val btnWhatsapp = dialog.findViewById<MaterialButton>(R.id.btnWhatsappSupport)
        val btnClose = dialog.findViewById<MaterialButton>(R.id.btnCloseDialog)

        btnCall.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse("tel:9999999999")
            startActivity(intent)
            dialog.dismiss()
        }

        btnEmail.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO)
            intent.data = Uri.parse("mailto:$ADMIN_EMAIL")
            intent.putExtra(Intent.EXTRA_SUBJECT, "App Support Request")
            intent.putExtra(Intent.EXTRA_TEXT, "Hello, I need assistance with the app.\n\nUser Email: ${username.text}\n\nDetails of the issue:")
            startActivity(intent)
            dialog.dismiss()
        }

        btnWhatsapp.setOnClickListener {
            try {
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=+919052579129&text=Hello, I need help with the app.")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "WhatsApp not installed on your device", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
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
                // Save login status
                val editor = userPrefs.edit()
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
        // First check if it's a user
        firestore.collection("users").whereEqualTo("email", email).get()
            .addOnSuccessListener { userDocuments ->
                if (!userDocuments.isEmpty) {
                    // It's a user
                    val document = userDocuments.documents[0]
                    val role = document.getString("role") ?: ""

                    val editor = userPrefs.edit()
                    editor.putString("userRole", role)
                    editor.putString("accountType", "user")
                    editor.apply()

                    when (role) {
                        "agent" -> startActivity(Intent(this@login_api_otp, Agent_dashboard::class.java))
                        "employee" -> startActivity(Intent(this@login_api_otp, Mainpage::class.java))
                        "company" -> startActivity(Intent(this@login_api_otp, company_dashboard::class.java))
                        "admin" -> startActivity(Intent(this@login_api_otp, Mainpage::class.java))
                        else -> {
                            Toast.makeText(this@login_api_otp, "Unknown role", Toast.LENGTH_SHORT).show()
                        }
                    }
                    finish()
                } else {
                    // Not found in users, check in companies
                    checkCompanyLogin(email)
                }
            }.addOnFailureListener { e ->
                Toast.makeText(this@login_api_otp, "Error fetching user: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun checkCompanyLogin(email: String) {
        firestore.collection("companies").whereEqualTo("email", email).get()
            .addOnSuccessListener { companyDocuments ->
                if (!companyDocuments.isEmpty) {
                    // It's a company
                    val document = companyDocuments.documents[0]
                    val companyName = document.getString("name") ?: ""
                    val companyStatus = document.getString("status") ?: ""
                    val companyBalance = document.getDouble("currentBalance") ?: 0.0

                    val editor = userPrefs.edit()
                    editor.putString("companyName", companyName)
                    editor.putString("companyStatus", companyStatus)
                    editor.putFloat("companyBalance", companyBalance.toFloat())
                    editor.putString("accountType", "company")
                    editor.apply()

                    if (companyStatus == "active") {
                        startActivity(Intent(this@login_api_otp, company_dashboard::class.java))
                        finish()
                    } else {
                        Toast.makeText(this@login_api_otp, "Your company account is not active. Please contact support.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@login_api_otp, "Account not found", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener { e ->
                Toast.makeText(this@login_api_otp, "Error fetching company: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}