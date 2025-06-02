package com.example.testappcc.presentation.search

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.testappcc.data.model.MapboxSearchState

@Composable
fun AddressAutoComplete(
    state: MapboxSearchState,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        TextField(
            value = state.query,
            onValueChange = state.onQueryChange,
            label = { Text("Nhập địa chỉ") },
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = Color.Black),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Blue,
                unfocusedIndicatorColor = Color.Gray
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Hiển thị danh sách gợi ý nếu có
        if (state.places.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .border(1.dp, Color.LightGray)
            ) {
                items(state.places) { place ->
                    Text(
                        text = place.placeName ?: "Không rõ địa chỉ",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                state.onAddressSelected(place.placeName ?: "")
                            }
                            .padding(8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        state.selectedAddress?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Đã chọn: $it", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

