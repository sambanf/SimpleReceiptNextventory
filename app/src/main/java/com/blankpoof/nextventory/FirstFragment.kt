package com.blankpoof.nextventory

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
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
import com.blankpoof.nextventory.databinding.FragmentFirstBinding
import com.blankpoof.nextventory.databinding.ItemReceiptBinding
import java.io.InputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.concurrent.thread

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private val items = mutableListOf<ReceiptItem>()

    private val printerUUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val requestBluetooth = 100

    private var editingIndex: Int? = null

    // Format for thousands separator
    private val decimalFormat = DecimalFormat("#,###.##")

    private var selectedLogoUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedLogoUri = uri
            binding.storeLogo.setImageURI(uri)
        }
    }

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

        setupPriceFormatting()

        binding.storeLogo.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // ADD OR UPDATE ITEM
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

        // CLEAR ALL
        binding.clearAllButton.setOnClickListener {

            items.clear()
            editingIndex = null
            binding.addItemButton.text = "Add Item"
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
                        } catch (e: Exception) {
                            // Ignore
                        }
                    } else {
                        current = ""
                    }

                    binding.priceText.addTextChangedListener(this)
                }
            }
        })
    }

    private fun updatePreview() {
        binding.itemsContainer.removeAllViews()

        if (items.isEmpty()) {
            return
        }

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
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                ),
                requestBluetooth
            )
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.BLUETOOTH),
                requestBluetooth
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun printReceipt(header: String) {

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

                val bluetoothManager =
                    requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val adapter = bluetoothManager.adapter
                val device = adapter?.getRemoteDevice(printerAddress) ?: return@thread

                val socket: BluetoothSocket =
                    device.createRfcommSocketToServiceRecord(printerUUID)

                socket.connect()

                val output = socket.outputStream

                // Reset printer
                output.write(byteArrayOf(0x1B, 0x40))

                // Print logo if selected
                selectedLogoUri?.let { uri ->
                    val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) {
                        val printerBitmap = resizeAndGrayScale(bitmap, 384) // standard 58mm width
                        val command = decodeBitmap(printerBitmap)
                        output.write(byteArrayOf(0x1B, 0x61, 0x01)) // Center alignment
                        output.write(command)
                        output.write(byteArrayOf(0x0A)) // New line
                    }
                    inputStream?.close()
                }

                var subtotal = 0.0

                val receiptBuilder = StringBuilder()

                // Thermal printer width is usually 32 characters for standard fonts
                val lineLength = 32
                val itemColWidth = 10

                // Current date and time
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                val currentDateAndTime: String = sdf.format(Date())

                output.write(byteArrayOf(0x1B, 0x61, 0x01)) // Center alignment
                output.write("$header\n".toByteArray())
                output.write(byteArrayOf(0x1B, 0x61, 0x00)) // Left alignment
                
                receiptBuilder.appendLine("Date: $currentDateAndTime")
                receiptBuilder.appendLine("-".repeat(lineLength))
                
                val headerRow = String.format(
                    "%-10s %3s %8s %8s",
                    "Item", "Qty", "Price", "Total"
                )
                receiptBuilder.appendLine(headerRow)

                for (i in items) {
                    val total = i.qty * i.price
                    subtotal += total

                    val name = if (i.name.length > itemColWidth) i.name.substring(0, itemColWidth) else i.name
                    val formattedPrice = decimalFormat.format(i.price)
                    val formattedTotal = decimalFormat.format(total)
                    
                    val line = String.format(
                        "%-10s %3d %8s %8s",
                        name, i.qty, formattedPrice, formattedTotal
                    )
                    receiptBuilder.appendLine(line)
                }

                receiptBuilder.appendLine("-".repeat(lineLength))

                val formattedGrandTotal = decimalFormat.format(subtotal)
                val totalLine = String.format("%-23s %8s", "Grand Total:", formattedGrandTotal)
                receiptBuilder.appendLine(totalLine)
                
                receiptBuilder.appendLine("")
                receiptBuilder.appendLine("Thank you!")
                receiptBuilder.appendLine("\n\n")

                output.write(receiptBuilder.toString().toByteArray())

                output.flush()
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

    private fun resizeAndGrayScale(bm: Bitmap, newWidth: Int): Bitmap {
        val width = bm.width
        val height = bm.height
        val scaleWidth = newWidth.toFloat() / width
        val matrix = Matrix()
        matrix.postScale(scaleWidth, scaleWidth)
        val resizedBitmap = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, false)
        
        val grayBitmap = Bitmap.createBitmap(resizedBitmap.width, resizedBitmap.height, Bitmap.Config.RGB_565)
        val canvas = Canvas(grayBitmap)
        val paint = android.graphics.Paint()
        val colorMatrix = android.graphics.ColorMatrix()
        colorMatrix.setSaturation(0f)
        val filter = android.graphics.ColorMatrixColorFilter(colorMatrix)
        paint.colorFilter = filter
        canvas.drawBitmap(resizedBitmap, 0f, 0f, paint)
        return grayBitmap
    }

    private fun decodeBitmap(bmp: Bitmap): ByteArray {
        val width = bmp.width
        val height = bmp.height
        val bwPx = IntArray(width * height)
        bmp.getPixels(bwPx, 0, width, 0, 0, width, height)

        val data = mutableListOf<Byte>()
        // GS v 0 m xL xH yL yH d1...dk
        val m = 0
        val xL = (width / 8) % 256
        val xH = (width / 8) / 256
        val yL = height % 256
        val yH = height / 256

        data.add(0x1D.toByte())
        data.add(0x76.toByte())
        data.add(0x30.toByte())
        data.add(m.toByte())
        data.add(xL.toByte())
        data.add(xH.toByte())
        data.add(yL.toByte())
        data.add(yH.toByte())

        var byteVal = 0
        var bitIndex = 0
        for (i in 0 until height) {
            for (j in 0 until width) {
                val pixel = bwPx[i * width + j]
                val gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                if (gray < 128) {
                    byteVal = byteVal or (1 shl (7 - bitIndex))
                }
                bitIndex++
                if (bitIndex == 8) {
                    data.add(byteVal.toByte())
                    byteVal = 0
                    bitIndex = 0
                }
            }
        }
        return data.toByteArray()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}