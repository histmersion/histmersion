package com.histmersion.upstreams.parse
import io.circe.{ACursor, Json}
import com.histmersion.domain.library.acquisition.{FollowupRow, RefRow, UpdateNotice, WorkRef}
import com.histmersion.upstreams.archiveorg.{ArchiveFile, ArchiveItem}
import com.histmersion.upstreams.europepmc.EuropePmcHit
import com.histmersion.upstreams.identifiers.Identifiers
import com.histmersion.upstreams.pmc.{PmcObject, PmcRecord}
import com.histmersion.upstreams.unpaywall.OaAccess

/** PURE parsers for the JSON the acquisition upstreams return (OpenAlex, Crossref, Unpaywall, CORE, Semantic Scholar,
  * Europe PMC, PMC Article Datasets, archive.org, Wikipedia).
  *
  * Keeping them apart from the HTTP clients is what makes the toolset testable from fixtures: a test feeds real
  * captured JSON straight into these functions with no network involved. Everything is total: a missing or unexpected
  * field yields `None`/`Nil`, never an exception, because upstreams do change shape.
  */
object AcquireParse:

  // ── small cursor helpers ────────────────────────────────────────────────────────────────────
  type StrResult = StrResult.T
  object StrResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IntResult = IntResult.T
  object IntResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BoolResult = BoolResult.T
  object BoolResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StringsResult = StringsResult.T
  object StringsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TailResult = TailResult.T
  object TailResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TailUri = TailUri.T
  object TailUri extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenalexAbstractResult = OpenalexAbstractResult.T
  object OpenalexAbstractResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenalexOaLocationsResult = OpenalexOaLocationsResult.T
  object OpenalexOaLocationsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenalexPdfUrlsResult = OpenalexPdfUrlsResult.T
  object OpenalexPdfUrlsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArxivIdFromUrlResult = ArxivIdFromUrlResult.T
  object ArxivIdFromUrlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArxivIdFromUrlUrl = ArxivIdFromUrlUrl.T
  object ArxivIdFromUrlUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InlineMarkupResult = InlineMarkupResult.T
  object InlineMarkupResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InlineMarkupS = InlineMarkupS.T
  object InlineMarkupS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GluedPageNumberResult = GluedPageNumberResult.T
  object GluedPageNumberResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GluedPageNumberS = GluedPageNumberS.T
  object GluedPageNumberS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StripTagsResult = StripTagsResult.T
  object StripTagsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StripTagsS = StripTagsS.T
  object StripTagsS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnpaywallResultPart1 = UnpaywallResultPart1.T
  object UnpaywallResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnpaywallResultPart2 = UnpaywallResultPart2.T
  object UnpaywallResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EuropePmcFree = EuropePmcFree.T
  object EuropePmcFree extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EuropepmcHitDoi = EuropepmcHitDoi.T
  object EuropepmcHitDoi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PmcLatestVersionResult = PmcLatestVersionResult.T
  object PmcLatestVersionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PmcLatestVersionListXml = PmcLatestVersionListXml.T
  object PmcLatestVersionListXml extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PmcLatestVersionPmcid = PmcLatestVersionPmcid.T
  object PmcLatestVersionPmcid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PmcObjectS3 = PmcObjectS3.T
  object PmcObjectS3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoreDownloadUrlsResult = CoreDownloadUrlsResult.T
  object CoreDownloadUrlsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type S2NextResult = S2NextResult.T
  object S2NextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArchiveItemIdentifier = ArchiveItemIdentifier.T
  object ArchiveItemIdentifier extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PickArchiveFileWant = PickArchiveFileWant.T
  object PickArchiveFileWant extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WikiTitleResult = WikiTitleResult.T
  object WikiTitleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WikiTextResult = WikiTextResult.T
  object WikiTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WikiRefsResultPart1 = WikiRefsResultPart1.T
  object WikiRefsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WikiRefsResultPart2 = WikiRefsResultPart2.T
  object WikiRefsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WikiRefsWikitext = WikiRefsWikitext.T
  object WikiRefsWikitext extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WikiPageTitleResult = WikiPageTitleResult.T
  object WikiPageTitleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WikiPageTitleUrl = WikiPageTitleUrl.T
  object WikiPageTitleUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def str(c: ACursor, keys: String*): Option[com.histmersion.upstreams.parse.AcquireParse.StrResult] =
    (keys.foldLeft(c)(_.downField(_)).as[String].toOption.map(_.trim).filter(_.nonEmpty)).map(refactorValue => _root_.com.histmersion.upstreams.parse.AcquireParse.StrResult(refactorValue))
  private def int(c: ACursor, keys: String*): Option[com.histmersion.upstreams.parse.AcquireParse.IntResult] =
    (keys.foldLeft(c)(_.downField(_)).as[Int].toOption).map(refactorValue2 => _root_.com.histmersion.upstreams.parse.AcquireParse.IntResult(refactorValue2))
  private def bool(c: ACursor, keys: String*): com.histmersion.upstreams.parse.AcquireParse.BoolResult =
    _root_.com.histmersion.upstreams.parse.AcquireParse.BoolResult(keys.foldLeft(c)(_.downField(_)).as[Boolean].toOption.getOrElse(false))
  private def arr(c: ACursor, keys: String*): List[Json] =
    keys.foldLeft(c)(_.downField(_)).values.map(_.toList).getOrElse(Nil)
  private def strings(c: ACursor, keys: String*): List[com.histmersion.upstreams.parse.AcquireParse.StringsResult] =
    (arr(c, keys*).flatMap(_.asString).map(_.trim).filter(_.nonEmpty)).map(refactorValue3 => _root_.com.histmersion.upstreams.parse.AcquireParse.StringsResult(refactorValue3))

  /** The trailing path segment of an OpenAlex URI (`https://openalex.org/W123` → `W123`). */
  private def tail(uri: com.histmersion.upstreams.parse.AcquireParse.TailUri): com.histmersion.upstreams.parse.AcquireParse.TailResult = (uri).value.split("/").lastOption.getOrElse(uri)

  // ── OpenAlex ────────────────────────────────────────────────────────────────────────────────

  /** OpenAlex ships abstracts as an inverted index (`word -> [positions]`) because publishers forbid redistributing the
    * running text. Re-ordering the positions restores it — the same trick `discover.py::_abstract` used.
    */
  def openalexAbstract(json: Json): Option[com.histmersion.upstreams.parse.AcquireParse.OpenalexAbstractResult] =
    (json.asObject
      .map { o =>
        val words = o.toList.flatMap { (w, positions) =>
          positions.asArray.toList.flatten.flatMap(_.asNumber).flatMap(_.toInt).map(_ -> w)
        }
        words.sortBy(_._1).map(_._2).mkString(" ")
      }
      .map(_.trim)
      .filter(_.nonEmpty)).map(refactorValue4 => _root_.com.histmersion.upstreams.parse.AcquireParse.OpenalexAbstractResult(refactorValue4))

  /** One OpenAlex `work` → the normalised [[WorkRef]] (the port of `discover.py::normalize_work`). */
  def openalexWork(json: Json): WorkRef =
    val c = json.hcursor
    val authors = arr(c, "authorships").flatMap(a => str(a.hcursor, "author", "display_name"))
    val doi = str(c, "doi").flatMap(Identifiers.doi)
    val title = str(c, "display_name").map(inlineMarkup)
    val year = int(c, "publication_year")
    WorkRef(
      doi = doi,
      openalexId = str(c, "id").map(tail),
      // the arXiv preprint of a paywalled paper is usually the best OA location, so look there too
      arxivId = (str(c.downField("best_oa_location"), "landing_page_url").toList ++
        str(c, "open_access", "oa_url").toList ++
        arr(c, "locations").flatMap(l => str(l.hcursor, "landing_page_url")))
        .flatMap(arxivIdFromUrl)
        .headOption,
      title = title,
      year = year,
      authors = authors,
      venue = str(c, "primary_location", "source", "display_name"),
      citedByCount = int(c, "cited_by_count"),
      isOa = bool(c, "open_access", "is_oa"),
      oaStatus = str(c, "open_access", "oa_status"),
      oaUrl = str(c, "open_access", "oa_url"),
      isRetracted = bool(c, "is_retracted"),
      workType = str(c, "type"),
      abstractText = c.downField("abstract_inverted_index").focus.flatMap(openalexAbstract),
      referencedWorks = strings(c, "referenced_works").map(tail),
      oaLocations = openalexOaLocations(c),
      oaLicence = str(c.downField("best_oa_location"), "license").orElse(
        arr(c, "locations").filter(l => bool(l.hcursor, "is_oa")).flatMap(l => str(l.hcursor, "license")).headOption
      )
    )

  /** The open locations' landing pages and PDFs, the best location first. */
  private def openalexOaLocations(c: ACursor): List[com.histmersion.upstreams.parse.AcquireParse.OpenalexOaLocationsResult] =
    val best = c.downField("best_oa_location")
    val open = arr(c, "locations").filter(l => (bool(l.hcursor, "is_oa")).value)
    (List(str(best, "landing_page_url"), str(best, "pdf_url")).flatten ++
      open.flatMap(l => List(str(l.hcursor, "landing_page_url"), str(l.hcursor, "pdf_url")).flatten)).distinct

  /** An OpenAlex `/works` search page. */
  def openalexWorks(json: Json): List[WorkRef] =
    arr(json.hcursor, "results").map(openalexWork)

  /** OA locations OpenAlex knows about, best first — catches green-OA repository PDFs (PMC, bioRxiv) that Unpaywall's
    * `best_oa_location` sometimes misses.
    */
  def openalexPdfUrls(json: Json): List[com.histmersion.upstreams.parse.AcquireParse.OpenalexPdfUrlsResult] =
    val c = json.hcursor
    val oa = str(c, "open_access", "oa_url").toList
    val best = str(c.downField("best_oa_location"), "pdf_url").toList
    val rest = arr(c, "locations").flatMap(l => str(l.hcursor, "pdf_url"))
    (best ++ oa ++ rest).distinct

  private def arxivIdFromUrl(url: com.histmersion.upstreams.parse.AcquireParse.ArxivIdFromUrlUrl): Option[com.histmersion.upstreams.parse.AcquireParse.ArxivIdFromUrlResult] =
    if !(url).value.toLowerCase.contains("arxiv.org") then None
    else Identifiers.arxiv((url).value.split("/").lastOption.getOrElse("").stripSuffix(".pdf"))

  /** Citing works from OpenAlex (`filter=cites:<id>`) — the fallback when Semantic Scholar is down or rate-limited.
    * OpenAlex has no citation intents, so every row has an empty context (the host classifies it as `cites`).
    */
  def openalexCitations(json: Json): List[FollowupRow] =
    openalexWorks(json).map { w =>
      FollowupRow(
        title = w.title,
        year = w.year,
        doi = w.doi,
        arxiv = w.arxivId,
        citedBy = w.citedByCount,
        isInfluential = _root_.com.histmersion.domain.library.acquisition.FollowupRow.IsInfluential(false),
        intents = Nil,
        contexts = Nil
      )
    }

  // ── Crossref ────────────────────────────────────────────────────────────────────────────────

  /** A Crossref `works/{doi}` message → [[WorkRef]] (the metadata fallback when OpenAlex misses). */
  def crossrefWork(json: Json): WorkRef =
    val m = json.hcursor.downField("message")
    val c = if m.succeeded then m else json.hcursor
    val authors = arr(c, "author")
      .map { a =>
        val h = a.hcursor
        List(str(h, "given"), str(h, "family")).flatten.mkString(" ").trim
      }
      .filter(_.nonEmpty)
    val title = strings(c, "title").headOption.map(inlineMarkup).map(gluedPageNumber)
    val year = arr(c, "issued", "date-parts").headOption
      .flatMap(_.asArray)
      .flatMap(_.headOption)
      .flatMap(_.asNumber)
      .flatMap(_.toInt)
    WorkRef(
      doi = str(c, "DOI").flatMap(Identifiers.doi),
      title = title,
      year = year,
      authors = authors,
      venue = strings(c, "container-title").headOption,
      citedByCount = int(c, "is-referenced-by-count"),
      workType = str(c, "type"),
      abstractText = str(c, "abstract").map(stripTags)
    )

  /** Titles carry inline markup (`<i>c</i>. 1200 BC`, `H<sub>2</sub>O`): drop the tags WITHOUT a space — they sit
    * inside words — and collapse what is left.
    */
  private[upstreams] def inlineMarkup(s: com.histmersion.upstreams.parse.AcquireParse.InlineMarkupS): com.histmersion.upstreams.parse.AcquireParse.InlineMarkupResult =
    (s).value.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim

  /** Some publishers deposit a chapter title with its first page glued on: "109Writing and Religion in …". Digits
    * running straight into a capitalised word are that page; "1177 B.C." (a space) and "3.2 ka" are left alone.
    */
  private[upstreams] def gluedPageNumber(s: com.histmersion.upstreams.parse.AcquireParse.GluedPageNumberS): com.histmersion.upstreams.parse.AcquireParse.GluedPageNumberResult =
    (s).value.replaceFirst("^\\d{1,4}(?=\\p{Lu}\\p{Ll})", "")

  /** A Crossref `works?query=` page → the works on it (the search fallback when OpenAlex is down). */
  def crossrefWorks(json: Json): List[WorkRef] =
    json.hcursor.downField("message").downField("items").values.toList.flatten.map(crossrefWork)

  /** Crossref abstracts are JATS XML fragments. */
  private def stripTags(s: com.histmersion.upstreams.parse.AcquireParse.StripTagsS): com.histmersion.upstreams.parse.AcquireParse.StripTagsResult =
    (s).value.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim

  /** The deposited `reference[]` array — structured, often carrying DOIs, and free of tokens. */
  def crossrefReferences(json: Json): List[RefRow] =
    val m = json.hcursor.downField("message")
    val c = if m.succeeded then m else json.hcursor
    arr(c, "reference").map { r =>
      val h = r.hcursor
      RefRow(
        title = str(h, "article-title").orElse(str(h, "volume-title")),
        authors = str(h, "author").toList,
        year = str(h, "year").filter((_).value.forall(_.isDigit)).map((_).value.toInt),
        doi = str(h, "DOI").flatMap(Identifiers.doi),
        raw = str(h, "unstructured").map((_).value.take(300))
      )
    }

  /** `update-to`: the retraction / correction / expression-of-concern notices attached to a DOI. */
  def crossrefUpdates(json: Json): List[UpdateNotice] =
    val m = json.hcursor.downField("message")
    val c = if m.succeeded then m else json.hcursor
    arr(c, "update-to").map { u =>
      val h = u.hcursor
      UpdateNotice(str(h, "type"), str(h, "label"), str(h, "DOI").flatMap(Identifiers.doi))
    }

  // ── Unpaywall / CORE ────────────────────────────────────────────────────────────────────────

  /** `(pdf urls best-first, oa_status)`. Unpaywall's `best_oa_location` first, then every other location it lists.
    */
  def unpaywall(json: Json): (List[com.histmersion.upstreams.parse.AcquireParse.UnpaywallResultPart1], Option[com.histmersion.upstreams.parse.AcquireParse.UnpaywallResultPart2]) =
    val c = json.hcursor
    val best = str(c.downField("best_oa_location"), "url_for_pdf").toList
    val rest = arr(c, "oa_locations").flatMap(l => str(l.hcursor, "url_for_pdf"))
    ((best ++ rest).distinct, str(c, "oa_status"))

  /** Unpaywall's verdict on one DOI: whether a free copy exists, its `oa_status` (`gold`, `green`, …, `closed`) and the
    * best location — the PDF when there is one, else its landing page.
    */
  def unpaywallAccess(json: Json): OaAccess =
    val c = json.hcursor
    val best = c.downField("best_oa_location")
    OaAccess(
      bool(c, "is_oa"),
      str(c, "oa_status"),
      str(best, "url_for_pdf").orElse(str(best, "url")).orElse(str(best, "url_for_landing_page"))
    )

  // ── Europe PMC / PMC Article Datasets ───────────────────────────────────────────────────────

  /** Europe PMC availability codes that mean a reader may download the copy without an account. */
  val EuropePmcFree: Set[com.histmersion.upstreams.parse.AcquireParse.EuropePmcFree] = Set(_root_.com.histmersion.upstreams.parse.AcquireParse.EuropePmcFree("OA"), _root_.com.histmersion.upstreams.parse.AcquireParse.EuropePmcFree("F"))

  /** The `search?query=DOI:"…"&resultType=core` hit whose DOI IS the one asked for (a DOI query can match a correction
    * or a comment that cites it).
    */
  def europepmcHit(json: Json, doi: com.histmersion.upstreams.parse.AcquireParse.EuropepmcHitDoi): Option[EuropePmcHit] =
    val want = Identifiers.doi(doi)
    arr(json.hcursor, "resultList", "result")
      .find(r => want.isDefined && str(r.hcursor, "doi").flatMap(Identifiers.doi) == want)
      .map { r =>
        val c = r.hcursor
        val pdfs = arr(c, "fullTextUrlList", "fullTextUrl").flatMap { u =>
          val h = u.hcursor
          val isPdf = str(h, "documentStyle").exists((_).value.equalsIgnoreCase("pdf"))
          val free = str(h, "availabilityCode").exists(a => EuropePmcFree((a).value.toUpperCase))
          if isPdf && free then str(h, "url").filter((_).value.startsWith("http")) else None
        }.distinct
        EuropePmcHit(
          str(c, "pmcid").map((_).value.toUpperCase).filter(PmcId.matches),
          _root_.com.histmersion.upstreams.europepmc.EuropePmcHit.IsOpenAccess(str(c, "isOpenAccess").contains("Y")),
          pdfs
        )
      }

  private val PmcId = """PMC\d+""".r

  /** The newest version NCBI publishes of `pmcid`, from an S3 `ListObjectsV2` answer for the prefix `metadata/<pmcid>.`
    * (keys `metadata/PMC123.1.json`, `metadata/PMC123.2.json`, …).
    */
  def pmcLatestVersion(listXml: com.histmersion.upstreams.parse.AcquireParse.PmcLatestVersionListXml, pmcid: com.histmersion.upstreams.parse.AcquireParse.PmcLatestVersionPmcid): Option[com.histmersion.upstreams.parse.AcquireParse.PmcLatestVersionResult] =
    if !PmcId.matches((pmcid).value) then None
    else
      val key = ("<Key>metadata/" + pmcid + """\.(\d+)\.json</Key>""").r
      key.findAllMatchIn(listXml).flatMap(m => m.group(1).toIntOption).maxOption

  /** A dataset metadata record (`metadata/<pmcid>.<v>.json`). */
  def pmcRecord(json: Json): Option[PmcRecord] =
    val c = json.hcursor
    str(c, "pmcid").filter(PmcId.matches).map { id =>
      PmcRecord(
        id,
        bool(c, "is_pmc_openaccess"),
        bool(c, "is_manuscript"),
        str(c, "license_code"),
        str(c, "pdf_url").flatMap(pmcObject),
        str(c, "xml_url").flatMap(pmcObject)
      )
    }

  /** `s3://pmc-oa-opendata/PMC1.1/PMC1.1.pdf?md5=…` → the key and the MD5. Only the shapes the dataset uses are
    * accepted, so a record can never point a download outside an article's own folder.
    */
  def pmcObject(s3: com.histmersion.upstreams.parse.AcquireParse.PmcObjectS3): Option[PmcObject] =
    S3Url.findFirstMatchIn((s3).value.trim).map(m => PmcObject(m.group(1), Option(m.group(2)).map(_.toLowerCase)))

  private val S3Url = """^s3://[a-z0-9.-]+/(PMC\d+\.\d+/[A-Za-z0-9_-][A-Za-z0-9._-]*)(?:\?md5=([0-9a-fA-F]{32}))?$""".r

  def coreDownloadUrls(json: Json): List[com.histmersion.upstreams.parse.AcquireParse.CoreDownloadUrlsResult] =
    arr(json.hcursor, "results").flatMap(r => str(r.hcursor, "downloadUrl"))

  // ── Semantic Scholar ────────────────────────────────────────────────────────────────────────

  /** One page of `/paper/DOI:{doi}/citations`. Each row carries the citing paper plus the citation's `intents`,
    * `isInfluential` flag and `contexts` — the signals the host classifies a citation by.
    */
  def s2Citations(json: Json): List[FollowupRow] =
    arr(json.hcursor, "data").map { d =>
      val h = d.hcursor
      val cp = h.downField("citingPaper")
      FollowupRow(
        title = str(cp, "title"),
        year = int(cp, "year"),
        doi = str(cp, "externalIds", "DOI").flatMap(Identifiers.doi),
        arxiv = str(cp, "externalIds", "ArXiv").flatMap(Identifiers.arxiv),
        citedBy = int(cp, "citationCount"),
        isInfluential = bool(h, "isInfluential"),
        intents = strings(h, "intents"),
        contexts = strings(h, "contexts").map((_).value.replaceAll("\\s+", " ").take(400)).take(3)
      )
    }

  /** `next` offset of an S2 page, absent on the last page. */
  def s2Next(json: Json): Option[com.histmersion.upstreams.parse.AcquireParse.S2NextResult] = int(json.hcursor, "next")

  // ── archive.org ─────────────────────────────────────────────────────────────────────────────

  /** `/metadata/{id}` → the item, including the lending restriction that stops an import dead.
    *
    * `access-restricted-item = true` (or `is_dark`) means the scan is LENDING-ONLY: it may be borrowed on archive.org
    * but never downloaded here. The Python tool refused at exactly this point and so do we — there is no DRM
    * circumvention anywhere in this pipeline.
    */
  def archiveItem(identifier: com.histmersion.upstreams.parse.AcquireParse.ArchiveItemIdentifier, json: Json): ArchiveItem =
    val c = json.hcursor
    val m = c.downField("metadata")
    val yr = str(m, "year").orElse(str(m, "date")).map((_).value.take(4)).filter(_.forall(_.isDigit)).map(_.toInt)
    val creators =
      (str(m, "creator").toList ++ strings(m, "creator")).distinct.filterNot(_.startsWith("http"))
    val files = arr(c, "files").flatMap { f =>
      val h = f.hcursor
      val name = str(h, "name").getOrElse("")
      val ext = name.toLowerCase.split("\\.").lastOption.getOrElse("")
      Option.when(ext == "pdf" || ext == "epub") {
        val size = str(h, "size").flatMap((_).value.toLongOption).getOrElse(0L)
        ArchiveFile(name, str(h, "format"), _root_.com.histmersion.upstreams.archiveorg.ArchiveFile.Megabytes(math.round(size / 1e5) / 10.0))
      }
    }
    ArchiveItem(
      identifier = identifier,
      title = str(m, "title"),
      creator = creators,
      year = yr,
      publisher = str(m, "publisher"),
      description = str(m, "description").map(stripTags).map(_.take(500)),
      lendingOnly = str(m, "access-restricted-item").exists((_).value.equalsIgnoreCase("true")) ||
        (bool(c, "is_dark")).value,
      files = files
    )

  /** `advancedsearch.php` docs → identifiers with the little metadata the index carries. */
  def archiveSearch(json: Json): List[ArchiveItem] =
    arr(json.hcursor, "response", "docs").flatMap { d =>
      val h = d.hcursor
      str(h, "identifier").map { id =>
        ArchiveItem(
          identifier = id,
          title = str(h, "title"),
          creator = (str(h, "creator").toList ++ strings(h, "creator")).distinct,
          year = str(h, "year").map((_).value.take(4)).filter(_.forall(_.isDigit)).map(_.toInt)
        )
      }
    }

  /** Pick the best downloadable file: a clean text format (EPUB, or the OCR'd `_text.pdf`) beats the giant raw image
    * scan, unless the caller asks for a specific format. Returns the file name.
    */
  def pickArchiveFile(files: List[ArchiveFile], want: Option[com.histmersion.upstreams.parse.AcquireParse.PickArchiveFileWant]): Option[ArchiveFile] =
    def ext(f: ArchiveFile) = f.name.toLowerCase.split("\\.").lastOption.getOrElse("")
    val epubs = files.filter(ext(_) == "epub")
    val textPdfs = files.filter(_.name.toLowerCase.endsWith("_text.pdf"))
    val pdfs = files.filter(f => ext(f) == "pdf" && !textPdfs.contains(f))
    val order = want.map((_).value.toLowerCase) match
      case Some("pdf")  => textPdfs ++ pdfs ++ epubs
      case Some("epub") => epubs ++ textPdfs ++ pdfs
      case _            => epubs ++ textPdfs ++ pdfs
    order.headOption

  // ── Wikipedia (the trailhead) ───────────────────────────────────────────────────────────────

  private val DoiInText = """10\.\d{4,9}/[-._;()/:A-Za-z0-9]+""".r
  private val ArxivInText = """(?i)arxiv\s*[:=]\s*([0-9]{4}\.[0-9]{4,5})""".r

  /** The article title from `action=parse&prop=wikitext` (after redirects). */
  def wikiTitle(json: Json): Option[com.histmersion.upstreams.parse.AcquireParse.WikiTitleResult] = str(json.hcursor.downField("parse"), "title")

  def wikiText(json: Json): Option[com.histmersion.upstreams.parse.AcquireParse.WikiTextResult] =
    (json.hcursor.downField("parse").downField("wikitext").downField("*").as[String].toOption).map(refactorValue48 => _root_.com.histmersion.upstreams.parse.AcquireParse.WikiTextResult(refactorValue48))

  /** Harvest every DOI and arXiv id cited by a Wikipedia article's wikitext. Citations trail punctuation (`|`, `}}`,
    * `.`) into the match, so each hit goes through [[Identifiers.doi]].
    */
  def wikiRefs(wikitext: com.histmersion.upstreams.parse.AcquireParse.WikiRefsWikitext): (List[com.histmersion.upstreams.parse.AcquireParse.WikiRefsResultPart1], List[com.histmersion.upstreams.parse.AcquireParse.WikiRefsResultPart2]) =
    val dois = DoiInText.findAllIn(wikitext).toList.flatMap(Identifiers.doi).distinct.sorted
    val arxiv = ArxivInText.findAllMatchIn(wikitext).map(_.group(1)).toList.distinct.sorted
    (dois, arxiv)

  /** The Wikipedia page title of an article URL (`…/wiki/Late_Bronze_Age_collapse`). */
  def wikiPageTitle(url: com.histmersion.upstreams.parse.AcquireParse.WikiPageTitleUrl): Option[com.histmersion.upstreams.parse.AcquireParse.WikiPageTitleResult] =
    val s = (url).value.trim.stripSuffix("/")
    Option(if s.contains("/wiki/") then s.split("/wiki/").last else s)
      .map(_.split("[?#]").head)
      .map(_.trim)
      .filter(_.nonEmpty)
