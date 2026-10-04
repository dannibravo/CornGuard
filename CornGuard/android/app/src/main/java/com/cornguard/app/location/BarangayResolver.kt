package com.cornguard.app.location

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * GPS → barangay lookup over the Bukidnon barangay boundaries bundled at
 * `assets/geo/bukidnon-barangays.json` (464 polygons, 22 municipalities — see "Map data" in
 * android/README.md). Kotlin port of caps 3's `src/services/geo-service.ts`.
 *
 * Pure JVM (kotlinx.serialization, no Android types), so it's unit-testable off-device.
 */
class BarangayResolver(geoJson: String) {

    data class Barangay(
        val name: String,
        val municipality: String,
        /** Outer ring first, then any holes; each ring is [lng, lat] pairs flattened. */
        internal val rings: List<DoubleArray>,
        internal val minLng: Double,
        internal val maxLng: Double,
        internal val minLat: Double,
        internal val maxLat: Double
    ) {
        val province: String get() = PROVINCE

        /** Plain average of the outer ring's vertices — the same "centroid" caps 3 uses. */
        val centroid: Pair<Double, Double>
            get() {
                val ring = rings.first()
                val n = ring.size / 2
                var lat = 0.0
                var lng = 0.0
                for (i in 0 until n) {
                    lng += ring[2 * i]
                    lat += ring[2 * i + 1]
                }
                return lat / n to lng / n
            }

        override fun equals(other: Any?) =
            other is Barangay && other.name == name && other.municipality == municipality

        override fun hashCode() = 31 * name.hashCode() + municipality.hashCode()
    }

    val barangays: List<Barangay> = parse(geoJson)

    /** The barangay containing (lat, lng), or null if the point is outside Bukidnon. */
    fun resolve(latitude: Double, longitude: Double): Barangay? = barangays.firstOrNull { b ->
        longitude in b.minLng..b.maxLng && latitude in b.minLat..b.maxLat &&
            contains(b.rings.first(), longitude, latitude) &&
            b.rings.drop(1).none { hole -> contains(hole, longitude, latitude) }
    }

    fun find(name: String, municipality: String): Barangay? =
        barangays.firstOrNull { it.name == name && it.municipality == municipality }

    /** All barangays sorted by name, then municipality — for the manual picker. */
    fun sorted(): List<Barangay> = barangays.sortedWith(compareBy({ it.name }, { it.municipality }))

    companion object {
        const val ASSET_PATH = "geo/bukidnon-barangays.json"
        const val PROVINCE = "Bukidnon"

        /** Ray-casting point-in-polygon test on a flattened [lng, lat] ring. */
        internal fun contains(ring: DoubleArray, x: Double, y: Double): Boolean {
            val n = ring.size / 2
            var inside = false
            var j = n - 1
            for (i in 0 until n) {
                val xi = ring[2 * i]
                val yi = ring[2 * i + 1]
                val xj = ring[2 * j]
                val yj = ring[2 * j + 1]
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
                j = i
            }
            return inside
        }

        private fun parse(geoJson: String): List<Barangay> {
            val features = Json.parseToJsonElement(geoJson).jsonObject["features"]!!.jsonArray
            return features.mapNotNull { feature ->
                val obj = feature.jsonObject
                val props = obj["properties"]!!.jsonObject
                val geometry = obj["geometry"]!!.jsonObject
                val polygons: List<JsonArray> = when (geometry["type"]!!.jsonPrimitive.content) {
                    "Polygon" -> listOf(geometry["coordinates"]!!.jsonArray)
                    "MultiPolygon" -> geometry["coordinates"]!!.jsonArray.map { it.jsonArray }
                    else -> return@mapNotNull null
                }
                // Multi-part barangays keep their largest part's outline (none exist in the
                // bundled file, which is all simple Polygons).
                val rings = polygons.maxBy { it.first().jsonArray.size }.map { ring ->
                    val points = ring.jsonArray
                    DoubleArray(points.size * 2).also { flat ->
                        points.forEachIndexed { i, p ->
                            flat[2 * i] = p.jsonArray[0].jsonPrimitive.double
                            flat[2 * i + 1] = p.jsonArray[1].jsonPrimitive.double
                        }
                    }
                }
                val outer = rings.first()
                val lngs = outer.filterIndexed { i, _ -> i % 2 == 0 }
                val lats = outer.filterIndexed { i, _ -> i % 2 == 1 }
                Barangay(
                    name = props["barangay"]!!.jsonPrimitive.content,
                    municipality = props["municipality"]!!.jsonPrimitive.content,
                    rings = rings,
                    minLng = lngs.min(), maxLng = lngs.max(),
                    minLat = lats.min(), maxLat = lats.max()
                )
            }
        }
    }
}
