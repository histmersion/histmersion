package com.histmersion.domain

import com.histmersion.values.MediaRole

import io.circe.Json

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale

/** Licences of pictures from outside providers (#1446), as data.
  *
  * A licence is stored as its SPDX id (`CC-BY-SA-3.0`, `CC0-1.0`) or `public-domain`, and judged by its FAMILY
  * (`cc-by-sa`): a provider's allowlist names families, so a new version of an allowed licence needs no config change.
  * Two families are never stored whatever a config says — an unreadable licence and all-rights-reserved. Pure
  * throughout.
  */
object MediaLicence:

  /** `id` is what is stored and shown; `family` is what an allowlist names. */
  final case class Licence(id: String, family: String)

  val Cc0 = "cc0"
  val PublicDomain = "public-domain"
  val CcBy = "cc-by"
  val CcBySa = "cc-by-sa"
  val CcByNc = "cc-by-nc"
  val CcByNcSa = "cc-by-nc-sa"
  val CcByNd = "cc-by-nd"
  val CcByNcNd = "cc-by-nc-nd"
  val Gfdl = "gfdl"
  val AllRights = "all-rights-reserved"
  val Unknown = "unknown"

  /** Every family an allowlist may name. */
  val Families: List[String] = List(Cc0, PublicDomain, CcBy, CcBySa, CcByNc, CcByNcSa, CcByNd, CcByNcNd, Gfdl)

  /** Never stored, whatever the allowlist says. */
  val Never: Set[String] = Set(AllRights, Unknown)

  /** Pure: the families a config string allows (`"cc0,public-domain,cc-by,cc-by-sa"`). Unknown names and the
    * never-allowed ones are dropped.
    */
  def allowList(csv: String): Set[String] =
    Option(csv).toList
      .flatMap(_.split(",").toList)
      .map(_.trim.toLowerCase(Locale.ROOT))
      .filter(Families.contains)
      .toSet -- Never

  def allowed(l: Licence, allow: Set[String]): Boolean = !Never(l.family) && allow(l.family)

  /** Does the family require the author to be named? Every CC BY variant and the GFDL do. */
  def needsAttribution(family: String): Boolean = family.startsWith(CcBy) || family == Gfdl

  /** The ones that need no credit at all (the credit line is still shown, as a courtesy and a source). */
  def isPublicDomain(family: String): Boolean = family == Cc0 || family == PublicDomain

  private val CcRe = """^cc-by(-nc)?(-sa|-nd)?(?:-([0-9](?:\.[0-9])?))?(?:-([a-z]{2,3}))?$""".r

  /** Pure: one licence string — a machine id (Commons' `License`: `cc-by-sa-3.0`, `cc0`, `pd`) or a short name (`CC
    * BY-SA 3.0 de`, `Public domain`) — as a [[Licence]]; [[Unknown]] when it is neither.
    */
  def classify(raw: String): Licence =
    val s = raw.trim.toLowerCase(Locale.ROOT).replaceAll("[\\s_]+", "-")
    s match
      case ""                                                        => Licence("", Unknown)
      case c if c.startsWith("cc0") || c == "cc-zero" || c == "cc-0" => Licence("CC0-1.0", Cc0)
      case p
          if p == "pd" || p.startsWith("pd-") || p.startsWith("public-domain") || p == "publicdomain" ||
            p.startsWith("pdm") =>
        Licence("public-domain", PublicDomain)
      case CcRe(nc, variant, version, port) =>
        val family = CcBy + Option(nc).getOrElse("") + Option(variant).getOrElse("")
        val v = Option(version).map(x => if x.contains(".") then x else s"$x.0")
        val id = (family.toUpperCase(Locale.ROOT) :: v.toList ++ Option(port).map(_.toUpperCase(Locale.ROOT)).toList)
          .mkString("-")
        Licence(id, family)
      case g if g.startsWith("gfdl") => Licence("GFDL", Gfdl)
      case a if a == "arr" || a == "all-rights-reserved" || a == "copyrighted" || a.startsWith("©") =>
        Licence(AllRights, AllRights)
      case other => Licence(other, Unknown)

  private val CcUrl =
    """^https?://(?:www\.)?creativecommons\.org/licenses/([a-z-]+)/([0-9](?:\.[0-9])?)(?:/([a-z]{2,3}))?/?(?:deed\.[a-z-]+)?$""".r
  private val PdUrl =
    """^https?://(?:www\.)?creativecommons\.org/publicdomain/(zero|mark)/[0-9.]+/?(?:deed\.[a-z-]+)?$""".r

  /** Pure: a licence given as its Creative Commons URL (Open Context's `dc-terms:license`):
    * `https://creativecommons.org/licenses/by-sa/4.0` → `CC-BY-SA-4.0` (family `cc-by-sa`), `…/publicdomain/zero/1.0/`
    * → CC0, `…/publicdomain/mark/1.0/` → public domain. Anything else — another site's terms page, a malformed URL — is
    * [[Unknown]]: never guessed.
    */
  def ofUrl(url: String): Licence =
    val u = Option(url).getOrElse("").trim.toLowerCase(Locale.ROOT)
    u match
      case PdUrl("zero")              => Licence("CC0-1.0", Cc0)
      case PdUrl(_)                   => Licence("public-domain", PublicDomain)
      case CcUrl(code, version, port) => classify(s"cc-$code-$version" + Option(port).fold("")("-" + _))
      case _                          => Licence(u, Unknown)

  /** Pure: the first of the candidates that reads as a known licence (the machine id first, then the short name);
    * [[Unknown]] with the first non-empty one when none does.
    */
  def parse(machine: Option[String], short: Option[String]): Licence =
    val cands = (machine.toList ++ short.toList).map(_.trim).filter(_.nonEmpty)
    cands.iterator
      .map(classify)
      .find(_.family != Unknown)
      .getOrElse(Licence(cands.headOption.getOrElse(""), Unknown))

