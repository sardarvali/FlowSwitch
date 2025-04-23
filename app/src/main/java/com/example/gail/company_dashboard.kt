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
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
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
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class company_dashboard : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    // UI Components
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var fabAddAgent: FloatingActionButton
    private lateinit var navView: NavigationView
    private lateinit var logout: Button
    private lateinit var btnReports: Button
    private lateinit var btnEmployees: Button
    private lateinit var totalConsumptionValue: TextView
    private lateinit var activeContractsValue: TextView
    private lateinit var revenueValue: TextView
    private lateinit var consumptionChart: BarChart
    private lateinit var capacityIndicator: CircularProgressIndicator
    private lateinit var capacityPercentage: TextView
    private lateinit var storageStatus: TextView
    private lateinit var emergencyCard: MaterialCardView
    private lateinit var agentRecyclerView: RecyclerView

    // Firebase
    private lateinit var mAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var dashboardListener: ListenerRegistration
    private lateinit var agentsListener: ListenerRegistration
    private lateinit var notificationsListener: ListenerRegistration

    // Constants
    private val EMERGENCY_CONTACT = "911"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_company_dashboard)

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Setup UI
        initializeViews()
        setupNavigation()
        setGreeting()
        setupClickListeners()

        // Load data
        setupRealTimeDashboardUpdates()
        setupRealTimeAgentsList()
        setupNotificationListener()
    }

    private fun initializeViews() {
        // Toolbar and Navigation
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)

        // Buttons and FABs
        fabAddAgent = findViewById(R.id.fabAddAgent)
        logout = findViewById(R.id.logout)
        btnReports = findViewById(R.id.btnReports)
        btnEmployees = findViewById(R.id.btnEmployees)

        // Dashboard Metrics
        totalConsumptionValue = findViewById(R.id.totalConsumptionValue)
        activeContractsValue = findViewById(R.id.activeContractsValue)
        revenueValue = findViewById(R.id.revenueValue)
        consumptionChart = findViewById(R.id.consumptionChart)
        capacityIndicator = findViewById(R.id.capacityIndicator)
        capacityPercentage = findViewById(R.id.capacityPercentage)
        storageStatus = findViewById(R.id.storageStatus)
        emergencyCard = findViewById(R.id.emergencyCard)
        agentRecyclerView = findViewById(R.id.agentRecyclerView)
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
            currentHour < 12 -> "Good Morning, Company Admin!"
            currentHour < 18 -> "Good Afternoon, Company Admin!"
            else -> "Good Evening, Company Admin!"
        }
        findViewById<TextView>(R.id.greetingtv).text = greeting
    }

    private fun setupClickListeners() {
        fabAddAgent.setOnClickListener {
            it.startAnimation(AnimationUtils.loadAnimation(this, R.anim.rotate))
            showAddAgentDialog()
        }

        logout.setOnClickListener {
            signOutUser()
        }

        btnReports.setOnClickListener {
            startActivity(Intent(this, company_reports::class.java))
        }

        btnEmployees.setOnClickListener {
            // Commented because it's not implemented yet
            // startActivity(Intent(this, CompanyEmployeeManagement::class.java))
            showSnackbar("Employee management coming soon")
        }

        emergencyCard.setOnClickListener {
            showEmergencyProtocolDialog()
        }
    }

    private fun setupRealTimeDashboardUpdates() {
        val companyId = mAuth.currentUser?.uid ?: return

        dashboardListener = firestore.collection("company_metrics")
            .document(companyId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    showSnackbar("Error loading dashboard data: ${error.message}")
                    return@addSnapshotListener
                }

                snapshot?.let { doc ->
                    val consumption = doc.getDouble("total_consumption") ?: 0.0
                    totalConsumptionValue.text = "%.1f MMSCM".format(consumption)

                    val contracts = doc.getLong("active_contracts")?.toInt() ?: 0
                    activeContractsValue.text = contracts.toString()

                    val revenue = doc.getDouble("monthly_revenue") ?: 0.0
                    revenueValue.text = "₹ %.2f Cr".format(revenue / 10000000)

                    val storageLevel = doc.getDouble("storage_level") ?: 0.0
                    val storageCapacity = doc.getDouble("storage_capacity") ?: 500.0
                    val percentage = (storageLevel / storageCapacity * 100).toInt()

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
            months.add(getMonthName(entry.key.split("-")[1]))
        }

        val dataSet = BarDataSet(entries, "Monthly Consumption (MMSCM)")
        dataSet.color = ContextCompat.getColor(this, R.color.primary)

        val data = BarData(dataSet).apply { barWidth = 0.6f }

        consumptionChart.apply {
            this.data = data
            description.isEnabled = false
            legend.isEnabled = true
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(months)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setCenterAxisLabels(false)
            }
            axisRight.isEnabled = false
            animateY(1000)
            invalidate()
        }
    }

    private fun getMonthName(monthKey: String): String {
        // Handle both formats: "2023-01" or just "01"
        val monthPart = if (monthKey.contains("-")) {
            monthKey.split("-")[1]
        } else {
            monthKey
        }

        return when (monthPart) {
            "01" -> "Jan"; "02" -> "Feb"; "03" -> "Mar"; "04" -> "Apr"
            "05" -> "May"; "06" -> "Jun"; "07" -> "Jul"; "08" -> "Aug"
            "09" -> "Sep"; "10" -> "Oct"; "11" -> "Nov"; "12" -> "Dec"
            else -> monthKey
        }
    }

    private fun setupRealTimeAgentsList() {
        val companyId = mAuth.currentUser?.uid ?: return

        agentsListener = firestore.collection("agents")
            .whereEqualTo("company_id", companyId)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    showSnackbar("Error loading agents: ${error.message}")
                    return@addSnapshotListener
                }

                val agents = snapshots?.map { doc ->
                    AgentModel(
                        id = doc.id,
                        name = doc.getString("name") ?: "Unknown Agent",
                        region = doc.getString("region") ?: "Unknown Region",
                        status = doc.getString("status") ?: "Inactive",
                        activeContracts = (doc.get("active_contracts") as? Number)?.toInt() ?: 0
                    )
                } ?: emptyList()

                agentRecyclerView.apply {
                    layoutManager = LinearLayoutManager(this@company_dashboard)
                    adapter = AgentAdapter(agents)
                }
            }
    }

    private fun setupNotificationListener() {
        val companyId = mAuth.currentUser?.uid ?: return

        notificationsListener = firestore.collection("notifications")
            .whereEqualTo("company_id", companyId)
            .whereEqualTo("is_read", false)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    showSnackbar("Error loading notifications: ${error.message}")
                    return@addSnapshotListener
                }

                snapshots?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val title = change.document.getString("title") ?: "New Notification"
                        val message = change.document.getString("message") ?: ""
                        showSnackbar("$title: $message")
                    }
                }
            }
    }

    private fun showAddAgentDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Add New Agent")
            .setView(R.layout.dialog_add_agent)
            .setPositiveButton("Add") { dialog, _ ->
                // In a real app, you would save the agent data to Firestore
                showSnackbar("New agent registration initiated")
            }
            .setNegativeButton("Cancel", null)
            .show()
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
                val companyId = mAuth.currentUser?.uid ?: return@setPositiveButton

                // Log emergency action
                val emergencyLog = hashMapOf(
                    "company_id" to companyId,
                    "action" to protocol,
                    "timestamp" to dateFormat.format(Date()),
                    "status" to "pending_confirmation"
                )

                firestore.collection("emergency_logs").add(emergencyLog)
                    .addOnSuccessListener {
                        showSnackbar("$protocol initiated - Confirmation pending")
                    }
                    .addOnFailureListener { e ->
                        showSnackbar("Failed to initiate protocol: ${e.message}")
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun contactEmergencyTeam() {
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$EMERGENCY_CONTACT")))
    }

    private fun signOutUser() {
        mAuth.signOut()
        getSharedPreferences("user_prefs", MODE_PRIVATE).edit()
            .putBoolean("is_logged_in", false)
            .apply()
        startActivity(Intent(this, Login_page::class.java))
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.company_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.nav_notifications -> {
                showNotificationsDialog()
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
            R.id.dashboard -> { /* Already here */ }
            // R.id.contracts -> startActivity(Intent(this, CompanyContracts::class.java))
            // R.id.agents -> startActivity(Intent(this, CompanyAgentManagement::class.java))
            R.id.reports -> startActivity(Intent(this, company_reports::class.java))
            R.id.settings -> showSettingsDialog()
        }
        drawerLayout.closeDrawers()
        return true
    }

    private fun showNotificationsDialog() {
        val companyId = mAuth.currentUser?.uid ?: return

        firestore.collection("notifications")
            .whereEqualTo("company_id", companyId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(10)
            .get()
            .addOnSuccessListener { documents ->
                val notifications = documents.map { doc ->
                    "${doc.getString("title")}: ${doc.getString("message")} (${doc.getString("timestamp")})"
                }.toTypedArray()

                MaterialAlertDialogBuilder(this)
                    .setTitle("Recent Notifications")
                    .setItems(notifications) { _, _ -> }
                    .setPositiveButton("Mark All Read") { _, _ ->
                        val batch = firestore.batch()
                        documents.forEach { doc ->
                            batch.update(doc.reference, "is_read", true)
                        }
                        batch.commit()
                    }
                    .show()
            }
    }

    private fun showSettingsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Company Settings")
            .setItems(arrayOf(
                "Account Management",
                "Notification Preferences",
                "Security Settings",
                "System Configuration",
                "About"
            )) { _, which ->
                when (which) {
                    // 0 -> startActivity(Intent(this, AccountManagement::class.java))
                    // 1 -> startActivity(Intent(this, NotificationSettings::class.java))
                    // 2 -> startActivity(Intent(this, SecuritySettings::class.java))
                    // 3 -> startActivity(Intent(this, SystemConfiguration::class.java))
                    4 -> showAboutDialog()
                    else -> showSnackbar("Feature coming soon")
                }
            }
            .show()
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("About")
            .setMessage("Gas Management System v1.0\n© 2023 GAIL Company")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        dashboardListener.remove()
        agentsListener.remove()
        notificationsListener.remove()
    }
}

data class AgentModel(
    val id: String,
    val name: String,
    val region: String,
    val status: String,
    val activeContracts: Int
)

class AgentAdapter(private val agents: List<AgentModel>) :
    RecyclerView.Adapter<AgentAdapter.AgentViewHolder>() {

    class AgentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.agentName)
        val region: TextView = view.findViewById(R.id.agentRegion)
        val status: TextView = view.findViewById(R.id.agentStatus)
        val contracts: TextView = view.findViewById(R.id.agentContracts)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AgentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_agent, parent, false)
        return AgentViewHolder(view)
    }

    override fun onBindViewHolder(holder: AgentViewHolder, position: Int) {
        val agent = agents[position]
        holder.name.text = agent.name
        holder.region.text = agent.region
        holder.status.text = agent.status
        holder.contracts.text = "${agent.activeContracts} contracts"

        val statusColor = when (agent.status) {
            "Active" -> R.color.success
            "Inactive" -> R.color.error
            else -> R.color.warning
        }
        holder.status.setTextColor(ContextCompat.getColor(holder.itemView.context, statusColor))
    }

    override fun getItemCount() = agents.size
}