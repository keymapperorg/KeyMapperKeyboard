package io.github.sds100.keymapper.inputmethod.latin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.DeadObjectException
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent
import io.github.sds100.keymapper.IKeyEventRelayService
import io.github.sds100.keymapper.IKeyEventRelayServiceCallback

/**
 * This handles connecting to the relay service and exposes an interface
 * so other parts of the app can get a reference to the service even when it isn't
 * bound yet.
 */
class KeyEventRelayServiceWrapperImpl(
    context: Context,
    private val callback: IKeyEventRelayServiceCallback,
) : KeyEventRelayServiceWrapper {
    private val ctx: Context = context.applicationContext

    private val keyEventRelayServiceLock: Any = Any()
    private var keyEventRelayService: IKeyEventRelayService? = null

    private val keyEventReceiverConnection: ServiceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                synchronized(keyEventRelayServiceLock) {
                    keyEventRelayService = IKeyEventRelayService.Stub.asInterface(service)
                    Log.e(LatinIME.TAG, "Register callback")
                    keyEventRelayService?.registerCallback(callback)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                synchronized(keyEventRelayServiceLock) {
                    keyEventRelayService?.unregisterCallback()
                    keyEventRelayService = null
                }
            }
        }

    override fun sendKeyEvent(
        event: KeyEvent?,
        targetPackageName: String?,
    ): Boolean {
        synchronized(keyEventRelayServiceLock) {
            if (keyEventRelayService == null) {
                return false
            }

            try {
                return keyEventRelayService!!.sendKeyEvent(event, targetPackageName)
            } catch (e: DeadObjectException) {
                keyEventRelayService = null
                return false
            }
        }
    }

    fun bind() {
        try {
            val keyEventReceiverComponent =
                ComponentName("io.github.sds100.keymapper.debug", "io.github.sds100.keymapper.api.KeyEventRelayService")
            val keyEventReceiverServiceIntent = Intent()
            keyEventReceiverServiceIntent.setComponent(keyEventReceiverComponent)
            ctx.bindService(keyEventReceiverServiceIntent, keyEventReceiverConnection, 0)

            Log.e(LatinIME.TAG, "Bind to service")
        } catch (e: SecurityException) {
            Log.e(LatinIME.TAG, e.toString())
        }
    }

    fun unbind() {
        try {
            ctx.unbindService(keyEventReceiverConnection)
        } catch (e: DeadObjectException) {
            // do nothing
        }
    }
}

interface KeyEventRelayServiceWrapper {
    fun sendKeyEvent(
        event: KeyEvent?,
        targetPackageName: String?,
    ): Boolean
}
