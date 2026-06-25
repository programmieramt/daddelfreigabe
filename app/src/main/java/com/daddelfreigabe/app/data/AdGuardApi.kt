package com.daddelfreigabe.app.data

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class AccessList(
    val allowed_clients: List<String>,
    val disallowed_clients: List<String>,
    val blocked_hosts: List<String>
)

class AdGuardApi(
    private val serverUrl: String,
    private val username: String,
    private val password: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().build()
    private val accessListAdapter = moshi.adapter(AccessList::class.java)
    private val jsonMediaType = "application/json".toMediaType()

    private val credential = Credentials.basic(username, password)

    private fun baseUrl(): String {
        val url = serverUrl.trimEnd('/')
        return if (url.startsWith("http")) url else "http://$url"
    }

    suspend fun getAccessList(): Result<AccessList> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("${baseUrl()}/control/access/list")
                .header("Authorization", credential)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Empty response")
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}: $body")
            accessListAdapter.fromJson(body) ?: throw Exception("Failed to parse response")
        }
    }

    suspend fun setAccessList(accessList: AccessList): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val json = accessListAdapter.toJson(accessList)
            val request = Request.Builder()
                .url("${baseUrl()}/control/access/set")
                .header("Authorization", credential)
                .post(json.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val body = response.body?.string() ?: ""
                throw Exception("HTTP ${response.code}: $body")
            }
        }
    }

    suspend fun blockClient(clientId: String): Result<Unit> {
        return getAccessList().mapCatching { current ->
            if (clientId in current.disallowed_clients) return@mapCatching
            val updated = current.copy(
                disallowed_clients = current.disallowed_clients + clientId
            )
            setAccessList(updated).getOrThrow()
        }
    }

    suspend fun unblockClient(clientId: String): Result<Unit> {
        return getAccessList().mapCatching { current ->
            val updated = current.copy(
                disallowed_clients = current.disallowed_clients - clientId
            )
            setAccessList(updated).getOrThrow()
        }
    }

    suspend fun isClientBlocked(clientId: String): Result<Boolean> {
        return getAccessList().map { clientId in it.disallowed_clients }
    }
}
