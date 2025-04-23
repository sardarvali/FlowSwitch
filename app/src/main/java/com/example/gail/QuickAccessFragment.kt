package com.example.gail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class QuickAccessFragment : Fragment() {

    private var isStreamAActive = true
    private lateinit var flowRateValue: TextView
    private lateinit var pressureValue: TextView
    private lateinit var streamAStatus: Chip
    private lateinit var streamBStatus: Chip
    private lateinit var btnManualOverride: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.quick_access_layout, container, false)

        flowRateValue = view.findViewById(R.id.flowRateValue)
        pressureValue = view.findViewById(R.id.pressureValue)
        streamAStatus = view.findViewById(R.id.streamAStatus)
        streamBStatus = view.findViewById(R.id.streamBStatus)
        btnManualOverride = view.findViewById(R.id.btnManualOverride)

        startDataUpdates()
        btnManualOverride.setOnClickListener {
            showManualOverrideDialog()
        }

        return view
    }

    private fun startDataUpdates() {
        lifecycleScope.launch {
            while (true) {
                updateSystemData()
                delay(1000)
            }
        }
    }

    private fun updateSystemData() {
        val newFlowRate = String.format("%.1f", (Math.random() * 10))
        flowRateValue.text = "$newFlowRate L/s"

        val newPressure = String.format("%.1f", (Math.random() * 5))
        pressureValue.text = "$newPressure bar"

        updateStreamStatus()
    }

    private fun updateStreamStatus() {
        streamAStatus.text = if (isStreamAActive) "Active" else "Inactive"
        streamAStatus.setChipBackgroundColorResource(
            if (isStreamAActive) R.color.success else R.color.inactive
        )

        streamBStatus.text = if (!isStreamAActive) "Active" else "Inactive"
        streamBStatus.setChipBackgroundColorResource(
            if (!isStreamAActive) R.color.success else R.color.inactive
        )
    }

    private fun showManualOverrideDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("You Want Override")
            .setMessage("Are you sure you want to change Stream?")
            .setPositiveButton("Confirm") { _, _ ->
                isStreamAActive = !isStreamAActive
                updateStreamStatus()
                Toast.makeText(requireContext(), "Stream Changed", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


}