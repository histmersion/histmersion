package com.histmersion.pluginhost

import com.histmersion.api.*
import com.histmersion.api.given

import io.circe.{ACursor, Json}

/** PURE parsers for the JSON the acquisition upstreams return (OpenAlex, Crossref, Unpaywall, CORE,
  * Semantic Scholar, archive.org, Wikipedia).
  *
  * Keeping them here — separate from the HTTP clients — is what makes the whole toolset testable
  * from fixtures: a test feeds real captured JSON (`src/test/resources/acquire/`)
  * straight into these functions with no network involved. Everything is total: a missing or
  * unexpected field yields `None`/`Nil`, never an exception, because upstreams do change shape.
  */
/** Whether a free copy of a work exists, as an OA index (Unpaywall) reports it. */
final case class OaAccess(isOa: Boolean, status: Option[String], url: Option[String])

/** What Europe PMC's search knows about one DOI (#1523): the PMC copy, if any, whether it is in the
  * open-access subset, and the PDF links it lists as free to read (`OA` open access, `F` free —
  * an author manuscript); `S` (subscription) and `R` (registration) links are never taken. */
final case class EuropePmcHit(pmcid: Option[String], isOpenAccess: Boolean, pdfUrls: List[String])

/** One object of NCBI's PMC Article Datasets bucket, by key, with the MD5 the metadata records. */
final case class PmcObject(key: String, md5: Option[String])

/** A PMC Article Datasets metadata record: which files NCBI publishes for one article version. */
final case class PmcRecord(pmcid: String, openAccess: Boolean, manuscript: Boolean, license: Option[String],
    pdf: Option[PmcObject], xml: Option[PmcObject])