/** Plain text out of the bits of HTML a provider's metadata carries (Commons' `Artist` is a link to a user page). Pure.
  */
object MediaText:
  private val Tag = "<[^>]*>".r
  private val Dec = "&#([0-9]{1,7});".r
  private val Hex = "&#[xX]([0-9a-fA-F]{1,6});".r
  private val Named = Map(
    "&amp;" -> "&",
    "&lt;" -> "<",
    "&gt;" -> ">",
    "&quot;" -> "\"",
    "&#39;" -> "'",
    "&apos;" -> "'",
    "&nbsp;" -> " "
  )

  def plain(html: String, max: Int = 300): String =
    val noTags = Tag.replaceAllIn(Option(html).getOrElse(""), " ")
    val named = Named.foldLeft(noTags) { case (acc, (k, v)) => acc.replace(k, v) }
    val dec = Dec.replaceAllIn(
      named,
      m =>
        java.util.regex.Matcher
          .quoteReplacement(scala.util.Try(new String(Character.toChars(m.group(1).toInt))).getOrElse(""))
    )
    val hex = Hex.replaceAllIn(
      dec,
      m =>
        java.util.regex.Matcher.quoteReplacement(
          scala.util.Try(new String(Character.toChars(Integer.parseInt(m.group(1), 16)))).getOrElse("")
        )
    )
    val text = hex.replaceAll("\\s+", " ").trim
    if text.length > max then text.take(max - 1).trim + "…" else text

  /** Pure: a URL without its `utm_*` tracking parameters (Commons adds them to every file URL). */
  def stripTracking(url: String): String =
    url.split("\\?", 2) match
      case Array(base, query) =>
        val kept = query.split("&").filterNot(p => p.toLowerCase(Locale.ROOT).startsWith("utm_") || p.isEmpty)
        if kept.isEmpty then base else base + "?" + kept.mkString("&")
      case _ => url

  /** Pure: a link-safe file name for a provider's file title (`File:Lions Gate.jpg` → `Lions_Gate.jpg`). */
  def safeName(title: String): String =
    val n = title.stripPrefix("File:").trim.replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("_+", "_")
    if n.isEmpty then "file" else n.take(180)

