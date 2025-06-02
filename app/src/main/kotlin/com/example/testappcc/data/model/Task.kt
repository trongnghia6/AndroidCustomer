package com.example.testappcc.data.model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testappcc.core.supabase
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate

@Serializable
data class Task(
    val id: String,
    val typeService: String,
    val location: String? = null,
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val time: String? = null
)
@Serializable
data class TaskRaw(
    val id: Int,
    @SerialName("service_type")
    val typeService: String,
    val location: String? = null,
    val time: String? = null
)
@Serializable
data class BookingDetail(
    val id: Int,
    val bookingDate: String,
    val providers: Provider,
    val customers: Customer,
    val services: Service
)

@Serializable
data class Provider(
    val id: Int,
    val name: String,
    val phone: String? = null
)

@Serializable
data class Customer(
    val id: Int,
    val name: String,
    val email: String? = null
)

//@Serializable
//data class Service(
//    val id: Int,
//    val name: String,
//    val serviceTypes: ServiceType
//)
//
//@Serializable
//data class ServiceType(
//    val id: Int,
//    val name: String
//)

class TaskViewModel(private val userId: String) : ViewModel() {
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks

    fun fetchTasks() {
        viewModelScope.launch {
            val response = supabase
                .from("bookings")
                .select(columns = Columns.ALL) {
                    filter {
                        eq("provider_id", userId)
                    }
                }
                .decodeList<TaskRaw>()

            val tasksList = response.mapNotNull { raw ->
                val datetimeStr = raw.time ?: return@mapNotNull null
                try {
                    val odt = OffsetDateTime.parse(datetimeStr)
                    val date = odt.toLocalDate()
                    val time = odt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))

                    val task = Task(
                        id = raw.id.toString(),
                        typeService = raw.typeService,
                        location = raw.location,
                        date = date,
                        time = time
                    )
                    Log.d("Calendar", "$task")
                    task
                } catch (e: Exception) {
                    Log.e("Calendar", "Error parsing time: $datetimeStr", e)
                    null
                }
            }
            _tasks.value = tasksList
            val bookings = supabase
                .from("bookings")
                .select(
                    columns = Columns.list("*, providers(*), customers(*), services(*, service_types(*))")
                ).decodeList<BookingDetail>()

        }
    }
}
object LocalDateSerializer : KSerializer<LocalDate> {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) {
        val string = value.format(formatter)
        encoder.encodeString(string)
    }

    override fun deserialize(decoder: Decoder): LocalDate {
        val string = decoder.decodeString()
        return LocalDate.parse(string, formatter)
    }
}
