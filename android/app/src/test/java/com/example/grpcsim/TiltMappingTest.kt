package com.example.grpcsim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TiltMappingTest {

    @Test
    fun `zero tilt maps to zero control`() {
        val (vx, vy) = TiltMapping.map(0.0, 0.0)
        assertEquals(0f, vx, 1e-6f)
        assertEquals(0f, vy, 1e-6f)
    }

    @Test
    fun `full tilt maps to the sign-scaled maximum`() {
        // full tilt one way => the signed maximum (SIGN_VX / SIGN_VY)
        val (vx, vy) = TiltMapping.map(
            TiltMapping.FULL_RANGE_DEG.toDouble(),
            TiltMapping.FULL_RANGE_DEG.toDouble(),
        )
        assertEquals(TiltMapping.SIGN_VX, vx, 1e-4f)
        assertEquals(TiltMapping.SIGN_VY, vy, 1e-4f)
        // full tilt the other way => the opposite, also saturated
        val (vx2, vy2) = TiltMapping.map(
            -TiltMapping.FULL_RANGE_DEG.toDouble(),
            -TiltMapping.FULL_RANGE_DEG.toDouble(),
        )
        assertEquals(-TiltMapping.SIGN_VX, vx2, 1e-4f)
        assertEquals(-TiltMapping.SIGN_VY, vy2, 1e-4f)
    }

    @Test
    fun `linear half tilt maps to half`() {
        val (vx, _) = TiltMapping.map(TiltMapping.FULL_RANGE_DEG / 2.0, 0.0)
        assertEquals(TiltMapping.SIGN_VX * 0.5f, vx, 1e-3f)
        val (vx2, _) = TiltMapping.map(-TiltMapping.FULL_RANGE_DEG / 2.0, 0.0)
        assertEquals(TiltMapping.SIGN_VX * -0.5f, vx2, 1e-3f)
    }

    @Test
    fun `over the full range is clamped`() {
        val (vx, vy) = TiltMapping.map(-90.0, 90.0)
        assertEquals(-TiltMapping.SIGN_VX, vx)
        assertEquals(TiltMapping.SIGN_VY, vy)
        assertTrue(vx in -1f..1f && vy in -1f..1f)
    }
}