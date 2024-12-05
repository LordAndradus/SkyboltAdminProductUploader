package edu.utsa.cs3443.adminproductuploader

data class Product(
    val id: String,
    val name: String,
    val category: String,
    val price: Float,
    val offerPercentage: Float? = null,
    val description: String? = null,
    val colors: List<Int>? = null,
    val special: Boolean? = false,
    val bestDeal: Boolean? = false,
    val bestProduct : Boolean? = false,
    val sizes: List<String>? = null,
    val images: List<String>
)
