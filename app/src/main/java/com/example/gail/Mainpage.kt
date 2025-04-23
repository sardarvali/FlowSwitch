package com.example.gail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class Mainpage : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    // Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storageListener: ListenerRegistration
    private lateinit var metricsListener: ListenerRegistration

    // UI Components
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var fabSettings: FloatingActionButton
    private lateinit var navView: NavigationView
    private lateinit var logout: Button
    private lateinit var btnReports: Button
    private lateinit var btnStorage: Button
    private lateinit var btnCompanies: Button
    private lateinit var btnaddcompany: Button
    private lateinit var btnpending: Button

    // Dashboard elements
    private lateinit var totalConsumptionValue: TextView
    private lateinit var activeContractsValue: TextView
    private lateinit var revenueValue: TextView
    private lateinit var consumptionChart: BarChart
    private lateinit var capacityIndicator: CircularProgressIndicator
    private lateinit var capacityPercentage: TextView
    private lateinit var storageStatus: TextView
    private lateinit var gasDataRecyclerView: RecyclerView
    private lateinit var gasStorageAdapter: GasStorageAdapter

    // Cards
    private lateinit var metricsCard: CardView
    private lateinit var chartCard: CardView
    private lateinit var storageCard: CardView
    private lateinit var gasStorageListCard: CardView
    private lateinit var emergencyCard: CardView
    private lateinit var controlsCard: CardView

    // Constants
    private val EMERGENCY_CONTACT = "1800-123-4567"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_mainpage)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val isLoggedIn = sharedPreferences.getBoolean("is_logged_in", false)
        val userEmail = sharedPreferences.getString("user_email", null)

        if (!isLoggedIn || userEmail == null) {
            startActivity(Intent(this, Login_page::class.java))
            finish()
            return
        }


        // Initialize UI components
        initializeViews()
        setupNavigation()
        setGreeting()
        setupClickListeners()

        // Setup RecyclerView and adapter
        setupRecyclerView()
        setupKeyMetrics()

        // Load real-time data
        setupRealTimeDataListeners()

        setupGasDataGraph()
    }

    private fun initializeViews() {
        // Toolbar and Navigation
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)

        // Buttons and FABs
        fabSettings = findViewById(R.id.fabSettings)
        logout = findViewById(R.id.logout)
        btnReports = findViewById(R.id.btnReports)
        btnStorage = findViewById(R.id.btnGasStorage)
        btnCompanies = findViewById(R.id.btnCompaniesList)
        btnaddcompany = findViewById(R.id.btnaddcompanies)
        btnpending = findViewById(R.id.btnPendingTasks)

        // Dashboard Metrics
        totalConsumptionValue = findViewById(R.id.totalConsumptionValue)
        activeContractsValue = findViewById(R.id.activeContractsValue)
        revenueValue = findViewById(R.id.revenueValue)
        consumptionChart = findViewById(R.id.consumptionChart)
        capacityIndicator = findViewById(R.id.capacityIndicator)
        capacityPercentage = findViewById(R.id.capacityPercentage)
        storageStatus = findViewById(R.id.storageStatus)
        gasDataRecyclerView = findViewById(R.id.gasDataRecyclerView)

        // Cards
        metricsCard = findViewById(R.id.metricsCard)
        chartCard = findViewById(R.id.chartCard)
        storageCard = findViewById(R.id.storageCard)
        gasStorageListCard = findViewById(R.id.gasStorageListCard)
        emergencyCard = findViewById(R.id.emergencyCard)
        controlsCard = findViewById(R.id.controlsCard)
    }



    private fun setupRecyclerView() {
        gasStorageAdapter = GasStorageAdapter(emptyList()) { storage ->
            showStorageDetails(storage)
        }
        gasDataRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@Mainpage)
            adapter = gasStorageAdapter
        }
    }
    private fun setupKeyMetrics() {
        // Fetch accurate metrics data from Firestore
        firestore.collection("system_metrics")
            .document("current")
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // Get values from document
                    val consumption = document.getDouble("total_consumption") ?: 0.0
                    val revenue = document.getDouble("revenue") ?: 0.0

                    // Get active contracts by counting active companies
                    firestore.collection("companies")
                        .whereEqualTo("status", "active")
                        .get()
                        .addOnSuccessListener { companyDocs ->
                            val activeContractsCount = companyDocs.size()

                            // Update UI with formatted values
                            totalConsumptionValue.text = "%.1f MMSCM".format(consumption)
                            activeContractsValue.text = activeContractsCount.toString()
                            revenueValue.text = "₹ %.2f Cr".format(revenue / 10000000)

                            // Update Firestore with accurate contract count
                            firestore.collection("system_metrics").document("current")
                                .update("active_contracts", activeContractsCount)

                            // Add animation to the metrics card
                            metricsCard.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))
                        }
                        .addOnFailureListener { e ->
                            showSnackbar("Error fetching contracts: ${e.message}")
                        }
                } else {
                    showSnackbar("No metrics data available")
                }
            }
            .addOnFailureListener { e ->
                showSnackbar("Error fetching metrics: ${e.message}")
            }
    }


    private fun setupRealTimeDataListeners() {
        storageListener = firestore.collection("storage_facilities")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    showSnackbar("Error loading storage data")
                    return@addSnapshotListener
                }

                val storageData = snapshots?.map { doc ->
                    val capacity = doc.getDouble("capacity") ?: 0.0
                    val current = doc.getDouble("current") ?: 0.0
                    val percentage = (current / capacity * 100).toInt()

                    GasStorageData(
                        id = doc.id,
                        name = doc.getString("name") ?: "Unknown",
                        currentLevel = "%.1f MMSCM".format(current),
                        capacityUsed = "$percentage%",
                        status = when {
                            percentage > 90 -> "Critical"
                            percentage > 75 -> "Warning"
                            else -> "Normal"
                        }
                    )
                } ?: emptyList()

                gasStorageAdapter.updateData(storageData)
            }

        // System metrics listener
        metricsListener = firestore.collection("system_metrics")
            .document("current")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    showSnackbar("Error loading system metrics")
                    return@addSnapshotListener
                }

                snapshot?.let { doc ->
                    // Update dashboard metrics
                    val consumption = doc.getDouble("total_consumption") ?: 0.0
                    val contracts = doc.getLong("active_contracts") ?: 0
                    val revenue = doc.getDouble("revenue") ?: 0.0

                    totalConsumptionValue.text = "%.1f MMSCM".format(consumption)
                    activeContractsValue.text = contracts.toString()
                    revenueValue.text = "₹ %.2f Cr".format(revenue / 10000000)

                    // Update storage capacity
                    val capacity = doc.getDouble("total_capacity") ?: 0.0
                    val current = doc.getDouble("current_storage") ?: 0.0
                    val percentage = (current / capacity * 100).toInt()

                    capacityIndicator.progress = percentage
                    capacityPercentage.text = "$percentage%"

                    when {
                        percentage > 90 -> {
                            storageStatus.text = "Critical: Near Capacity"
                            storageStatus.setTextColor(ContextCompat.getColor(this, R.color.error))
                        }
                        percentage > 75 -> {
                            storageStatus.text = "Warning: High Usage"
                            storageStatus.setTextColor(ContextCompat.getColor(this, R.color.warning))
                        }
                        else -> {
                            storageStatus.text = "Normal Operation"
                            storageStatus.setTextColor(ContextCompat.getColor(this, R.color.success))
                        }
                    }

                    // Update consumption chart
                    val monthlyData = doc.get("monthly_consumption") as? Map<String, Double>
                    monthlyData?.let { updateConsumptionChart(it) }
                }
            }
    }

    private fun updateConsumptionChart(monthlyData: Map<String, Double>) {
        val entries = ArrayList<BarEntry>()
        val months = ArrayList<String>()

        monthlyData.entries.sortedBy { it.key }.forEachIndexed { index, entry ->
            entries.add(BarEntry(index.toFloat(), entry.value.toFloat()))
            months.add(getMonthName(entry.key))
        }

        val dataSet = BarDataSet(entries, "Monthly Consumption (MMSCM)").apply {
            color = ContextCompat.getColor(this@Mainpage, R.color.primary)
        }

        consumptionChart.apply {
            data = BarData(dataSet).apply { barWidth = 0.6f }
            description.isEnabled = false
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(months)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
            }
            axisRight.isEnabled = false
            animateY(1000)
            invalidate()
        }
    }

    private fun getMonthName(monthKey: String): String {
        return when (monthKey) {
            "01" -> "Jan"; "02" -> "Feb"; "03" -> "Mar"; "04" -> "Apr"
            "05" -> "May"; "06" -> "Jun"; "07" -> "Jul"; "08" -> "Aug"
            "09" -> "Sep"; "10" -> "Oct"; "11" -> "Nov"; "12" -> "Dec"
            else -> monthKey
        }
    }

    private fun showStorageDetails(storage: GasStorageData) {
        MaterialAlertDialogBuilder(this)
            .setTitle(storage.name)
            .setMessage("""
                Current Level: ${storage.currentLevel}
                Capacity Used: ${storage.capacityUsed}
                Status: ${storage.status}
            """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun setupNavigation() {
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        navView.setNavigationItemSelectedListener(this)
    }

    private fun setGreeting() {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            currentHour < 12 -> "Good Morning!"
            currentHour < 18 -> "Good Afternoon!"
            else -> "Good Evening!"
        }
        findViewById<TextView>(R.id.greetingtv).text = greeting
    }

    private fun setupClickListeners() {
        fabSettings.setOnClickListener {
            it.startAnimation(AnimationUtils.loadAnimation(this, R.anim.rotate))
            showSettingsDialog()
        }

        logout.setOnClickListener {
            auth.signOut()
            val sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
            sharedPreferences.edit().apply {
                putBoolean("is_logged_in", false)
                remove("user_email")
                remove("user_role")
                apply()
            }

            startActivity(Intent(this, Login_page::class.java))
            finish()
        }

        btnStorage.setOnClickListener {
            startActivity(Intent(this, gas_data::class.java))
        }
        btnCompanies.setOnClickListener {
            startActivity(Intent(this, CompanyListActivity::class.java))
        }
        btnaddcompany.setOnClickListener {
            startActivity(Intent(this, admin::class.java))
        }

        emergencyCard.setOnClickListener {
            showEmergencyProtocolDialog()
        }

        btnReports.setOnClickListener {
            showReportsDialog()
        }
        btnpending.setOnClickListener {
            startActivity(Intent(this, AdminDashboard::class.java))
        }
    }

    private fun showEmergencyProtocolDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Emergency Protocols")
            .setItems(arrayOf(
                "Shutdown Protocol",
                "Pressure Relief",
                "Emergency Supply Reallocation",
                "Contact Emergency Team"
            )) { _, which ->
                when (which) {
                    0 -> confirmEmergencyAction("Shutdown Protocol")
                    1 -> confirmEmergencyAction("Pressure Relief")
                    2 -> confirmEmergencyAction("Emergency Supply Reallocation")
                    3 -> contactEmergencyTeam()
                }
            }
            .show()
    }

    private fun confirmEmergencyAction(protocol: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Confirm $protocol")
            .setMessage("This is a critical operation that requires authorization.")
            .setPositiveButton("Confirm") { _, _ ->
                logEmergencyAction(protocol)
                showSnackbar("$protocol initiated - Confirmation pending")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logEmergencyAction(protocol: String) {
        val actionData = hashMapOf(
            "action" to protocol,
            "timestamp" to dateFormat.format(Date()),
            "status" to "pending",
            "initiated_by" to auth.currentUser?.uid
        )

        firestore.collection("emergency_actions").add(actionData)
            .addOnFailureListener { e ->
                showSnackbar("Failed to log emergency action: ${e.message}")
            }
    }

    private fun contactEmergencyTeam() {
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$EMERGENCY_CONTACT")))
    }

    private fun showSettingsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Settings")
            .setItems(arrayOf(
                "System Configuration",
                "Alerts",
                "Maintenance",
                "About"
            )) { _, which ->
                when (which) {
                    0 -> showSnackbar("System Configuration")
                    1 -> showSnackbar("Alerts")
                    2 -> showSnackbar("Maintenance")
                    3 -> showAboutDialog()
                }
            }
            .show()
    }


    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("About")
            .setMessage("Gas Management System v1.0\n© ${Calendar.getInstance().get(Calendar.YEAR)} GAIL")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.nav_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.nav_profile -> {
                Toast.makeText(this, "Profile", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.nav_settings -> {
                showSettingsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onNavigationItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.profile -> {
                Toast.makeText(this, "Profile Clicked", Toast.LENGTH_SHORT).show()
            }
            R.id.setting -> {
                showSettingsDialog()
            }
        }
        drawerLayout.closeDrawers()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        storageListener.remove()
        metricsListener.remove()
    }
    private fun showReportsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_reports, null)
        val streamChangesRecyclerView = dialogView.findViewById<RecyclerView>(R.id.streamChangesRecyclerView)
        val gasAdditionsRecyclerView = dialogView.findViewById<RecyclerView>(R.id.gasAdditionsRecyclerView)

        // Add loading indicators
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.loadingProgressBar)
        val statusText = dialogView.findViewById<TextView>(R.id.statusText)
        statusText.text = "Loading recent gas operations..."

        // Setup RecyclerViews
        streamChangesRecyclerView.layoutManager = LinearLayoutManager(this)
        gasAdditionsRecyclerView.layoutManager = LinearLayoutManager(this)

        val streamChangesAdapter = StreamChangeAdapter(emptyList())
        val gasAdditionsAdapter = GasAdditionAdapter(emptyList())

        streamChangesRecyclerView.adapter = streamChangesAdapter
        gasAdditionsRecyclerView.adapter = gasAdditionsAdapter

        // Create and show the dialog
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Gas Operations Report (Last 48 Hours)")
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .create()

        dialog.show()

        // Fetch the last 48 hours of data
        val cutoffTime = Calendar.getInstance().apply {
            add(Calendar.HOUR, -48)
        }.time

        // First fetch current storage data to display at the top
        firestore.collection("system_metrics").document("current")
            .get()
            .addOnSuccessListener { doc ->
                val currentStorage = doc.getDouble("current_storage") ?: 0.0
                val dailyImport = doc.getDouble("daily_import") ?: 0.0

                // Update storage summary in the dialog
                dialogView.findViewById<TextView>(R.id.currentStorageValue).text =
                    String.format("%.2f MCM", currentStorage)
                dialogView.findViewById<TextView>(R.id.dailyImportValue).text =
                    String.format("%.2f MCM", dailyImport)
            }

        // Fetch stream changes
        firestore.collection("stream_change_events")
            .whereGreaterThan("timestamp", com.google.firebase.Timestamp(cutoffTime))
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                val streamChanges = documents.map { doc ->
                    val timestamp = doc.getTimestamp("timestamp")?.toDate() ?: Date()
                    val timeString = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(timestamp)

                    StreamChangeEvent(
                        timeString,
                        "Stream ${doc.getString("previousStream") ?: "Unknown"} → ${doc.getString("newStream") ?: "Unknown"}",
                        doc.getString("initiatedBy") ?: "System"
                    )
                }
                streamChangesAdapter.updateData(streamChanges)
                statusText.visibility = View.GONE
                progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                statusText.text = "Error loading data: ${e.message}"
                progressBar.visibility = View.GONE
            }

        // Fetch gas additions with proper error handling
        firestore.collection("gas_addition_records")
            .whereGreaterThan("timestamp", com.google.firebase.Timestamp(cutoffTime))
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // Add a message if no records
                    val emptyRecord = GasAdditionRecord(
                        "N/A",
                        "No recent additions",
                        "0.00 MCM"
                    )
                    gasAdditionsAdapter.updateData(listOf(emptyRecord))
                } else {
                    val gasAdditions = documents.map { doc ->
                        val timestamp = doc.getTimestamp("timestamp")?.toDate() ?: Date()
                        val timeString = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(timestamp)

                        GasAdditionRecord(
                            timeString,
                            doc.getString("companyName") ?: "Unknown",
                            "%.2f MCM".format(doc.getDouble("volume") ?: 0.0)
                        )
                    }
                    gasAdditionsAdapter.updateData(gasAdditions)
                }
            }
            .addOnFailureListener { e ->
                showSnackbar("Failed to load gas additions: ${e.message}")
            }
    }
    private fun setupGasDataGraph() {
        // Assuming you have a LineChart in your layout with id gasDataChart
        val gasDataChart: com.github.mikephil.charting.charts.LineChart = findViewById(R.id.gasDataChart)

        // Fetch the last 48 hours of gas data
        val cutoffTime = Calendar.getInstance().apply {
            add(Calendar.HOUR, -48)
        }.time

        firestore.collection("gas_storage_history")
            .whereGreaterThan("timestamp", com.google.firebase.Timestamp(cutoffTime))
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    showSnackbar("No gas data available for the last 48 hours")
                    return@addOnSuccessListener
                }

                // Prepare data for the chart
                val storageEntries = ArrayList<com.github.mikephil.charting.data.Entry>()
                val flowRateEntries = ArrayList<com.github.mikephil.charting.data.Entry>()
                val timeLabels = ArrayList<String>()
                val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

                documents.forEachIndexed { index, doc ->
                    val timestamp = doc.getTimestamp("timestamp")?.toDate() ?: Date()
                    val storageLevel = doc.getDouble("totalStorageLevel")?.toFloat() ?: 0f
                    val flowRate = doc.getDouble("flowRate")?.toFloat()?.div(100000) ?: 0f // Scale down for visibility

                    storageEntries.add(com.github.mikephil.charting.data.Entry(index.toFloat(), storageLevel))
                    flowRateEntries.add(com.github.mikephil.charting.data.Entry(index.toFloat(), flowRate))
                    timeLabels.add(dateFormat.format(timestamp))
                }

                // Create storage level dataset
                val storageDataSet = com.github.mikephil.charting.data.LineDataSet(storageEntries, "Storage Level (MCM)").apply {
                    color = ContextCompat.getColor(this@Mainpage, R.color.primary)
                    setCircleColor(ContextCompat.getColor(this@Mainpage, R.color.primary))
                    setDrawCircles(false)
                    lineWidth = 2f
                    mode = com.github.mikephil.charting.data.LineDataSet.Mode.CUBIC_BEZIER
                }

                // Create flow rate dataset
                val flowRateDataSet = com.github.mikephil.charting.data.LineDataSet(flowRateEntries, "Flow Rate (scaled)").apply {
                    color = ContextCompat.getColor(this@Mainpage, R.color.warning)
                    setCircleColor(ContextCompat.getColor(this@Mainpage, R.color.warning))
                    setDrawCircles(false)
                    lineWidth = 2f
                    mode = com.github.mikephil.charting.data.LineDataSet.Mode.CUBIC_BEZIER
                }

                // Configure the chart
                gasDataChart.apply {
                    data = com.github.mikephil.charting.data.LineData(listOf(storageDataSet, flowRateDataSet))
                    description.isEnabled = false
                    legend.isEnabled = true

                    xAxis.apply {
                        valueFormatter = com.github.mikephil.charting.formatter.IndexAxisValueFormatter(timeLabels)
                        position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                        granularity = 1f
                        labelCount = 5
                    }

                    axisRight.isEnabled = false
                    animateX(1000)
                    invalidate()
                }
            }
            .addOnFailureListener { e ->
                showSnackbar("Failed to load gas data: ${e.message}")
            }
    }

}
data class StreamChangeEvent(
    val timestamp: String,
    val changeDescription: String,
    val initiatedBy: String
)
class StreamChangeAdapter(
    private var streamChanges: List<StreamChangeEvent>
) : RecyclerView.Adapter<StreamChangeAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val timestamp: TextView = view.findViewById(R.id.timestamp)
        val changeDescription: TextView = view.findViewById(R.id.changeDescription)
        val initiatedBy: TextView = view.findViewById(R.id.initiatedBy)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stream_change, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val event = streamChanges[position]
        holder.timestamp.text = event.timestamp
        holder.changeDescription.text = event.changeDescription
        holder.initiatedBy.text = "By: ${event.initiatedBy}"
    }

    override fun getItemCount() = streamChanges.size

    fun updateData(newStreamChanges: List<StreamChangeEvent>) {
        streamChanges = newStreamChanges
        notifyDataSetChanged()
    }
}

