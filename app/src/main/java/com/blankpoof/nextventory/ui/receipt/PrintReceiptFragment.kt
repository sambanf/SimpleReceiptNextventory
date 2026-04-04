package com.blankpoof.nextventory.ui.receipt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.blankpoof.nextventory.data.model.ReceiptItem
import com.blankpoof.nextventory.databinding.FragmentPrintreceiptBinding
import com.blankpoof.nextventory.databinding.ItemReceiptBinding
import java.io.InputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.concurrent.thread

class PrintReceiptFragment : Fragment() {

    private var _binding: FragmentPrintreceiptBinding? = null
    private val binding get() = _binding!!

    private val items = mutableListOf<ReceiptItem>()
    private val printerUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val requestBluetooth = 100
    private var editingIndex: Int? = null
    private val decimalFormat = DecimalFormat("#,###.##")
    private var selectedLogoUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedLogoUri = uri
            binding.storeLogo.setImageURI(uri)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPrintreceiptBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPriceFormatting()
        binding.storeLogo.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.addItemButton.setOnClickListener {
            val name = binding.itemText.text.toString()
            val qty = binding.qtyText.text.toString().toIntOrNull() ?: 0
            val price = binding.priceText.text.toString().replace(",", "").toDoubleOrNull() ?: 0.0
            if (name.isNotEmpty() && qty > 0) {
                val item = ReceiptItem(name, qty, price)
                if (editingIndex != null) {
                    items[editingIndex!!] = item
                    editingIndex = null
                    binding.addItemButton.text = "Add Item"
                } else {
                    items.add(item)
                }
            }
            binding.itemText.text.clear()
            binding.qtyText.text.clear()
            binding.priceText.text.clear()
            updatePreview()
        }
        binding.clearAllButton.setOnClickListener {
            items.clear()
            editingIndex = null
            binding.addItemButton.text = "Add Item"
            updatePreview()
        }
        binding.buttonFirst.setOnClickListener {
            val header = binding.headerText.text.toString()
            if (hasBluetoothPermission()) printReceipt(header) else requestBluetoothPermission()
        }
    }

    private fun setupPriceFormatting() {
        binding.priceText.addTextChangedListener(object : TextWatcher {
            private var current = ""
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.toString() != current) {
                    binding.priceText.removeTextChangedListener(this)
                    val cleanString = s.toString().replace(",", "")
                    if (cleanString.isNotEmpty()) {
                        try {
                            val parsed = cleanString.toDouble()
                            val formatted = decimalFormat.format(parsed)
                            current = formatted
                            binding.priceText.setText(formatted)
                            binding.priceText.setSelection(formatted.length)
                        } catch (e: Exception) {}
                    } else { current = "" }
                    binding.priceText.addTextChangedListener(this)
                }
            }
        })
    }

    private fun updatePreview() {
        binding.itemsContainer.removeAllViews()
        items.forEachIndexed { index, item ->
            val itemBinding = ItemReceiptBinding.inflate(layoutInflater, binding.itemsContainer, false)
            val total = item.qty * item.price
            itemBinding.itemDescription.text = "${index + 1}. ${item.name} x${item.qty} ${decimalFormat.format(total)}"
            itemBinding.editButton.setOnClickListener {
                editingIndex = index
                binding.itemText.setText(item.name)
                binding.qtyText.setText(item.qty.toString())
                binding.priceText.setText(decimalFormat.format(item.price))
                binding.addItemButton.text = "Update Item"
            }
            itemBinding.deleteButton.setOnClickListener {
                items.removeAt(index)
                if (editingIndex == index) {
                    editingIndex = null
                    binding.addItemButton.text = "Add Item"
                    binding.itemText.text.clear()
                    binding.qtyText.text.clear()
                    binding.priceText.text.clear()
                } else if (editingIndex != null && editingIndex!! > index) {
                    editingIndex = editingIndex!! - 1
                }
                updatePreview()
            }
            binding.itemsContainer.addView(itemBinding.root)
        }
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
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else { arrayOf(Manifest.permission.BLUETOOTH) }
        ActivityCompat.requestPermissions(requireActivity(), perms, requestBluetooth)
    }

    @SuppressLint("MissingPermission")
    private fun printReceipt(header: String) {
        thread {
            try {
                val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE)
                val printerAddress = sharedPref.getString("selected_printer_address", null)
                if (printerAddress == null) {
                    requireActivity().runOnUiThread { Toast.makeText(context, "Please select a printer in settings", Toast.LENGTH_SHORT).show() }
                    return@thread
                }

                val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val device = bluetoothManager.adapter?.getRemoteDevice(printerAddress) ?: return@thread
                val socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(printerUUID)
                socket.connect()
                val output = socket.outputStream

                // 1. HARD RESET & SETUP
                output.write(byteArrayOf(0x1B, 0x40)) // ESC @
                output.write(byteArrayOf(0x1C, 0x2E)) // FS . (Cancel Chinese mode)
                output.write(byteArrayOf(0x1B, 0x74, 0x00)) // ESC t 0 (Codepage 437)
                Thread.sleep(300)

                // 2. PRINT LOGO
                selectedLogoUri?.let { uri ->
                    val options = BitmapFactory.Options().apply { inSampleSize = 4 } // Load smaller
                    requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream, null, options)
                        if (bitmap != null) {
                            val targetWidth = 160 // Safe size
                            val printerBitmap = resizeAndGrayScale(bitmap, targetWidth)
                            val command = decodeBitmap(printerBitmap)
                            output.write(byteArrayOf(0x1B, 0x61, 0x01)) // Center
                            
                            val chunkSize = 256
                            var offset = 0
                            while (offset < command.size) {
                                val length = minOf(chunkSize, command.size - offset)
                                output.write(command, offset, length)
                                offset += length
                                Thread.sleep(100)
                            }
                            output.write(byteArrayOf(0x0A, 0x0A))
                            output.flush()
                            Thread.sleep(1000) // LONG delay for physical printing
                        }
                    }
                }

                // 3. RECOVERY RESET
                output.write(byteArrayOf(0x18)) // CAN
                output.write(byteArrayOf(0x1B, 0x40)) // ESC @
                Thread.sleep(500)

                // 4. HEADER
                output.write(byteArrayOf(0x1B, 0x61, 0x01)) // Center
                output.write("$header\r\n\r\n".toByteArray(Charsets.US_ASCII))
                output.write(byteArrayOf(0x1B, 0x61, 0x00)) // Left
                Thread.sleep(100)

                // 5. BODY
                val receipt = StringBuilder()
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                receipt.append("Date: ${sdf.format(Date())}\r\n")
                receipt.append("-".repeat(32) + "\r\n")
                receipt.append(String.format("%-10s %3s %8s %8s\r\n", "Item", "Qty", "Price", "Total"))

                var subtotal = 0.0
                for (i in items) {
                    val total = i.qty * i.price
                    subtotal += total
                    
                    // Improved Item Name Wrapping
                    val nameParts = i.name.chunked(10)
                    for (index in nameParts.indices) {
                        if (index == 0) {
                            // First line contains Qty, Price, Total
                            receipt.append(String.format("%-10s %3d %8s %8s\r\n", nameParts[index], i.qty, decimalFormat.format(i.price), decimalFormat.format(total)))
                        } else {
                            // Subsequent lines only contain the rest of the name
                            receipt.append(String.format("%-10s\r\n", nameParts[index]))
                        }
                    }
                }
                receipt.append("-".repeat(32) + "\r\n")
                receipt.append(String.format("%-23s %8s\r\n", "Grand Total:", decimalFormat.format(subtotal)))
                receipt.append("\r\nThank you!\r\n\r\n\r\n\r\n\r\n")

                val textBytes = receipt.toString().toByteArray(Charsets.US_ASCII)
                var tOffset = 0
                while (tOffset < textBytes.size) {
                    val length = minOf(512, textBytes.size - tOffset)
                    output.write(textBytes, tOffset, length)
                    tOffset += length
                    Thread.sleep(80)
                }

                // 6. FINAL SHUTDOWN
                output.write(byteArrayOf(0x1B, 0x40))
                output.flush()
                Thread.sleep(2000)
                socket.close()

            } catch (e: Exception) {
                requireActivity().runOnUiThread { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun resizeAndGrayScale(bm: Bitmap, newWidth: Int): Bitmap {
        val width = bm.width
        val height = bm.height
        val scale = newWidth.toFloat() / width
        val matrix = Matrix().apply { postScale(scale, scale) }
        val resized = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, false)
        val gray = Bitmap.createBitmap(resized.width, resized.height, Bitmap.Config.RGB_565)
        val canvas = Canvas(gray)
        val paint = android.graphics.Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(0f) })
        }
        canvas.drawBitmap(resized, 0f, 0f, paint)
        return gray
    }

    private fun decodeBitmap(bmp: Bitmap): ByteArray {
        val width = bmp.width
        val height = bmp.height
        val widthInBytes = (width + 7) / 8
        val bwPx = IntArray(width * height)
        bmp.getPixels(bwPx, 0, width, 0, 0, width, height)
        val data = mutableListOf<Byte>()
        data.add(0x1D.toByte()); data.add(0x76.toByte()); data.add(0x30.toByte()); data.add(0.toByte())
        data.add((widthInBytes % 256).toByte()); data.add((widthInBytes / 256).toByte())
        data.add((height % 256).toByte()); data.add((height / 256).toByte())
        for (i in 0 until height) {
            for (j in 0 until widthInBytes) {
                var byteVal = 0
                for (k in 0 until 8) {
                    val x = j * 8 + k
                    if (x < width) {
                        val pixel = bwPx[i * width + x]
                        val gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                        if (gray < 128) byteVal = byteVal or (1 shl (7 - k))
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
