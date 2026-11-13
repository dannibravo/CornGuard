# Outbreak level: method and justification

This explains how CornGuard assigns a barangay's **outbreak level** for a corn disease, and what each part is based on. Code: [`backend/convex/lib/outbreakRules.ts`](../backend/convex/lib/outbreakRules.ts) (the rule) and [`backend/convex/barangayStats.ts`](../backend/convex/barangayStats.ts) (the counting). Tests: [`backend/scripts/outbreak-rules.test.mts`](../backend/scripts/outbreak-rules.test.mts) and the "Baseline monitoring" section of [`backend/scripts/smoke-test.mjs`](../backend/scripts/smoke-test.mjs).

> **Status:** all numeric values are **development values awaiting expert approval** (decision gate D-07). Approved by: ____________________ (name, position, date).

## 1. Starting point

- **No published study gives a numeric threshold** that defines an outbreak of a corn foliar disease (Northern Leaf Blight, Common Rust, Gray Leaf Spot). The numeric thresholds that do exist are field-level fungicide decision rules (e.g. US extension guidance for Gray Leaf Spot). They are not outbreak definitions.
- **Recognised definitions of an outbreak are relative to what is normal:**
  - **WHO:** an outbreak is the occurrence of disease cases **"in excess of normal expectancy"**.
  - **DA-BPI, PhilRice and IRRI (2020), *Philippine Pest Surveillance and Early Warning Protocol for Rice*, Annex A:** "Outbreaks — large, sporadic (occasional) population increase of pest; when pest population **rises significantly above its general equilibrium level**." This is the Philippine government's own definition. The protocol is written for rice, but its outbreak definition is not crop-specific.
- **Neither definition sets a time frame.** The period is chosen for the disease and place being watched.

So CornGuard compares each barangay with **its own normal level**, and it does not claim an outbreak until it has data to know what normal is.

**Method summary (for the paper).** For areas without established historical records, CORNGUARD first collects data for at least four weeks. During this period the area's outbreak status is shown as *Undetermined* and no outbreak alerts are generated. Once sufficient data exists, the system calculates a baseline for each disease from the area's recent history. Following Serfling (1963) and Farrington et al. (1996), weeks that were themselves at outbreak level are excluded from the baseline, so that past outbreaks do not raise the expected level and mask later ones (Noufaily et al., 2013). It then compares the number of distinct farms with verified reports in a rolling seven-day period against that baseline. A disease is flagged as a potential outbreak when reports reach at least twice the baseline and a minimum number of farms. All thresholds are development values pending validation by agricultural experts.

## 2. The rule

For one barangay and one disease:

1. **What counts.** Only **verified** scans count: confident scans with a photo (auto-verified) or scans an admin verified. Scans from suspended accounts never count. A scan counts in the barangay of the **farm it was shared from** (My Farms), or else in the farmer's Settings barangay.
   - **Farms:** the number of different farmers (accounts) that reported the disease. One person's repeat scans count once.
   - **Score:** each scan adds a weight from the model's confidence: 80%+ = 1.0, 60–79% = 0.6, below 60% = 0.3.
2. **Collecting data (warm-up).** Detection starts only when the barangay has **both**:
   - at least **4 weeks** since its first verified scan, and
   - at least **10 verified scans of any kind** (healthy included) in the baseline weeks.

   Until then its outbreak status is **Undetermined**, its level stays Mild, and **no alerts are sent**. Healthy scans count here because they show farmers are actively checking their fields. There is no deadline: it is rechecked with every scan and nightly, and switches on as soon as both are met.
   - **Borrowed baseline.** If the barangay doesn't have enough data of its own, CornGuard pools it with the **barangays bordering it** (the same list used for alert recipients). If they have enough together (the same 4 weeks and 10 scans), the baseline is their **average per barangay**, counting only barangays that scanned at all, so neighbours without app users don't pull it down. It is always *provisional*, is labelled "nearby areas" for admins, and is dropped as soon as the barangay has enough data of its own. Only the baseline is borrowed: this week's count is still the barangay's own.
   - **Data-poor cluster.** If even the neighbourhood doesn't have enough data, the barangay stays Undetermined and sends no alert. But if **5 or more farms** report the same disease there in 7 days (score ≥ 4, the Severe minimums), it is listed for admins ("Clusters without a baseline") to review the scans and contact the Municipal Agriculture Office.
3. **Current period:** the last **7 days**, rolling (checked every time a scan arrives, and nightly).
4. **Baseline:** the average number of farms reporting the disease **per week**, over up to **12 earlier weeks**.
   - A week in which the barangay shared **no scans at all** is missing data and is left out. A week with scans but none of this disease is a real **0**.
   - **Outbreak weeks are left out.** A past week counts as an outbreak week if its farms would have been at least Moderate against the plain average of all the weeks (≥ 2× and ≥ 3 farms). The baseline is the average of the remaining, normal weeks. Example: 9 weeks at 3 farms and 3 weeks at 12 → plain average 5.25, the 12-farm weeks are left out, baseline **3** (Severe at 9 instead of 16). Outbreak weeks still count toward the 12 weeks of data.
   - The baseline is **provisional** until it has 12 weeks of data, then **established**. It rolls forward every week, so it follows the seasons without manual recalibration.
