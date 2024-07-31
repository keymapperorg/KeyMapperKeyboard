package io.github.sds100.keymapper.inputmethod.latin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.DeadObjectException
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import android.view.KeyEvent
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
    private val ctx: Context = context.applicationContext

    private val keyEventRelayServiceLock: Any = Any()
    private var keyEventRelayService: IKeyEventRelayService? = null

    private val serviceConnection: ServiceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                synchronized(keyEventRelayServiceLock) {
                    keyEventRelayService = IKeyEventRelayService.Stub.asInterface(service)
                    Log.d(LatinIME.TAG, "Register with key event relay service")
                    keyEventRelayService?.registerCallback(callback)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                synchronized(keyEventRelayServiceLock) {
                    try {
                        Log.d(LatinIME.TAG, "Unregister with key event relay service")
                        keyEventRelayService?.unregisterCallback()
                    } catch (_: RemoteException) {
                    } finally {
                        keyEventRelayService = null
                    }
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
            val relayServiceIntent = Intent()
            val component =
                ComponentName(servicePackageName, "io.github.sds100.keymapper.api.KeyEventRelayService")
            relayServiceIntent.setComponent(component)
            val isSuccess = ctx.bindService(relayServiceIntent, serviceConnection, 0)

            if (!isSuccess) {
                ctx.unbindService(serviceConnection)
            }
        } catch (e: SecurityException) {
            Log.e(LatinIME.TAG, e.toString())
        }
    }

    fun unbind() {
        try {
            ctx.unbindService(serviceConnection)
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
