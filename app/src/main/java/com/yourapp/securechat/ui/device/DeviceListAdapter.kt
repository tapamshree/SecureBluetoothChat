package com.yourapp.securechat.ui.device

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yourapp.securechat.data.model.BluetoothDeviceInfo
import com.yourapp.securechat.databinding.ItemDeviceBinding

/**
 * ============================================================================
 * FILE: DeviceListAdapter.kt
 * ============================================================================
 *
 * 1. PURPOSE OF THE FILE:
 * To render Bluetooth device rows in the paired/discovered RecyclerViews 
 * on the `DeviceListActivity` screen.
 *
 * 2. HOW IT WORKS:
 * Extends `ListAdapter` with `DiffUtil` for efficient list updates. Each row 
 * inflates `item_device.xml` and displays the device's display name and 
 * MAC address, with a click listener to initiate connection.
 *
 * 3. WHY IS IT IMPORTANT:
 * Provides the visual representation of available devices, enabling the user 
 * to identify and select a target for connection.
 *
 * 4. ROLE IN THE PROJECT:
 * Pure UI adapter bridging `DeviceViewModel`'s device lists to the RecyclerView.
 *
 * 5. WHAT DOES EACH PART DO:
 * - [DeviceViewHolder.bind()]: Populates name/address and wires the click callback.
 * - [DiffCallback]: Compares devices by MAC address for identity, full equality for content.
 * - [onDeviceClick]: Lambda callback fired when a device row is tapped.
 * ============================================================================
 */
class DeviceListAdapter(
    private val onDeviceClick: (BluetoothDeviceInfo) -> Unit
) : ListAdapter<BluetoothDeviceInfo, DeviceListAdapter.DeviceViewHolder>(DiffCallback) {

    // ── ViewHolder ────────────────────────────────────────────────────────────

    inner class DeviceViewHolder(
        private val binding: ItemDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: BluetoothDeviceInfo) {
            binding.textDeviceName.text = device.displayName
            binding.textDeviceAddress.text = device.address
            binding.root.setOnClickListener { onDeviceClick(device) }
        }
    }

    // ── Inflate ───────────────────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // ── DiffUtil ──────────────────────────────────────────────────────────────

    companion object DiffCallback : DiffUtil.ItemCallback<BluetoothDeviceInfo>() {
        override fun areItemsTheSame(
            oldItem: BluetoothDeviceInfo,
            newItem: BluetoothDeviceInfo
        ): Boolean = oldItem.address == newItem.address

        override fun areContentsTheSame(
            oldItem: BluetoothDeviceInfo,
            newItem: BluetoothDeviceInfo
        ): Boolean = oldItem == newItem
    }
}
