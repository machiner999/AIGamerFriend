package com.example.aigamerfriend.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PermissionHelperTest {
    private val context = mockk<Context>()

    @Before
    fun setup() {
        mockkStatic(androidx.core.content.ContextCompat::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(androidx.core.content.ContextCompat::class)
    }

    @Test
    fun `REQUIRED_PERMISSIONS contains CAMERA and RECORD_AUDIO`() {
        val permissions = PermissionHelper.REQUIRED_PERMISSIONS.toList()
        assertEquals(2, permissions.size)
        assertTrue(permissions.contains(Manifest.permission.CAMERA))
        assertTrue(permissions.contains(Manifest.permission.RECORD_AUDIO))
    }

    @Test
    fun `hasAllPermissions returns true when all permissions granted`() {
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(context, any())
        } returns PackageManager.PERMISSION_GRANTED

        assertTrue(PermissionHelper.hasAllPermissions(context))
    }

    @Test
    fun `hasAllPermissions returns false when camera denied`() {
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        } returns PackageManager.PERMISSION_DENIED
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        } returns PackageManager.PERMISSION_GRANTED

        assertFalse(PermissionHelper.hasAllPermissions(context))
    }

    @Test
    fun `hasAllPermissions returns false when audio denied`() {
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        } returns PackageManager.PERMISSION_GRANTED
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        } returns PackageManager.PERMISSION_DENIED

        assertFalse(PermissionHelper.hasAllPermissions(context))
    }

    @Test
    fun `hasAllPermissions returns false when both denied`() {
        every {
            androidx.core.content.ContextCompat.checkSelfPermission(context, any())
        } returns PackageManager.PERMISSION_DENIED

        assertFalse(PermissionHelper.hasAllPermissions(context))
    }
}
