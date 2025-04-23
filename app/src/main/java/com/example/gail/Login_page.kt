package com.example.gail

import android.app.Dialog
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
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

class Login_page : AppCompatActivity() {

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
    private var generatedOTP = "" // Store the generated OTP

    private lateinit var mAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var sharedPreferences: SharedPreferences

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

                // First verify if the user exists with these credentials
                verifyCredentialsAndGenerateOTP(userEmail, userPassword)
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
                Toast.makeText(this, "Please enter your email address first", Toast.LENGTH_SHORT).show()
            } else {
                // Check in both collections
                checkEmailExistsAndSendReset(email)
            }
        }

        tvContactSupport.setOnClickListener {
            showSupportDialog()
        }
    }

    private fun verifyCredentialsAndGenerateOTP(email: String, password: String) {
        // Show progress dialog
        val loadingDialog = Dialog(this)
        loadingDialog.setContentView(R.layout.loading_dialog)
        loadingDialog.setCancelable(false)
        loadingDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        loadingDialog.show()

        // First check if the credentials are valid
        mAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { signInTask ->
                loadingDialog.dismiss()
                if (signInTask.isSuccessful) {
                    // Sign out immediately as we are just verifying credentials
                    mAuth.signOut()

                    // Credentials are valid, generate and display OTP
                    generateAndShowOTP(email)
                } else {
                    Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun generateAndShowOTP(email: String) {
        // Generate a random 6-digit OTP
        generatedOTP = (100000..999999).random().toString()

        // Save OTP in shared preferences
        val editor = sharedPreferences.edit()
        editor.putString("stored_otp", generatedOTP)
        editor.putString("otp_email", email)
        editor.apply()

        // Show OTP dialog directly to the user
        showOTPDialog(generatedOTP)
    }

    private fun showOTPDialog(otp: String) {
        val dialog = Dialog(this, R.style.RoundedDialogStyle)
        dialog.setContentView(R.layout.otp_fallback_dialog)
        dialog.setCancelable(false)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }

        val tvOTPDisplay = dialog.findViewById<TextView>(R.id.tvOTPDisplay)
        val btnCopy = dialog.findViewById<Button>(R.id.btnCopyOTP)
        val btnClose = dialog.findViewById<Button>(R.id.btnCloseOTPDialog)

        tvOTPDisplay.text = otp

        btnCopy.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("OTP", otp)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "OTP copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
            tvOTP.visibility = View.VISIBLE
            isOTPGenerated = true
        }

        dialog.show()
    }

    private fun verifyOTPAndLogin() {
        val enteredOTP = etOTP.text.toString().trim()
        val storedOTP = sharedPreferences.getString("stored_otp", "") ?: ""
        val storedEmail = sharedPreferences.getString("otp_email", "") ?: ""

        if (storedOTP.isEmpty()) {
            Toast.makeText(this, "OTP not found. Please generate a new OTP.", Toast.LENGTH_SHORT).show()
            return
        }

        // Show progress dialog
        val loadingDialog = Dialog(this)
        loadingDialog.setContentView(R.layout.loading_dialog)
        loadingDialog.setCancelable(false)
        loadingDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        loadingDialog.show()

        // Directly verify OTP
        if (enteredOTP == storedOTP && userEmail == storedEmail) {
            // OTP is valid, proceed with login
            login(userEmail, userPassword)
        } else {
            loadingDialog.dismiss()
            Toast.makeText(this, "Invalid OTP. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkEmailExistsAndSendReset(email: String) {
        // First check in users collection
        firestore.collection("users").whereEqualTo("email", email).get()
            .addOnSuccessListener { userDocuments ->
                if (!userDocuments.isEmpty) {
                    sendPasswordResetEmail(email)
                } else {
                    // If not found in users, check in companies
                    firestore.collection("companies").whereEqualTo("email", email).get()
                        .addOnSuccessListener { companyDocuments ->
                            if (!companyDocuments.isEmpty) {
                                sendPasswordResetEmail(email)
                            } else {
                                Toast.makeText(this, "This email is not registered with us", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Error checking email: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error checking email: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sendPasswordResetEmail(email: String) {
        mAuth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Reset link sent to your email", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Failed to send reset link: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
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
            intent.data = Uri.parse("mailto:syedvalilap2@gmail.com")
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
            Toast.makeText(this, "Please generate OTP first", Toast.LENGTH_SHORT).show()
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
                val sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
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
        // First check if it's a user
        firestore.collection("users").whereEqualTo("email", email).get()
            .addOnSuccessListener { userDocuments ->
                if (!userDocuments.isEmpty) {
                    // It's a user
                    val document = userDocuments.documents[0]
                    val role = document.getString("role") ?: ""

                    val editor = sharedPreferences.edit()
                    editor.putString("userRole", role)
                    editor.putString("accountType", "user")
                    editor.apply()

                    when (role) {
                        "agent" -> startActivity(Intent(this@Login_page, Agent_dashboard::class.java))
                        "employee" -> startActivity(Intent(this@Login_page, Mainpage::class.java))
                        "company" -> startActivity(Intent(this@Login_page, company_dashboard::class.java))
                        "admin" -> startActivity(Intent(this@Login_page, Mainpage::class.java))
                        else -> {
                            Toast.makeText(this@Login_page, "Unknown role", Toast.LENGTH_SHORT).show()
                        }
                    }
                    finish()
                } else {
                    // Not found in users, check in companies
                    checkCompanyLogin(email)
                }
            }.addOnFailureListener { e ->
                Toast.makeText(this@Login_page, "Error fetching user: ${e.message}", Toast.LENGTH_SHORT).show()
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

                    val editor = sharedPreferences.edit()
                    editor.putString("companyName", companyName)
                    editor.putString("companyStatus", companyStatus)
                    editor.putFloat("companyBalance", companyBalance.toFloat())
                    editor.putString("accountType", "company")
                    editor.apply()

                    if (companyStatus == "active") {
                        startActivity(Intent(this@Login_page, company_dashboard::class.java))
                        finish()
                    } else {
                        Toast.makeText(this@Login_page, "Your company account is not active. Please contact support.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@Login_page, "Account not found", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener { e ->
                Toast.makeText(this@Login_page, "Error fetching company: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}