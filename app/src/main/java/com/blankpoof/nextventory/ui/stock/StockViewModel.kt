package com.blankpoof.nextventory.ui.stock

import androidx.lifecycle.ViewModel
import com.blankpoof.nextventory.data.model.StockItem

class StockViewModel : ViewModel() {
    val stockItems = mutableListOf<StockItem>()
}
