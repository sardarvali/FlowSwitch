package com.example.gail

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class Agent_details : AppCompatActivity() {
    private lateinit var etCompanyName: TextInputEditText
    private lateinit var etBusinessPan: TextInputEditText
    private lateinit var etCompanyAddress: TextInputEditText
    private lateinit var etPhoneNumber: TextInputEditText
    private lateinit var etGstin: TextInputEditText
    private lateinit var checkBox10kg: CheckBox
    private lateinit var checkBox14kg: CheckBox
    private lateinit var btnSubmit: MaterialButton
    private lateinit var progressBar: ProgressBar

    // TextInputLayouts for error handling
    private lateinit var tilCompanyName: TextInputLayout
    private lateinit var tilBusinessPan: TextInputLayout
    private lateinit var tilCompanyAddress: TextInputLayout
    private lateinit var tilPhoneNumber: TextInputLayout
    private lateinit var tilGstin: TextInputLayout

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agent_details)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Initialize UI elements
        initializeViews()

        // Set up click listener
        btnSubmit.setOnClickListener {
            if (validateInputs()) {
                showLoadingState(true)
                saveAgentDetails()
            }
        }

        // Check if user is authenticated
        if (auth.currentUser == null) {
            showErrorAndRedirect("User not authenticated. Please sign in first.")
            return
        }
    }

    private fun initializeViews() {
        // EditText fields
        etCompanyName = findViewById(R.id.etCompanyName)
        etBusinessPan = findViewById(R.id.etBusinessPan)
        etCompanyAddress = findViewById(R.id.etCompanyAddress)
        etPhoneNumber = findViewById(R.id.etPhoneNumber)
        etGstin = findViewById(R.id.etGstin)

        // TextInputLayout fields
        tilCompanyName = findViewById(R.id.tilCompanyName)
        tilBusinessPan = findViewById(R.id.tilBusinessPan)
        tilCompanyAddress = findViewById(R.id.tilCompanyAddress)
        tilPhoneNumber = findViewById(R.id.tilPhoneNumber)
        tilGstin = findViewById(R.id.tilGstin)

        // Checkboxes
        checkBox10kg = findViewById(R.id.checkBox10kg)
        checkBox14kg = findViewById(R.id.checkBox14kg)

        // Button and progress indicator
        btnSubmit = findViewById(R.id.btnSubmit)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        // Reset errors
        tilCompanyName.error = null
        tilBusinessPan.error = null
        tilCompanyAddress.error = null
        tilPhoneNumber.error = null
        tilGstin.error = null

        // Validate company name
        if (etCompanyName.text.toString().trim().isEmpty()) {
            tilCompanyName.error = "Company name cannot be empty"
            isValid = false
        }

        // Validate Business PAN
        val pan = etBusinessPan.text.toString().trim()
        if (pan.isEmpty()) {
            tilBusinessPan.error = "Business PAN cannot be empty"
            isValid = false
        } else if (pan.length != 10 || !isValidPAN(pan)) {
            tilBusinessPan.error = "Enter a valid 10-character PAN"
            isValid = false
        }

        // Validate company address
        if (etCompanyAddress.text.toString().trim().isEmpty()) {
            tilCompanyAddress.error = "Company address cannot be empty"
            isValid = false
        }

        // Validate phone number
        val phone = etPhoneNumber.text.toString().trim()
        if (phone.isEmpty()) {
            tilPhoneNumber.error = "Phone number cannot be empty"
            isValid = false
        } else if (phone.length != 10 || !phone.all { it.isDigit() }) {
            tilPhoneNumber.error = "Enter a valid 10-digit phone number"
            isValid = false
        }

        // Validate GSTIN
        val gstin = etGstin.text.toString().trim()
        if (gstin.isEmpty()) {
            tilGstin.error = "GSTIN cannot be empty"
            isValid = false
        } else if (gstin.length != 15 || !isValidGSTIN(gstin)) {
            tilGstin.error = "Enter a valid 15-character GSTIN"
            isValid = false
        }

        // Validate gas variants selection
        if (!checkBox10kg.isChecked && !checkBox14kg.isChecked) {
            Toast.makeText(this, "Please select at least one gas variant", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        return isValid
    }

    private fun isValidPAN(pan: String): Boolean {
        // Basic PAN validation - 10 characters, alphanumeric
        val regex = Regex("^[A-Z]{5}[0-9]{4}[A-Z]{1}$")
        return regex.matches(pan)
    }

    private fun isValidGSTIN(gstin: String): Boolean {
        // Basic GSTIN validation - 15 characters
        val regex = Regex("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$")
        return regex.matches(gstin)
    }

    private fun saveAgentDetails() {
        val currentUser = auth.currentUser ?: run {
            showLoadingState(false)
            showErrorAndRedirect("User not authenticated. Please sign in first.")
            return
        }

        // Collect selected gas variants
        val selectedGasVariants = mutableListOf<String>()
        if (checkBox10kg.isChecked) selectedGasVariants.add("10 kg")
        if (checkBox14kg.isChecked) selectedGasVariants.add("14.8 kg")

        // Create agent details data
        val agentDetails = hashMapOf(
            "companyName" to etCompanyName.text.toString().trim(),
            "businessPan" to etBusinessPan.text.toString().trim(),
            "companyAddress" to etCompanyAddress.text.toString().trim(),
            "phoneNumber" to etPhoneNumber.text.toString().trim(),
            "gstin" to etGstin.text.toString().trim(),
            "gasVariants" to selectedGasVariants,
            "verified" to false,  // Agent needs verification by admin
            "registrationDate" to com.google.firebase.Timestamp.now(),
            "lastUpdated" to com.google.firebase.Timestamp.now()
        )

        // First check if the business PAN already exists
        firestore.collection("users")
            .whereEqualTo("businessPan", etBusinessPan.text.toString().trim())
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // No duplicate PAN, proceed with save
                    updateUserDocument(currentUser.uid, agentDetails)
                } else {
                    // Check if the document belongs to the current user
                    val existingDoc = documents.documents[0]
                    if (existingDoc.id != currentUser.uid) {
                        showLoadingState(false)
                        Toast.makeText(this, "This Business PAN is already registered", Toast.LENGTH_LONG).show()
                    } else {
                        // User is updating their own record
                        updateUserDocument(currentUser.uid, agentDetails)
                    }
                }
            }
            .addOnFailureListener { e ->
                showLoadingState(false)
                Toast.makeText(this, "Error checking PAN: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateUserDocument(userId: String, agentDetails: HashMap<String, Any>) {
        // Start a batch to ensure consistency
        val batch = firestore.batch()

        // Update the user document
        val userRef = firestore.collection("users").document(userId)
        batch.update(userRef, agentDetails as Map<String, Any>)

        // Create an entry in the agents collection with the same ID
        val agentRef = firestore.collection("agents").document(userId)
        batch.set(agentRef, agentDetails)

        // Commit the batch
        batch.commit()
            .addOnSuccessListener {
                showLoadingState(false)
                Toast.makeText(this, "Agent details saved successfully", Toast.LENGTH_SHORT).show()
                navigateTodashboardScreen()
            }
            .addOnFailureListener { e ->
                showLoadingState(false)
                Toast.makeText(this, "Error saving agent details: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showLoadingState(isLoading: Boolean) {
        if (isLoading) {
            progressBar.visibility = View.VISIBLE
            btnSubmit.isEnabled = false
        } else {
            progressBar.visibility = View.GONE
            btnSubmit.isEnabled = true
        }
    }

    private fun showErrorAndRedirect(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Authentication Error")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ ->
                val intent = Intent(this, SIgnup::class.java)
                startActivity(intent)
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun navigateTodashboardScreen() {
        val intent = Intent(this, Agent_dashboard::class.java)
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        // Show confirmation dialog since data might be lost
        AlertDialog.Builder(this)
            .setTitle("Cancel Registration")
            .setMessage("Are you sure you want to cancel? All entered information will be lost.")
            .setPositiveButton("Yes") { _, _ ->
                super.onBackPressed()
            }
            .setNegativeButton("No", null)
            .show()
    }
}