package com.svartifoss.snfell.watch.communication

import android.graphics.Bitmap
import com.svartifoss.snfell.proto.CustomList

data class CustomListWithBitmaps(
        val listTimestamp: Long,
        val listId: String,
        val items: List<CustomListItemWithIcon>,
        val activeEntryId: String? = null,
        /** Entries the phone holds in total, which exceeds [items] when a long queue was paged.
         *  Defaults to what was received, matching a phone build that doesn't report a total. */
        val totalEntryCount: Int = items.size
)

data class CustomListItemWithIcon(
        val listItem: CustomList.ListEntry,
        val icon: Bitmap?
)