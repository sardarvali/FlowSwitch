package com.example.gail

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class gas_data : AppCompatActivity() {

    private lateinit var todayImport: TextView
    private lateinit var totalStorage: TextView
    private lateinit var storageProgress: ProgressBar
    private lateinit var storagePercentage: TextView
    private lateinit var pipelinePressure: TextView
    private lateinit var pipelineTemperature: TextView
    private lateinit var pipelineFlowRate: TextView
    private lateinit var gasVelocity: TextView
    private lateinit var gasDensity: TextView
    private lateinit var btnToggleStream: Button
    private lateinit var btnEmergencyStop: Button
    private lateinit var btnReleaseGas: Button
    private lateinit var btnResetStorage: Button
    private lateinit var totalFlowRateValue: TextView
    private lateinit var activeStreamValue: TextView
    private lateinit var systemPressureValue: TextView
    private lateinit var streamACard: MaterialCardView
    private lateinit var streamBCard: MaterialCardView
    private lateinit var streamAStatus: Chip
    private lateinit var streamBStatus: Chip
    private lateinit var streamAPressure: TextView
    private lateinit var streamAFlowRate: TextView
    private lateinit var streamBPressure: TextView
    private lateinit var streamBFlowRate: TextView
    private lateinit var btnReports: Button

    // Firestore
    private lateinit var firestore: FirebaseFirestore

    // Data
    private var dailyImport = 0.0 // million m³
    private var totalStoredGas = 0.0 // million m³
    private val storageCapacity = 10000.0 // 100 days * 100 MCM/day
    private val handler = Handler(Looper.getMainLooper())
//    private val updateInterval = 8 * 60 * 60 * 1000L // 5 seconds
    private val updateInterval =5000L
    private val millionCubicMetersPerDay = 100.0 // 100 MCM per day
    private var dailyFlowRate = millionCubicMetersPerDay * 1_000_000 / 24.0

    private val twentyFourHoursInMillis = TimeUnit.HOURS.toMillis(24)
    private var weeklyImport = 0.0 // million m³
    private var monthlyImport = 0.0 // million m³
    private val weekFormat = SimpleDateFormat("yyyy-ww", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    private var currentWeek = weekFormat.format(Date())
    private var currentMonth = monthFormat.format(Date())
//    private val twentyFourHoursInMillis = 24 * 60 * 60 * 1000L

    private enum class Stream { A, B }
    private var activeStream = Stream.A
    private var lastStreamChangeTime = System.currentTimeMillis()

    private data class StreamParameters(
        var pressure: Double = 8.5, // MPa
        var temperature: Double = 15.0, // °C
        var flowRate: Double = 4166666.67 // m³/h (100 MCM/day = 100,000,000 / 24)
    )

    private val streamAParams = StreamParameters()
    private val streamBParams = StreamParameters(
        pressure = 8.3,
        temperature = 14.5,
        flowRate = 4166666.67
    )

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private var currentDate = dateFormat.format(Date())

    private val periodicUpdateRunnable = object : Runnable {
        override fun run() {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastStreamChangeTime >= twentyFourHoursInMillis) {
                toggleActiveStream()
                lastStreamChangeTime = currentTime
            }

            updateStreamParameters()
            updateUI()
            val flowRate = getCurrentFlowRate()
            updatePipelineParameters(flowRate)

            // Add gas to storage
            val millionCubicMeters = flowRate * (updateInterval / 3600000.0) / 1_000_000 // Convert to MCM
            addGasToStorage(millionCubicMeters)

            // Save to Firestore
            saveGasDataToFirestore()

            // Update last update time for tracking between app sessions
            getSharedPreferences("gas_data_prefs", MODE_PRIVATE).edit()
                .putLong("last_update_time", System.currentTimeMillis())
                .apply()

            // Check if date has changed
            val newDate = dateFormat.format(Date())
            if (newDate != currentDate) {
                dailyImport = 0.0
                currentDate = newDate
                getSharedPreferences("gas_data_prefs", MODE_PRIVATE).edit()
                    .putString("last_import_date", currentDate)
                    .apply()
            }

            handler.postDelayed(this, updateInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gas_data)

        firestore = FirebaseFirestore.getInstance()
        initViews()
        setupManualControls()
        loadInitialStorageData()
        startPeriodicUpdates()
        setupStreamParameters()
        checkLastUpdateTimeAndCalculate()
        checkForAdminStreamChanges()
    }

    private fun initViews() {
        todayImport = findViewById(R.id.today_import)
        totalStorage = findViewById(R.id.total_storage)
        storageProgress = findViewById(R.id.storage_progress)
        storagePercentage = findViewById(R.id.storage_percentage)
        pipelinePressure = findViewById(R.id.pipeline_pressure)
        pipelineTemperature = findViewById(R.id.pipeline_temperature)
        pipelineFlowRate = findViewById(R.id.pipeline_flow_rate)
        gasVelocity = findViewById(R.id.gas_velocity)
        gasDensity = findViewById(R.id.gas_density)
        btnToggleStream = findViewById(R.id.btn_toggle_stream)
        btnEmergencyStop = findViewById(R.id.btn_emergency_stop)
        btnReleaseGas = findViewById(R.id.btn_release_gas)
        btnResetStorage = findViewById(R.id.btn_reset_storage)
        totalFlowRateValue = findViewById(R.id.totalFlowRateValue)
        activeStreamValue = findViewById(R.id.activeStreamValue)
        systemPressureValue = findViewById(R.id.systemPressureValue)
        streamACard = findViewById(R.id.streamACard)
        streamBCard = findViewById(R.id.streamBCard)
        streamAStatus = findViewById(R.id.streamAStatus)
        streamBStatus = findViewById(R.id.streamBStatus)
        streamAPressure = findViewById(R.id.streamAPressure)
        streamAFlowRate = findViewById(R.id.streamAFlowRate)
        streamBPressure = findViewById(R.id.streamBPressure)
        streamBFlowRate = findViewById(R.id.streamBFlowRate)
        btnReports = findViewById(R.id.btnReports)

        streamACard.setOnClickListener { showStreamSwitchConfirmation(Stream.A) }
        streamBCard.setOnClickListener { showStreamSwitchConfirmation(Stream.B) }

        updateStorageUI()
        updateUI()
    }
    private fun checkLastUpdateTimeAndCalculate() {
        val prefs = getSharedPreferences("gas_data_prefs", MODE_PRIVATE)
        val lastUpdateTime = prefs.getLong("last_update_time", 0L)
        val currentTime = System.currentTimeMillis()

        if (lastUpdateTime > 0) {
            val timeDiffMillis = currentTime - lastUpdateTime

            // Calculate how much gas would have been imported during this time
            if (timeDiffMillis > 0) {
                val flowRate = millionCubicMetersPerDay * 1_000_000 / 24.0 / 3600000.0 // m³ per millisecond
                val millionCubicMeters = (flowRate * timeDiffMillis) / 1_000_000 // Convert to MCM

                // Update daily import and storage
                addMissedGasToStorage(millionCubicMeters)

                // Log the catch-up import
                Log.d("GasData", "Added missed gas import of $millionCubicMeters MCM during app closure")
            }
        }

        // Update the last update time
        prefs.edit().putLong("last_update_time", currentTime).apply()
    }

    private fun addMissedGasToStorage(millionCubicMeters: Double) {
        // Check if we're on a new day compared to when the app was last opened
        val prefs = getSharedPreferences("gas_data_prefs", MODE_PRIVATE)
        val previousDate = prefs.getString("last_import_date", "")
        val currentDate = dateFormat.format(Date())

        if (currentDate != previousDate) {
            // It's a new day, reset daily import
            dailyImport = millionCubicMeters
            prefs.edit().putString("last_import_date", currentDate).apply()
        } else {
            // Same day, add to existing import
            dailyImport += millionCubicMeters
        }

        // Update storage
        firestore.collection("system_metrics").document("current")
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val currentStorage = doc.getDouble("current_storage") ?: 0.0
                    totalStoredGas = currentStorage + millionCubicMeters
                    totalStoredGas = minOf(totalStoredGas, storageCapacity)

                    // Update Firestore
                    updateFirestoreStorage()

                    // Also update daily import in Firestore
                    firestore.collection("system_metrics").document("current")
                        .update("daily_import", dailyImport)

                    // Now distribute this gas to companies
                    distributeGasToCompanies(millionCubicMeters)
                }
            }
    }
    private fun setupStreamParameters() {
        // Initialize stream parameters to ensure 100 MCM per day
        streamAParams.flowRate = dailyFlowRate
        streamBParams.flowRate = dailyFlowRate

        // Retrieve the last stream change time from shared preferences
        val prefs = getSharedPreferences("gas_data_prefs", MODE_PRIVATE)
        lastStreamChangeTime = prefs.getLong("last_stream_change", System.currentTimeMillis())

        // Check which stream was active last time
        val lastActiveStream = prefs.getString("active_stream", null)
        if (lastActiveStream != null) {
            activeStream = if (lastActiveStream == "A") Stream.A else Stream.B
        }

        // Update UI to reflect the current stream
        updateUI()
    }

    private fun loadInitialStorageData() {
        firestore.collection("system_metrics").document("current")
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    totalStoredGas = doc.getDouble("current_storage") ?: 0.0
                    dailyImport = doc.getDouble("daily_import") ?: 0.0

                    // Reset daily import at midnight
                    val currentDateStr = dateFormat.format(Date())
                    val lastImportDate = getSharedPreferences("gas_data_prefs", MODE_PRIVATE)
                        .getString("last_import_date", "")

                    if (currentDateStr != lastImportDate) {
                        dailyImport = 0.0
                        getSharedPreferences("gas_data_prefs", MODE_PRIVATE).edit()
                            .putString("last_import_date", currentDateStr)
                            .apply()

                        // Update Firestore with reset daily import
                        firestore.collection("system_metrics").document("current")
                            .update("daily_import", 0.0)
                    }
                } else {
                    // Create the document if it doesn't exist
                    val initialData = hashMapOf(
                        "current_storage" to 0.0,
                        "daily_import" to 0.0,
                        "total_capacity" to storageCapacity,
                        "active_contracts" to 0,
                        "revenue" to 0.0,
                        "total_consumption" to 0.0
                    )
                    firestore.collection("system_metrics").document("current")
                        .set(initialData)
                        .addOnSuccessListener {
                            Log.d("GasData", "Initial storage document created")
                        }
                        .addOnFailureListener { e ->
                            Log.e("GasData", "Failed to create initial storage document", e)
                        }
                }
                updateStorageUI()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading storage: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun calculateTransactionCost(volume: Double): Double {
        return volume * 1.48e7 // 1.48 crore per MCM, same as in admin.kt
    }

    private fun setupManualControls() {
        btnToggleStream.setOnClickListener {
            val targetStream = when (activeStream) {
                Stream.A -> Stream.B
                Stream.B -> Stream.A
            }
            showStreamSwitchConfirmation(targetStream)
        }

        btnEmergencyStop.setOnClickListener {
            showEmergencyStopConfirmation()
        }

        btnReleaseGas.setOnClickListener {
            val releaseAmount = 10.0 // million m³
            totalStoredGas -= releaseAmount
            if (totalStoredGas < 0) totalStoredGas = 0.0
            updateStorageUI()
            updateFirestoreStorage()
            Toast.makeText(this, "Released $releaseAmount million m³ of gas", Toast.LENGTH_SHORT).show()
        }

        btnResetStorage.setOnClickListener {
            totalStoredGas = storageCapacity / 2
            dailyImport = 0.0
            updateStorageUI()
            updateFirestoreStorage()
            Toast.makeText(this, "Storage Reset", Toast.LENGTH_SHORT).show()
        }

    }

    private fun updateFirestoreStorage() {
        firestore.collection("system_metrics").document("current")
            .update("current_storage", totalStoredGas)
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update storage: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showStreamSwitchConfirmation(targetStream: Stream) {
        if (activeStream == targetStream) return

        MaterialAlertDialogBuilder(this)
            .setTitle("Request Stream Switch")
            .setMessage("Request to switch to Stream ${targetStream.name} will be sent to admin for approval.")
            .setPositiveButton("Send Request") { _, _ ->
                // Create a request document in Firestore
                val requestData = hashMapOf(
                    "requestType" to "streamSwitch",
                    "requestedStream" to targetStream.name,
                    "currentStream" to activeStream.name,
                    "requestedBy" to FirebaseAuth.getInstance().currentUser?.email,
                    "timestamp" to com.google.firebase.Timestamp.now(),
                    "status" to "pending"
                )

                firestore.collection("admin_requests").add(requestData)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Stream switch request sent to admin", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to send request: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

// Similarly update the btnReleaseGas and btnResetStorage click listeners to request admin approval

    private fun showEmergencyStopConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Emergency Stop")
            .setMessage("This will halt all gas flow. Are you sure?")
            .setPositiveButton("Confirm") { _, _ ->
                showAdminNotification()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAdminNotification() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Request Sent")
            .setMessage("Emergency stop request has been sent to administration for approval.")
            .setPositiveButton("OK", null)
            .show()
        Toast.makeText(this, "Admin notification sent", Toast.LENGTH_LONG).show()
    }

    private fun startPeriodicUpdates() {
        handler.post(periodicUpdateRunnable)
    }


    private fun toggleActiveStream() {
        val previousStream = activeStream
        activeStream = when (activeStream) {
            Stream.A -> Stream.B
            Stream.B -> Stream.A
        }
        lastStreamChangeTime = System.currentTimeMillis()

        // Save stream change time and active stream to preferences for persistence
        val prefs = getSharedPreferences("gas_data_prefs", MODE_PRIVATE)
        prefs.edit()
            .putLong("last_stream_change", lastStreamChangeTime)
            .putString("active_stream", activeStream.name)
            .apply()

        // Update system metrics with the active stream
        firestore.collection("system_metrics").document("current")
            .update("activeStream", activeStream.name)
            .addOnFailureListener { e ->
                Log.e("GasData", "Failed to update active stream in system metrics: ${e.message}")
            }

        // Log the stream change event
        val streamChangeData = hashMapOf(
            "timestamp" to com.google.firebase.Timestamp.now(),
            "previousStream" to previousStream.name,
            "newStream" to activeStream.name,
            "initiatedBy" to "system" // This could be changed if user-initiated
        )

        firestore.collection("stream_change_events").add(streamChangeData)
            .addOnSuccessListener {
                Log.d("GasData", "Successfully logged stream change from ${previousStream.name} to ${activeStream.name}")
            }
            .addOnFailureListener { e ->
                Log.e("GasData", "Failed to log stream change: ${e.message}")
            }

        updateUI()
    }
    private fun checkForAdminStreamChanges() {
        firestore.collection("system_metrics").document("current")
            .addSnapshotListener { documentSnapshot, e ->
                if (e != null || documentSnapshot == null || !documentSnapshot.exists()) {
                    return@addSnapshotListener
                }

                val adminSetStream = documentSnapshot.getString("activeStream")
                if (adminSetStream != null) {
                    val adminStream = if (adminSetStream == "A") Stream.A else Stream.B

                    // Only change if different from current stream
                    if (activeStream != adminStream) {
                        val previousStream = activeStream
                        activeStream = adminStream

                        // Update UI and last change time
                        lastStreamChangeTime = System.currentTimeMillis()
                        updateUI()

                        // Save to preferences
                        getSharedPreferences("gas_data_prefs", MODE_PRIVATE).edit()
                            .putLong("last_stream_change", lastStreamChangeTime)
                            .putString("active_stream", activeStream.name)
                            .apply()

                        Toast.makeText(this, "Stream changed to ${activeStream.name} by admin", Toast.LENGTH_SHORT).show()

                        // Log the admin-initiated change
                        val streamChangeData = hashMapOf(
                            "timestamp" to com.google.firebase.Timestamp.now(),
                            "previousStream" to previousStream.name,
                            "newStream" to activeStream.name,
                            "initiatedBy" to "admin_system"
                        )

                        firestore.collection("stream_change_events").add(streamChangeData)
                    }
                }

                // Also check for emergency stop status
                val emergencyStop = documentSnapshot.getBoolean("emergencyStop") ?: false
                if (emergencyStop) {
                    // Potentially pause or modify the gas flow calculation
                    // This would depend on your requirements for emergency stop
                }
            }
    }

    private fun getCurrentFlowRate(): Double {
        return when (activeStream) {
            Stream.A -> streamAParams.flowRate
            Stream.B -> streamBParams.flowRate
        }
    }

    private fun updateStreamParameters() {
        val currentParams = when (activeStream) {
            Stream.A -> streamAParams
            Stream.B -> streamBParams
        }

        currentParams.pressure += Random.nextDouble(-0.1, 0.1)
        currentParams.pressure = currentParams.pressure.coerceIn(8.0, 9.0)

        currentParams.flowRate += Random.nextDouble(-50000.0, 50000.0)
        currentParams.flowRate = currentParams.flowRate.coerceIn(4000000.0, 4300000.0)
    }

    private fun updateUI() {
        val activeParams = when (activeStream) {
            Stream.A -> streamAParams
            Stream.B -> streamBParams
        }
        val inactiveParams = when (activeStream) {
            Stream.A -> streamBParams
            Stream.B -> streamAParams
        }

        totalFlowRateValue.text = String.format("%.1f m³/h", activeParams.flowRate)
        activeStreamValue.text = if (activeStream == Stream.A) "Stream A" else "Stream B"
        systemPressureValue.text = String.format("%.2f MPa", activeParams.pressure)

        streamAStatus.text = if (activeStream == Stream.A) "Active" else "Inactive"
        streamAStatus.setChipBackgroundColorResource(
            if (activeStream == Stream.A) R.color.success_container
            else R.color.inactive_container
        )
        streamAPressure.text = String.format("%.2f MPa", if (activeStream == Stream.A) activeParams.pressure else inactiveParams.pressure)
        streamAFlowRate.text = String.format("%.1f m³/h", if (activeStream == Stream.A) activeParams.flowRate else inactiveParams.flowRate)

        streamBStatus.text = if (activeStream == Stream.B) "Active" else "Inactive"
        streamBStatus.setChipBackgroundColorResource(
            if (activeStream == Stream.B) R.color.error_container
            else R.color.inactive_container
        )
        streamBPressure.text = String.format("%.2f MPa", if (activeStream == Stream.B) activeParams.pressure else inactiveParams.pressure)
        streamBFlowRate.text = String.format("%.1f m³/h", if (activeStream == Stream.B) activeParams.flowRate else inactiveParams.flowRate)
    }

    private fun updatePipelineParameters(flowRate: Double) {
        val pressure = 8.5 + Random.nextDouble(-0.3, 0.3)
        val temperature = 15.0 + Random.nextDouble(-2.0, 2.0)
        val velocity = calculateVelocity(flowRate)
        val density = calculateDensity(pressure, temperature)

        pipelinePressure.text = String.format("%.2f MPa", pressure)
        pipelineTemperature.text = String.format("%.1f °C", temperature)
        pipelineFlowRate.text = String.format("%.1f m³/h", flowRate)
        gasVelocity.text = String.format("%.2f m/s", velocity)
        gasDensity.text = String.format("%.2f kg/m³", density)
    }

    private fun calculateVelocity(flowRate: Double): Double {
        val pipelineArea = Math.PI * 0.5 * 0.5 // π * r²
        return flowRate / (pipelineArea * 3600)
    }

    private fun calculateDensity(pressure: Double, temperature: Double): Double {
        val molarMassNaturalGas = 0.016 // kg/mol
        val gasConstant = 8.314 // J/(mol*K)
        val absoluteTemp = temperature + 273.15 // Kelvin
        val pressurePa = pressure * 1_000_000 // MPa to Pa
        return (pressurePa * molarMassNaturalGas) / (gasConstant * absoluteTemp)
    }

    private fun addGasToStorage(millionCubicMeters: Double) {
        dailyImport += millionCubicMeters
        totalStoredGas += millionCubicMeters
        totalStoredGas = minOf(totalStoredGas, storageCapacity)
        updateStorageUI()

        // Only update storage in Firestore - companies get updated separately
        firestore.collection("system_metrics").document("current")
            .update("current_storage", totalStoredGas)
            .addOnFailureListener { e ->
                Log.e("GasData", "Failed to update storage: ${e.message}")
            }

        // Distribute gas to companies based on their quotas
        distributeGasToCompanies(millionCubicMeters)
    }
    // In gas_data.kt, add these methods to the gas_data class
    private fun distributeGasToCompanies(gasImported: Double) {
        // Get the date for tracking daily distributions
        val today = dateFormat.format(Date())

        firestore.collection("companies")
            .whereEqualTo("status", "active")
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) return@addOnSuccessListener

                // Check if daily fixed distribution already happened
                val prefs = getSharedPreferences("gas_data_prefs", MODE_PRIVATE)
                val lastDistributionDate = prefs.getString("last_distribution_date", "")

                // Calculate regular distribution based on quota
                var totalQuota = 0.0
                val activeCompanies = mutableListOf<DocumentSnapshot>()

                // Calculate total quota for proportional distribution
                for (document in documents) {
                    val quota = document.getDouble("dailyExportQuota") ?: 0.0
                    totalQuota += quota
                    activeCompanies.add(document)
                }

                if (totalQuota <= 0) return@addOnSuccessListener

                // Use batch write to ensure all updates succeed or fail together
                val batch = firestore.batch()

                // Track total transaction cost for system metrics
                var totalTransactionCost = 0.0
                var totalDistributed = 0.0

                // Check if we need to do the daily fixed distribution (10 MCM per company)
                if (today != lastDistributionDate) {
                    val fixedAmountPerCompany = 10.0 // 10 MCM per company
                    val totalFixedAmount = activeCompanies.size * fixedAmountPerCompany

                    // Check if we have enough gas in storage
                    if (totalStoredGas >= totalFixedAmount) {
                        // Do the fixed daily distribution (10 MCM to each company)
                        for (company in activeCompanies) {
                            val companyRef = firestore.collection("companies").document(company.id)
                            val companyName = company.getString("name") ?: "Unknown"
                            val currentBalance = company.getDouble("currentBalance") ?: 0.0

                            // Add fixed 10 MCM to each company
                            val newBalance = currentBalance + fixedAmountPerCompany
                            val transactionCost = calculateTransactionCost(fixedAmountPerCompany)
                            totalTransactionCost += transactionCost
                            totalDistributed += fixedAmountPerCompany

                            // Update company balance
                            batch.update(companyRef, "currentBalance", newBalance)
                            batch.update(companyRef, "totalTransactionCost", FieldValue.increment(transactionCost))
                            batch.update(companyRef, "revenue", FieldValue.increment(transactionCost))

                            // Create fixed distribution record
                            val fixedDistributionData = hashMapOf(
                                "companyId" to company.id,
                                "companyName" to companyName,
                                "volume" to fixedAmountPerCompany,
                                "timestamp" to com.google.firebase.Timestamp.now(),
                                "type" to "daily_fixed_distribution",
                                "transactionCost" to transactionCost
                            )

                            batch.set(firestore.collection("gas_transactions").document(), fixedDistributionData)
                        }

                        // Update the last distribution date
                        prefs.edit()
                            .putString("last_distribution_date", today)
                            .apply()

                        // Deduct from total storage - this should happen after all companies are processed
                        totalStoredGas -= totalFixedAmount
                    } else {
                        // Not enough gas, log error
                        Toast.makeText(this, "Insufficient gas storage for daily distribution", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }
                }

                // Now handle the normal import distribution based on quota percentages
                if (gasImported > 0) {
                    for (company in activeCompanies) {
                        val companyRef = firestore.collection("companies").document(company.id)
                        val companyName = company.getString("name") ?: "Unknown"
                        val quota = company.getDouble("dailyExportQuota") ?: 0.0
                        val currentBalance = company.getDouble("currentBalance") ?: 0.0

                        val quotaPercentage = quota / totalQuota
                        val gasShare = gasImported * quotaPercentage
                        val newBalance = currentBalance + gasShare
                        val transactionCost = calculateTransactionCost(gasShare)
                        totalTransactionCost += transactionCost
                        totalDistributed += gasShare

                        // Update company balance for regular import
                        batch.update(companyRef, "currentBalance", newBalance)
                        batch.update(companyRef, "totalTransactionCost", FieldValue.increment(transactionCost))
                        batch.update(companyRef, "revenue", FieldValue.increment(transactionCost))

                        // Create a gas import record
                        val gasAdditionData = hashMapOf(
                            "companyId" to company.id,
                            "companyName" to companyName,
                            "volume" to gasShare,
                            "timestamp" to com.google.firebase.Timestamp.now(),
                            "type" to "import",
                            "transactionCost" to transactionCost
                        )

                        batch.set(firestore.collection("gas_transactions").document(), gasAdditionData)
                    }
                }

                // Update system metrics with the new revenue and consumption data
                firestore.collection("system_metrics").document("current").get()
                    .addOnSuccessListener { metricsDoc ->
                        if (metricsDoc.exists()) {
                            val currentRevenue = metricsDoc.getDouble("revenue") ?: 0.0
                            val currentConsumption = metricsDoc.getDouble("total_consumption") ?: 0.0

                            batch.update(firestore.collection("system_metrics").document("current"),
                                mapOf(
                                    "revenue" to (currentRevenue + totalTransactionCost),
                                    "total_consumption" to (currentConsumption + totalDistributed),
                                    "current_storage" to totalStoredGas,
                                    "total_export" to FieldValue.increment(totalDistributed)
                                )
                            )
                        }

                        // Commit all the batch operations
                        batch.commit()
                            .addOnSuccessListener {
                                Log.d("GasData", "Successfully updated all companies with gas distributions")
                                updateStorageUI()
                            }
                            .addOnFailureListener { e ->
                                Log.e("GasData", "Batch update failed: ${e.message}")
                            }
                    }
            }
            .addOnFailureListener { e ->
                Log.e("GasData", "Failed to fetch companies: ${e.message}")
            }
    }

    private fun updateStorageUI() {
        todayImport.text = String.format("%.2f million m³", dailyImport)
        totalStorage.text = String.format("%.2f million m³", totalStoredGas)
        val storagePercentageValue = (totalStoredGas / storageCapacity * 100).toInt()
        storageProgress.progress = storagePercentageValue
        storagePercentage.text = String.format("%d%%", storagePercentageValue)
    }

    private fun saveGasDataToFirestore() {
        val activeParams = when (activeStream) {
            Stream.A -> streamAParams
            Stream.B -> streamBParams
        }

        val periodImport = activeParams.flowRate * (updateInterval / 3600000.0) / 1_000_000 // Convert to MCM
        val periodCost = calculateTransactionCost(periodImport)

        // Update system metrics with new data
        firestore.collection("system_metrics").document("current")
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    // Get current values
                    val currentConsumption = doc.getDouble("total_consumption") ?: 0.0
                    val currentRevenue = doc.getDouble("revenue") ?: 0.0

                    // Update with new values
                    firestore.collection("system_metrics").document("current")
                        .update(
                            mapOf(
                                "current_storage" to totalStoredGas,
                                "daily_import" to dailyImport,
                                "total_consumption" to (currentConsumption + periodImport),
                                "revenue" to (currentRevenue + periodCost)
                            )
                        )
                }
            }

        // Save detailed history record
        val gasData = hashMapOf(
            "timestamp" to com.google.firebase.Timestamp.now(),
            "stream" to activeStream.name,
            "flowRate" to activeParams.flowRate,
            "pressure" to activeParams.pressure,
            "temperature" to activeParams.temperature,
            "volume" to periodImport,  // Changed from dailyImport to periodImport for more accuracy
            "totalStorageLevel" to totalStoredGas,
            "storagePercentage" to (totalStoredGas / storageCapacity * 100),
            "status" to "active",
            "periodImport" to periodImport,
            "periodCost" to periodCost
        )

        firestore.collection("gas_storage_history").add(gasData)
            .addOnSuccessListener {
                Log.d("GasData", "Successfully saved gas data to history")
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save gas data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

}