package com.example.gail

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import kotlin.random.Random

class gas_info : Fragment() {

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

    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 5000L
    private val twentyFourHoursInMillis = 24 * 60 * 60 * 1000L

    private var activeStream = Stream.A
    private enum class Stream { A, B }

    interface DataChangeListener {
        fun onDataChanged(flowRate: Double, millionCubicMeters: Double)
    }

    private var dataChangeListener: DataChangeListener? = null

    private var lastStreamChangeTime = System.currentTimeMillis()

    private data class StreamParameters(
        var pressure: Double = 8.5, // MPa
        var temperature: Double = 15.0, // °C
        var flowRate: Double = 2160000.0 // m³/h
    )

    private val streamAParams = StreamParameters()
    private val streamBParams = StreamParameters(
        pressure = 8.3,
        temperature = 14.5,
        flowRate = 2080000.0
    )

    private val periodicUpdateRunnable = object : Runnable {
        override fun run() {
            // Check if 24 hours have passed since last stream change
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastStreamChangeTime >= twentyFourHoursInMillis) {
                toggleActiveStream()
                lastStreamChangeTime = currentTime
            }

            updateStreamParameters()

            updateUI()

            val currentFlowRate = when (activeStream) {
                Stream.A -> streamAParams.flowRate
                Stream.B -> streamBParams.flowRate
            }
            val millionCubicMeters = currentFlowRate / 1_000_000 // Convert to million m³
            dataChangeListener?.onDataChanged(currentFlowRate, millionCubicMeters)

            handler.postDelayed(this, updateInterval)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.gas_info, container, false)
        initViews(view)
        return view
    }

    private fun initViews(view: View) {
        totalFlowRateValue = view.findViewById(R.id.totalFlowRateValue)
        activeStreamValue = view.findViewById(R.id.activeStreamValue)
        systemPressureValue = view.findViewById(R.id.systemPressureValue)

        streamACard = view.findViewById(R.id.streamACard)
        streamBCard = view.findViewById(R.id.streamBCard)
        streamAStatus = view.findViewById(R.id.streamAStatus)
        streamBStatus = view.findViewById(R.id.streamBStatus)

        streamAPressure = view.findViewById(R.id.streamAPressure)
        streamAFlowRate = view.findViewById(R.id.streamAFlowRate)
        streamBPressure = view.findViewById(R.id.streamBPressure)
        streamBFlowRate = view.findViewById(R.id.streamBFlowRate)

        streamACard.setOnClickListener { toggleActiveStream() }
        streamBCard.setOnClickListener { toggleActiveStream() }

        updateUI()
    }

    fun toggleActiveStream() {
        activeStream = when (activeStream) {
            Stream.A -> Stream.B
            Stream.B -> Stream.A
        }
        lastStreamChangeTime = System.currentTimeMillis()
        updateUI()
    }

    private fun updateStreamParameters() {
        val currentParams = when (activeStream) {
            Stream.A -> streamAParams
            Stream.B -> streamBParams
        }

        // Apply small random variations
        currentParams.pressure += Random.Default.nextDouble(-0.1, 0.1)
        currentParams.pressure = currentParams.pressure.coerceIn(8.0, 9.0)

        currentParams.flowRate += Random.Default.nextDouble(-50000.0, 50000.0)
        currentParams.flowRate = currentParams.flowRate.coerceIn(2000000.0, 2300000.0)
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
        streamAPressure.text = String.format("%.2f MPa",
            if (activeStream == Stream.A) activeParams.pressure else inactiveParams.pressure)
        streamAFlowRate.text = String.format("%.1f m³/h",
            if (activeStream == Stream.A) activeParams.flowRate else inactiveParams.flowRate)

        streamBStatus.text = if (activeStream == Stream.B) "Active" else "Inactive"
        streamBStatus.setChipBackgroundColorResource(
            if (activeStream == Stream.B) R.color.error_container
            else R.color.inactive_container
        )
        streamBPressure.text = String.format("%.2f MPa",
            if (activeStream == Stream.B) activeParams.pressure else inactiveParams.pressure)
        streamBFlowRate.text = String.format("%.1f m³/h",
            if (activeStream == Stream.B) activeParams.flowRate else inactiveParams.flowRate)
    }

    override fun onResume() {
        super.onResume()
        handler.post(periodicUpdateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(periodicUpdateRunnable)
    }

    fun setDataChangeListener(listener: DataChangeListener) {
        this.dataChangeListener = listener
    }
}