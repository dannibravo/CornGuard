# CORNGUARD UI/UX CONCEPT AND DIAGRAM REVISION GUIDE

## Purpose

This document provides two separate forms of guidance for the CORNGUARD development team:

1. **Initial UI/UX Concept Guidance for Claude**
2. **Diagram Review and Revision Guidance Based on the CornGuard Capstone Study and Minutes of the Meeting (MoM)**

This document is a **development reference**, not a final design specification.

The developers — **Ligue, Panes, and Acenas** — retain the final authority over the actual UI/UX implementation, screen design, technical approach, component structure, navigation pattern, and diagram implementation.

Claude may propose designs and technical representations, but it must not treat its own proposal as final or approved unless the developers explicitly approve it.

---

# PART I — INITIAL UI/UX CONCEPT GUIDANCE

## 1. UI/UX Status

CORNGUARD does **not yet have an approved or final UI/UX prototype**.

Any existing screenshots, sample layouts, wireframes, or interface illustrations in the study should be treated as **conceptual references only** unless the developers formally adopt them.

Claude is allowed to create an **initial UI/UX proposal** based on the current system requirements, system workflow, target users, features, architecture, and practical limitations described in the capstone study.

The proposal must remain flexible.

### Final UI/UX Authority

The final design decisions belong to:

- Ligue
- Panes
- Acenas

The developers may:

- Modify Claude's proposed layouts
- Replace navigation patterns
- Rearrange screens
- Change visual hierarchy
- Simplify workflows
- Change component placement
- Improve usability
- Redesign individual screens
- Adjust colors, typography, spacing, iconography, or branding
- Change a suggested implementation when required by technical limitations

Any redesign must still preserve the approved functional requirements, system purpose, required workflows, and capstone scope.

---

# 2. Claude UI/UX Design Instruction

When asked to design CORNGUARD's UI/UX, Claude must follow the instruction below.

## Recommended Prompt for Claude

> You are helping design an initial UI/UX concept for **CORNGUARD**, an Android mobile application for real-time and offline corn leaf disease detection using MobileNetV2 and TensorFlow Lite, with disease history, treatment recommendations, GIS-based disease monitoring, heatmap visualization, community reporting, and outbreak notifications.
>
> There is currently **no approved final UI/UX prototype**.
>
> Your task is to propose an initial interface concept based on the CORNGUARD capstone study, development documentation, system requirements, architecture, workflows, and target users.
>
> The proposed design must prioritize:
>
> - Usability
> - Simplicity
> - Clarity
> - Responsiveness
> - Readability
> - Low learning difficulty
> - Minimal number of steps
> - Clear visual hierarchy
> - Practical field use
> - Accessibility for users with limited digital literacy
>
> CORNGUARD is intended primarily for corn farmers, including users who may use low-end Android smartphones and may have limited or unstable internet connectivity.
>
> Preserve the system's **offline-first design**:
>
> - Disease scanning must work offline.
> - TensorFlow Lite inference must work offline.
> - Treatment recommendations must remain available offline.
> - Local scan history must remain available offline.
> - Online community features must clearly indicate when internet connectivity is required.
> - Cloud synchronization, community posting, cloud images, GIS outbreak updates, and push notifications must fail gracefully when offline.
>
> Treat your design as a **reference or initial proposal only**.
>
> Do not state that the design is final or approved.
>
> The developers — Ligue, Panes, and Acenas — have the final decision on actual implementation and may modify or redesign your proposal.
>
> Before proposing screens, first review:
>
> - CORNGUARD system requirements
> - System architecture
> - Current use cases
> - ERD/database requirements
> - Disease detection workflow
> - Offline/online boundaries
> - Community reporting workflow
> - GIS and outbreak monitoring requirements
> - Minutes of the Meeting, especially comments affecting monitoring, validation, alerts, and user workflows
>
> Then provide:
>
> 1. Recommended information architecture
> 2. Suggested navigation structure
> 3. Suggested list of screens
> 4. Purpose of each screen
> 5. Main components on each screen
> 6. Primary user actions
> 7. Offline and online behavior
> 8. Empty, loading, success, error, and permission states
> 9. Suggested visual hierarchy
> 10. Accessibility and usability considerations
> 11. Responsive behavior for different Android screen sizes
> 12. Any design decision that should remain open for developer approval
>
> Do not write implementation code unless specifically requested.
>
> Do not invent features outside the CORNGUARD study or approved development documentation.

