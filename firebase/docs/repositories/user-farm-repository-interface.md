# CORNGUARD — User / Farm Repository Interface (Draft, Sprint 0)

Provider-agnostic contract for profile and farm data, mirroring `users/{userId}` and
`farms/{farmId}` in `schema/logical-schema.md`. Written to stay valid under either D-03 outcome
(one farm per farmer, or many).

## Value types

```
UserProfile {
  user_id: string,
  display_name: string,
  email: string?,
  mobile_number: string?,
  barangay: string,
  municipality: string,
  province: string,
  farm_ids: List<string>
}

Farm {
  farm_id: string,
  owner_user_id: string,
  farm_name_or_label: string,
  barangay: string,
  municipality: string,
  province: string,
  latitude: float?,
  longitude: float?
}
```

## Operations

```
createUserProfile(uid: string, displayName: string, barangay: string, municipality: string, province: string) -> void

  Called once, immediately after a successful Auth registration (see
  auth-repository-interface.md). Server-side rules fix role="farmer" and
  account_status="active" at creation — this call must not attempt to set either.

getUserProfile(uid: string) -> UserProfile?
observeUserProfile(uid: string) -> Stream<UserProfile?>

updateUserProfile(uid: string, fields: Partial<{ display_name, barangay, municipality, province }>) -> void

  Only these four fields are client-writable. role, account_status, and farm_ids are rejected by
  security/firestore.rules if present in the update payload.

createFarm(ownerUserId: string, farmNameOrLabel: string, barangay: string, municipality: string, province: string, latitude: float?, longitude: float?) -> farmId: string

  Callable more than once per user — the interface does not enforce a one-farm limit. If the team
  resolves D-03 as "one farm per farmer," that constraint is a UI-layer decision (disable the
  "add another farm" action), not a change to this interface or the rules.

getFarmsForUser(uid: string) -> List<Farm>
observeFarmsForUser(uid: string) -> Stream<List<Farm>>
updateFarm(farmId: string, fields: Partial<Farm>) -> void
deleteFarm(farmId: string) -> void
```

## Explicit non-goals

- Does not decide farm cardinality (D-03) — supports 1-to-many structurally; a stricter UX can be
  layered on top without touching this contract.
- Does not expose `farm.latitude`/`farm.longitude` to any consumer other than the owner and admin
  — enforced by `security/firestore.rules`, not by this interface, but callers must not assume
  they can read another user's exact farm coordinates through this repository.
- `farm_ids` on `UserProfile` is a read-only convenience field maintained by backend logic
  (Cloud Function keeping it in sync with the `farms` collection) — callers should not attempt to
  write it directly from `updateUserProfile`.
