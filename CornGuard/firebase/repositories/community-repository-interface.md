# CORNGUARD — Community Repository Interface (Draft, Sprint 0)

Provider-agnostic contract for `communityPosts/{postId}`, its `comments` and `votes`
subcollections, per `schema/logical-schema.md`. Consumed by Ligue's community feed / post /
comment UI.

## Value types

```
CommunityPost {
  post_id: string,
  user_id: string,
  linked_diagnosis_record_id: string?,
  title: string,
  body: string,
  disease_tag: string,        // one of the 4 disease classes, or "unknown"
  image_url: string?,
  barangay: string,
  municipality: string,
  province: string,
  verification_status: "unverified" | "verified" | "rejected",
  moderation_status: "visible" | "hidden" | "removed",
  upvote_count: int,          // server-computed, read-only to clients
  created_at: timestamp
}

Comment {
  comment_id: string,
  post_id: string,
  user_id: string,
  body: string,
  moderation_status: "visible" | "hidden" | "removed",
  created_at: timestamp
}

DraftPost {                   // local-only, not yet submitted
  title: string,
  body: string,
  disease_tag: string,
  image_uri: string?,
  linked_diagnosis_record_id: string?,
  approximate_location: AdministrativeArea?
}
```

## Operations

```
prefillPostFromScan(draft: ShareableScanDraft) -> DraftPost

  Builds an unsent DraftPost from a completed scan (disease type, image, approximate location,
  diagnosis reference). Per the Data and Integration Contract: "the user must still review/edit
  approved report fields before publishing" — this never calls createPost itself.

createPost(userId: string, draft: DraftPost) -> postId: string

  Always created with upvote_count=0 and moderation_status="visible" — server rules
  (security/firestore.rules) reject any other initial value for either field.

getPostsFeed(areaFilter: AdministrativeArea?, diseaseFilter: string?, cursor: string?) -> Page<CommunityPost>

  Location-aware ordering: same barangay first, then municipality, then province — per manuscript
  section 1.5.3(d). diseaseFilter narrows to a single disease_tag when set.

getPost(postId: string) -> CommunityPost?
observePost(postId: string) -> Stream<CommunityPost?>
updatePostContent(postId: string, fields: Partial<{ title, body, disease_tag, image_url }>) -> void
deletePost(postId: string) -> void

addComment(postId: string, userId: string, body: string) -> commentId: string
getComments(postId: string) -> List<Comment>
observeComments(postId: string) -> Stream<List<Comment>>

toggleUpvote(postId: string, userId: string) -> void

  Creates or deletes a votes/{userId} document under the post. upvote_count is recomputed by
  backend logic from the votes subcollection — never incremented directly by the client
  (security/access-control-matrix.md: "do not trust client-supplied aggregate count").
```

## Explicit non-goals

- No automated fact-checking or expert moderation (manuscript limitation, section 1.5.2 #5) —
  `moderation_status` changes are admin-only and out of scope for this repository's normal
  write path.
- `verification_status` on a post is reserved but has no authorized writer until D-02 resolves
  the Agricultural Technician / verifier role.
- Comment nesting is single-level only, per the Data and Integration Contract — this interface
  has no reply-to-comment operation.
- Does not implement outbreak-alert triggering — a new post may *feed into* the outbreak-rule
  evaluation (D-07), but that evaluation and any resulting notification is the FCM/backend path
  (`notifications/fcm-plan.md`), not something this repository calls directly.
