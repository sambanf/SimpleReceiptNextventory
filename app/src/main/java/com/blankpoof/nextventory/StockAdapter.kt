package com.blankpoof.nextventory

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.blankpoof.nextventory.databinding.ItemStockBinding

class StockAdapter(
    private val items: List<StockItem>,
    private val onPrintQrClick: (StockItem) -> Unit
) : RecyclerView.Adapter<StockAdapter.StockViewHolder>() {

    class StockViewHolder(val binding: ItemStockBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StockViewHolder {
        val binding = ItemStockBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StockViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StockViewHolder, position: Int) {
        val item = items[position]
        holder.binding.textViewStockName.text = item.name
        holder.binding.textViewStockId.text = "ID: ${item.id}"
        holder.binding.textViewStockQuantity.text = "Qty: ${item.quantity}"
        holder.binding.textViewStockPrice.text = "Price: ${String.format("%.2f", item.price)}"
        
        holder.binding.buttonPrintQr.setOnClickListener {
            onPrintQrClick(item)
        }
    }

    override fun getItemCount(): Int = items.size
}