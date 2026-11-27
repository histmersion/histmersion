package com.histmersion
package plugins.media
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.Problem

import com.histmersion.plugin.{GazetteerPlace, Plugin}

import java.util.Locale
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** Pictures a Pleiades place record links (https://pleiades.stoa.org/places/{id}/json, no key). A place's `references`
  * (`accessURI`, `alternateURI`) sometimes name a Wikimedia Commons file — those are the pictures; their licence is
  * read from Commons, never from Pleiades (whose own CC BY covers its data, not what it links). A link to Flickr, to a
  * Commons category, or to a picture on any other site is counted with the reason it is not taken. Pure.
  */
@tolerated(LintRule.Helper, "pending G63 (no free-standing helper objects)")
@atBoundary(BoundaryKind.Json, "picks the Commons file links out of a Pleiades place record")
object PleiadesLinks:
  type Text = Text.T
  object Text extends Newtype[String, Pure]

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def placeUrl(api: CollectionSettings.ApiUrl, id: GazetteerPlace.Id): Plugin.Http.Url =
    Plugin.Http.Url(s"${api.value.stripSuffix("/")}/places/${id.value.enc}/json")

  val OnFlickr: PleiadesLink.Untaken = PleiadesLink.Untaken.OnFlickr
  val Category: PleiadesLink.Untaken = PleiadesLink.Untaken.Category

  private val Pictures = """(?i).*\.(jpe?g|png|gif|tiff?|webp)$""".r
  private val PictureHosts = List("geograph.org.uk/photo", "panoramio.com/photo", "/photos/", "/image/", "iiif")

  /** Pure: a path segment decoded (`%27` → `'`; a `+` stays a `+`). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def decode(s: Text): Commons.File.Title =
    Commons.File.Title(
      scala.util
        .Try(java.net.URLDecoder.decode(s.value.replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8))
        .getOrElse(s.value)
    )

  /** Pure: what a referenced URL is — a Commons file's title, a picture that is not taken (and why), or `None`: not a
    * picture at all (a book, a gazetteer entry). Wikipedia's `File:` pages are asked of Commons too — a file only a
    * Wikipedia holds (a non-free one) is then "missing on Commons".
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def classify(url: Plugin.Http.Url): Option[Either[PleiadesLink.Untaken, Commons.File.Title]] =
    val u = url.value.trim.stripPrefix("=")
    val lower = u.toLowerCase(Locale.ROOT)
    val host = scala.util
      .Try(java.net.URI.create(u.replace(" ", "%20")).getHost)
      .toOption
      .flatMap(Option(_))
      .map(_.toLowerCase(Locale.ROOT).stripPrefix("www."))
      .getOrElse("")
    val filePart = """(?:^|/|#/media/)(?:File|Datei|Fichier|Archivo|Image):([^?#]+)""".r
    if host.endsWith("wikimedia.org") || host.endsWith("wikipedia.org") then
      if host == "upload.wikimedia.org" then
        val segs = u.split("[?#]").head.split("/").toList.dropWhile(_ != "commons").drop(1)
        val name = if segs.headOption.contains("thumb") then segs.lift(3) else segs.lift(2)
        Some(
          name
            .map(n => Commons.fileTitle(decode(Text(n))))
            .filter(_.value.nonEmpty)
            .toRight(PleiadesLink.Untaken.UnreadableCommonsUrl)
        )
      else
        filePart.findFirstMatchIn(u) match
          case Some(m) =>
            Some(
              Right[PleiadesLink.Untaken, Commons.File.Title](Commons.fileTitle(decode(Text(m.group(1)))))
                .filterOrElse(_.value.nonEmpty, PleiadesLink.Untaken.UnreadableCommonsUrl)
            )
          case None if lower.contains("category:") => Some(Left(Category))
          case None                                => None
    else if host.endsWith("flickr.com") || host.endsWith("staticflickr.com") then Some(Left(OnFlickr))
    else if Pictures.matches(u.split("[?#]").head) || PictureHosts.exists(lower.contains) then
      Some(
        Left(PleiadesLink.Untaken.Elsewhere(PleiadesLink.Host(host)))
      )
    else None

  /** Pure: the picture links of a place record, in its order, one per URL. */
  def links(json: Json): Either[Problem, List[PleiadesLink]] =
    val c = json.cursor
    c.downField("references").focus.flatMap(_.asArray) match
      case None =>
        Left(
          c.str("message")
            .fold(MediaPlugin.missing("references"))(MediaPlugin.said("media-pleiades", Problem.Plugin.Claim.Failed, _))
        )
      case Some(refs) =>
        Right(
          refs.toList
            .flatMap { r =>
              val h = r.cursor
              val cito = h.str("type").orElse(h.str("citationTypeURI").map(_.split("/").last)).getOrElse("reference")
              (h.str("accessURI").toList ++ h.str("alternateURI").toList)
                .flatMap(u =>
                  classify(Plugin.Http.Url(u))
                    .map(PleiadesLink(PleiadesLink.Url(u.trim), PleiadesLink.Citation(cito), _))
                )
            }
            .distinctBy(_.url)
        )
