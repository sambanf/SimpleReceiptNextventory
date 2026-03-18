package com.blankpoof.nextventory

import java.util.UUID

data class StockItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val quantity: Int,
    val price: Double
)