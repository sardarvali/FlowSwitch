package com.example.gail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class pending_tasks : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var noRequestsView: TextView
    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: AdminRequestsAdapter
    private var requestsList = mutableListOf<AdminRequest>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pending_tasks)

        firestore = FirebaseFirestore.getInstance()

        recyclerView = findViewById(R.id.recyclerViewRequests)
        noRequestsView = findViewById(R.id.textViewNoRequests)

        setupRecyclerView()
        loadPendingRequests()
    }

    private fun setupRecyclerView() {
        adapter = AdminRequestsAdapter(requestsList, object : AdminRequestsAdapter.OnRequestActionListener {
            override fun onApprove(request: AdminRequest) {
                handleRequestApproval(request)
            }

            override fun onReject(request: AdminRequest) {
                handleRequestRejection(request)
            }
        })

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadPendingRequests() {
        firestore.collection("admin_requests")
            .whereEqualTo("status", "pending")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Toast.makeText(this, "Error loading requests: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                requestsList.clear()
                if (snapshot != null && !snapshot.isEmpty) {
                    for (document in snapshot.documents) {
                        val request = AdminRequest(
                            id = document.id,
                            requestType = document.getString("requestType") ?: "",
                            requestedBy = document.getString("requestedBy") ?: "",
                            timestamp = document.getTimestamp("timestamp")?.toDate() ?: Date(),
                            status = document.getString("status") ?: "pending",
                            details = document.data ?: mapOf()
                        )
                        requestsList.add(request)
                    }
                    recyclerView.visibility = View.VISIBLE
                    noRequestsView.visibility = View.GONE
                } else {
                    recyclerView.visibility = View.GONE
                    noRequestsView.visibility = View.VISIBLE
                }
                adapter.notifyDataSetChanged()
            }
    }

    private fun handleRequestApproval(request: AdminRequest) {
        when (request.requestType) {
            "streamSwitch" -> {
                showConfirmationDialog("Approve Stream Switch",
                    "Are you sure you want to approve switching to Stream ${request.details["requestedStream"]}?",
                    { approveStreamSwitch(request) })
            }
            "emergencyStop" -> {
                showConfirmationDialog("Approve Emergency Stop",
                    "Are you sure you want to approve the emergency stop request?",
                    { approveEmergencyStop(request) })
            }
            "releaseGas" -> {
                val amount = request.details["releaseAmount"]?.toString() ?: "0.0"
                showConfirmationDialog("Approve Gas Release",
                    "Are you sure you want to approve releasing $amount million m³ of gas?",
                    { approveGasRelease(request) })
            }
            "resetStorage" -> {
                showConfirmationDialog("Approve Storage Reset",
                    "Are you sure you want to reset the storage level?",
                    { approveStorageReset(request) })
            }
            else -> {
                Toast.makeText(this, "Unknown request type", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showConfirmationDialog(title: String, message: String, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Confirm") { _, _ -> onConfirm() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun approveStreamSwitch(request: AdminRequest) {
        val requestedStream = request.details["requestedStream"] as? String ?: return
        val currentStream = request.details["currentStream"] as? String ?: return

        // Update the system_metrics document with new stream info
        firestore.collection("system_metrics").document("current")
            .update(
                mapOf(
                    "activeStream" to requestedStream,
                    "previousStream" to currentStream,
                    "lastStreamChangeTime" to com.google.firebase.Timestamp.now()
                )
            )
            .addOnSuccessListener {
                // Log the stream change event
                val streamChangeData = hashMapOf(
                    "timestamp" to com.google.firebase.Timestamp.now(),
                    "previousStream" to currentStream,
                    "newStream" to requestedStream,
                    "initiatedBy" to request.requestedBy,
                    "approvedBy" to "admin" // Can be updated with actual admin email
                )

                firestore.collection("stream_change_events").add(streamChangeData)
                    .addOnSuccessListener {
                        updateRequestStatus(request.id, "approved")
                        Toast.makeText(this, "Stream switch approved", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update stream: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun approveEmergencyStop(request: AdminRequest) {
        // Update system metrics to indicate emergency stop
        firestore.collection("system_metrics").document("current")
            .update("emergencyStop", true)
            .addOnSuccessListener {
                // Log emergency stop event
                val emergencyStopData = hashMapOf(
                    "timestamp" to com.google.firebase.Timestamp.now(),
                    "initiatedBy" to request.requestedBy,
                    "approvedBy" to "admin"
                )

                firestore.collection("emergency_events").add(emergencyStopData)
                    .addOnSuccessListener {
                        updateRequestStatus(request.id, "approved")
                        Toast.makeText(this, "Emergency stop approved", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to process emergency stop: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun approveGasRelease(request: AdminRequest) {
        val releaseAmount = (request.details["releaseAmount"] as? Number)?.toDouble() ?: 0.0

        // First get current storage
        firestore.collection("system_metrics").document("current")
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val currentStorage = doc.getDouble("current_storage") ?: 0.0
                    val newStorage = maxOf(0.0, currentStorage - releaseAmount)

                    // Update storage
                    firestore.collection("system_metrics").document("current")
                        .update("current_storage", newStorage)
                        .addOnSuccessListener {
                            // Log gas release event
                            val gasReleaseData = hashMapOf(
                                "timestamp" to com.google.firebase.Timestamp.now(),
                                "initiatedBy" to request.requestedBy,
                                "approvedBy" to "admin",
                                "amount" to releaseAmount,
                                "previousStorage" to currentStorage,
                                "newStorage" to newStorage
                            )

                            firestore.collection("gas_release_events").add(gasReleaseData)
                                .addOnSuccessListener {
                                    updateRequestStatus(request.id, "approved")
                                    Toast.makeText(this, "Gas release approved", Toast.LENGTH_SHORT).show()
                                }
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to process gas release: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun approveStorageReset(request: AdminRequest) {
        val targetAmount = (request.details["targetAmount"] as? Number)?.toDouble() ?: 5000.0 // Default to half capacity

        firestore.collection("system_metrics").document("current")
            .update(
                mapOf(
                    "current_storage" to targetAmount,
                    "daily_import" to 0.0
                )
            )
            .addOnSuccessListener {
                // Log storage reset event
                val resetData = hashMapOf(
                    "timestamp" to com.google.firebase.Timestamp.now(),
                    "initiatedBy" to request.requestedBy,
                    "approvedBy" to "admin",
                    "newStorageValue" to targetAmount
                )

                firestore.collection("storage_reset_events").add(resetData)
                    .addOnSuccessListener {
                        updateRequestStatus(request.id, "approved")
                        Toast.makeText(this, "Storage reset approved", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to reset storage: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun handleRequestRejection(request: AdminRequest) {
        showConfirmationDialog("Reject Request",
            "Are you sure you want to reject this request?",
            { updateRequestStatus(request.id, "rejected") })
    }

    private fun updateRequestStatus(requestId: String, status: String) {
        firestore.collection("admin_requests").document(requestId)
            .update(
                mapOf(
                    "status" to status,
                    "processedAt" to com.google.firebase.Timestamp.now()
                )
            )
            .addOnSuccessListener {
                Toast.makeText(this, "Request $status", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update request status: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}

// Data class for Admin Requests
data class AdminRequest(
    val id: String,
    val requestType: String,
    val requestedBy: String,
    val timestamp: Date,
    val status: String,
    val details: Map<String, Any>
)

// Adapter for the RecyclerView
class AdminRequestsAdapter(
    private val requests: List<AdminRequest>,
    private val listener: OnRequestActionListener
) : RecyclerView.Adapter<AdminRequestsAdapter.ViewHolder>() {

    interface OnRequestActionListener {
        fun onApprove(request: AdminRequest)
        fun onReject(request: AdminRequest)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardRequest: CardView = itemView.findViewById(R.id.cardRequest)
        val chipRequestType: Chip = itemView.findViewById(R.id.chipRequestType)
        val textRequestTitle: TextView = itemView.findViewById(R.id.textRequestTitle)
        val textRequestDetails: TextView = itemView.findViewById(R.id.textRequestDetails)
        val textRequestTime: TextView = itemView.findViewById(R.id.textRequestTime)
        val btnApprove: Button = itemView.findViewById(R.id.btnApprove)
        val btnReject: Button = itemView.findViewById(R.id.btnReject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_request, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val request = requests[position]
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

        // Set request type chip
        holder.chipRequestType.text = request.requestType

        // Set colors based on request type
        when (request.requestType) {
            "streamSwitch" -> holder.chipRequestType.setChipBackgroundColorResource(R.color.success_container)
            "emergencyStop" -> holder.chipRequestType.setChipBackgroundColorResource(R.color.error_container)
            else -> holder.chipRequestType.setChipBackgroundColorResource(R.color.inactive_container)
        }

        // Set request title based on type
        holder.textRequestTitle.text = when (request.requestType) {
            "streamSwitch" -> "Stream Switch Request"
            "emergencyStop" -> "Emergency Stop Request"
            "releaseGas" -> "Gas Release Request"
            "resetStorage" -> "Storage Reset Request"
            else -> "Unknown Request"
        }

        // Set request details
        holder.textRequestDetails.text = when (request.requestType) {
            "streamSwitch" -> "Request to switch from Stream ${request.details["currentStream"]} to Stream ${request.details["requestedStream"]}\nBy: ${request.requestedBy}"
            "emergencyStop" -> "Emergency stop requested\nBy: ${request.requestedBy}"
            "releaseGas" -> "Request to release ${request.details["releaseAmount"]} million m³ of gas\nBy: ${request.requestedBy}"
            "resetStorage" -> "Request to reset storage to ${request.details["targetAmount"] ?: "default"} million m³\nBy: ${request.requestedBy}"
            else -> "By: ${request.requestedBy}"
        }

        // Set time
        holder.textRequestTime.text = dateFormat.format(request.timestamp)

        // Set button listeners
        holder.btnApprove.setOnClickListener { listener.onApprove(request) }
        holder.btnReject.setOnClickListener { listener.onReject(request) }
    }

    override fun getItemCount(): Int = requests.size
}