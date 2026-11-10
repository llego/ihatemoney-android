package net.llego.ihatemoney.util

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import at.bitfire.cert4android.CustomCertManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Share foreground state with cert4android so background sync never opens an activity. */
object CertificateTrust : DefaultLifecycleObserver {
    private val foregroundState = MutableStateFlow(false)
    internal val appInForeground = foregroundState.asStateFlow()

    @JvmStatic
    fun initialize() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        foregroundState.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        foregroundState.value = false
    }

    @JvmStatic
    fun createManager(context: Context, trustSystemCerts: Boolean): CustomCertManager =
        CustomCertManager(context.applicationContext, trustSystemCerts, appInForeground)
}
