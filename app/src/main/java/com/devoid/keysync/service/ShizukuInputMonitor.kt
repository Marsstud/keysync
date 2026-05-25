package com.devoid.keysync.service

import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.util.Log
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import com.devoid.keysync.domain.EventHandler
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Proxy

class ShizukuInputMonitor(
    private val eventHandler: EventHandler,
    private val onMotionEvent: ((MotionEvent) -> Unit)? = null
) {
    private val TAG = "ShizukuInputMonitor"

    private var inputManager: Any? = null
    private var setInputFilterMethod: java.lang.reflect.Method? = null
    private var registered = false
    @Volatile
    private var stopped = false

    private val mainHandler = Handler(Looper.getMainLooper())

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
                        data.readInt()
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

    private fun onInputEvent(event: InputEvent) {
        if (stopped) return
        when (event) {
            is KeyEvent -> mainHandler.post {
                if (stopped) return@post
                eventHandler.handleKeyEvent(event)
            }
            is MotionEvent -> mainHandler.post {
                if (stopped) return@post
                onMotionEvent?.invoke(event)
            }
        }
    }

    companion object {
        private const val DESCRIPTOR = "android.hardware.input.IInputFilter"
        private const val IINPUT_FILTER_CLASS = "android.hardware.input.IInputFilter"
        private const val TRANSACTION_ON_INPUT_EVENT = 1
        private const val TRANSACTION_ON_INSTALLED = 2
        private const val TRANSACTION_ON_UNINSTALLED = 3
    }
}
