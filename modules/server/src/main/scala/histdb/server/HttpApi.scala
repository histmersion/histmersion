package histdb.server

import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.files.*
import sttp.shared.Identity
import sttp.model.{Header, MediaType}
import histdb.core.*
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.util.data.MutableDataSet

/** REST (the `Api` contract) + HTML article pages + the static web UI, all interpreted on the same
  * netty-sync server as MCP. */
final class HttpApi(deps: Deps, h: Handlers):

  private val rest: List[ServerEndpoint[Any, Identity]] = List(
    Api.version.serverLogicSuccess[Identity](_ => h.version),
    Api.health.serverLogicSuccess[Identity](_ => h.health),
    Api.config.serverLogicSuccess[Identity](_ => h.uiConfig),
    Api.search.serverLogicSuccess[Identity]((q, limit) => h.search(q, limit)),
    Api.sources.serverLogicSuccess[Identity](f => h.sources(f)),
    Api.facets.serverLogic[Identity]((field, prefix, f) => h.facets(field, prefix, f)),
    Api.getSource.serverLogic[Identity](ck => h.getSource(ck)),
    Api.findings.serverLogicSuccess[Identity](q => h.findings(q)),
    Api.aggregate.serverLogic[Identity](q => h.aggregate(q)),
    Api.neighbors.serverLogicSuccess[Identity](ck => h.neighbors(ck)),
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
    Api.restore.serverLogic[Identity](req => h.restore(req))
  )

  // ── Markdown → HTML article pages (flexmark) ─────────────────────────────────────────────────
  private val mdOptions = new MutableDataSet()
  private val mdParser  = Parser.builder(mdOptions).build()
  private val mdRender  = HtmlRenderer.builder(mdOptions).build()

  private def mdToHtml(md: String): String = mdRender.render(mdParser.parse(Article.resolveLinks(md)))

  /** `?q=` (plan §1.4) highlights the query terms in the rendered article and prepends a
    * "N matches · next ▼" control, so a search result lands the reader on the passage. */
  private def articleHtml(ck: String, full: Boolean, q: Option[String]): Option[(String, String)] =
    h.getSource(ck).toOption.map { sw =>
      val nb   = h.neighbors(ck)
      val md   = Markdown.source(sw.source, sw.findings.toVector, full) + "\n\n" + Markdown.neighbors(ck, nb.toVector)
      val term = q.map(_.trim).filter(_.nonEmpty)
      val (html, matches) = term match
        case Some(t) => Highlight(mdToHtml(md), t)
        case None    => (mdToHtml(md), 0)
      (sw.source.title, Article.matchNav(matches) + Article.toggle(ck, full, term) + html)
    }

  private val sourcePage: ServerEndpoint[Any, Identity] =
    endpoint.get
      .in("source" / path[String]("citekey"))
      .in(query[Boolean]("full").default(false))
      .in(query[Boolean]("fragment").default(false)) // fragment = inner HTML only (for the modal)
      .in(query[Option[String]]("q"))                // highlight these terms in the article
      .out(stringBody)
      .out(header(Header.contentType(MediaType.TextHtml)))
      .serverLogicSuccess[Identity] { case (ck, full, fragment, q) =>
        articleHtml(ck, full, q) match
          case Some((title, inner)) => if fragment then inner else Article.page(title, inner)
          case None =>
            val body = s"<p>No source <code>$ck</code>.</p>"
            if fragment then body else Article.page("Not found", body)
      }

  // ── static web UI from resources: / → web/index.html, /web/* → web/* ───────────────────────
  private val index: ServerEndpoint[Any, Identity] =
    endpoint.get
      .out(stringBody)
      .out(header(Header.contentType(MediaType.TextHtml)))
      .serverLogicSuccess[Identity](_ => Static.read("web/index.html"))

  private val static: ServerEndpoint[Any, Identity] =
    staticResourcesGetServerEndpoint[Identity]("web")(classOf[HttpApi].getClassLoader, "web")

  // Articles (stream 2.7) and ops (stream 2.8) live in their own files; their routes are appended here.
  val endpoints: List[ServerEndpoint[Any, Identity]] =
    rest ++ ArticlesApi(h).endpoints ++ OpsRoutes(h.ops) ++ List(sourcePage, static, index)

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
    * `web/article.js` wires the link and the `n` shortcut to the `<mark id="hl…">` elements. */
  def matchNav(matches: Int): String =
    if matches <= 0 then ""
    else
      val plural = if matches == 1 then "match" else "matches"
      s"""<p class="toggle match-nav" data-matches="$matches">$matches $plural · """ +
        """<a href="#hl1" class="next-match">next ▼</a></p>"""

  def page(title: String, bodyHtml: String): String =
    s"""<!doctype html><html lang="en"><head><meta charset="utf-8">
       |<meta name="viewport" content="width=device-width,initial-scale=1"><title>$title · hist-db</title>
       |<link rel="stylesheet" href="/web/style.css"></head><body class="article-page">
       |<div class="nav"><a href="/">← atlas</a></div>
       |$bodyHtml
       |<script src="/web/article.js"></script>
       |</body></html>""".stripMargin

object Static:
  def read(path: String): String =
    val is = Option(classOf[HttpApi].getClassLoader.getResourceAsStream(path))
      .getOrElse(throw new IllegalStateException(s"missing resource $path"))
    try scala.io.Source.fromInputStream(is, "UTF-8").mkString finally is.close()