---

# 3. UI/UX Design Principles

The initial CORNGUARD UI should follow these principles.

## 3.1 Simple First

The app should avoid unnecessary menus, nested settings, or complicated controls.

Important actions should be immediately visible.

The disease scanning function should be one of the most visible actions because it is the main practical function of the application.

## 3.2 Field-Friendly Design

The interface will be used in real farming environments.

Design for:

- Outdoor lighting
- One-handed mobile use
- Users wearing gloves or working with dirty hands
- Short attention periods during farm work
- Intermittent mobile data
- Older or low-end smartphones

Use sufficiently large touch targets and avoid tiny interactive elements.

## 3.3 Clear Language

Use short and understandable labels.

Avoid unnecessary technical terms such as:

- CNN
- Tensor
- Inference
- Softmax

These concepts may exist internally, but the farmer-facing interface should use practical wording such as:

- Scan Leaf
- Disease Result
- Confidence
- Treatment Guide
- Disease Map
- Nearby Reports

## 3.4 Strong Offline Awareness

The user should always understand whether a feature is available offline.

Examples:

- "Available offline"
- "Internet required"
- "Saved locally"
- "Will sync when connected"

The app must not make the user believe a cloud action succeeded if it has not yet synchronized.

## 3.5 Action-Oriented Results

Disease results should not stop at a disease name.

A result screen should make it easy to continue to:

- View treatment guidance
- Save/view the diagnosis
- Review location
- Share/report the finding when online
- View disease activity in the surrounding area

## 3.6 Avoid Alarmist Language

Because disease reports and outbreak information can affect farmer decisions, the interface should not display unverified reports as confirmed outbreaks.

Use clear states such as:

- AI Detection
- Community Report
- Pending Verification
- Verified
- Monitoring
- Confirmed Outbreak

The exact statuses must follow the final validation process approved by the project team and agricultural validators.

---

# 4. Suggested Information Architecture

The following structure is recommended as an initial proposal.

It is not mandatory.

## Primary Farmer Navigation

A simple bottom navigation may contain:

1. **Home**
2. **Scan**
3. **Map**
4. **Community**
5. **Profile**

Alternative layouts may be used if the developers determine that four tabs, a navigation drawer, or another structure is more usable.

### Important Rule

The Scan function should remain reachable in very few actions.

The study's usability requirements emphasize minimal interaction before reaching the camera workflow.

---

# 5. Suggested Farmer Screens

## 5.1 Splash / Launch Screen

Purpose:

- Display CORNGUARD identity
- Initialize local resources
- Load model
- Check app state
- Check authentication state when needed

Avoid long loading animations.

## 5.2 Welcome / Onboarding

Optional.

Only include onboarding if it improves first-time usability.

Possible content:

- Scan corn leaves
- Get offline results
- View treatment information
- Monitor nearby disease reports
- Join community reporting when online

Keep onboarding short.

A skip option is recommended.

## 5.3 Login / Registration

Required for online community functions if Firebase Authentication is used.

Possible registration information:

- Name
- Email or mobile number
- Password
- Farm/location information required by the approved schema

Do not collect unnecessary personal data.

The developers must finalize which farm and location fields are mandatory after database and MoM alignment.

## 5.4 Home Dashboard

Recommended high-priority actions:

- Scan Corn Leaf
- Disease History
- Disease Map
- Community / Nearby Reports

Additional small sections may show:

- Last scan
- Offline/online status
- Recent nearby disease activity
- Pending synchronization

Do not overcrowd the home screen.

## 5.5 Scan / Camera Screen

Primary components:

- Camera preview
- Leaf framing guide
- Capture button
- Upload from gallery
- Short photo quality guidance
- Camera permission state

Recommended guidance:

- Use one leaf if possible
- Keep the leaf inside the frame
- Avoid excessive blur
- Use adequate lighting

Do not make these instructions too long.

## 5.6 Image Review Screen

Before analysis, optionally allow:

- Retake
- Use Photo

This reduces accidental low-quality scans.

If the team prefers faster scanning, this step may be skipped.

## 5.7 Processing State

The user should see a short progress indicator while the local TensorFlow Lite model processes the image.

Example:

"Analyzing corn leaf..."

