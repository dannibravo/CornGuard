# Third-party notices

CornGuard bundles or displays the following third-party material.

## Barangay boundaries (admin web): `admin-web/src/assets/bukidnon-barangays.json`

The admin web's map uses more detailed outlines than the app: Bukidnon's 464 barangays from the
`hires` GeoJSON of **philippines-json-maps** v1.1.1 (https://github.com/faeldon/philippines-json-maps,
release asset `geojson-hires.zip`), PSGC 3Q 2026. Built by `admin-web/scripts/build-boundaries.mjs`:
properties are reduced to `barangay` and `municipality`, and the names are kept exactly as in the
app's file below (a few spellings differ in the source, e.g. "Kahaponan" for our "Kahapunan"), so
statistics and search match. Coordinates are rounded to 6 decimal places.

Data attribution, as the repository requests: boundaries from OCHA's Common Operational Datasets
(COD-AB) for the Philippines, produced by NAMRIA (National Mapping and Resource Information
Authority, https://namria.gov.ph/) and the Philippine Statistics Authority (PSA,
https://psa.gov.ph/classification/psgc/), licensed CC BY-IGO 3.0
(http://creativecommons.org/licenses/by/3.0/igo/legalcode). The repository's scripts and maps are
released under the MIT License ("Copyright (c) James Faeldon."; same terms as the MIT text below).

## Barangay boundaries (app): `android/app/src/main/assets/geo/bukidnon-barangays.json`

A Bukidnon-only extract (464 barangays) of `2023-10-24/enriched_t0p005/adm4.geojson` from
**barangay-boundaries-repository** (https://github.com/bendlikeabamboo/barangay-boundaries-repository).
Properties are reduced to `barangay` and `municipality`, and some names are cleaned up. See
`android/README.md`, "Where `bukidnon-barangays.json` comes from".

Data attribution, as the repository requests:

- Administrative boundaries © NAMRIA (National Mapping and Resource Information Authority),
  shapefile version 2023-11-06. https://namria.gov.ph/
- PSGC © Philippine Statistics Authority (PSA). https://psa.gov.ph/classification/psgc/

The repository is released under the MIT License:

```
MIT License

Copyright (c) 2026 bendlikeabamboo

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Leaflet 1.9.4: `android/app/src/main/assets/map/leaflet/`

https://leafletjs.com/

```
BSD 2-Clause License

Copyright (c) 2010-2023, Volodymyr Agafonkin
Copyright (c) 2010-2011, CloudMade
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

## OpenStreetMap basemap tiles (loaded at runtime, not bundled)

Map data © OpenStreetMap contributors, available under the Open Database License (ODbL).
https://www.openstreetmap.org/copyright. The attribution is shown on the map.

Tiles come from `tile.openstreetmap.org`, which is fine for development and light use under the
OSM Tile Usage Policy (https://operations.osmfoundation.org/policies/tiles/). A production release
with many users should switch to a tile provider with a suitable plan.

## Esri World Imagery satellite tiles (loaded at runtime, not bundled)

The optional Satellite view on the outbreak maps uses Esri's World Imagery and World Boundaries and
Places services from `server.arcgisonline.com`. Credit, as shown on the map: Tiles © Esri — Source:
Esri, Maxar, Earthstar Geographics, and the GIS User Community. Esri's terms of use apply
(https://www.esri.com/en-us/legal/terms/full-master-agreement). The tiles are streamed only, never
cached for offline use or redistributed. A production release with many users should check Esri's
licensing for that level of traffic.

## Lucide icons (map basemap toggle)

The "satellite" and "map" icons on the Map / Satellite button (inlined in
`android/app/src/main/assets/map/outbreak-map.html` and `admin-web/src/pages/MapPage.tsx`) are from
Lucide (https://lucide.dev), ISC License, Copyright (c) for portions of Lucide are held by Cole
Bemis 2013-2022 as part of Feather (MIT); all other copyright (c) for Lucide are held by Lucide
Contributors 2022.

## Inter 4.1 (font): `android/app/src/main/res/font/inter_*.ttf`, `admin-web/src/assets/fonts/inter-latin-*.woff2`

https://github.com/rsms/inter — Latin + Latin Extended-A subsets made with fontTools `pyftsubset`
(glyphs outside those ranges removed; no other changes). Inter declares no Reserved Font Name.

```
Copyright (c) 2016 The Inter Project Authors (https://github.com/rsms/inter)

This Font Software is licensed under the SIL Open Font License, Version 1.1.
This license is copied below, and is also available with a FAQ at:
http://scripts.sil.org/OFL

-----------------------------------------------------------
SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007
-----------------------------------------------------------

PREAMBLE
The goals of the Open Font License (OFL) are to stimulate worldwide
development of collaborative font projects, to support the font creation
efforts of academic and linguistic communities, and to provide a free and
open framework in which fonts may be shared and improved in partnership
with others.

The OFL allows the licensed fonts to be used, studied, modified and
redistributed freely as long as they are not sold by themselves. The
fonts, including any derivative works, can be bundled, embedded,
redistributed and/or sold with any software provided that any reserved
names are not used by derivative works. The fonts and derivatives,
however, cannot be released under any other type of license. The
requirement for fonts to remain under this license does not apply
to any document created using the fonts or their derivatives.

DEFINITIONS
"Font Software" refers to the set of files released by the Copyright
Holder(s) under this license and clearly marked as such. This may
include source files, build scripts and documentation.

"Reserved Font Name" refers to any names specified as such after the
copyright statement(s).

"Original Version" refers to the collection of Font Software components as
distributed by the Copyright Holder(s).

"Modified Version" refers to any derivative made by adding to, deleting,
or substituting -- in part or in whole -- any of the components of the
Original Version, by changing formats or by porting the Font Software to a
new environment.

"Author" refers to any designer, engineer, programmer, technical
writer or other person who contributed to the Font Software.

PERMISSION AND CONDITIONS
Permission is hereby granted, free of charge, to any person obtaining
a copy of the Font Software, to use, study, copy, merge, embed, modify,
redistribute, and sell modified and unmodified copies of the Font
Software, subject to the following conditions:

1) Neither the Font Software nor any of its individual components,
in Original or Modified Versions, may be sold by itself.

2) Original or Modified Versions of the Font Software may be bundled,
redistributed and/or sold with any software, provided that each copy
contains the above copyright notice and this license. These can be
included either as stand-alone text files, human-readable headers or
in the appropriate machine-readable metadata fields within text or
binary files as long as those fields can be easily viewed by the user.

3) No Modified Version of the Font Software may use the Reserved Font
Name(s) unless explicit written permission is granted by the corresponding
Copyright Holder. This restriction only applies to the primary font name as
presented to the users.

4) The name(s) of the Copyright Holder(s) or the Author(s) of the Font
Software shall not be used to promote, endorse or advertise any
Modified Version, except to acknowledge the contribution(s) of the
Copyright Holder(s) and the Author(s) or with their explicit written
permission.

5) The Font Software, modified or unmodified, in part or in whole,
must be distributed entirely under this license, and must not be
distributed under any other license. The requirement for fonts to
remain under this license does not apply to any document created
using the Font Software.

TERMINATION
This license becomes null and void if any of the above conditions are
not met.

DISCLAIMER
THE FONT SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO ANY WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT
OF COPYRIGHT, PATENT, TRADEMARK, OR OTHER RIGHT. IN NO EVENT SHALL THE
COPYRIGHT HOLDER BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
INCLUDING ANY GENERAL, SPECIAL, INDIRECT, INCIDENTAL, OR CONSEQUENTIAL
DAMAGES, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF THE USE OR INABILITY TO USE THE FONT SOFTWARE OR FROM
OTHER DEALINGS IN THE FONT SOFTWARE.
```
