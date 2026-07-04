package com.svartifoss.snfell.watch.communication

import android.graphics.Bitmap
import com.svartifoss.snfell.proto.CustomList

data class CustomListWithBitmaps(
        val listTimestamp: Long,
        val listId: String,
        val items: List<CustomListItemWithIcon>,
        val activeEntryId: String? = null
)

data class CustomListItemWithIcon(
        val listItem: CustomList.ListEntry,
        val icon: Bitmap?
)