Avoid showing technical implementation details.

## 5.8 Diagnosis Result Screen

Recommended information:

- Detected class
- Confidence score
- Result type
- Scan time
- Location status if captured
- Save status

Target model classes:

- Common Rust
- Gray Leaf Spot
- Northern Leaf Blight
- Healthy

Primary actions:

- View Treatment Guide
- View Scan History
- Share / Report to Community
- Scan Another Leaf

Important:

The UI should communicate that AI-based classification is a decision-support tool and does not replace agricultural expert diagnosis.

## 5.9 Treatment Recommendation Screen

Recommended content:

- Disease name
- Short explanation
- General management steps
- Preventive practices
- Important caution/disclaimer
- Offline availability indicator

Treatment recommendations must come from approved agricultural references or validated reference data.

Do not allow Claude to invent pesticide instructions or agricultural treatments beyond the verified project reference content.

## 5.10 Scan History Screen

The study requires historical diagnosis records.

Recommended list information:

- Date
- Disease label
- Confidence
- Farm
- Barangay/location
- Thumbnail if available
- Sync status

Recommended filters:

- Disease
- Date
- Farm

The final filtering scope may be simplified if needed.

## 5.11 Diagnosis Detail Screen

Shows a historical record in full.

Possible information:

- Captured image
- Detected disease
- Confidence
- Date/time
- Farm
- Location
- Treatment guidance
- Community report linkage
- Verification status if applicable

## 5.12 Farm Management Screen

The MoM specifically requires disease records to be associated with farms and farm owners.

A practical UI may therefore include:

- Farm name
- Farm owner
- Barangay
- Municipality/city
- Province
- GPS location if available

Users may have one or multiple farms depending on the final approved requirements.

This must be finalized before implementation.

## 5.13 Disease Map / GIS Screen

Recommended components:

- Map
- Disease markers
- Heatmap layer
- Current location
- Nearby report list
- Disease filter
- Date filter
- Verification/status indicator

Location hierarchy may include:

Province → Municipality/City → Barangay → Farm/GPS Point

Important:

Do not visually treat every community report as a confirmed outbreak.

Markers should distinguish report types and verification states.

## 5.14 Community Feed

Recommended post information:

- User/farm display name as approved
- Disease tag
- Location
- Timestamp
- Photo
- Short report
- Verification status
- Upvotes/helpfulness
- Comment count

Filters may include:

- Nearby
- Disease
- Barangay
- Municipality
- Verified reports

The final feed algorithm is a developer decision.

## 5.15 Create Disease Report

Possible fields:

- Title
- Description
- Disease tag
- Photo
- Farm
- Location
- Related scan result
- Submit

When launched from a completed scan, the system may pre-fill:

- Disease type
- Captured image
- Approximate location
- Diagnosis reference

The user should still review the report before submission.

## 5.16 Community Post Detail

Recommended components:

- Original report
- Status
- Photo
- Location
- Disease tag
- Comments/thread
- Expert/technician response if implemented
- Verification information

Avoid presenting community advice as official treatment guidance.

## 5.17 Notifications

Possible notification types:

- Nearby disease report
- Verified disease case
- Monitoring alert
- Outbreak alert
- Community reply
- Verification update

Alert severity and outbreak wording must depend on the approved outbreak validation rules.

## 5.18 Profile / Settings

Possible functions:

- Account profile
- Farm management
- Location settings
- Notification preferences
- Data synchronization status
- About CORNGUARD
- Logout

Keep settings limited to useful functions.

---

# 6. Admin / Monitoring UI Concept

The capstone and MoM indicate administrator-level management and monitoring needs.

However, the exact final administrator platform and scope must be confirmed by the developers.

The MoM also suggests considering centralized or web-based monitoring for administrators and stakeholders.

Therefore, Claude may propose an **Admin Monitoring UI concept**, but it must be labeled as:

**Proposed / Pending Final Architecture Decision**

Possible functions:

- User account management
- Community moderation
- Disease report monitoring
- Report verification workflow
- Disease map and outbreak monitoring
- Reference data management
- Notification monitoring
- Farm/diagnosis records
- Analytics or disease occurrence summaries

Do not automatically implement a separate web application unless the team formally approves it.

---

# PART II — DIAGRAM REVIEW AND REVISION GUIDANCE