/** Wikidata, read through `wbgetentities` (props=claims). Pure parsing and URL building; the fetch is the server's.
  */
object Wikidata:

  /** A picture statement of an item: the property (`P18`), the Commons file name it gives, whether it is the item's
    * preferred one, and its order among the item's statements of that property.
    */
  final case class Statement(property: String, file: String, preferred: Boolean, order: Int)

  /** An item's picture statements and its Commons category (P373). */
  final case class Item(id: String, statements: List[Statement], commonsCategory: Option[String])

  val CommonsCategory = "P373"

  def isQid(s: String): Boolean = s.matches("Q[1-9][0-9]*")

  /** Pure: the item a gazetteer place id names (`wikidata_q132564` → `Q132564`). */
  def qidOfPlaceId(id: String): Option[String] =
    val s = id.trim.toLowerCase(Locale.ROOT)
    Option.when(s.startsWith("wikidata_q"))("Q" + s.drop("wikidata_q".length)).filter(isQid)

  private def enc(s: String): String = URLEncoder.encode(s, UTF_8)

  /** Pure: the `wbgetentities` URL for up to the API's batch of ids. `maxlag` 0 = not sent. */
  def entitiesUrl(api: String, ids: List[String], maxlag: Int): String =
    val lag = if maxlag > 0 then s"&maxlag=$maxlag" else ""
    s"$api?action=wbgetentities&format=json&formatversion=2&props=claims&ids=${enc(ids.mkString("|"))}$lag"

  /** Pure: the items of a `wbgetentities` answer, keyed by the id ASKED for (a redirected item answers under the id
    * that was asked, with the statements of its target). `props` are the picture properties to keep; P373 is always
    * read. An API error is `Left("code: info")`.
    */
  def items(json: Json, props: Set[String]): Either[String, Map[String, Item]] =
    val c = json.hcursor
    c.downField("error").focus match
      case Some(err) =>
        val code = err.hcursor.get[String]("code").getOrElse("error")
        Left(s"$code: ${err.hcursor.get[String]("info").getOrElse("")}".trim)
      case None =>
        val ents = c.downField("entities").focus.flatMap(_.asObject).map(_.toList).getOrElse(Nil)
        Right(ents.flatMap { (key, ent) =>
          val e = ent.hcursor
          if e.downField("missing").focus.isDefined then None
          else
            val claims = e.downField("claims").focus.flatMap(_.asObject)
            def statementsOf(p: String): List[Statement] =
              claims.flatMap(_(p)).flatMap(_.asArray).toList.flatten.zipWithIndex.flatMap { (st, i) =>
                val s = st.hcursor
                val rank = s.get[String]("rank").getOrElse("normal")
                val snak = s.downField("mainsnak")
                val v = snak.downField("datavalue").get[String]("value").toOption.map(_.trim).filter(_.nonEmpty)
                if rank == "deprecated" || snak.get[String]("snaktype").toOption.exists(_ != "value") then None
                else v.map(Statement(p, _, rank == "preferred", i))
              }
            val pictures = props.toList.sorted.flatMap(statementsOf)
            val category = best(statementsOf(CommonsCategory), 1).headOption.map(_.file)
            Some(key -> Item(key, pictures, category))
        }.toMap)

  /** Pure: the statements of one property to use, best first — the preferred ones when there are any (Wikidata's own
    * rule for "the" value), else the normal ones — at most `per`.
    */
  def best(statements: List[Statement], per: Int): List[Statement] =
    val preferred = statements.filter(_.preferred)
    (if preferred.nonEmpty then preferred else statements).sortBy(_.order).take(per.max(0))

