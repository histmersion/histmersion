package com.histmersion.plugins.media

import io.circe.Json

import java.util.Locale

/** Pictures a Pleiades place record links (https://pleiades.stoa.org/places/{id}/json, no key). A place's `references`
  * (`accessURI`, `alternateURI`) sometimes name a Wikimedia Commons file — those are the pictures; their licence is
  * read from Commons, never from Pleiades (whose own CC BY covers its data, not what it links). A link to Flickr, to a
  * Commons category, or to a picture on any other site is counted with the reason it is not taken. Pure.
  */
object PleiadesLinks:
  type Decoded = Decoded.T
  object Decoded extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def placeUrl(api: com.histmersion.plugin.Plugin.Http.GetUrl, id: com.histmersion.plugin.GazetteerPlace.Id): com.histmersion.plugin.Plugin.Http.GetUrl = s"${api.stripSuffix("/")}/places/${id.enc}/json"

  val OnFlickr = "on Flickr (its API needs a key)"
  val Category = "a Commons category, not a file"

  private val Pictures = """(?i).*\.(jpe?g|png|gif|tiff?|webp)$""".r
  private val PictureHosts = List("geograph.org.uk/photo", "panoramio.com/photo", "/photos/", "/image/", "iiif")

  /** Pure: a path segment decoded (`%27` → `'`; a `+` stays a `+`). */
  def decode(s: com.histmersion.plugins.media.PleiadesLinks.Text): com.histmersion.plugins.media.PleiadesLinks.Decoded =
    scala.util
      .Try(java.net.URLDecoder.decode(s.replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8))
      .getOrElse(s)

  /** Pure: what a referenced URL is — a Commons file's title, a picture that is not taken (and why), or `None`: not a
    * picture at all (a book, a gazetteer entry). Wikipedia's `File:` pages are asked of Commons too — a file only a
    * Wikipedia holds (a non-free one) is then "missing on Commons".
    */
  def classify(url: com.histmersion.plugin.Plugin.Http.GetUrl): Option[Either[com.histmersion.plugins.media.PleiadesLinks.Problem, com.histmersion.plugins.media.PleiadesLinks.Kind]] =
    val u = url.trim.stripPrefix("=")
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
        Some(name.map(n => Commons.fileTitle(decode(n))).filter(_.nonEmpty).toRight("an unreadable Commons file URL"))
      else
        filePart.findFirstMatchIn(u) match
          case Some(m) =>
            Some(
              Right[String, String](Commons.fileTitle(decode(m.group(1))))
                .filterOrElse(_.nonEmpty, "an unreadable Commons file URL")
            )
          case None if lower.contains("category:") => Some(Left(Category))
          case None                                => None
    else if host.endsWith("flickr.com") || host.endsWith("staticflickr.com") then Some(Left(OnFlickr))
    else if Pictures.matches(u.split("[?#]").head) || PictureHosts.exists(lower.contains) then
      Some(Left(s"a picture on ${if host.isEmpty then "another site" else host} (no documented keyless licence API)"))
    else None

  /** Pure: the picture links of a place record, in its order, one per URL. */
  def links(json: Json): Either[com.histmersion.plugins.media.PleiadesLinks.Problem, List[PleiadesLink]] =
    val c = json.hcursor
    c.downField("references").focus.flatMap(_.asArray) match
      case None =>
        Left(c.str("message").getOrElse("no references in the answer (not a Pleiades place record)"))
      case Some(refs) =>
        Right(
          refs.toList
            .flatMap { r =>
              val h = r.hcursor
              val cito = h.str("type").orElse(h.str("citationTypeURI").map(_.split("/").last)).getOrElse("reference")
              (h.str("accessURI").toList ++ h.str("alternateURI").toList)
                .flatMap(u => classify(u).map(PleiadesLink(u.trim, cito, _)))
            }
            .distinctBy(_.url)
        )
