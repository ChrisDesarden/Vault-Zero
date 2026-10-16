package com.example.model

import org.json.JSONArray
import org.json.JSONObject

enum class VaultType(val displayName: String, val iconName: String) {
    LOGIN("Logins", "vpn_key"),
    CARD("Cards", "credit_card"),
    API_SECRET("API Secrets", "code"),
    CRYPTO_WALLET("Crypto Wallets", "currency_bitcoin"),
    NOTE("Secure Notes", "description"),
    IDENTITY("Identities", "badge")
}

sealed interface ItemPayload {
    fun toJson(): JSONObject
}

data class LoginPayload(
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val totpSecret: String = "",
    val notes: String = ""
) : ItemPayload {
    override fun toJson(): JSONObject = JSONObject().apply {
        put("username", username)
        put("password", password)
        put("url", url)
        put("totpSecret", totpSecret)
        put("notes", notes)
    }

    companion object {
        fun fromJson(json: JSONObject): LoginPayload {
            return LoginPayload(
                username = json.optString("username", ""),
                password = json.optString("password", ""),
                url = json.optString("url", ""),
                totpSecret = json.optString("totpSecret", ""),
                notes = json.optString("notes", "")
            )
        }
    }
}

data class CardPayload(
    val cardholderName: String = "",
    val cardNumber: String = "",
    val expMonth: String = "",
    val expYear: String = "",
    val cvv: String = "",
    val pin: String = "",
    val brand: String = "Visa",
    val notes: String = ""
) : ItemPayload {
    override fun toJson(): JSONObject = JSONObject().apply {
        put("cardholderName", cardholderName)
        put("cardNumber", cardNumber)
        put("expMonth", expMonth)
        put("expYear", expYear)
        put("cvv", cvv)
        put("pin", pin)
        put("brand", brand)
        put("notes", notes)
    }

    companion object {
        fun fromJson(json: JSONObject): CardPayload {
            return CardPayload(
                cardholderName = json.optString("cardholderName", ""),
                cardNumber = json.optString("cardNumber", ""),
                expMonth = json.optString("expMonth", ""),
                expYear = json.optString("expYear", ""),
                cvv = json.optString("cvv", ""),
                pin = json.optString("pin", ""),
                brand = json.optString("brand", "Visa"),
                notes = json.optString("notes", "")
            )
        }
    }
}

data class ApiSecretPayload(
    val serviceName: String = "",
    val apiKey: String = "",
    val secretToken: String = "",
    val environment: String = "Production",
    val scopes: String = "",
    val endpointUrl: String = "",
    val notes: String = ""
) : ItemPayload {
    override fun toJson(): JSONObject = JSONObject().apply {
        put("serviceName", serviceName)
        put("apiKey", apiKey)
        put("secretToken", secretToken)
        put("environment", environment)
        put("scopes", scopes)
        put("endpointUrl", endpointUrl)
        put("notes", notes)
    }

    companion object {
        fun fromJson(json: JSONObject): ApiSecretPayload {
            return ApiSecretPayload(
                serviceName = json.optString("serviceName", ""),
                apiKey = json.optString("apiKey", ""),
                secretToken = json.optString("secretToken", ""),
                environment = json.optString("environment", "Production"),
                scopes = json.optString("scopes", ""),
                endpointUrl = json.optString("endpointUrl", ""),
                notes = json.optString("notes", "")
            )
        }
    }
}

data class CryptoPayload(
    val walletName: String = "",
    val network: String = "Ethereum",
    val publicAddress: String = "",
    val seedPhrase: String = "",
    val privateKey: String = "",
    val derivationPath: String = "m/44'/60'/0'/0/0",
    val notes: String = ""
) : ItemPayload {
    override fun toJson(): JSONObject = JSONObject().apply {
        put("walletName", walletName)
        put("network", network)
        put("publicAddress", publicAddress)
        put("seedPhrase", seedPhrase)
        put("privateKey", privateKey)
        put("derivationPath", derivationPath)
        put("notes", notes)
    }

    companion object {
        fun fromJson(json: JSONObject): CryptoPayload {
            return CryptoPayload(
                walletName = json.optString("walletName", ""),
                network = json.optString("network", "Ethereum"),
                publicAddress = json.optString("publicAddress", ""),
                seedPhrase = json.optString("seedPhrase", ""),
                privateKey = json.optString("privateKey", ""),
                derivationPath = json.optString("derivationPath", "m/44'/60'/0'/0/0"),
                notes = json.optString("notes", "")
            )
        }
    }
}

data class NotePayload(
    val content: String = "",
    val isPinned: Boolean = false
) : ItemPayload {
    override fun toJson(): JSONObject = JSONObject().apply {
        put("content", content)
        put("isPinned", isPinned)
    }

    companion object {
        fun fromJson(json: JSONObject): NotePayload {
            return NotePayload(
                content = json.optString("content", ""),
                isPinned = json.optBoolean("isPinned", false)
            )
        }
    }
}

data class IdentityPayload(
    val fullName: String = "",
    val idType: String = "Passport",
    val idNumber: String = "",
    val birthDate: String = "",
    val expiryDate: String = "",
    val notes: String = ""
) : ItemPayload {
    override fun toJson(): JSONObject = JSONObject().apply {
        put("fullName", fullName)
        put("idType", idType)
        put("idNumber", idNumber)
        put("birthDate", birthDate)
        put("expiryDate", expiryDate)
        put("notes", notes)
    }

    companion object {
        fun fromJson(json: JSONObject): IdentityPayload {
            return IdentityPayload(
                fullName = json.optString("fullName", ""),
                idType = json.optString("idType", "Passport"),
                idNumber = json.optString("idNumber", ""),
                birthDate = json.optString("birthDate", ""),
                expiryDate = json.optString("expiryDate", ""),
                notes = json.optString("notes", "")
            )
        }
    }
}

data class VaultItem(
    val id: String,
    val title: String,
    val type: VaultType,
    val category: String = "Personal",
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val tags: List<String> = emptyList(),
    val payload: ItemPayload
) {
    fun payloadToJsonString(): String {
        return payload.toJson().toString()
    }

    companion object {
        fun parsePayload(type: VaultType, jsonString: String): ItemPayload {
            val json = try {
                JSONObject(jsonString)
            } catch (e: Exception) {
                JSONObject()
            }
            return when (type) {
                VaultType.LOGIN -> LoginPayload.fromJson(json)
                VaultType.CARD -> CardPayload.fromJson(json)
                VaultType.API_SECRET -> ApiSecretPayload.fromJson(json)
                VaultType.CRYPTO_WALLET -> CryptoPayload.fromJson(json)
                VaultType.NOTE -> NotePayload.fromJson(json)
                VaultType.IDENTITY -> IdentityPayload.fromJson(json)
            }
        }
    }
}
