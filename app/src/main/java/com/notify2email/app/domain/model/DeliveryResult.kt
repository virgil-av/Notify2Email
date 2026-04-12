package com.notify2email.app.domain.model

sealed interface DeliveryResult {
    data class Success(val attempts: Int) : DeliveryResult
    data class Failure(val message: String, val attempts: Int) : DeliveryResult
    data object Disabled : DeliveryResult
}
