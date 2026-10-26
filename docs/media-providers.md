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
  A provider that needs a key is **off by default** and gets the user's own key — a free one; none
  that would make each user pay a third party is built — as a secret reference, sent only as a header
  to its API (never in a URL, never to an image host).
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
| `pleiades` | places on the map with a Pleiades id | image | Commons `imageinfo` + `extmetadata` of each linked file | the place's Pleiades record links the Commons file, and the allowlist takes it |
| `opencontext` | places on the map | object | the record's `dc-terms:license` (a CC URL), read again from its media item | the allowlist takes it, not flagged as human remains, and matched (by id or name, and dates) |
| `smithsonian` (a key; off by default) | places on the map | object | each image media item's `usage.access` (`CC0`) | an image of the record is itself CC0, not a natural-history unit's, made by `media.smithsonian.max-year` (1500), and matched |
| `europeana` (a key; off by default) | places on the map | object | the item's `rights` (the edm:rights URL), read again from the Record API | the allowlist takes it, not a natural-history specimen, made by `media.europeana.max-year` (1500), and matched (by its `dcterms:spatial`, an id or a name, and dates) |

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

## Pictures a Pleiades place record links (`pleiades`)

Places carry Pleiades ids already — a `pleiades_<n>` place is that record, another place has its exact
Pleiades links — so nothing is matched by name. For each place on the map that has one, its place
record is read (`https://pleiades.stoa.org/places/{id}/json`, no key, one a second), and each URL in
its `references` (`accessURI`, `alternateURI`) is classified:

- a **Wikimedia Commons file** (`commons.wikimedia.org/wiki/File:…`, an `upload.wikimedia.org` file or
  thumbnail URL, a Wikipedia `File:` page or `#/media/File:` link): judged by its licence ON COMMONS,
  exactly as `media import wikidata` judges a file (`imageinfo` + `extmetadata`, the allowlist
  `media.pleiades.licences`). Pleiades' own CC BY covers its data, not what it links, so it is never
  taken as the picture's licence. A file only a Wikipedia holds (a non-free one) is "missing on
  Commons" and refused. At most `per-place` (3) per place, in the record's order, linked in role
  `image`; the link's basis is `Pleiades place 757208100 links it (citesAsRelated)`. The pictures are
  ordinary `wikimedia-commons` assets; the checks are recorded as `pleiades`;
- **on Flickr**: counted as "on Flickr (its API needs a key)" and not asked — Flickr's API needs a key;
- a **Commons category**: counted as "a Commons category, not a file";
- a **picture on another site** (a `.jpg`/`.png` URL, a Geograph or Livius photo page, an IIIF manifest):
  counted as "a picture on <host> (no documented keyless licence API)";
- anything else (a book, a gazetteer entry) is not a picture and not counted.

How much there is (the full dump of 2026-10-17, 42,405 places): 40 references name a Commons file, 2 a
Wikipedia `File:` page, 198 a Commons category, 6 Flickr, and a few hundred other pictures — mostly
Pauly-Wissowa page scans (`elexikon.ch`, archive.org). Pleiades shows its own photographs from a Flickr
group, which its place record does not list; they would need the Flickr API (a key).

A dry run, from the test fakes (a real Zygouries record with three real references of other records
appended):

```
**Asked about:** 1 place · 1 Pleiades record read · 1 linked Commons file · 1 file
**Not stored**
- a Commons category, not a file: 1
- a picture on elexikon.ch (no documented keyless licence API): 1
- on Flickr (its API needs a key): 1

**Would link (sample of 1)**
- place `pleiades_757208100` (Zygouries) ← Zygouries-General plan of the excavations.jpg [`File:Zygouries-General plan of the excavations.jpg`](…) · `CC-BY-SA-4.0` — Pleiades place 757208100 links it (citesAsRelated)
```

## Excavation media from Open Context (`opencontext`)

Open Context publishes excavation projects' data and media, each record under the licence its project
chose. No key. Each place on the map is searched by its name through the documented search API —
`/query/.json?type=media&cat=oc-gen-image&q=<name>&attributes=dc-terms-license,dc-terms-spatial,dc-terms-creator,dc-terms-subject&add-attribute-uris=1&response=metadata,uri-meta`
— which answers each record with its licence URL, creators, subjects, its spatial coverage with their
URIs, its context path (`Asia/Jordan/Petra Great Temple/Upper Temenos/Trench 94/…`) and its dates
(`early bce/ce`, `late bce/ce`: signed years already). Then each record is judged:

1. **withheld**: a record Open Context flags as related to human remains (`oc-api:human-remains-related`),
   or whose subject is human remains, is never stored, whatever its licence;
