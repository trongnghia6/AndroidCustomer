package com.example.customerapp.core.network

import com.google.gson.annotations.SerializedName

// Mỗi feature có place_name và center (List<Double>)
data class MapboxPlace(
    @SerializedName("place_name")
    val placeName: String,
    @SerializedName("center")
    val center: List<Double>?
)

data class MapboxResponse(
    val features: List<MapboxPlace>
)
