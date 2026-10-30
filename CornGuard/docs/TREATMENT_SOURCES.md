# Treatment content: sources and review record

The source of truth for the treatment guidance in
`android/app/src/main/java/com/cornguard/app/data/local/db/DiseaseReferenceSeedData.kt`
(content version `2026-10-evidence-v1`).

## Review status

| Disease | Status in app | Label shown |
|---|---|---|
| Northern Leaf Blight | `sources_reviewed` | "Reviewed against published sources · Oct 2026" |
| Common Rust | `sources_reviewed` | same |
| Gray Leaf Spot | `sources_reviewed` | same |
| Healthy | `unreviewed` | none (no treatment) |

**Not yet reviewed by an agricultural expert.** The in-app disclaimer stays until someone qualified
signs off below.

## Expert sign-off

The reviewer should check the doses, intervals, product names and safety text against current product
labels and local practice.

| Name | Position / office | Date | Diseases reviewed | Notes |
|---|---|---|---|---|
| | | | | |

After sign-off, in `DiseaseReferenceSeedData.kt`, set each reviewed entry to
`ReviewStatus.EXPERT_VERIFIED`, set `reviewedBy` to the reviewer's name and office, and set
`reviewedAt` to the sign-off date. The label then reads "Verified by an agricultural expert · {name},
{Mon YYYY}" and the disclaimer is hidden. A blank `reviewedBy` keeps the lower tier, by design.

## Evidence grades

- **Established**: multi-year, multi-location field trials or long-standing extension guidance.
- **Emerging**: peer-reviewed field or greenhouse trials, but few of them, or no registered product locally.
- **Unsupported**: no corn field evidence found. Removed from the app.

## What the app says, and why

### Fungicides (established)

Crop Protection Network (CPN), *Fungicide Efficacy for Control of Corn Foliar Diseases*, table dated
02/2026 (CPN-2011). Ratings are for a single application at the labelled rate, and measure disease
control, not yield.
<https://cropprotectionnetwork.org/publications/fungicide-efficacy-for-control-of-corn-diseases>

| Active ingredient | Common rust | Gray leaf spot | N. corn leaf blight | Southern rust |
|---|---|---|---|---|
| Azoxystrobin (Quadris) | E | E | G | VG |
| Pyraclostrobin (Headline) | E | E | VG | VG |
| Propiconazole (Tilt) | VG | G | G | F |
| Azoxystrobin + propiconazole (Quilt Xcel) | VG-E | E | VG | VG |
| Mancozeb, chlorothalonil, thiophanate-methyl | not in table | | | |

*E = Excellent, VG = Very Good, G = Good, F = Fair.*

### Philippine registrations

Source: FPA *List of Registered Agricultural Pesticides*, as of 30 Sep 2026.
<https://mirrored.fpa-gov.ph/wp-content/uploads/2026/10/UPDATED-LIST-OF-REGISTERED-PRODUCTS-As-of-September-30-2026-PMID.pdf>

**Caveat:** the PDF's text columns are partly misaligned when extracted. Confirm each product on its
actual label before relying on it.

- **Azoxystrobin 250 g/L SC** (Amistar 25 SC, Syngenta, reg. 011-309-0834, and generics): corn
  sheath blight, leaf spot, leaf rust and ear rot, 2–4 tbsp/16 L; 14-day pre-harvest interval for
  corn on the Amistar row. Corn use is also confirmed on Syngenta PH's product page:
  <https://www.syngenta.com.ph/product/crop-protection/amistar-25-sc>
- **Azoxystrobin + difenoconazole 200 + 125 g/L SC** (several companies): corn "Northern corn leaf
  blight, white spot, gray leaf spot, brown spot", 500–750 mL product/ha.
- **Difenoconazole + propiconazole 150 + 150 g/L EC**: corn banded leaf and sheath blight, ear rot,
  leaf blight and rust.
- **Mancozeb 800 g/kg WP** (many generics): corn "Helminthosporium leaf spot" (*Helminthosporium* is
  the old name for the Blight fungus), 3–6 tbsp/16 L; 7-day pre-harvest interval.
- **Chlorothalonil 500–720 g/L SC**: corn Helminthosporium and Cercospora leaf spot.
- **Not used:** Tilt (propiconazole on its own), Cabrio and Fungitox (thiophanate-methyl) could not
  be matched to a corn leaf-disease registration, so the app no longer names them.

### Spray timing (established)

