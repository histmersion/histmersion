package com.histmersion.upstreams.parse
import io.circe.{ACursor, Json}
import com.histmersion.domain.library.acquisition.{FollowupRow, RefRow, UpdateNotice, WorkRef}
import com.histmersion.upstreams.archiveorg.{ArchiveFile, ArchiveItem}
import com.histmersion.upstreams.europepmc.EuropePmcHit
import com.histmersion.upstreams.identifiers.Identifiers
import com.histmersion.upstreams.pmc.{PmcObject, PmcRecord}
import com.histmersion.upstreams.unpaywall.OaAccess
import com.histmersion.upstreams.Offset

/** PURE parsers for the JSON the acquisition upstreams return (OpenAlex, Crossref, Unpaywall, CORE, Semantic Scholar,
  * Europe PMC, PMC Article Datasets, archive.org, Wikipedia).
  *
  * Keeping them apart from the HTTP clients is what makes the toolset testable from fixtures: a test feeds real
  * captured JSON straight into these functions with no network involved. Everything is total: a missing or unexpected
  * field yields `None`/`Nil`, never an exception, because upstreams do change shape.
  */
object AcquireParse:
  type PmcVersion = PmcVersion.T
  object PmcVersion extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WantedFileKind = WantedFileKind.T
  object WantedFileKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Wikitext = Wikitext.T
  object Wikitext extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  // ── small cursor helpers ────────────────────────────────────────────────────────────────────
  type AvailabilityCode = AvailabilityCode.T
  object AvailabilityCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def str(c: ACursor, keys: String*): Option[String] =
    (keys.foldLeft(c)(_.downField(_)).as[String].toOption.map(_.trim).filter(_.nonEmpty)).map(refactorValue => (refactorValue))
  private def int(c: ACursor, keys: String*): Option[Int] =
    (keys.foldLeft(c)(_.downField(_)).as[Int].toOption).map(refactorValue2 => (refactorValue2))
  private def bool(c: ACursor, keys: String*): Boolean =
    (keys.foldLeft(c)(_.downField(_)).as[Boolean].toOption.getOrElse(false))
  private def arr(c: ACursor, keys: String*): List[Json] =
    keys.foldLeft(c)(_.downField(_)).values.map(_.toList).getOrElse(Nil)
  private def strings(c: ACursor, keys: String*): List[String] =
    (arr(c, keys*).flatMap(_.asString).map(_.trim).filter(_.nonEmpty)).map(refactorValue3 => (refactorValue3))

  /** The trailing path segment of an OpenAlex URI (`https://openalex.org/W123` → `W123`). */
  private def tail(uri: String): String = (uri).split("/").lastOption.getOrElse(uri)

  private def doiOf(s: String) = Identifiers.doi(Identifiers.DoiText(s))
  private def arxivOf(s: String) = Identifiers.arxiv(Identifiers.ArxivText(s))

  // ── OpenAlex ────────────────────────────────────────────────────────────────────────────────

  /** OpenAlex ships abstracts as an inverted index (`word -> [positions]`) because publishers forbid redistributing the
    * running text. Re-ordering the positions restores it — the same trick `discover.py::_abstract` used.
    */
  def openalexAbstract(json: Json): Option[WorkRef.AbstractText] =
    (json.asObject
      .map { o =>
        val words = o.toList.flatMap { (w, positions) =>
          positions.asArray.toList.flatten.flatMap(_.asNumber).flatMap(_.toInt).map(_ -> w)
        }
        words.sortBy(_._1).map(_._2).mkString(" ")
      }
      .map(_.trim)
      .filter(_.nonEmpty)).map(refactorValue4 => WorkRef.AbstractText(refactorValue4))

  /** One OpenAlex `work` → the normalised [[WorkRef]] (the port of `discover.py::normalize_work`). */
  def openalexWork(json: Json): WorkRef =
    val c = json.hcursor
    val authors = arr(c, "authorships").flatMap(a => str(a.hcursor, "author", "display_name"))
    val doi = str(c, "doi").flatMap(doiOf)
    val title = str(c, "display_name").map({ val refactorFunction2 = inlineMarkup; (refactorValue6) => refactorFunction2(((refactorValue6))) })
    val year = int(c, "publication_year").map(com.histmersion.plugin.Year(_))
    WorkRef(
      doi = doi,
      openalexId = str(c, "id").map(id => WorkRef.OpenalexId(tail(id))),
      // the arXiv preprint of a paywalled paper is usually the best OA location, so look there too
      arxivId = (str(c.downField("best_oa_location"), "landing_page_url").toList ++
        str(c, "open_access", "oa_url").toList ++
        arr(c, "locations").flatMap(l => str(l.hcursor, "landing_page_url")))
        .flatMap({ val refactorFunction3 = arxivIdFromUrl; (refactorValue7) => refactorFunction3(_root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url((refactorValue7))) })
        .headOption,
      title = title,
      year = year,
      authors = authors.map(com.histmersion.plugin.WorkKey.Authors(_)),
      venue = str(c, "primary_location", "source", "display_name").map(WorkRef.Venue(_)),
      citedByCount = int(c, "cited_by_count").map(WorkRef.CitedByCount(_)),
      isOa = WorkRef.IsOa(bool(c, "open_access", "is_oa")),
      oaStatus = str(c, "open_access", "oa_status").map(WorkRef.OaStatus(_)),
      oaUrl = str(c, "open_access", "oa_url").map(WorkRef.OaUrl(_)),
      isRetracted = WorkRef.IsRetracted(bool(c, "is_retracted")),
      workType = str(c, "type").map(WorkRef.WorkType(_)),
      abstractText = c.downField("abstract_inverted_index").focus.flatMap(openalexAbstract),
      referencedWorks = strings(c, "referenced_works").map(w => WorkRef.ReferencedWorks(tail(w))),
      oaLocations = openalexOaLocations(c),
      oaLicence = str(c.downField("best_oa_location"), "license")
        .orElse(
          arr(c, "locations").filter(l => ((bool(l.hcursor, "is_oa")) == (true))).flatMap(l => str(l.hcursor, "license")).headOption
        )
        .map(WorkRef.OaLicence(_))
    )

  /** The open locations' landing pages and PDFs, the best location first. */
  private def openalexOaLocations(c: ACursor): List[WorkRef.OaLocations] =
    val best = c.downField("best_oa_location")
    val open = arr(c, "locations").filter(l => (bool(l.hcursor, "is_oa")))
    ((List(str(best, "landing_page_url"), str(best, "pdf_url")).flatten ++
      open.flatMap(l => List(str(l.hcursor, "landing_page_url"), str(l.hcursor, "pdf_url")).flatten)).distinct).map(refactorValue17 => WorkRef.OaLocations(refactorValue17))

  /** An OpenAlex `/works` search page. */
  def openalexWorks(json: Json): List[WorkRef] =
    arr(json.hcursor, "results").map(openalexWork)

  /** OA locations OpenAlex knows about, best first — catches green-OA repository PDFs (PMC, bioRxiv) that Unpaywall's
    * `best_oa_location` sometimes misses.
    */
  def openalexPdfUrls(json: Json): List[com.histmersion.upstreams.europepmc.EuropePmcHit.PdfUrls] =
    val c = json.hcursor
    val oa = str(c, "open_access", "oa_url").toList
    val best = str(c.downField("best_oa_location"), "pdf_url").toList
    val rest = arr(c, "locations").flatMap(l => str(l.hcursor, "pdf_url"))
    ((best ++ oa ++ rest).distinct).map(refactorValue18 => _root_.com.histmersion.upstreams.europepmc.EuropePmcHit.PdfUrls((refactorValue18)))

  private def arxivIdFromUrl(url: com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url): Option[com.histmersion.upstreams.ArxivId] =
    if !(url).value.toLowerCase.contains("arxiv.org") then None
    else Identifiers.arxiv(_root_.com.histmersion.upstreams.identifiers.Identifiers.ArxivText((url).value.split("/").lastOption.getOrElse("").stripSuffix(".pdf")))

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
        citedBy = w.citedByCount.map(c => FollowupRow.CitedBy(c.value)),
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
    val title = strings(c, "title").headOption.map({ val refactorFunction4 = inlineMarkup; (refactorValue23) => refactorFunction4(((refactorValue23))) }).map({ val refactorFunction5 = gluedPageNumber; (refactorValue26) => refactorFunction5(((refactorValue26).value)) })
    val year = arr(c, "issued", "date-parts").headOption
      .flatMap(_.asArray)
      .flatMap(_.headOption)
      .flatMap(_.asNumber)
      .flatMap(_.toInt)
    WorkRef(
      doi = str(c, "DOI").flatMap(doiOf),
      title = title,
      year = year.map(com.histmersion.plugin.Year(_)),
      authors = authors.map(com.histmersion.plugin.WorkKey.Authors(_)),
      venue = strings(c, "container-title").headOption.map(WorkRef.Venue(_)),
      citedByCount = int(c, "is-referenced-by-count").map(WorkRef.CitedByCount(_)),
      workType = str(c, "type").map(WorkRef.WorkType(_)),
      abstractText = str(c, "abstract").map(a => WorkRef.AbstractText(stripTags(a)))
    )

  /** Titles carry inline markup (`<i>c</i>. 1200 BC`, `H<sub>2</sub>O`): drop the tags WITHOUT a space — they sit
    * inside words — and collapse what is left.
    */
  private[upstreams] def inlineMarkup(s: String): com.histmersion.plugin.WorkKey.Title =
    _root_.com.histmersion.plugin.WorkKey.Title((s).replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim)

  /** Some publishers deposit a chapter title with its first page glued on: "109Writing and Religion in …". Digits
    * running straight into a capitalised word are that page; "1177 B.C." (a space) and "3.2 ka" are left alone.
    */
  private[upstreams] def gluedPageNumber(s: String): com.histmersion.plugin.WorkKey.Title =
    _root_.com.histmersion.plugin.WorkKey.Title((s).replaceFirst("^\\d{1,4}(?=\\p{Lu}\\p{Ll})", ""))

  /** A Crossref `works?query=` page → the works on it (the search fallback when OpenAlex is down). */
  def crossrefWorks(json: Json): List[WorkRef] =
    json.hcursor.downField("message").downField("items").values.toList.flatten.map(crossrefWork)

  /** Crossref abstracts are JATS XML fragments. */
  private def stripTags(s: String): String =
    ((s).replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim)

  /** The deposited `reference[]` array — structured, often carrying DOIs, and free of tokens. */
  def crossrefReferences(json: Json): List[RefRow] =
    val m = json.hcursor.downField("message")
    val c = if m.succeeded then m else json.hcursor
    arr(c, "reference").map { r =>
      val h = r.hcursor
      RefRow(
        title = str(h, "article-title").orElse(str(h, "volume-title")).map(RefRow.Title(_)),
        authors = str(h, "author").toList.map(RefRow.Authors(_)),
        year = str(h, "year").filter((_).forall(_.isDigit)).map(y => com.histmersion.plugin.Year(y.toInt)),
        doi = str(h, "DOI").flatMap(doiOf),
        raw = str(h, "unstructured").map(u => RefRow.Raw(u.take(300)))
      )
    }

  /** `update-to`: the retraction / correction / expression-of-concern notices attached to a DOI. */
  def crossrefUpdates(json: Json): List[UpdateNotice] =
    val m = json.hcursor.downField("message")
    val c = if m.succeeded then m else json.hcursor
    arr(c, "update-to").map { u =>
      val h = u.hcursor
      UpdateNotice(
        str(h, "type").map(UpdateNotice.Kind(_)),
        str(h, "label").map(UpdateNotice.Label(_)),
        str(h, "DOI").flatMap(doiOf)
      )
    }

  // ── Unpaywall / CORE ────────────────────────────────────────────────────────────────────────

  /** `(pdf urls best-first, oa_status)`. Unpaywall's `best_oa_location` first, then every other location it lists.
    */
  def unpaywall(json: Json): (List[com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url], Option[com.histmersion.upstreams.unpaywall.OaAccess.Status]) =
    val c = json.hcursor
    val best = str(c.downField("best_oa_location"), "url_for_pdf").toList
    val rest = arr(c, "oa_locations").flatMap(l => str(l.hcursor, "url_for_pdf"))
    (((best ++ rest).distinct).map(refactorValue32 => _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url((refactorValue32))), (str(c, "oa_status")).map(refactorValue33 => _root_.com.histmersion.upstreams.unpaywall.OaAccess.Status((refactorValue33))))

  /** Unpaywall's verdict on one DOI: whether a free copy exists, its `oa_status` (`gold`, `green`, …, `closed`) and the
    * best location — the PDF when there is one, else its landing page.
    */
  def unpaywallAccess(json: Json): OaAccess =
    val c = json.hcursor
    val best = c.downField("best_oa_location")
    OaAccess(
      OaAccess.IsOa(bool(c, "is_oa")),
      str(c, "oa_status").map(OaAccess.Status(_)),
      str(best, "url_for_pdf").orElse(str(best, "url")).orElse(str(best, "url_for_landing_page")).map(OaAccess.Url(_))
    )

  // ── Europe PMC / PMC Article Datasets ───────────────────────────────────────────────────────

  /** Europe PMC availability codes that mean a reader may download the copy without an account. */
  val EuropePmcFree: Set[AvailabilityCode] = Set(AvailabilityCode("OA"), AvailabilityCode("F"))

  /** The `search?query=DOI:"…"&resultType=core` hit whose DOI IS the one asked for (a DOI query can match a correction
    * or a comment that cites it).
    */
  def europepmcHit(json: Json, doi: com.histmersion.plugin.Doi): Option[EuropePmcHit] =
    val want = doiOf(doi.value)
    arr(json.hcursor, "resultList", "result")
      .find(r => want.isDefined && str(r.hcursor, "doi").flatMap(doiOf) == want)
      .map { r =>
        val c = r.hcursor
        val pdfs = arr(c, "fullTextUrlList", "fullTextUrl").flatMap { u =>
          val h = u.hcursor
          val isPdf = str(h, "documentStyle").exists((_).equalsIgnoreCase("pdf"))
          val free = str(h, "availabilityCode").exists(a => EuropePmcFree(AvailabilityCode((a).toUpperCase)))
          if isPdf && free then str(h, "url").filter((_).startsWith("http")) else None
        }.distinct.map(EuropePmcHit.PdfUrls(_))
        EuropePmcHit(
          (str(c, "pmcid").map((_).toUpperCase).filter(PmcId.matches)).map(refactorValue31 => EuropePmcHit.Pmcid(refactorValue31)),
          _root_.com.histmersion.upstreams.europepmc.EuropePmcHit.IsOpenAccess(str(c, "isOpenAccess").contains("Y")),
          pdfs
        )
      }

  private val PmcId = """PMC\d+""".r

  /** The newest version NCBI publishes of `pmcid`, from an S3 `ListObjectsV2` answer for the prefix `metadata/<pmcid>.`
    * (keys `metadata/PMC123.1.json`, `metadata/PMC123.2.json`, …).
    */
  def pmcLatestVersion(listXml: com.histmersion.upstreams.jats.Jats.Xml, pmcid: com.histmersion.plugin.WorkKey.Pmcid): Option[com.histmersion.upstreams.parse.AcquireParse.PmcVersion] =
    if !PmcId.matches((pmcid).value) then None
    else
      val key = ("<Key>metadata/" + pmcid + """\.(\d+)\.json</Key>""").r
      (key.findAllMatchIn((listXml).value).flatMap(m => m.group(1).toIntOption).maxOption).map(refactorValue37 => _root_.com.histmersion.upstreams.parse.AcquireParse.PmcVersion(refactorValue37))

  /** A dataset metadata record (`metadata/<pmcid>.<v>.json`). */
  def pmcRecord(json: Json): Option[PmcRecord] =
    val c = json.hcursor
    str(c, "pmcid").filter((PmcId).matches).map { id =>
      PmcRecord(
        PmcRecord.Pmcid(id),
        PmcRecord.OpenAccess(bool(c, "is_pmc_openaccess")),
        PmcRecord.Manuscript(bool(c, "is_manuscript")),
        str(c, "license_code").map(PmcRecord.License(_)),
        str(c, "pdf_url").flatMap({ val refactorFunction8 = pmcObject; (refactorValue39) => refactorFunction8(_root_.com.histmersion.upstreams.pmc.PmcObject.Key((refactorValue39))) }),
        str(c, "xml_url").flatMap({ val refactorFunction9 = pmcObject; (refactorValue40) => refactorFunction9(_root_.com.histmersion.upstreams.pmc.PmcObject.Key((refactorValue40))) })
      )
    }

  /** `s3://pmc-oa-opendata/PMC1.1/PMC1.1.pdf?md5=…` → the key and the MD5. Only the shapes the dataset uses are
    * accepted, so a record can never point a download outside an article's own folder.
    */
  def pmcObject(s3: com.histmersion.upstreams.pmc.PmcObject.Key): Option[PmcObject] =
    S3Url.findFirstMatchIn((s3).value.trim).map(m => PmcObject(_root_.com.histmersion.upstreams.pmc.PmcObject.Key(m.group(1)), Option(m.group(2)).map(md5 => _root_.com.histmersion.upstreams.pmc.PmcObject.Md5(md5.toLowerCase))))

  private val S3Url = """^s3://[a-z0-9.-]+/(PMC\d+\.\d+/[A-Za-z0-9_-][A-Za-z0-9._-]*)(?:\?md5=([0-9a-fA-F]{32}))?$""".r

  def coreDownloadUrls(json: Json): List[com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url] =
    arr(json.hcursor, "results").flatMap(r => str(r.hcursor, "downloadUrl").map(com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url(_)))

  // ── Semantic Scholar ────────────────────────────────────────────────────────────────────────

  /** One page of `/paper/DOI:{doi}/citations`. Each row carries the citing paper plus the citation's `intents`,
    * `isInfluential` flag and `contexts` — the signals the host classifies a citation by.
    */
  def s2Citations(json: Json): List[FollowupRow] =
    arr(json.hcursor, "data").map { d =>
      val h = d.hcursor
      val cp = h.downField("citingPaper")
      FollowupRow(
        title = str(cp, "title").map(FollowupRow.Title(_)),
        year = int(cp, "year").map(com.histmersion.plugin.Year(_)),
        doi = str(cp, "externalIds", "DOI").flatMap(doiOf),
        arxiv = str(cp, "externalIds", "ArXiv").flatMap(arxivOf),
        citedBy = int(cp, "citationCount").map(FollowupRow.CitedBy(_)),
        isInfluential = FollowupRow.IsInfluential(bool(h, "isInfluential")),
        intents = strings(h, "intents").map(FollowupRow.Intents(_)),
        contexts = (strings(h, "contexts").map((_).replaceAll("\\s+", " ").take(400)).take(3)).map(refactorValue41 => _root_.com.histmersion.domain.library.acquisition.FollowupRow.Contexts(refactorValue41))
      )
    }

  /** `next` offset of an S2 page, absent on the last page. */
  def s2Next(json: Json): Option[Offset] = int(json.hcursor, "next").map(Offset(_))

  // ── archive.org ─────────────────────────────────────────────────────────────────────────────

  /** `/metadata/{id}` → the item, including the lending restriction that stops an import dead.
    *
    * `access-restricted-item = true` (or `is_dark`) means the scan is LENDING-ONLY: it may be borrowed on archive.org
    * but never downloaded here. The Python tool refused at exactly this point and so do we — there is no DRM
    * circumvention anywhere in this pipeline.
    */
  def archiveItem(identifier: com.histmersion.upstreams.ArchiveId, json: Json): ArchiveItem =
    val c = json.hcursor
    val m = c.downField("metadata")
    val yr = str(m, "year").orElse(str(m, "date")).map((_).take(4)).filter(_.forall(_.isDigit)).map(_.toInt)
    val creators =
      (str(m, "creator").toList ++ strings(m, "creator")).distinct.filterNot(_.startsWith("http"))
    val files = arr(c, "files").flatMap { f =>
      val h = f.hcursor
      val name = str(h, "name").getOrElse("")
      val ext = name.toLowerCase.split("\\.").lastOption.getOrElse("")
      Option.when(ext == "pdf" || ext == "epub") {
        val size = str(h, "size").flatMap((_).toLongOption).getOrElse(0L)
        ArchiveFile(ArchiveFile.Name(name), str(h, "format").map(ArchiveFile.Format(_)), _root_.com.histmersion.upstreams.archiveorg.ArchiveFile.Megabytes(math.round(size / 1e5) / 10.0))
      }
    }
    ArchiveItem(
      identifier = identifier,
      title = str(m, "title").map(ArchiveItem.Title(_)),
      creator = creators.map(ArchiveItem.Creator(_)),
      year = (yr).map(refactorValue46 => _root_.com.histmersion.plugin.Year(refactorValue46)),
      publisher = str(m, "publisher").map(ArchiveItem.Publisher(_)),
      description = str(m, "description").map({ val refactorFunction11 = stripTags; (refactorValue55) => refactorFunction11(((refactorValue55))) }).map((refactorCallback => _root_.com.histmersion.upstreams.archiveorg.ArchiveItem.Description((refactorCallback).take(500)))),
      lendingOnly = _root_.com.histmersion.upstreams.archiveorg.ArchiveItem.LendingOnly(
        str(m, "access-restricted-item").exists((_).equalsIgnoreCase("true")) || (bool(c, "is_dark"))
      ),
      files = files
    )

  /** `advancedsearch.php` docs → identifiers with the little metadata the index carries. */
  def archiveSearch(json: Json): List[ArchiveItem] =
    arr(json.hcursor, "response", "docs").flatMap { d =>
      val h = d.hcursor
      str(h, "identifier").map { id =>
        ArchiveItem(
          identifier = ArchiveItem.Identifier(id),
          title = str(h, "title").map(ArchiveItem.Title(_)),
          creator = (str(h, "creator").toList ++ strings(h, "creator")).distinct.map(ArchiveItem.Creator(_)),
          year = str(h, "year").map((_).take(4)).filter(_.forall(_.isDigit)).map(y => _root_.com.histmersion.plugin.Year(y.toInt))
        )
      }
    }

  /** Pick the best downloadable file: a clean text format (EPUB, or the OCR'd `_text.pdf`) beats the giant raw image
    * scan, unless the caller asks for a specific format. Returns the file name.
    */
  def pickArchiveFile(files: List[ArchiveFile], want: Option[com.histmersion.upstreams.parse.AcquireParse.WantedFileKind]): Option[ArchiveFile] =
    def ext(f: ArchiveFile) = f.name.value.toLowerCase.split("\\.").lastOption.getOrElse("")
    val epubs = files.filter(ext(_) == "epub")
    val textPdfs = files.filter(_.name.value.toLowerCase.endsWith("_text.pdf"))
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
  def wikiTitle(json: Json): Option[com.histmersion.plugin.WorkKey.Title] = (str(json.hcursor.downField("parse"), "title")).map(refactorValue58 => _root_.com.histmersion.plugin.WorkKey.Title((refactorValue58)))

  def wikiText(json: Json): Option[com.histmersion.upstreams.parse.AcquireParse.Wikitext] =
    (json.hcursor.downField("parse").downField("wikitext").downField("*").as[String].toOption).map(refactorValue48 => _root_.com.histmersion.upstreams.parse.AcquireParse.Wikitext(refactorValue48))

  /** Harvest every DOI and arXiv id cited by a Wikipedia article's wikitext. Citations trail punctuation (`|`, `}}`,
    * `.`) into the match, so each hit goes through [[Identifiers.doi]].
    */
  def wikiRefs(wikitext: com.histmersion.upstreams.parse.AcquireParse.Wikitext): (List[com.histmersion.plugin.Doi], List[com.histmersion.upstreams.ArxivId]) =
    val dois = DoiInText.findAllIn((wikitext).value).toList.flatMap(doiOf).distinct.sortBy(_.value)
    val arxiv = ArxivInText.findAllMatchIn((wikitext).value).map(_.group(1)).toList.distinct.sorted
    (dois, arxiv.map(com.histmersion.upstreams.ArxivId(_)))

  /** The Wikipedia page title of an article URL (`…/wiki/Late_Bronze_Age_collapse`). */
  def wikiPageTitle(url: com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url): Option[com.histmersion.plugin.WorkKey.Title] =
    val s = (url).value.trim.stripSuffix("/")
    (Option(if s.contains("/wiki/") then s.split("/wiki/").last else s)
      .map(_.split("[?#]").head)
      .map(_.trim)
      .filter(_.nonEmpty)).map(refactorValue52 => _root_.com.histmersion.plugin.WorkKey.Title(refactorValue52))