5. **Levels:**

| Level | Rule | Notification (automatic) |
|---|---|---|
| **Severe** | farms ≥ **3 × baseline** and ≥ **5 farms**, score ≥ 4 | Potential-outbreak warning |
| **Moderate** | farms ≥ **2 × baseline** and ≥ **3 farms**, score ≥ 2 | Awareness notice |
| **Mild** | verified reports below those levels | none |
| **Undetermined** | still collecting data | none |

| Baseline (farms / week) | 0–1.5 | 2 | 2.5 | 3 | 5 |
|---|---|---|---|---|---|
| Moderate at | 3 | 4 | 5 | 6 | 10 |
| Severe at | 5 | 6 | 8 | 9 | 15 |

Alerts go to farmers in the barangay and the barangays bordering it. They are sent only when the level goes **up**, and the same level isn't repeated for that barangay and disease within 14 days.

**Examples**
- A barangay joined CornGuard last week and 6 farms report blight → **Undetermined**. Nothing is known yet about what is normal there; no alert.
- A barangay has scanned regularly for 6 weeks and has never reported rust (baseline 0). Now 3 farms report it → **Moderate** (awareness notice); 5 farms → **Severe** (potential-outbreak warning).
- Rust is normally reported by about 2 farms a week; now 3 farms report it → **Mild**. That is its normal level. It takes 4 farms for Moderate and 6 for Severe.
- One farmer scans the same leaf 4 times (1 farm) → **Mild**. One person can't raise the level.

## 3. Each component and why

| Component | Status | Reason |
|---|---|---|
| Only **verified** scans count | Kept | Reports must be validated before they are used for alerts (IPPC ISPM 8 on the reliability of pest records; Brown et al. 2020 on validating citizen reports). |
| **Collecting-data period** before detection | **Added** | Without history there is no "normal expectancy" to exceed (WHO, DA-BPI). Treating "no history" as "normally zero" would turn the first wave of app users into outbreaks everywhere. |
| **Rolling 7-day** period | **Revised** from 14 days | A weekly comparison, as in short-baseline surveillance systems (Hutwagner et al. 2003); a rolling window gives the same weekly view without waiting for a calendar week to end. Development value (D-07). |
| **Baseline** from the barangay's own recent weeks, missing weeks skipped | **Revised** | Outbreak detection compares counts with an expected level from a baseline (Farrington et al. 1996). Skipping weeks with no scans at all keeps missing data from being read as "no disease". |
| **Borrowed baseline** from bordering barangays | **Added** | Rural barangays with few app users could otherwise stay Undetermined for months. Corn diseases spread across barangay lines, so the neighbourhood is a fair reference for "normal"; combining small areas into a larger reference area when local counts are too small is a common surveillance practice. *(Add a checked citation before the defense.)* |
| **Data-poor cluster flag** for admins | **Added** | Where nothing is known about what is normal, an automatic warning would have no basis (the launch-wave problem), but a cluster as large as Severe's minimum shouldn't go unnoticed either. A person reviews it. |
| **Outbreak weeks left out** of the baseline | **Added** | The normal level is estimated from non-epidemic weeks only (Serfling 1963), and past outbreaks are kept from inflating the expected level (Farrington et al. 1996; Noufaily et al. 2013 show that outbreaks left in the baseline reduce the detection of later ones). CornGuard uses the simplest form: leave out weeks that were at least Moderate, in one pass. Without this the baseline "creeps" up after every outbreak. |
| **2× / 3× the baseline** | **Revised** from a Poisson test | Simple to explain and audit: "reports at least twice / three times the usual weekly level". Development values (D-07). |
| **Minimum farms** 3 / 5 | Kept (raised) | Several **independent** reporters are needed, so one farmer can't raise an alert, and a tiny or zero baseline can't make 1–2 reports look like a 200% increase. Standard implementations also use a minimum-count guard (R `surveillance`, `algo.farrington`). |
| **Confidence weights** 1.0 / 0.6 / 0.3 and **score** ≥ 2 / ≥ 4 | Kept | They measure the **strength of evidence**, like the *confirmed / probable / suspected* case classification used in disease surveillance. Calibrated to the model's confidence range (its top score is near 92.5%). Development values (D-07). |
| **Admin approval** before farmers are alerted | **Revised** to automatic alerts | On the instructor's advice, alerts go out automatically when a level goes up (Moderate = awareness notice, Severe = potential-outbreak warning), with fixed message templates and a 14-day no-repeat rule. Confirmation comes at the evidence stage instead: only verified reports count. An admin can still mark an alert a false alarm and reject the scans behind it. |
| "Potential outbreak" wording | **Added** | Scan counts are surveillance signals, not confirmed case counts; the warning says "potential outbreak", and pest status is decided by the national authority (ISPM 8). |
| The name "severity" | **Revised** wording | The level describes the **outbreak in the area**, not how badly a leaf is affected. The labels stay Mild / Moderate / Severe; the map heading reads "Outbreak level". |