# 7. Source Priority for Diagram Revision

Before revising any CORNGUARD diagram, Claude and the developers must review sources in this order:

1. **Minutes of the Meeting (MoM)**
2. Current CORNGUARD capstone study
3. Approved team decisions resolving conflicts
4. Necessary technical corrections for consistency

The diagrams must not be revised simply because a different design looks better.

Every change must have a traceable reason.

---

# 8. Major MoM Requirements Affecting Diagrams

The panel comments require the diagrams to reflect several concepts more clearly.

These include:

- Disease verification procedures
- Qualified agricultural validators
- Field validation
- Farm-related information
- Farm owners
- Farm locations
- Multiple diagnosis records per farm
- Disease occurrence monitoring
- Geographic disease monitoring
- Heatmaps/location indicators
- Community-based disease monitoring
- Nearby farmer alerts
- Structured disease reporting
- Safeguards against false reports
- Confidence thresholds or validation conditions
- Outbreak declaration criteria
- Expert validation
- Disease monitoring over time
- Better database relationships
- Correct DFD data-flow notation
- Lower-level DFDs
- Clear system architecture
- Centralized monitoring considerations

These requirements should guide diagram updates.

---

# 9. System Architecture Diagram

## Current Issue

The current architecture already shows:

- Farmer
- Android application
- Offline AI Detection
- Community Module
- Firebase
- Cloud database
- Cloud storage
- Notification service
- Admin

However, it does not fully communicate all newly emphasized farm-level, local-storage, verification, GIS, and outbreak-monitoring responsibilities.

## Required Revision Direction

The revised architecture should clearly separate:

### A. On-Device / Offline Layer

- Android Application
- Camera / Gallery
- Image preprocessing
- TensorFlow Lite MobileNetV2 model
- Disease classification
- Local treatment reference
- SQLite/local diagnosis history
- Local farm information needed for offline records

### B. Online / Cloud Layer

- Firebase Authentication
- Firestore / approved Firebase database
- Cloud Storage
- Firebase Cloud Messaging
- Community posts and comments
- Cloud diagnosis synchronization
- Farm/location synchronization
- GIS/outbreak data
- Verification/moderation information
- Notifications

### C. Monitoring / Validation Layer

Depending on the final team decision:

- System Administrator
- Agricultural Technician / Expert Validator
- Agricultural Office / Stakeholder monitoring interface

Do not add a new actor to the final architecture until the team confirms its actual system role.

## Why

The MoM requires clearer architecture, expert-based verification, farm-level monitoring, location-based disease analysis, and stronger outbreak monitoring.

---

# 10. Context Diagram / Level 0 DFD

## Current Problem

The panel explicitly requested that the DFD use proper data-oriented flows rather than action-oriented commands.

## Revision Requirements

The Level 0 DFD should show external entities and the data exchanged with CORNGUARD.

Potential external entities:

- Farmer
- Administrator
- Agricultural Validator / Technician — only if formally adopted
- Model Training Process — if the team keeps it as an external process

### Example Data Inputs

From Farmer:

- Account data
- Farm data
- Leaf image
- Location data
- Community report data
- Comments
- Feedback

From Admin:

- User management data
- Moderation decisions
- Reference data updates
- Verification/moderation data

From Validator if included:

- Disease verification result
- Expert validation data
- Outbreak assessment

### Example Outputs

To Farmer:

- Diagnosis result
- Confidence score
- Treatment information
- History data
- Disease map data
- Community reports
- Notifications
- Verification status

To Admin:

- User records
- Disease reports
- Community content
- Monitoring data
- Outbreak information

Important:

Flow names should represent **data**, not commands such as:

- "Manage User"
- "Create Post"
- "Scan Leaf"

Prefer labels such as:

- User Account Data
- Community Post Data
- Leaf Image
- Diagnosis Result
- Verification Result
- Location Data
- Notification Data

---

# 11. Level 1 DFD

## Required Processes

The Level 1 DFD should clearly represent major system processes such as:

1. User and Farm Management
2. Image Capture and Preprocessing
3. Disease Detection
4. Diagnosis and Treatment Management
5. Community Reporting and Discussion
6. GIS Disease Monitoring
7. Verification and Outbreak Assessment
8. Notification Management
9. Synchronization / Offline Data Handling

The exact numbering may be changed by the team.

## Required Data Stores