Kansas State Research and Extension: "the single best time to apply a fungicide to corn for gray
leaf spot control is from VT to R1" (tasseling to silking). Start scouting about two weeks before
tassels appear.
<https://eupdate.agronomy.ksu.edu/article/fungicide-management-of-gray-leaf-spot-don-t-miss-treatment-window-343>

The app uses the same window for Northern Leaf Blight and rust, combined with "spray when lesions
are spreading toward the ear leaf in favourable weather".

### Mancozeb is weaker (emerging)

A Thai field trial against Northern Leaf Blight, sprayed 7 days before inoculation, reduced
severity by 86% with mancozeb, against 92% with chlorothalonil and 90% with difenoconazole.
<https://li04.tci-thaijo.org/index.php/IJAT/article/view/6385>

### Resistance management (established principle)

Alternate or mix fungicide groups (Group 11 strobilurins, Group 3 triazoles). There is no confirmed
field resistance in these fungi. For the Gray Leaf Spot fungus only baseline studies exist (Plant
Disease, 2011):
<https://www.apsnet.org/publications/plantdisease/2011/February/Pages/95_2_189.aspx>

### Cultural control (established)

CPN disease encyclopedia (2018) pages:
- <https://cropprotectionnetwork.org/encyclopedia/northern-corn-leaf-blight-of-corn>
- <https://cropprotectionnetwork.org/encyclopedia/gray-leaf-spot-of-corn>
- <https://cropprotectionnetwork.org/encyclopedia/common-rust-of-corn>

These cover resistant hybrids, crop rotation, and reducing residue through tillage. Northern Leaf
Blight resistance can be single-gene ("Ht", which fails against some races of the fungus) or
multi-gene (protects against all races, but only partially). Resistance is specific to each
disease.

### Southern rust in the Philippines (unconfirmed)

A Philippine farm-advice site (juanmagsasaka.com, low authority) describes local corn leaf rust as
caused by *Puccinia polysora* (southern rust). The app therefore prefers azoxystrobin or a
combination product over propiconazole on its own, which is rated F for southern rust.
<https://www.juanmagsasaka.com/2020/12/corn-disease-and-disease-management.html>

## Removed from the app

| Former advice | Grade | Reason |
|---|---|---|
| "Bt corn" for blight and rust resistance | Unsupported | Bt traits target insects, not fungal diseases. |
| Baking soda + dish soap spray | Unsupported | No corn study. Inconsistent results elsewhere, with a risk of leaf burn (WSU review: <https://Puyallup.WSU.edu/wp-content/uploads/sites/403/2015/03/baking-soda.pdf>). It did help in a wheat leaf rust trial (<https://avesis.uludag.edu.tr/yayin/128a0b1f-d6e8-4678-926c-9318726f95c6/the-effect-of-sodium-bicarbonate-alone-or-in-combination-with-a-reduced-rate-of-mancozeb-on-the-control-of-leaf-rust-puccinia-triticina-in-wheat>). |
| Neem oil spray | Unsupported for these diseases | The only corn field trial found was on Southern leaf blight, with yields below mancozeb: <https://www.phytojournal.com/archives/2020.v9.i1.10556/efficacy-of-bio-agents-botanical-extracts-and-essential-oils-against-southern-leaf-blight-of-maize-zea-mays-l> |
| Garlic extract spray | Unsupported | No corn field data found. |
| "Burn all corn stalks" | Replaced | Ploughing residue under or composting it achieves the same goal (CPN). Open burning may also conflict with local rules (not verified). |
| Prices in pesos | Removed | Not verifiable. |

## Not in the app yet (emerging)

- *Bacillus velezensis* EM-A8: field trials in China gave 25–50% fewer leaves with Northern Leaf
  Blight symptoms (Plants, 2025). <https://pubmed.ncbi.nlm.nih.gov/41304616/>
- *Trichoderma* spp.: lab, greenhouse and field trials against Northern Leaf Blight.
  - <https://www.ncbi.nlm.nih.gov/pmc/articles/PMC10532967/>
  - <https://www.sciencedirect.com/science/article/abs/pii/S1049964423000701>

No FPA-registered corn product containing either was found, so neither is recommended yet.

## Known gaps

- Local prices and product availability in Bukidnon have not been verified.
- No DA or BPI corn disease guide for Mindanao was found.
- The CPN ratings come from US trials. They show which ingredients work, not local product
  performance.
