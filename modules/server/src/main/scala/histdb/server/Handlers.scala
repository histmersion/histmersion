package histdb.server

import histdb.core.*
import histdb.db.{AgentStateRepo, AggregateRepo, ArticleRepo, AttestationRepo, BackupRepo, EventRepo, GoalRepo, Importer, Migrate, PlaceRepo, PlaceScope, SourceRepo, TreeRepo}
import java.nio.file.{Files, Path}
import java.time.Instant

/** The ONE implementation of every operation in the contract. REST routes and MCP tools both call
  * these; the CLI reaches them over HTTP. Pure functions of (deps, input) except for the DB. */
final class Handlers(deps: Deps, converter: ConverterClient):

  /** The ordinary construction: the OCR sidecar client comes from config. The two-argument form is
    * the seam an HTTP test uses to substitute a fake converter (plan §1.8). */
  def this(deps: Deps) = this(deps, ConverterClient.fromConfig(deps.cfg.converter))

  private val ds  = deps.ds
  private val cfg = deps.cfg

  /** Every source write re-materialises that citekey's `finding_places` (see `SourceRepo.write`), so
    * the gazetteer tolerance travels with the write rather than only with `places rebuild`. */
  private val tolKm = cfg.places.pointToleranceKm
  private val backups = BackupService(cfg)

  /** One lock per server (plan §1.10): held by a restore, checked by EVERY write — here, in
    * `OpsHandlers`, in `IngestHandlers` and in the acquisition handlers, which is why it lives on
    * `Deps` rather than in this class. A restore drops and recreates every table; a write that
    * slipped past the lock wrote into a schema that was about to be replaced. */
  val maintenance: Maintenance = deps.maintenance

  /** Stream 2.8 — lint, the bookkeeping queues and the read-only query. Its own object (one file per
    * stream), wired into REST and MCP through this same `Handlers` value. */
  val ops: OpsHandlers = OpsHandlers(deps)

  /** The ingest/jobs/assets surface (plan §1.8), in its own file but reached only through here — so
    * "one implementation per operation" still holds for REST, MCP and the CLI alike. */
  val ingest: IngestHandlers = IngestHandlers(deps, converter)

  def version: VersionInfo = VersionInfo(Version.name, Version.version)

  def health: Health =
    val conv = ingest.converterHealth
    scala.util.Try(Migrate.current(ds)) match
      case scala.util.Success(v) => Health(if conv == "ok" then "ok" else "degraded", "ok", v, Some(conv))
      case scala.util.Failure(e) => Health("degraded", s"error: ${e.getMessage}", None, Some(conv))

  def uiConfig: UiConfig = cfg.ui

  /** Legacy `GET /api/search` — kept working (the web page uses it) by delegating to the filtered
    * search and dropping the evidence, so there is ONE query path (FTS ranking and snippets
    * included). New callers should use [[sources]]. */
  def search(q: Option[String], limit: Option[Int]): List[SearchHit] =
    sources(Filter(q = q.map(_.trim).filter(_.nonEmpty), limit = limit.getOrElse(0))).hits.map(_.source)


  /** `limit <= 0` means "use the configured page size" — no literal page size exists in the code —
    * and any limit is CAPPED at `limits.max-page`. An uncapped `limit` is a denial of service by
    * query parameter: `?limit=100000000` used to build every row of it. */
  private def resolve(f: Filter): Filter =
    f.copy(limit = cfg.limits.page(Some(f.limit), cfg.limits.search), offset = f.offset.max(0))

  /** How the gazetteer reads this filter's `places` values (plan §1.6a): the ids among them expand
    * to themselves plus their descendants as of `f.period`; the rest fall back to the name ILIKE.
    * Resolved ONCE per request and threaded into every query, so the sidebar, the map, the facets and
    * the histogram all agree. No `places` filter ⇒ no query at all. */
  private def scopeOf(f: Filter): PlaceScope =
    if f.places.isEmpty then PlaceScope.empty else PlaceRepo.scopeFor(ds, f.places, f.period)

  /** `GET /api/sources` — THE discovery operation: sources with their matched findings and reasons. */
  def sources(f: Filter): SearchResult =
    SourceRepo.sources(ds, resolve(f), cfg.limits.snippetWords, scopeOf(f))

  /** `GET /api/facets` — the vocabulary of one dimension inside the current filter. */
  def facets(field: String, prefix: Option[String], f: Filter): Either[ApiError, List[Facet]] =
    FacetField.parse(field)
      .toRight(ApiError(s"unknown facet field '$field' (one of: ${FacetField.all.mkString(", ")})"))
      .map(ff => SourceRepo.facets(ds, ff, prefix, resolve(f), resolve(f).limit, scopeOf(f)).toList)

  /** `GET /api/sources/{ck}/articles` — the compiled articles that cite this source (plan §5.2).
    * 404 when the citekey itself is unknown, so "no articles yet" and "no such source" are different
    * answers. */
  def sourceArticles(citekey: String): Either[ApiError, List[Backlink]] =
    getSource(citekey).map(_ => ArticleRepo.backlinksForSource(ds, citekey))

  def getSource(citekey: String): Either[ApiError, SourceWithFindings] =
    SourceRepo.get(ds, citekey)
      .map((s, fs) => SourceWithFindings(s, fs.toList))
      .toRight(ApiError(s"No source '$citekey'"))

  /** `GET /api/findings` — the MAP FEED (plan §1.0): the findings that themselves satisfy the
    * filter's finding-level criteria, of the sources that satisfy its source-level ones. Same query
    * parameters as [[sources]]; `limit <= 0` means the configured `limits.findings`. */
  def findings(q: Api.FindingsQuery): List[GeoHit] =
    val f = q.toFilter
    // the map feed's own cap is `limits.findings`, which is BOTH the default and the ceiling
    SourceRepo.findingsFor(
      ds, f.copy(limit = cfg.limits.page(Some(f.limit), cfg.limits.findings).min(cfg.limits.findings)),
      scopeOf(f)).toList

  /** `/api/aggregate` — the timeline strip (§1.7) AND the nested tree (§1.6). Narrowed by the SAME
    * filter as `/api/sources` and `/api/findings`; the window defaults to the configured UI span.
    *
    * `by=time` alone keeps the wave-1 behaviour byte for byte: the flat bars, gaps included, which
    * is what the strip draws. Adding `depth`/`samples` nests the same numbers, and
    * `by=region|time,region|region,time` fills `tree` instead. SQL only ever SELECTS rows;
    * [[Aggregate]] and [[Tree]] (both pure) do all the bucketing, so the strip, the tabs, the CLI
    * and MCP cannot drift apart.
    *
    * Deeper levels are LAZY by construction: there is no cursor — a client narrows `from`/`to` (or
    * `place`) to one bucket and asks again, which is the same query with a smaller window. */
  def aggregate(q: Api.AggregateQuery): Either[ApiError, TimeHistogram] =
    def norm(o: Option[String], dflt: String) = o.map(_.trim.toLowerCase).filter(_.nonEmpty).getOrElse(dflt)
    val unit = norm(q.unit, "sources")
    Tree.parseBy(norm(q.by, Tree.KindTime))
      .toRight(ApiError(s"unsupported by='${norm(q.by, Tree.KindTime)}' — use " +
        "time | region | time,region | region,time"))
      .flatMap { axes =>
        if unit != "sources" && unit != "findings" && unit != Aggregate.UnitStarts then
          Left(ApiError(s"unsupported unit='$unit' — use sources, findings or starts"))
        else aggregateOn(axes, unit, q)
      }

  /** The aggregation itself, once `by`/`unit` have been validated.
    *
    * The window is CLAMPED to the configured deep-time span (`ui.time.min/max`) and the resulting
    * bucket count is capped (`ui.max-buckets`). Without either, `from=-1e15&to=1e15` asked for
    * 400 000 000 000 buckets: the request built them one `TimeBucket` at a time until the heap gave
    * out, and `to = Long.MaxValue` overflowed the bucket arithmetic on top of that. */
  private def aggregateOn(axes: List[String], unit: String, q: Api.AggregateQuery)
      : Either[ApiError, TimeHistogram] =
    val t = cfg.ui.time
    // the window to bucket = the filter's deep-time range, defaulted (half-open bounds included)
    val a = Filter.lowerGiven(q.filter.period).getOrElse(t.defaultFrom)
    val b = Filter.upperGiven(q.filter.period).getOrElse(t.defaultTo)
    val (lo, hi)   = if a <= b then (a, b) else (b, a)
    val (from, to) = (clampToSpan(lo, t), clampToSpan(hi, t))
    val size       = Aggregate.bucketSize(to - from)
    val wanted     = Aggregate.bucketCount(from, to, size)
    if wanted > BigInt(math.max(1, cfg.ui.maxBuckets)) then
      Left(ApiError(s"that window needs $wanted buckets of $size year(s), over the " +
        s"${cfg.ui.maxBuckets} allowed (ui.max-buckets) — narrow `from`/`to`"))
    else Right(aggregateWindow(axes, unit, q, from, to, size))

  /** A caller's year, forced inside the configured span. `ui.time.min/max` is what the atlas can
    * draw and what the corpus covers; nothing outside it can have a non-empty answer anyway. */
  private def clampToSpan(v: Long, t: TimeConfig): Long = math.max(t.min, math.min(t.max, v))

  private def aggregateWindow(axes: List[String], unit: String, q: Api.AggregateQuery,
      from: Long, to: Long, size: Long): TimeHistogram =
    val window  = q.filter.copy(period = Some(Period(from, to)))
    val scope   = scopeOf(window)
    val depth   = q.depth.filter(_ > 0).getOrElse(cfg.ui.treeDepth).max(1).min(Tree.MaxDepth)
    val samples = q.samples.getOrElse(cfg.ui.treeSamples).max(0).min(Tree.MaxSamples)
    // `unit=starts` is the SOURCE unit counted a different way (plan §1.7): same rows, one bar each,
    // by where the period begins. The rows query therefore still asks for sources.
    val starts   = unit == Aggregate.UnitStarts
    val rowUnit  = if starts then "sources" else unit
    val counting = if starts then Aggregate.Counting.Starts else Aggregate.Counting.Overlap
    val flat =
      if axes.headOption.contains(Tree.KindTime) then
        Aggregate.histogram(AggregateRepo.periods(ds, rowUnit, window, scope).toList, from, to, size,
          counting)
      else Nil
    val base = TimeHistogram(from, to, size, flat, by = axes.mkString(","), unit = unit, depth = depth)
    // The TIMELINE STRIP asks for `by=time` on every span change and only wants `buckets`; building
    // the tree as well would double its cost for nothing. A caller that wants the nested answer for
    // the same axis says so by asking for a `depth`/`samples` — which is exactly what the "by time"
    // tab does, and what the CLI's `--group time` sends.
    if axes == List(Tree.KindTime) && q.depth.isEmpty && q.samples.isEmpty then base
    else treeOn(axes, rowUnit, window, scope, from, to, depth, samples, q.asOf, base)

  /** The nested part of [[aggregateOn]] — split out so the flat strip never pays for it. */
  private def treeOn(axes: List[String], unit: String, window: Filter, scope: PlaceScope,
      from: Long, to: Long, depth: Int, samples: Int, askedAsOf: Option[Long],
      base: TimeHistogram): TimeHistogram =
    val rows = TreeRepo.rows(ds, unit, window, scope).toList
    // the gazetteer slice these rows hang off — one upward closure, not the whole 16 k-place table
    def gaz = TreeRepo.gazetteer(ds, rows)
    // `by=region` reads the hierarchy at ONE year: the caller's, else the middle of the window
    val asOf = askedAsOf.orElse(Some(Tree.midpoint(from, to)))
    // LAZY DEEPER LEVELS: `place=<one gazetteer id>` roots the region tree AT that place, so
    // expanding a node returns its subtree rather than the whole forest with the node buried in it.
    // (Two place chips, or a name that is not a gazetteer id, is an ordinary filter — no re-rooting.)
    val root = window.places.filter(p => scope.idsFor(p).isDefined) match
      case one :: Nil => Some(one)
      case _          => None
    val tree = axes match
      case Tree.KindTime :: Nil                    => Tree.timeTree(rows, from, to, depth, samples)
      case Tree.KindRegion :: Nil                  =>
        val (ns, rs) = gaz; Tree.regionTree(rows, ns, rs, asOf, depth, samples, root)
      case Tree.KindTime :: Tree.KindRegion :: Nil =>
        val (ns, rs) = gaz; Tree.timeRegionTree(rows, from, to, ns, rs, depth, samples)
      case _                                       =>
        val (ns, rs) = gaz; Tree.regionTimeTree(rows, from, to, ns, rs, asOf, depth, samples, root)
    base.copy(
      total = rows.iterator.map(_.key).toSet.size,
      // `time,region` reads the gazetteer at each bucket's OWN midpoint, so no single asOf applies
      asOf = if axes == List(Tree.KindTime, Tree.KindRegion) || axes == List(Tree.KindTime) then None else asOf,
      tree = tree)

  /** Rows whose target is not a source but IS a compiled article are flagged, so every renderer (MCP,
    * CLI over HTTP, the HTML page) can link them into the wiki without a second lookup. */
  def neighbors(citekey: String, kinds: List[String] = Nil): List[NeighborRow] =
    val rows = SourceRepo.neighbors(ds, citekey, kinds).toList
    if rows.forall(_.title.isDefined) then rows
    else
      val slugs = articleSlugs.toSet
      rows.map(r => if r.title.isEmpty && slugs.contains(r.citekey) then r.copy(article = true) else r)

  def graph(citekey: String, depth: Option[Int]): SourceGraph =
    SourceRepo.graph(ds, citekey, depth.getOrElse(2).max(0).min(4))

  def contradictions(citekey: Option[String], since: Option[Long]): ContradictionsResult =
    SourceRepo.contradictions(ds, citekey, since)

  def seed(req: SeedRequest): Either[ApiError, SeedResult] = maintenance.guard("seed"):
    val asked = Option(req.dir).map(_.trim).filter(_.nonEmpty).getOrElse(cfg.corpus.dir)
    if asked.isEmpty then Left(ApiError("no directory given and histdb.corpus.dir is not configured"))
    else
      // `dir` is a SERVER-side path chosen by the caller, so it is confined to `corpus.dir` when
      // that is configured. When it is not, the old behaviour stands (the host-side workflow points
      // the server at the user's own tree) — but say so, once, rather than silently.
      if !SafePath.configured(corpusRoot) then
        scribe.warn("histdb.corpus.dir is not configured — `seed` will read any path the caller " +
          "names; set it (HISTDB_CORPUS_DIR) to confine server-side reads")
      SafePath.confine(corpusRoot, asked).left.map(ApiError(_)).flatMap { dir =>
        if !Files.isDirectory(dir) then Left(ApiError(s"not a directory on the server: $dir"))
        else Right(Importer.importDir(ds, dir, cfg.limits.chunkChars, req.force,
          cfg.limits.assetBytes, tolKm, corpusRoot))
      }

  /** `corpus.dir` as an `Option`: empty string = not configured. */
  private def corpusRoot: Option[String] = Option(cfg.corpus.dir).map(_.trim).filter(_.nonEmpty)

  // ── writes (plan §1.9a) ─────────────────────────────────────────────────────────────────────

  /** Create or REPLACE everything under one citekey. */
  def putSource(citekey: String, in: SourcePut): Either[ApiError, SourceWithFindings] =
    maintenance.guard("put_source"):
      Citekey.of(citekey).flatMap { _ =>
      Write.applyPut(citekey, in)
        .left.map(m => ApiError(m, Some(Write.CodeInvalid)))
        .flatMap { s => val _ = SourceRepo.put(ds, s, cfg.limits.chunkChars, tolKm); getSource(citekey) } }

  /** Merge only the parts present in the patch (read + merge + write in one transaction). */
  def patchSource(citekey: String, p: SourcePatch): Either[ApiError, SourceWithFindings] =
    maintenance.guard("patch_source"):
      Citekey.of(citekey).flatMap(_ =>
        SourceRepo.patch(ds, citekey, p, cfg.limits.chunkChars, tolKm).flatMap(_ => getSource(citekey)))

  /** The note as it would live on disk — frontmatter YAML + the `notes` body. */
  def exportSource(citekey: String): Either[ApiError, String] =
    SourceRepo.load(ds, citekey).map(Export.note).toRight(Write.notFound(citekey))

  def citekeys: List[String] = SourceRepo.citekeys(ds).toList

  def resolveEdges: Either[ApiError, EdgesResolved] =
    maintenance.guard("edges/resolve"):
      Right(EdgesResolved(SourceRepo.resolveEdges(ds)))

  /** Seed ONE note from raw text (the host-side path of `histdb seed`, for a corpus dir the SERVER
    * cannot see). Applies the same "stored row is newer than the file" guard as [[seed]] whenever the
    * client sends the file's mtime. */
  def importNote(req: NoteImport): Either[ApiError, SeedResult] = maintenance.guard("import"):
    Frontmatter.parseNote(req.text, req.citekey.getOrElse("")) match
      case None => Left(ApiError("no YAML frontmatter in the note"))
      case Some(parsed) =>
        val ck = parsed.citekey
        if ck.isEmpty then Left(ApiError("note has no citekey (none in the frontmatter, none supplied)"))
        else if Citekey.parse(ck).isLeft then Citekey.of(ck).map(_ => SeedResult(0, Nil, 0, Nil))
        else if !req.force && req.mtime.exists(m => Importer.isStale(SourceRepo.updatedAtMillis(ds), ck, m))
        then Right(SeedResult(0, Nil, 0, List(ck)))
        else
          // `body` is the converted paper text the client read from `md_path`; NUL bytes occur in some
          // OCR output and Postgres `text` rejects them.
          val withBody = req.body.map(_.filterNot(_.toInt == 0)).filter(_.nonEmpty) match
            case Some(b) => parsed.copy(ocrMarkdown = Some(b))
            case None    => parsed
          // figures of the old file pipeline (plan §1.8): every `…/figures/<name>` spelling is
          // normalised to the canonical `assets/<name>` and the OCR's picture-text dumps folded
          // away, and any base64 payloads that travelled with the note stored
          val normalised = withBody.copy(ocrMarkdown = withBody.ocrMarkdown.map(AssetLinks.normalizeBody(_, withBody.title)))
          val _ = SourceRepo.put(ds, normalised, cfg.limits.chunkChars, tolKm)
          if req.assets.isEmpty then Right(SeedResult(1, Nil, 0, Nil))
          else ingest.putAssets(ck, req.assets).map(_ => SeedResult(1, Nil, 0, Nil))

  /** `get_source` as Markdown, with image links made ABSOLUTE (plan §1.8) so an image-capable MCP
    * client can fetch the figures. The ONE renderer both MCP and the article page go through.
    *
    * Sectioning (A1/A7/A18): `section` selects what to return; `heading` filters to chunks under one
    * heading; `offset`/`chars` slice the body. */
  def sourceMarkdown(citekey: String, fullText: Boolean,
      section: Option[String] = None, heading: Option[String] = None,
      offset: Option[Int] = None, chars: Option[Int] = None): Either[ApiError, String] =
    val sec = section.flatMap(SourceSection.parse)
    val badSection = section.filter(_.nonEmpty).exists(_ => sec.isEmpty)
    if badSection then
      Left(ApiError(s"unknown section '${section.getOrElse("")}' — one of: ${SourceSection.all.mkString(", ")}"))
    else getSource(citekey).map { sw =>
      sec match
        case Some(SourceSection.Toc) =>
          val entries = SourceRepo.toc(ds, citekey)
          Markdown.toc(entries)

        case Some(SourceSection.Body) =>
          val bodyText = bodySlice(citekey, heading, offset, chars)
          AssetLinks.toAbsolute(bodyText, citekey, cfg.server.publicUrl)

        case Some(SourceSection.Summary) =>
          val s = sw.source
          val meta = List(s.authors, Markdown.periodLabel(s.periodStart, s.periodEnd))
            .filter(_.nonEmpty).mkString(" · ")
          s"# ${s.title}\n\n_${meta}_" +
            s.summary.map(x => s"\n\n${x.trim}").getOrElse("")

        case Some(SourceSection.Findings) =>
          Markdown.sourceFindingsOnly(sw.findings.toVector)

        case Some(SourceSection.Notes) =>
          sw.source.notes.map(_.trim).filter(_.nonEmpty)
            .map(n => s"## Notes\n\n$n").getOrElse("_(no notes)_")

        case Some(SourceSection.All) =>
          val ft = fullText || offset.isDefined || heading.isDefined
          val md = if ft && (offset.isDefined || chars.isDefined) then
            val bodyText = bodySlice(citekey, heading, offset, chars)
            Markdown.source(sw.source, sw.findings.toVector, false) + s"\n\n---\n\n$bodyText"
          else
            Markdown.source(sw.source, sw.findings.toVector, ft)
          AssetLinks.toAbsolute(md + mentionedIn(citekey), citekey, cfg.server.publicUrl)

        case Some(_) =>
          Markdown.source(sw.source, sw.findings.toVector, false)

        case None =>
          val ft = fullText
          val md =
            if ft && (offset.isDefined || chars.isDefined) then
              val bodyText = bodySlice(citekey, heading, offset, chars)
              Markdown.source(sw.source, sw.findings.toVector, false) + s"\n\n---\n\n$bodyText"
            else
              Markdown.source(sw.source, sw.findings.toVector, ft)
          AssetLinks.toAbsolute(md + mentionedIn(citekey), citekey, cfg.server.publicUrl)
    }

  /** "Mentioned in articles" rides along with the default/all views rather than becoming a tool of
    * its own: an LLM asking about a source wants what the corpus has WRITTEN about it in the same
    * answer, not after a second round trip. */
  private def mentionedIn(citekey: String): String =
    Markdown.sourceArticles(ArticleRepo.backlinksForSource(ds, citekey).toVector)

  /** Read the body (from chunks or raw `ocr_markdown`), optionally filtered by heading and sliced. */
  private def bodySlice(citekey: String, heading: Option[String],
      offset: Option[Int], chars: Option[Int]): String =
    val raw = heading match
      case Some(h) => SourceRepo.bodyUnderHeading(ds, citekey, h)
      case None    => SourceRepo.bodyText(ds, citekey)
    val total = raw.length
    val off   = offset.filter(_ > 0).getOrElse(0).min(total)
    val cap   = chars.filter(_ > 0).getOrElse(cfg.limits.fullTextChars)
    val slice = raw.substring(off, math.min(total, off + cap))
    Markdown.bodySlice(slice, off, total)

  // ── events (plan §1.2) ──────────────────────────────────────────────────────────────────────

  def listEvents(q: Api.EventQuery): EventList =
    EventRepo.list(ds, q.q, q.kind, q.historicity, q.placeId, q.from, q.to,
      cfg.limits.page(q.limit, cfg.limits.search), q.offset.getOrElse(0))

  def getEvent(id: String): Either[ApiError, EventDetail] =
    EventRepo.get(ds, id).toRight(ApiError(s"No event '$id'", Some(Write.CodeNotFound)))

  def putEvent(id: String, in: EventPut): Either[ApiError, EventDetail] =
    maintenance.guard("put_event"):
      EventRepo.put(ds, id, in)
      getEvent(id)

  def patchEvent(id: String, p: EventPatch): Either[ApiError, EventDetail] =
    maintenance.guard("patch_event"):
      EventRepo.patch(ds, id, p).flatMap(_ => getEvent(id))

  def deleteEvent(id: String): Either[ApiError, EventDeleted] =
    maintenance.guard("delete_event"):
      if EventRepo.delete(ds, id) then Right(EventDeleted(id, true))
      else Left(ApiError(s"No event '$id'", Some(Write.CodeNotFound)))

  // ── attestations (plan §1.3) ──────────────────────────────────────────────────────────────

  def listAttestations(q: Api.AttestationQuery): AttestationList =
    AttestationRepo.list(ds, q.citekey, q.targetType, q.targetId,
      cfg.limits.page(q.limit, cfg.limits.search), q.offset.getOrElse(0))

  def getAttestation(id: Long): Either[ApiError, Attestation] =
    AttestationRepo.get(ds, id).toRight(ApiError(s"No attestation $id", Some(Write.CodeNotFound)))

  def createAttestation(in: AttestationPut): Either[ApiError, Attestation] =
    maintenance.guard("create_attestation"):
      Right(AttestationRepo.create(ds, in))

  def patchAttestation(id: Long, p: AttestationPatch): Either[ApiError, Attestation] =
    maintenance.guard("patch_attestation"):
      AttestationRepo.patch(ds, id, p)

  def deleteAttestation(id: Long): Either[ApiError, AttestationDeleted] =
    maintenance.guard("delete_attestation"):
      if AttestationRepo.delete(ds, id) then Right(AttestationDeleted(id, true))
      else Left(ApiError(s"No attestation $id", Some(Write.CodeNotFound)))

  // ── goals (plan §1.5) ───────────────────────────────────────────────────────────────────────

  def listGoals(q: Api.GoalQuery): GoalList =
    GoalRepo.list(ds, q.status, cfg.limits.page(q.limit, cfg.limits.search), q.offset.getOrElse(0))

  def getGoal(slug: String): Either[ApiError, Goal] =
    GoalRepo.get(ds, slug).toRight(ApiError(s"No goal '$slug'", Some(Write.CodeNotFound)))

  def putGoal(slug: String, in: GoalPut): Either[ApiError, Goal] =
    maintenance.guard("put_goal"):
      GoalRepo.put(ds, slug, in)
      getGoal(slug)

  def patchGoal(slug: String, p: GoalPatch): Either[ApiError, Goal] =
    maintenance.guard("patch_goal"):
      GoalRepo.patch(ds, slug, p).flatMap(_ => getGoal(slug))

  def deleteGoal(slug: String): Either[ApiError, GoalDeleted] =
    maintenance.guard("delete_goal"):
      if GoalRepo.delete(ds, slug) then Right(GoalDeleted(slug, true))
      else Left(ApiError(s"No goal '$slug'", Some(Write.CodeNotFound)))

  // ── agent state (plan §1.5) ────────────────────────────────────────────────────────────────

  def agentStateList(): List[AgentStateEntry] = AgentStateRepo.list(ds)

  def agentStateGet(key: String): Either[ApiError, AgentStateEntry] =
    AgentStateRepo.get(ds, key).toRight(ApiError(s"No agent state key '$key'", Some(Write.CodeNotFound)))

  def agentStateSet(key: String, in: AgentStatePut): Either[ApiError, AgentStateEntry] =
    maintenance.guard("agent_state_set"):
      AgentStateRepo.set(ds, key, in.value)
      agentStateGet(key)

  def agentStateDelete(key: String): Either[ApiError, AgentStateDeleted] =
    maintenance.guard("agent_state_delete"):
      if AgentStateRepo.delete(ds, key) then Right(AgentStateDeleted(key, true))
      else Left(ApiError(s"No agent state key '$key'", Some(Write.CodeNotFound)))

  // ── the gazetteer (plan §1.6a) ──────────────────────────────────────────────────────────────

  /** `GET /api/places` — name search; `limit <= 0` means the configured `places.search-limit`. */
  def places(q: Option[String], asOf: Option[Long], limit: Option[Int]): List[PlaceHit] =
    PlaceRepo.search(ds, q, asOf, cfg.limits.page(limit, cfg.places.searchLimit)).toList

  /** `GET /api/places/{id}` — the row, its GeoJSON geometry and the hierarchy around it at `asOf`. */
  def place(id: String, asOf: Option[Long]): Either[ApiError, PlaceDetail] =
    PlaceRepo.get(ds, id, asOf).toRight(ApiError(s"No place '$id'"))

  def placeChildren(id: String, asOf: Option[Long]): Either[ApiError, List[PlaceSummary]] =
    place(id, asOf).map(_ => PlaceRepo.children(ds, id, asOf, cfg.places.childrenLimit).toList)

  /** The subtree under one place, depth-tagged; `depth <= 0` means the configured `places.tree-depth`. */
  def placeTree(id: String, asOf: Option[Long], depth: Option[Int]): Either[ApiError, List[PlaceTreeRow]] =
    place(id, asOf).map(_ =>
      PlaceRepo.hierarchy(ds, asOf, Some(id), depth.filter(_ > 0).getOrElse(cfg.places.treeDepth),
        cfg.places.treeLimit, depthCap = cfg.places.treeDepth).toList)

  /** `POST /api/places/import` — an LPF document or a plain GeoJSON FeatureCollection. Parsing is
    * pure ([[Lpf]]) and all-or-nothing: a malformed document is a 400, never a half import. */
  def importPlaces(kind: Option[String], body: String): Either[ApiError, PlaceImportResult] =
    maintenance.guard("places/import"):
      val k = kind.map(_.trim.toLowerCase).filter(_.nonEmpty).getOrElse(PlaceKind.Region)
      if !PlaceKind.all.contains(k) then
        Left(ApiError(s"unknown kind '$k' (one of: ${PlaceKind.all.mkString(", ")})"))
      else Lpf.parse(body, k).left.map(ApiError(_)).map(PlaceRepo.upsertPlaces(ds, _))

  def placeFootprints(id: String, asOf: Option[Long]): Either[ApiError, List[PlaceFootprint]] =
    place(id, asOf).map(_ => PlaceRepo.footprints(ds, id, asOf).toList)

  def placeLinks(id: String): Either[ApiError, List[PlaceLink]] =
    PlaceRepo.get(ds, id).map(_ => PlaceRepo.links(ds, id).toList).toRight(ApiError(s"No place '$id'"))

  def periods(q: Option[String], region: Option[String], from: Option[Long],
      to: Option[Long]): List[HistoricalPeriod] =
    PlaceRepo.listPeriods(ds, q, region, from, to).toList

  /** `POST /api/places/rebuild` — re-materialise `finding_places`. Returns `Either` (and its route
    * uses the write error mapping) so a rebuild arriving during a restore is a 503 like every other
    * write, rather than a rebuild against a half-restored schema. */
  def rebuildPlaces(): Either[ApiError, PlaceRebuildResult] =
    maintenance.guard("places/rebuild"):
      Right(PlaceRepo.rebuildFindingPlaces(ds, cfg.places.pointToleranceKm))

  /** One resumable slice of the FTS chunk rebuild (plan §1.4); loop on `remaining` > 0. */
  def reindex(after: Option[String], limit: Option[Int]): Either[ApiError, ReindexResult] =
    maintenance.guard("reindex"):
      Right(SourceRepo.reindexChunks(ds, cfg.limits.chunkChars, after.map(_.trim).filter(_.nonEmpty),
        limit.filter(_ > 0), cfg.limits.reindexChars))

  // ── backup / restore (plan §1.10) ───────────────────────────────────────────────────────────

  /** `pg_dump -Fc` of the server's own database into `backups.dir`. */
  def backup(): Either[ApiError, BackupResult] =
    maintenance.guard("backup")(backups.create(Instant.now()))

  def backupList: Either[ApiError, List[BackupInfo]] = backups.list

  /** The archive itself, for `GET /api/backups/{file}` — validated against the listing first. */
  def backupFile(file: String): Either[ApiError, java.io.File] = backups.resolve(file).map(_.toFile)

  /** Restore under the maintenance lock: every other write is answered 503 until it returns.
    * Refuses a database that still holds sources unless `force` (`pg_restore --clean` DROPS them),
    * and re-validates the schema with Flyway afterwards so a stale dump cannot pass silently. */
  def restore(req: RestoreRequest): Either[ApiError, RestoreResult] =
    maintenance.exclusively("restore"):
      // The OCR workers hold long transactions and write sources; a job landing mid-restore writes
      // into a schema that is being replaced. Paused for the duration, resumed whatever happens.
      ingest.pause()
      try restoreNow(req)
      finally ingest.resume()

  private def restoreNow(req: RestoreRequest): Either[ApiError, RestoreResult] =
      val before = BackupRepo.sourceCount(ds)
      for
        path <- backups.resolve(req.file)
        _    <- if req.force || before == 0 then Right(())
                else Left(ApiError(s"the database still holds $before source(s); restoring would drop them — " +
                  "re-run with force"))
        r    <- backups.restore(path)
        // Every pooled connection now holds a session cache describing types the restore dropped;
        // PostGIS in particular fails outright on the next spatial query. Throw them away here,
        // while the maintenance lock still keeps everyone else out.
        _     = histdb.db.Db.evictAll()
        v    <- Migrate.validate(ds).left.map(m => ApiError(s"restored ${req.file}, but the schema does not validate: $m"))
      yield RestoreResult(req.file, v, before, BackupRepo.sourceCount(ds), r._1, r._2)

  // ── articles: the compiled wiki (plan §5.2 stream 2.7) ──────────────────────────────────────

  /** `GET /api/articles` — the compiled articles, filtered by kind/tag and searched with `q`. */
  def listArticles(q: Api.ArticleQuery): ArticleList =
    ArticleRepo.list(ds, q.kind, q.q, q.tag,
      cfg.limits.page(q.limit, cfg.limits.search), q.offset.getOrElse(0))

  /** One article, with its backlinks and the sources it cites. */
  def getArticle(slug: String): Either[ApiError, ArticleView] =
    ArticleRepo.view(ds, norm(slug)).toRight(Articles.notFound(slug))

  /** How one article's `[[wikilinks]]` resolve (plan §5.2): two queries for the whole body — the
    * citekey aliases its targets expand to, and which of the rest are real article slugs — fed into
    * the pure [[ArticleLinks.hrefFor]].
    *
    * The article's own `sourcesCited` cannot do this job: it holds the targets AS WRITTEN, and the
    * corpus writes short aliases (`[[knapp2016]]` → `knapp2016_seven`). Everything unresolvable
    * comes back `None` and renders as plain text rather than a link to a 404.
    *
    * `targets` is taken from the WHOLE rendered document, not from the body: the backlinks and
    * "Sources cited" sections are wikilinks too, and a resolver that has never heard of them turns
    * the page's own footer into unresolved text.
    */
  def linkResolver(targets: List[String]): String => Option[String] =
    val aliases = SourceRepo.resolveAliases(ds, targets)
    val slugs   = ArticleRepo.existingSlugs(ds, targets)
    t => ArticleLinks.hrefFor(t, aliases.get, slugs.contains)

  /** Create or REPLACE one article. */
  def putArticle(slug: String, in: ArticlePut): Either[ApiError, ArticleView] =
    maintenance.guard("put_article"):
      Articles.applyPut(slug, in)
        .left.map(m => ApiError(m, Some(Write.CodeInvalid)))
        .flatMap { a => val _ = ArticleRepo.put(ds, a); getArticle(a.slug) }

  /** Merge only the parts present in the patch (read + merge + write in one transaction). */
  def patchArticle(slug: String, p: ArticlePatch): Either[ApiError, ArticleView] =
    maintenance.guard("patch_article"):
      Articles.slugOf(slug).left.map(m => ApiError(m, Some(Write.CodeInvalid)))
        .flatMap(s => ArticleRepo.patch(ds, s, p)).flatMap(_ => getArticle(slug))

  def deleteArticle(slug: String): Either[ApiError, ArticleDeleted] =
    maintenance.guard("delete_article"):
      if ArticleRepo.delete(ds, norm(slug)) then Right(ArticleDeleted(norm(slug), true))
      else Left(Articles.notFound(slug))

  /** The article as it would live on disk — `wiki/<kind>/<slug>.md`. */
  def exportArticle(slug: String): Either[ApiError, String] =
    ArticleRepo.load(ds, norm(slug)).map(ArticleNote.render).toRight(Articles.notFound(slug))

  /** The edit history of one article. 404 when the slug itself is unknown (so "no versions yet" and
    * "no such article" are different answers). */
  def articleVersions(slug: String): Either[ApiError, List[ArticleVersion]] =
    getArticle(norm(slug)).map(_ => ArticleRepo.listVersions(ds, norm(slug)))

  /** One historical version's full body. 404 when the slug or the version id is unknown. */
  def articleVersion(slug: String, versionId: Long): Either[ApiError, ArticleVersionFull] =
    getArticle(norm(slug)).flatMap(_ =>
      ArticleRepo.getVersion(ds, norm(slug), versionId)
        .toRight(ApiError(s"No version $versionId for article '$slug'", Some(Write.CodeNotFound))))

  def articleSlugs: List[String] = ArticleRepo.slugs(ds).toList

  /** Import ONE article note from raw text (the host-side path of `histdb seed`). Applies the same
    * "stored row is newer than the file" guard as the source seed whenever the client sends the
    * file's mtime, so a reseed cannot wipe an agent's compiled prose. */
  def importArticle(req: ArticleImport): Either[ApiError, SeedResult] =
    maintenance.guard("import_article")(importArticleNow(req))

  private def importArticleNow(req: ArticleImport): Either[ApiError, SeedResult] =
    val fallbackSlug = req.slug.map(_.trim).filter(_.nonEmpty).getOrElse("")
    val fallbackKind = req.kind.map(_.trim).filter(_.nonEmpty).getOrElse(Articles.Kinds.head)
    ArticleNote.parse(req.text, fallbackSlug, fallbackKind) match
      case None => Left(ApiError("no YAML frontmatter in the article note"))
      case Some(parsed) =>
        Articles.slugOf(parsed.slug).left.map(m => ApiError(m, Some(Write.CodeInvalid))).flatMap { s =>
          if !req.force && req.mtime.exists(m =>
               Importer.isStale(ArticleRepo.updatedAtMillis(ds), s, m))
          then Right(SeedResult(0, Nil, 0, List(s)))
          else Articles.kindOf(parsed.kind).left.map(m => ApiError(m, Some(Write.CodeInvalid))).map { k =>
            val _ = ArticleRepo.put(ds, parsed.copy(slug = s, kind = k))
            SeedResult(1, Nil, 0, Nil)
          }
        }

  /** Slugs are lower-case by construction; be forgiving about what a caller sends. */
  private def norm(slug: String): String = slug.trim.toLowerCase