The DFD should represent data stores consistent with the final database model.

Likely stores include:

- Users
- Farms
- Diagnosis Records
- Disease Reference
- Locations
- Community Posts
- Comments
- Notifications
- Verification Records
- Outbreak/Monitoring Records if separated

Do not create a separate store unless it has a defined purpose in the final schema.

---

# 12. Level 2 DFDs

The MoM explicitly requested lower-level DFDs where necessary.

At minimum, the team should strongly consider Level 2 diagrams for the following processes.

## 12.1 Disease Detection

Suggested subprocesses:

1. Receive leaf image
2. Validate/prepare image
3. Resize and normalize image
4. Run TensorFlow Lite inference
5. Generate class probabilities
6. Select classification result
7. Retrieve treatment reference
8. Save local diagnosis
9. Prepare optional synchronization/report data

## 12.2 Disease Management

Suggested subprocesses:

1. Store diagnosis
2. Associate diagnosis with farm
3. Associate location
4. Retrieve history
5. Retrieve disease reference
6. Synchronize diagnosis when online
7. Provide diagnosis data for monitoring

## 12.3 Community Reporting

Suggested subprocesses:

1. Receive report data
2. Attach related diagnosis if available
3. Attach farm/location
4. Store report
5. Receive comments/replies
6. Apply moderation/verification status
7. Provide report data to feed
8. Pass qualified report data to monitoring/alert process

## 12.4 GIS / Outbreak Monitoring

Suggested subprocesses:

1. Receive geotagged diagnosis/report data
2. Filter eligible/validated records
3. Group records geographically
4. Calculate monitoring/outbreak condition
5. Generate map/heatmap data
6. Produce alert candidate
7. Require validation when defined
8. Send notification data

The exact outbreak algorithm and threshold must be approved before this diagram is finalized.

---

# 13. Use Case Diagram

## Current Concern

The current study primarily identifies Farmer and Administrator, while other sections discuss an optional Agricultural Technician role.

The MoM also asks for agricultural experts, technicians, or experienced farmers to participate in validation.

This creates an actor-definition decision that must be resolved.

## Required Revision Direction

### Farmer Use Cases

The final diagram should consider:

- Register / Login
- Manage profile
- Manage farm information
- Capture/upload leaf image
- Run disease detection
- View diagnosis result
- View confidence
- View treatment recommendation
- Save/view diagnosis history
- View GIS disease map
- View heatmap
- View nearby disease reports
- Submit community report
- Comment/reply
- Receive notifications

### Administrator Use Cases

Depending on final implementation:

- Manage users
- Moderate community content
- Maintain disease reference data
- Monitor disease reports
- Monitor GIS/outbreak information
- Manage/monitor notification events
- Review flagged reports

### Agricultural Technician / Validator

Only add this actor after the team confirms how validation will work.

Possible use cases:

- Review disease report
- Verify disease occurrence
- Provide expert response
- Approve/reject verification
- Provide outbreak assessment

## Why Revision Is Needed

The final Use Case Diagram must match the real user roles and must not contradict the architecture, database, or community workflow.

---

# 14. ERD / Database Diagram

## Current Problem

The MoM specifically requests stronger farm-level relationships.

The current schema focuses on:

- User
- Diagnosis Records
- Disease Reference
- Location
- Community Posts
- Comments
- Notifications

A dedicated farm relationship is not sufficiently represented.

## Recommended Revision

Add or formalize a **FARM** entity.

Suggested relationship direction:

USER
→ owns one or more FARM records

FARM
→ has one or more DIAGNOSIS_RECORD records

FARM
→ has LOCATION information

DIAGNOSIS_RECORD
→ references DISEASE_REFERENCE

DIAGNOSIS_RECORD
→ may reference LOCATION

COMMUNITY_POST
→ belongs to USER
→ may reference FARM
→ may reference DIAGNOSIS_RECORD
→ may reference LOCATION

COMMENT
→ belongs to COMMUNITY_POST
→ belongs to USER

NOTIFICATION
→ belongs to USER
→ may reference COMMUNITY_POST / outbreak event

## Possible Additional Entities

These should only be created if needed after final workflow decisions:

### VERIFICATION_RECORD

Useful when expert validation needs:

- Validator
- Report
- Status
- Notes
- Date

### OUTBREAK_EVENT / MONITORING_EVENT

