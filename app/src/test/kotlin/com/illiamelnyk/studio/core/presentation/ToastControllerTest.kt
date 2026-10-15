package com.illiamelnyk.studio.core.presentation

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToastControllerTest {
    @Test
    fun `an old expiry cannot hide newer feedback`() = runTest {
        val controller = ToastController(backgroundScope)
        controller.show(UiText.Raw("First"))
        val old = controller.message.value!!.id
        controller.show(UiText.Raw("Second"))
        controller.dismiss(old)
        assertEquals(UiText.Raw("Second"), controller.message.value!!.title)
        controller.dismiss(controller.message.value!!.id)
        assertNull(controller.message.value)
    }

    @Test
    fun `closing a modal restores the previous window without restarting feedback expiry`() = runTest {
        val controller = ToastController(backgroundScope)
        val root = controller.registerHost()
        controller.show(UiText.Raw("Saved"))
        runCurrent()
        advanceTimeBy(2_000)
        val sheet = controller.registerHost(priority = 1)
        val confirmation = controller.registerHost(priority = 2)
        assertEquals(confirmation, controller.activeHost.value)
        controller.unregisterHost(sheet)
        assertEquals(confirmation, controller.activeHost.value)
        controller.unregisterHost(confirmation)
        assertEquals(root, controller.activeHost.value)
        assertEquals(UiText.Raw("Saved"), controller.message.value!!.title)
        advanceTimeBy(2_000)
        runCurrent()
        assertNull(controller.message.value)
        controller.unregisterHost(root)
        assertNull(controller.activeHost.value)
    }

    @Test
    fun `a replacement message gets its own full lifetime`() = runTest {
        val controller = ToastController(backgroundScope)
        controller.show(UiText.Raw("First"))
        runCurrent()
        advanceTimeBy(3_000)
        controller.show(UiText.Raw("Second"))
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(UiText.Raw("Second"), controller.message.value!!.title)
        advanceTimeBy(3_000)
        runCurrent()
        assertNull(controller.message.value)
    }

    @Test
    fun `restored modal wins even when the root registers afterward`() = runTest {
        val controller = ToastController(backgroundScope)
        val sheet = controller.registerHost(priority = 1)
        val root = controller.registerHost(priority = 0)
        assertEquals(sheet, controller.activeHost.value)
        controller.unregisterHost(sheet)
        assertEquals(root, controller.activeHost.value)
    }
}
