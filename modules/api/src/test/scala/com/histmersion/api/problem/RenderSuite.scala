package com.histmersion
package api
package problem
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json

import util.problem.{External, Problem, Problems}
import util.problem.Problem.*
import Column.stored
import Answer.{answer, decoded, entry, problem, relayedEntry, retryAfter, status}
import util.problem.Text.{sentence, sentences}
import json.NewtypeCodecs.{list, problemCodec}

/** Every case of [[Problem]], through every renderer the api owns: its HTTP status, its English sentence, its frozen
  * stored sentence (which reads back as the case) and its JSON (which decodes back to the case). The renderers' matches
  * name every case, so a new case does not compile until each renderer has an answer for it; this table then fails
  * until the case has a row.
  */
class RenderSuite extends munit.FunSuite:

  /** The frozen sentences and the statuses: changing a sentence breaks every row an earlier version stored. */
  private val Frozen: Map[String, (Int, String)] = Map(
    "Malformed" -> (
      400,
      "edges / dst: 'Gimbutas 1956' is no Citekey: it fails Match[\"[a-z][a-z0-9_-]*\"] (Should match [a-z][a-z0-9_-]*)"
    ),
    "Unknown" -> (
      400,
      "unknown Source.Status 'dubious' (one of: 'current', 'contested')"
    ),
    "Unreadable" -> (
      400,
      "from: '13oo BCE' is unreadable as Year (e.g. '1300 BCE', '530 CE', '-1300')"
    ),
    "Missing" -> (400, "rationale is required"),
    "Undeclared" -> (400, "staus is undeclared (one of: 'status', 'tags')"),
    "Wrapped" -> (400, "geometry: 'Feature' wraps a EventGeometry.Geojson in geometry"),
    "StoredOn" -> (
      400,
      "edges / add: Edge 'kristiansen2005' --refutes--> 'gimbutas1956' is stored on 'kristiansen2005'"
    ),
    "Ambiguous" -> (400, "edits / 0: Article.Edits.Anchor 'the Hittite texts' matches 2"),
    "Repeated" -> (400, "chain: Agents.ModelRef 'claude:opus' is repeated"),
    "Misplaced" -> (400, "status belongs under metadata"),
    "OutOfRange" -> (400, "Latitude '91' is out of [-90, 90]"),
    "Inconsistent" -> (400, "lat, lon break Together"),
    "Inverted" -> (400, "goal 'g': the period would start in -900 after it ends in -1000"),
    "OutOfTime" -> (400, "source / period_start: year 6000 is beyond [-5000, 3000]"),
    "Unparsable" -> (
      400,
      "the Json input at LPF is damaged: Unreadable — expected '\"' or '}', offset: 0x00000002"
    ),
    "Reference" -> (400, "plugins / api-key: no secret reference: EnvName '1PASSWORD'"),
    "Broken" -> (400, "cron: breaks CronField | 61 * * * * | Minute | NotNumber | 61 | 0 | 59"),
    "Entity" -> (404, "no Citekey 'anthony2007'"),
    "Copy" -> (404, "no open-access copy of 'doi:10.1017/S0003598X00000001'"),
    "Listed" -> (404, "no MergeRepo.MergeIdOrPrefix '0193f1' listed by Merges"),
    "Name" -> (404, "no name 'Cnossus' in 'la' for 'Knossos' [pleiades_579885] (has: 'en', '')"),
    "SetByHand" -> (404, "nothing set by hand on 'Knossos' [pleiades_579885]"),
    "Undownloaded" -> (404, "no download of the vector layer; fetch with `histmersion basemap fetch`"),
    "Dangling" -> (
      404,
      "no Bundle.Kind.Source '0193f1c4-7b2a-7c3e-9d41-5e6f7a8b9c0d' in BundleOrHere, referred to by finding anthony2007#1"
    ),
    "Held" -> (409, "held SameWork anthony2006 | doi: source anthony2007"),
    "Referred" -> (409, "place 'Arkona' [local_arkona] is referred to by 3 Events, 1 ChildPlaces"),
    "Job" -> (409, "job '0193f1c4' is queued: NotStarted"),
    "Entry" -> (500, "Bundle entry entities/sources.ndjson: Differs Lines 4/3"),
    "AlreadyExists" -> (409, "Place.Id 'pleiades_579885' already exists"),
    "Renamed" -> (409, "Citekey 'anthony2006' was renamed to 'anthony2007'"),
    "Aliased" -> (409, "Place.Id 'pleiades_579885' is an old key of 'pleiades_579886'"),
    "OldKey" -> (409, "Delete by an old key: Citekey 'anthony2006' is now 'anthony2007'"),
    "Collides" -> (409, "Finding.Id 'anthony2007#1' already exists; 'anthony2007' collides"),
    "Claimed" -> (409, "Place.Id 'local_zzn_arkona' is claimed: OldKeyOf 'local_arkona'"),
    "Stale" -> (
      412,
      "Citekey 'anthony2007' has moved on: sent 2026-11-01T10:00:00Z, stored 2026-11-02T08:30:00Z"
    ),
    "WrongState" -> (409, "Job.Id '412' is running; it needs paused or failed"),
    "Busy" -> (503, "Backup is still running"),
    "Referenced" -> (
      409,
      "Place.Id 'pleiades_579885' is referred to by 3 Finding"
    ),
    "Policy" -> (403, "refused: RunTokenRequired"),
    "Quota" -> (429, "DailySpend is spent: 5.25 of 4.5 until 2025-11-10T00:00:00Z"),
    "Disabled" -> (403, "events-gvp is switched off by histmersion.events.gvp.enabled"),
    "Space" -> (403, "no room for Gebco | 4200000000 | /srv/histmersion/basemap | 5000000000 | 2000000000"),
    "Offline" -> (503, "the provider is offline until 2025-11-10T00:00:00Z"),
    "Changed" -> (502, "the provider's file changed"),
    "Rebuilt" -> (502, "https://build.protomaps.com/20261101.pmtiles changed during the download"),
    "Mislisted" -> (
      502,
      "https://build.protomaps.com/20261101.pmtiles is 120000000000 bytes, listed 121000000000 for '20261101.pmtiles'"
    ),
    "Http" -> (
      502,
      "core answered HTTP 429 at https://api.core.ac.uk/v3/search/works — {\"message\":\"rate limited\"}"
    ),
    "Challenged" -> (502, "archive-org challenged with HTTP 403 at https://archive.org/download/x/x.pdf"),
    "Limited" -> (429, "claude is limited until 2025-11-10T00:00:00Z"),
    "Unasked" -> (500, "not asked this sweep — CORE asked to wait until 2025-11-10T00:00:00Z"),
    "Unreachable" -> (
      502,
      "cannot reach core at https://api.core.ac.uk/v3/search/works — java.io.IOException: connection reset"
    ),
    "Timeout" -> (504, "core did not answer at https://api.core.ac.uk/v3/search/works within 30000 ms"),
    "BadReply" -> (
      502,
      "core's answer at https://api.core.ac.uk/v3/search/works is not Json — expected json value got '<html>' (line 1)"
    ),
    "File" -> (500, "could not Read /tmp/bundle.hmx — java.nio.file.NoSuchFileException: /tmp/bundle.hmx"),
    "Wrong" -> (500, "/srv/seed is not Directory"),
    "Absent" -> (500, "no GazetteerSeed at /srv/histmersion/gazetteer (set init.data-dir)"),
    "Extract" -> (
      500,
      "extract 'geonames' cannot be read: Mismatch | 2026-09 | /home/x/.cache/histmersion/gazetteer/geonames.lpf.gz | ab12 | cd34"
    ),
    "Oversize" -> (500, "whg-crete.json is 612 MB, over places.sources.max-lpf-mb"),
    "Corrupt" -> (500, "the Png data at terrarium tile 3/4/2 is damaged: Length 1024/1000"),
    "Process" -> (500, "calibredb exited with 2 — calibredb: library is locked"),
    "Database" -> (
      500,
      "the database refused to Migrate — org.postgresql.util.PSQLException: relation \"jobs\" does not exist"
    ),
    "Setting" -> (503, "histmersion.agents.providers.codex.command is wrong: Empty"),
    "Unavailable" -> (503, "this server has no Originals"),
    "Reload" -> (503, "reloading the settings failed at Close in plugins"),
    "Cycle" -> (503, "reload cycle: db → lease → db"),
    "Load" -> (500, "plugin media-met did not load: NullComponent"),
    "Rejected" -> (422, "media-met did not take '248284': NotOpen isPublicDomain is false"),
    "Vault" -> (500, "secret vault keepassxc failed: TimedOut 20000 ms"),
    "Dependency" -> (
      409,
      "doi-mirror-0.1.0.jar needs com.github.plokhotnyuk.jsoniter-scala:jsoniter-scala-core_3 2.42.0; it ships 2.41.5"
    ),
    "Said" -> (503, "gazetteer-awmc says (Unavailable): the AWMC server is in maintenance"),
    "Paused" -> (409, "paused for Allowance until 2025-11-10T00:00:00Z"),
    "NoModel" -> (
      409,
      "no model of the reviewer chain can run (claude-code:opus passed over (model claude-code:opus is unpriced) ‖ " +
        "codex:gpt-6 passed over (no fall from a plan to a paid key for reviewer))"
    ),
    "Exhausted" -> (
      409,
      "all models of the enricher chain are spent (PlanOrAllowance, At 2025-11-10T00:00:00Z claude-code:sonnet | " +
        "Claude plan [claude-plan]) (claude-code:sonnet passed over (below the floor of Claude plan (5-hour window 3% " +
        "left, floor 10%)))"
    ),
    "PassedOver" -> (409, "local:qwen passed over (cooling until 2025-11-10T00:00:00Z)"),
    "Unassigned" -> (409, "no model set for skeptic (Call)"),
    "Cooling" -> (409, "cooling until 2025-11-10T00:00:00Z"),
    "BelowFloor" -> (409, "below the floor of ChatGPT plan (weekly window 2% left, floor 5%)"),
    "BelowAllowance" -> (409, "below its allowance: 5 usd/day | 4.9 USD | 0.5 USD ‖ 2M tokens/week | 1.9M | 0"),
    "AllowanceUnread" -> (
      409,
      "allowance unreadable (claude-code:*: '5 usd/fortnight' is unreadable as ModelChains.Limit.Spec)"
    ),
    "PlanOnly" -> (409, "no fall from a plan to a paid key for writer"),
    "Blocked" -> (409, "held back: PoolFull review"),
    "Interrupted" -> (409, "interrupted"),
    "Cancelled" -> (409, "cancelled by Person"),
    "Draining" -> (409, "draining since 2025-11-10T00:00:00Z"),
    "Unpriced" -> (503, "model claude-opus-5-5 is unpriced (ApiKey claude-code)"),
    "CutShort" -> (409, "cut short: MergeApply"),
    "Unrecorded" -> (409, "unrecorded: MergeApply"),
    "Aborted" -> (409, "step draft cannot go on: CheckpointMismatch"),
    "Finding" -> (422, "finding: UncitedParagraph"),
    "Proposal" -> (422, "claims: TooManyClaims 12/15"),
    "Answer" -> (422, "the answer breaks SummaryGiven"),
    "Unexpected" -> (
      500,
      "backup failed unexpectedly (java.lang.IllegalStateException, problem 3f2a9c1e) — java.lang.IllegalStateException: pg_dump vanished"
    ),
    "Unclassified" -> (400, "unclassified: cannot open the bundle"),
    "Unrecognised" -> (500, "merge apply crashed: NullPointerException"),
    "Relayed" -> (500, "the server is reindexing")
  )

  test("every case has a row, and every row a case"):
    assertEquals(examples.map(_.productPrefix).toSet, Frozen.keySet)
    assertEquals(examples.size, Frozen.size)

  examples.foreach { p =>
    val (code, frozen) = Frozen(p.productPrefix)

    test(s"${p.group.productPrefix}.${p.productPrefix}: its status"):
      assertEquals(p.status.code, code)

    test(s"${p.group.productPrefix}.${p.productPrefix}: an English sentence"):
      assert(p.sentence.trim.nonEmpty)
      assertEquals(p.sentence, p.sentence.trim)

    test(s"${p.group.productPrefix}.${p.productPrefix}: its frozen stored sentence"):
      assertEquals(p.stored, frozen)

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
    assertEquals(
      (Invalid.Missing.here(Field("rationale")): Problem).relayedEntry.asJson.noSpaces,
      """{"group":"Relayed","case":"Relayed","data":{},"text":"rationale is required"}"""
    )
    assertEquals(relayed.relayedEntry.asJson.noSpaces, written)
    assertEquals((relayed: Problem).asJson.noSpaces, written)
    assertEquals(RawJson.decode[Problem](written), Right(relayed))

  test("a problem written bare by an earlier version that no case writes stays bare"):
    // any other text (an English sentence, an old message) is the stored text as it was, written back byte for byte
    List("converter down", "no legal open-access PDF found for 10.1/x", "").foreach { text =>
      val bare = Json.fromString(text).noSpaces
      val read = RawJson.decode[Problem](bare)
      read match
        case Right(unrecognised: Stored.Unrecognised) => assertEquals(unrecognised.stored, text)
        case other                                    => fail(s"$bare reads as $other")
      assertEquals(read.map(_.asJson.noSpaces), Right(bare))
    }

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

  test("the status of several problems is the first one's; a spent allowance says when to retry"):
    val many: Problems = Problems.of(NotFound.Entity(TypeName("x.Y"), Raw("k")), Invalid.Missing.here(Field("f")))
    assertEquals(many.status.code, 404)
    assertEquals(
      examples.collect { case q: Refused.Quota => q.retryAfter },
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
    assertEquals(said(Upstream.Shape.Image), "x's answer at https://x.example/f is not an image — eof")
    assertEquals(Upstream.Shape.values.map(said).distinct.length, Upstream.Shape.values.length)
    assert(Upstream.Shape.values.forall(s => !said(s).contains(s" ${s.productPrefix} ")))

  test("a missing copy with the chain's answers lists them, in its sentence and in its stored sentence"):
    val copy = NotFound.Copy(
      Raw("10.1/x"),
      Vector(
        NotFound.Answer(
          NotFound.Fetcher("unpaywall"),
          NotFound.Kind.Miss,
          NotFound.Copy(Raw("doi:10.1/x"), Vector.empty)
        ),
        NotFound.Answer(
          NotFound.Fetcher("landing"),
          NotFound.Kind.Refused,
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
        "landing: refused — landing answered HTTP 403 at https://x.example/f with a bot-protection challenge, not the page"
    )
    assertEquals(
      copy.stored,
      "no open-access copy of '10.1/x' — unpaywall: Miss — no open-access copy of 'doi:10.1/x'; " +
        "landing: Refused — landing challenged with HTTP 403 at https://x.example/f"
    )
    assert(
      Column.read(copy.stored) match
        case _: Stored.Unrecognised => true
        case _                      => false
      ,
      "the answers are not parsed back"
    )
    assertEquals(Column.read(copy.stored).stored, copy.stored)
    assertEquals(copy.status.code, 404)

  test("a text no case writes reads as it was, and writes back the same"):
    List(
      "cancelled",
      "interrupted ",
      "no such file: /tmp/x",
      "paused for Nothing",
      "enrich: all models for role writer are cooling down",
      ""
    ).foreach { text =>
      assertEquals(Column.read(text), Stored.Unrecognised(Stored.Text(s"$text")))
      assertEquals(Column.read(text).stored, text)
    }

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
    Vector(unauthorised, writeBack).foreach(p => assertEquals(Column.read(p.stored), p, p.stored))

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
    val floor = Run.PassedOver(opus, NonEmptyVector.of(Run.BelowFloor(Raw("Claude plan"), Raw("5-hour 3% left"))))
    val planOnly = Run.PassedOver(gpt, NonEmptyVector.of(Run.PlanOnly(role)))
    Vector[(Problem, String)](
      Run.Unassigned(Run.Role("fact-checker"), Run.Asked.Call) ->
        ("no model for the fact-checker role — set agents.roles.fact-checker (HISTMERSION_AGENTS_ROLE_FACT_CHECKER) " +
          "to provider:model[ > provider:model …], save one with `histmersion agent models set fact-checker " +
          "<provider:model>…`, or pass one"),
      Run.Unassigned(role, Run.Asked.Listing) ->
        "no model — set agents.roles.enricher or `histmersion agent models set enricher …`",
      Run.NoModel(role, Vector(floor, planOnly)) ->
        ("no model of the enricher chain can run: claude-code:opus: the Claude plan is below its floor: 5-hour 3% " +
          "left; codex:gpt-6: not used — the chain does not fall from a plan to a paid key (histmersion agent quota " +
          "fallthrough enricher on)"),
      Run.Exhausted(role, Run.Floor.PlanOrAllowance, Run.Frees.At(at, opus, Vector(held)), Vector(floor, planOnly)) ->
        ("all models for role enricher are below their plan floor or allowance; next reset at 2025-11-10T00:00:00Z " +
          "(claude-code:opus), when the run resumes by itself; the Claude plan is held (release it with `histmersion " +
          "agent quota release claude-plan`) (claude-code:opus: the Claude plan is below its floor: 5-hour 3% left; " +
          "codex:gpt-6: not used — the chain does not fall from a plan to a paid key (histmersion agent quota " +
          "fallthrough enricher on))"),
      Run.Exhausted(role, Run.Floor.Allowance, Run.Frees.Released(NonEmptyVector.of(held)), Vector.empty) ->
        ("all models for role enricher are below their allowance; no window frees by itself: the Claude plan is held " +
          "(release it with `histmersion agent quota release claude-plan`) ()"),
      Run.Exhausted(role, Run.Floor.Allowance, Run.Frees.Never, Vector.empty) ->
        "all models for role enricher are below their allowance; no window frees by itself — raise or reset the allowance ()",
      Run.Cooling(at) -> "its plan reported its usage limit; skipped until 2025-11-10T00:00:00Z",
      Run.BelowAllowance(
        NonEmptyVector.of(
          Run.Spent(Raw("5 usd/day"), Raw("4.9 USD"), Raw("0.5 USD")),
          Run.Spent(Raw("2M tok/week"), Raw("2M"), Raw("0"))
        )
      ) -> "below its allowance: 5 usd/day (4.9 USD used, reserve 0.5 USD), 2M tok/week (2M used, reserve 0)",
      Run.AllowanceUnread(
        NonEmptyVector.of(
          Run.Unread(Raw("local:*"), NonEmptyVector.of(Relayed(Sentence("x")), Relayed(Sentence("y")))),
          Run.Unread(Raw("codex:*"), NonEmptyVector.of(Relayed(Sentence("z"))))
        )
      ) -> "its allowance cannot be read (local:*: x; y; codex:*: z)"
    ).foreach((p, want) => assertEquals(p.sentence, want))

  test("a watch left out of a sweep says so before why, in its sentence and its stored text"):
    val unasked = Upstream.Unasked(Relayed(Sentence("CORE asked to wait")))
    assertEquals(unasked.stored, "not asked this sweep — CORE asked to wait")
    assertEquals(unasked.sentence, "not asked this sweep — CORE asked to wait")