data class GasAdditionRecord(
    val timestamp: String,
    val companyName: String,
    val volume: String
)

class GasAdditionAdapter(
    private var gasAdditions: List<GasAdditionRecord>
) : RecyclerView.Adapter<GasAdditionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val timestamp: TextView = view.findViewById(R.id.timestamp)
        val companyName: TextView = view.findViewById(R.id.companyName)
        val volume: TextView = view.findViewById(R.id.volume)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gas_addition, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = gasAdditions[position]
        holder.timestamp.text = record.timestamp
        holder.companyName.text = record.companyName
        holder.volume.text = record.volume
    }

    override fun getItemCount() = gasAdditions.size

    fun updateData(newGasAdditions: List<GasAdditionRecord>) {
        gasAdditions = newGasAdditions
        notifyDataSetChanged()
    }
}

// Gas Storage data model and adapter
data class GasStorageData(
    val id: String,
    val name: String,
    val currentLevel: String,
    val capacityUsed: String,
    val status: String
)

class GasStorageAdapter(
    private var storageList: List<GasStorageData>,
    private val onItemClick: (GasStorageData) -> Unit
) : RecyclerView.Adapter<GasStorageAdapter.GasStorageViewHolder>() {

    class GasStorageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val storageName: TextView = view.findViewById(R.id.storageName)
        val storageLevel: TextView = view.findViewById(R.id.storageLevel)
        val storageCapacity: TextView = view.findViewById(R.id.storageCapacity)
        val storageStatus: TextView = view.findViewById(R.id.storageStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GasStorageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gas_storage, parent, false)
        return GasStorageViewHolder(view)
    }

    override fun onBindViewHolder(holder: GasStorageViewHolder, position: Int) {
        val storage = storageList[position]
        holder.storageName.text = storage.name
        holder.storageLevel.text = storage.currentLevel
        holder.storageCapacity.text = storage.capacityUsed
        holder.storageStatus.text = storage.status

        // Set status color
        val textColor = when (storage.status) {
            "Normal" -> holder.itemView.context.getColor(R.color.success)
            "Critical" -> holder.itemView.context.getColor(R.color.error)
            else -> holder.itemView.context.getColor(R.color.warning)
        }
        holder.storageStatus.setTextColor(textColor)

        // Set click listener
        holder.itemView.setOnClickListener { onItemClick(storage) }
    }

    override fun getItemCount() = storageList.size

    fun updateData(newStorageList: List<GasStorageData>) {
        storageList = newStorageList
        notifyDataSetChanged()
    }
}
