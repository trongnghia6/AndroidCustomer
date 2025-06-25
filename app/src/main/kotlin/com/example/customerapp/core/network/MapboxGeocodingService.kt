package com.example.testappcc.core.network

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MapboxGeocodingService {
    @GET("geocoding/v5/mapbox.places/{longitude},{latitude}.json")
    suspend fun reverseGeocode(
        @Path("longitude") longitude: Double,
        @Path("latitude") latitude: Double,
        @Query("access_token") accessToken: String
    ): MapboxResponse

    @GET("geocoding/v5/mapbox.places/{query}.json")
    suspend fun searchPlaces(
        @Path("query") query: String,
        @Query("access_token") accessToken: String
    ): MapboxResponse
}