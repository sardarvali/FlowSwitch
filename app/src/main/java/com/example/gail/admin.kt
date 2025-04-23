package com.example.gail

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class admin : AppCompatActivity() {

    // Data classes for type safety
    data class Company(
        val name: String,
        val email: String,  // Changed from contact to email
        val address: String,
        val dailyExportQuota: Double,
        val currentBalance: Double,
        val status: String,
        val createdAt: Any,
        val createdBy: String
    ) {
        fun toMap(): Map<String, Any> = hashMapOf(
            "name" to name,
            "email" to email,  // Changed from contact to email
            "address" to address,
            "dailyExportQuota" to dailyExportQuota,
            "currentBalance" to currentBalance,
            "status" to status,
            "createdAt" to createdAt,
            "createdBy" to createdBy
        )
    }

    data class Transaction(
        val companyId: String,
        val companyName: String,
        val volume: Double,
        val timestamp: Any,
        val status: String,
        val type: String,
        val cost: Double,
        val processedBy: String
    ) {
        fun toMap(): Map<String, Any> = hashMapOf(
            "companyId" to companyId,
            "companyName" to companyName,
            "volume" to volume,
            "timestamp" to timestamp,
            "status" to status,
            "type" to type,
            "cost" to cost,
            "processedBy" to processedBy
        )
    }

    companion object {
        private const val TAG = "AdminActivity"
        private const val INITIAL_BALANCE = 0.0
        private const val DAILY_QUOTA = 10.0
    }

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var tvStatus: TextView
    private lateinit var etCompanyName: EditText
    private lateinit var etCompanyEmail: EditText
    private lateinit var etCompanyAddress: EditText
    private lateinit var etCompanyQuota: EditText
    private var isAdmin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        initializeFirebase()
        setupUI()
        verifyAdminStatusByEmail()
    }

    private fun initializeFirebase() {
        firestore = FirebaseFirestore.getInstance()
        auth = Firebase.auth
    }

    private fun setupUI() {
        tvStatus = findViewById(R.id.tv_status)
        etCompanyName = findViewById(R.id.et_company_name)
        etCompanyEmail = findViewById(R.id.et_company_email)
        etCompanyAddress = findViewById(R.id.et_company_address)
        etCompanyQuota = findViewById(R.id.et_company_quota)

        findViewById<Button>(R.id.btn_add_company).setOnClickListener {
            if (isAdmin) addNewCompany() else showAdminError()
        }
    }

    private fun verifyAdminStatusByEmail() {
        val user = auth.currentUser ?: run {
            showError("User not authenticated")
            finish()
            return
        }

        val email = user.email ?: run {
            showError("Email not available")
            finish()
            return
        }

        firestore.collection("users")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val document = documents.documents[0]
                    val role = document.getString("role") ?: ""
                    isAdmin = role == "admin"

                    if (isAdmin) {
                        // Show admin form if user is admin
                        findViewById<View>(R.id.admin_form_container).visibility = View.VISIBLE
                        tvStatus.text = "Status: Admin verified"
                    } else {
                        showError("Admin privileges required")
                        finish()
                    }
                } else {
                    showError("User profile not found")
                    finish()
                }
            }
            .addOnFailureListener { e ->
                showError("Error verifying admin status: ${e.message}")
                finish()
            }
    }

    private fun addNewCompany() {
        // Validate inputs
        val companyName = etCompanyName.text.toString().trim()
        val companyEmail = etCompanyEmail.text.toString().trim()
        val companyAddress = etCompanyAddress.text.toString().trim()
        val quotaText = etCompanyQuota.text.toString().trim()

        // Input validation
        if (companyName.isEmpty() || companyEmail.isEmpty() || companyAddress.isEmpty()) {
            showError("All fields are required")
            return
        }

        // Parse quota with fallback to default
        val quota = if (quotaText.isEmpty()) DAILY_QUOTA else try {
            quotaText.toDouble()
        } catch (e: NumberFormatException) {
            showError("Invalid quota format")
            return
        }

        // Create company object
        val company = Company(
            name = companyName,
            email = companyEmail,  // Changed from contact to email
            address = companyAddress,
            dailyExportQuota = quota,
            currentBalance = INITIAL_BALANCE,
            status = "active",
            createdAt = FieldValue.serverTimestamp(),
            createdBy = auth.currentUser?.uid ?: ""
        )

        // Add to database
        addCompanyWithTransaction(company)

        // Clear fields after successful addition
        clearInputFields()
    }

    private fun clearInputFields() {
        etCompanyName.text.clear()
        etCompanyEmail.text.clear()
        etCompanyAddress.text.clear()
        etCompanyQuota.text.clear()
        etCompanyName.requestFocus()
    }

    private fun createTransactionData(companyId: String, companyName: String, quota: Double): Transaction {
        return Transaction(
            companyId = companyId,
            companyName = companyName,
            volume = quota,
            timestamp = FieldValue.serverTimestamp(),
            status = "completed",
            type = "initial",
            cost = calculateTransactionCost(quota),
            processedBy = auth.currentUser?.uid ?: ""
        )
    }

    private fun addCompanyWithTransaction(company: Company) {
        tvStatus.text = "Adding company..."

        firestore.collection("companies")
            .add(company.toMap())
            .addOnSuccessListener { docRef ->
                logTransaction(docRef.id, company.name, company.dailyExportQuota)
                updateCompanyBalance(docRef.id, INITIAL_BALANCE)
                showStatus("Added ${company.name}")
            }
            .addOnFailureListener { e ->
                showError("Failed to add company: ${e.message}")
                Log.e(TAG, "Company creation failed", e)
            }
    }

    private fun logTransaction(companyId: String, companyName: String, quota: Double) {
        val transaction = createTransactionData(companyId, companyName, quota)
        firestore.collection("transactions")
            .add(transaction.toMap())
            .addOnFailureListener { e ->
                Log.e(TAG, "Transaction logging failed", e)
            }
    }

    private fun updateCompanyBalance(companyId: String, amount: Double) {
        firestore.collection("companies").document(companyId)
            .update("currentBalance", amount)
            .addOnFailureListener { e ->
                Log.e(TAG, "Balance update failed", e)
            }
    }

    private fun calculateTransactionCost(volume: Double): Double {
        return volume * 1.48e7 // 1.48 crore per MCM
    }

    private fun showStatus(message: String) {
        runOnUiThread {
            tvStatus.text = "Status: $message"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showError(message: String) {
        runOnUiThread {
            tvStatus.text = "Error: $message"
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showAdminError() {
        showError("Admin privileges required for this action")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up any listeners if needed
    }
}