# Media providers (#1446)

Pictures of places, regions and events come from outside providers only when they can be reused
legally, and every one keeps its licence, its credit line in the provider's own form, the page it
comes from and the original's URL. This page says what each provider is asked, how a licence is
decided, how a record is tied to a place, and which providers are not in yet and why.

## Rules every provider follows

- **Documented APIs only, at the provider's pace.** One request at a time per host, at least the
  provider's `request-interval-ms` apart; a 429/502/503/504 (and MediaWiki's `maxlag`) waits for its
  `Retry-After` (`core/HttpPolicy`). No scraping, and a bot challenge is never worked around: the run
  stops with that reason.
- **No personal data on the wire.** The User-Agent is `media.user-agent` (the project and its
  repository); no e-mail, no account, no key unless the provider requires one and it is configured.
- **The licence is read from each record, never assumed**, and judged by family against the
  provider's allowlist (`cc0`, `public-domain`, `cc-by`, `cc-by-sa`; non-commercial only when listed;
  an unreadable licence and all-rights-reserved never). A stored picture whose licence a later
  `--refresh` no longer allows is kept and listed as *revoked* for a person to review.
- **Originals stay at the provider.** The stored file is the provider's own web rendition (at most
  `media.max-bytes`), the card's thumbnail is the provider's small rendition or one made locally by
  the converter, and the original's URL is recorded.
- **One import, many providers:** `histmersion media import <provider> [--dry-run] [--limit n]
  [--place id | --event id] [--refresh] [--no-wait]`. A dry run reads one batch and stores nothing;
  otherwise a server job asks every target not asked yet, batch by batch, records each batch before
  the next (a stopped job resumes), and reports a failed item as a line, not the end of the job.

| `media import …` | asks about | role | licence read from | stored when |
|---|---|---|---|---|
| `wikidata` | places, events with a Wikidata item | image, locator-map, plan, aerial-view, route-map | Commons `imageinfo` + `extmetadata` | the allowlist takes it |
| `depicts` | events with a Wikidata item | depiction | Commons `imageinfo` + `extmetadata` | the allowlist takes it |
| `met` | places on the map | object | the object's `isPublicDomain` (Open Access = CC0) | Open Access, and matched |
| `cleveland` | places on the map | object | the record's `share_license_status` (`CC0`) | CC0, and matched |
| `aic` | places on the map | object | the work's `is_public_domain` (CC0 images) | public domain, and matched |
| `loc` | places on the map | map | the item's `rights_advisory` and date | "No known restrictions" AND dated by `media.loc.max-year` (1928), and matched |

"Places on the map" are the places that hold findings, sites and features first, then by the number
of sources citing them.

## Events: pictures that depict them (`depicts`)

For each event with a Wikidata item:

1. the works its item lists as **`P1299 depicted by`** (a relief, a painting): each work's own image
   (`P18`), when `media.depicts.depicted-by` is on;
2. the Commons files whose structured data says **`P180 depicts`** the item, found by CirrusSearch's
   documented `haswbstatement:P180=Q…` keyword through the Commons action API (`list=search`,
   namespace 6) — no login, no Wikidata Query Service (WCQS needs one).

