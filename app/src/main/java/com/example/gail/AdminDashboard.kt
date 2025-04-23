package com.example.gail

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class AdminDashboard : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    // UI Components
    private lateinit var textStorageLevel: TextView
    private lateinit var textDailyImport: TextView
    private lateinit var textActiveStream: TextView
    private lateinit var textTotalRevenue: TextView
    private lateinit var textPendingRequests: TextView
    private lateinit var chipSystemStatus: Chip

    private lateinit var cardRequests: CardView
    private lateinit var cardCompanies: CardView
    private lateinit var cardGasData: CardView
    private lateinit var cardReports: CardView
    private lateinit var cardSettings: CardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        // Initialize Firebase instances
        firestore = FirebaseFirestore.getInstance()
        auth = Firebase.auth

        // First verify if user is admin before initializing the dashboard
        verifyAdminStatus()
    }

    private fun initializeDashboard() {
        initViews()
        loadSystemMetrics()
        countPendingRequests()
        setupClickListeners()
    }

    private fun initViews() {
        textStorageLevel = findViewById(R.id.textStorageLevel)
        textDailyImport = findViewById(R.id.textDailyImport)
        textActiveStream = findViewById(R.id.textActiveStream)
        textTotalRevenue = findViewById(R.id.textTotalRevenue)
        textPendingRequests = findViewById(R.id.textPendingRequests)
        chipSystemStatus = findViewById(R.id.chipSystemStatus)

        cardRequests = findViewById(R.id.cardRequests)
        cardCompanies = findViewById(R.id.cardCompanies)
        cardGasData = findViewById(R.id.cardGasData)
        cardReports = findViewById(R.id.cardReports)
        cardSettings = findViewById(R.id.cardSettings)
    }

    private fun loadSystemMetrics() {
        firestore.collection("system_metrics").document("current")
            .addSnapshotListener { documentSnapshot, e ->
                if (e != null) {
                    Toast.makeText(this, "Error loading metrics: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (documentSnapshot != null && documentSnapshot.exists()) {
                    val currentStorage = documentSnapshot.getDouble("current_storage") ?: 0.0
                    val totalCapacity = documentSnapshot.getDouble("total_capacity") ?: 10000.0
                    val dailyImport = documentSnapshot.getDouble("daily_import") ?: 0.0

                    // Make sure the active stream is properly tracked
                    val activeStream = documentSnapshot.getString("activeStream") ?: "A"
                    val revenue = documentSnapshot.getDouble("revenue") ?: 0.0
                    val emergencyStop = documentSnapshot.getBoolean("emergencyStop") ?: false

                    // Format numbers with commas
                    val storagePercentage = (currentStorage / totalCapacity * 100).toInt()
                    textStorageLevel.text = String.format("%.2f MCM (%.2f%%)", currentStorage, storagePercentage.toDouble())
                    textDailyImport.text = String.format("%.2f MCM", dailyImport)
                    textActiveStream.text = "Stream $activeStream"

                    // Format revenue in crores
                    val revenueInCrores = revenue / 10000000 // Convert to crores
                    textTotalRevenue.text = String.format("₹%.2f Cr", revenueInCrores)

                    // Update system status chip
                    if (emergencyStop) {
                        chipSystemStatus.text = "EMERGENCY STOP"
                        chipSystemStatus.setChipBackgroundColorResource(R.color.error_container)
                    } else {
                        chipSystemStatus.text = "OPERATIONAL"
                        chipSystemStatus.setChipBackgroundColorResource(R.color.success_container)
                    }
                }
            }
    }

    private fun countPendingRequests() {
        firestore.collection("admin_requests")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { querySnapshot, e ->
                if (e != null) {
                    return@addSnapshotListener
                }

                val count = querySnapshot?.size() ?: 0
                textPendingRequests.text = count.toString()

                // Highlight the requests card if there are pending requests
                val frameLayout = cardRequests.getChildAt(0) as FrameLayout
                if (count > 0) {
                    frameLayout.setBackgroundResource(R.drawable.card_border_background)
                } else {
                    frameLayout.setBackgroundResource(android.R.color.transparent)
                }
            }
    }

    private fun setupClickListeners() {
        cardRequests.setOnClickListener {
            startActivity(Intent(this, pending_tasks::class.java))
        }

        cardCompanies.setOnClickListener {
            startActivity(Intent(this, CompanyListActivity::class.java))
        }

        cardGasData.setOnClickListener {
            startActivity(Intent(this, gas_data::class.java))
        }

        cardReports.setOnClickListener {
            // Navigate to reports screen
            Toast.makeText(this, "Reports section coming soon", Toast.LENGTH_SHORT).show()
        }

        cardSettings.setOnClickListener {
            // Navigate to admin settings
            Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show()
        }

        cardGasData.setOnLongClickListener {
            toggleActiveStream()
            true
        }

        findViewById<Button>(R.id.btnToggleEmergency).setOnClickListener {
            toggleEmergencyStatus()
        }
    }

    private fun toggleEmergencyStatus() {
        firestore.collection("system_metrics").document("current")
            .get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    val currentStatus = document.getBoolean("emergencyStop") ?: false

                    // Toggle the status
                    firestore.collection("system_metrics").document("current")
                        .update(
                            mapOf(
                                "emergencyStop" to !currentStatus,
                                "lastEmergencyChange" to com.google.firebase.Timestamp.now()
                            )
                        )
                        .addOnSuccessListener {
                            val newStatus = if (!currentStatus) "EMERGENCY STOP" else "OPERATIONAL"
                            Toast.makeText(this, "System status set to: $newStatus", Toast.LENGTH_SHORT).show()

                            // Log the event
                            val eventData = hashMapOf(
                                "timestamp" to com.google.firebase.Timestamp.now(),
                                "previousStatus" to if (currentStatus) "EMERGENCY STOP" else "OPERATIONAL",
                                "newStatus" to if (!currentStatus) "EMERGENCY STOP" else "OPERATIONAL",
                                "changedBy" to (auth.currentUser?.email ?: "unknown")
                            )

                            firestore.collection("emergency_status_events").add(eventData)
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to update status: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
    }

    private fun toggleActiveStream() {
        firestore.collection("system_metrics").document("current")
            .get()
            .addOnSuccessListener { document ->
                // Get current active stream
                val currentActiveStream = document.getString("activeStream") ?: "A"
                val newActiveStream = if (currentActiveStream == "A") "B" else "A"

                // Update the active stream in system metrics
                firestore.collection("system_metrics").document("current")
                    .update("activeStream", newActiveStream)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Active stream switched to $newActiveStream", Toast.LENGTH_SHORT).show()

                        // Log stream change
                        val streamChangeData = hashMapOf(
                            "timestamp" to com.google.firebase.Timestamp.now(),
                            "previousStream" to currentActiveStream,
                            "newStream" to newActiveStream,
                            "initiatedBy" to (auth.currentUser?.email ?: "unknown")
                        )

                        // Add to stream change events
                        firestore.collection("stream_change_events").add(streamChangeData)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to change stream: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    private fun verifyAdminStatus() {
        val user = auth.currentUser
        if (user == null) {
            // User not logged in, redirect to login page
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            return
        }

        val email = user.email
        if (email == null) {
            // Email not available
            Toast.makeText(this, "User email not available", Toast.LENGTH_SHORT).show()
            return
        }

        // Query Firestore to check if user is admin
        firestore.collection("users")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // User not found in database
                    Toast.makeText(this, "User not found in system", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val document = documents.documents[0]
                val role = document.getString("role") ?: ""

                if (role == "admin") {
                    // User is admin, initialize dashboard
                    initializeDashboard()
                } else {
                    // User is not admin
                    Toast.makeText(this, "Admin privileges required for this action", Toast.LENGTH_SHORT).show()
                    navigateToPrevious()
                }
            }
            .addOnFailureListener { e ->
                // Error checking admin status
                Toast.makeText(this, "Error verifying access: ${e.message}", Toast.LENGTH_SHORT).show()
                navigateToPrevious()
            }
    }


    private fun navigateToPrevious() {
        // Simply finish this activity to go back to previous screen
        finish()
    }
}