object AcquireParse:

  // ── small cursor helpers ────────────────────────────────────────────────────────────────────
  private def str(c: ACursor, keys: String*): Option[String] =
    keys.foldLeft(c)(_.downField(_)).as[String].toOption.map(_.trim).filter(_.nonEmpty)
  private def int(c: ACursor, keys: String*): Option[Int] =
    keys.foldLeft(c)(_.downField(_)).as[Int].toOption
  private def bool(c: ACursor, keys: String*): Boolean =
    keys.foldLeft(c)(_.downField(_)).as[Boolean].toOption.getOrElse(false)
  private def arr(c: ACursor, keys: String*): List[Json] =
    keys.foldLeft(c)(_.downField(_)).values.map(_.toList).getOrElse(Nil)
  private def strings(c: ACursor, keys: String*): List[String] =
    arr(c, keys*).flatMap(_.asString).map(_.trim).filter(_.nonEmpty)

  /** The trailing path segment of an OpenAlex URI (`https://openalex.org/W123` → `W123`). */
  private def tail(uri: String): String = uri.split("/").lastOption.getOrElse(uri)

  // ── OpenAlex ────────────────────────────────────────────────────────────────────────────────

  /** OpenAlex ships abstracts as an inverted index (`word -> [positions]`) because publishers forbid
    * redistributing the running text. Re-ordering the positions restores it — the same trick
    * `discover.py::_abstract` used. */
  def openalexAbstract(json: Json): Option[String] =
    json.asObject.map { o =>
      val words = o.toList.flatMap { (w, positions) =>
        positions.asArray.toList.flatten.flatMap(_.asNumber).flatMap(_.toInt).map(_ -> w)
      }
      words.sortBy(_._1).map(_._2).mkString(" ")
    }.map(_.trim).filter(_.nonEmpty)

  /** One OpenAlex `work` → the normalised [[WorkRef]] (the port of `discover.py::normalize_work`). */
  def openalexWork(json: Json): WorkRef =
    val c       = json.hcursor
    val authors = arr(c, "authorships").flatMap(a => str(a.hcursor, "author", "display_name"))
    val doi     = str(c, "doi").flatMap(Acquire.normalizeDoi)
    val title   = str(c, "display_name").map(inlineMarkup)
    val year    = int(c, "publication_year")
    WorkRef(
      doi = doi,
      openalexId = str(c, "id").map(tail),
      // the arXiv preprint of a paywalled paper is usually the best OA location, so look there too
      arxivId = (str(c.downField("best_oa_location"), "landing_page_url").toList ++
        str(c, "open_access", "oa_url").toList ++
        arr(c, "locations").flatMap(l => str(l.hcursor, "landing_page_url")))
        .flatMap(arxivIdFromUrl).headOption,
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
      citekey = Some(Acquire.citekey(authors, year, title)),
      oaLocations = openalexOaLocations(c),
      oaLicence = str(c.downField("best_oa_location"), "license").orElse(
        arr(c, "locations").filter(l => bool(l.hcursor, "is_oa")).flatMap(l => str(l.hcursor, "license")).headOption)
    )

  /** The open locations' landing pages and PDFs, the best location first. */
  private def openalexOaLocations(c: ACursor): List[String] =
    val best = c.downField("best_oa_location")
    val open = arr(c, "locations").filter(l => bool(l.hcursor, "is_oa"))
    (List(str(best, "landing_page_url"), str(best, "pdf_url")).flatten ++
      open.flatMap(l => List(str(l.hcursor, "landing_page_url"), str(l.hcursor, "pdf_url")).flatten)).distinct

  /** An OpenAlex `/works` search page. */
  def openalexWorks(json: Json): List[WorkRef] =
    arr(json.hcursor, "results").map(openalexWork)

  /** OA locations OpenAlex knows about, best first — catches green-OA repository PDFs (PMC, bioRxiv)
    * that Unpaywall's `best_oa_location` sometimes misses. */
  def openalexPdfUrls(json: Json): List[String] =
    val c   = json.hcursor
    val oa  = str(c, "open_access", "oa_url").toList
    val best = str(c.downField("best_oa_location"), "pdf_url").toList
    val rest = arr(c, "locations").flatMap(l => str(l.hcursor, "pdf_url"))
    (best ++ oa ++ rest).distinct

  private def arxivIdFromUrl(url: String): Option[String] =
    if !url.toLowerCase.contains("arxiv.org") then None
    else Acquire.normalizeArxiv(url.split("/").lastOption.getOrElse("").stripSuffix(".pdf"))

  /** Citing works from OpenAlex (`filter=cites:<id>`) — the fallback when Semantic Scholar is down or
    * rate-limited. OpenAlex has no citation intents, so every row is left for [[Acquire.classify]] to
    * read from an empty context (i.e. `cites`). */
  def openalexCitations(json: Json): List[FollowupRow] =
    openalexWorks(json).map { w =>
      FollowupRow(title = w.title, year = w.year, doi = w.doi, arxiv = w.arxivId,
        citedBy = w.citedByCount, isInfluential = false, intents = Nil, contexts = Nil)
    }

  // ── Crossref ────────────────────────────────────────────────────────────────────────────────

  /** A Crossref `works/{doi}` message → [[WorkRef]] (the metadata fallback when OpenAlex misses). */
  def crossrefWork(json: Json): WorkRef =
    val m       = json.hcursor.downField("message")
    val c       = if m.succeeded then m else json.hcursor
    val authors = arr(c, "author").map { a =>
      val h = a.hcursor
      List(str(h, "given"), str(h, "family")).flatten.mkString(" ").trim
    }.filter(_.nonEmpty)
    val title = strings(c, "title").headOption.map(inlineMarkup).map(gluedPageNumber)
    val year  = arr(c, "issued", "date-parts").headOption
      .flatMap(_.asArray).flatMap(_.headOption).flatMap(_.asNumber).flatMap(_.toInt)
    WorkRef(
      doi = str(c, "DOI").flatMap(Acquire.normalizeDoi),
      title = title,
      year = year,
      authors = authors,
      venue = strings(c, "container-title").headOption,
      citedByCount = int(c, "is-referenced-by-count"),
      workType = str(c, "type"),
      abstractText = str(c, "abstract").map(stripTags),
      citekey = Some(Acquire.citekey(authors, year, title))
    )

  /** Titles carry inline markup (`<i>c</i>. 1200 BC`, `H<sub>2</sub>O`): drop the tags WITHOUT a
    * space — they sit inside words — and collapse what is left. */
  private[pluginhost] def inlineMarkup(s: String): String =
    s.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim

  /** Some publishers deposit a chapter title with its first page glued on: "109Writing and Religion
    * in …". Digits running straight into a capitalised word are that page; "1177 B.C." (a space) and
    * "3.2 ka" are left alone. */
  private[pluginhost] def gluedPageNumber(s: String): String =
    s.replaceFirst("^\\d{1,4}(?=\\p{Lu}\\p{Ll})", "")

  /** A Crossref `works?query=` page → the works on it (the search fallback when OpenAlex is down). */
  def crossrefWorks(json: Json): List[WorkRef] =
    json.hcursor.downField("message").downField("items").values.toList.flatten.map(crossrefWork)

  /** Crossref abstracts are JATS XML fragments. */
  private def stripTags(s: String): String =
    s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim

  /** The deposited `reference[]` array — structured, often carrying DOIs, and free of tokens. */
  def crossrefReferences(json: Json): List[RefRow] =
    val m = json.hcursor.downField("message")
    val c = if m.succeeded then m else json.hcursor
    arr(c, "reference").map { r =>
      val h = r.hcursor
      RefRow(
        title = str(h, "article-title").orElse(str(h, "volume-title")),
        authors = str(h, "author").toList,
        year = str(h, "year").filter(_.forall(_.isDigit)).map(_.toInt),
        doi = str(h, "DOI").flatMap(Acquire.normalizeDoi),
        raw = str(h, "unstructured").map(_.take(300))
      )
    }

  /** `update-to`: the retraction / correction / expression-of-concern notices attached to a DOI. */
  def crossrefUpdates(json: Json): List[UpdateNotice] =
    val m = json.hcursor.downField("message")
    val c = if m.succeeded then m else json.hcursor
    arr(c, "update-to").map { u =>
      val h = u.hcursor
      UpdateNotice(str(h, "type"), str(h, "label"), str(h, "DOI").flatMap(Acquire.normalizeDoi))
    }

  // ── Unpaywall / CORE ────────────────────────────────────────────────────────────────────────

  /** `(pdf urls best-first, oa_status)`. Unpaywall's `best_oa_location` first, then every other
    * location it lists. */
  def unpaywall(json: Json): (List[String], Option[String]) =
    val c    = json.hcursor
    val best = str(c.downField("best_oa_location"), "url_for_pdf").toList
    val rest = arr(c, "oa_locations").flatMap(l => str(l.hcursor, "url_for_pdf"))
    ((best ++ rest).distinct, str(c, "oa_status"))

  /** Unpaywall's verdict on one DOI: whether a free copy exists, its `oa_status` (`gold`, `green`,
    * …, `closed`) and the best location — the PDF when there is one, else its landing page. */
  def unpaywallAccess(json: Json): OaAccess =
    val c    = json.hcursor
    val best = c.downField("best_oa_location")
    OaAccess(bool(c, "is_oa"), str(c, "oa_status"),
      str(best, "url_for_pdf").orElse(str(best, "url")).orElse(str(best, "url_for_landing_page")))

  // ── Europe PMC / PMC Article Datasets ───────────────────────────────────────────────────────

  /** Europe PMC availability codes that mean a reader may download the copy without an account. */
  val EuropePmcFree: Set[String] = Set("OA", "F")

  /** The `search?query=DOI:"…"&resultType=core` hit whose DOI IS the one asked for (a DOI query can
    * match a correction or a comment that cites it). */
  def europepmcHit(json: Json, doi: String): Option[EuropePmcHit] =
    val want = Acquire.normalizeDoi(doi)
    arr(json.hcursor, "resultList", "result")
      .find(r => want.isDefined && str(r.hcursor, "doi").flatMap(Acquire.normalizeDoi) == want)
      .map { r =>
        val c = r.hcursor
        val pdfs = arr(c, "fullTextUrlList", "fullTextUrl").flatMap { u =>
          val h = u.hcursor
          val isPdf = str(h, "documentStyle").exists(_.equalsIgnoreCase("pdf"))
          val free  = str(h, "availabilityCode").exists(a => EuropePmcFree(a.toUpperCase))
          if isPdf && free then str(h, "url").filter(_.startsWith("http")) else None
        }.distinct
        EuropePmcHit(str(c, "pmcid").map(_.toUpperCase).filter(PmcId.matches), str(c, "isOpenAccess").contains("Y"), pdfs)
      }

  private val PmcId = """PMC\d+""".r

  /** The newest version NCBI publishes of `pmcid`, from an S3 `ListObjectsV2` answer for the prefix
    * `metadata/<pmcid>.` (keys `metadata/PMC123.1.json`, `metadata/PMC123.2.json`, …). */
  def pmcLatestVersion(listXml: String, pmcid: String): Option[Int] =
    if !PmcId.matches(pmcid) then None
    else
      val key = ("<Key>metadata/" + pmcid + """\.(\d+)\.json</Key>""").r
      key.findAllMatchIn(listXml).flatMap(m => m.group(1).toIntOption).maxOption

  /** A dataset metadata record (`metadata/<pmcid>.<v>.json`). */
  def pmcRecord(json: Json): Option[PmcRecord] =
    val c = json.hcursor
    str(c, "pmcid").filter(PmcId.matches).map { id =>
      PmcRecord(id, bool(c, "is_pmc_openaccess"), bool(c, "is_manuscript"), str(c, "license_code"),
        str(c, "pdf_url").flatMap(pmcObject), str(c, "xml_url").flatMap(pmcObject))
    }

  /** `s3://pmc-oa-opendata/PMC1.1/PMC1.1.pdf?md5=…` → the key and the MD5. Only the shapes the dataset
    * uses are accepted, so a record can never point a download outside an article's own folder. */
  def pmcObject(s3: String): Option[PmcObject] =
    S3Url.findFirstMatchIn(s3.trim).map(m => PmcObject(m.group(1), Option(m.group(2)).map(_.toLowerCase)))

  private val S3Url = """^s3://[a-z0-9.-]+/(PMC\d+\.\d+/[A-Za-z0-9_-][A-Za-z0-9._-]*)(?:\?md5=([0-9a-fA-F]{32}))?$""".r

  def coreDownloadUrls(json: Json): List[String] =
    arr(json.hcursor, "results").flatMap(r => str(r.hcursor, "downloadUrl"))

  // ── Semantic Scholar ────────────────────────────────────────────────────────────────────────

  /** One page of `/paper/DOI:{doi}/citations`. Each row carries the citing paper plus the citation's
    * `intents`, `isInfluential` flag and `contexts` — the signals [[Acquire.classify]] reads. */
  def s2Citations(json: Json): List[FollowupRow] =
    arr(json.hcursor, "data").map { d =>
      val h  = d.hcursor
      val cp = h.downField("citingPaper")
      FollowupRow(
        title = str(cp, "title"),
        year = int(cp, "year"),
        doi = str(cp, "externalIds", "DOI").flatMap(Acquire.normalizeDoi),
        arxiv = str(cp, "externalIds", "ArXiv").flatMap(Acquire.normalizeArxiv),
        citedBy = int(cp, "citationCount"),
        isInfluential = bool(h, "isInfluential"),
        intents = strings(h, "intents"),
        contexts = strings(h, "contexts").map(_.replaceAll("\\s+", " ").take(400)).take(3)
      )
    }

  /** `next` offset of an S2 page, absent on the last page. */
  def s2Next(json: Json): Option[Int] = int(json.hcursor, "next")

  // ── archive.org ─────────────────────────────────────────────────────────────────────────────

  /** `/metadata/{id}` → the item, including the lending restriction that stops an import dead.
    *
    * `access-restricted-item = true` (or `is_dark`) means the scan is LENDING-ONLY: it may be
    * borrowed on archive.org but never downloaded here. The Python tool refused at exactly this
    * point and so do we — there is no DRM circumvention anywhere in this pipeline.
    */
  def archiveItem(identifier: String, json: Json): ArchiveItem =
    val c  = json.hcursor
    val m  = c.downField("metadata")
    val yr = str(m, "year").orElse(str(m, "date")).map(_.take(4)).filter(_.forall(_.isDigit)).map(_.toInt)
    val creators =
      (str(m, "creator").toList ++ strings(m, "creator")).distinct.filterNot(_.startsWith("http"))
    val files = arr(c, "files").flatMap { f =>
      val h    = f.hcursor
      val name = str(h, "name").getOrElse("")
      val ext  = name.toLowerCase.split("\\.").lastOption.getOrElse("")
      Option.when(ext == "pdf" || ext == "epub") {
        val size = str(h, "size").flatMap(_.toLongOption).getOrElse(0L)
        ArchiveFile(name, str(h, "format"), math.round(size / 1e5) / 10.0)
      }
    }
    ArchiveItem(
      identifier = identifier,
      title = str(m, "title"),
      creator = creators,
      year = yr,
      publisher = str(m, "publisher"),
      description = str(m, "description").map(stripTags).map(_.take(500)),
      lendingOnly = str(m, "access-restricted-item").exists(_.equalsIgnoreCase("true")) ||
        bool(c, "is_dark"),
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
          year = str(h, "year").map(_.take(4)).filter(_.forall(_.isDigit)).map(_.toInt)
        )
      }
    }

  /** Pick the best downloadable file: a clean text format (EPUB, or the OCR'd `_text.pdf`) beats the
    * giant raw image scan, unless the caller asks for a specific format. Returns the file name. */
  def pickArchiveFile(files: List[ArchiveFile], want: Option[String]): Option[ArchiveFile] =
    def ext(f: ArchiveFile) = f.name.toLowerCase.split("\\.").lastOption.getOrElse("")
    val epubs    = files.filter(ext(_) == "epub")
    val textPdfs = files.filter(_.name.toLowerCase.endsWith("_text.pdf"))
    val pdfs     = files.filter(f => ext(f) == "pdf" && !textPdfs.contains(f))
    val order = want.map(_.toLowerCase) match
      case Some("pdf")  => textPdfs ++ pdfs ++ epubs
      case Some("epub") => epubs ++ textPdfs ++ pdfs
      case _            => epubs ++ textPdfs ++ pdfs
    order.headOption

  // ── Wikipedia (the trailhead) ───────────────────────────────────────────────────────────────

  private val DoiInText   = """10\.\d{4,9}/[-._;()/:A-Za-z0-9]+""".r
  private val ArxivInText = """(?i)arxiv\s*[:=]\s*([0-9]{4}\.[0-9]{4,5})""".r

  /** The article title from `action=parse&prop=wikitext` (after redirects). */
  def wikiTitle(json: Json): Option[String] = str(json.hcursor.downField("parse"), "title")

  def wikiText(json: Json): Option[String] =
    json.hcursor.downField("parse").downField("wikitext").downField("*").as[String].toOption

  /** Harvest every DOI and arXiv id cited by a Wikipedia article's wikitext. Citations trail
    * punctuation (`|`, `}}`, `.`) into the match, so each hit goes through [[Acquire.normalizeDoi]]. */
  def wikiRefs(wikitext: String): (List[String], List[String]) =
    val dois  = DoiInText.findAllIn(wikitext).toList.flatMap(Acquire.normalizeDoi).distinct.sorted
    val arxiv = ArxivInText.findAllMatchIn(wikitext).map(_.group(1)).toList.distinct.sorted
    (dois, arxiv)

  /** The Wikipedia page title of an article URL (`…/wiki/Late_Bronze_Age_collapse`). */
  def wikiPageTitle(url: String): Option[String] =
    val s = url.trim.stripSuffix("/")
    Option(if s.contains("/wiki/") then s.split("/wiki/").last else s)
      .map(_.split("[?#]").head).map(_.trim).filter(_.nonEmpty)
