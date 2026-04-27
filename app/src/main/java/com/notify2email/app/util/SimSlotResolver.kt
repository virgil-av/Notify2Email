package com.notify2email.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

data class SimResolution(
    val slotIndex: Int? = null,
    val subscriptionId: Int? = null,
) {
    val displayName: String?
        get() = slotIndex?.let { "SIM ${it + 1}" }
}

class SimSlotResolver(private val context: Context) {
    fun resolveBestEffort(
        slotIndex: Int? = null,
        subscriptionId: Int? = null,
        intent: Intent? = null,
        phoneAccountHandle: PhoneAccountHandle? = null,
    ): SimResolution {
        val activeSlots = activeDualSimSlotsOrNull() ?: return SimResolution()
        val explicitSlot = slotIndex
            ?: intent?.readIntExtra(SLOT_EXTRA_KEYS)
        val explicitSubscriptionId = subscriptionId
            ?: intent?.readIntExtra(SUBSCRIPTION_EXTRA_KEYS)
            ?: phoneAccountHandle?.let(::subscriptionIdForPhoneAccount)

        val resolvedSlot = explicitSlot?.takeIf(activeSlots::containsKey)
            ?: explicitSubscriptionId?.let { subId ->
                activeSlots.entries.firstOrNull { it.value == subId }?.key
            }
            ?: return SimResolution()
        val resolvedSubscriptionId = explicitSubscriptionId ?: activeSlots[resolvedSlot]

        return SimResolution(
            slotIndex = resolvedSlot,
            subscriptionId = resolvedSubscriptionId,
        )
    }

    @SuppressLint("MissingPermission")
    private fun activeDualSimSlotsOrNull(): Map<Int, Int>? {
        if (!hasReadPhoneStatePermission()) return null
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_SUBSCRIPTION)) return null

        val telephonyManager = context.getSystemService(TelephonyManager::class.java) ?: return null
        val subscriptionManager = context.getSystemService(SubscriptionManager::class.java) ?: return null
        val activeModemCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { telephonyManager.activeModemCount }.getOrDefault(0)
        } else {
            runCatching { subscriptionManager.activeSubscriptionInfoCount }.getOrDefault(0)
        }
        if (activeModemCount < DUAL_SIM_SLOT_COUNT) return null

        val activeSubscriptions = runCatching {
            subscriptionManager.activeSubscriptionInfoList.orEmpty()
        }.getOrElse {
            return null
        }
        val activeSlots = activeSubscriptions
            .mapNotNull { info ->
                val slot = info.simSlotIndex
                val subId = info.subscriptionId
                if (slot in REQUIRED_SIM_SLOTS && subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    slot to subId
                } else {
                    null
                }
            }
            .distinctBy { it.first }
            .toMap()

        if (!REQUIRED_SIM_SLOTS.all(activeSlots::containsKey)) return null
        if (!REQUIRED_SIM_SLOTS.all { slot -> telephonyManager.hasInstalledSim(slot) }) return null
        return activeSlots
    }

    @SuppressLint("MissingPermission")
    private fun subscriptionIdForPhoneAccount(handle: PhoneAccountHandle): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !hasReadPhoneStatePermission()) return null
        val telephonyManager = context.getSystemService(TelephonyManager::class.java) ?: return null
        return runCatching { telephonyManager.getSubscriptionId(handle) }
            .getOrNull()
            ?.takeUnless { it == SubscriptionManager.INVALID_SUBSCRIPTION_ID }
    }

    private fun hasReadPhoneStatePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    private fun TelephonyManager.hasInstalledSim(slotIndex: Int): Boolean = runCatching {
        when (getSimState(slotIndex)) {
            TelephonyManager.SIM_STATE_ABSENT,
            TelephonyManager.SIM_STATE_UNKNOWN,
            -> false
            else -> true
        }
    }.getOrDefault(false)

    private fun Intent.readIntExtra(keys: List<String>): Int? {
        val extras = extras ?: return null
        return keys.firstNotNullOfOrNull { key ->
            extras.get(key)?.toIntOrNull()
        }
    }

    private fun Any.toIntOrNull(): Int? = when (this) {
        is Int -> this
        is Long -> takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
        is Short -> toInt()
        is String -> toIntOrNull()
        else -> null
    }

    private companion object {
        const val DUAL_SIM_SLOT_COUNT = 2

        val REQUIRED_SIM_SLOTS = setOf(0, 1)

        val SLOT_EXTRA_KEYS = listOf(
            "slot",
            SubscriptionManager.EXTRA_SLOT_INDEX,
        )

        val SUBSCRIPTION_EXTRA_KEYS = listOf(
            "subscription",
            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
        )
    }
}
