package com.histmersion
package api
package problem
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.json.RawJson

import util.problem.{External, Problem}
import util.problem.Problem.*
import Column.stored
import util.problem.Text.sentence
import json.NewtypeCodecs.{list, problemCodec}

/** A column's text read back on the JVM: every case's frozen sentence reads as the case, in a column and written bare
  * in a record by an earlier version. The browser reads no column ([[SentenceReaders]]).
  */
class ReadBackSuite extends munit.FunSuite:

  /** A relayed problem is known by its sentence only, inside another case too: it reads back as that text. */
  private def relayed(p: Problem): Boolean = p match
    case _: Relayed                   => true
    case Upstream.Unasked(_: Relayed) => true
    case _                            => false

  examples.filterNot(relayed).foreach { p =>
    test(s"${p.group.productPrefix}.${p.productPrefix}: its frozen stored sentence reads back as the case"):
      assertEquals(Column.read(p.stored), p)
  }

  test("a relayed problem is stored as its sentence, which reads back as the text it is"):
    val relayed = Relayed(Sentence("the server is reindexing"))
    assertEquals(relayed.stored, "the server is reindexing")
    assertEquals(Column.read(relayed.stored), Stored.Unrecognised(Stored.Text("the server is reindexing")))

  test("a text stored when a problem named its type in full reads back as its case, and writes back the same"):
    List(
      "edges / dst: 'Gimbutas 1956' is no com.histmersion.domain.library.Citekey: it fails Match[\"[a-z][a-z0-9_-]*\"] (Should match [a-z][a-z0-9_-]*)",
      "unknown com.histmersion.domain.library.Source.Status 'dubious' (one of: 'current', 'contested')",
      "from: '13oo BCE' is unreadable as com.histmersion.Year (e.g. '1300 BCE', '530 CE', '-1300')",
      "geometry: 'Feature' wraps a com.histmersion.api.catalogue.EventGeometry.Geojson in geometry",
      "edges / add: com.histmersion.api.library.Edge 'kristiansen2005' --refutes--> 'gimbutas1956' is stored on 'kristiansen2005'",
      "edits / 0: com.histmersion.api.research.Article.Edits.Anchor 'the Hittite texts' matches 2",
      "chain: com.histmersion.api.automation.Agents.ModelRef 'claude:opus' is repeated",
      "com.histmersion.Latitude '91' is out of [-90, 90]",
      "no com.histmersion.domain.library.Citekey 'anthony2007'",
      "no com.histmersion.api.exchange.Bundle.Kind.Source '0193f1c4-7b2a-7c3e-9d41-5e6f7a8b9c0d' in BundleOrHere, referred to by finding anthony2007#1",
      "com.histmersion.domain.catalogue.Place.Id 'pleiades_579885' already exists",
      "com.histmersion.domain.library.Citekey 'anthony2006' was renamed to 'anthony2007'",
      "com.histmersion.domain.library.Citekey 'anthony2007' has moved on: sent 2026-11-01T10:00:00Z, stored 2026-11-02T08:30:00Z",
      "com.histmersion.util.jobs.Job.Id '412' is running; it needs paused or failed",
      "com.histmersion.domain.catalogue.Place.Id 'pleiades_579885' is referred to by 3 com.histmersion.domain.library.Finding"
    ).foreach { text =>
      val read = Column.read(text)
      assert(read.group != Group.Stored, s"$text reads as $read")
      assertEquals(read.stored, text)
    }

  test("a watch's last error stored as 'not asked this sweep — <why>' reads as the index left out, and writes back"):
    // the text the versions before the typed problem stored, and the one stored now while CORE's why is unclassified
    val text = "not asked this sweep — CORE's daily budget for searches is spent (6 of 6 tokens today, UTC; " +
      "acquire.core.search-daily-tokens)"
    val read = Upstream.Unasked(
      Stored.Unrecognised(
        Stored.Text(
          "CORE's daily budget for searches is spent (6 of 6 tokens today, UTC; acquire.core.search-daily-tokens)"
        )
      )
    )
    assertEquals(Column.read(text), read)
    assertEquals(Column.read(text).stored, text)

  test("a refusal naming a role its pipeline or manager does not use, or the variable that switches it on, reads back"):
    Vector[Problem](
      Invalid.Broken(
        Invalid.Rule.RoleUsed(Invalid.RoleOwner.Pipeline, Raw("enrich-source"), Raw("skeptic"), Vector(Raw("writer"))),
        Vector(Field("--model-for"))
      ),
      Invalid.Broken(
        Invalid.Rule.RoleUsed(
          Invalid.RoleOwner.Manager,
          Raw("review"),
          Raw("drafter"),
          Vector(Raw("reviewer"), Raw("fixer"))
        ),
        Vector(Field("models"))
      ),
      Invalid.Broken.here(Invalid.Rule.RoleUsed(Invalid.RoleOwner.Pipeline, Raw("probe"), Raw("writer"), Vector.empty)),
      Refused.Disabled(
        Refused.Feature("the embedded agent runtime"),
        SettingKey("agents.enabled"),
        Some(SettingKey("HISTMERSION_AGENTS_ENABLED")),
        None
      ),
      Refused.Disabled(
        Refused.Feature("write-back"),
        SettingKey("histmersion.plugins.calibre.write-back"),
        Some(SettingKey("HISTMERSION_PLUGIN_CALIBRE_WRITE_BACK")),
        Some(Refused.Effect.WritesLibrary)
      ),
      Refused.Disabled(
        Refused.Feature("u"),
        SettingKey("histmersion.acquire.allow-unauthorised"),
        None,
        Some(Refused.Effect.Unauthorised)
      )
    ).foreach { p =>
      assertEquals(Column.read(p.stored), p, p.stored)
    }

  test("a switched-off refusal stored before it named its variable still reads back, and writes back the same"):
    val text = "events-gvp is switched off by histmersion.events.gvp.enabled"
    assertEquals(
      Column.read(text),
      Refused.Disabled(Refused.Feature("events-gvp"), SettingKey("histmersion.events.gvp.enabled"), None, None)
    )
    assertEquals(Column.read(text).stored, text)

  test("a problem written bare by an earlier version reads through the column reader"):
    // the frozen sentence of a case reads as that case, which is written typed from then on
    assertEquals(RawJson.decode[Problem]("\"interrupted\""), Right(Problem.Run.Interrupted))
    assertEquals(
      RawJson.decode[List[Problem]]("[\"cancelled by Person\"]"),
      Right(List(Run.Cancelled(Run.Actor.Person)))
    )

  test(
    "the code words of every enum with fields read back from a column; no English sentence shows a type's code name"
  ):
    val reported = External.Reported("x: y")
    val variants: Vector[Problem] =
      Vector(Io.Damage.Checksum, Io.Damage.Truncated, Io.Damage.Undecodable)
        .map(Io.Corrupt(Io.Format.Tar, Where("entry 3"), _)) ++
        Vector(Config.Fault.Empty, Config.Fault.NoSecret, Config.Fault.Unset(SettingKey("HOME")))
          .map(Config.Setting(SettingKey("a.b"), _)) ++
        Vector(
          Config.Fault.Malformed(Raw("x y")),
          Config.Fault.Unknown(Raw("z")),
          Config.Fault.Unregistered(Raw("gone")),
          Config.Fault.NoPlugin(Raw("media-x")),
          Config.Fault.Conflicts(SettingKey("c.d")),
          Config.Fault.Block,
          Config.Fault.WrongKind(Config.ValueKind.Number, Config.ValueKind.Text),
          Config.Fault.Unreadable(reported),
          Config.Fault.Undeclared,
          Config.Fault.NotInDefaults,
          Config.Fault.OutsideRoot,
          Config.Fault.Credential(None),
          Config.Fault.Credential(Some(SettingKey("ANTHROPIC_API_KEY"))),
          Config.Fault.Rejected(Upstream.Name("europeana"), Upstream.Status(401)),
          Config.Fault.Literal,
          Config.Fault.Substituted,
          Config.Fault.Included(Io.Path("/etc/histmersion/extra.conf")),
          Config.Fault.NotReadBack,
          Config.Fault.KeyNeeded(External.PluginSaid("an api.data.gov key"))
        )
          .map(Config.Setting(SettingKey("a.b"), _)) ++
        Vector(
          Invalid.Form.Bare,
          Invalid.Form.EmptyValue,
          Invalid.Form.EnvName(Raw("1X")),
          Invalid.Form.NoEntry(Raw("keepassxc")),
          Invalid.Form.VaultName(Raw("Bad Vault"))
        ).map(Invalid.Reference.here(_)) ++
        Vector(
          Io.Damage.Unreadable(reported),
          Io.Damage.Newer(Raw("2"), Raw("1")),
          Io.Damage.Schema(Count(54L), Count(53L))
        ).map(Io.Corrupt(Io.Format.Json, Where("kit.json"), _)) ++
        Vector(
          Io.Fault.Missing,
          Io.Fault.Omitted,
          Io.Fault.Unlisted,
          Io.Fault.Repeated,
          Io.Fault.Foreign,
          Io.Fault.Ahead(Io.Path("manifest.json")),
          Io.Fault.Unreferenced,
          Io.Fault.Dangling(Count(1L)),
          Io.Fault.Dangling(Count(2L))
        ).map(Io.Entry(Io.Format.Bundle, Io.Path("blobs/ab12"), _)) ++
        Io.Measure.values.toVector.map(m =>
          Io.Entry(Io.Format.Bundle, Io.Path("blobs/"), Io.Fault.Differs(m, Raw("ab12"), Raw("cd34")))
        ) ++
        NotFound.Scope.values.toVector.map(
          NotFound.Dangling(TypeName("Bundle.Kind.Place"), Raw("x_1"), Where("event e"), _)
        ) ++
        Vector(
          Conflict.Hold.Open,
          Conflict.Hold.SameWork(Raw("anthony2006"), Raw("title and year")),
          Conflict.Hold.Folds(Raw("S-1a2b"), Raw("map:anthony2006"), Raw("anthony2006")),
          Conflict.Hold.Merges,
          Conflict.Hold.Renames,
          Conflict.Hold.Renumbers,
          Conflict.Hold.Carries(Raw("keep-right")),
          Conflict.Hold.Stale,
          Conflict.Hold.Conditional(Raw("S-1a2b"), Raw("keep-left"), None),
          Conflict.Hold.Conditional(Raw("S-1a2b"), Raw("keep-left"), Some(Raw("open"))),
          Conflict.Hold.Collides,
          Conflict.Hold.PlanChanged(Raw("7c41e9a2")),
          Conflict.Hold.TextNotSent(Raw("file")),
          Conflict.Hold.CitekeyOfAnother(Raw("anthony2006")),
          Conflict.Hold.CitekeyTaken(Raw("guterbock1997")),
          Conflict.Hold.StoredAs(Raw("anthony2007")),
          Conflict.Hold.Focus(Raw("bronze-age"))
        ).map(Conflict.Held(Where("S-3c1e7a2b source anthony2007 — same work?"), _)) ++
        Vector(
          Conflict.Control.OnePiece(Raw("backup")),
          Conflict.Control.Unrecognised,
          Conflict.Control.Ended,
          Conflict.Control.AlreadyPaused,
          Conflict.Control.NotStarted,
          Conflict.Control.PausedBeforeRestart,
          Conflict.Control.NotPaused
        ).map(Conflict.Job(Raw("j: 1"), Conflict.State("running"), _)) ++
        Io.Format.values.toVector.map(Io.Corrupt(_, Where("x"), Io.Damage.Checksum)) ++
        Vector(
          Io.Flaw.NotFeatureCollection,
          Io.Flaw.NoLandPolygons,
          Io.Flaw.NotArray,
          Io.Flaw.NoImageHeader,
          Io.Flaw.NegativeAt(Io.Quantity.Offset, Count(24L)),
          Io.Flaw.NegativeAt(Io.Quantity.Length, Count(32L)),
          Io.Flaw.DirectorySize(Count(-3L)),
          Io.Flaw.NegativeEntry(Count(7L)),
          Io.Flaw.OffsetOutside(Count(-12L)),
          Io.Flaw.NegativeLength(Count(-1L)),
          Io.Flaw.TooDeep,
          Io.Flaw.RootOutside,
          Io.Flaw.MetadataOver,
          Io.Flaw.PrefixTooLong,
          Io.Flaw.PrefixIncomplete,
          Io.Flaw.PrefixInvalid,
          Io.Flaw.ChunkTruncated,
          Io.Flaw.StreamTruncated,
          Io.Flaw.NotVp8l,
          Io.Flaw.TransformTwice,
          Io.Flaw.CacheSize,
          Io.Flaw.SymbolOutside,
          Io.Flaw.CodeLengthsOver,
          Io.Flaw.CodeLengthsPast,
          Io.Flaw.ReferenceOutside,
          Io.Flaw.CacheWithout,
          Io.Flaw.CacheIndexOutside,
          Io.Flaw.RangeBytes(Count(100L), Count(200L)),
          Io.Flaw.NotLittleEndian,
          Io.Flaw.NoTag(Count(256L)),
          Io.Flaw.NoStripOffsets,
          Io.Flaw.NoPixelScale,
          Io.Flaw.NoTiePoint,
          Io.Flaw.NoStripForRow(Count(4L))
        ).map(f => Io.Corrupt(Io.Format.GeoJson, Where("land.geojson in /a b"), Io.Damage.Structure(f))) ++
        Vector(
          Io.Damage.Foreign,
          Io.Damage.Bytes(Count(127L), Count(50L)),
          Io.Damage.Inflate(External.Reported("java.util.zip.ZipException: invalid stored block lengths")),
          Io.Damage.Unsupported(Io.Variant.PngLayout(Count(3L), Count(16L), Count(1L))),
          Io.Damage.Unsupported(Io.Variant.PngFilter(Count(7L))),
          Io.Damage.Unsupported(Io.Variant.LossyWebP),
          Io.Damage.Unsupported(Io.Variant.NoLossless),
          Io.Damage.Unsupported(Io.Variant.Vp8lVersion),
          Io.Damage.Unsupported(Io.Variant.SpecVersion(Count(2L), Count(3L))),
          Io.Damage.Unsupported(Io.Variant.Compression(Raw("brotli"), Raw("gzip"))),
          Io.Damage.Unsupported(Io.Variant.BigTiff),
          Io.Damage.Unsupported(Io.Variant.SampleBits(Count(32L))),
          Io.Damage.Unsupported(Io.Variant.TiffCompression(Count(5L))),
          Io.Damage.Unsupported(Io.Variant.Bands(Count(3L))),
          Io.Damage.Unsupported(Io.Variant.Planar(Count(2L))),
          Io.Damage.Unsupported(Io.Variant.SampleFormat(Count(3L))),
          Io.Damage.Unsupported(Io.Variant.Tiled)
        ).flatMap(d =>
          Vector(
            Io.Corrupt(Io.Format.PmTiles, Where("the header in /srv/basemap/planet.pmtiles"), d),
            Invalid.Unparsable(Io.Format.WebP, Where("the upload"), d, Vector(Field("body")))
          )
        ) ++
        Io.Held.values.toVector.flatMap(h =>
          Vector(
            Io.Absent(h, Io.Path("/srv/a b"), None),
            Io.Absent(h, Io.Path("/srv/x"), Some(SettingKey("places.sources.lpf-dir")))
          )
        ) ++
        Vector(
          Io.Pinning.Unlisted(Io.Path("db/gazetteer/extracts.json")),
          Io.Pinning.NotBuilt(Io.Path("db/gazetteer/extracts.json")),
          Io.Pinning.Mismatch(Raw("2026-09"), Io.Path("/c/tgn.lpf.gz"), Raw("ab"), Raw("cd")),
          Io.Pinning.Unhosted(
            Raw("2026-09"),
            Raw("wikidata.lpf.gz"),
            Io.Path("/home/x/.cache/histmersion/gazetteer/wikidata.lpf.gz"),
            Raw("9f8e"),
            Count(1_450_000_000L),
            SettingKey("places.sources.dir"),
            SettingKey("HISTMERSION_GAZETTEER_DIR")
          )
        ).map(Io.Extract(Raw("wikidata"), _)) ++
        Vector(
          NotFound.Name(Raw("p_1"), Raw("Troy: Ilion"), Raw("Wilusa"), Raw(""), Vector.empty),
          NotFound.Name(Raw("p_1"), Raw("Troy"), Raw("Ilios"), Raw("grc"), Vector(Raw("grc-latn"))),
          Conflict.Referred(
            Raw("p_1"),
            Raw("Troy"),
            NonEmptyVector.from(Conflict.Referrer.values.toVector.map(Conflict.Reference(Count(2L), _))).get
          ),
          Invalid.Broken.here(Invalid.Rule.PointOnly(Raw("p_1"), Raw("Troy"))),
          Invalid.Broken.here(Invalid.Rule.NameByHand(Raw("Ilion"), Raw("p_1"), Raw("Troy"))),
          Invalid.Broken.here(Invalid.Rule.FocusRegion(Raw("crete"), Vector(Raw("alps"), Raw("aegean")))),
          Invalid.Broken.here(Invalid.Rule.FocusRegion(Raw("crete"), Vector.empty)),
          Invalid.Broken.here(Invalid.Rule.FocusDeeper(Count(12L))),
          Invalid.Broken.here(Invalid.Rule.LabelsUnranged),
          Invalid.Broken.here(Invalid.Rule.SeaFloorUnranged),
          Refused.Space(
            Refused.Work.Layer(Raw("vector tiles")),
            Count(1L),
            Io.Path("/a b"),
            Count(2L),
            Count(3L)
          ),
          Refused.Space(Refused.Work.SeaFloor, Count(1L), Io.Path("/a"), Count(2L), Count(3L)),
          Refused.Space(Refused.Work.Labels, Count(1L), Io.Path("/a"), Count(2L), Count(3L)),
          Upstream.Offline(Upstream.Name("the provider"), None),
          Io.Corrupt(Io.Format.Json, Where("https://x/builds.json"), Io.Damage.Structure(Io.Flaw.NoBuild))
        ) ++
        Config.Capability.values.toVector.map(Config.Unavailable(_)) ++
        Vector(
          Plugin.Fault.Missing,
          Plugin.Fault.NullComponent,
          Plugin.Fault.BadDescriptor,
          Plugin.Fault.VersionMismatch,
          Plugin.Fault.Duplicate,
          Plugin.Fault.Threw(reported),
          Plugin.Fault.SpiMismatch(Raw("7"), Raw("1")),
          Plugin.Fault.ForeignSpi(Raw("2")),
          Plugin.Fault.MissingClass(Raw("org.example.Lib")),
          Plugin.Fault.SignerChanged(Plugin.Signer("ABCD 1234"), Plugin.Signer("EF01")),
          Plugin.Fault.NoProvider,
          Plugin.Fault.Taken(Raw("other.jar"))
        ).map(Plugin.Load(Plugin.Id("p"), _)) ++
        Vector(
          Plugin.Retrieval.NoVault,
          Plugin.Retrieval.NoEntry,
          Plugin.Retrieval.EmptyValue,
          Plugin.Retrieval.Locked,
          Plugin.Retrieval.Failed(External.Stderr("locked")),
          Plugin.Retrieval.Blank(Raw("db/entry")),
          Plugin.Retrieval.Absent(Raw("op://vault/item")),
          Plugin.Retrieval.Unread(Raw("db/entry"), Io.ExitCode(1), External.Stderr("Error: locked")),
          Plugin.Retrieval.NoKey(Vector(Raw("2"), Raw("1:123")), Vector.empty),
          Plugin.Retrieval.NoKey(Vector(Raw("2:9")), Vector(Raw("123"), Raw("456"))),
          Plugin.Retrieval.Key(Raw("2:9"), Plugin.Attempt.NotTouched, None),
          Plugin.Retrieval.Key(Raw("1"), Plugin.Attempt.WrongCredentials, Some(External.Stderr("Error: invalid")))
        ).map(Plugin.Vault(Plugin.Id("v"), _)) ++
        Plugin.Attempt.values.toVector.map(a =>
          Plugin.Vault(Plugin.Id("v"), Plugin.Retrieval.Key(Raw("2"), a, None))
        ) ++
        Vector(Run.Block.SpendCap, Run.Block.Draining, Run.Block.OutsideWindow, Run.Block.Waiting)
          .map(Run.Blocked(_)) ++
        Vector(
          Config.Unpriced(Run.Model("local:llama"), None),
          Config.Unpriced(Run.Model("openai/gpt-6"), Some(Config.Metered.Opencode)),
          Invalid.Undeclared.here(Field("x"), Vector.empty),
          Invalid.Wrapped.here(TypeName("a.B"), Raw("FeatureCollection"), Field("features"))
        ) ++
        Conflict.Activity.values.toVector.flatMap(a => Vector(Run.CutShort(a), Run.Unrecorded(a))) ++
        Vector(
          Content.Requirement.NoYearZero,
          Content.Requirement.CitesNoSource,
          Content.Requirement.UnknownCitekey,
          Content.Requirement.OutsidePeriod,
          Content.Requirement.StandIn,
          Content.Requirement.ExactTitle,
          Content.Requirement.ReplaceText,
          Content.Requirement.AnchorInArticle,
          Content.Requirement.QuoteGiven,
          Content.Requirement.QuoteInRecords(Vector(Raw("anthony2007"), Raw("haak2015"))),
          Content.Requirement.KnownCitekey(Raw("Anthony 2007")),
          Content.Requirement.ListedCandidate(Raw("10.1000/invented")),
          Content.Requirement.EvidenceGiven,
          Content.Requirement.StatusNamed,
          Content.Requirement.NotUnassessed,
          Content.Requirement.EdgeTarget,
          Content.Requirement.NoSelfEdge,
          Content.Requirement.PositiveBce(Raw("-3300")),
          Content.Requirement.CeFromOne(Raw("0")),
          Content.Requirement.Calibrated(Raw("3270")),
          Content.Requirement.StartBeforeEnd(Raw("-1500"), Raw("-1600")),
          Content.Requirement.HistoricalReckoning,
          Content.Requirement.YearWithin(Raw("3000000")),
          Content.Requirement.WithinSourcePeriod(Raw("-3000"), Raw("-2900"), Raw("-1700"), Raw("-1200")),
          Content.Requirement.LinkTarget(Raw("ie-migration")),
          Content.Requirement.UnescapedLinks,
          Content.Requirement.SettingsGiven,
          Content.Requirement.KnownItems(Vector(Raw("goal one"), Raw("ie-migrations"))),
          Content.Requirement.ItemsNamed,
          Content.Requirement.HoldUntil,
          Content.Requirement.DateGiven(Raw("next week")),
          Content.Requirement.PriorityWithin(Raw("25"), Raw("20")),
          Content.Requirement.ExistingItems,
          Content.Requirement.FilterSelects,
          Content.Requirement.FilterRationale,
          Content.Requirement.PlanRationale,
          Content.Requirement.ArticleTitle,
          Content.Requirement.ArticleFilter,
          Content.Requirement.ArticlesGiven,
          Content.Requirement.SummaryGiven,
          Content.Requirement.StatusEvidence,
          Content.Requirement.NotesGiven,
          Content.Requirement.ClaimGiven,
          Content.Requirement.NoClaim,
          Content.Requirement.QuoteInRecord(Raw("anthony2007")),
          Content.Requirement.ReasonGiven,
          Content.Requirement.StatusUnstated(Raw("haak2015"), Raw("contested")),
          Content.Requirement.QuoteInWindow(Share(0.42)),
          Content.Requirement.CenturyAsYear(Raw("-11"), Count(11L)),
          Content.Requirement.PointerPhrase(Raw("this date")),
          Content.Requirement.PlacesResolve(
            Count(1L),
            Count(3L),
            Count(50L),
            Vector(Content.Unplaced(Raw("eastern shipping lanes"), Count(2L)), Content.Unplaced(Raw("x: y"), Count(3L)))
          ),
          Content.Requirement.TooManyArticles(Count(8L), Count(11L)),
          Content.Requirement.FilterTooNarrow(Raw("search {topics: [\"tin\"]}"), Count(1L), Count(5L)),
          Content.Requirement.FilterTooNarrow(Raw("search {query: \"it's: done 3/4\"}"), Count(3L), Count(5L)),
          Content.Requirement.FilterTooBroad(Raw("search {query: \"bronze\"}"), Count(900L), Count(400L)),
          Content.Requirement
            .FilterMisses(
              Raw("search {tags: [\"tin\"]}"),
              Count(2L),
              Count(5L),
              Count(40L),
              Count(60L),
              Vector(Raw("a1"), Raw("b2"))
            ),
          Content.Requirement.FilterMisses(
            Raw("search {tags: ['x' y]}"),
            Count(0L),
            Count(1L),
            Count(0L),
            Count(60L),
            Vector.empty
          ),
          Content.Requirement.TextGiven,
          Content.Requirement.KnownHeading(Raw("Open: questions")),
          Content.Requirement.NoSourcesCited,
          Content.Requirement.ParagraphCited(
            Raw(
              "Tin came by sea, 'it' says: the ingots of Uluburun weigh about ten tonnes, and their lead isotopes point to the Taurus and beyond"
            )
          ),
          Content.Requirement.StillUncited(Raw("Tin came by sea.")),
          Content.Requirement.SectionPresent(Vector(Raw("unresolved"), Raw("open questions"))),
          Content.Requirement.NoFiller(Raw("it is worth noting, delve (+2 more)")),
          Content.Requirement.ClaimTabled(Raw("anthony2007")),
          Content.Requirement.TitleGiven,
          Content.Requirement.ArticleSummary,
          Content.Requirement.NotUsedGenerated,
          Content.Requirement.MethodGenerated,
          Content.Requirement.NotUsedHandWritten,
          Content.Requirement.MethodHandWritten,
          Content.Requirement.ReasonForTabled(Raw("haak2015")),
          Content.Requirement.ReasonForUncited(Raw("haak2015")),
          Content.Requirement.ReasonText(Raw("haak2015")),
          Content.Requirement.TabledAccounted(Raw("knapp2016_tin")),
          Content.Requirement.BodyGiven,
          Content.Requirement
            .FormatClean(Raw("unclosed-fence"), Raw("a code fence is not closed: 'x' y"), Raw("```a b")),
          Content.Requirement.FormatClean(Raw("bad-table"), Raw("a row has 3 cells, the header 2"), Raw("")),
          Content.Requirement.SubGoalTitle(Raw("tin-trade")),
          Content.Requirement.QueryGiven,
          Content.Requirement.QueryWithin(
            Raw(
              "bronze age tin trade routes 'east' of the Taurus, from Afghanistan to the Aegean, by caravan and by sea"
            ),
            Count(60L)
          ),
          Content.Requirement.DoiGiven(Raw("doi: 10/x")),
          Content.Requirement.TooManySubGoals(Count(5L), Count(7L)),
          Content.Requirement.TooManyQueries(Count(8L), Count(9L)),
          Content.Requirement.TooManyWorks(Count(12L), Count(20L)),
          Content.Requirement.PlanGiven,
          Content.Requirement.GoalPlanRationale
        ).map(Content.Proposal(_, Where("findings"))) ++
        Vector(
          Content.Answer(Content.Requirement.TooManyWorks(Count(12L), Count(20L))),
          Content.Answer(Content.Requirement.FormatClean(Raw("r"), Raw("m: n"), Raw("a 1:b"))),
          Content.Proposal(Content.Requirement.ParagraphCited(Raw("a: b")), Where("self-check"))
        ) ++
        Vector(
          Content.Proposal(Content.Requirement.QuoteInRecords(Vector(Raw("a: b"))), Where("proposal for x: y")),
          Content.Proposal(Content.Requirement.StartBeforeEnd(Raw("1 in 2"), Raw("3")), Where("period"))
        ) ++
        Vector(Io.Ending.TimedOut(Millis(5L)), Io.Ending.NotStarted(reported)).map(Io.Process(Io.Tool("pg_dump"), _)) ++
        Io.Access.values.toVector.map(Io.File(_, Io.Path("/a b/c"), reported)) ++
        Vector(
          Io.Kind.File,
          Io.Kind.Directory,
          Io.Kind.Present,
          Io.Kind.Absent,
          Io.Kind.Executable,
          Io.Kind.Listed,
          Io.Kind.OnPath,
          Io.Kind.Inside(Io.Path("/srv/corpus"))
        ).map(Io.Wrong(Io.Path("/x"), _)) ++
        Conflict.Activity.values.toVector.map(Conflict.Busy(_)) ++
        Invalid.Exclusion.values.toVector
          .map(Invalid.Inconsistent.here(NonEmptyVector.of(Field("a"), Field("b")), _)) ++
        Vector(
          Plugin.Refusal.NoLicence,
          Plugin.Refusal.NoImage,
          Plugin.Refusal.NoName,
          Plugin.Refusal.NoLocation,
          Plugin.Refusal.NoDate,
          Plugin.Refusal.Duplicate,
          Plugin.Refusal.Restricted,
          Plugin.Refusal.LicenceNotAllowed(Raw("cc-by-nc")),
          Plugin.Refusal.NotOpen(Field("share_license_status"), Vector.empty),
          Plugin.Refusal.NoOpenImage(Field("usage.access"), Vector(Raw("Usage conditions apply"), Raw("CC BY")))
        ).map(Plugin.Rejected(Plugin.Id("p"), Raw("item 1"), _)) ++
        Upstream.Shape.values.toVector.map(
          Upstream.BadReply.here(Upstream.Name("x"), Upstream.Url("https://x.example"), _, External.Reported("eof"))
        ) ++
        Refused.Rule.values.toVector.map(Refused.Policy(_)) ++
        Vector(
          Invalid.Rule.CronFields(Raw("every day"), Vector(Raw("@daily"), Raw("@hourly"))),
          Invalid.Rule.CronField(Raw("*/0 * * * *"), Invalid.CronPart.Minute, Invalid.CronFlaw.Step(Raw("0"))),
          Invalid.Rule.CronField(Raw("0 1/2/3 * * *"), Invalid.CronPart.Hour, Invalid.CronFlaw.Slashes(Raw("1/2/3"))),
          Invalid.Rule
            .CronField(Raw("0 0 9-3 * *"), Invalid.CronPart.DayOfMonth, Invalid.CronFlaw.Backwards(Raw("9-3"))),
          Invalid.Rule.CronField(Raw("0 0 * 1-2-3 *"), Invalid.CronPart.Month, Invalid.CronFlaw.NotRange(Raw("1-2-3"))),
          Invalid.Rule.CronField(
            Raw("0 0 * * 8"),
            Invalid.CronPart.DayOfWeek,
            Invalid.CronFlaw.NotNumber(Raw("8"), Count(0L), Count(7L))
          ),
          Invalid.Rule.CronNeverFires(Raw("0 0 31 2 *")),
          Invalid.Rule.CronTooOften(Raw("* * * * *"), Count(1L), Count(5L)),
          Invalid.Rule.ZoomWithin(Count(23L), Count(14L)),
          Invalid.Rule.TileWithin(Count(9L), Count(2L), Count(3L)),
          Invalid.Rule.IdentifierGiven,
          Invalid.Rule.IdentifierKind(Raw("Gimbutas 1956")),
          Invalid.Rule.ActivityPage(Raw("yesterday")),
          Invalid.Rule.ActivityPageTime(Raw("noon~job:7")),
          Invalid.Rule.EventCatalogue(Raw("usgs"), Vector(Raw("gvp"), Raw("noaa"), Raw("wikidata"))),
          Invalid.Rule.CitekeyUnchanged(Raw("anthony2007")),
          Invalid.Rule.CitekeyLinkShaped(Raw("Anthony-2007")),
          Invalid.Rule.AssetBase64(Raw("fig1.png"), External.Reported("Illegal base64 character 2d")),
          Invalid.Rule.AssetEmpty(Raw("fig1.png")),
          Invalid.Rule.AssetBytes(Raw("fig1.png"), Count(9000000L), Count(5000000L)),
          Invalid.Rule.RunsCompared,
          Invalid.Rule.RunRepeated,
          Invalid.Rule.RunsPipeline(Raw("1a2b"), Raw("enrich"), Raw("3c4d"), Raw("review")),
          Invalid.Rule.RunsTarget(Raw("1a2b"), Raw("anthony2007"), Raw("3c4d"), Raw("haak2015")),
          Invalid.Rule.QueryGiven,
          Invalid.Rule.RegexTime(Millis(2000L)),
          Invalid.Rule.RegexValid(External.Reported("Unclosed group"), Some(Count(7L)), Raw("(bronze")),
          Invalid.Rule.RegexValid(External.Reported("Dangling meta character '*'"), None, Raw("*tin | copper")),
          Invalid.Rule.MergeId(Raw("not hex!")),
          Invalid.Rule.UpstreamRecord,
          Invalid.Rule.UploadGiven,
          Invalid.Rule.UploadBytes(Count(300L), Count(200L)),
          Invalid.Rule.InlineBytes(Count(30L), Count(20L)),
          Invalid.Rule.Base64Decodable(External.Reported("Illegal base64 character 2d")),
          Invalid.Rule.PathOrBase64,
          Invalid.Rule.ServerSees,
          Invalid.Rule.OriginalForReocr,
          Invalid.Rule.OriginalForDownload,
          Invalid.Rule.Frontmatter,
          Invalid.Rule.NoteCitekey,
          Invalid.Rule.FilterRecorded,
          Invalid.Rule.RegionWithin(Count(10L), Count(20L), Count(5L), Count(5L), Count(8L), Count(8L)),
          Invalid.Rule.ReviewLibraryWide,
          Invalid.Rule.EnrichLibraryWide,
          Invalid.Rule.SeedGoal,
          Invalid.Rule.WatchGoal,
          Invalid.Rule.EnrichCron,
          Invalid.Rule.HintSettings,
          Invalid.Rule.HintUntil,
          Invalid.Rule.HintTargets,
          Invalid.Rule.CronGiven,
          Invalid.Rule.ChainGiven,
          Invalid.Rule.HintConfig(Vector(Raw("prioritise"), Raw("hold"), Raw("skip"))),
          Invalid.Rule.RoundsWithin(Count(9L), Count(2L), Count(6L))
        ).map(Invalid.Broken.here(_)) ++
        Invalid.Purpose.values.toVector.map(p => Invalid.Broken(Invalid.Rule.DoiRecorded(p), Vector(Field("'k'")))) ++
        Vector(
          Invalid.Inverted.here(Invalid.Year(1400L), Invalid.Year(-1500L)),
          Invalid.Inverted(Invalid.Year(-1400L), Invalid.Year(-1500L), Vector(Field("asset 'x/y.jpg'"))),
          Invalid.OutOfTime(Invalid.Year(-6000L), Invalid.Year(-5000L), Invalid.Year(3000L), Vector.empty),
          Invalid.Unparsable(Io.Format.Json, Where("feature #3"), Io.Damage.Undecodable, Vector(Field("body"))),
          Invalid.Unparsable(Io.Format.Json, Where("policy"), Io.Damage.Length(Count(3L), Count(2L)), Vector.empty),
          Conflict.OldKey(TypeName("Citekey"), Raw("a1990"), Raw("b1990"), Conflict.Action.Rename),
          Conflict.Claimed(TypeName("Place.Id"), Raw("x"), Conflict.Clash.Taken),
          Conflict.Claimed(TypeName("Place.Id"), Raw("x"), Conflict.Clash.Removed)
        ) ++
        Run.PauseReason.values.toVector.map(Run.Paused(_, None)) ++
        Vector(
          Invalid.Unknown.here(TypeName("a.B"), Raw("x"), Vector.empty),
          Invalid.Unreadable.here(TypeName("a.B"), Raw("x"), Vector.empty),
          Invalid.OutOfRange.here(TypeName("a.B"), Raw("x"), Invalid.Bounds(None, Some(Raw("9")))),
          Conflict.WrongState(TypeName("a.B"), Raw("k"), Conflict.State("done"), Vector.empty),
          Run.NoModel(Run.Role("writer"), Vector.empty),
          Refused.Quota(Refused.Limit.RateLimit, Refused.Amount(1.5), Refused.Amount(2.5), None)
        ) ++ {
          val (met, url, at) =
            (Upstream.Name("met"), Upstream.Url("https://met.example/objects/7"), Vector(Field("object 7")))
          Vector(
            Upstream.Http(met, url, Upstream.Status(404), External.ReplyExcerpt("gone"), at),
            Upstream.Unreachable(met, url, External.Reported("reset"), at),
            Upstream.Challenged(met, url, Upstream.Status(403), at),
            Upstream.Limited(met, None),
            Upstream.Unasked(Upstream.Limited(met, None)),
            Upstream.Unasked(
              Refused.Quota(Refused.Limit.ProviderQuota, Refused.Amount(6.5), Refused.Amount(6.5), None)
            ),
            Upstream.Timeout(met, url, Millis(5L), at),
            Upstream.BadReply(met, url, Upstream.Shape.Json, External.Reported("eof"), at)
          )
        }
    variants.foreach(v => assertEquals(Column.read(v.stored), v, v.stored))
    (examples ++ variants).filterNot(_.group == Group.Stored).map(_.sentence).foreach { s =>
      assertEquals(testsupport.TypeNamesShown.in(s), None, s"a type's code name in: $s")
    }
