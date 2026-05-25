package com.devoid.keysync.data.external

import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.util.Log
import android.view.InputEvent
import android.view.KeyEvent
import com.devoid.keysync.domain.EventHandler
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Proxy

class ShizukuInputMonitor(private val eventHandler: EventHandler) {
    private val TAG = "ShizukuInputMonitor"

    private var inputManager: Any? = null
    private var setInputFilterMethod: java.lang.reflect.Method? = null
    private var registered = false
    @Volatile
    private var stopped = false

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Binder that receives IInputFilter calls from the system server.
     * Transaction codes match the auto-generated IInputFilter.Stub layout:
     *  1 = onInputEvent(InputEvent, int displayId)
     *  2 = onInstalled()
     *  3 = onUninstalled()
     */
    private val filterBinder = object : Binder() {
        init {
            attachInterface(null, DESCRIPTOR)
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            when (code) {
                INTERFACE_TRANSACTION -> {
                    reply?.writeString(DESCRIPTOR)
                    return true
                }
                TRANSACTION_ON_INPUT_EVENT -> {
                    data.enforceInterface(DESCRIPTOR)
                    if (data.readInt() != 0) {
                        val event = InputEvent.CREATOR.createFromParcel(data)
                        data.readInt() // displayId
                        onInputEvent(event)
                    }
                    return true
                }
                TRANSACTION_ON_INSTALLED -> {
                    Log.i(TAG, "Input filter installed")
                    return true
                }
                TRANSACTION_ON_UNINSTALLED -> {
                    Log.i(TAG, "Input filter uninstalled")
                    return true
                }
            }
            return super.onTransact(code, data, reply, flags)
        }
    }

    /** Start monitoring input events system-wide through Shizuku. */
    fun start() {
        if (registered) return
        stopped = false
        try {
            val inputBinder = ShizukuBinderWrapper(
                SystemServiceHelper.getSystemService("input")
            )
            val stubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
            val ifaceClass = Class.forName("android.hardware.input.IInputManager")

            inputManager = stubClass.getMethod("asInterface", IBinder::class.java)
                .invoke(null, inputBinder)

            val iInputFilterClass = Class.forName(IINPUT_FILTER_CLASS)
            setInputFilterMethod = ifaceClass.getMethod("setInputFilter", iInputFilterClass)

            // Dynamic proxy implements IInputFilter so the AIDL proxy can call
            // asBinder() and send our filterBinder to the system server.
            val filterProxy = Proxy.newProxyInstance(
                iInputFilterClass.classLoader,
                arrayOf(iInputFilterClass)
            ) { proxy, method, _ ->
                when (method.name) {
                    "asBinder" -> filterBinder
                    "equals" -> proxy === filterBinder
                    "hashCode" -> System.identityHashCode(filterBinder)
                    "toString" -> "ShizukuInputFilter"
                    else -> null
                }
            }

            setInputFilterMethod?.invoke(inputManager, filterProxy)
            registered = true
            Log.i(TAG, "Registered system input filter")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register input filter, keyboard capture unavailable", e)
            registered = false
        }
    }

    /** Stop monitoring and remove the system input filter. */
    fun stop() {
        if (!registered) return
        stopped = true
        try {
            setInputFilterMethod?.invoke(inputManager, null)
            Log.i(TAG, "Unregistered system input filter")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister input filter", e)
        }
        registered = false
    }

    /** Forward key events to the EventHandler on the main thread. */
    private fun onInputEvent(event: InputEvent) {
        if (event is KeyEvent && !stopped) {
            mainHandler.post {
                if (stopped) return@post
                eventHandler.handleKeyEvent(event)
            }
        }
    }

    companion object {
        private const val DESCRIPTOR = "android.hardware.input.IInputFilter"
        private const val IINPUT_FILTER_CLASS = "android.hardware.input.IInputFilter"

        // Transaction codes from IInputFilter.aidl (stable across Android versions)
        private const val TRANSACTION_ON_INPUT_EVENT = 1
        private const val TRANSACTION_ON_INSTALLED = 2
        private const val TRANSACTION_ON_UNINSTALLED = 3
    }
}
