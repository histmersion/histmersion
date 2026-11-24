package com.histmersion
package api
package problem
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json

import util.problem.{External, Problem, Problems}
import util.problem.Problem.*
import Answer.{answer, decoded, entry, problem, retryAfter, status}
import util.problem.Text.{label, sentence, sentences}
import json.NewtypeCodecs.{list, problemCodec}

/** Every case of [[Problem]], through every renderer the api owns: its HTTP status, its English sentence and its JSON
  * entry (which decodes back to the case; a stored column keeps the same entry). The renderers' matches name every
  * case, so a new case does not compile until each renderer has an answer for it; this table then fails until the case
  * has a row.
  */
class RenderSuite extends munit.FunSuite:

  /** Each case's status. */
  private val Statuses: Map[String, Int] = Map(
    "Malformed" -> 400,
    "Unknown" -> 400,
    "Unreadable" -> 400,
    "Missing" -> 400,
    "Undeclared" -> 400,
    "Wrapped" -> 400,
    "StoredOn" -> 400,
    "Ambiguous" -> 400,
    "Repeated" -> 400,
    "Misplaced" -> 400,
    "OutOfRange" -> 400,
    "Inconsistent" -> 400,
    "Inverted" -> 400,
    "OutOfTime" -> 400,
    "Unparsable" -> 400,
    "Reference" -> 400,
    "Broken" -> 400,
    "Entity" -> 404,
    "Copy" -> 404,
    "Listed" -> 404,
    "Name" -> 404,
    "SetByHand" -> 404,
    "Undownloaded" -> 404,
    "Dangling" -> 404,
    "Held" -> 409,
    "Referred" -> 409,
    "Job" -> 409,
    "Entry" -> 500,
    "AlreadyExists" -> 409,
    "Renamed" -> 409,
    "Aliased" -> 409,
    "OldKey" -> 409,
    "Collides" -> 409,
    "Claimed" -> 409,
    "Stale" -> 412,
    "WrongState" -> 409,
    "Busy" -> 503,
    "ServerHeld" -> 409,
    "Undrained" -> 503,
    "Referenced" -> 409,
    "Policy" -> 403,
    "Unauthenticated" -> 401,
    "Misdirected" -> 421,
    "Disabled" -> 403,
    "Space" -> 403,
    "Offline" -> 503,
    "Changed" -> 502,
    "Rebuilt" -> 502,
    "Mislisted" -> 502,
    "Http" -> 502,
    "Challenged" -> 502,
    "Limited" -> 429,
    "Unasked" -> 500,
    "Unreachable" -> 502,
    "CallFailed" -> 502,
    "CallThrew" -> 502,
    "Replied" -> 502,
    "McpServer" -> 502,
    "Hashed" -> 500,
    "Timeout" -> 504,
    "Unanswered" -> 504,
    "BadReply" -> 502,
    "File" -> 500,
    "Wrong" -> 500,
    "Absent" -> 500,
    "Extract" -> 500,
    "Oversize" -> 500,
    "Corrupt" -> 500,
    "Process" -> 500,
    "Database" -> 500,
    "Setting" -> 503,
    "Unavailable" -> 503,
    "Cycle" -> 503,
    "Load" -> 500,
    "Rejected" -> 422,
    "Vault" -> 500,
    "Dependency" -> 409,
    "Said" -> 503,
    "Paused" -> 409,
    "Halted" -> 409,
    "Unstarted" -> 409,
    "UploadUnheld" -> 409,
    "NoModel" -> 409,
    "PassedOver" -> 409,
    "Skipped" -> 409,
    "Stopped" -> 409,
    "NotNow" -> 409,
    "AtStep" -> 409,
    "AtModel" -> 409,
    "Failed" -> 409,
    "LimitReached" -> 409,
    "Needs" -> 409,
    "ApiKeyLogin" -> 503,
    "Exposed" -> 503,
    "ProxyUndeclared" -> 503,
    "WildcardOverlap" -> 503,
    "BindRepeated" -> 503,
    "FloorsUnread" -> 503,
    "ReadAt" -> 503,
    "Interrupted" -> 409,
    "Restored" -> 409,
    "Cancelled" -> 409,
    "Draining" -> 409,
    "Unpriced" -> 503,
    "Unbuilt" -> 503,
    "Unchanged" -> 503,
    "Drained" -> 503,
    "Definition" -> 503,
    "CutShort" -> 409,
    "Finding" -> 422,
    "Proposal" -> 422,
    "Answer" -> 422,
    "NotProposed" -> 422,
    "Undecoded" -> 422,
    "Unexpected" -> 500,
    "Unclassified" -> 400,
    "Unrecognised" -> 500,
    "Relayed" -> 500
  )

  test("every case has a row, and every row a case"):
    assertEquals(examples.map(_.productPrefix).toSet, Statuses.keySet)
    assertEquals(examples.size, Statuses.size)

  examples.foreach { p =>
    val code = Statuses(p.productPrefix)

    test(s"${p.group.productPrefix}.${p.productPrefix}: its status"):
      assertEquals(p.status.code, code)

    test(s"${p.group.productPrefix}.${p.productPrefix}: an English sentence"):
      assert(p.sentence.trim.nonEmpty)
      assertEquals(p.sentence, p.sentence.trim)

    test(s"${p.group.productPrefix}.${p.productPrefix}: its JSON, which decodes back to the case"):
      val e = p.entry
      assertEquals(s"${e.group}", p.group.productPrefix)
      assertEquals(s"${e.`case`}", p.productPrefix)
      assertEquals(s"${e.text}", p.sentence)
      assertEquals(RawJson.decode[Answer.Entry](e.asJson.noSpaces).flatMap(_.problem), Right(p))
  }

  test("a relayed problem is written as the Relayed group and case with no fields, and its sentence"):
    val relayed = Relayed(Sentence("the server is reindexing"))
    val written = """{"group":"Relayed","case":"Relayed","data":{},"text":"the server is reindexing"}"""
    assertEquals(relayed.entry.asJson.noSpaces, written)
    assertEquals((relayed: Problem).asJson.noSpaces, written)
    assertEquals(RawJson.decode[Problem](written), Right(relayed))

  test("English sentences tell the cases apart"):
    assertEquals(examples.map(_.sentence).distinct.size, examples.size)

  test("a body lists every problem in order and decodes back to the same problems"):
    val one: Problems = examples.head
    val many: Problems = Problems.of(examples.head, examples.tail*)
    assertEquals(RawJson.decode[Answer](one.answer.asJson.noSpaces).flatMap(_.decoded), Right(one))
    assertEquals(RawJson.decode[Answer](many.answer.asJson.noSpaces).flatMap(_.decoded), Right(many))
    assertEquals(many.answer.problems.map(e => s"${e.`case`}"), examples.map(_.productPrefix))
    assertEquals(many.sentences, examples.map(_.sentence))
    assert(
      RawJson.decode[Answer]("""{"problems":[]}""").flatMap(_.decoded).isLeft,
      "a body with no problem is no answer"
    )
    assert(
      RawJson
        .decode[Answer]("""{"problems":[{"group":"Nope","case":"X","data":{},"text":""}]}""")
        .flatMap(_.decoded)
        .isLeft
    )

  test("the status of several problems is the first one's; a reached usage limit says when to retry"):
    val many: Problems = Problems.of(NotFound.Entity(TypeName("x.Y"), Raw("k")), Invalid.Missing.here(Field("f")))
    assertEquals(many.status.code, 404)
    assertEquals(
      examples.collect { case q: Upstream.Limited => q.retryAfter },
      Vector(Some(EpochMs(1762732800000L)))
    )
    assertEquals(Invalid.Missing.here(Field("f")).retryAfter, None)

  test("English names what an answer should have been as a noun, and every reply shape differently"):
    def said(shape: Upstream.Shape) =
      Upstream.BadReply
        .here(Upstream.Name("x"), Upstream.Url("https://x.example/f"), shape, External.Reported("eof"))
        .sentence
    assertEquals(said(Upstream.Shape.Range), "x's answer at https://x.example/f is not a byte range — eof")
    assertEquals(said(Upstream.Shape.Json), "x's answer at https://x.example/f is not JSON — eof")
    assertEquals(said(Upstream.Shape.Document), "x's answer at https://x.example/f is not a document — eof")
    assertEquals(Upstream.Shape.values.map(said).distinct.length, Upstream.Shape.values.length)
    assert(Upstream.Shape.values.forall(s => !said(s).contains(s" ${s.productPrefix} ")))

  test("a missing copy with the chain's answers lists them in its sentence"):
    val copy = NotFound.Copy(
      Raw("10.1/x"),
      Vector(
        NotFound.Answer(
          NotFound.Fetcher("unpaywall"),
          NotFound.Said.Miss(NotFound.Absence.NotHeld),
          NotFound.Copy(Raw("doi:10.1/x"), Vector.empty)
        ),
        NotFound.Answer(
          NotFound.Fetcher("landing"),
          NotFound.Said.Refused(NotFound.Refusal.BotChallenge),
          Upstream.Challenged(
            Upstream.Name("landing"),
            Upstream.Url("https://x.example/f"),
            Upstream.Status(403),
            Vector.empty
          )
        )
      )
    )
    assertEquals(
      copy.sentence,
      "no legal open-access PDF found for 10.1/x — unpaywall: no copy — no legal open-access PDF found for doi:10.1/x; " +
        "landing: refused (scripts refused) — landing answered HTTP 403 at https://x.example/f with a bot-protection " +
        "challenge, not the page"
    )
    assertEquals(copy.status.code, 404)

  test("every way a fetcher comes back without a copy has words of its own"):
    val all = NotFound.Absence.values.toList.map(NotFound.Said.Miss(_)) ++
      NotFound.Refusal.values.toList.map(NotFound.Said.Refused(_))
    assertEquals(all.map(_.label).distinct.size, all.size)

  test("the hints a refusal gave before its case was typed are in its sentence, word for word"):
    val agentsOff = Refused.Disabled(
      Refused.Feature("the embedded agent runtime"),
      SettingKey("agents.enabled"),
      Some(SettingKey("HISTMERSION_AGENTS_ENABLED")),
      None
    )
    assertEquals(
      agentsOff.sentence,
      "the embedded agent runtime is switched off (agents.enabled = false) — set HISTMERSION_AGENTS_ENABLED=true on " +
        "the server to use it"
    )
    assertEquals(agentsOff.status.code, 403)
    assertEquals(
      Refused
        .Disabled(
          Refused.Feature("NOAA NCEI Natural Hazards (noaa)"),
          SettingKey("histmersion.plugins.events-noaa.enabled"),
          None,
          None
        )
        .sentence,
      "NOAA NCEI Natural Hazards (noaa) is switched off (histmersion.plugins.events-noaa.enabled = false)"
    )
    assertEquals(
      Invalid.Broken.here(Invalid.Rule.RoundsWithin(Count(9L), Count(2L), Count(6L))).sentence,
      "rounds must be between 2 and 6 (agents.review.rounds-cap)"
    )
    val (modelFor, models, skeptic, used) =
      (Vector(Field("--model-for")), Vector(Field("models")), Raw("skeptic"), Vector(Raw("extractor"), Raw("writer")))
    assertEquals(
      Invalid
        .Broken(Invalid.Rule.RoleUsed(Invalid.RoleOwner.Pipeline, Raw("enrich-source"), skeptic, used), modelFor)
        .sentence,
      "--model-for: enrich-source uses no skeptic role (its roles: extractor, writer)"
    )
    assertEquals(
      Invalid
        .Broken(Invalid.Rule.RoleUsed(Invalid.RoleOwner.Pipeline, Raw("seed-goal"), skeptic, Vector.empty), modelFor)
        .sentence,
      "--model-for: seed-goal uses no skeptic role (its roles: none — it calls no model)"
    )
    assertEquals(
      Invalid.Broken(Invalid.Rule.RoleUsed(Invalid.RoleOwner.Manager, Raw("enrich"), skeptic, used), models).sentence,
      "models: 'skeptic' is not a role a enrich manager runs (extractor, writer)"
    )
    assertEquals(
      Invalid.Broken
        .here(Invalid.Rule.HintConfig(Vector(Raw("prioritise"), Raw("hold"), Raw("skip"))))
        .sentence,
      "unknown hint action 'config' (one of: prioritise, hold, skip) — change the settings with the manager's config " +
        "instead"
    )

  test("what switching a feature on does is in its refusal, word for word as before the case was typed"):
    // pre-G60 FetchChain: "acquire: u obtains copies without the rights holder's authorisation and stays off
    // (acquire.allow-unauthorised = false)"
    val unauthorised = Refused.Disabled(
      Refused.Feature("u"),
      SettingKey("histmersion.acquire.allow-unauthorised"),
      None,
      Some(Refused.Effect.Unauthorised)
    )
    assertEquals(
      unauthorised.sentence,
      "u obtains copies without the rights holder's authorisation and stays off " +
        "(histmersion.acquire.allow-unauthorised = false)"
    )
    // pre-G60 CalibreCollection: "write-back is off — set histmersion.plugins.calibre.write-back = true
    // (HISTMERSION_PLUGIN_CALIBRE_WRITE_BACK=true) to let the server write to the library"
    val writeBack = Refused.Disabled(
      Refused.Feature("write-back"),
      SettingKey("histmersion.plugins.calibre.write-back"),
      Some(SettingKey("HISTMERSION_PLUGIN_CALIBRE_WRITE_BACK")),
      Some(Refused.Effect.WritesLibrary)
    )
    assertEquals(
      writeBack.sentence,
      "write-back is switched off (histmersion.plugins.calibre.write-back = false) — set it to true " +
        "(HISTMERSION_PLUGIN_CALIBRE_WRITE_BACK=true) to let the server write to the library"
    )
    assertEquals(writeBack.status.code, 403)

  test("a listed plugin id that names nothing says why, as before the case was typed"):
    // pre-G60: "acquire.chain names 'gone', which no enabled plugin registered — skipped" and
    // "histmersion.plugins.order names 'x', which is no plugin in this binary"
    assertEquals(
      Config.Setting(SettingKey("histmersion.acquire.chain"), Config.Fault.Unregistered(Raw("gone"))).sentence,
      "histmersion.acquire.chain names 'gone', which no enabled plugin registered — skipped"
    )
    assertEquals(
      Config.Setting(SettingKey("histmersion.plugins.order"), Config.Fault.NoPlugin(Raw("x"))).sentence,
      "histmersion.plugins.order names 'x', which is no plugin in this binary"
    )

  test("the places' and the gazetteer's refusals keep the words and hints they had before their cases were typed"):
    val (place, label) = (Raw("pleiades_579885"), Raw("Knossos"))
    Vector[(Problem, String)](
      NotFound.Name(place, label, Raw("Cnossus"), Raw("la"), Vector(Raw("en"), Raw(""))) ->
        "'Knossos' [pleiades_579885] has no name 'Cnossus' in la (it has it with en, no language — pass that language)",
      NotFound.Name(place, label, Raw("Zztell"), Raw(""), Vector.empty) ->
        "'Knossos' [pleiades_579885] has no name 'Zztell'",
      NotFound.SetByHand(place, label) -> "'Knossos' [pleiades_579885] has no kind, period or point set by hand",
      Invalid.Broken.here(Invalid.Rule.PointOnly(place, label)) ->
        ("'Knossos' [pleiades_579885] has a shape, not a point — a point set by hand moves only a place known by a " +
          "point"),
      Invalid.Broken.here(Invalid.Rule.NameByHand(Raw("Cnossus"), place, label)) ->
        ("'Cnossus' is a name the gazetteer seed or an imported source gives 'Knossos' [pleiades_579885], not one " +
          "added by hand — removing it would last only until the next import"),
      Conflict.Referred(
        place,
        label,
        NonEmptyVector.of(
          Conflict.Reference(Count(3L), Conflict.Referrer.Events),
          Conflict.Reference(Count(1L), Conflict.Referrer.AssetLinks),
          Conflict.Reference(Count(2L), Conflict.Referrer.OnlyPlaceOf)
        )
      ) -> ("'Knossos' [pleiades_579885] is referred to by 3 event(s), 1 asset link(s), 2 finding(s) it is the only " +
        "place of — merge it into the place it is the same as instead, or move those first"),
      Io.Absent(Io.Held.GazetteerSeed, Io.Path("/srv/seed"), Some(SettingKey("init.data-dir"))) ->
        "no gazetteer seed at /srv/seed (set init.data-dir)",
      Io.Absent(Io.Held.GazetteerManifest, Io.Path("/srv/m.json"), Some(SettingKey("places.sources.manifest"))) ->
        "no gazetteer manifest at /srv/m.json (set places.sources.manifest)",
      Io.Absent(Io.Held.LpfFiles, Io.Path("/srv/lpf"), None) ->
        "no LPF files in /srv/lpf (put World Historical Gazetteer downloads there)",
      Io.Absent(Io.Held.SeedFiles, Io.Path("/srv/seed"), None) -> "no JSON files under /srv/seed",
      Io.Extract(Raw("tgn"), Io.Pinning.Unlisted(Io.Path("db/gazetteer/extracts.json"))) ->
        "db/gazetteer/extracts.json pins no 'tgn' extract",
      Io.Extract(Raw("tgn"), Io.Pinning.NotBuilt(Io.Path("db/gazetteer/extracts.json"))) ->
        ("tgn is not pinned yet (db/gazetteer/extracts.json names no built file or sha256 for it) — build it with " +
          "db/gazetteer/build.py and pin it (db/gazetteer/README.md, \"Rebuilding and re-pinning\")"),
      Io.Extract(Raw("tgn"), Io.Pinning.Mismatch(Raw("2026-09"), Io.Path("/c/tgn.lpf.gz"), Raw("ab"), Raw("cd"))) ->
        ("/c/tgn.lpf.gz has sha256 ab, but tgn 2026-09 is pinned at cd — refused (rebuild it with " +
          "db/gazetteer/build.py and re-pin, or fetch the pinned file)"),
      Io.Extract(
        Raw("tgn"),
        Io.Pinning.Unhosted(
          Raw("2026-09"),
          Raw("tgn.lpf.gz"),
          Io.Path("/c/tgn.lpf.gz"),
          Raw("ab"),
          Count(1_450_000_000L),
          SettingKey("places.sources.dir"),
          SettingKey("HISTMERSION_GAZETTEER_DIR")
        )
      ) -> ("tgn 2026-09: tgn.lpf.gz is not at /c/tgn.lpf.gz, and its pin has no download url (the extracts are not " +
        "hosted). Put the file there — sha256 ab, 1.5 GB — copied from another install or rebuilt with " +
        "db/gazetteer/build.py (db/gazetteer/README.md); the folder is places.sources.dir (HISTMERSION_GAZETTEER_DIR; " +
        "by default the XDG cache directory, $XDG_CACHE_HOME/histmersion/gazetteer or ~/.cache/histmersion/gazetteer). " +
        "Then run the load again."),
      Io.Oversize(Io.Path("whg.json"), Count(612L), SettingKey("places.sources.max-lpf-mb")) ->
        "whg.json is 612 MB, over places.sources.max-lpf-mb — convert it to one feature per line (.ndjson)"
    ).foreach((p, s) => assertEquals(p.sentence, s))
    // a damaged seed file is named, as "<file>: <why>" named it
    assertEquals(
      Io.Corrupt(Io.Format.Json, Where("LPF"), Io.Damage.Unreadable(External.Reported("expected '}'")))
        .inside(Where("regions/aegean.json"))
        .sentence,
      "the json data at LPF in regions/aegean.json cannot be read — expected '}'"
    )
    assertEquals(Io.Extract(Raw("x"), Io.Pinning.NotBuilt(Io.Path("m"))).status.code, 500)
    assertEquals(NotFound.Name(place, label, Raw("n"), Raw(""), Vector.empty).status.code, 404)

  test("a decoder's refusal keeps the format it reads and the hint of what it reads instead"):
    def at(format: Io.Format, damage: Io.Damage) = Io.Corrupt(format, Where("the header"), damage).sentence
    Vector(
      at(Io.Format.Png, Io.Damage.Unsupported(Io.Variant.PngLayout(Count(0L), Count(8L), Count(0L)))) ->
        "the PNG data at the header is a PNG of colour type 0, depth 8, interlace 0: only 8-bit RGB(A) non-interlaced PNGs are read",
      at(Io.Format.Png, Io.Damage.Foreign) -> "the PNG data at the header is not a PNG (no magic number)",
      at(Io.Format.WebP, Io.Damage.Unsupported(Io.Variant.LossyWebP)) ->
        "the WebP data at the header is a lossy (VP8) WebP: only lossless WebP is read",
      at(Io.Format.WebP, Io.Damage.Unsupported(Io.Variant.NoLossless)) ->
        "the WebP data at the header has no VP8L chunk: only lossless WebP is read",
      at(
        Io.Format.PmTiles,
        Io.Damage.Foreign
      ) -> "the PMTiles data at the header is not a PMTiles archive (no magic number)",
      at(Io.Format.PmTiles, Io.Damage.Unsupported(Io.Variant.SpecVersion(Count(2L), Count(3L)))) ->
        "the PMTiles data at the header is spec version 2, which is not supported (only 3)",
      at(Io.Format.PmTiles, Io.Damage.Unsupported(Io.Variant.Compression(Raw("brotli"), Raw("gzip")))) ->
        "the PMTiles data at the header uses brotli compression, which is not supported (only gzip)",
      at(Io.Format.GeoTiff, Io.Damage.Unsupported(Io.Variant.BigTiff)) ->
        "the GeoTIFF data at the header is not a classic TIFF (BigTIFF is not read)",
      at(Io.Format.GeoTiff, Io.Damage.Unsupported(Io.Variant.SampleBits(Count(32L)))) ->
        "the GeoTIFF data at the header has 32-bit samples (only 16-bit are read)",
      at(Io.Format.GeoTiff, Io.Damage.Unsupported(Io.Variant.TiffCompression(Count(8L)))) ->
        "the GeoTIFF data at the header has compression 8 (only uncompressed grids are read)",
      at(Io.Format.GeoTiff, Io.Damage.Unsupported(Io.Variant.Bands(Count(3L)))) ->
        "the GeoTIFF data at the header has 3 bands (only one is read)",
      at(Io.Format.GeoTiff, Io.Damage.Unsupported(Io.Variant.SampleFormat(Count(1L)))) ->
        "the GeoTIFF data at the header has sample format 1 (only signed integers are read)",
      at(Io.Format.GeoTiff, Io.Damage.Unsupported(Io.Variant.Tiled)) ->
        "the GeoTIFF data at the header is a tiled TIFF (only strips are read)",
      at(Io.Format.GeoTiff, Io.Damage.Structure(Io.Flaw.NoPixelScale)) ->
        "the GeoTIFF data at the header has no pixel scale (not a GeoTIFF)",
      at(Io.Format.PmTiles, Io.Damage.Structure(Io.Flaw.NegativeAt(Io.Quantity.Offset, Count(24L)))) ->
        "the PMTiles data at the header holds a negative offset at byte 24",
      at(Io.Format.Png, Io.Damage.Bytes(Count(1024L), Count(1000L))) ->
        "the PNG data at the header holds 1000 bytes, not 1024"
    ).foreach((s, want) => assertEquals(s, want))

  test("the base map's refusals keep their words and hints"):
    val on = Io.Path("/srv/basemap")
    val (needs, free, least) = (Count(4_200_000_000L), Count(5_000_000_000L), Count(2_000_000_000L))
    Vector[(Problem, String)](
      Refused.Space(Refused.Work.Layer(Raw("vector")), needs, on, free, least) ->
        ("refused: the vector layer needs 4.2 GB more on /srv/basemap (5.0 GB free), which would leave less than " +
          "2.0 GB (basemap.min-free-gb). Free some space, fetch a smaller region (--bbox) or fewer zooms (--max-zoom " +
          "/ --terrain-max-zoom), or move the files with basemap.dir."),
      Refused.Space(Refused.Work.SeaFloor, needs, on, free, least) ->
        ("refused: the sea floor needs about 4.2 GB on /srv/basemap (5.0 GB free), which would leave less than 2.0 GB " +
          "(basemap.min-free-gb)"),
      Refused.Space(Refused.Work.Gebco, needs, on, free, least) ->
        ("refused: the GEBCO sea floor needs about 4.2 GB on /srv/basemap (5.0 GB free), which would leave less than " +
          "2.0 GB (basemap.min-free-gb). Fetch a smaller region (--bbox), or the ETOPO1 sea floor " +
          "(basemap.bathymetry.source = etopo1)."),
      Refused.Space(Refused.Work.Labels, needs, on, free, least) ->
        ("refused: the physical names need about 4.2 GB on /srv/basemap (5.0 GB free), which would leave less than " +
          "2.0 GB (basemap.min-free-gb)"),
      NotFound.Undownloaded(Raw("terrain"), Raw("histmersion basemap fetch")) ->
        "the terrain layer is not downloaded — run `histmersion basemap fetch`",
      Invalid.Broken.here(Invalid.Rule.FocusRegion(Raw("crete"), Vector(Raw("alps"), Raw("aegean")))) ->
        "no focus region 'crete' in basemap.terrain.focus-regions (alps, aegean): give its --bbox",
      Invalid.Broken.here(Invalid.Rule.FocusDeeper(Count(12L))) ->
        "a focus region holds the zooms deeper than the extract's (12): ask for more than that",
      Invalid.Broken.here(Invalid.Rule.LabelsUnranged) -> "the physical names are not read with range requests",
      Invalid.Broken.here(Invalid.Rule.SeaFloorUnranged) -> "the sea floor is not read with range requests",
      Upstream.Offline(Upstream.Name("the provider"), None) ->
        "the provider was unreachable a moment ago; not asked again yet",
      Upstream.Changed(Upstream.Name("the provider")) -> "the provider's file changed",
      Upstream.Rebuilt(Upstream.Url("https://x/b.pmtiles")) ->
        "https://x/b.pmtiles changed during the download (size or ETag differ) — run the fetch again for the new build",
      Upstream.Mislisted(Upstream.Url("https://x/b.pmtiles"), Count(10L), Count(12L), Raw("b.pmtiles")) ->
        "https://x/b.pmtiles is 10 bytes, but the provider lists 12 for b.pmtiles",
      Conflict.Busy(Conflict.Activity.TileRequests) -> "too many tile requests waiting",
      Io.Corrupt(Io.Format.Json, Where("https://x/builds.json"), Io.Damage.Structure(Io.Flaw.NoBuild)) ->
        "the json data at https://x/builds.json lists no build"
    ).foreach((p, s) => assertEquals(p.sentence, s))
    assertEquals(Upstream.Offline(Upstream.Name("the provider"), Some(EpochMs(5L))).retryAfter, Some(EpochMs(5L)))

  test("a chain's model picking keeps the words and hints it had before its cases were typed"):
    val (role, opus, gpt) = (Run.Role("enricher"), Run.Model("claude-code:opus"), Run.Model("codex:gpt-6"))
    val at = EpochMs(1762732800000L)
    val held = Run.HeldPool(Raw("Claude plan"), Raw("claude-plan"))
    val floor = Run.PassedOver(
      opus,
      NonEmptyVector.of(Run.Skipped(Run.Skip.BelowFloor(Raw("Claude plan"), Raw("5-hour 3% left"))))
    )
    val planOnly = Run.PassedOver(gpt, NonEmptyVector.of(Run.Skipped(Run.Skip.PlanOnly(role))))
    Vector[(Problem, String)](
      Run.NoModel(Run.Role("fact-checker"), Run.Shortfall.Unassigned(Run.Asked.Call), Vector.empty) ->
        ("no model for the fact-checker role — set agents.roles.fact-checker (HISTMERSION_AGENTS_ROLE_FACT_CHECKER) " +
          "to provider:model[ > provider:model …], save one with `histmersion agent models set fact-checker " +
          "<provider:model>…`, or pass one"),
      Run.NoModel(role, Run.Shortfall.Unassigned(Run.Asked.Listing), Vector.empty) ->
        "no model — set agents.roles.enricher or `histmersion agent models set enricher …`",
      Run.NoModel(role, Run.Shortfall.Unusable, Vector(floor, planOnly)) ->
        ("no model of the enricher chain can run: claude-code:opus: the Claude plan is below its floor: 5-hour 3% " +
          "left; codex:gpt-6: not used — the chain does not fall from a plan to a paid key (histmersion agent quota " +
          "fallthrough enricher on)"),
      Run.NoModel(
        role,
        Run.Shortfall.Exhausted(Run.Floor.PlanOrAllowance, Run.Frees.At(at, opus, Vector(held))),
        Vector(floor, planOnly)
      ) ->
        ("all models for role enricher are below their plan floor or allowance; next reset at 2025-11-10T00:00:00Z " +
          "(claude-code:opus), when the run resumes by itself; the Claude plan is held (release it with `histmersion " +
          "agent quota release claude-plan`) (claude-code:opus: the Claude plan is below its floor: 5-hour 3% left; " +
          "codex:gpt-6: not used — the chain does not fall from a plan to a paid key (histmersion agent quota " +
          "fallthrough enricher on))"),
      Run.NoModel(
        role,
        Run.Shortfall.Exhausted(Run.Floor.Allowance, Run.Frees.Released(NonEmptyVector.of(held))),
        Vector.empty
      ) ->
        ("all models for role enricher are below their allowance; no window frees by itself: the Claude plan is held " +
          "(release it with `histmersion agent quota release claude-plan`) ()"),
      Run.NoModel(role, Run.Shortfall.Exhausted(Run.Floor.Allowance, Run.Frees.Never), Vector.empty) ->
        "all models for role enricher are below their allowance; no window frees by itself — raise or reset the allowance ()",
      Run.Skipped(Run.Skip.Cooling(at)) -> "its plan reported its usage limit; skipped until 2025-11-10T00:00:00Z",
      Run.Skipped(
        Run.Skip.BelowAllowance(
          NonEmptyVector.of(
            Run.Spent(Raw("5 usd/day"), Raw("4.9 USD"), Raw("0.5 USD")),
            Run.Spent(Raw("2M tok/week"), Raw("2M"), Raw("0"))
          )
        )
      ) -> "below its allowance: 5 usd/day (4.9 USD used, reserve 0.5 USD), 2M tok/week (2M used, reserve 0)",
      Run.Skipped(
        Run.Skip.AllowanceUnread(
          NonEmptyVector.of(
            Run.Unread(Raw("local:*"), NonEmptyVector.of(Relayed(Sentence("x")), Relayed(Sentence("y")))),
            Run.Unread(Raw("codex:*"), NonEmptyVector.of(Relayed(Sentence("z"))))
          )
        )
      ) -> "its allowance cannot be read (local:*: x; y; codex:*: z)"
    ).foreach((p, want) => assertEquals(p.sentence, want))

  test("a run's lifecycle keeps the words and hints it had before its cases were typed"):
    val step = Run.Step("enrich")
    Vector[(Problem, String)](
      Run.Stopped(Run.Stop.Stalled(Count(4L), Count(31L), Count(3L))) ->
        "stalled 3 times at step 4 (no sign of life for 31 min each time; agents.watchdog)",
      Run.Stopped(
        Run.Stop.Silent(Count(4L), Count(31L), Count(1L), Count(3L))
      ) -> "no sign of life for 31 min at step 4 (stall 1 of 3)",
      Run.Stopped(Run.Stop.NoPipeline(Raw("x"))) -> "no pipeline 'x' in this build",
      Run.Stopped(Run.Stop.PipelineChanged) -> "the pipeline's version changed since this run started; start a new run",
      Run.Stopped(Run.Stop.Reordered(Count(2L), Run.Step("extract"), step)) ->
        "step 2 is 'extract' in the checkpoint but 'enrich' now — the pipeline changed; start a new run",
      Run.Stopped(Run.Stop.CheckpointUnread(Count(2L), step, External.Reported("eof"))) ->
        "step 2 (enrich): the checkpoint no longer decodes (eof)",
      Run.Stopped(Run.Stop.Vanished) -> "the run disappeared",
      Run.Paused(Run.PauseReason.Requested, None) -> "paused on request",
      Run.Stopped(
        Run.Stop.Over(Run.Spending.Usd(Raw("$1.2000"), Raw("$1.00")))
      ) -> "the run spent $1.2000 of its $1.00 USD",
      Run.Stopped(Run.Stop.Over(Run.Spending.Tokens(Count(600001L), Count(600000L), Raw("0.1")))) ->
        "the run used 600001 of its 600000 budget tokens (cache reads count 0.1: agents.budgets.cache-read-weight)",
      Run.Stopped(
        Run.Stop.Over(Run.Spending.Capped(Run.Cap.Daily, Raw("5.0")))
      ) -> "today's agent spend reached agents.budgets.daily-usd (5.0)",
      Run.Stopped(Run.Stop.Over(Run.Spending.Capped(Run.Cap.Monthly, Raw("50.0")))) ->
        "this month's agent spend reached agents.budgets.monthly-usd (50.0)",
      Run.Stopped(Run.Stop.MovedOn(Raw("r1"))) -> "run r1 moved on; look again",
      Run.NotNow(Raw("r1"), Raw("done"), Run.Control.Pause) ->
        "run r1 is done; only a queued or running run can be paused",
      Run.NotNow(Raw("r1"), Raw("done"), Run.Control.Resume) -> "run r1 is done; only a paused run can be resumed",
      Run.NotNow(Raw("r1"), Raw("done"), Run.Control.Cancel) -> "run r1 is done; it has already finished",
      Run.Stopped(Run.Stop.Withdrawn) -> "rejected: the run was cancelled",
      Run.AtStep(step, Run.Failure.Failed(NonEmptyVector.of(Relayed(Sentence("a")), Relayed(Sentence("b"))))) ->
        "enrich: a; b",
      Run.AtStep(step, Run.Failure.Unsettled(Count(16L))) ->
        "enrich: 16 attempts without an answer; the chain keeps running out mid-step",
      Run.AtStep(step, Run.Failure.ProviderDown(NonEmptyVector.of(Relayed(Sentence("HTTP 529"))))) ->
        "enrich: HTTP 529 — fix the provider, then resume",
      Run.AtStep(step, Run.Failure.BudgetSpent) -> "enrich: the run's budget is exhausted",
      Run.AtStep(
        step,
        Run.Failure.NoValidAnswer(Count(3L), Vector(Relayed(Sentence("a")), Relayed(Sentence("b"))))
      ) ->
        "enrich: no valid answer after 3 attempt(s): a; b"
    ).foreach((p, want) => assertEquals(p.sentence, want))

  test("a step's attempts and its preconditions keep the words and hints they had before their cases were typed"):
    val step = Run.Step("cold-reader-1")
    val haiku = Run.Model("claude-code:haiku")
    Vector[(Problem, String)](
      Run.Failed(NonEmptyVector.of(Relayed(Sentence("a")), Relayed(Sentence("b")))) -> "a; b",
      Run.AtStep(step, Run.Failure.Declined(Vector(Relayed(Sentence("a")), Relayed(Sentence("b"))))) -> "a; b",
      Run.AtStep(step, Run.Failure.Declined(Vector.empty)) -> "the answer was rejected",
      Run.LimitReached(Relayed(Sentence("claude-code:opus reached its usage limit")), EpochMs(1762732800000L)) ->
        "claude-code:opus reached its usage limit — skipped until 2025-11-10T00:00:00Z",
      Run.Skipped(Run.Skip.AllowanceReached(Run.Model("anthropic:claude-opus"))) ->
        "anthropic:claude-opus reached its allowance during the step; the chain's next model takes it",
      Run.AtStep(step, Run.Failure.OutputCapped(haiku, Count(4096L), Count(4096L), Run.Ceiling.StepMaxTokens)) ->
        ("cold-reader-1: claude-code:haiku's answer exceeded the 4096 output-token cap (the step sent 4096) — raise " +
          "agents.step.max-tokens (HISTMERSION_AGENTS_MAX_TOKENS) (a thinking model's reasoning counts toward it), " +
          "restart the server, then resume the run"),
      Run.AtStep(step, Run.Failure.OutputCapped(haiku, Count(4096L), Count(8192L), Run.Ceiling.StepOwn)) ->
        ("cold-reader-1: claude-code:haiku's answer exceeded the 4096 output-token cap (the step sent 8192) — raise " +
          "the step's own cap (agents.review.fixer-max-tokens or agents.compile.draft-max-tokens) (a thinking " +
          "model's reasoning counts toward it), restart the server, then resume the run"),
      Run.Needs(Run.Need.Text(Raw("anthony2007"))) -> "anthony2007 has no text — ingest or OCR it first",
      Run.Needs(Run.Need.Body(Raw("yamnaya"))) -> "article 'yamnaya' has no body to review",
      Run.AtModel(Run.Model("hello-ai:echo"), NonEmptyVector.of(Refused.Policy(Refused.Rule.PlanOutsideLocal))) ->
        ("hello-ai:echo: this provider is refused while the server listens beyond loopback: it spends your own " +
          "plan, and another client could spend it"),
      Refused.Policy(Refused.Rule.TokenOtherAgent) -> "this run token is not for that agent",
      Refused.Unauthenticated(Refused.Credential.RunToken) -> "a live run token is required",
      Upstream.CallFailed(Upstream.Name("anthropic:claude-opus"), Relayed(Sentence("Overloaded"))) ->
        "the call to anthropic:claude-opus failed: Overloaded",
      Upstream.CallThrew(Upstream.Name("anthropic:claude-opus"), External.Reported("IOException: reset")) ->
        "the call to anthropic:claude-opus failed: IOException: reset",
      Content.Undecoded(
        Content.Decoding.Mismatch(Raw(".notes"), External.Reported("expected '\"'")),
        None,
        Some(External.ModelSaid("{\"notes\": 5}"))
      ) -> "the answer does not match the schema at .notes: expected '\"' — the answer began: {\"notes\": 5}",
      Content.Undecoded(
        Content.Decoding.NotJson(External.Reported("eof")),
        Some(External.Reported("answered")),
        None
      ) ->
        "the answer is not valid JSON: eof (answered)",
      Content.Undecoded(Content.Decoding.Unreadable(External.Reported("odd")), None, None) ->
        "the answer could not be decoded: odd",
      Content.Undecoded(Content.Decoding.NotSchema, Some(External.Reported("iteration limit")), None) ->
        "the answer is not the JSON the schema asks for (iteration limit)"
    ).foreach((p, want) => assertEquals(p.sentence, want))

  test("the agents' runtimes, plan pools, doctor and run options keep the words and hints they had before typing"):
    Vector[(Problem, String)](
      Refused.Policy(Refused.Rule.ClaudeCodeOutsideLocal) ->
        ("claude-code is refused while the server listens beyond loopback: another client could spend your Claude " +
          "plan. Bind to 127.0.0.1 (HISTMERSION_BIND), or — when every client of this server is you — set " +
          "agents.providers.claude-code.allow-non-loopback (HISTMERSION_AGENTS_CLAUDE_ALLOW_NON_LOOPBACK)"),
      Refused.Policy(Refused.Rule.CodexOutsideLocal) ->
        "codex is refused while the server listens beyond loopback: another client could spend your ChatGPT plan",
      Refused.Policy(Refused.Rule.OpencodeChatgptOutsideLocal) ->
        "opencode on a ChatGPT login is refused while the server listens beyond loopback",
      Refused.Policy(Refused.Rule.OrdinaryUsageDenied) ->
        "the ChatGPT plan does not allow ordinary usage now (ordinaryUsageAllowed: false)",
      Config.Unavailable(Config.Capability.ClaudeLogin) ->
        "not logged in — run `claude`, then /login (or `claude setup-token` for a long-lived token)",
      Config.Unavailable(Config.Capability.AppServerRead) -> "no app-server read in this context",
      Config.Unavailable(Config.Capability.PlanProbe) -> "no probe in this context",
      Config.Unavailable(Config.Capability.LoginCheck) -> "not checked in this context",
      Config.ApiKeyLogin(Raw("api_key")) ->
        "logged in with an API key (api_key), not a Claude plan — log in with your Claude account, or set use-api-key",
      Config.FloorsUnread(Config.FloorsFrom.Saved, NonEmptyVector.of(Relayed(Sentence("a")), Relayed(Sentence("b")))) ->
        "saved floors do not parse: a; b",
      Config.FloorsUnread(
        Config.FloorsFrom.Setting(SettingKey("agents.quota.claude-plan.floors")),
        NonEmptyVector.of(Relayed(Sentence("a")))
      ) -> "agents.quota.claude-plan.floors: a",
      Config.ReadAt(
        Io.Path("~/h.conf"),
        Io.Path("/tmp/h.conf.1"),
        NonEmptyVector.of(Relayed(Sentence("/tmp/h.conf.1: bad")))
      ) -> "~/h.conf: bad",
      Config.Setting(
        SettingKey("agents.approvals.edges"),
        Config.Fault.Defaulted(Raw("auot"), Vector(Raw("manual"), Raw("auto")), Raw("manual"))
      ) -> "agents.approvals.edges = 'auot' is neither manual nor auto; treated as manual",
      Config.Setting(SettingKey("agents.providers.codex.command"), Config.Fault.Empty) ->
        "agents.providers.codex.command is empty",
      Upstream.Replied(Upstream.Name("codex exec"), External.Reported("usage limit\nreached")) ->
        "codex exec: usage limit\nreached",
      Upstream.McpServer(Raw("failed")) -> "the agent MCP server is failed",
      Run.Stopped(Run.Stop.Guard(Raw("the quota-guard mod ended the turn"))) ->
        "claude -p stopped by the quota guard: the quota-guard mod ended the turn",
      Run.Needs(Run.Need.Login(Raw("not logged in"))) -> "unavailable: no login — not logged in",
      Run.Needs(
        Run.Need.Probe(Io.ExitCode(1), Some(External.Reported("x")))
      ) -> "the probe printed no rate_limit_event (exit 1: x)",
      Run.Needs(Run.Need.Probe(Io.ExitCode(0), None)) -> "the probe printed no rate_limit_event (exit 0)",
      Io.Absent(Io.Held.CodexHome, Io.Path("/h/codex"), None) -> "no Codex home at /h/codex",
      Conflict.Busy(Conflict.Activity.CodexRun) -> "a Codex run is using the home — not now",
      Io.Process(Io.Tool("codex app-server"), Io.Ending.Unanswered(Raw("account/rateLimits/read"))) ->
        "codex app-server ended without answering account/rateLimits/read",
      Io.Process(Io.Tool("sqlite3 /h/s.sqlite VACUUM;"), Io.Ending.Exited(Io.ExitCode(1), External.Stderr(""))) ->
        "sqlite3 /h/s.sqlite VACUUM; exited with 1",
      NotFound.Entity(TypeName("ProviderKind"), Raw("gemini")) ->
        "no AI provider 'gemini' is registered (its plugin is switched off or was removed)",
      Refused.Disabled(
        Refused.Feature("the codex provider"),
        SettingKey("histmersion.plugins.ai-codex.enabled"),
        None,
        None
      ) -> "the codex provider is switched off (histmersion.plugins.ai-codex.enabled = false)",
      Invalid.Broken.here(Invalid.Rule.ProviderOf(Raw("codex:gpt-5"), Raw("opencode"))) ->
        "codex:gpt-5 is not an opencode model",
      Invalid.Broken.here(Invalid.Rule.ProviderOf(Raw("opencode:a/b"), Raw("codex"))) ->
        "opencode:a/b is not a codex model",
      Invalid.Broken.here(Invalid.Rule.OpencodeModelShape(Raw("opencode:gpt-5"))) ->
        "opencode:gpt-5: an opencode model is <provider>/<model>, e.g. opencode:openai/gpt-5",
      Invalid.Broken.here(Invalid.Rule.HostProvider(Raw("codex"))) ->
        "codex is Histmersion's own provider; it is run by the host, not through the SPI",
      Invalid.Broken.here(Invalid.Rule.OptionOf(Raw("fix"), Raw("review-article"), Raw("enrich-source"))) ->
        "fix is an option of review-article, not of enrich-source",
      Invalid.Broken.here(Invalid.Rule.RoundsWithin(Count(9L), Count(1L), Count(6L))) ->
        "rounds must be between 1 and 6 (agents.review.rounds-cap)",
      Invalid.Broken.here(Invalid.Rule.NewArticleFilter(Raw("yamnaya"))) ->
        "'yamnaya' is a new article: give a filter (query, topics or tags) for the sources to compile it from",
      Invalid.Broken.here(Invalid.Rule.NewArticleTitle(Raw("yamnaya"))) -> "'yamnaya' is a new article: give its title"
    ).foreach((p, want) => assertEquals(p.sentence, want))

  test("the hot graph's and the settings' refusals keep the words and hints they had before their cases were typed"):
    val pool = Raw("pool (generation 3, 2 pinned)")
    Vector[(Problem, String)](
      Config.Unavailable(Config.Capability.HotGraph) -> "hot graph: closed",
      Config.Unavailable(Config.Capability.Supervisor) ->
        ("This server was not started by a service that starts it again, so it cannot restart itself. " +
          "Stop it and start it again yourself."),
      Config.Unavailable(Config.Capability.SettingsFile) ->
        ("This server was started without a settings file, so Settings cannot change anything. " +
          "Set HISTMERSION_CONFIG to the file to use and start it again."),
      Config.Unbuilt(Config.Scope("pool"), NonEmptyVector.of(Relayed(Sentence("a")), Relayed(Sentence("b")))) ->
        "pool: a; b",
      Config.Unchanged(Relayed(Sentence("pool: a"))) -> "config reload: nothing changed — pool: a",
      Config.Drained(pool, Config.Drain.InUse(Count(10L))) ->
        "config reload: pool (generation 3, 2 pinned) is still in use after 10 s — it closes when its last user lets go",
      Config.Drained(
        pool,
        Config.Drain.ClosedInUse
      ) -> "shutdown: closing pool (generation 3, 2 pinned) while still in use",
      Config.Drained(Raw("pool (generation 3)"), Config.Drain.Unclosed(Count(5L))) ->
        "pool (generation 3): its scope did not close within 5 s",
      Io.Wrong(Io.Path("/srv/a.json"), Io.Kind.OnDisk) -> "/srv/a.json is not on disk",
      Io.Wrong(Io.Path("/srv/a.json"), Io.Kind.File) -> "/srv/a.json is not a file"
    ).foreach((p, want) => assertEquals(p.sentence, want))

  test("an agent definition's flaws keep the words they had before their cases were typed"):
    val skeptic = Raw("agents/skeptic.md")
    Vector[(Config.Flaw, String)](
      Config.Flaw.NoHeader -> "agents/skeptic.md: no YAML header (--- … ---)",
      Config.Flaw.HeaderUnclosed -> "agents/skeptic.md: the YAML header is not closed",
      Config.Flaw.HeaderNotMapping -> "agents/skeptic.md: the YAML header is not a mapping",
      Config.Flaw.HeaderUnparsable(External.Reported("mapping values are not allowed here")) ->
        "agents/skeptic.md: the YAML header does not parse: mapping values are not allowed here",
      Config.Flaw.NotList(Field("tools.histmersion"), Raw("search")) ->
        "agents/skeptic.md: tools.histmersion: expected a list, not 'search'",
      Config.Flaw.NoName -> "agents/skeptic.md: no name",
      Config.Flaw.NameNotFile(Raw("x")) -> "agents/skeptic.md: name 'x' is not the file's",
      Config.Flaw.NoDescription -> "agents/skeptic.md: no description",
      Config.Flaw.RoleUnknown(Vector(Raw("extractor"), Raw("writer"))) ->
        "agents/skeptic.md: model-role is not one of extractor, writer",
      Config.Flaw.RoleUnused(Raw("skeptic"), Vector(Raw("writer"))) ->
        "agents/skeptic.md: role skeptic is no contributed workflow's (one of: writer)",
      Config.Flaw.ToolsUnoffered(Vector(Raw("patch_source")), Vector(Raw("search"), Raw("get_source"))) ->
        ("agents/skeptic.md: tools.histmersion names tools an agent cannot be offered: patch_source (one of " +
          "search, get_source)"),
      Config.Flaw.WebUnknown -> "agents/skeptic.md: tools.web is none, discover, search or fetch",
      Config.Flaw.WritesUnknown -> "agents/skeptic.md: writes is none, propose or direct",
      Config.Flaw.TurnsNotPositive -> "agents/skeptic.md: max-turns is a positive number",
      Config.Flaw.RuntimesUnknown(Vector(Raw("emacs"))) -> "agents/skeptic.md: unknown runtime(s) emacs",
      Config.Flaw.InteractiveUnset ->
        "agents/skeptic.md: an interactive agent needs `interactive: {model, tools}`",
      Config.Flaw.SkillNotNamed(Raw("trailhead")) -> "agents/skeptic.md: its name is not 'trailhead'",
      Config.Flaw.IncludeMissing(Raw("writing-guide")) ->
        "agents/skeptic.md includes 'writing-guide', which is not in agents/_shared/",
      Config.Flaw.SkillMissing(
        Raw("trailhead")
      ) -> "agents/skeptic.md lists skill 'trailhead', which is not in skills/",
      Config.Flaw.AudienceUnknown(Raw("people")) -> "agents/skeptic.md: names no audience: only:people",
      Config.Flaw.MarkerUnclosed(Raw("writer")) -> "agents/skeptic.md: only:writer is never closed",
      Config.Flaw.MarkerNested(Raw("headless"), Raw("writer")) ->
        "agents/skeptic.md: only:headless opens inside only:writer",
      Config.Flaw.MarkerUnopened(Raw("writer")) -> "agents/skeptic.md: /only:writer closes nothing open",
      Config.Flaw.ToolUnknown(Raw("frobnicate")) ->
        "agents/skeptic.md names {{tool:frobnicate}}, which is not an MCP tool",
      Config.Flaw.DescriptionLong ->
        "agents/skeptic.md: the description is over 1,024 characters (opencode refuses it)"
    ).foreach((flaw, want) => assertEquals(Config.Definition(skeptic, flaw).sentence, want))

  test("a watch left out of a sweep says so before why"):
    val unasked = Upstream.Unasked(Relayed(Sentence("CORE asked to wait")))
    assertEquals(unasked.sentence, "not asked this sweep — CORE asked to wait")

  test("the access refusals name the setting or the header that lets the request in"):
    Vector[(Problem, String)](
      Refused.Unauthenticated(Refused.Credential.ApiKey) ->
        ("this server needs its API key from your network — send it as `Authorization: Bearer <key>` (the CLI " +
          "reads HISTMERSION_TOKEN or the file cli.token-file names; the web UI asks for it once)"),
      Refused.Misdirected(Raw("rebound.example")) ->
        ("this server does not answer to the name 'rebound.example' — if that is its own name, add it to auth.hosts " +
          "on the server and restart it"),
      Refused.Policy(Refused.Rule.CrossSite) ->
        ("a change sent by a page of another site is refused — if that page is this server's own (behind a proxy, " +
          "or under another name), add its origin to auth.origins on the server and restart it"),
      Refused.Policy(Refused.Rule.NetworkNotAccepted) ->
        ("this server does not accept requests from your network — its settings list it neither in " +
          "auth.trusted-networks nor in auth.authenticated-networks"),
      Refused.Policy(Refused.Rule.NetworkRefused) ->
        "this server refuses every request from your network — its settings list it in auth.refused-networks",
      Config.Exposed(Raw("0.0.0.0"), Raw("0.0.0.0/0"), Config.Exposure.Unlisted) ->
        ("server.host lists '0.0.0.0', which can be reached from 0.0.0.0/0; none of auth.trusted-networks, " +
          "auth.authenticated-networks and auth.refused-networks names that network — bind to 127.0.0.1 only " +
          "(HISTMERSION_BIND), or list the network as trusted, as authenticated (with auth.api-key set) or as refused"),
      Config.Exposed(Raw("0.0.0.0"), Raw("::/0"), Config.Exposure.NoKey) ->
        ("server.host lists '0.0.0.0', which can be reached from ::/0; auth.authenticated-networks names that " +
          "network, but auth.api-key is empty — set the key (HISTMERSION_API_KEY), list the network in " +
          "auth.refused-networks, or bind to 127.0.0.1 only (HISTMERSION_BIND)"),
      Config.ProxyUndeclared(Raw("https://histmersion.example.edu")) ->
        ("server.public-url = 'https://histmersion.example.edu' is https, so a reverse proxy serves it, and " +
          "auth.trusted-networks trusts loopback, where a proxy on this machine connects from — declare the proxy " +
          "by listing loopback in auth.proxy-networks (what it forwards then needs auth.api-key), or take loopback " +
          "out of auth.trusted-networks"),
      Refused.Policy(Refused.Rule.UndeclaredProxy) ->
        ("this request says a proxy forwarded it (Forwarded, X-Forwarded-For, X-Forwarded-Host, X-Forwarded-Proto, " +
          "X-Real-IP or Via), but it comes from no network the server lists in auth.proxy-networks — send it " +
          "directly, or, when a proxy serves it, add the proxy's network to auth.proxy-networks on the server and " +
          "restart it"),
      Config.WildcardOverlap(Raw("0.0.0.0"), Raw("::1")) ->
        ("server.host lists '0.0.0.0' and '::1' on one port, but '0.0.0.0' already listens on every address of this " +
          "computer — list '0.0.0.0' alone, or only specific addresses (HISTMERSION_BIND)"),
      Config.BindRepeated(Raw("::1"), Raw("[::1]")) ->
        ("server.host lists the address '::1' twice (as '::1' and as '[::1]'), and a second listener cannot bind an " +
          "address the first already holds — list each address once (HISTMERSION_BIND)"),
      Config.BindRepeated(Raw("127.0.0.1"), Raw("127.0.0.1")) ->
        ("server.host lists the address '127.0.0.1' twice (as '127.0.0.1' and as '127.0.0.1'), and a second " +
          "listener cannot bind an address the first already holds — list each address once (HISTMERSION_BIND)")
    ).foreach((p, want) => assertEquals(p.sentence, want))
    assertEquals(
      (Config.BindRepeated(Raw("::1"), Raw("[::1]")): Problem).status.code,
      503,
      "an address listed twice stops the start, as every start-up problem does"
    )
    // stored and answered as its derived entry, no reader of its own: both spellings kept as written
    val repeated = Config.BindRepeated(Raw("::1"), Raw("[::1]"))
    val written = repeated.entry.asJson.noSpaces
    assertEquals(
      written,
      s"""{"group":"Config","case":"BindRepeated","data":{"address":"::1","again":"[::1]"},"text":"${repeated.sentence}"}"""
    )
    assertEquals(RawJson.decode[Answer.Entry](written).flatMap(_.problem), Right(repeated))
    assertEquals(
      (Refused.Policy(Refused.Rule.NetworkRefused): Problem).status.code,
      403,
      "a refused network is forbidden, not asked for a key"
    )
    assertEquals(
      (Refused.Policy(Refused.Rule.UndeclaredProxy): Problem).status.code,
      403,
      "a forwarded request with no proxy declared is forbidden: well formed (not 400), and no key lets it in (not 401)"
    )
