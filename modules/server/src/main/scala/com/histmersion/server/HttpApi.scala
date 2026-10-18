package com.histmersion.server

import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.files.*
import sttp.shared.Identity
import sttp.model.{Header, MediaType, StatusCode}
import sttp.tapir.json.circe.*
import com.histmersion.core.*

/** REST (the `Api` contract) + HTML article pages + the static web UI, all interpreted on the same
  * netty-sync server as MCP. */
final class HttpApi(deps: Deps, h: Handlers, acquire: com.histmersion.server.acquire.AcquireHandlers):

  private val rest: List[ServerEndpoint[Any, Identity]] = List(
    Api.version.serverLogicSuccess[Identity](_ => h.version),
    Api.health.serverLogicSuccess[Identity](_ => h.health),
    Api.about.serverLogicSuccess[Identity](_ => h.about()),
    Api.config.serverLogicSuccess[Identity](_ => h.uiConfig),
    // the toolbar's counts and "something new" marks: REST only, like `config` (UiApi says why)
    UiApi.counters.serverLogicSuccess[Identity](_ => h.uiCounters),
    UiApi.markSeen.serverLogic[Identity]((board, mark) => h.markSeen(board, mark)),
    Api.search.serverLogicSuccess[Identity]((q, limit) => h.search(q, limit)),
    Api.sources.serverLogicSuccess[Identity](f => h.sources(f)),
    Api.facets.serverLogic[Identity]((field, prefix, f) => h.facets(field, prefix, f)),
    Api.sourceArticles.serverLogic[Identity](ck => h.sourceArticles(ck)),
    // `sources/{ck}/lint(/fix)` are longer paths than `sources/{ck}`
    Api.lintSource.serverLogic[Identity](ck => h.lintSource(ck)),
    Api.fixSourceFormat.serverLogic[Identity](ck => h.fixSource(ck)),
    OpsApi.lintMarkdown.serverLogicSuccess[Identity](in => h.lintText(in)),
    Api.sourceView.serverLogic[Identity](q =>
      h.sourceMarkdown(q.citekey, q.fullText, q.section, q.heading, q.offset, q.chars)),
    Api.passages.serverLogic[Identity](q => h.passages(q)),
    Api.resolveId.serverLogic[Identity](h.resolveId),
    Api.getSource.serverLogic[Identity]((ck, body) => h.getSource(ck, body)),
    Api.findingsPage.serverLogicSuccess[Identity](q => h.findingsPage(q)),
    Api.findings.serverLogicSuccess[Identity](q => h.findings(q)),
    Api.aggregate.serverLogic[Identity](q => h.aggregate(q)),
    Api.neighbors.serverLogicSuccess[Identity]((ck, kinds) => h.neighbors(ck, kinds)),
    Api.graph.serverLogicSuccess[Identity]((ck, depth) => h.graph(ck, depth)),
    Api.contradictions.serverLogicSuccess[Identity]((ck, since) => h.contradictions(ck, since)),
    Api.seed.serverLogic[Identity](req => h.seed(req)),
    // ── writes (plan §1.9a): the `export` route must precede `getSource`, which is a prefix of it ──
    Api.exportSource.serverLogic[Identity](ck => h.exportSource(ck)),
    Api.putSource.serverLogic[Identity]((ck, in, strict) => h.putSource(ck, in, strict)),
    Api.patchSource.serverLogic[Identity]((ck, p, strict) => h.patchSource(ck, p, strict)),
    Api.renameSource.serverLogic[Identity]((ck, req) => h.renameSource(ck, req)),
    Api.deleteSource.serverLogic[Identity]((ck, req) => h.deleteSource(ck, req)),
    Api.sourceAliases.serverLogicSuccess[Identity](_ => h.sourceAliases),
    Api.importNote.serverLogic[Identity](req => h.importNote(req)),
    Api.citekeys.serverLogicSuccess[Identity](_ => h.citekeys),
    Api.resolveEdges.serverLogic[Identity](_ => h.resolveEdges),
    Api.reindex.serverLogic[Identity]((after, limit) => h.reindex(after, limit)),
    // ── backup / restore (plan §1.10); the listing route precedes the per-file download ──
    Api.configReload.serverLogic[Identity](_ => Right(h.configReload())),
    Api.backup.serverLogic[Identity](_ => h.backup()),
    Api.maintenanceVacuum.serverLogic[Identity](in => h.vacuum(in)),
    Api.backupStatus.serverLogic[Identity](_ => h.backupStatus),
    Api.backups.serverLogic[Identity](_ => h.backupList),
    Api.downloadBackup.serverLogic[Identity](file => h.backupFile(file)),
    Api.restore.serverLogic[Identity](req => h.restore(req)),
    // ── portable bundles (M0–M2) ──────────────────────────────────────────────────────────────
    Api.instance.serverLogicSuccess[Identity](_ => h.bundles.instance),
    Api.setInstance.serverLogic[Identity](in => h.bundles.setInstance(in)),
    Api.exportBundle.serverLogic[Identity](since => h.bundles.exportBundle(since)),
    Api.verifyBundle.serverLogic[Identity](f => h.bundles.verify(f)),
    Api.importBundle.serverLogic[Identity]((dryRun, f) => h.bundles.importBundle(dryRun, f)),
    // ── the merge plan (M3) ──
    Api.uploadBundle.serverLogic[Identity](f => h.bundles.upload(f)),
    Api.mergePlan.serverLogic[Identity](req => h.bundles.plan(req)),
    Api.mergeImport.serverLogic[Identity](req => h.bundles.importUploaded(req)),
    Api.mergeApply.serverLogic[Identity](req => h.bundles.applyUploaded(req)),
    Api.mergeApplyStatus.serverLogic[Identity](job => h.bundles.applyStatus(job)),
    Api.mergeSession.serverLogic[Identity](b => h.bundles.session(b)),
    Api.putMergeSession.serverLogic[Identity]((b, r) => h.bundles.putSession(b, r)),
    Api.parseResolutions.serverLogic[Identity](text => h.bundles.parseResolutions(text)),
    // ── merge list | show | ledger (M7): `merges/ledger` before `merges/{id}` ──
    Api.merges.serverLogicSuccess[Identity](limit => h.bundles.merges(limit)),
    Api.mergeLedger.serverLogic[Identity]((uid, merge, limit) => h.bundles.mergeLedger(uid, merge, limit)),
    Api.mergeDetail.serverLogic[Identity](id => h.bundles.mergeDetail(id)),
    // ── label renames (V042, V053) ──
    Api.renameArticle.serverLogic[Identity]((slug, req) => h.renameArticle(slug, req)),
    Api.renameEvent.serverLogic[Identity]((id, req) => h.renameEvent(id, req)),
    Api.renameGoal.serverLogic[Identity]((slug, req) => h.renameGoal(slug, req)),
    Api.renamePlace.serverLogic[Identity]((id, req) => h.renamePlace(id, req)),
    // ── the gazetteer (plan §1.6a): the /{id} route is a prefix of /{id}/children and /{id}/tree,
    //    so the longer paths are registered first ──────────────────────────────────────────────
    Api.places.serverLogicSuccess[Identity]((q, asOf, limit) => h.places(q, asOf, limit)),
    Api.placesGeo.serverLogic[Identity](q => h.placesGeo(q)),
    Api.placeNames.serverLogicSuccess[Identity](place => h.manualPlaceNames(place)),
    Api.placeOverrides.serverLogicSuccess[Identity](id => h.placeOverrides(id)),
    Api.placeSources.serverLogic[Identity](_ => Right(h.bootstrap.gazetteerSources)),
    Api.placeChildren.serverLogic[Identity]((id, asOf) => h.placeChildren(id, asOf)),
    Api.placeTree.serverLogic[Identity]((id, asOf, depth) => h.placeTree(id, asOf, depth)),
    Api.placeFootprints.serverLogic[Identity]((id, asOf) => h.placeFootprints(id, asOf)),
    Api.placeLinks.serverLogic[Identity](id => h.placeLinks(id)),
    Api.place.serverLogic[Identity]((id, asOf, counts) => h.place(id, asOf, counts.getOrElse(false))),
    Api.addPlaceName.serverLogic[Identity]((id, in) => h.addPlaceName(id, in)),
    Api.removePlaceName.serverLogic[Identity]((id, in) => h.removePlaceName(id, in)),
    Api.setPlaceOverride.serverLogic[Identity]((id, in) => h.setPlaceOverride(id, in)),
    Api.mergePlace.serverLogic[Identity]((id, in) => h.mergePlace(id, in)),
    Api.removePlace.serverLogic[Identity]((id, in) => h.removePlace(id, in)),
    Api.addPlace.serverLogic[Identity](in => h.addPlace(in)),
    Api.importPlaces.serverLogic[Identity]((kind, dataset, body) => h.importPlaces(kind, dataset, body)),
    Api.rebuildPlaces.serverLogic[Identity](_ => h.rebuildPlaces()),
    Api.fetchExternalPlaces.serverLogic[Identity]((source, dryRun) => h.fetchExternalPlaces(source, dryRun)),
    Api.loadPlaces.serverLogic[Identity]((source, apply) => h.bootstrap.loadGazetteer(source, apply)),
    Api.buildHierarchy.serverLogic[Identity](_ => h.buildHierarchy()),
    Api.periods.serverLogicSuccess[Identity]((q, region, from, to) => h.periods(q, region, from, to)),
    Api.period.serverLogic[Identity](id => h.bootstrap.period(id)),
    Api.seedPeriods.serverLogic[Identity](replace => h.bootstrap.seedPeriods(replace)),
    Api.importPeriods.serverLogic[Identity]((cols, dataset, body) => h.bootstrap.importPeriods(cols, dataset, body)),
    Api.fetchPeriods.serverLogic[Identity]((source, cols) => h.bootstrap.fetchPeriods(source, cols)),
    Api.init.serverLogic[Identity](fetch => h.bootstrap.init(fetch)),
    // ── events (plan §1.2) ────────────────────────────────────────────────────────────────────
    Api.events.serverLogicSuccess[Identity](q => h.listEvents(q)),
    Api.eventsGeo.serverLogicSuccess[Identity](q => h.eventsGeo(q)),
    Api.getEvent.serverLogic[Identity](id => h.getEvent(id)),
    Api.putEvent.serverLogic[Identity]((id, in) => h.putEvent(id, in)),
    Api.patchEvent.serverLogic[Identity]((id, p) => h.patchEvent(id, p)),
    Api.deleteEvent.serverLogic[Identity](id => h.deleteEvent(id)),
    // ── attestations (plan §1.3) ──────────────────────────────────────────────────────────────
    Api.attestations.serverLogicSuccess[Identity](q => h.listAttestations(q)),
    Api.getAttestation.serverLogic[Identity](id => h.getAttestation(id)),
    Api.createAttestation.serverLogic[Identity](in => h.createAttestation(in)),
    Api.patchAttestation.serverLogic[Identity]((id, p) => h.patchAttestation(id, p)),
    Api.deleteAttestation.serverLogic[Identity](id => h.deleteAttestation(id)),
    // ── goals (plan §1.5) ─────────────────────────────────────────────────────────────────────
    Api.goals.serverLogicSuccess[Identity](q => h.listGoals(q)),
    Api.getGoal.serverLogic[Identity](slug => h.getGoal(slug)),
    Api.putGoal.serverLogic[Identity]((slug, in) => h.putGoal(slug, in)),
    Api.patchGoal.serverLogic[Identity]((slug, p) => h.patchGoal(slug, p)),
    Api.deleteGoal.serverLogic[Identity](slug => h.deleteGoal(slug)),
    // ── agent state (plan §1.5) ───────────────────────────────────────────────────────────────
    Api.agentStateList.serverLogicSuccess[Identity](_ => h.agentStateList()),
    Api.agentStateGet.serverLogic[Identity](key => h.agentStateGet(key)),
    Api.agentStateSet.serverLogic[Identity]((key, in) => h.agentStateSet(key, in)),
    Api.agentStateDelete.serverLogic[Identity](key => h.agentStateDelete(key)),
    // ── ingest, jobs and assets (plan §1.8): `sources/{ck}/assets` precedes `sources/{ck}` ─────
    Api.ingest.serverLogic[Identity]((p, file) => h.ingest.ingest(p, file)),
    Api.reocr.serverLogic[Identity]((ck, quality) => h.ingest.reocr(ck, quality)),
    Api.original.serverLogic[Identity](ck => h.ingest.original(ck)),
    Api.job.serverLogic[Identity](id => h.ingest.job(id)),
    Api.jobs.serverLogic[Identity]((status, limit, before, upTo) => h.ingest.jobsPage(status, limit, before, upTo)),
    Api.assets.serverLogicSuccess[Identity](ck => h.ingest.assets(ck)),
    Api.putAssets.serverLogic[Identity]((ck, in) => h.ingest.putAssets(ck, in)),
    Api.assetDetail.serverLogic[Identity]((ck, name) => h.assetDetail(ck, name)),
    Api.patchAsset.serverLogic[Identity]((ck, name, p) => h.patchAsset(ck, name, p)),
    Api.asset.serverLogic[Identity]((ck, name) => h.ingest.asset(ck, name)),
    // media geo + asset links (1.6)
    Api.mediaGeo.serverLogicSuccess[Identity](q => h.mediaGeo(q)),
    Api.assetLinks.serverLogicSuccess[Identity]((ck, a, tt, ti) => h.listAssetLinks(ck, a, tt, ti)),
    Api.createAssetLink.serverLogic[Identity](in => h.createAssetLink(in)),
    Api.deleteAssetLink.serverLogic[Identity](id => h.deleteAssetLink(id)),
    // media from outside providers (#1446): the fixed paths before `media/{uid}`
    Api.mediaFacets.serverLogicSuccess[Identity](_ => h.media.facets),
    Api.mediaProviders.serverLogicSuccess[Identity](_ => h.media.providers),
    Api.mediaImport.serverLogic[Identity](req => h.media.importMedia(req)),
    Api.mediaImportJob.serverLogic[Identity](req => h.media.importJob(req)),
    Api.mediaCovers.serverLogic[Identity](req => h.media.covers(req)),
    Api.mediaCoversJob.serverLogic[Identity](req => h.media.coversJob(req)),
    Api.putSourceCover.serverLogic[Identity]((p, bytes) => h.media.putCover(p, bytes)),
    Api.fillFigureLicences.serverLogic[Identity](apply => h.media.fillLicences(apply)),
    Api.mediaList.serverLogicSuccess[Identity](q => h.media.list(q)),
    Api.mediaShow.serverLogic[Identity](uid => h.media.show(uid)),
    Api.mediaThumb.serverLogic[Identity](uid => h.media.thumb(uid)),
    Api.mediaFile.serverLogic[Identity](uid => h.media.file(uid))
  )

  // ── the HTML source page (typed page model → HtmlRender; the Markdown in core is for MCP/CLI) ──
  private val pages = PageBuilder(h)

  /** `/source/{citekey}`. An unknown citekey answers **404** with the same readable body it always
    * had (item 21): a page that says "no such source" under a 200 lies to every cache, crawler and
    * `curl -f` that asks. The status is an OUTPUT value rather than an error variant so the body is
    * still produced by the same expression.
    *
    * An OLD citekey (V027) answers **301** to the source's page under its current citekey, the
    * query string kept — so bookmarks and links elsewhere land on the canonical URL. */
  private val sourcePage: ServerEndpoint[Any, Identity] =
    endpoint.get
      .in("source" / path[String]("citekey"))
      .in(query[Boolean]("full").default(false))
      .in(query[Boolean]("fragment").default(false)) // fragment = inner HTML only (for the modal)
      .in(query[Option[String]]("q"))                // highlight these terms in the article
      .out(statusCode)
      .out(header[Option[String]](sttp.model.HeaderNames.Location))
      .out(stringBody)
      .out(header(Header.contentType(MediaType.TextHtml)))
      .serverLogicSuccess[Identity] { case (ck, full, fragment, q) =>
        val now = h.canonical(ck)
        if now != ck then
          val to = Article.sourceUrl(now, full, fragment, q)
          (sttp.model.StatusCode.MovedPermanently, Some(to),
            s"""<p>Moved to <a href="${Article.esc(to)}">${Article.esc(now)}</a>.</p>""")
        else pages.source(ck, full, q) match
          case Some((title, inner)) =>
            (sttp.model.StatusCode.Ok, None,
              if fragment then inner else Article.page(title, inner, PageKind.Source))
          case None =>
            // ESCAPED: `ck` is whatever was in the URL, and this page is served as text/html
            val body = s"<p>No source <code>${Article.esc(ck)}</code>.</p>"
            (sttp.model.StatusCode.NotFound, None,
              if fragment then body else Article.page("Not found", body, PageKind.Source))
      }

  // ── static web UI from resources: / → web/index.html, /web/* → web/* ───────────────────────

  /** An `/api/...` path that matched nothing above is a 404 with a JSON body, not the web UI.
    * Registered after every real route and before [[index]], which would otherwise answer it: a
    * client parsing JSON must not be handed `<!doctype html>` with a 200. */
  private val apiNotFound: ServerEndpoint[Any, Identity] =
    endpoint
      .in("api" / paths)
      .errorOut(statusCode(StatusCode.NotFound)).errorOut(jsonBody[ApiError])
      .serverLogic[Identity](p => Left(ApiError(s"no such endpoint: /api/${p.mkString("/")}")))

  /** The web UI, at the ROOT path only. It used to have no path input at all, so every
    * unmatched GET — a typo, a probe, a missing asset — came back 200 with the whole HTML page. */
  private val index: ServerEndpoint[Any, Identity] =
    endpoint.get
      .in(paths)
      .errorOut(statusCode(StatusCode.NotFound)).errorOut(stringBody)
      .out(stringBody)
      .out(header(Header.contentType(MediaType.TextHtml)))
      .serverLogic[Identity] { segments =>
        val path = segments.filter(_.nonEmpty)
        if path.isEmpty || HttpApi.appPaths.contains(path) then Right(Static.read("web/index.html"))
        else Left(s"not found: /${segments.mkString("/")}")
      }

  private val static: ServerEndpoint[Any, Identity] =
    staticResourcesGetServerEndpoint[Identity]("web")(classOf[HttpApi].getClassLoader, "web")

  // ── IIIF Image API Level 1 (plan §4.3) ───────────────────────────────────────────────────────

  private def baseUrl = deps.cfg.server.publicUrl.stripSuffix("/")

  private val iiifInfoRoute: ServerEndpoint[Any, Identity] =
    Api.iiifInfo.serverLogic[Identity] { (ck, name) =>
      IiifHandler.info(deps.ds, ck, name, baseUrl)
    }

  private val iiifImageRoute: ServerEndpoint[Any, Identity] =
    Api.iiifImage.serverLogic[Identity] { (ck, name, region, size, rotation, filename) =>
      IiifHandler.image(deps.ds, ck, name, region, size, rotation, filename)
    }

  // Articles (2.7), ops (2.8) and acquisition (2.6) live in their own files; their routes are appended here.
  private val acquireRoutes = com.histmersion.server.acquire.AcquireRoutes(acquire).endpoints

  val endpoints: List[ServerEndpoint[Any, Identity]] =
    com.histmersion.server.events.EventCatalogRoutes(h) ++ rest ++ ArticlesApi(h).endpoints ++ OpsRoutes(h.ops) ++ com.histmersion.server.agents.AgentsRoutes(h.agents) ++ com.histmersion.server.agents.ManagersRoutes(h.agents.managing) ++ acquireRoutes ++
      List(iiifInfoRoute, iiifImageRoute, sourcePage, static, apiNotFound, index)


