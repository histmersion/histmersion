package histdb.server

import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.files.*
import sttp.shared.Identity
import sttp.model.{Header, MediaType, StatusCode}
import sttp.tapir.json.circe.*
import histdb.core.*
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.util.data.MutableDataSet

/** REST (the `Api` contract) + HTML article pages + the static web UI, all interpreted on the same
  * netty-sync server as MCP. */
final class HttpApi(deps: Deps, h: Handlers, acquire: histdb.server.acquire.AcquireHandlers):

  private val rest: List[ServerEndpoint[Any, Identity]] = List(
    Api.version.serverLogicSuccess[Identity](_ => h.version),
    Api.health.serverLogicSuccess[Identity](_ => h.health),
    Api.config.serverLogicSuccess[Identity](_ => h.uiConfig),
    Api.search.serverLogicSuccess[Identity]((q, limit) => h.search(q, limit)),
    Api.sources.serverLogicSuccess[Identity](f => h.sources(f)),
    Api.facets.serverLogic[Identity]((field, prefix, f) => h.facets(field, prefix, f)),
    Api.sourceArticles.serverLogic[Identity](ck => h.sourceArticles(ck)),
    Api.sourceView.serverLogic[Identity](q =>
      h.sourceMarkdown(q.citekey, q.fullText, q.section, q.heading, q.offset, q.chars)),
    Api.getSource.serverLogic[Identity](ck => h.getSource(ck)),
    Api.findings.serverLogicSuccess[Identity](q => h.findings(q)),
    Api.aggregate.serverLogic[Identity](q => h.aggregate(q)),
    Api.neighbors.serverLogicSuccess[Identity]((ck, kinds) => h.neighbors(ck, kinds)),
    Api.graph.serverLogicSuccess[Identity]((ck, depth) => h.graph(ck, depth)),
    Api.contradictions.serverLogicSuccess[Identity]((ck, since) => h.contradictions(ck, since)),
    Api.seed.serverLogic[Identity](req => h.seed(req)),
    // ── writes (plan §1.9a): the `export` route must precede `getSource`, which is a prefix of it ──
    Api.exportSource.serverLogic[Identity](ck => h.exportSource(ck)),
    Api.putSource.serverLogic[Identity]((ck, in) => h.putSource(ck, in)),
    Api.patchSource.serverLogic[Identity]((ck, p) => h.patchSource(ck, p)),
    Api.importNote.serverLogic[Identity](req => h.importNote(req)),
    Api.citekeys.serverLogicSuccess[Identity](_ => h.citekeys),
    Api.resolveEdges.serverLogic[Identity](_ => h.resolveEdges),
    Api.reindex.serverLogic[Identity]((after, limit) => h.reindex(after, limit)),
    // ── backup / restore (plan §1.10); the listing route precedes the per-file download ──
    Api.backup.serverLogic[Identity](_ => h.backup()),
    Api.backups.serverLogic[Identity](_ => h.backupList),
    Api.downloadBackup.serverLogic[Identity](file => h.backupFile(file)),
    Api.restore.serverLogic[Identity](req => h.restore(req)),
    // ── the gazetteer (plan §1.6a): the /{id} route is a prefix of /{id}/children and /{id}/tree,
    //    so the longer paths are registered first ──────────────────────────────────────────────
    Api.places.serverLogicSuccess[Identity]((q, asOf, limit) => h.places(q, asOf, limit)),
    Api.placesGeo.serverLogicSuccess[Identity]((kind, asOf, limit) => h.placesGeo(kind, asOf, limit)),
    Api.placeChildren.serverLogic[Identity]((id, asOf) => h.placeChildren(id, asOf)),
    Api.placeTree.serverLogic[Identity]((id, asOf, depth) => h.placeTree(id, asOf, depth)),
    Api.placeFootprints.serverLogic[Identity]((id, asOf) => h.placeFootprints(id, asOf)),
    Api.placeLinks.serverLogic[Identity](id => h.placeLinks(id)),
    Api.place.serverLogic[Identity]((id, asOf) => h.place(id, asOf)),
    Api.importPlaces.serverLogic[Identity]((kind, body) => h.importPlaces(kind, body)),
    Api.rebuildPlaces.serverLogic[Identity](_ => h.rebuildPlaces()),
    Api.fetchExternalPlaces.serverLogic[Identity]((source, dryRun) => h.fetchExternalPlaces(source, dryRun)),
    Api.buildHierarchy.serverLogic[Identity](_ => h.buildHierarchy()),
    Api.periods.serverLogicSuccess[Identity]((q, region, from, to) => h.periods(q, region, from, to)),
    // ── events (plan §1.2) ────────────────────────────────────────────────────────────────────
    Api.events.serverLogicSuccess[Identity](q => h.listEvents(q)),
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
    Api.job.serverLogic[Identity](id => h.ingest.job(id)),
    Api.jobs.serverLogicSuccess[Identity]((status, limit) => h.ingest.jobs(status, limit)),
    Api.assets.serverLogicSuccess[Identity](ck => h.ingest.assets(ck)),
    Api.putAssets.serverLogic[Identity]((ck, in) => h.ingest.putAssets(ck, in)),
    Api.assetDetail.serverLogic[Identity]((ck, name) => h.assetDetail(ck, name)),
    Api.patchAsset.serverLogic[Identity]((ck, name, p) => h.patchAsset(ck, name, p)),
    Api.asset.serverLogic[Identity]((ck, name) => h.ingest.asset(ck, name)),
    // media geo + asset links (1.6)
    Api.mediaGeo.serverLogicSuccess[Identity]((kind, from, to, limit) => h.mediaGeo(kind, from, to, limit)),
    Api.assetLinks.serverLogicSuccess[Identity]((ck, a, tt, ti) => h.listAssetLinks(ck, a, tt, ti)),
    Api.createAssetLink.serverLogic[Identity](in => h.createAssetLink(in)),
    Api.deleteAssetLink.serverLogic[Identity](id => h.deleteAssetLink(id))
  )

  // ── Markdown → HTML article pages (flexmark) ─────────────────────────────────────────────────
  private val mdOptions = new MutableDataSet()
  private val mdParser  = Parser.builder(mdOptions).build()
  private val mdRender  = HtmlRenderer.builder(mdOptions).build()

  /** Markdown → HTML for one source's article. Image links are rewritten from the stored, relative
    * `assets/<name>` to the path the server actually serves (`/source/{ck}/assets/<name>`) BEFORE
    * rendering, every figure gets an `alt` (`Figure N` when the OCR left it empty), and the rendered
    * `<img>`s are made lazy — a 300-page paper's figures must not all download at once (plan §1.8). */
  private def mdToHtml(md: String, citekey: String, resolve: String => Option[String],
      displayLabel: String => Option[String] = _ => None): String =
    val resolved = ArticleLinks.linkMarkdown(md, resolve, displayLabel)
    val linked = AssetLinks.numberFigures(AssetLinks.fromFigures(resolved))
    AssetLinks.lazyImages(mdRender.render(mdParser.parse(AssetLinks.toWeb(linked, citekey))))

  /** `?q=` (plan §1.4) highlights the query terms in the rendered article and prepends a
    * "N matches · next ▼" control, so a search result lands the reader on the passage. */
  private def articleHtml(ck: String, full: Boolean, q: Option[String]): Option[(String, String)] =
    h.getSource(ck).toOption.map { sw =>
      val nb   = h.neighbors(ck)
      // the wiki around this source: which articles cite it, and which outgoing edge targets are
      // themselves articles — the two hops that turn a source page from a dead end into a graph node
      val into = h.sourceArticles(ck).getOrElse(Nil)
      val md   = Markdown.source(sw.source, sw.findings.toVector, full) +
        Markdown.sourceArticles(into.toVector) +
        "\n\n" + Markdown.neighbors(ck, nb.toVector, h.articleSlugs.toSet.contains)
      val resolve = h.linkResolver(ArticleLinks.targets(md))
      val term = q.map(_.trim).filter(_.nonEmpty)
      val (html, matches) = term match
        case Some(t) => Highlight(mdToHtml(md, ck, resolve), t)
        case None    => (mdToHtml(md, ck, resolve), 0)
      (sw.source.title, Article.matchNav(matches) + Article.toggle(ck, full, term) + html)
    }

  /** `/source/{citekey}`. An unknown citekey answers **404** with the same readable body it always
    * had (item 21): a page that says "no such source" under a 200 lies to every cache, crawler and
    * `curl -f` that asks. The status is an OUTPUT value rather than an error variant so the body is
    * still produced by the same expression. */
  private val sourcePage: ServerEndpoint[Any, Identity] =
    endpoint.get
      .in("source" / path[String]("citekey"))
      .in(query[Boolean]("full").default(false))
      .in(query[Boolean]("fragment").default(false)) // fragment = inner HTML only (for the modal)
      .in(query[Option[String]]("q"))                // highlight these terms in the article
      .out(statusCode)
      .out(stringBody)
      .out(header(Header.contentType(MediaType.TextHtml)))
      .serverLogicSuccess[Identity] { case (ck, full, fragment, q) =>
        articleHtml(ck, full, q) match
          case Some((title, inner)) =>
            (sttp.model.StatusCode.Ok, if fragment then inner else Article.page(title, inner))
          case None =>
            // ESCAPED: `ck` is whatever was in the URL, and this page is served as text/html
            val body = s"<p>No source <code>${Article.esc(ck)}</code>.</p>"
            (sttp.model.StatusCode.NotFound, if fragment then body else Article.page("Not found", body))
      }

  // ── static web UI from resources: / → web/index.html, /web/* → web/* ───────────────────────

  /** An `/api/...` path that matched nothing above is a 404 with a JSON body, not the atlas page.
    * Registered after every real route and before [[index]], which would otherwise answer it: a
    * client parsing JSON must not be handed `<!doctype html>` with a 200. */
  private val apiNotFound: ServerEndpoint[Any, Identity] =
    endpoint
      .in("api" / paths)
      .errorOut(statusCode(StatusCode.NotFound)).errorOut(jsonBody[ApiError])
      .serverLogic[Identity](p => Left(ApiError(s"no such endpoint: /api/${p.mkString("/")}")))

  /** The atlas page, at the ROOT path only. It used to have no path input at all, so every
    * unmatched GET — a typo, a probe, a missing asset — came back 200 with the whole HTML page. */
  private val index: ServerEndpoint[Any, Identity] =
    endpoint.get
      .in(paths)
      .errorOut(statusCode(StatusCode.NotFound)).errorOut(stringBody)
      .out(stringBody)
      .out(header(Header.contentType(MediaType.TextHtml)))
      .serverLogic[Identity] { segments =>
        val path = segments.filter(_.nonEmpty)
        if path.isEmpty || path == List("acquire") then Right(Static.read("web/index.html"))
        else Left(s"not found: /${segments.mkString("/")}")
      }

  private val static: ServerEndpoint[Any, Identity] =
    staticResourcesGetServerEndpoint[Identity]("web")(classOf[HttpApi].getClassLoader, "web")

  // ── IIIF Image API Level 1 (plan §4.3) ───────────────────────────────────────────────────────

  private val baseUrl = deps.cfg.server.publicUrl.stripSuffix("/")

  private val iiifInfoRoute: ServerEndpoint[Any, Identity] =
    Api.iiifInfo.serverLogic[Identity] { (ck, name) =>
      IiifHandler.info(deps.ds, ck, name, baseUrl)
    }

  private val iiifImageRoute: ServerEndpoint[Any, Identity] =
    Api.iiifImage.serverLogic[Identity] { (ck, name, region, size, rotation, filename) =>
      IiifHandler.image(deps.ds, ck, name, region, size, rotation, filename)
    }

  // Articles (2.7), ops (2.8) and acquisition (2.6) live in their own files; their routes are appended here.
  private val acquireRoutes = histdb.server.acquire.AcquireRoutes(acquire).endpoints

  val endpoints: List[ServerEndpoint[Any, Identity]] =
    rest ++ ArticlesApi(h).endpoints ++ OpsRoutes(h.ops) ++ acquireRoutes ++
      List(iiifInfoRoute, iiifImageRoute, sourcePage, static, apiNotFound, index)


