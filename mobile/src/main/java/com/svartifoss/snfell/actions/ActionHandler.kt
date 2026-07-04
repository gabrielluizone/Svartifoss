package com.svartifoss.snfell.actions

interface ActionHandler<T : PhoneAction> {
    suspend fun handleAction(action: T)
}
