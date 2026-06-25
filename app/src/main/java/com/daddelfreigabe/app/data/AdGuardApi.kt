package com.daddelfreigabe.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AdGuardApi(
    private val serverUrl: String,
    private val username: String,
    private val password: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()
    private val credential = Credentials.basic(username, password)

    private fun baseUrl(): String {
        val url = serverUrl.trimEnd('/')
        return if (url.startsWith("http")) url else "http://$url"
    }

    private suspend fun findClientByIp(ip: String): Pair<String, JSONObject>? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${baseUrl()}/control/clients")
            .header("Authorization", credential)
            .get()
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Empty response")
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}: $body")

        val json = JSONObject(body)
        val clients = json.getJSONArray("clients")

        for (i in 0 until clients.length()) {
            val c = clients.getJSONObject(i)
            val ids = c.getJSONArray("ids")
            for (j in 0 until ids.length()) {
                if (ids.getString(j) == ip) {
                    return@withContext Pair(c.getString("name"), c)
                }
            }
        }
        null
    }

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${baseUrl()}/control/status"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", credential)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: $body\nURL: $url\nAuth: Basic ${credential.substringAfter("Basic ")}")
            }
            "Verbunden! (${response.code})"
        }
    }

    suspend fun getBlockedServices(clientIp: String): Result<List<String>> = runCatching {
        val (_, clientObj) = findClientByIp(clientIp)
            ?: throw Exception("Client $clientIp nicht gefunden")

        val blockedServices = clientObj.optJSONObject("blocked_services")
        val ids = blockedServices?.optJSONArray("ids") ?: return@runCatching emptyList()

        (0 until ids.length()).map { ids.getString(it) }
    }

    suspend fun setBlockedServices(
        clientIp: String,
        services: List<String>
    ): Result<Unit> = runCatching {
        val (name, clientObj) = findClientByIp(clientIp)
            ?: throw Exception("Client $clientIp nicht gefunden")

        val blockedServices = clientObj.optJSONObject("blocked_services")
            ?: JSONObject()
        blockedServices.put("ids", JSONArray(services))
        clientObj.put("blocked_services", blockedServices)

        val update = JSONObject().apply {
            put("name", name)
            put("data", clientObj)
        }

        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${baseUrl()}/control/clients/update")
                .header("Authorization", credential)
                .post(update.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val respBody = response.body?.string() ?: ""
                throw Exception("HTTP ${response.code}: $respBody")
            }
        }
    }

    suspend fun unblockServices(
        clientIp: String,
        servicesToUnblock: List<String>
    ): Result<Unit> {
        return getBlockedServices(clientIp).mapCatching { current ->
            val updated = current - servicesToUnblock.toSet()
            setBlockedServices(clientIp, updated).getOrThrow()
        }
    }

    suspend fun blockServices(
        clientIp: String,
        servicesToBlock: List<String>
    ): Result<Unit> {
        return getBlockedServices(clientIp).mapCatching { current ->
            val updated = (current + servicesToBlock).distinct()
            setBlockedServices(clientIp, updated).getOrThrow()
        }
    }
}
