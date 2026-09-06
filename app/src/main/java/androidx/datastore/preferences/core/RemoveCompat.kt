package androidx.datastore.preferences.core

/**
 * Compatibility shim for the explicit `remove` import used by Nyra.
 * MutablePreferences already exposes remove as a member; this extension only
 * makes the symbol importable across DataStore versions where no top-level
 * remove symbol is published.
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun <T> MutablePreferences.remove(key: Preferences.Key<T>) {
    this.remove(key)
}
