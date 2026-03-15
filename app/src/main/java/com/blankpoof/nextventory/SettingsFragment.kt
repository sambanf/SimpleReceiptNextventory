package com.blankpoof.nextventory

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.blankpoof.nextventory.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private var bluetoothAdapter: BluetoothAdapter? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                listPairedDevices()
            } else {
                Toast.makeText(context, "Permissions required for Bluetooth", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        binding.recyclerViewDevices.layoutManager = LinearLayoutManager(context)

        binding.buttonBluetooth.setOnClickListener {
            checkPermissionsAndListDevices()
        }
    }

    private fun checkPermissionsAndListDevices() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        if (permissions.all { ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED }) {
            listPairedDevices()
        } else {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    @SuppressLint("MissingPermission")
    private fun listPairedDevices() {
        if (bluetoothAdapter == null) {
            Toast.makeText(context, "Bluetooth not supported", Toast.LENGTH_SHORT).show()
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            Toast.makeText(context, "Please enable Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        val devicesList = pairedDevices?.toList() ?: emptyList()

        if (devicesList.isEmpty()) {
            Toast.makeText(context, "No paired devices found", Toast.LENGTH_SHORT).show()
        }

        binding.recyclerViewDevices.adapter = BluetoothDeviceAdapter(devicesList) { device ->
            onDeviceSelected(device)
        }
    }

    @SuppressLint("MissingPermission")
    private fun onDeviceSelected(device: BluetoothDevice) {
        Toast.makeText(context, "Selected: ${device.name} (${device.address})", Toast.LENGTH_SHORT).show()
        // Save selected device address to SharedPreferences for future use
        val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE) ?: return
        with(sharedPref.edit()) {
            putString("selected_printer_address", device.address)
            apply()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}