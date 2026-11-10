package it.multipla.obd

import android.content.Context
import it.multipla.obd.core.ConnectionStore
import it.multipla.obd.core.SavedTarget

/** Writes complete before policy state is published, including the durable manual gate. */
class AndroidConnectionStore(context: Context) : ConnectionStore {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    override fun target(): SavedTarget? {
        val address = preferences.getString("address", null) ?: return null
        val name = preferences.getString("name", null) ?: return null
        return SavedTarget(name, address)
    }
    override fun saveTarget(target: SavedTarget) {
        check(preferences.edit().putString("name", target.name).putString("address", target.address).commit()) { "Impossibile salvare il dispositivo" }
    }
    override fun manualRequired() = preferences.getBoolean("manual_required", false)
    override fun setManualRequired(required: Boolean) {
        check(preferences.edit().putBoolean("manual_required", required).commit()) { "Impossibile salvare lo stato della connessione" }
    }
    companion object {
        const val PREFERENCES = "obd-connection-v1"
    }
}
