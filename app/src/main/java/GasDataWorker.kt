package com.example.gail

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class GasDataWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val firestore = FirebaseFirestore.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override suspend fun doWork(): Result {
        try {
            Log.d("GasDataWorker", "Background work started")

            // Get current storage data
            val doc = firestore.collection("system_metrics").document("current").get().await()
            var totalStoredGas = doc.getDouble("current_storage") ?: 0.0
            var dailyImport = doc.getDouble("daily_import") ?: 0.0
            val storageCapacity = doc.getDouble("total_capacity") ?: 10000.0

            // Calculate gas import for 8 hour period
            // Using average flow rate of 4.2 million m³/h × 8 hours
            val baseFlowRate = 4200000.0
            val flowRate = baseFlowRate + Random.nextDouble(-100000.0, 100000.0)
            val hoursElapsed = 8.0
            val millionCubicMeters = (flowRate * hoursElapsed) / 1_000_000

            // Update storage values
            dailyImport += millionCubicMeters
            totalStoredGas += millionCubicMeters
            totalStoredGas = minOf(totalStoredGas, storageCapacity)

            // Calculate other metrics
            val pressure = 8.5 + Random.nextDouble(-0.3, 0.3)
            val temperature = 15.0 + Random.nextDouble(-2.0, 2.0)

            // Save current data
            firestore.collection("system_metrics").document("current")
                .update(
                    mapOf(
                        "current_storage" to totalStoredGas,
                        "daily_import" to dailyImport
                    )
                )

            // Save historical data point
            val gasData = hashMapOf(
                "timestamp" to com.google.firebase.Timestamp.now(),
                "stream" to if (Random.nextBoolean()) "A" else "B",
                "flowRate" to flowRate,
                "pressure" to pressure,
                "temperature" to temperature,
                "volume" to millionCubicMeters,
                "total_storage" to totalStoredGas,
                "daily_import" to dailyImport,
                "status" to "active"
            )

            firestore.collection("gas_storage").add(gasData)

            // Distribute gas to companies
            distributeGasToCompanies(millionCubicMeters)

            Log.d("GasDataWorker", "Background work completed successfully")
            return Result.success()

        } catch (e: Exception) {
            Log.e("GasDataWorker", "Background work failed: ${e.message}")
            return Result.retry()
        }
    }

    private suspend fun distributeGasToCompanies(gasImported: Double) {
        try {
            val documents = firestore.collection("companies")
                .whereEqualTo("status", "active")
                .get()
                .await()

            if (documents.isEmpty) return

            var totalQuota = 0.0
            val activeCompanies = mutableListOf<DocumentSnapshot>()

            // Calculate total quota
            for (document in documents) {
                val quota = document.getDouble("dailyExportQuota") ?: 0.0
                totalQuota += quota
                activeCompanies.add(document)
            }

            if (totalQuota <= 0) return

            // Use batch write to ensure all updates succeed or fail together
            val batch = firestore.batch()

            for (company in activeCompanies) {
                val companyRef = firestore.collection("companies").document(company.id)
                val companyName = company.getString("name") ?: "Unknown"
                val quota = company.getDouble("dailyExportQuota") ?: 0.0
                val currentBalance = company.getDouble("currentBalance") ?: 0.0

                val quotaPercentage = quota / totalQuota
                val gasShare = gasImported * quotaPercentage
                val newBalance = currentBalance + gasShare

                // Update company balance in batch
                batch.update(companyRef, "currentBalance", newBalance)

                // Create transaction log
                val transaction = hashMapOf(
                    "companyId" to company.id,
                    "companyName" to companyName,
                    "volume" to gasShare,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "status" to "completed",
                    "type" to "import",
                    "cost" to gasShare * 1.48e7,
                    "processedBy" to "system"
                )

                // Add transaction to batch
                batch.set(firestore.collection("transactions").document(), transaction)
            }

            // Commit the batch
            batch.commit()

        } catch (e: Exception) {
            Log.e("GasDataWorker", "Failed to distribute gas: ${e.message}")
        }
    }
}