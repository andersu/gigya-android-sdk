package com.gigya.android.sample

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import android.webkit.WebView
import com.gigya.android.sdk.Gigya
import com.gigya.android.sdk.log.GigyaLogger

class GigyaSampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Allow WebViews debugging.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (0 != applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) {
                WebView.setWebContentsDebuggingEnabled(true)
            }
        }

        GigyaLogger.setDebugMode(true)
        Log.d("GigyaSampleApplication", Gigya.VERSION)

        /*
        Initialization with explicit api-key.
         */
        //Gigya.getInstance(applicationContext).init(getString(R.string.api_key_with_email_verification))

        /*
        Initialization with explicit api-key, api-domain type.
        */
        //Gigya.getInstance(applicationContext).init(getString(R.string.api_kay), getString(R.string.api_domain)))

        /*
        Initialization with implicit configuration & without a custom account scheme.
        Will use the default GigyaAccount scheme.
        */
        Gigya.getInstance(applicationContext)

        /*
        Initialization with implicit configuration & account scheme.
         */
        //Gigya.getInstance(applicationContext, MyAccount::class.java)
    }
}