package com.example.gail

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
import java.util.regex.Pattern

class SIgnup : AppCompatActivity() {
    private lateinit var etFirstName: EditText
    private lateinit var etMiddleName: EditText
    private lateinit var etLastName: EditText
    private lateinit var etAadhaar: EditText
    private lateinit var btnGenerateAadhaarOTP: Button
    private lateinit var spinnerCategory: AutoCompleteTextView
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnSignUp: Button
    private lateinit var tilAadhaarOTP: TextInputLayout
    private lateinit var etAadhaarOTP: TextInputEditText

    private var generatedAadhaarOTP: String = ""
    private var isAadhaarOTPGenerated = false

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        etFirstName = findViewById(R.id.etFirstName)
        etMiddleName = findViewById(R.id.etMiddleName)
        etLastName = findViewById(R.id.etLastName)
        etAadhaar = findViewById(R.id.etAadhaar)
        etAadhaarOTP = findViewById(R.id.etAadhaarOTP)
        btnGenerateAadhaarOTP = findViewById(R.id.btnGenerateAadhaarOTP)
        spinnerCategory = findViewById(R.id.spinnerCategory)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnSignUp = findViewById(R.id.btnSignUp)
        tilAadhaarOTP = findViewById(R.id.tilAadhaarOTP)

        setupSpinner()
        setClickListeners()
    }

    private fun setClickListeners() {
        btnGenerateAadhaarOTP.setOnClickListener {
            if (validateAadhaar()) {
                checkAadhaarExists()
            }
        }

        btnSignUp.setOnClickListener {
            if (validateSignUp()) {
                signUp()
            }
        }
    }

    private fun setupSpinner() {
        val items = resources.getStringArray(R.array.dropview)
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, items)
        (spinnerCategory as AutoCompleteTextView).setAdapter(adapter)
    }

    private fun validateAadhaar(): Boolean {
        val aadhaar = etAadhaar.text.toString().trim()
        if (aadhaar.isEmpty() || aadhaar.length != 12) {
            etAadhaar.error = "Please enter a valid 12-digit Aadhaar number"
            return false
        }
        return true
    }

    private fun checkAadhaarExists() {
        val aadhaar = etAadhaar.text.toString().trim()

        firestore.collection("users")
            .whereEqualTo("aadhaar", aadhaar)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    generateAadhaarOTP()
                } else {
                    etAadhaar.error = "This Aadhaar number is already registered"
                    Toast.makeText(this, "This Aadhaar number is already registered", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error checking Aadhaar: ${e.message}")
                Toast.makeText(this, "Error checking Aadhaar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun generateAadhaarOTP() {
        generatedAadhaarOTP = (100000..999999).random().toString()
        isAadhaarOTPGenerated = true

        tilAadhaarOTP.visibility = View.VISIBLE

        Toast.makeText(this, "Your Aadhaar OTP is: $generatedAadhaarOTP", Toast.LENGTH_LONG).show()
    }

    private fun validateSignUp(): Boolean {
        if (etFirstName.text.toString().trim().isEmpty()) {
            etFirstName.error = "Please enter your first name"
            return false
        }

        if (etLastName.text.toString().trim().isEmpty()) {
            etLastName.error = "Please enter your last name"
            return false
        }

        if (!isAadhaarOTPGenerated || etAadhaarOTP.text.toString() != generatedAadhaarOTP) {
            Toast.makeText(this, "Invalid or missing Aadhaar OTP", Toast.LENGTH_SHORT).show()
            return false
        }

        if (etEmail.text.toString().trim().isEmpty()) {
            etEmail.error = "Please enter your email"
            return false
        }

        val password = etPassword.text.toString().trim()
        if (password.isEmpty()) {
            etPassword.error = "Please enter a password"
            return false
        }

        if (password.length < 8) {
            etPassword.error = "Password must be at least 8 characters long"
            return false
        }

        val passwordPattern = Pattern.compile("^(?=.*[0-9])(?=.*[a-zA-Z]).{8,}$")
        if (!passwordPattern.matcher(password).matches()) {
            etPassword.error = "Password must contain both letters and numbers"
            return false
        }

        if (etPassword.text.toString() != etConfirmPassword.text.toString()) {
            etConfirmPassword.error = "Passwords do not match"
            return false
        }

        val selectedCategory = spinnerCategory.text.toString()
        if (selectedCategory.isEmpty()) {
            Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun signUp() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    auth.currentUser?.let { user ->
                        Log.d("SignUp", "User authenticated: ${user.uid}")
                        saveUserData(user.uid, email)
                    }
                } else {
                    val exception = task.exception
                    if (exception is FirebaseAuthUserCollisionException) {
                        Toast.makeText(this, "This email is already registered", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Error: ${exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    private fun saveUserData(userId: String, email: String) {
        val userData = hashMapOf(
            "firstName" to etFirstName.text.toString().trim(),
            "middleName" to etMiddleName.text.toString().trim(),
            "lastName" to etLastName.text.toString().trim(),
            "aadhaar" to etAadhaar.text.toString().trim(),
            "category" to spinnerCategory.text.toString(),
            "email" to email,
            "role" to "agent",
            "registrationDate" to com.google.firebase.Timestamp.now(),
            "registrationCompleted" to false
        )

        firestore.collection("users").document(userId).set(userData)
            .addOnSuccessListener {
                Log.d("Firestore", "User data saved successfully")
                Toast.makeText(this, "Basic registration successful!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, Agent_details::class.java)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error saving user data: ${e.message}")
                Toast.makeText(this, "Error saving user data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setTitle("Exit Registration")
            .setMessage("Are you sure you want to go back? Your registration progress will be lost.")
            .setPositiveButton("Yes") { _, _ ->
                super.onBackPressed()
            }
            .setNegativeButton("No", null)
            .show()
    }
}