package com.example.testappcc.data.model

import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.MapboxDirections
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.geocoding.v5.MapboxGeocoding
import com.mapbox.api.geocoding.v5.models.CarmenFeature
import com.mapbox.api.geocoding.v5.models.GeocodingResponse
import com.mapbox.geojson.Point
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

fun getRouteFromAddress(
    originAddress: String,
    destinationAddress: String,
    mapboxAccessToken: String,
    onResult: (distanceMeters: Double?, durationSeconds: Double?, error: String?) -> Unit
) {
    // Step 1: Geocode origin address
    geocodeAddress(originAddress, mapboxAccessToken) { originLat, originLon, errorOrigin ->
        if (errorOrigin != null) {
            onResult(null, null, "Lỗi lấy tọa độ điểm bắt đầu: $errorOrigin")
            return@geocodeAddress
        }
        // Step 2: Geocode destination address
        geocodeAddress(destinationAddress, mapboxAccessToken) { destLat, destLon, errorDest ->
            if (errorDest != null) {
                onResult(null, null, "Lỗi lấy tọa độ điểm đến: $errorDest")
                return@geocodeAddress
            }
            if (originLat == null || originLon == null || destLat == null || destLon == null) {
                onResult(null, null, "Thiếu tọa độ điểm bắt đầu hoặc điểm đến")
                return@geocodeAddress
            }

            // Step 3: Gọi Directions API lấy đường đi
            val originPoint = Point.fromLngLat(originLon, originLat)
            val destPoint = Point.fromLngLat(destLon, destLat)

            val directionsClient = MapboxDirections.builder()
                .origin(originPoint)
                .destination(destPoint)
                .overview(DirectionsCriteria.OVERVIEW_FULL)
                .profile(DirectionsCriteria.PROFILE_DRIVING)
                .accessToken(mapboxAccessToken)
                .build()

            directionsClient.enqueueCall(object : Callback<DirectionsResponse> {
                override fun onResponse(
                    call: Call<DirectionsResponse>,
                    response: Response<DirectionsResponse>
                ) {
                    val route = response.body()?.routes()?.firstOrNull()
                    if (route != null) {
                        onResult(route.distance(), route.duration(), null)
                    } else {
                        onResult(null, null, "Không tìm thấy đường đi")
                    }
                }

                override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
                    onResult(null, null, "Lỗi Directions API: ${t.message}")
                }
            })
        }
    }
}

// Hàm phụ: geocode địa chỉ thành tọa độ
fun geocodeAddress(
    address: String,
    accessToken: String,
    callback: (latitude: Double?, longitude: Double?, error: String?) -> Unit
) {
    val geocoding = MapboxGeocoding.builder()
        .accessToken(accessToken)
        .query(address)
        .build()

    geocoding.enqueueCall(object : Callback<GeocodingResponse> {
        override fun onResponse(
            call: Call<GeocodingResponse>,
            response: Response<GeocodingResponse>
        ) {
            val results = response.body()?.features()
            if (!results.isNullOrEmpty()) {
                val firstResult: CarmenFeature = results[0]
                val point = firstResult.center()
                if (point != null) {
                    callback(point.latitude(), point.longitude(), null)
                } else {
                    callback(null, null, "Không lấy được tọa độ")
                }
            } else {
                callback(null, null, "Không tìm thấy kết quả cho địa chỉ")
            }
        }

        override fun onFailure(
            call: Call<GeocodingResponse>,
            t: Throwable
        ) {
            callback(null, null, t.message)
        }
    })
}
