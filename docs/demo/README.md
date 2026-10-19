# The demo collection

A small collection about the Late Bronze Age collapse and the Thera eruption, from which the
documentation's screenshots are taken (`just docs-screenshots`, see
`docs/user-guide/contributing/docs.md`). It is loaded into a **throwaway** server only, never into a
real collection.

| file | what |
|---|---|
| `dataset.json` | the sources (metadata, summary, findings, links), five events, three attestations, one article with its filter and reasons, a goal, three agent managers, the bookkeeping lists |
| `load.mjs` | loads `dataset.json` through the REST API (`PUT /api/sources/{citekey}`, `PUT /api/events/{id}`, …); refuses a server that already holds sources |
| `extras.sql` | what no endpoint writes, applied with psql to the throwaway database: the sources' licences, one museum picture with its credit line, two approvals, fixed timestamps |
| `media/` | that picture and its thumbnail |
| `converter-stub.mjs` | answers the server's health check in place of the OCR converter (nothing is converted) |

## Sources and licences

Every source is an open-access paper published under **CC BY 4.0** (the licence each DOI's version of
record carries at Crossref, checked 2026-10-19). Titles, authors and years are the publishers'. The
summaries and findings are enrichment written in Histmersion from each paper (`meyer2021_resilient`'s
summary is the paper's own abstract, reused under its licence).

| citekey | work | licence |
|---|---|---|
| `kaniewski2011_peoples` | Kaniewski et al. 2011, *The Sea Peoples, from Cuneiform Tablets to Carbon Dating*, PLoS ONE, 10.1371/journal.pone.0020232 | CC BY |
| `kaniewski2013_environmental` | Kaniewski et al. 2013, *Environmental Roots of the Late Bronze Age Crisis*, PLoS ONE, 10.1371/journal.pone.0071004 | CC BY |
| `finn2017_late` | Finné et al. 2017, *Late Bronze Age climate change and the destruction of the Mycenaean Palace of Nestor at Pylos*, PLoS ONE, 10.1371/journal.pone.0189447 | CC BY 4.0 |
| `manning2020_beyond` | Manning et al. 2020, *Beyond megadrought and collapse in the Northern Levant*, PLoS ONE, 10.1371/journal.pone.0240799 | CC BY 4.0 |
| `middlesea2021_review` | Iacono et al. 2021, *Establishing the Middle Sea*, Journal of Archaeological Research, 10.1007/s10814-021-09165-1 | CC BY 4.0 |
| `meyer2021_resilient` | Meyer & Knapp 2021, *Resilient Social Actors in the Transition from the Late Bronze to the Early Iron Age on Cyprus*, Journal of World Prehistory, 10.1007/s10963-021-09163-7 | CC BY 4.0 |
| `manning2022_thera` | Manning & Biehl 2022, *Second Intermediate Period date for the Thera (Santorini) eruption and historical implications*, PLoS ONE, 10.1371/journal.pone.0274835 | CC BY 4.0 |
| `molloy2022_there` | Molloy 2022, *Was There a 3.2 ka Crisis in Europe?*, Journal of Archaeological Research, 10.1007/s10814-022-09176-6 | CC BY 4.0 |
| `manning2023_severe` | Manning et al. 2023, *Severe multi-year drought coincident with Hittite collapse around 1198–1196 bc*, Nature, 10.1038/s41586-022-05693-y | CC BY 4.0 |
| `middleton2023_getting` | Middleton 2023, *Getting closer to the Late Bronze Age collapse in the Aegean and eastern Mediterranean*, Antiquity, 10.15184/aqy.2023.187 | CC BY 4.0 |
| `resilience2023_settlement` | Molloy et al. 2023, *Resilience, innovation and collapse of settlement networks in later Bronze Age Europe*, PLoS ONE, 10.1371/journal.pone.0288750 | CC BY 4.0 |
| `linkov2024_lessons` | Linkov et al. 2024, *Are civilizations destined to collapse? Lessons from the Mediterranean Bronze Age*, Global Environmental Change, 10.1016/j.gloenvcha.2023.102792 | CC BY 4.0 |

Six of them carry a **body**: an excerpt of the paper — its abstract and one or two sections, from
the publisher's JATS XML — with a line naming the authors, the DOI and the licence, so that the
source window, the full-text search and the event attestations have real text to show. The others
have no body. No text of any work that is not openly licensed is included.

The links between the sources are their references, as the publishers deposited them, plus one
disagreement: `manning2020_beyond` **challenges** `kaniewski2013_environmental` (Manning et al.
present Tell Tayinat as an alternative to the megadrought-and-collapse narrative). In this collection
`kaniewski2013_environmental` is therefore `contested`, and `kaniewski2011_peoples` is `unassessed`.

## The picture

*Miniature Stirrup Jar*, Cyprus, c. 1350–1300 BCE, The Cleveland Museum of Art 1916.2002, Gift of
the John Huntington Art and Polytechnic Trust — Open Access, **CC0**
(<https://clevelandart.org/art/1916.2002>). Stored as the museum collection plugin stores it: the
museum's web image, downscaled to 640 px, with its credit line, licence and the place its record
names (Cyprus).

## The rest

The events (the Thera eruption, the destructions of the Palace of Nestor, Gibala and Ugarit, the
Gordion drought) are written by hand from the findings of the sources that date them, and cite them.
The article, the goal, the managers, the approvals and the bookkeeping items are written for the demo.
The gazetteer and the period presets are the bundled ones (`db/`), imported by the server on its first
start.

To change the collection, edit `dataset.json` (the fields are the API's: `SourcePut`, `EventPut`,
`AttestationPut`, `ArticlePut`, `GoalPut`, …), run `just docs-screenshots`, and look at the pictures.
