package com.cornguard.app.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Runs the resolver against the real bundled Bukidnon boundaries file. */
class BarangayResolverTest {

    private val resolver = BarangayResolver(File("src/main/assets/${BarangayResolver.ASSET_PATH}").readText())

    @Test
    fun loadsAllBukidnonBarangays() {
        assertEquals(464, resolver.barangays.size)
        assertEquals(22, resolver.barangays.map { it.municipality }.toSet().size)
        assertEquals(464, resolver.sorted().size)
    }

    @Test
    fun pointInsideAPolygonResolvesToThatBarangay() {
        // Most polygon vertex-averages fall inside their own polygon; nearly all must round-trip.
        val roundTrips = resolver.barangays.count { b ->
            val (lat, lng) = b.centroid
            resolver.resolve(lat, lng) == b
        }
        assertTrue("only $roundTrips/464 centroids resolved to their own barangay", roundTrips >= 400)
    }

    @Test
    fun valenciaPoblacionIsFound() {
        // "Poblacion" exists in 14 municipalities, so lookups always need the municipality too.
        assertEquals(14, resolver.barangays.count { it.name == "Poblacion" })
        val poblacion = resolver.find("Poblacion", "City of Valencia")
        assertNotNull("Poblacion in Valencia missing", poblacion)
        val (lat, lng) = poblacion!!.centroid
        assertEquals(poblacion, resolver.resolve(lat, lng))
        assertEquals("Bukidnon", poblacion.province)
    }

    @Test
    fun pointsOutsideBukidnonResolveToNothing() {
        assertNull(resolver.resolve(14.5995, 120.9842))   // Manila
        assertNull(resolver.resolve(37.4220, -122.0840))  // emulator default (Mountain View)
    }
}
