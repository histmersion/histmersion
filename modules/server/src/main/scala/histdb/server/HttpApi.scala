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
    Api.getSource.serverLogic[Identity](ck => h.getSource(ck)),
    Api.findings.serverLogicSuccess[Identity](q => h.findings(q)),
    Api.aggregate.serverLogic[Identity](q => h.aggregate(q)),
    Api.neighbors.serverLogicSuccess[Identity](ck => h.neighbors(ck)),
    Api.seed.serverLogic[Identity](req => h.seed(req))
  )

  // ── Markdown → HTML article pages (flexmark) ─────────────────────────────────────────────────
  private val mdOptions = new MutableDataSet()
  private val mdParser  = Parser.builder(mdOptions).build()
  private val mdRender  = HtmlRenderer.builder(mdOptions).build()

  private def mdToHtml(md: String): String = mdRender.render(mdParser.parse(Article.resolveLinks(md)))

  private def articleHtml(ck: String, full: Boolean): Option[(String, String)] =
    h.getSource(ck).toOption.map { sw =>
      val nb = h.neighbors(ck)
      val md = Markdown.source(sw.source, sw.findings.toVector, full) + "\n\n" + Markdown.neighbors(ck, nb.toVector)
      (sw.source.title, Article.toggle(ck, full) + mdToHtml(md))
    }

  private val sourcePage: ServerEndpoint[Any, Identity] =
    endpoint.get
      .in("source" / path[String]("citekey"))
      .in(query[Boolean]("full").default(false))
      .in(query[Boolean]("fragment").default(false)) // fragment = inner HTML only (for the modal)
      .out(stringBody)
      .out(header(Header.contentType(MediaType.TextHtml)))
      .serverLogicSuccess[Identity] { case (ck, full, fragment) =>
        articleHtml(ck, full) match
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

  val endpoints: List[ServerEndpoint[Any, Identity]] = rest ++ List(sourcePage, static, index)

/** Pure helpers for the article pages. */
object Article:
  private val CkAlias = """\[\[([a-z][a-z0-9_]+)\|([^\]]+)\]\]""".r
  private val Ck      = """\[\[([a-z][a-z0-9_]+)\]\]""".r

  /** `[[citekey]]` / `[[citekey|label]]` → Markdown links to `/source/citekey`. */
  def resolveLinks(md: String): String =
    Ck.replaceAllIn(CkAlias.replaceAllIn(md, m => s"[${m.group(2)}](/source/${m.group(1)})"),
      m => s"[${m.group(1)}](/source/${m.group(1)})")

  def toggle(ck: String, full: Boolean): String =
    if full then s"""<p class="toggle"><a href="/source/$ck">▲ hide full text</a></p>"""
    else s"""<p class="toggle"><a href="/source/$ck?full=true">▼ show full text (whole paper)</a></p>"""

  def page(title: String, bodyHtml: String): String =
    s"""<!doctype html><html lang="en"><head><meta charset="utf-8">
       |<meta name="viewport" content="width=device-width,initial-scale=1"><title>$title · hist-db</title>
       |<link rel="stylesheet" href="/web/style.css"></head><body class="article-page">
       |<div class="nav"><a href="/">← atlas</a></div>
       |$bodyHtml
       |</body></html>""".stripMargin

object Static:
  def read(path: String): String =
    val is = Option(classOf[HttpApi].getClassLoader.getResourceAsStream(path))
      .getOrElse(throw new IllegalStateException(s"missing resource $path"))
    try scala.io.Source.fromInputStream(is, "UTF-8").mkString finally is.close()
