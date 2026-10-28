# Source Sans 3 and Source Serif 4

The web UI's two typefaces, served by the app itself so the interface works offline (no font is fetched
from another host at run time). Source Sans 3 is the interface; Source Serif 4 the reading text of article
and source pages. Weights 400 and 600, upright only.

| file | from | release | SHA-256 |
|---|---|---|---|
| `SourceSans3-Regular.woff2` | `WOFF2/TTF/SourceSans3-Regular.ttf.woff2` | [adobe-fonts/source-sans 3.052R](https://github.com/adobe-fonts/source-sans/releases/tag/3.052R) (`WOFF2-source-sans-3.052R.zip`, SHA-256 `d7f6724027fe5ca0bab44d6121284f1fa8f66dec5f2864aa969aed347041ac95`) | `53492fb3a0def77354f166a55d09b63a10855e91c206c7620a81cf56e97f8ec3` |
| `SourceSans3-Semibold.woff2` | `WOFF2/TTF/SourceSans3-Semibold.ttf.woff2` | the same | `47b9b661b9f395fe7f0d0e119637fba5c8dad97bde3df60066fd24229c0792f4` |
| `SourceSerif4-Regular.woff2` | `source-serif-4.005_WOFF2/TTF/SourceSerif4-Regular.ttf.woff2` | [adobe-fonts/source-serif 4.005R](https://github.com/adobe-fonts/source-serif/releases/tag/4.005R) (`source-serif-4.005_WOFF2.zip`, SHA-256 `af10e80dcd2296748b04cb9917db9f7ba0ae65101165fd2f0c16b9812d9abd28`) | `6b053e98f0838afe81f3e784727be4583a7c13bb42f198dc5202ecffee0aaee0` |
| `SourceSerif4-Semibold.woff2` | `source-serif-4.005_WOFF2/TTF/SourceSerif4-Semibold.ttf.woff2` | the same | `457a13ac92e977d82e376038a2daac7fdb5433610910bf5787bdf6c1c3781865` |

The files are the releases' own, unmodified (not subset), only renamed. Both are licensed under the SIL
Open Font License 1.1, with the Reserved Font Name "Source": `SourceSans3-OFL.txt` and
`SourceSerif4-OFL.txt` are the releases' `LICENSE.md`. The `@font-face` rules are at the top of
`web/style.css`.
