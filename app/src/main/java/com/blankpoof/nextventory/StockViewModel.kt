package com.blankpoof.nextventory

import androidx.lifecycle.ViewModel

class StockViewModel : ViewModel() {
    val stockItems = mutableListOf<StockItem>()
}
