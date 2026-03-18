package com.blankpoof.nextventory

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.blankpoof.nextventory.databinding.FragmentStockManagementBinding
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.util.UUID
import kotlin.concurrent.thread

class StockManagementFragment : Fragment() {

    private var _binding: FragmentStockManagementBinding? = null
    private val binding get() = _binding!!

    private val stockItems = mutableListOf<StockItem>()
    private lateinit var adapter: StockAdapter

    private val printerUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val requestBluetooth = 100

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStockManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = StockAdapter(stockItems) { item ->
            if (hasBluetoothPermission()) {
                printQrCode(item)
            } else {
                requestBluetoothPermission()
            }
        }
        binding.recyclerViewStock.adapter = adapter

        binding.fabAddItem.setOnClickListener {
            showAddItemDialog()
        }
    }

    private fun showAddItemDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_stock_item, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.editTextItemName)
        val quantityEditText = dialogView.findViewById<EditText>(R.id.editTextItemQuantity)
        val priceEditText = dialogView.findViewById<EditText>(R.id.editTextItemPrice)

        AlertDialog.Builder(context)
            .setTitle("Add Stock Item")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = nameEditText.text.toString()
                val quantity = quantityEditText.text.toString().toIntOrNull() ?: 0
                val price = priceEditText.text.toString().toDoubleOrNull() ?: 0.0

                if (name.isNotEmpty()) {
                    stockItems.add(StockItem(name = name, quantity = quantity, price = price))
                    adapter.notifyItemInserted(stockItems.size - 1)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), requestBluetooth)
        } else {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.BLUETOOTH), requestBluetooth)
        }
    }

    @SuppressLint("MissingPermission")
    private fun printQrCode(item: StockItem) {
        thread {
            try {
                val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE)
                val printerAddress = sharedPref.getString("selected_printer_address", null)

                if (printerAddress == null) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(context, "Please select a printer in settings first", Toast.LENGTH_SHORT).show()
                    }
                    return@thread
                }

                val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val adapter = bluetoothManager.adapter
                val device = adapter?.getRemoteDevice(printerAddress) ?: return@thread

                val socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(printerUUID)
                socket.connect()

                val output = socket.outputStream

                // 1. Initialize printer and wait
                output.write(byteArrayOf(0x1B, 0x40))
                Thread.sleep(200)

                // 2. Generate a slightly smaller QR Code for better compatibility
                val qrBitmap = generateQrCode(item.id, 256)
                if (qrBitmap != null) {
                    val command = decodeBitmap(qrBitmap)
                    
                    // 3. Center alignment
                    output.write(byteArrayOf(0x1B, 0x61, 0x01)) 
                    output.flush()
                    Thread.sleep(50)

                    // 4. Send the bitmap in larger chunks but with consistent delays
                    val chunkSize = 2048
                    var offset = 0
                    while (offset < command.size) {
                        val length = minOf(chunkSize, command.size - offset)
                        output.write(command, offset, length)
                        output.flush()
                        offset += length
                        Thread.sleep(50) // More generous delay for printer buffer
                    }
                    
                    // 5. Reset alignment and print text
                    output.write(byteArrayOf(0x1B, 0x61, 0x00)) // Left alignment
                    output.write(byteArrayOf(0x0A)) // New line
                    output.write("${item.name}\n".toByteArray())
                    output.write("ID: ${item.id}\n".toByteArray())
                    output.write(byteArrayOf(0x0A, 0x0A, 0x0A)) // Feed
                }

                // 6. Final reset and CRITICAL: Wait for printer to finish mechanical printing
                output.write(byteArrayOf(0x1B, 0x40))
                output.flush()
                Thread.sleep(2000) // 2 seconds delay to ensure data is processed

                output.close()
                socket.close()

            } catch (e: Exception) {
                e.printStackTrace()
                requireActivity().runOnUiThread {
                    Toast.makeText(context, "Printing failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun generateQrCode(text: String, size: Int): Bitmap? {
        return try {
            val bitMatrix: BitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    pixels[y * width + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun decodeBitmap(bmp: Bitmap): ByteArray {
        val width = bmp.width
        val height = bmp.height
        val widthInBytes = (width + 7) / 8
        val bwPx = IntArray(width * height)
        bmp.getPixels(bwPx, 0, width, 0, 0, width, height)

        val data = mutableListOf<Byte>()
        // GS v 0 m xL xH yL yH
        data.add(0x1D.toByte())
        data.add(0x76.toByte())
        data.add(0x30.toByte())
        data.add(0.toByte())
        data.add((widthInBytes % 256).toByte())
        data.add((widthInBytes / 256).toByte())
        data.add((height % 256).toByte())
        data.add((height / 256).toByte())

        for (i in 0 until height) {
            for (j in 0 until widthInBytes) {
                var byteVal = 0
                for (k in 0 until 8) {
                    val x = j * 8 + k
                    if (x < width) {
                        val pixel = bwPx[i * width + x]
                        val gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                        if (gray < 128) { // Black
                            byteVal = byteVal or (1 shl (7 - k))
                        }
                    }
                }
                data.add(byteVal.toByte())
            }
        }
        return data.toByteArray()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
