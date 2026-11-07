package com.histmersion.server

import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.files.*
import sttp.shared.Identity
import sttp.model.{Header, MediaType, StatusCode}
import sttp.tapir.json.circe.*
import com.histmersion.api.{Api, OpsApi, WebPaths}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.server.ArticleHtml
import com.histmersion.api.basemap.BasemapApi
import com.histmersion.api.catalogue.TilesApi
import com.histmersion.api.instance.UiApi
import com.histmersion.api.settings.ConfigEditApi
import com.histmersion.api.views.AutopilotApi

/** REST (the `Api` contract) + HTML article pages + the static web UI, all interpreted on the same netty-sync server as
  * MCP.
  */
final class HttpApi(
    deps: Deps,
    h: ServerWiring,
    acquire: com.histmersion.domain.library.acquisition.AcquisitionControllers,
    collections: com.histmersion.domain.library.collections.CollectionsControllers
):

  private val rest: List[ServerEndpoint[Any, Identity]] = List(
    Api.version.serverLogicSuccess[Identity](_ => h.instance.version),
    Api.health.serverLogicSuccess[Identity](_ => h.instance.health),
    Api.about.serverLogicSuccess[Identity](_ => h.instance.about()),
    Api.config.serverLogicSuccess[Identity](_ => h.instance.uiConfig),
    // the toolbar's counts and "something new" marks: REST only, like `config` (UiApi says why)
    UiApi.counters.serverLogicSuccess[Identity](_ => h.instance.uiCounters),
    UiApi.markSeen.serverLogic[Identity]((board, mark) => h.instance.markSeen(board, mark)),
    // the terrain base map: REST and the CLI only (BasemapApi says why); tiles before nothing else —
    // `basemap/fetch` is a POST, so the GET `basemap/{layer}/…` never shadows it
    BasemapApi.status.serverLogicSuccess[Identity](_ => h.basemap.status),
    BasemapApi.fetch.serverLogic[Identity](q => h.basemap.fetch(q)),
    BasemapApi.remove.serverLogic[Identity](q => h.basemap.remove(q)),
    BasemapApi.tile.serverLogic[Identity]((l, z, x, y, sub, keys) => h.basemap.tile(l, z, x, y, sub, keys)),
    TilesApi.tile.serverLogic[Identity](q => h.tiles.tile(q))
  ) ++ com.histmersion.domain.library.LibraryRoutes(
    h.library.search,
    h.library.labels,
    h.library.sources,
    h.library.findings,
    h.library.citations,
    Api.sourceArticles.serverLogic[Identity](ck => h.research.articles.sourceArticles(ck)),
    OpsApi.lintMarkdown.serverLogicSuccess[Identity](in => h.formats.check(in)),
    Api.resolveId.serverLogic[Identity](id => h.resolveView(id)),
    Api.seed.serverLogic[Identity](req => h.seeding.seed(req)),
    // ── writes (plan §1.9a): the `export` route must precede `getSource`, which is a prefix of it ──
    Api.exportSource.serverLogic[Identity](ck =>
      h.mirror.note(com.histmersion.domain.library.LibraryData.SourceNotes, ck)
    ),
    Api.reindex.serverLogic[Identity]((after, limit) => h.upkeep.reindex(after, limit))
  ) ++ List[ServerEndpoint[Any, Identity]](
    // ── backup / restore (plan §1.10); the listing route precedes the per-file download ──
    Api.configReload.serverLogic[Identity](_ => Right(h.instance.configReload())),
    Api.backup.serverLogic[Identity](_ => h.upkeep.backup()),
    Api.maintenanceVacuum.serverLogic[Identity](in => h.upkeep.vacuum(in)),
    Api.backupStatus.serverLogic[Identity](_ => h.upkeep.backupStatus),
    Api.backups.serverLogic[Identity](_ => h.upkeep.backupList),
    Api.downloadBackup.serverLogic[Identity](file => h.upkeep.backupFile(file)),
    Api.restore.serverLogic[Identity](req => h.upkeep.restore(req)),
    // ── portable bundles (M0–M2) ──────────────────────────────────────────────────────────────
    Api.instance.serverLogicSuccess[Identity](_ => h.bundles.instance),
    Api.setInstance.serverLogic[Identity](in => h.bundles.setInstance(in.label)),
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
    Api.renameArticle.serverLogic[Identity]((slug, req) => h.research.articles.rename(slug, req)),
    Api.renameEvent.serverLogic[Identity]((id, req) => h.events.renamed(id, req)),
    Api.renameGoal.serverLogic[Identity]((slug, req) => h.goals.rename(slug, req)),
    Api.renamePlace.serverLogic[Identity]((id, req) => h.places.renamed(id, req))
  ) ++ com.histmersion.domain.catalogue.places.PlacesRoutes(
    h.places,
    h.gazetteer,
    Api.place.serverLogic[Identity]((id, asOf, counts) => h.placeCard(id, asOf, counts.getOrElse(false))),
    () => h.bootstrap.gazetteerSources,
    (source, apply) => h.bootstrap.loadGazetteer(source, apply)
  ) ++ com.histmersion.domain.catalogue.periods.PeriodsRoutes(h.periods) ++ List[ServerEndpoint[Any, Identity]](
    Api.init.serverLogic[Identity](fetch => h.bootstrap.init(fetch))
    // ── events (plan §1.2) and attestations (plan §1.3) ──────────────────────────────────────
  ) ++ com.histmersion.domain.catalogue.events.EventsRoutes(
    h.events,
    List(
      Api.getEvent.serverLogic[Identity](id => h.eventCard.get(id)),
      Api.putEvent.serverLogic[Identity]((id, in) => h.eventCard.put(Caller.Local, id, in)),
      Api.patchEvent.serverLogic[Identity]((id, p) => h.eventCard.patch(Caller.Local, id, p))
    )
  ) ++ com.histmersion.domain.catalogue.evidence
    .AttestationsRoutes(h.attestations) ++ List[ServerEndpoint[Any, Identity]](
    // ── goals (plan §1.5) ─────────────────────────────────────────────────────────────────────
    Api.goals.serverLogicSuccess[Identity](q => h.goals.list(q)),
    Api.getGoal.serverLogic[Identity](slug => h.goals.get(slug)),
    Api.putGoal.serverLogic[Identity]((slug, in) => h.goals.put(slug, in)),
    Api.patchGoal.serverLogic[Identity]((slug, p) => h.goals.patch(slug, p)),
    Api.deleteGoal.serverLogic[Identity](slug => h.goals.delete(slug)),
    // ── agent state (plan §1.5) ───────────────────────────────────────────────────────────────
    Api.agentStateList.serverLogicSuccess[Identity](_ => h.agentState.list()),
    Api.agentStateGet.serverLogic[Identity](key => h.agentState.get(key)),
    Api.agentStateSet.serverLogic[Identity]((key, in) => h.agentState.set(key, in)),
    Api.agentStateDelete.serverLogic[Identity](key => h.agentState.delete(key)),
    // ── ingest, jobs and assets (plan §1.8): `sources/{ck}/assets` precedes `sources/{ck}` ─────
    Api.ingest.serverLogic[Identity]((p, file) =>
      h.ingest.controllers.ingest(p, com.histmersion.domain.library.ingest.Upload.handed(com.histmersion.util.storage.HandedFile.snapshot(file)))
    ),
    Api.reocr.serverLogic[Identity]((ck, quality) => h.ingest.controllers.reocr(ck, quality)),
    Api.original.serverLogic[Identity](ck =>
      // the stored original is served from where the store keeps it (the HTTP layer streams a file)
      h.ingest.controllers.original(ck).map((blob, disposition) => (java.io.File(blob.where), disposition))
    ),
    Api.job.serverLogic[Identity](id => h.jobs.job(id)),
    Api.jobControl.serverLogic[Identity]((id, action) => h.jobControl.control(id, action)),
    ConfigEditApi.get.serverLogic[Identity](prefix => h.settings.page(prefix)),
    ConfigEditApi.write.serverLogic[Identity](w => h.settings.write(w)),
    ConfigEditApi.restart.serverLogic[Identity](_ => h.settings.restart())
  ) ++ com.histmersion.domain.personal.PersonalRoutes(h.personal) ++ List[ServerEndpoint[Any, Identity]](
    Api.activity.serverLogic[Identity]((show, since, goal, limit, before) =>
      h.activity.page(show, since, goal, limit, before)
    ),
    Api.jobs.serverLogic[Identity]((f, limit, before, upTo) =>
      h.jobList.page(f, java.time.Instant.now(deps.clock), limit, before, upTo)
    )
  ) ++ com.histmersion.domain.media.MediaRoutes(
    h.media.assets,
    h.media.links,
    h.media.imports,
    h.media.covers,
    h.media.figures
  )

  // ── the HTML source page (typed page model → HtmlRender; the Markdown in core is for MCP/CLI) ──
  private val pages = h.research.pages

  /** `/source/{citekey}`. An unknown citekey answers **404** with the same readable body it always had (item 21): a
    * page that says "no such source" under a 200 lies to every cache, crawler and `curl -f` that asks. The status is an
    * OUTPUT value rather than an error variant so the body is still produced by the same expression.
    *
    * An OLD citekey (V027) answers **301** to the source's page under its current citekey, the query string kept — so
    * bookmarks and links elsewhere land on the canonical URL.
    */
  private val sourcePage: ServerEndpoint[Any, Identity] =
    endpoint.get
      .in("source" / path[String]("citekey"))
      .in(query[Boolean]("full").default(false))
      .in(query[Boolean]("fragment").default(false)) // fragment = inner HTML only (for the modal)
      .in(query[Option[String]]("q")) // highlight these terms in the article
      .out(statusCode)
      .out(header[Option[String]](sttp.model.HeaderNames.Location))
      .out(stringBody)
      .out(header(Header.contentType(MediaType.TextHtml)))
      .serverLogicSuccess[Identity] { case (ck, full, fragment, q) =>
        val now = h.library.sources.canonical(ck)
        if now != ck then
          val to = ArticleHtml.sourceUrl(now, full, fragment, q)
          (
            sttp.model.StatusCode.MovedPermanently,
            Some(to),
            s"""<p>Moved to <a href="${ArticleHtml.esc(to)}">${ArticleHtml.esc(now)}</a>.</p>"""
          )
        else
          pages.source(ck, full, q) match
            case Some((title, inner)) =>
              (
                sttp.model.StatusCode.Ok,
                None,
                if fragment then inner else ArticleHtml.page(title, inner, PageKind.Source)
              )
            case None =>
              // ESCAPED: `ck` is whatever was in the URL, and this page is served as text/html
              val body = s"<p>No source <code>${ArticleHtml.esc(ck)}</code>.</p>"
              (
                sttp.model.StatusCode.NotFound,
                None,
                if fragment then body else ArticleHtml.page(com.histmersion.domain.Title("Not found"), body, PageKind.Source)
              )
      }

  // ── static web UI from resources: / → web/index.html, /web/* → web/* ───────────────────────

  /** An `/api/...` path that matched nothing above is a 404 with a JSON body, not the web UI. Registered after every
    * real route and before [[index]], which would otherwise answer it: a client parsing JSON must not be handed
    * `<!doctype html>` with a 200.
    */
  private val apiNotFound: ServerEndpoint[Any, Identity] =
    endpoint
      .in("api" / paths)
      .errorOut(statusCode(StatusCode.NotFound))
      .errorOut(jsonBody[Api.Error])
      .serverLogic[Identity](p => Left(Api.Error(s"no such endpoint: /api/${p.mkString("/")}")))

  /** The web UI, at its own pages' addresses only ([[WebPaths]]). It used to have no path input at all, so every
    * unmatched GET — a typo, a probe, a missing asset — came back 200 with the whole HTML page.
    */
  private val index: ServerEndpoint[Any, Identity] =
    endpoint.get
      .in(paths)
      .errorOut(statusCode(StatusCode.NotFound))
      .errorOut(stringBody)
      .out(stringBody)
      .out(header(Header.contentType(MediaType.TextHtml)))
      .serverLogic[Identity] { segments =>
        val path = segments.filter(_.nonEmpty)
        if WebPaths.isApp(path) then Right(Static.read(com.histmersion.server.Static.ReadPath("web/index.html")))
        else Left(s"not found: /${segments.mkString("/")}")
      }

  private val static: ServerEndpoint[Any, Identity] =
    staticResourcesGetServerEndpoint[Identity]("web")(classOf[HttpApi].getClassLoader, "web")

  // Articles (2.7), ops (2.8) and acquisition (2.6) live in their own files; their routes are appended here.
  private val acquireRoutes =
    com.histmersion.domain.library.acquisition.AcquisitionRoutes(acquire, collections, h.calibreGaps).endpoints ++
      com.histmersion.domain.library.collections.CollectionsRoutes(collections)

  val endpoints: List[ServerEndpoint[Any, Identity]] =
    com.histmersion.domain.catalogue.events.EventImportsRoutes(h.eventImports) ++
      com.histmersion.domain.catalogue.evidence.EventLinksRoutes(h.eventLinks) ++
      com.histmersion.domain.catalogue.places.SiteTypesRoutes(h.siteTypes) ++ rest ++
      com.histmersion.domain.research.articles.ArticleRoutes(h.research.articles, h.research.pages) ++ OpsRoutes(
        h
      ) ++ com.histmersion.domain.library.search.QueryRoutes(
        h.library.query
      ) ++ com.histmersion.domain.automation.agents.AgentsRoutes(h.agents) ++ com.histmersion.domain.automation.managers
        .ManagersRoutes(
          h.agents.managing,
          List(
            AutopilotApi.autopilot.serverLogicSuccess[Identity](_ => h.autopilot.autopilot),
            AutopilotApi.setAutopilot.serverLogic[Identity](in => h.autopilot.setAutopilot(in)),
            AutopilotApi.setMode.serverLogic[Identity](in => h.autopilot.setMode(in))
          ),
          com.histmersion.domain.library.workflows.AcquisitionWorkRoutes(
            com.histmersion.server.agents.EngineContributions.work(h.agents)
          ),
          List(AutopilotApi.drain.serverLogic[Identity](in => h.autopilot.drain(in)))
        ) ++ acquireRoutes ++
      com.histmersion.domain.media.assets.IiifRoutes(h.media.iiif) ++
      List(sourcePage, static, apiNotFound, index)