/** Which standalone page a document is — the web bundle's page entry point reads it from
  * `<body data-page>` to wire that page's behaviour (link previews, match navigation). */
enum PageKind(val wire: String):
  case Source  extends PageKind("source")
  case Article extends PageKind("article")

/** Pure helpers for the article pages. */
object Article:
  /** HTML-escape a value destined for an attribute or text node. */
  def esc(s: String): String = HtmlRender.esc(s)

  private def link(ck: String, full: Boolean, q: Option[String]): String =
    esc(sourceUrl(ck, full, fragment = false, q))

  /** `/source/{ck}` with the page's own query parameters (unescaped — for a header or `esc`). */
  def sourceUrl(ck: String, full: Boolean, fragment: Boolean, q: Option[String]): String =
    val params = List(
      if full then Some("full=true") else None,
      if fragment then Some("fragment=true") else None,
      q.map(t => "q=" + java.net.URLEncoder.encode(t, "UTF-8"))
    ).flatten
    s"/source/${java.net.URLEncoder.encode(ck, "UTF-8")}" + (if params.isEmpty then "" else params.mkString("?", "&", ""))

  def toggle(ck: String, full: Boolean, q: Option[String] = None): String =
    if full then s"""<p class="toggle"><a href="${link(ck, false, q)}">▲ hide full text</a></p>"""
    else s"""<p class="toggle"><a href="${link(ck, true, q)}">▼ show full text (whole paper)</a></p>"""

  /** The match counter + jump control for `?q=`; empty when nothing matched (or no query).
    * the web bundle's page entry point wires the link and the `n` shortcut to the `<mark id="hl…">` elements — and
    * the control NAMES that shortcut, because a keyboard shortcut nobody is told about is a
    * shortcut nobody uses (item 14). */
  def matchNav(matches: Int): String =
    if matches <= 0 then ""
    else
      s"""<p class="toggle match-nav" data-matches="$matches">${Plural.count(matches, "match", "matches")} · """ +
        """<a href="#hl1" class="next-match">next ▼</a> <span class="kbd-hint">(n)</span></p>"""

  /** `title` is DATA — a source's or an article's stored title, which an agent wrote — so it is
    * escaped here rather than at every call site. `bodyHtml` is already-rendered HTML and is not. */
  def page(title: String, bodyHtml: String, kind: PageKind): String =
    s"""<!doctype html><html lang="en"><head><meta charset="utf-8">
       |<meta name="viewport" content="width=device-width,initial-scale=1"><title>${esc(title)} · Histmersion</title><link rel="icon" href="data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 32 32%22%3E%3Ccircle cx=%2216%22 cy=%2216%22 r=%2214%22 fill=%22%231f2433%22/%3E%3Cpath d=%22M6 20c5-7 15-7 20 0%22 stroke=%22%236aa3ff%22 stroke-width=%223%22 fill=%22none%22/%3E%3Ccircle cx=%2216%22 cy=%2212%22 r=%223%22 fill=%22%23f2c14e%22/%3E%3C/svg%3E">
       |<link rel="stylesheet" href="/web/style.css"></head><body class="article-page" data-page="${kind.wire}">
       |<div class="nav"><a href="/">← Histmersion</a></div>
       |<main id="page-content">$bodyHtml</main>
       |<script src="$BundlePath"></script>
       |</body></html>""".stripMargin

  /** The Scala.js bundle — the same file the web UI's `index.html` loads, which carries every page's
    * entry point (`web.Main` dispatches on the path). */
  val BundlePath = "/web/app.bundle.js?v=2"

object HttpApi:
  /** Page paths that serve the app itself: each bookkeeping list's address (`/acquire`, `/tasks`,
    * `/log`) and the merge review screen's (`/merge`), which the page turns into that window. */
  val appPaths: Set[List[String]] = Queue.Kind.values.map(k => List(k.label)).toSet + List("merge")

object Static:
  def read(path: String): String =
    val is = Option(classOf[HttpApi].getClassLoader.getResourceAsStream(path))
      .getOrElse(throw new IllegalStateException(s"missing resource $path"))
    try scala.io.Source.fromInputStream(is, "UTF-8").mkString finally is.close()
