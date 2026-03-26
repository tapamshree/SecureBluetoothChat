package com.yourapp.securechat.ui.device

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yourapp.securechat.data.model.BluetoothDeviceInfo
import com.yourapp.securechat.databinding.ItemDeviceBinding

/**
 * RecyclerView adapter for displaying [BluetoothDeviceInfo] items.
 * Uses [ListAdapter] + [DiffUtil] for efficient list updates.
 */
class DeviceListAdapter(
    private val onDeviceClick: (BluetoothDeviceInfo) -> Unit
) : ListAdapter<BluetoothDeviceInfo, DeviceListAdapter.DeviceViewHolder>(DiffCallback) {

    // ── ViewHolder ────────────────────────────────────────────────────────────

    inner class DeviceViewHolder(
        private val binding: ItemDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: BluetoothDeviceInfo) {
            binding.deviceName.text = device.displayName
            binding.deviceAddress.text = device.address
            binding.bondStateBadge.text = when (device.bondState) {
                BluetoothDeviceInfo.BondState.BONDED -> "Paired"
                BluetoothDeviceInfo.BondState.BONDING -> "Pairing..."
                BluetoothDeviceInfo.BondState.NONE -> ""
            }
            binding.bondStateBadge.visibility =
                if (device.bondState == BluetoothDeviceInfo.BondState.NONE) android.view.View.GONE
                else android.view.View.VISIBLE
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
