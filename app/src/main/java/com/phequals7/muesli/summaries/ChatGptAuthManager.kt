package com.phequals7.muesli.summaries

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * ChatGPT OAuth (PKCE) sign-in, ported from muesli-ios ChatGPTAuthManager.
 *
 * Flow: loopback HTTP server on 127.0.0.1:1455 + Chrome Custom Tab to
 * auth.openai.com → authorization code → token exchange. Tokens are stored
 * in app-private SharedPreferences and refreshed on demand.
 */
class ChatGptAuthManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "ChatGptAuth"
        private const val PREFS_NAME = "muesli_chatgpt_auth"
        private const val CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
        private const val AUTH_URL = "https://auth.openai.com/oauth/authorize"
        private const val TOKEN_URL = "https://auth.openai.com/oauth/token"
        private const val CALLBACK_PORT = 1455
        private const val REDIRECT_URI = "http://localhost:1455/auth/callback"
        private const val SCOPES = "openid profile email offline_access"
        private const val CALLBACK_TIMEOUT_MS = 300_000L
    }

    val isAuthenticated: Boolean
        get() = !prefs.getString("access_token", null).isNullOrEmpty()

    fun signOut() {
        prefs.edit().clear().apply()
    }

    /** Opens a Custom Tab for sign-in and waits for the loopback callback.
     * Returns an error message on failure, null on success. */
    suspend fun signIn(launcher: (Intent) -> Unit): String? = withContext(Dispatchers.IO) {
        val verifier = randomBase64Url()
        val challenge = sha256Base64Url(verifier)
        val state = randomBase64Url()

        val server = try {
            ServerSocket(CALLBACK_PORT)
        } catch (e: Exception) {
            return@withContext "Sign-in callback port ($CALLBACK_PORT) is already in use."
        }

        try {
            coroutineScope {
                val callback = async {
                    withTimeout(CALLBACK_TIMEOUT_MS) { awaitCallback(server, state) }
                }

                // Open in the user's full browser (Chrome), not an in-app tab:
                // cookies/password managers are available and the loopback
                // callback flow works identically.
                val url = buildAuthorizeUrl(challenge, state)
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                launcher(browserIntent)

                val code = try {
                    callback.await()
                } catch (e: Exception) {
                    return@coroutineScope "ChatGPT sign-in timed out or was cancelled."
                }

                when (val error = exchangeCode(code, verifier)) {
                    null -> null
                    else -> error
                }
            }
        } finally {
            try {
                server.close()
            } catch (_: Exception) {
            }
        }
    }

    /** Returns a valid access token (+ account id), refreshing when expired. */
    suspend fun validAccessToken(): Pair<String, String> = withContext(Dispatchers.IO) {
        val accessToken = prefs.getString("access_token", null)
            ?: throw AuthException("Not signed in to ChatGPT.")
        val accountId = prefs.getString("account_id", "") ?: ""
        val expiresAt = prefs.getLong("expires_at", 0L)

        if (expiresAt > System.currentTimeMillis() + 30_000) {
            return@withContext accessToken to accountId
        }

        val refreshToken = prefs.getString("refresh_token", null)
            ?: throw AuthException("Not signed in to ChatGPT.")

        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", CLIENT_ID)
            .add("refresh_token", refreshToken)
            .build()
        val response = http.newCall(Request.Builder().url(TOKEN_URL).post(body).build()).execute()
        val responseBody = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw AuthException("ChatGPT token refresh failed: ${responseBody.take(300)}")
        }
        saveTokens(JSONObject(responseBody), fallbackRefresh = refreshToken)
        (prefs.getString("access_token", null)!! to (prefs.getString("account_id", "") ?: ""))
    }

    class AuthException(message: String) : Exception(message)

    // ── loopback callback server ─────────────────────────────────────────

    private fun awaitCallback(server: ServerSocket, expectedState: String): String {
        val socket: Socket = server.accept()
        socket.soTimeout = 10_000
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val requestLine = reader.readLine() ?: ""

        fun respond(status: String, title: String, detail: String) {
            val html = "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                "<style>body{font-family:sans-serif;background:#111214;color:#f5f5f7;display:flex;align-items:center;" +
                "justify-content:center;height:100vh;margin:0;padding:24px;text-align:center}p{color:#a1a1aa}</style></head>" +
                "<body><main><h1>$title</h1><p>$detail</p></main></body></html>"
            val writer = PrintWriter(socket.getOutputStream(), true)
            writer.print("HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\n\r\n$html")
            writer.flush()
        }

        val path = requestLine.split(" ").getOrNull(1) ?: ""
        val params = path.substringAfter('?', "")
            .split('&')
            .filter { it.contains('=') }
            .associate {
                val (k, v) = it.split('=', limit = 2)
                k to URLDecoder.decode(v, Charsets.UTF_8)
            }

        return when {
            params["state"] != expectedState -> {
                respond("400 Bad Request", "Sign-in failed", "Security validation failed. Please try again.")
                throw AuthException("ChatGPT sign-in failed security validation.")
            }
            params["code"].isNullOrEmpty() -> {
                respond("400 Bad Request", "Sign-in failed", "ChatGPT did not return an authorization code.")
                throw AuthException("ChatGPT sign-in did not return an authorization code.")
            }
            else -> {
                respond("200 OK", "Signed in to Muesli", "You can close this window and return to Muesli.")
                params["code"]!!
            }
        }
    }

    private fun buildAuthorizeUrl(challenge: String, state: String): String =
        Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("id_token_add_organizations", "true")
            .appendQueryParameter("codex_cli_simplified_flow", "true")
            .appendQueryParameter("originator", "opencode")
            .build()
            .toString()

    // ── tokens ───────────────────────────────────────────────────────────

    private fun exchangeCode(code: String, verifier: String): String? {
        return try {
            val body = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", CLIENT_ID)
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("code_verifier", verifier)
                .build()
            val response = http.newCall(Request.Builder().url(TOKEN_URL).post(body).build()).execute()
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Log.e(TAG, "token exchange failed: ${response.code} $responseBody")
                return "ChatGPT token exchange failed (${response.code})."
            }
            saveTokens(JSONObject(responseBody), fallbackRefresh = "")
            null
        } catch (e: Exception) {
            Log.e(TAG, "token exchange error", e)
            "ChatGPT token exchange failed: ${e.message}"
        }
    }

    private fun saveTokens(json: JSONObject, fallbackRefresh: String) {
        val accessToken = json.getString("access_token")
        val refreshToken = json.optString("refresh_token", fallbackRefresh)
        val expiresIn = json.optLong("expires_in", 3600L)
        prefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putLong("expires_at", System.currentTimeMillis() + expiresIn * 1000)
            .putString("account_id", extractAccountId(accessToken))
            .apply()
    }

    /** Port of the iOS JWT account-id extraction (chatgpt_account_id / auth claims / org). */
    private fun extractAccountId(jwt: String): String {
        val segments = jwt.split('.')
        if (segments.size < 2) return ""
        return try {
            var payload = segments[1].replace('-', '+').replace('_', '/')
            while (payload.length % 4 != 0) payload += "="
            val json = JSONObject(String(android.util.Base64.decode(payload, android.util.Base64.DEFAULT)))
            json.optString("chatgpt_account_id").ifEmpty {
                json.optJSONObject("https://api.openai.com/auth")?.optString("chatgpt_account_id")
                    ?: json.optJSONArray("organizations")?.optJSONObject(0)?.optString("id")
                    ?: ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    // ── PKCE helpers ─────────────────────────────────────────────────────

    private fun randomBase64Url(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
    }

    private fun sha256Base64Url(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.US_ASCII))
        return android.util.Base64.encodeToString(digest, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
    }
}
