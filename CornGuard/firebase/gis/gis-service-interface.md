# CORNGUARD — GIS / Location Service Interface (Draft, Sprint 0)

Provider-agnostic contract between the Android map UI (Ligue) and the GIS/cloud service layer
(Panes). Deliberately contains **no map-SDK-specific types** (no Google Maps `LatLng`, no
Mapbox/OSM types) so D-10 (map SDK/provider) can be resolved later without reworking this
boundary. Types are written as plain data shapes; the actual language binding (Kotlin data
class, etc.) is Ligue's/Panes' implementation detail once D-10 lands.

## Value types

```
GeoPoint { latitude: float, longitude: float }

AdministrativeArea {
  barangay: string?,
  municipality: string?,
  province: string
}

DiseaseOccurrence {
  occurrence_id: string,
  disease_code: string,          // one of the 4 model classes
  location: GeoPoint | AdministrativeArea,  // GeoPoint only when precision is approved for display
  occurred_at: timestamp,
  verification_status: "unverified" | "verified" | "rejected",
  severity_or_weight: float?      // present only once an approved outbreak rule defines it (D-07)
}
```

## Service operations

```
getNearbyOccurrences(center: GeoPoint, radiusKm: float, diseaseFilter: string?, sinceTimestamp: timestamp?)
  -> List<DiseaseOccurrence>

  Returns occurrences from diagnosisRecordsCloud + communityPosts that are location-valid and
  within radius. Exact-coordinate precision is only returned for the requesting user's own
  records (Rule #9); other users' occurrences are area-generalized (barangay/municipality)
  unless a future approved design permits point-level display of others' data.

getHeatmapAggregates(bounds: { northEast: GeoPoint, southWest: GeoPoint }, timeWindowHours: int, diseaseFilter: string?)
  -> List<{ area: AdministrativeArea, count: int, diseaseBreakdown: Map<string, int> }>

  Aggregation only — never returns individual farmer identities or exact coordinates.

getAdministrativeAreaForPoint(point: GeoPoint) -> AdministrativeArea

  Reverse-geocoding step used when a scan/report is created with only GPS coordinates, to derive
  the barangay/municipality/province fields required by the Data Contract. The actual geocoding
  provider is part of D-10 and is swappable behind this one function.

submitOccurrenceLocation(occurrenceId: string, location: GeoPoint, area: AdministrativeArea) -> void

  Called once, at record/post creation time, to attach location metadata. Does not itself decide
  public visibility — that is governed by the Firestore/Storage rules and verification status.
```

## Explicit non-goals of this interface (pending decision gates)

- No outbreak-declaration logic lives here — `getHeatmapAggregates` returns counts only; whether
  a count crosses into "outbreak" status is D-07's job, implemented in `outbreakRules` +
  Cloud Functions, not in the GIS read path.
- No map rendering, marker styling, or tile provider concerns — those belong to Ligue's map UI
  once D-10 is resolved.
- No caching/offline-persistence strategy is specified here; per the Testing plan, cached
  community/GIS content must be visibly distinguished from the guaranteed-offline local
  diagnosis history (`claude/08_DATA_AND_INTEGRATION_CONTRACT.md`, CachedCommunityContent).
