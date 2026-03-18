package com.blankpoof.nextventory.ui.stock

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
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.blankpoof.nextventory.R
import com.blankpoof.nextventory.data.model.StockItem
import com.blankpoof.nextventory.databinding.FragmentStockManagementBinding
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject
import java.util.UUID
import kotlin.concurrent.thread

class StockManagementFragment : Fragment() {

    private var _binding: FragmentStockManagementBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StockViewModel by activityViewModels()
    private lateinit var adapter: StockAdapter

    private val printerUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val requestBluetooth = 100

    private var isFabMenuOpen = false

    private val barcodeLauncher: ActivityResultLauncher<ScanOptions> = registerForActivityResult(
        ScanContract()
    ) { result ->
        if (result.contents != null) {
            Handler(Looper.getMainLooper()).postDelayed({
                processScanResult(result.contents)
            }, 500)
        }
    }

    private fun processScanResult(contents: String) {
        try {
            val json = JSONObject(contents)
            val id = json.optString("id", "")
            val name = json.optString("name", "")
            val qty = json.optInt("qty", 0)
            val price = json.optDouble("price", 0.0)
            
            if (id.isNotEmpty()) {
                val existingItem = viewModel.stockItems.find { it.id == id }
                if (existingItem != null) {
                    showEditItemDialog(existingItem)
                } else {
                    showAddItemDialog(id, name, qty, price)
                }
            } else {
                showAddItemDialog(initialName = name, initialQty = qty, initialPrice = price)
            }
        } catch (e: Exception) {
            val rawValue = contents
            val existingItem = viewModel.stockItems.find { it.id == rawValue }
            if (existingItem != null) {
                showEditItemDialog(existingItem)
            } else {
                showAddItemDialog(initialName = rawValue)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStockManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = StockAdapter(
            items = viewModel.stockItems,
            onPrintQrClick = { item ->
                if (hasBluetoothPermission()) {
                    printQrCode(item)
                } else {
                    requestBluetoothPermission()
                }
            },
            onEditClick = { item ->
                showEditItemDialog(item)
            }
        )
        binding.recyclerViewStock.adapter = adapter

        setupFabMenu()
    }

    private fun setupFabMenu() {
        binding.fabMain.setOnClickListener {
            if (isFabMenuOpen) closeFabMenu() else openFabMenu()
        }

        binding.fabAddItemManual.setOnClickListener {
            closeFabMenu()
            showAddItemDialog()
        }

        binding.fabScanQr.setOnClickListener {
            closeFabMenu()
            startQrScanner()
        }
    }

    private fun openFabMenu() {
        isFabMenuOpen = true
        binding.fabMain.animate().rotation(45f)
        binding.fabAddItemManual.visibility = View.VISIBLE
        binding.labelAddManual.visibility = View.VISIBLE
        binding.fabScanQr.visibility = View.VISIBLE
        binding.labelScanQr.visibility = View.VISIBLE
        
        binding.fabAddItemManual.animate().translationY(-resources.getDimension(R.dimen.standard_55))
        binding.labelAddManual.animate().translationY(-resources.getDimension(R.dimen.standard_55))
        binding.fabScanQr.animate().translationY(-resources.getDimension(R.dimen.standard_105))
        binding.labelScanQr.animate().translationY(-resources.getDimension(R.dimen.standard_105))
    }

    private fun closeFabMenu() {
        isFabMenuOpen = false
        binding.fabMain.animate().rotation(0f)
        binding.fabAddItemManual.animate().translationY(0f)
        binding.labelAddManual.animate().translationY(0f)
        binding.fabScanQr.animate().translationY(0f).withEndAction {
            if (!isFabMenuOpen) {
                binding.fabAddItemManual.visibility = View.GONE
                binding.labelAddManual.visibility = View.GONE
                binding.fabScanQr.visibility = View.GONE
                binding.labelScanQr.visibility = View.GONE
            }
        }
        binding.labelScanQr.animate().translationY(0f)
    }

    private fun startQrScanner() {
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        options.setPrompt("Scan Item QR Code")
        options.setBeepEnabled(false)
        options.setBarcodeImageEnabled(true)
        options.setOrientationLocked(true)
        barcodeLauncher.launch(options)
    }

    private fun showAddItemDialog(initialId: String? = null, initialName: String = "", initialQty: Int = 0, initialPrice: Double = 0.0) {
        if (!isAdded) return
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_stock_item, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.editTextItemName)
        val quantityEditText = dialogView.findViewById<EditText>(R.id.editTextItemQuantity)
        val priceEditText = dialogView.findViewById<EditText>(R.id.editTextItemPrice)

        nameEditText.setText(initialName)
        if (initialQty > 0) quantityEditText.setText(initialQty.toString())
        if (initialPrice > 0.0) priceEditText.setText(initialPrice.toString())

        AlertDialog.Builder(requireContext())
            .setTitle("Add Stock Item")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = nameEditText.text.toString()
                val quantity = quantityEditText.text.toString().toIntOrNull() ?: 0
                val price = priceEditText.text.toString().toDoubleOrNull() ?: 0.0

                if (name.isNotEmpty()) {
                    val newItem = StockItem(
                        id = initialId ?: UUID.randomUUID().toString(),
                        name = name,
                        quantity = quantity,
                        price = price
                    )
                    viewModel.stockItems.add(newItem)
                    adapter.notifyItemInserted(viewModel.stockItems.size - 1)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditItemDialog(item: StockItem) {
        if (!isAdded) return
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_stock_item, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.editTextItemName)
        val quantityEditText = dialogView.findViewById<EditText>(R.id.editTextItemQuantity)
        val priceEditText = dialogView.findViewById<EditText>(R.id.editTextItemPrice)

        nameEditText.setText(item.name)
        quantityEditText.setText(item.quantity.toString())
        priceEditText.setText(item.price.toString())

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Stock Item")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val name = nameEditText.text.toString()
                val quantity = quantityEditText.text.toString().toIntOrNull() ?: 0
                val price = priceEditText.text.toString().toDoubleOrNull() ?: 0.0

                if (name.isNotEmpty()) {
                    val index = viewModel.stockItems.indexOf(item)
                    if (index != -1) {
                        viewModel.stockItems[index] = item.copy(name = name, quantity = quantity, price = price)
                        adapter.notifyItemChanged(index)
                    }
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
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH)
        }
        ActivityCompat.requestPermissions(requireActivity(), perms, requestBluetooth)
    }

    @SuppressLint("MissingPermission")
    private fun printQrCode(item: StockItem) {
        thread {
            try {
                val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE)
                val printerAddress = sharedPref.getString("selected_printer_address", null) ?: return@thread

                val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val device = bluetoothManager.adapter?.getRemoteDevice(printerAddress) ?: return@thread
                val socket = device.createRfcommSocketToServiceRecord(printerUUID)
                socket.connect()

                val output = socket.outputStream
                output.write(byteArrayOf(0x1B, 0x40))
                Thread.sleep(150)

                val qrData = JSONObject().apply { put("id", item.id); put("name", item.name) }.toString()
                val qrBitmap = generateQrCode(qrData, 256)
                
                if (qrBitmap != null) {
                    val command = decodeBitmap(qrBitmap)
                    output.write(byteArrayOf(0x1B, 0x61, 0x01))
                    
                    val chunkSize = 512
                    var offset = 0
                    while (offset < command.size) {
                        val length = minOf(chunkSize, command.size - offset)
                        output.write(command, offset, length)
                        output.flush()
                        offset += length
                        Thread.sleep(60)
                    }
                    
                    output.write(byteArrayOf(0x0A))
                    output.write(byteArrayOf(0x1B, 0x61, 0x00))
                    output.write("${item.name}\nID: ${item.id}\n\n\n".toByteArray())
                }

                output.write(byteArrayOf(0x1B, 0x40))
                output.flush()
                Thread.sleep(1500)
                socket.close()
            } catch (e: Exception) {
                requireActivity().runOnUiThread { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun generateQrCode(text: String, size: Int): Bitmap? {
        return try {
            val bitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
            val pixels = IntArray(size * size)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    pixels[y * size + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply { setPixels(pixels, 0, size, 0, 0, size, size) }
        } catch (e: Exception) { null }
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
