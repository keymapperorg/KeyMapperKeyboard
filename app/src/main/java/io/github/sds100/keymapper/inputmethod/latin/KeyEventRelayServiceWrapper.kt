package io.github.sds100.keymapper.inputmethod.latin

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.DeadObjectException
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import io.github.sds100.keymapper.api.IKeyEventRelayService
import io.github.sds100.keymapper.api.IKeyEventRelayServiceCallback


/**
 * This handles connecting to the relay service and exposes an interface
 * so other parts of the app can get a reference to the service even when it isn't
 * bound yet.
 *
 * @param servicePackageName This is the package name of the key mapper app to connect to to listen
 * to key events. This is needed because the .debug and .ci key mapper builds are commonly
 * used.
 */
class KeyEventRelayServiceWrapperImpl(
    context: Context,
    private val servicePackageName: String,
    private val callback: IKeyEventRelayServiceCallback,
) : KeyEventRelayServiceWrapper {

    companion object {
        /**
         * This is used to listen to when the key event relay service is restarted in Key Mapper.
         */
        const val ACTION_REBIND_RELAY_SERVICE = "io.github.sds100.keymapper.ACTION_REBIND_RELAY_SERVICE"
    }

    private val ctx: Context = context.applicationContext

    private val keyEventRelayServiceLock: Any = Any()
    private var isBound = false
    private var keyEventRelayService: IKeyEventRelayService? = null

    private val serviceConnection: ServiceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                synchronized(keyEventRelayServiceLock) {
                    keyEventRelayService = IKeyEventRelayService.Stub.asInterface(service)
                    Log.d(LatinIME.TAG, "Key event relay service started: $servicePackageName")
                    keyEventRelayService?.registerCallback(callback)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                synchronized(keyEventRelayServiceLock) {
                    // Do not unregister the callback in onServiceDisconnected
                    // because the connection is already broken at that point and it
                    // will fail.

                    Log.d(LatinIME.TAG, "Key event relay service stopped: $servicePackageName")

                    keyEventRelayService = null
                }
            }
        }

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            context ?: return
            intent ?: return

            when (intent.action) {
                ACTION_REBIND_RELAY_SERVICE -> {
                    bind()
                }
            }
        }
    }

    init {
        val intentFilter = IntentFilter().apply {
            addAction(ACTION_REBIND_RELAY_SERVICE)
        }

        ContextCompat.registerReceiver(ctx, broadcastReceiver, intentFilter, ContextCompat.RECEIVER_EXPORTED)
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
            val relayServiceIntent = Intent()
            val component =
                ComponentName(servicePackageName, "io.github.sds100.keymapper.api.KeyEventRelayService")
            relayServiceIntent.setComponent(component)
            val isSuccess = ctx.bindService(relayServiceIntent, serviceConnection, 0)

            if (isSuccess) {
                isBound = true
            } else {
                ctx.unbindService(serviceConnection)
                isBound = false
            }
        } catch (e: SecurityException) {
            Log.e(LatinIME.TAG, e.toString())
        }
    }

    fun unbind() {
        // Check if it is bound because otherwise
        // an exception is thrown if you unbind from a service
        // while there is no registered connection.
        if (isBound) {
            // Unregister the callback if this input method is unbinding
            // from the relay service. This should not happen in onServiceDisconnected
            // because the connection is already broken at that point and it
            // will fail.
            try {
                keyEventRelayService?.unregisterCallback()
            } catch (e: RemoteException) {
                // do nothing
            }
            ctx.unbindService(serviceConnection)
        }
    }
}

interface KeyEventRelayServiceWrapper {
    fun sendKeyEvent(
        event: KeyEvent?,
        targetPackageName: String?,
    ): Boolean
}
