package com.example.taskmanager.domain.cache

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class AppUiState(
    val label: String,
    val icon: Drawable?,
    val isSystem: Boolean = false
)

class AppInfoCache(private val context: Context) {

    private val packageManager = context.packageManager
    private val cache = ConcurrentHashMap<String, AppUiState>()
    
    // Observable map to trigger UI updates when cache is populated
    private val _updates = MutableStateFlow<Map<String, AppUiState>>(emptyMap())
    val updates: StateFlow<Map<String, AppUiState>> = _updates.asStateFlow()

    private val defaultIcon = ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)

    fun getAppUiState(processName: String, scope: CoroutineScope): AppUiState {
        // 1. Check Memory Cache
        val cached = cache[processName]
        if (cached != null) {
            return cached
        }

        // 2. Return Placeholder immediately
        val placeholder = AppUiState(
            label = processName,
            icon = defaultIcon,
            isSystem = false
        )
        // Temporarily put placeholder to avoid spamming coroutines for same key
        cache[processName] = placeholder 

        // 3. Launch Async Loader
        scope.launch(Dispatchers.IO) {
            val loadedState = try {
                // If the name looks like a package (contains dot), try to find it
                if (processName.contains(".")) {
                    // CRITICAL FIX: Many apps run in sub-processes (e.g., "com.example.app:remote")
                    // We must strip the suffix to find the actual package info.
                    val packageName = processName.split(":")[0]
                    Log.d("AppInfoCache", "Resolving: $processName -> $packageName")
                    
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    val label = packageManager.getApplicationLabel(appInfo).toString()
                    val icon = packageManager.getApplicationIcon(appInfo)
                    
                    // Check if it is actually a system app
                    val isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    
                    Log.d("AppInfoCache", "Success: $packageName | Label: $label | System: $isSystemApp")
                    AppUiState(label, icon, isSystem = isSystemApp)
                } else {
                    // Likely a system binary or kernel thread
                    AppUiState(processName, defaultIcon, isSystem = true)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w("AppInfoCache", "NameNotFound: $processName (Base: ${processName.split(":")[0]})")
                // Name looked like a package but wasn't found (e.g. split apks or native process)
                AppUiState(processName, defaultIcon, isSystem = true)
            } catch (e: Exception) {
                Log.e("AppInfoCache", "Error resolving $processName", e)
                AppUiState(processName, defaultIcon, isSystem = true)
            }

            // Update Cache
            cache[processName] = loadedState
            
            // Trigger Flow to notify observers (efficiently)
            _updates.emit(cache.toMap())
        }

        return placeholder
    }
}