Useful when the system needs to separate a verified/declared outbreak from ordinary diagnosis records.

Potential fields:

- Disease
- Geographic scope
- Detection count
- Threshold
- Status
- Verification status
- Start date
- Alert date

Do not add these automatically.

The team should first finalize how outbreak validation and alert declaration will work.

---

# 15. GIS / Disease Mapping Diagram

If a separate GIS workflow or component diagram is maintained, it should show:

Input:

- Diagnosis location
- Farm location
- Disease class
- Timestamp
- Confidence
- Verification status

Processing:

- Geographic grouping
- Location filtering
- Heatmap generation
- Disease filtering
- Time-window filtering
- Outbreak rule evaluation

Output:

- Disease markers
- Heatmap
- Nearby cases
- Monitoring status
- Alert data

Important:

Community posts and AI detections should not automatically be treated as confirmed outbreak records unless they satisfy the approved validation process.

---

# 16. Community Reporting Workflow Diagram

A revised community workflow should show more than a simple forum.

Recommended flow:

Farmer
→ Scan Leaf or Create Manual Report
→ Add Farm/Location Data
→ Submit Report
→ Store Community Report
→ Moderation / Verification Process
→ Nearby Disease Monitoring
→ Alert Eligibility Check
→ Notification when approved criteria are satisfied

Discussion comments can continue independently of outbreak verification.

This matches the MoM requirement that community disease monitoring go beyond a basic discussion forum.

---

# 17. Disease Verification / Outbreak Alert Workflow

The MoM strongly requires safeguards against false alarms.

The development team should create a dedicated workflow once the validation rules are approved.

A conceptual structure is:

Detection / Community Report
↓
Check required data
↓
Check model confidence or report quality
↓
Check repeated detections / related cases
↓
Expert or approved validation step when required
↓
Monitoring status
↓
Outbreak criteria evaluation
↓
Alert approved?
↓
Yes → Notify affected/nearby users
No → Continue monitoring

Important:

Claude must not invent final confidence thresholds, number of reports, radius, time window, or outbreak declaration values.

These must come from agricultural expert consultation or an approved team decision.

---

# 18. Gantt Chart / Development Timeline

The project timeline should be reviewed because the MoM introduces work that may require explicit scheduling.

Recommended activities to represent if not already present:

- Dataset source validation
- Local dataset collection
- Agricultural expert consultation
- Disease verification procedure definition
- Outbreak threshold definition
- Database/ERD revision
- DFD revision
- Architecture revision
- Mobile development
- Model training
- TFLite integration
- Firebase/community implementation
- GIS/heatmap implementation
- Field testing
- Agricultural validation
- UAT with farmers
- IT expert evaluation
- Bug fixing
- Final documentation

The team may keep the existing timeline if these activities are already covered, but the final chart should clearly represent required validation and revision work.

---

# 19. Diagram Consistency Checklist

Before approving any revised diagram, verify:

## Roles

- Are the same users/actors used consistently?
- If Agricultural Technician exists, is the role defined everywhere?
- Does Admin have only functions that actually exist?

## Farm Data

- Is farm information represented?
- Can one farm have multiple diagnosis records?
- Are diagnosis records linked to a farm and location?

## Offline Architecture

- Is local diagnosis possible without Firebase?
- Is TensorFlow Lite clearly on-device?
- Is SQLite/local storage represented?

## Online Architecture

- Are Firebase features shown only for online/cloud functions?
- Are community posts, images, sync, and notifications represented correctly?

## Disease Monitoring

- Are diagnosis records linked to GIS data?
- Is map/heatmap generation represented?
- Are reports distinguishable from verified outbreak information?

## Community

- Does community reporting support disease/location information?
- Is the forum more than generic discussion?
- Is nearby reporting represented?

## Verification

- Are false-report safeguards represented?
- Is there a clear verification status or process?
- Are outbreak alerts prevented from triggering directly from a single unverified report unless that is an approved rule?

## DFD Quality

- Are data flows nouns/data labels?
- Are processes verbs/process names?
- Are data stores connected correctly?
- Does Level 1 match Level 0?
- Do Level 2 diagrams decompose the correct Level 1 processes?

## Database

- Does the ERD match the real data flow?
- Do optional relationships remain optional?
- Are IDs and foreign-key relationships logical?

