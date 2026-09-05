package com.svartifoss.snfell.view.buttonconfig

import com.svartifoss.snfell.common.actions.StandardActions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionPickerSurfacePolicyTest {
    @Test
    fun menuCannotAssignOpenMenuToItself() {
        assertFalse(ActionPickerSurfacePolicy.allows(
                ActionPickerSurface.WATCH_MENU,
                StandardActions.ACTION_OPEN_MENU))
        assertTrue(ActionPickerSurfacePolicy.allows(
                ActionPickerSurface.WATCH_MENU,
                StandardActions.ACTION_OPEN_QUICK_ACTIONS_PANEL))
    }

    @Test
    fun quickPanelCannotAssignOpenPanelToItself() {
        assertFalse(ActionPickerSurfacePolicy.allows(
                ActionPickerSurface.QUICK_PANEL,
                StandardActions.ACTION_OPEN_QUICK_ACTIONS_PANEL))
        assertTrue(ActionPickerSurfacePolicy.allows(
                ActionPickerSurface.QUICK_PANEL,
                StandardActions.ACTION_OPEN_MENU))
    }

    @Test
    fun ordinaryButtonsCanOpenEverySurface() {
        assertTrue(ActionPickerSurfacePolicy.allows(
                ActionPickerSurface.BUTTON,
                StandardActions.ACTION_OPEN_MENU))
        assertTrue(ActionPickerSurfacePolicy.allows(
                ActionPickerSurface.BUTTON,
                StandardActions.ACTION_OPEN_QUICK_ACTIONS_PANEL))
    }
}
