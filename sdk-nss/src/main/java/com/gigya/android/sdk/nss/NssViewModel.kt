package com.gigya.android.sdk.nss

import android.content.Intent
import android.net.Uri
import com.gigya.android.sdk.GigyaLogger
import com.gigya.android.sdk.account.models.GigyaAccount
import com.gigya.android.sdk.nss.bloc.SchemaHelper
import com.gigya.android.sdk.nss.bloc.data.NssDataResolver
import com.gigya.android.sdk.nss.bloc.flow.NssFlowManager
import com.gigya.android.sdk.nss.channel.*
import com.gigya.android.sdk.nss.utils.guard
import com.gigya.android.sdk.nss.utils.refined
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class NssViewModel<T : GigyaAccount>(
        private val screenChannel: ScreenMethodChannel,
        private val dataChannel: DataMethodChannel,
        private val apiChannel: ApiMethodChannel,
        private val logChannel: LogMethodChannel,
        private val flowManager: NssFlowManager<T>,
        private val schemaHelper: SchemaHelper<T>,
        private val nssDataResolver: NssDataResolver
) {

    var finishClosure: () -> Unit? = { }
    var intentAction: (Intent) -> Unit? = { }
    var nssEvents: NssEvents<T>? = null
        set(value) {
            field = value
            flowManager.nssEvents = value
        }

    companion object {

        const val LOG_TAG = "NssViewModel"
    }

    internal fun dispose() {
        nssEvents = null
        screenChannel.dispose()
        logChannel.dispose()
        apiChannel.dispose()
    }

    /**
     * Register Flutter engine specific communication channels used for Nss usage.
     */
    fun loadChannels(engine: FlutterEngine) {
        screenChannel.initChannel(engine.dartExecutor.binaryMessenger)
        screenChannel.setMethodChannelHandler(screenMethodChannelHandler)

        apiChannel.initChannel(engine.dartExecutor.binaryMessenger)
        apiChannel.setMethodChannelHandler(apiMethodChannelHandler)

        logChannel.initChannel(engine.dartExecutor.binaryMessenger)
        logChannel.setMethodChannelHandler(logMethodChannelHandler)

        dataChannel.initChannel(engine.dartExecutor.binaryMessenger)
        dataChannel.setMethodChannelHandler(dataMethodChannelHandler)
    }

    /**
     * Handle engine logging options.
     */
    private val logMethodChannelHandler: MethodChannel.MethodCallHandler by lazy {
        MethodChannel.MethodCallHandler { call, _ ->
            call.arguments.refined<Map<String, String>> { logMap ->
                when (call.method) {
                    LogMethodChannel.LogCall.DEBUG.identifier -> {
                        GigyaLogger.debug(logMap["tag"], logMap["message"])
                    }
                    LogMethodChannel.LogCall.ERROR.identifier -> {
                        GigyaLogger.error(logMap["tag"], logMap["message"])
                    }
                }
            }
        }
    }

    /**
     * Handle engine screen actions.
     */
    private val screenMethodChannelHandler: MethodChannel.MethodCallHandler by lazy {
        MethodChannel.MethodCallHandler { call, result ->
            when (call.method) {
                // Screen initiated with action call.
                ScreenMethodChannel.ScreenCall.ACTION.identifier -> {
                    GigyaLogger.debug(LOG_TAG, "screen action")
                    call.arguments.refined<Map<String, String>> { map ->
                        val actionId = map["actionId"]
                        val screenId = map["screenId"]
                        actionId.guard {
                            GigyaLogger.error(LOG_TAG, "Missing action if in screen action initializer")
                            throw RuntimeException("Missing action if in screen action initializer. Unable to generate the correct action")
                        }
                        flowManager.setCurrent(actionId!!, screenId!!, result)
                    }
                }
                // Screen initiated with dismiss call.
                ScreenMethodChannel.ScreenCall.DISMISS.identifier -> {
                    GigyaLogger.debug(LOG_TAG, "screen dismiss")
                    flowManager.dispose()
                    finishClosure()
                }
                // Screen initiated with cancel call.
                ScreenMethodChannel.ScreenCall.CANCEL.identifier -> {
                    GigyaLogger.debug(LOG_TAG, "screen action")
                    // Pass a cancel event to main Nss events interface.
                    nssEvents?.onCancel()
                    flowManager.dispose()
                    finishClosure()
                }
                // Screen initiated an external link call.
                ScreenMethodChannel.ScreenCall.LINK.identifier -> {
                    GigyaLogger.debug(LOG_TAG, "screen link")
                    call.arguments.refined<Map<String, String>> { map ->
                        val uri = Uri.parse(map["url"])
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        // Forward intent action to activity in order to open browser URL.
                        intentAction(intent)
                    }
                }
            }
        }
    }

    /**
     * Handle specific engine component data requests.
     */
    private val dataMethodChannelHandler: MethodChannel.MethodCallHandler by lazy {
        MethodChannel.MethodCallHandler { call, result ->
            call.arguments.refined<MutableMap<String, Any>> { args ->
                nssDataResolver.handleDataRequest(call.method, args, result)
            }
        }
    }

    /**
     * Handle engine API requests.
     */
    private val apiMethodChannelHandler: MethodChannel.MethodCallHandler by lazy {
        MethodChannel.MethodCallHandler { call, result ->
            call.arguments.refined<MutableMap<String, Any>> { args ->
                flowManager.onNext(call.method, args, result)
            }
        }
    }

    /**
     * Load site schema.
     */
    fun loadSchema(result: MethodChannel.Result) {
        schemaHelper.getSchema(result)
    }

}