2. **its own licence**: `dc-terms:license`, a Creative Commons URL, read as its SPDX id
   (`https://creativecommons.org/licenses/by/4.0` → `CC-BY-4.0`); a missing or unreadable one refuses
   it; the allowlist is `media.open-context.licences` (`cc0,public-domain,cc-by,cc-by-sa`; NC only if
   added);
3. **the place, finest evidence first** (#2571): the context path below the country names the site and
   its parts; its excavation units and items (`I`, `Lot 666`, `Trench 94`, `Locus none provided`,
   `Survey`, `2631.1`) name no place and are left out — the map has a place called "I", and Domuztepe's
   operation "I" once refused all its finds. The most specific remaining segment that names a place on
   the map decides: naming this place alone ties the record ("context = Domuztepe"), whatever its ids;
   naming only another place refuses it ("its context names another place on the map": a trench of a
   site on the map is that site's); naming this place among others is settled by an id naming it alone,
   else ambiguous. With no such segment the spatial-coverage URIs decide — a Pleiades, GeoNames or
   Wikidata id (`https://pleiades.stoa.org/places/697725`) that a place on the map carries, as its own id
   or an exact link, ties the record to that place alone (equivalents within `same-site-km` count as
   one); one naming only other places refuses it ("its place id names another place on the map": a find
   from Petra is not filed under Jordan), one naming this place among others is ambiguous. Open
   Context's ids are often coarser than the site (Domuztepe's records name the province of Kahramanmaraş
   and the Middle East), which is why the path comes first. Without either, the museum rule's names
   decide (the gazetteer's name key, whole value against whole name, uncertain values ignored);
4. **dates**: the record's range must overlap the place's period, as for the museums.

Only a record that is kept costs a second request: its media item (`/media/{uuid}.json`), for the files
— `oc-gen:preview` is stored, `oc-gen:thumbnail` is the card's thumbnail, `oc-gen:fullfile` (on
archive.org) is recorded as the original and never downloaded — and for its licence, read again: a
media item whose licence is missing or differs from the search's is not stored (a line in the report).

Credit, in Open Context's own citation form: *Creators (year). "Label from context". In Project. Open
Context. ARK. CC BY 4.0*. The link's basis is the id or the field, and the dates:

```
**Not stored**
- human remains (Open Context flags them; not shown): 1
- its dates do not overlap the place's: 1

**Would link (sample of 2)**
- place `pleiades_697725` (Petra) ← Bowls 7 (30-38).jpg [`f589a368-62ed-43df-5738-4c84d2f5f765`](https://opencontext.org/media/f589a368-62ed-43df-5738-4c84d2f5f765) · `CC-BY-4.0` — pleiades:697725 (the record's place id); 200 BCE–360 CE within 300 BCE–100 CE
- place `pleiades_697725` (Petra) ← Bowls 6 (24-29).jpg [`d9feb4e7-3ec6-444f-6a85-2b6873c8822a`](…) · `CC-BY-4.0` — pleiades:697725 (the record's place id); 200 BCE–360 CE within 300 BCE–100 CE
```

(from the test fakes: three real Petra Great Temple records and a Domuztepe one of 2026-10-17, against a
Petra whose period is 300 BCE–100 CE). Open Context's label for that Pleiades URI is "al-Wuʿaira": the id,
not the label, is what is compared. Most of Open Context is Anatolian, Levantine and Italian fieldwork
(Domuztepe, Çatalhöyük, Petra, Poggio Civitate…); a search for "Mycenae" answered nothing on 2026-10-17.

## Collections that need a key of your own (`smithsonian`, `europeana`)

Both are built in and **off by default**: each needs a free key that every user requests for
themselves ([credentials](user-guide/how-to/credentials.md#getting-the-keys)). The key is a secret
reference in the plugin's block — `histmersion.plugins.media-smithsonian.api-key`,
`histmersion.plugins.media-europeana.api-key` (`"env:<NAME>"` or a vault's, e.g.
`"keepassxc:Histmersion/Europeana#password"`) — resolved when the plugin is built and on every
`config reload`, and sent as the API's `X-Api-Key` header (both APIs document it; Europeana calls the
`wskey` parameter deprecated). Switched on without a key, the collection is listed by
`media providers` ("NO KEY SET") and `media import` refuses it, naming the setting; the API is not
asked. A 401/403 answer ends the batch with "the API key was refused (check …api-key)". Everything
after the search — the museum rule, the download, the thumbnail, the credit, `--refresh` — is the
collections' import above.

Both search everything their institutions hold, so two more rules keep their answers to objects the
research is about — comparative mythology, folklore, Bronze-Age history — rather than whatever names
the place. The first live Europeana dry run (2026-10-26) linked three 1896 jellyfish specimens of the
Natural History Museum, London, to the Orkney Islands: their `dcterms:spatial` was "Orkney", and 1896
lies inside the place's gazetteer span (7000 BCE–2100 CE).

- **Natural-history records are refused.** Each collection's `exclude-*` lists name them in the
  provider's own fields; the search leaves them out where the API can, and a record that still
  answers is refused with the reason ("an excluded provider (OpenUp!)", "an excluded unit (NMNHINV)"),
  counted under *Not stored*. Lists are comma-separated; an empty one excludes nothing.
- **An object made after `max-year` is refused** ("made after 1500 CE"), whatever place it names, and
  the place's period is read up to that year (the basis says "within 7000 BCE–1500 CE"). A record
  must still carry dates ("no dates on the record") and they must overlap the place's period.
  `max-year = 0` sets no limit (the museum collections without a key leave it at 0).

**Smithsonian Open Access** (`media import smithsonian`; an [api.data.gov](https://api.data.gov/signup/) key):

- one search per place: `GET https://api.si.edu/openaccess/api/v1.0/search?q="<name>" AND
  online_media_type:Images&rows=20&type=edanmdm&row_group=objects`;
- a record is used only through an **image media item whose own `usage.access` is `CC0`**
  (`content.descriptiveNonRepeating.online_media.media[]`); a record whose images are not CC0, or
  that has none, is refused with that reason. The stored file is the item's "Screen Image" resource,
  its "Thumbnail Image" the card's thumbnail, its "High-resolution JPEG" the recorded original;
- place fields, most specific first: `freetext.place` (each value's comma parts in order:
  "Raqqa, Syria" → Raqqa, then Syria), then `indexedStructured.geoLocation` from its finest level
  (`L5` City … `L1` Continent);
- dates: the `freetext.date` entry labelled as the object's date ("Date", "Date made", …; never an
  accession or collection date) read as signed years — `1550-1196 BCE`, `100 BCE-100 CE`,
  `12th-13th century`, `2nd millennium BCE`; an uncertain (`?`) or unreadable date is none, and the
  record is not matched;
- natural history: the units in `media.smithsonian.exclude-providers` (the record's
  `descriptiveNonRepeating.unit_code`, else the row's `unitCode`) — by default the National Museum of
  Natural History's departments except Anthropology: `NMNHBIRDS`, `NMNHBOTANY`, `NMNHENTO`,
  `NMNHFISHES`, `NMNHHERPS`, `NMNHINV`, `NMNHMAMMALS`, `NMNHMINSCI`, `NMNHPALEO` — are left out of the
  search (`AND NOT unit_code:(…)` in `q`; `unit_code` is one of the fielded terms the API's `terms`
  endpoint lists) and refused as "an excluded unit (…)" if one still answers;
- `media.smithsonian.max-year` (1500): an object made later is refused;
- credit: *Title, Maker, Unit, Smithsonian Institution, Credit line, CC0*;
- pace: one search every 4 s (`media.smithsonian.request-interval-ms`), under api.data.gov's
  documented 1,000 requests an hour; images come from `ids.si.edu`, which the key does not count.

**Europeana** (`media import europeana`; a Europeana **personal** API key):

- one search per place: `GET https://api.europeana.eu/record/v2/search.json?query=where:("<name>")
  &reusability=open&media=true&thumbnail=true&qf=TYPE:IMAGE&profile=rich&rows=20` —
  `reusability=open` is Europeana's own filter for PDM, CC0, CC BY and CC BY-SA, and `where` is its
  aggregated place field;
- each item's licence is still its own `rights` (the edm:rights URL) read by the server and judged by
  `media.europeana.licences` (`cc0,public-domain,cc-by,cc-by-sa`); a rightsstatements.org statement
  (NoC-NC, InC, …) is not a licence the server reads and is refused. Before a picture is stored its
  record is read again through the Record API (`/record/v2/{id}.json`): the licence of the web resource
  shown (`webResourceEdmRights`, else the aggregation's `edmRights`) must be the search's, or it is not
  stored (a line in the report). A dry run never pays for that request;
- tied to a place by its provider's own `dcterms:spatial` values first (the finest evidence: one
  naming only another place on the map refuses it), then by a GeoNames/Pleiades/Wikidata id among
  its place URIs, else by the museum rule over its place labels; dates: its `year` values, and an
  object made after `media.europeana.max-year` (1500) is refused;
- natural history, three lists, all in Europeana's own fields:
  - `exclude-providers` (`OpenUp!`, the aggregator of Europe's natural-history collections): the
    aggregator's exact name (`PROVIDER`), any case — left out of the search (`NOT PROVIDER:("…")` in
    `query`, the documented boolean syntax) and refused as "an excluded provider (…)";
  - `exclude-types` (`PreservedSpecimen`, `Preserved Specimen`, `FossilSpecimen`, `LivingSpecimen`,
    their spaced forms and `Specimen` — the Darwin Core record bases natural-history providers write
    in `dc:type`): left out of the search (`NOT proxy_dc_type:(…)`); a search item carries no
    `dc:type`, so the Record API's proxies are read again before a picture is stored, and a match
    (letters compared, any case) is refused as "an excluded type (…)";
  - `exclude-data-providers` (`Natural History Museum`, `Naturalis`, `Naturkunde`, `Botanic Garden`,
    `Herbarium`): a record of an institution (`dataProvider`) whose name contains one, any case, is
    refused as "an excluded data provider (…)" — checked on the answer only, since `DATA_PROVIDER` is
    matched exactly in a search. A natural-history museum's archaeology (the Naturhistorisches Museum
    Wien's Hallstatt finds) is kept unless its name matches: add or drop entries to taste;
- the pictures are Europeana's own cached renditions through its keyless Thumbnail API v3
  (`/thumbnail/v3/400/<md5 of the media URL>.jpg` stored, `…/200/…` the card's thumbnail; the media URL
  is the one its `edmPreview` was made from); the provider's file (`edmIsShownBy`) is recorded as the
  original and never downloaded; the page is `https://www.europeana.eu/item/<id>`;
- credit: *Title, Creator, Data provider, via Europeana, CC BY-SA 4.0*.

What was confirmed and what is not yet (2026-10-26, without a key): the endpoints, parameters, the
envelope and error answers of both (each API answered a keyless and a wrong-key request: Smithsonian
`403 {"error":{"code":"API_KEY_MISSING"|"API_KEY_INVALID",…}}`, Europeana `401
{"success":false,"error":…,"message":…,"code":"401_key_invalid"}`, and both read the key from the
`X-Api-Key` header); the Smithsonian record schema, from three real records of its open-data dump (the
schema its API rows carry); Europeana's field names, from its Search and Record API documentation;
the Thumbnail API's MD5 rule, against the documentation's own example; the fields of the 1896
specimen records (`provider` OpenUp!, `dataProvider` "The Trustees of the Natural History Museum,
London", `dc:type` "Preserved Specimen"), from Europeana's public item page. Not yet seen in a live
answer: whether the Smithsonian search accepts the fielded `online_media_type:Images` and
`NOT unit_code:(…)` terms in `q`, and Europeana the `NOT PROVIDER:(…)` and `NOT proxy_dc_type:(…)`
clauses in `query`;
how Europeana writes BCE years in `year` and its place labels (`edmPlaceLabel`) in the `rich`
profile. The fixtures of the Europeana suites are constructed in the documented shape; the first
dry run with a key is the check.

## A plugin's own collection

Every provider above is a built-in **plugin** (`media-wikidata`, `media-depicts`, `media-met`,
`media-cleveland`, `media-aic`, `media-loc`, `media-pleiades`, `media-opencontext`, and — off by
default — `media-smithsonian`, `media-europeana`;
docs/research-plugins.md §19), and a third-party plugin can register a collection of its own
(`com.histmersion.plugin.MediaCollection`). `histmersion media providers` lists what this server has;
`histmersion media import <name>` runs a plugin's collection exactly like a built-in one — dry run, job,
`--place`, `--refresh`.

A plugin's collection only **searches and describes**: for a place it returns records with the licence
**as the provider states it** (an id such as `CC-BY-4.0`, a short name such as `CC BY-NC 4.0`, or a
Creative Commons URL), a credit line, its place fields and dates, and the rendition to store. Everything
that decides what is stored is the server's and the same as for the built-in collections:

- **The licence** is classified by the server from the statement (the licence URL when the statement is
  unclear); a statement it cannot read is *unknown*. It is judged against `media.collections.licences`
  (default `cc0,public-domain,cc-by,cc-by-sa`) or, for one collection,
  `media.collections.per-collection.<name>` — settings of the server's config, never of the plugin, so a
  plugin cannot widen them. A provider-stated CC BY-NC record is refused until the user lists `cc-by-nc`
  for that collection; unknown and all-rights-reserved never pass. A record without a credit line is
  refused.
- **The match** is one of the server's rules, chosen by the collection: the museum rule (the most
  specific place field names this place and no other, by the gazetteer's name key; uncertain values
  ignored; the dates overlap), the map rule (a location names this place alone; dated by a year the
  collection gives), or the id rule (a gazetteer id the record carries, else the museum rule). The
  reason is stored on the link.
- **The rest** — the polite client (the server's User-Agent replaces any the plugin sends), the
  download within `media.max-bytes`, the thumbnail, the credit line, the place recorded as asked, and
  a later `--refresh` re-reading the licence — is the server's import.

A plugin's collection asks about places (its pictures play the role `object`, `map` or `image`); it may
not take the name of a built-in provider, an alias of one, or a provider pictures are stored under.

## Not built: Arachne / iDAI.objects and ARIADNE

Both were checked on 2026-10-17 with read-only requests to their JSON endpoints:

- **Arachne / iDAI.objects** (`arachne.dainst.org/data/search`, `/data/entity/{id}`): keyless and
  answers JSON, but **no record carries a licence** — neither the object entity nor the image entity
  (`type: Bilder`; its sections hold the negative number, file name and project, nothing on rights) —
  and `/data/image/{id}` serves the JPEG without any rights header. Arachne's terms of use restrict its
  photographs (most are the DAI's own, not openly licensed). With no per-record licence there is
  nothing to read, so nothing may be stored. What it would need: a per-image licence field in the API
  (or a documented statement that a defined set is CC-licensed).
- **ARIADNE** (the ARIADNEplus portal, `portal.ariadne-infrastructure.eu/api/search`): its records do
  carry a licence (`accessRights`, a CC URL), but they describe **datasets** (excavation seasons,
  surveys — `resourceType: dataset`), not images: no record names an image file. The JSON route the
  portal's own pages use is not documented as a public API. What it would need: a documented API whose
  records include digital images with their own licence.

## Not in this slice

| provider | why not yet | what it needs |
|---|---|---|
| Rijksmuseum | not built yet. Its current Data Services need **no key** (checked 2026-10-26: `GET https://data.rijksmuseum.nl/search/collection?description=Mycenae` answers a Linked Art search page of object ids, its documentation says "No API key is needed"; the legacy `www.rijksmuseum.nl/api/…/collection` answers 410). Each object id resolves keyless (`https://id.rijksmuseum.nl/{id}`, `Accept: application/ld+json`) to Linked Art: the object `shows` a VisualItem whose own `subject_to` Right names its licence (a Creative Commons URL, e.g. the Public Domain Mark), and the VisualItem is `digitally_shown_by` a DigitalObject whose `access_point` is the IIIF image (`iiif.micr.io`) | a Linked Art reader: the search has no place field (only `description`, `title`, `creator`, `creationDate`, `type`, … — keyword matches), so each candidate costs three requests (object, visual item, digital object); few objects are in this corpus' periods |
| David Rumsey Map Collection | no documented keyless JSON API with a per-record licence (its maps are CC BY-NC-SA, off by default) | a documented API, and `cc-by-nc-sa` listed for it |
| Flickr (Commons, CC) | needs a user's API key, and since 2026 Flickr creates keys only for paid Flickr Pro accounts | not planned: a key would need a paid account |
| Pleiades' Flickr photographs | the photographs Pleiades shows come from a Flickr group its place records do not list; Flickr's API needs a key, which only paid Flickr Pro accounts can create | not planned: a key would need a paid account |
| Pictures Pleiades links on other sites (Geograph, Livius, …) | no documented keyless API with a per-picture licence on those sites | a reader per site, each with its licence per picture |
| Arachne / iDAI.objects | no licence on any record (above) | a per-image licence in its API |
| ARIADNE | records are datasets, not images; the portal's JSON route is undocumented | a documented API with images and their licences |

## Configuration

`media.depicts.*`, `media.met.*`, `media.cleveland.*`, `media.aic.*`, `media.loc.*`, `media.pleiades.*`,
`media.open-context.*`, `media.smithsonian.*`, `media.europeana.*` and `media.samples` — see the configuration reference (`docs/user-guide/reference/configuration.md`). A collection can be switched off with
`media.<provider>.enabled = false` (env `HISTMERSION_MEDIA_<PROVIDER>_ENABLED`, `…_OPEN_CONTEXT_…` for
Open Context); its import then answers with that reason. Each is also a plugin:
`histmersion.plugins.media-<provider>.enabled = false` (env `HISTMERSION_PLUGIN_MEDIA_<PROVIDER>_ENABLED`)
takes it out of `media import` and `media providers` altogether — both switches must be on for a
collection to run. `media-smithsonian` and `media-europeana` are off by default and need their
`api-key` too (above). A plugin's collection is judged by `media.collections.licences` and
`media.collections.per-collection.<name>`.
