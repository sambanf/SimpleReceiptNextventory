package com.blankpoof.nextventory

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.blankpoof.nextventory.databinding.FragmentFirstBinding
import java.util.UUID
import kotlin.concurrent.thread

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private val items = mutableListOf<ReceiptItem>()

    private val printerUUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val REQUEST_BLUETOOTH = 100

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ADD ITEM
        binding.addItemButton.setOnClickListener {

            val name = binding.itemText.text.toString()
            val qty = binding.qtyText.text.toString().toIntOrNull() ?: 0
            val price = binding.priceText.text.toString().toDoubleOrNull() ?: 0.0

            if (name.isNotEmpty() && qty > 0) {
                items.add(ReceiptItem(name, qty, price))
            }

            binding.itemText.text.clear()
            binding.qtyText.text.clear()
            binding.priceText.text.clear()

            updatePreview()
        }

        // REMOVE ITEM
        binding.removeItemButton.setOnClickListener {

            val index = binding.removeIndexText.text.toString().toIntOrNull()

            if (index != null && index >= 0 && index < items.size) {
                items.removeAt(index)
                updatePreview()
            }

            binding.removeIndexText.text.clear()
        }

        // CLEAR ALL
        binding.clearAllButton.setOnClickListener {

            items.clear()
            updatePreview()
        }

        // PRINT RECEIPT
        binding.buttonFirst.setOnClickListener {

            val header = binding.headerText.text.toString()

            if (hasBluetoothPermission()) {
                printReceipt(header)
            } else {
                requestBluetoothPermission()
            }
        }
    }

    private fun updatePreview() {

        if (items.isEmpty()) {
            binding.itemListPreview.text = "No items yet"
            return
        }

        val builder = StringBuilder()

        items.forEachIndexed { index, item ->

            val total = item.qty * item.price

            builder.append(
                "$index. ${item.name} x${item.qty} ${"%.2f".format(total)}\n"
            )
        }

        binding.itemListPreview.text = builder.toString()
    }

    private fun hasBluetoothPermission(): Boolean {

        val connectPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

        val scanPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED

        return connectPermission && scanPermission
    }

    private fun requestBluetoothPermission() {

        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ),
            REQUEST_BLUETOOTH
        )
    }

    private fun printReceipt(header: String) {

        thread {

            try {

                val adapter = BluetoothAdapter.getDefaultAdapter()
                adapter?.cancelDiscovery()

                val device = adapter?.bondedDevices?.find {
                    it.name == "RPP02N"
                } ?: return@thread

                val socket: BluetoothSocket =
                    device.createRfcommSocketToServiceRecord(printerUUID)

                socket.connect()

                val output = socket.outputStream

                var subtotal = 0.0

                val receiptBuilder = StringBuilder()

                receiptBuilder.appendLine(header)
                receiptBuilder.appendLine("----------------")
                receiptBuilder.appendLine("Item  Qty  Total")

                for (i in items) {

                    val total = i.qty * i.price
                    subtotal += total

                    receiptBuilder.appendLine(
                        "${i.name}  ${i.qty}  ${"%.2f".format(total)}"
                    )
                }

                receiptBuilder.appendLine("----------------")
                receiptBuilder.appendLine("Subtotal: ${"%.2f".format(subtotal)}")

                val grandTotal = subtotal

                receiptBuilder.appendLine("Grand Total: ${"%.2f".format(grandTotal)}")
                receiptBuilder.appendLine("")
                receiptBuilder.appendLine("Thank you!")

                val receipt = receiptBuilder.toString()

                output.write(receipt.toByteArray())

                output.flush()
                output.close()
                socket.close()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}