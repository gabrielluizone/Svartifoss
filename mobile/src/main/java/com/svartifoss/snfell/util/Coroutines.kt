package com.svartifoss.snfell.util

import android.content.Context
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.GooglePlayServicesRepairableException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

fun CoroutineScope.launchWithPlayServicesErrorHandling(
        androidContext: Context,
        coroutineContext: CoroutineContext = EmptyCoroutineContext,
        block: suspend () -> Unit
): Job = launch(coroutineContext) {
    try {
        block()
    } catch (e: GooglePlayServicesRepairableException) {
        GoogleApiAvailability.getInstance().showErrorNotification(androidContext, e.connectionStatusCode)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Action trigger fail")
    }
}