---

# 20. Decision Gates Before Final Diagram Approval

The team must resolve the following before treating the revised diagrams as final.

## DG-UI-01 — Final UI/UX

Claude may propose.

Developers decide.

No Claude-generated UI becomes final automatically.

## DG-DIAGRAM-01 — Agricultural Technician Role

Decide whether Agricultural Technician is:

- A system user
- A validator only during evaluation
- An administrator subtype
- Not an application actor

Update all diagrams consistently afterward.

## DG-DIAGRAM-02 — Farm Ownership Model

Decide:

- One farm per farmer?
- Multiple farms per farmer?

Then finalize the FARM relationship.

## DG-DIAGRAM-03 — Cloud Database Choice

The study refers to Firebase Firestore / Realtime Database in different places.

Choose the official cloud data implementation before final schema documentation.

## DG-DIAGRAM-04 — Outbreak Verification

Define:

- Who verifies
- What data is required
- Whether expert approval is mandatory
- What statuses exist

## DG-DIAGRAM-05 — Outbreak Threshold

Do not hardcode or diagram arbitrary values.

The threshold should be based on agricultural expert recommendations and team approval.

## DG-DIAGRAM-06 — Alert Radius / Geographic Scope

Decide whether notifications are based on:

- Same barangay
- Same municipality
- Configurable radius
- Other approved geographic logic

## DG-DIAGRAM-07 — Admin Platform

Decide whether Admin uses:

- Firebase console only
- Android admin interface
- Separate web monitoring dashboard
- Another approved interface

The MoM suggests exploring centralized/web monitoring, but this should not be assumed as final implementation without team approval.

---

# 21. Instructions to Claude When Revising Diagrams

Use this prompt:

> Review the CORNGUARD capstone study and the Minutes of the Meeting before proposing any diagram revision.
>
> Treat the MoM as the primary source for panel-requested changes.
>
> Do not redesign diagrams based only on preference.
>
> For each diagram:
>
> 1. State what the current diagram represents.
> 2. Identify specific issues or missing elements.
> 3. Cite the study requirement or MoM comment that justifies the revision.
> 4. Explain what should change.
> 5. Explain how the revised diagram should represent the system.
> 6. Identify dependencies or decisions that must be resolved first.
> 7. Mark anything uncertain as a decision gate instead of inventing an answer.
>
> Review at least:
>
> - System Architecture
> - Context / Level 0 DFD
> - Level 1 DFD
> - Required Level 2 DFDs
> - Use Case Diagram
> - ERD / Database Schema
> - GIS / Disease Mapping workflow
> - Community Reporting workflow
> - Verification / Outbreak Alert workflow
> - Project timeline / Gantt Chart when affected
>
> Ensure all diagrams remain consistent with:
>
> - The approved CORNGUARD system scope
> - Offline TensorFlow Lite disease detection
> - SQLite/local data behavior
> - Firebase online services
> - Farm-linked diagnosis history
> - GIS and heatmap monitoring
> - Community disease reporting
> - Verification safeguards
> - Nearby outbreak notifications
>
> Do not produce final implementation code.
>
> The developers — Ligue, Panes, and Acenas — make the final implementation decisions.

---

# 22. Final Rule

CORNGUARD development documentation should distinguish between:

### Approved Requirement

Something explicitly supported by the study, MoM, or a documented team decision.

### Proposed Design

A recommendation from Claude or a developer that has not yet been formally approved.

### Decision Gate

A requirement conflict, missing value, undefined role, or technical choice that must be resolved before implementation is finalized.

This distinction must be preserved throughout UI/UX design, diagram revision, implementation, and testing.

---

# 23. Summary for Developers

For UI/UX:

- Claude creates the first concept.
- Claude must design for farmers and field use.
- Claude's UI is only a reference.
- Ligue, Panes, and Acenas make the final design decisions.

For diagrams:

- Read the MoM first.
- Do not make arbitrary changes.
- Fix DFD notation and add necessary Level 2 diagrams.
- Strengthen farm and location relationships.
- Represent GIS and disease monitoring correctly.
- Represent community disease reporting as more than a forum.
- Add a validation/verification flow before outbreak alerts.
- Resolve the technician/validator role before finalizing actors.
- Make architecture, use cases, ERD, DFDs, workflows, and timeline consistent with one another.
