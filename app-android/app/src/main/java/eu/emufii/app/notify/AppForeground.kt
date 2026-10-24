package eu.emufii.app.notify

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.Collections
import java.util.IdentityHashMap

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppForeground {
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    /** Internal game and bottom-screen Activities are part of Emufii too. */
    fun install(application: Application) {
        val resumed = Collections.newSetFromMap(IdentityHashMap<Activity, Boolean>())
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { resumed.add(activity); set(true) }
            override fun onActivityPaused(activity: Activity) { resumed.remove(activity); set(resumed.isNotEmpty()) }
            override fun onActivityDestroyed(activity: Activity) { resumed.remove(activity); set(resumed.isNotEmpty()) }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        })
    }

    fun set(value: Boolean) {
        _visible.value = value
    }
}