## 4. Limitations

1. **No outbreak alerts in the first weeks.** Every barangay starts in "collecting data", so for about the first 4 weeks after launch (and after a new barangay's first scan) no alerts are sent there, even if there is a real spike.
2. **App growth can look like disease growth.** As more farmers install the app, more reports come in. The minimum farm counts limit this; comparing the *share* of scans that show a disease would remove it, and is a possible improvement.
3. **Short, provisional baselines.** 4–12 weeks can't represent Bukidnon's cropping seasons. Once there are 2 or more years of data, the baseline should come from the same weeks of earlier years (Farrington et al. 1996). A starting baseline from MAO / DA-RCPC records would also help.
4. **Reports are voluntary.** Farmers choose which leaves to scan and share. The level is an indication to investigate, not a measured prevalence.
5. **Outbreak weeks are judged against the plain average.** A long outbreak (more than about a third of the 12 weeks) raises that average enough that some of its weeks may not be flagged. The statistical methods above handle this with models; CornGuard keeps the one-pass rule because it is easy to explain.
6. **Borrowed baselines are less specific.** A barangay may differ from its neighbours (more or less corn, different varieties). Neighbour data is picked up when the barangay recomputes (with its own scans, and nightly), so it can be up to a day behind.
7. **Model errors.** Misclassified scans that are confident enough to auto-verify are only caught when an admin reviews them.
8. **All numbers are development values** (warm-up weeks and scans, 7-day window, 12 baseline weeks, 2× / 3×, minimum farms, weights, scores) until a plant pathologist or a DA-BPI / MAO crop protection officer approves them (D-07). They are all set in one place (`OUTBREAK_RULES`).

## 5. Sources

1. **Bureau of Plant Industry (2020).** *Philippine Pest Surveillance and Early Warning Protocol for Rice* (2020 ed.). DA-BPI Crop Pest Management Division, PhilRice and IRRI (PRIME project). https://pestrisk.da.gov.ph/wp-content/uploads/2020/11/09-Philippine-Pest-Surveillance-and-Early-Warning-Protocol-ver2.0.pdf. Annex A: definitions of outbreak, epidemic and endemic; reporting to the RCPC.
2. **World Health Organization, Regional Office for the Eastern Mediterranean.** Disease outbreaks. https://www.emro.who.int/health-topics/disease-outbreaks/index.html. Outbreak as cases "in excess of normal expectancy". *Confirm the exact wording on the page before quoting.*
3. **Farrington, C. P., Andrews, N. J., Beale, A. D., & Catchpole, M. A. (1996).** A statistical algorithm for the early detection of outbreaks of infectious disease. *Journal of the Royal Statistical Society, Series A*, 159(3), 547–563. https://doi.org/10.2307/2983331. Outbreak detection as counts exceeding an expected level from a baseline, with flagged counts investigated; seasonal baselines.
4. **Hutwagner, L., Thompson, W., Seeman, G. M., & Treadwell, T. (2003).** The bioterrorism preparedness and response Early Aberration Reporting System (EARS). *Journal of Urban Health*, 80(Suppl 1), i89–i96. https://doi.org/10.1007/PL00022319. Detecting unusual counts against a short recent baseline.
5. **Brown, N., Pérez-Sierra, A., Crow, P., & Parnell, S. (2020).** The role of passive surveillance and citizen science in plant health. *CABI Agriculture and Bioscience*, 1, 17. https://doi.org/10.1186/s43170-020-00016-5. Citizen and app reports need expert validation; report counts depend on reporting effort.
6. **IPPC / FAO.** ISPM 8, *Determination of pest status in an area.* https://www.ippc.int/static/media/files/publication/en/2016/11/ISPM8.pdf. The reliability of a pest record depends on how it was identified; pest status is decided by the national authority.
7. **R package `surveillance`**, `algo.farrington` documentation. https://surveillance.r-forge.r-project.org/pkgdown/reference/algo.farrington.html. A minimum-count guard in a standard implementation.
8. **Serfling, R. E. (1963).** Methods for current statistical analysis of excess pneumonia-influenza deaths. *Public Health Reports*, 78(6), 494–506. The expected (normal) level is estimated from non-epidemic periods only. Still the basis of CDC's pneumonia-and-influenza epidemic threshold. *Check page numbers against the original.*
9. **Noufaily, A., Enki, D. G., Farrington, P., Garthwaite, P., Andrews, N., & Charlett, A. (2013).** An improved algorithm for outbreak detection in multiple surveillance systems. *Statistics in Medicine*, 32(7), 1206–1222. https://doi.org/10.1002/sim.5595. Past outbreaks in the baseline reduce the ability to detect later ones; they should be down-weighted. *Check against the original.*
10. **Unkel, S., Farrington, C. P., Garthwaite, P. H., Robertson, C., & Andrews, N. (2012).** Statistical methods for the prospective detection of infectious disease outbreaks: a review. *Journal of the Royal Statistical Society, Series A*, 175(1), 49–82. https://doi.org/10.1111/j.1467-985X.2011.00714.x. Review of current outbreak-detection methods. *Check against the original.*