/** Pure helpers for the article pages. */
object Article:
  private val CkAlias = """\[\[([a-z][a-z0-9_]+)\|([^\]]+)\]\]""".r
  private val Ck      = """\[\[([a-z][a-z0-9_]+)\]\]""".r

  /** `[[citekey]]` / `[[citekey|label]]` → Markdown links to `/source/citekey`. */
  def resolveLinks(md: String): String =
    Ck.replaceAllIn(CkAlias.replaceAllIn(md, m => s"[${m.group(2)}](/source/${m.group(1)})"),
      m => s"[${m.group(1)}](/source/${m.group(1)})")

  /** HTML-escape a value destined for an attribute or text node. */
  def esc(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

  private def link(ck: String, full: Boolean, q: Option[String]): String =
    val params = List(
      if full then Some("full=true") else None,
      q.map(t => "q=" + java.net.URLEncoder.encode(t, "UTF-8"))
    ).flatten
    esc(s"/source/$ck" + (if params.isEmpty then "" else params.mkString("?", "&", "")))

  def toggle(ck: String, full: Boolean, q: Option[String] = None): String =
    if full then s"""<p class="toggle"><a href="${link(ck, false, q)}">▲ hide full text</a></p>"""
    else s"""<p class="toggle"><a href="${link(ck, true, q)}">▼ show full text (whole paper)</a></p>"""

  /** The match counter + jump control for `?q=`; empty when nothing matched (or no query).
    * `web/article.js` wires the link and the `n` shortcut to the `<mark id="hl…">` elements — and
    * the control NAMES that shortcut, because a keyboard shortcut nobody is told about is a
    * shortcut nobody uses (item 14). */
  def matchNav(matches: Int): String =
    if matches <= 0 then ""
    else
      val plural = if matches == 1 then "match" else "matches"
      s"""<p class="toggle match-nav" data-matches="$matches">$matches $plural · """ +
        """<a href="#hl1" class="next-match">next ▼</a> <span class="kbd-hint">(n)</span></p>"""

  /** `title` is DATA — a source's or an article's stored title, which an agent wrote — so it is
    * escaped here rather than at every call site. `bodyHtml` is already-rendered HTML and is not. */
  def page(title: String, bodyHtml: String): String =
    s"""<!doctype html><html lang="en"><head><meta charset="utf-8">
       |<meta name="viewport" content="width=device-width,initial-scale=1"><title>${esc(title)} · hist-db</title>
       |<link rel="stylesheet" href="/web/style.css"></head><body class="article-page">
       |<div class="nav"><a href="/">← hist-db</a></div>
       |$bodyHtml
       |<script src="/web/article.js"></script>
       |<script src="/web/citations.js"></script>
       |</body></html>""".stripMargin

object Static:
  def read(path: String): String =
    val is = Option(classOf[HttpApi].getClassLoader.getResourceAsStream(path))
      .getOrElse(throw new IllegalStateException(s"missing resource $path"))
    try scala.io.Source.fromInputStream(is, "UTF-8").mkString finally is.close()
