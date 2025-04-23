package com.example.gail

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.github.mikephil.charting.components.Legend
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import com.itextpdf.text.Document
import com.itextpdf.text.Paragraph
import com.itextpdf.text.pdf.PdfWriter
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import java.io.IOException

class company_reports : AppCompatActivity() {

    private lateinit var tabLayout: TabLayout
    private lateinit var dateRangeText: TextView
    private lateinit var btnExport: Button
    private lateinit var btnChangeDates: Button
    private lateinit var lineChart: LineChart
    private lateinit var pieChart: PieChart
    private lateinit var summaryText: TextView
    private lateinit var noDataText: TextView
    private lateinit var firestore: FirebaseFirestore
    private lateinit var mAuth: FirebaseAuth
    private lateinit var companyId: String
    private lateinit var companyName: String

    private var startDate = Calendar.getInstance().apply {
        add(Calendar.MONTH, -1)
    }.timeInMillis

    private var endDate = Calendar.getInstance().timeInMillis

    // Data storage for reports
    private var  consumptionData = mutableMapOf<String, Double>()
    private var distributionData = mutableMapOf<String, Double>()
    private var financialData = mutableMapOf<String, Double>()
    private var transactionHistory = mutableListOf<Map<String, Any>>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_company_reports)

        // Initialize Firebase
        firestore = FirebaseFirestore.getInstance()
        mAuth = FirebaseAuth.getInstance()

        // Get current company ID
        companyId = mAuth.currentUser?.uid ?: ""
        if (companyId.isEmpty()) {
            Snackbar.make(
                findViewById(android.R.id.content),
                "Error: Unable to identify company",
                Snackbar.LENGTH_LONG
            ).show()
            finish()
            return
        }

        // Fetch company name
        getCompanyDetails()

        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Gas Reports"

        initViews()
        setupTabLayout()
        updateDateRange()
        setupClickListeners()

        // Load initial data
        loadConsumptionData()
    }

    private fun getCompanyDetails() {
        firestore.collection("companies").document(companyId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    companyName = document.getString("name") ?: "Unknown Company"
                    supportActionBar?.subtitle = companyName
                }
            }
            .addOnFailureListener { e ->
                Log.e("CompanyReports", "Error fetching company details", e)
            }
    }

    private fun initViews() {
        tabLayout = findViewById(R.id.tabLayout)
        dateRangeText = findViewById(R.id.dateRangeText)
        btnExport = findViewById(R.id.btnExport)
        btnChangeDates = findViewById(R.id.btnChangeDates)
        lineChart = findViewById(R.id.lineChart)
        pieChart = findViewById(R.id.pieChart)
        summaryText = findViewById(R.id.summaryText)
        noDataText = findViewById(R.id.noDataText)

        // Initialize charts with better appearance
        setupChartsAppearance()
    }

    private fun setupChartsAppearance() {
        // Line chart setup
        lineChart.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setDrawBorders(false)
            setPinchZoom(true)
            isDoubleTapToZoomEnabled = true

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
            }

            axisLeft.apply {
                setDrawGridLines(true)
                setDrawZeroLine(true)
            }

            axisRight.isEnabled = false

            legend.apply {
                verticalAlignment = Legend.LegendVerticalAlignment.TOP
                horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
                orientation = Legend.LegendOrientation.VERTICAL
                setDrawInside(false)
            }
        }

        // Pie chart setup
        pieChart.apply {
            description.isEnabled = false
            setUsePercentValues(true)
            setDrawEntryLabels(false)
            holeRadius = 40f
            transparentCircleRadius = 45f
            setHoleColor(Color.WHITE)
            centerText = "Distribution"
            setCenterTextSize(12f)

            legend.apply {
                verticalAlignment = Legend.LegendVerticalAlignment.CENTER
                horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
                orientation = Legend.LegendOrientation.VERTICAL
                setDrawInside(false)
                xEntrySpace = 10f
                yEntrySpace = 5f
            }
        }
    }

    private fun setupTabLayout() {
        tabLayout.addTab(tabLayout.newTab().setText("Gas Consumption"))
        tabLayout.addTab(tabLayout.newTab().setText("Distribution"))
        tabLayout.addTab(tabLayout.newTab().setText("Financial"))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> loadConsumptionData()
                    1 -> loadDistributionData()
                    2 -> loadFinancialData()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateDateRange() {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        dateRangeText.text = "Period: ${dateFormat.format(Date(startDate))} to ${dateFormat.format(Date(endDate))}"
    }

    private fun setupClickListeners() {
        btnChangeDates.setOnClickListener {
            showDateRangePicker()
        }

        btnExport.setOnClickListener {
            showExportDialog()
        }
    }

    private fun showDateRangePicker() {
        val dateRangePicker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select Date Range")
            .setSelection(androidx.core.util.Pair(startDate, endDate))
            .build()

        dateRangePicker.addOnPositiveButtonClickListener {
            startDate = it.first
            endDate = it.second + 86400000
            updateDateRange()

            // Reload data based on current tab
            when (tabLayout.selectedTabPosition) {
                0 -> loadConsumptionData()
                1 -> loadDistributionData()
                2 -> loadFinancialData()
            }
        }

        dateRangePicker.show(supportFragmentManager, "DATE_PICKER")
    }

    private fun showExportDialog() {
        val reportTypes = arrayOf("PDF Report", "Excel Spreadsheet", "CSV Data", "Share Summary")

        MaterialAlertDialogBuilder(this)
            .setTitle("Export Report")
            .setItems(reportTypes) { _, which ->
                val reportType = reportTypes[which]
                generateReport(reportType)
            }
            .show()
    }

    private fun generateReport(reportType: String) {
        try {
            when (reportType) {
                "PDF Report" -> {
                    val tabName = when (tabLayout.selectedTabPosition) {
                        0 -> "Consumption"
                        1 -> "Distribution"
                        else -> "Financial"
                    }

                    // Create a simple PDF document
                    val pdfFileName = "${companyName}_${tabName}_Report.pdf"
                    val pdfFile = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), pdfFileName)

                    val document = Document()
                    PdfWriter.getInstance(document, FileOutputStream(pdfFile))
                    document.open()

                    // Add content
                    document.add(Paragraph("$companyName - $tabName Report"))
                    document.add(Paragraph("Period: ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(startDate))} to ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(endDate))}"))
                    document.add(Paragraph("\n"))

                    when (tabLayout.selectedTabPosition) {
                        0 -> {
                            document.add(Paragraph("Gas Consumption Data"))
                            for ((date, value) in consumptionData) {
                                document.add(Paragraph("$date: ${String.format("%.2f", value)} MMSCM"))
                            }
                        }
                        1 -> {
                            document.add(Paragraph("Gas Distribution Data"))
                            for ((sector, value) in distributionData) {
                                document.add(Paragraph("$sector: ${String.format("%.2f", value)} MMSCM"))
                            }
                        }
                        2 -> {
                            document.add(Paragraph("Financial Data"))
                            for ((date, value) in financialData) {
                                document.add(Paragraph("$date: ₹ ${String.format("%.2f", value/10000000)} Cr"))
                            }
                        }
                    }

                    document.close()

                    // Share the PDF
                    val uri = FileProvider.getUriForFile(this, "${packageName}.provider", pdfFile)
                    shareFile(uri, "application/pdf", "PDF Report")
                }

                "Excel Spreadsheet" -> {
                    // In a real implementation, you would use a library like Apache POI
                    // For demo purposes, we'll just show a message
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        "Excel export would be implemented with Apache POI in a production app",
                        Snackbar.LENGTH_LONG
                    ).show()
                }

                "CSV Data" -> {
                    val tabName = when (tabLayout.selectedTabPosition) {
                        0 -> "Consumption"
                        1 -> "Distribution"
                        else -> "Financial"
                    }

                    // Create a simple CSV file
                    val csvFileName = "${companyName}_${tabName}_Report.csv"
                    val csvFile = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), csvFileName)
                    val fos = FileOutputStream(csvFile)

                    // Write header and data
                    when (tabLayout.selectedTabPosition) {
                        0 -> {
                            fos.write("Date,Consumption (MMSCM)\n".toByteArray())
                            for ((date, value) in consumptionData) {
                                fos.write("$date,${String.format("%.2f", value)}\n".toByteArray())
                            }
                        }
                        1 -> {
                            fos.write("Sector,Volume (MMSCM)\n".toByteArray())
                            for ((sector, value) in distributionData) {
                                fos.write("$sector,${String.format("%.2f", value)}\n".toByteArray())
                            }
                        }
                        2 -> {
                            fos.write("Date,Amount (Cr)\n".toByteArray())
                            for ((date, value) in financialData) {
                                fos.write("$date,${String.format("%.2f", value/10000000)}\n".toByteArray())
                            }
                        }
                    }

                    fos.close()

                    // Share the CSV
                    val uri = FileProvider.getUriForFile(this, "${packageName}.provider", csvFile)
                    shareFile(uri, "text/csv", "CSV Data")
                }

                "Share Summary" -> {
                    val tabName = when (tabLayout.selectedTabPosition) {
                        0 -> "Gas Consumption"
                        1 -> "Gas Distribution"
                        else -> "Financial"
                    }

                    // Create a summary text
                    val summary = StringBuilder()
                    summary.append("$companyName - $tabName Report\n")
                    summary.append("Period: ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(startDate))} to ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(endDate))}\n\n")

                    when (tabLayout.selectedTabPosition) {
                        0 -> {
                            val totalConsumption = consumptionData.values.sum()
                            summary.append("Total Gas Consumed: ${String.format("%.2f", totalConsumption)} MMSCM\n")
                            summary.append("Average Monthly Consumption: ${String.format("%.2f", totalConsumption / if (consumptionData.size > 0) consumptionData.size else 1)} MMSCM\n")
                        }
                        1 -> {
                            val totalDistribution = distributionData.values.sum()
                            summary.append("Total Gas Distributed: ${String.format("%.2f", totalDistribution)} MMSCM\n")
                            summary.append("Distribution by Sector:\n")
                            for ((sector, value) in distributionData) {
                                val percentage = (value / totalDistribution) * 100
                                summary.append("- $sector: ${String.format("%.2f", value)} MMSCM (${String.format("%.1f", percentage)}%)\n")
                            }
                        }
                        2 -> {
                            val totalRevenue = financialData.values.sum()
                            summary.append("Total Revenue: ₹ ${String.format("%.2f", totalRevenue/10000000)} Cr\n")
                            summary.append("Average Monthly Revenue: ₹ ${String.format("%.2f", totalRevenue / if (financialData.size > 0) financialData.size else 1 / 10000000)} Cr\n")
                        }
                    }

                    // Share the text summary
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, summary.toString())
                        type = "text/plain"
                    }
                    startActivity(Intent.createChooser(sendIntent, "Share Report Summary"))
                }
            }
        } catch (e: IOException) {
            Log.e("CompanyReports", "Error generating report", e)
            Snackbar.make(
                findViewById(android.R.id.content),
                "Error generating report: ${e.message}",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun shareFile(uri: Uri, mimeType: String, title: String) {
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            type = mimeType
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share $title"))
    }

    private fun loadConsumptionData() {
        lineChart.visibility = View.VISIBLE
        pieChart.visibility = View.GONE
        noDataText.visibility = View.GONE
        showLoading(true)

        if (startDate >= endDate) {
            showNoData("Invalid date range: Start date must be before end date")
            return
        }

        firestore.collection("gas_addition_records")
            .whereEqualTo("companyId", companyId)
            .whereGreaterThanOrEqualTo("timestamp", Timestamp(Date(startDate)))
            .whereLessThanOrEqualTo("timestamp", Timestamp(Date(endDate)))
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { records ->
                // Define monthlyData here
                val monthlyData = mutableMapOf<String, Double>()
                val dateFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

                for (doc in records) {
                    val timestamp = doc.getTimestamp("timestamp")
                    if (timestamp != null) {
                        val monthKey = dateFormat.format(timestamp.toDate())
                        val volume = doc.getDouble("volume") ?: 0.0
                        monthlyData[monthKey] = (monthlyData[monthKey] ?: 0.0) + volume
                    }
                }

                consumptionData = monthlyData

                // Create entries and months list
                val entries = ArrayList<Entry>()
                val months = ArrayList<String>()

                // Sort by date to ensure chronological order
                monthlyData.entries.sortedBy { it.key }.forEachIndexed { index, entry ->
                    entries.add(Entry(index.toFloat(), entry.value.toFloat()))
                    val monthYear = entry.key.split("-")
                    val monthName = getMonthName(monthYear[1])
                    months.add("$monthName ${monthYear[0]}")
                }

                if (entries.isEmpty()) {
                    showNoData("No consumption data available for the selected period")
                    return@addOnSuccessListener
                }

                val dataSet = LineDataSet(entries, "Gas Consumption (MMSCM)")
                dataSet.color = getColor(R.color.primary)
                dataSet.setCircleColor(getColor(R.color.primary))
                dataSet.lineWidth = 2f
                dataSet.circleRadius = 4f
                dataSet.setDrawCircleHole(false)
                dataSet.valueTextSize = 10f

                val lineData = LineData(dataSet)
                lineChart.data = lineData

                lineChart.xAxis.valueFormatter = IndexAxisValueFormatter(months)
                lineChart.animateX(1000)
                lineChart.invalidate()

                val totalConsumption = monthlyData.values.sum()
                val avgMonthlyConsumption = totalConsumption / monthlyData.size

                summaryText.text = "Total Gas Consumed: ${String.format("%.2f", totalConsumption)} MMSCM\n" +
                        "Average Monthly: ${String.format("%.2f", avgMonthlyConsumption)} MMSCM\n" +
                        "Number of imports: ${records.size()}"

                showLoading(false)
            }
            .addOnFailureListener { e ->
                if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                    showIndexCreationDialog(e.message ?: "Index required for this query")
                } else {
                    showNoData("Error loading data: ${e.message}")
                }
            }
    }

    private fun showIndexCreationDialog(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Index Required")
            .setMessage("This query requires a Firestore index to be created. Please contact your administrator to set this up.")
            .setPositiveButton("OK", null)
            .show()
    }


    private fun loadDistributionData() {
        lineChart.visibility = View.GONE
        pieChart.visibility = View.VISIBLE
        noDataText.visibility = View.GONE

        showLoading(true)

        // Look for transactions where this company was involved
        firestore.collection("transactions")
            .whereEqualTo("companyId", companyId)
            .whereGreaterThanOrEqualTo("timestamp", Timestamp(Date(startDate)))
            .whereLessThanOrEqualTo("timestamp", Timestamp(Date(endDate)))

            .get()
            .addOnSuccessListener { documents ->
                distributionData.clear()
                transactionHistory.clear()

                if (documents.isEmpty) {
                    showNoData("No distribution data available for the selected period")
                    return@addOnSuccessListener
                }

                // Group by sector or type
                val sectorData = mutableMapOf<String, Double>()

                for (doc in documents) {
                    val volume = doc.getDouble("volume") ?: 0.0
                    val type = doc.getString("type") ?: "Unknown"

                    // Store transaction for possible export
                    val transaction = mapOf(
                        "id" to doc.id,
                        "timestamp" to (doc.getTimestamp("timestamp")?.toDate() ?: Date()),
                        "volume" to volume,
                        "type" to type,
                        "status" to (doc.getString("status") ?: "Unknown")
                    )
                    transactionHistory.add(transaction)

                    // Group by transaction type (import/export)
                    sectorData[type] = (sectorData[type] ?: 0.0) + volume
                }

                // Store for export
                distributionData = sectorData

                // Create pie chart entries
                val entries = ArrayList<PieEntry>()
                val totalVolume = sectorData.values.sum()

                for ((key, value) in sectorData) {
                    // Only show sectors with significant volume (at least 1% of total)
                    if (value / totalVolume >= 0.01) {
                        entries.add(PieEntry(value.toFloat(), key))
                    }
                }

                if (entries.isEmpty()) {
                    showNoData("No significant distribution data available")
                    return@addOnSuccessListener
                }

                val dataSet = PieDataSet(entries, "Distribution by Type")
                dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
                dataSet.sliceSpace = 3f
                dataSet.selectionShift = 5f
                dataSet.valueTextSize = 12f
                dataSet.valueFormatter = PercentFormatter(pieChart)

                val pieData = PieData(dataSet)
                pieChart.data = pieData

                pieChart.animateY(1000)
                pieChart.invalidate()

                // Update summary
                val importVolume = sectorData["import"] ?: 0.0
                val exportVolume = sectorData["export"] ?: 0.0
                val netBalance = importVolume - exportVolume

                summaryText.text = "Total Transactions: ${documents.size()}\n" +
                        "Import Volume: ${String.format("%.2f", importVolume)} MMSCM\n" +
                        "Export Volume: ${String.format("%.2f", exportVolume)} MMSCM\n" +
                        "Net Balance: ${String.format("%.2f", netBalance)} MMSCM"

                showLoading(false)
            }
            .addOnFailureListener { e ->
                Log.e("CompanyReports", "Error loading distribution data", e)
                showNoData("Error loading data: ${e.message}")
            }
    }

    private fun loadFinancialData() {
        lineChart.visibility = View.VISIBLE
        pieChart.visibility = View.GONE
        noDataText.visibility = View.GONE

        showLoading(true)

        // Fetch both transactions and gas_addition_records to compute financial data
        val transactionsTask = firestore.collection("transactions")
            .whereEqualTo("companyId", companyId)
            .whereGreaterThan("timestamp", com.google.firebase.Timestamp(Date(startDate)))
            .whereLessThan("timestamp", com.google.firebase.Timestamp(Date(endDate)))
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()

        transactionsTask.addOnSuccessListener { documents ->
            if (documents.isEmpty) {
                showNoData("No financial data available for the selected period")
                return@addOnSuccessListener
            }

            // Group financial data by month
            val monthlyData = mutableMapOf<String, Double>()
            val dateFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

            for (doc in documents) {
                val timestamp = doc.getTimestamp("timestamp")
                if (timestamp != null) {
                    val monthKey = dateFormat.format(timestamp.toDate())
                    val cost = doc.getDouble("cost") ?: 0.0
                    monthlyData[monthKey] = (monthlyData[monthKey] ?: 0.0) + cost
                }
            }

            // Store for export
            financialData = monthlyData

            // Setup chart data
            val entries = ArrayList<Entry>()
            val months = ArrayList<String>()

            monthlyData.entries.sortedBy { it.key }.forEachIndexed { index, entry ->
                // Convert to Crores for easier reading
                entries.add(Entry(index.toFloat(), (entry.value / 10000000).toFloat()))
                months.add(getMonthName(entry.key.split("-")[1]))
            }

            if (entries.isEmpty()) {
                showNoData("No financial data available for the selected period")
                return@addOnSuccessListener
            }

            val dataSet = LineDataSet(entries, "Cost (Cr)")
            dataSet.color = getColor(R.color.error)
            dataSet.setCircleColor(getColor(R.color.error))
            dataSet.lineWidth = 2f
            dataSet.circleRadius = 4f
            dataSet.valueTextSize = 10f

            val lineData = LineData(dataSet)
            lineChart.data = lineData

            lineChart.xAxis.valueFormatter = IndexAxisValueFormatter(months)
            lineChart.animateX(1000)
            lineChart.invalidate()

            // Update summary
            val totalCost = monthlyData.values.sum()
            val avgMonthlyCost = totalCost / monthlyData.size

            summaryText.text = "Total Expenditure: ₹ ${String.format("%.2f", totalCost/10000000)} Cr\n" +
                    "Monthly Average: ₹ ${String.format("%.2f", avgMonthlyCost/10000000)} Cr\n" +
                    "Number of Transactions: ${documents.size()}"

            showLoading(false)
        }
            .addOnFailureListener { e ->
                Log.e("CompanyReports", "Error loading financial data", e)
                showNoData("Error loading data: ${e.message}")
            }
    }

    private fun showLoading(isLoading: Boolean) {
        val loadingView = findViewById<View>(R.id.loadingView)
        if (isLoading) {
            loadingView.visibility = View.VISIBLE
            lineChart.visibility = View.GONE
            pieChart.visibility = View.GONE
            summaryText.visibility = View.GONE
            noDataText.visibility = View.GONE
        } else {
            loadingView.visibility = View.GONE
            summaryText.visibility = View.VISIBLE
        }
    }

    private fun showNoData(message: String) {
        showLoading(false)
        lineChart.visibility = View.GONE
        pieChart.visibility = View.GONE
        summaryText.visibility = View.GONE
        noDataText.visibility = View.VISIBLE
        noDataText.text = message
    }

    private fun getMonthName(month: String): String {
        return when (month) {
            "01" -> "Jan"; "02" -> "Feb"; "03" -> "Mar"; "04" -> "Apr"
            "05" -> "May"; "06" -> "Jun"; "07" -> "Jul"; "08" -> "Aug"
            "09" -> "Sep"; "10" -> "Oct"; "11" -> "Nov"; "12" -> "Dec"
            else -> month
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}