At most `media.depicts.per-event` (3), works first. The pictures are ordinary `wikimedia-commons`
assets (one already stored as the event's `P18` is linked once more, and its card shows it once);
the checks are recorded as `wikimedia-commons-depicts`.

## Museum objects (`met`, `cleveland`, `aic`)

No key. Each place on the map is searched by its name; then each record is judged in this order:

1. **its own licence statement** (above) and the collection's allowlist (`cc0,public-domain`);
2. **geography, never coordinates.** The record's place fields, most specific first — the Met's
   `excavation, locus, locale, city, county, state, subregion, region, river, country`; Cleveland's
   `find_spot`, then its `culture` entries (plain place names: "Cyprus"); Chicago's
   `place_of_origin` — are compared with the place's label and alternative names by the gazetteer's
   name key (`histmersion_place_key`: case, diacritics, punctuation and parenthesised asides folded),
   whole value against whole name, never a substring. A value marked uncertain (`?`, "probably",
   "possibly", "said to be", …) is not used. The first field that names **any** place on the map
   decides: it must name this place and no other (its equivalents — the same Wikidata item, or a
   point within `media.same-site-km` — count as one), so an object from Mycenae is not also filed
   under Greece, and "Thebes" with two Thebes on the map is refused as ambiguous;
3. **dates**: the object's range must overlap the place's period — its own when bounded at both
   ends, else the span of the findings it holds. A record or place without dates is not matched. The
   Met's years are astronomical (`-1399` = 1400 BCE) and are moved one back; Cleveland's and
   Chicago's are taken as given.

At most `per-place` (3) are kept per place, in the collection's relevance order. A dry run answers
when it is done, at the collection's pace — a Met place costs up to 21 requests — so start with
`--dry-run --limit 5` (or `--place <id>`). A refusal (HTTP 401/403) ends the batch rather than being
asked again for every place. The reason is stored
on the link (`media_links.basis`, V094) and shown by `histmersion media show`:

```
- place `pleiades_570491` — object — matched by excavation = Mycenae; 1400–1201 BCE within 1600–1100 BCE
```

A dry run lists the matches with their reasons, for a person to judge (from the test fakes):

```
**Would link (sample of 1)**
- place `pleiades_570491` (Mycenae) ← Terracotta animal figurine [`252448`](https://www.metmuseum.org/art/collection/search/252448) · `CC0-1.0` — excavation = Mycenae; 1400–1201 BCE within 1600–1100 BCE

**Not stored**
- names another place on the map: 1
- not Open Access (isPublicDomain is false): 1
```

Credit lines, in each museum's form: *Title, Artist, The Metropolitan Museum of Art, CC0* · *Title,
Creator, The Cleveland Museum of Art, Credit line, CC0* · *Title, Artist, The Art Institute of
Chicago, CC0*.

Notes per API (checked 2026-10-17):

- **The Met**: the Solr-backed `/public/collection/v1/search` was retired on 2026-10-01 (it answers
  410); search is `/v1.1/search` with `limit`, then `/v1/objects/{id}` per object (`candidates`, 20,
  per place). The stored file is `primaryImageSmall`; the thumbnail is made locally.
- **Cleveland**: one search answers the records (`cc0=1&has_image=1`); the stored file is the `web`
  rendition, the `print` one is recorded as the original.
- **Chicago**: one search with the fields asked; images through its IIIF server at 843 px (the size
  it caches) and the thumbnail at `media.thumb-px`.

## Historical maps (`loc`)

The Library of Congress JSON API: `/maps/?q=<name>&fo=json&fa=partof:geography and map division`,
then each candidate's item (`/item/{id}/?fo=json`). A map is stored only when its item's rights
advisory says **"No known restrictions"** and its date is no later than `media.loc.max-year` (1928:
US works published before 1929 are in the public domain) — both, never one. It is linked as role
`map` to the place its `location` list names (any entry, naming that place alone on the map): a shape
reference, not georeferenced. The stored file is the largest JPEG no wider than `display-px`.

**On 2026-10-17 loc.gov answered automated clients with a Cloudflare bot challenge** (HTTP 403,
`cf-mitigated: challenge`) for both the search and the documented example URLs. The provider reports
that reason and stops; it does not retry or work around it. The parsing follows the documented JSON
shape and is tested against fixtures of that shape; the first real run, when loc.gov lets API clients
through, is the check of the live shape.

## Not in this slice

| provider | why not yet | what it needs |
|---|---|---|
| Smithsonian Open Access | needs an api.data.gov key; without one its response shape could not be confirmed | a key in `media.smithsonian.api-key` (env override), default off; CC0 per media item (`usage.access`) |
| Rijksmuseum | the legacy collection API needs a key; the newer data services are a Linked Art API to be studied | a key, or a Linked Art reader; few objects are in this corpus' periods |
| David Rumsey Map Collection | no documented keyless JSON API with a per-record licence (its maps are CC BY-NC-SA, off by default) | a documented API, and `cc-by-nc-sa` listed for it |
| Europeana | needs a user's API key (`wskey`) | a key in config; the record's `edm:rights` per item |
| Flickr (Commons, CC) | needs a user's API key | a key; the photo's licence id per photo |
| Pleiades image links | Pleiades links images on other sites without a licence of its own | per-link licence lookup at the target site |
| ARIADNE / Arachne / Open Context | heterogeneous licences per record; Arachne's images are mostly not openly licensed | a per-provider reader of each record's licence; Open Context (CC BY per project) is the likeliest next |

## Configuration

`media.depicts.*`, `media.met.*`, `media.cleveland.*`, `media.aic.*`, `media.loc.*` and
`media.samples` — see the README's configuration table. A collection can be switched off with
`media.<provider>.enabled = false` (env `HISTMERSION_MEDIA_<PROVIDER>_ENABLED`); its import then
answers with that reason.