/** Wikimedia Commons, read through the action API's `prop=imageinfo` with `extmetadata`. Pure parsing, URL building and
  * the licence/credit decision; the fetch is the server's.
  */
object Commons:

  /** One file as `imageinfo` describes it. `rendition*` is the provider's resized copy at the width asked (`thumburl`;
    * the original itself when that is smaller). `meta` is `extmetadata`, by name.
    */
  final case class FileInfo(
      title: String,
      pageUrl: String,
      originalUrl: String,
      renditionUrl: Option[String],
      renditionWidth: Option[Int],
      renditionHeight: Option[Int],
      mime: String,
      width: Option[Int],
      height: Option[Int],
      size: Option[Long],
      meta: Map[String, String]
  )

  /** What the licence requires beside the picture, read from `extmetadata` and never assumed. */
  final case class Rights(
      licence: MediaLicence.Licence,
      licenceName: String,
      licenceUrl: Option[String],
      author: Option[String],
      credit: String
  )

  /** The `extmetadata` fields read — and asked for, so the answer stays small. */
  val MetaFields: List[String] = List(
    "LicenseShortName",
    "License",
    "LicenseUrl",
    "Artist",
    "Credit",
    "Attribution",
    "AttributionRequired",
    "Copyrighted",
    "NonFree",
    "UsageTerms",
    "Restrictions",
    "ObjectName"
  )

  /** Pure: the canonical title of a file name as Wikidata gives it (`Lions-Gate-Mycenae.jpg` →
    * `File:Lions-Gate-Mycenae.jpg`): underscores are spaces, and the first letter is upper case.
    */
  def fileTitle(name: String): String =
    val n = name.trim.stripPrefix("File:").stripPrefix("file:").replace('_', ' ').replaceAll("\\s+", " ").trim
    if n.isEmpty then "" else "File:" + n.head.toUpper + n.tail

  private def enc(s: String): String = URLEncoder.encode(s, UTF_8)

  /** Pure: the `imageinfo` URL for a batch of titles, with the rendition `width`; `meta` adds the licence fields (the
    * thumbnail call needs only the URL). `maxlag` 0 = not sent.
    */
  def imageInfoUrl(api: String, titles: List[String], width: Int, maxlag: Int, meta: Boolean): String =
    val props = if meta then "url|size|mime|extmetadata" else "url|size|mime"
    val ext = if meta then s"&iiextmetadatafilter=${enc(MetaFields.mkString("|"))}&iiextmetadatalanguage=en" else ""
    val lag = if maxlag > 0 then s"&maxlag=$maxlag" else ""
    s"$api?action=query&format=json&formatversion=2&prop=imageinfo&iiprop=${enc(props)}&iiurlwidth=$width" +
      s"$ext&titles=${enc(titles.mkString("|"))}$lag"

  /** Pure: the files of an `imageinfo` answer, keyed by the title ASKED for (the API's `normalized` list maps an asked
    * title to its canonical one). A file Commons does not have is `Left("missing on Commons")`; an API error is `Left`
    * for the whole answer.
    */
  def imageInfo(json: Json): Either[String, Map[String, Either[String, FileInfo]]] =
    val c = json.hcursor
    c.downField("error").focus match
      case Some(err) =>
        Left(
          s"${err.hcursor.get[String]("code").getOrElse("error")}: ${err.hcursor.get[String]("info").getOrElse("")}".trim
        )
      case None =>
        val q = c.downField("query")
        val normalized = q
          .downField("normalized")
          .focus
          .flatMap(_.asArray)
          .toList
          .flatten
          .flatMap { n =>
            for f <- n.hcursor.get[String]("from").toOption; t <- n.hcursor.get[String]("to").toOption yield t -> f
          }
          .groupMap(_._1)(_._2)
        val pages = q.downField("pages").focus.flatMap(_.asArray).toList.flatten.flatMap { p =>
          val h = p.hcursor
          val title = h.get[String]("title").getOrElse("")
          val info = h.downField("imageinfo").downArray
          val entry: Either[String, FileInfo] =
            if h.downField("missing").focus.isDefined || h.downField("invalid").focus.isDefined || info.focus.isEmpty
            then Left("missing on Commons")
            else
              val i = info
              val meta = i
                .downField("extmetadata")
                .focus
                .flatMap(_.asObject)
                .toList
                .flatMap(_.toList)
                .flatMap { (k, v) =>
                  v.hcursor
                    .downField("value")
                    .focus
                    .flatMap(j =>
                      j.asString
                        .orElse(j.asNumber.map(_.toString))
                        .orElse(j.asBoolean.map(_.toString))
                    )
                    .map(k -> _)
                }
                .toMap
              (for
                url <- i.get[String]("url").toOption
                page <- i.get[String]("descriptionurl").toOption
              yield FileInfo(
                title,
                page,
                MediaText.stripTracking(url),
                i.get[String]("thumburl").toOption.map(MediaText.stripTracking),
                i.get[Int]("thumbwidth").toOption,
                i.get[Int]("thumbheight").toOption,
                i.get[String]("mime").getOrElse("application/octet-stream"),
                i.get[Int]("width").toOption,
                i.get[Int]("height").toOption,
                i.get[Long]("size").toOption,
                meta
              ))
                .toRight("no file URL in the answer")
          (title :: normalized.getOrElse(title, Nil)).map(_ -> entry)
        }
        Right(pages.toMap)

  /** Pure: what the licence of a file requires, from its `extmetadata` — or why it cannot be used: a file Commons marks
    * non-free, a licence that is unreadable or all-rights-reserved, a licence that requires attribution with no author
    * to name. The credit line is Commons' own suggested form, "Author, Licence, via Wikimedia Commons" (the author is
    * left out for a public-domain file that names none). Whether the provider's allowlist takes the licence is
    * [[MediaLicence.allowed]].
    */
  def rights(meta: Map[String, String]): Either[String, Rights] =
    def field(k: String) = meta.get(k).map(MediaText.plain(_)).filter(_.nonEmpty)
    val licence = MediaLicence.parse(meta.get("License").map(_.trim), field("LicenseShortName"))
    val required = meta.get("AttributionRequired").exists(_.trim.equalsIgnoreCase("true")) ||
      MediaLicence.needsAttribution(licence.family)
    val author = field("Attribution").orElse(field("Artist"))
    if meta.get("NonFree").exists(_.trim.equalsIgnoreCase("true")) then Left("marked non-free on Commons")
    else if licence.family == MediaLicence.Unknown then
      Left(if licence.id.isEmpty then "no licence stated" else s"licence not recognised (${licence.id})")
    else if licence.family == MediaLicence.AllRights then Left("all rights reserved")
    else if required && author.isEmpty then Left("no author to credit")
    else
      val name = field("LicenseShortName").getOrElse(licence.id)
      val credit = (author.toList :+ name :+ "via Wikimedia Commons").mkString(", ")
      Right(
        Rights(
          licence,
          name,
          meta
            .get("LicenseUrl")
            .map(_.trim)
            .filter(_.nonEmpty)
            .map(u => if u.startsWith("//") then "https:" + u else u),
          author,
          credit
        )
      )

/** `P18:image,P242:locator-map` (config) → `List(P18 -> image, P242 -> locator-map)`. An entry that is not
  * `P<digits>:<known role>` is dropped, as is a second entry for the same property.
  */
object MediaRoles:
  def parse(s: String): List[(String, String)] =
    Option(s).toList
      .flatMap(_.split(",").toList)
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap { e =>
        e.split(":", 2).map(_.trim) match
          case Array(p, r) if p.matches("P[1-9][0-9]*") && MediaRole.placeRoles.contains(r) => Some(p -> r)
          case _                                                                            => None
      }
      .distinctBy(_._1)
