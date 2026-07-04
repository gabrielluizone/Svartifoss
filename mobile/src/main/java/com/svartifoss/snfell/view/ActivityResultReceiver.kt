package com.svartifoss.snfell.view

import android.content.Intent

interface ActivityResultReceiver {
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
}