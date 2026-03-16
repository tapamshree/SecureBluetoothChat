package com.yourapp.securechat.ui.device

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yourapp.securechat.R
import com.yourapp.securechat.data.model.BluetoothDeviceModel
import com.yourapp.securechat.databinding.ItemDeviceBinding

/**
 * RecyclerView adapter for displaying [BluetoothDeviceModel] items.
 * Uses [ListAdapter] + [DiffUtil] for efficient list updates.
 */
class DeviceListAdapter(
    private val onDeviceClick: (BluetoothDeviceModel) -> Unit
) : ListAdapter<BluetoothDeviceModel, DeviceListAdapter.DeviceViewHolder>(DiffCallback) {

    // ── ViewHolder ────────────────────────────────────────────────────────────

    inner class DeviceViewHolder(
        private val binding: ItemDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: BluetoothDeviceModel) {
            binding.tvDeviceName.text = device.displayName
            binding.tvDeviceAddress.text = device.address

            binding.tvBondState.text = when (device.bondState) {
                BluetoothDeviceModel.BondState.BONDED  -> binding.root.context.getString(R.string.bond_paired)
                BluetoothDeviceModel.BondState.BONDING -> binding.root.context.getString(R.string.bond_pairing)
                BluetoothDeviceModel.BondState.NONE    -> binding.root.context.getString(R.string.bond_none)
            }

            binding.ivDeviceIcon.setImageResource(
                if (device.isPaired) R.drawable.ic_bluetooth_paired
                else R.drawable.ic_bluetooth_device
            )

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

    companion object DiffCallback : DiffUtil.ItemCallback<BluetoothDeviceModel>() {
        override fun areItemsTheSame(
            oldItem: BluetoothDeviceModel,
            newItem: BluetoothDeviceModel
        ): Boolean = oldItem.address == newItem.address

        override fun areContentsTheSame(
            oldItem: BluetoothDeviceModel,
            newItem: BluetoothDeviceModel
        ): Boolean = oldItem == newItem
    }
}
