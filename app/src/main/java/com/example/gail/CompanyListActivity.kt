    package com.example.gail

    import android.os.Bundle
    import android.widget.TextView
    import android.widget.Toast
    import androidx.appcompat.app.AppCompatActivity
    import androidx.recyclerview.widget.LinearLayoutManager
    import androidx.recyclerview.widget.RecyclerView
    import com.google.firebase.firestore.FirebaseFirestore

    class CompanyListActivity : AppCompatActivity() {

        private lateinit var companyRecyclerView: RecyclerView
        private lateinit var companyAdapter: CompanyAdapter
        private lateinit var firestore: FirebaseFirestore

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_company_list)

            firestore = FirebaseFirestore.getInstance()
            companyRecyclerView = findViewById(R.id.companyRecyclerView)

            setupRecyclerView()
            fetchCompanies()
        }

        private fun setupRecyclerView() {
            companyAdapter = CompanyAdapter(emptyList())
            companyRecyclerView.apply {
                layoutManager = LinearLayoutManager(this@CompanyListActivity)
                adapter = companyAdapter
            }
        }

        private fun fetchCompanies() {
            firestore.collection("companies")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Toast.makeText(this, "Error loading companies: ${error.message}", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }

                    val companies = snapshots?.map { doc ->
                        CompanyModel(
                            id = doc.id,
                            name = doc.getString("name") ?: "Unknown",
                            dailyExportQuota = doc.getDouble("dailyExportQuota")?.toInt() ?: 0,
                            currentBalance = doc.getDouble("currentBalance")?.toInt() ?: 0,
                            status = doc.getString("status") ?: "inactive",
                            revenue = doc.getDouble("revenue") ?: 0.0
                        )
                    } ?: emptyList()

                    companyAdapter.updateData(companies)
                }
        }
    }
    data class CompanyModel(
        val id: String,
        val name: String,
        val dailyExportQuota: Int,
        val currentBalance: Int,
        val status: String,
        val revenue: Double = 0.0  // Added revenue field
    )

    class CompanyAdapter(
        private var companies: List<CompanyModel>
    ) : RecyclerView.Adapter<CompanyAdapter.CompanyViewHolder>() {

        class CompanyViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
            val companyName: TextView = view.findViewById(R.id.companyName)
            val dailyQuota: TextView = view.findViewById(R.id.dailyQuota)
            val currentBalance: TextView = view.findViewById(R.id.currentBalance)
            val companyStatus: TextView = view.findViewById(R.id.companyStatus)
            val companyRevenue: TextView = view.findViewById(R.id.companyRevenue) // New TextView for revenue
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): CompanyViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_company, parent, false)
            return CompanyViewHolder(view)
        }

        override fun onBindViewHolder(holder: CompanyViewHolder, position: Int) {
            val company = companies[position]
            holder.companyName.text = company.name
            holder.dailyQuota.text = "Daily quota: ${company.dailyExportQuota} MMSCM"
            holder.currentBalance.text = "Total shared: ${company.currentBalance} MMSCM"
            holder.companyStatus.text = company.status.capitalize()
            holder.companyRevenue.text = "Revenue: ₹ ${String.format("%.2f", company.revenue / 10000000)} Cr" // Display revenue in crores
        }

        override fun getItemCount() = companies.size

        fun updateData(newCompanies: List<CompanyModel>) {
            companies = newCompanies
            notifyDataSetChanged()
        }
    }