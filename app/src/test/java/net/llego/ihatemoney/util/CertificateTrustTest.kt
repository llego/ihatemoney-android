package net.llego.ihatemoney.util

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CertificateTrustTest {
    private val owner = object : LifecycleOwner {
        override val lifecycle: Lifecycle
            get() = error("Observer callbacks do not need to query the owner")
    }

    @Before
    fun resetForegroundState() {
        CertificateTrust.onStop(owner)
    }

    @Test
    fun certificatePromptsFollowProcessForegroundState() {
        assertFalse(CertificateTrust.appInForeground.value)
        CertificateTrust.onStart(owner)
        assertTrue(CertificateTrust.appInForeground.value)
        CertificateTrust.onStop(owner)
        assertFalse(CertificateTrust.appInForeground.value)
    }

    @Test
    fun existingManagersObserveTheSameFlowAcrossTransitions() {
        val state = CertificateTrust.appInForeground
        CertificateTrust.onStart(owner)
        assertSame(state, CertificateTrust.appInForeground)
        assertTrue(state.value)
        CertificateTrust.onStop(owner)
        assertFalse(state.value)
    }
}
