package com.matedroid.domain

/**
 * Which energy figure the cost-per-kWh breakdowns divide by (Settings → Display → Costs).
 *
 * [ENERGY_ADDED] — energy that ended up in the battery: the effective price of the energy
 * you actually drive on. [ENERGY_USED] — energy drawn from the charger, including charging
 * losses: TeslaMate's own convention, directly comparable to a tariff or a charging invoice.
 *
 * Like [UnitSystem], the selection is mirrored process-wide: restored at app start by
 * [com.matedroid.MateDroidApp] and written through by
 * [com.matedroid.ui.screens.settings.SettingsViewModel] when the user picks a new value.
 * The charge detail screen ignores the setting and always shows both figures; every
 * aggregate surface divides through [energyFor] (or asks its DAO the equivalent).
 */
enum class CostPerKwhBasis(val id: String) {
    ENERGY_ADDED("added"),
    ENERGY_USED("used");

    companion object {
        val DEFAULT = ENERGY_ADDED

        @Volatile
        var current: CostPerKwhBasis = DEFAULT

        fun fromId(id: String?): CostPerKwhBasis =
            entries.firstOrNull { it.id == id } ?: DEFAULT

        /**
         * The divisor for a cost-per-kWh figure under the current basis. Falls back to energy
         * added when energy used is missing or zero — rows cached before the field was synced,
         * and sessions where TeslaMate logs no usage.
         */
        fun energyFor(energyAdded: Double, energyUsed: Double?): Double = when (current) {
            ENERGY_ADDED -> energyAdded
            ENERGY_USED -> if (energyUsed != null && energyUsed > 0.0) energyUsed else energyAdded
        }
    }
}
