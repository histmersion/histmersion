package com.histmersion
package upstreams.parse
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import com.histmersion.util.json.Cursor
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.domain.library.acquisition.{FollowupRow, RefRow, UpdateNotice, WorkRef}
import com.histmersion.upstreams.archiveorg.{ArchiveFile, ArchiveItem}
import com.histmersion.upstreams.europepmc.EuropePmcHit
import com.histmersion.upstreams.identifiers.Identifiers
import com.histmersion.upstreams.pmc.{PmcObject, PmcRecord}
import com.histmersion.upstreams.unpaywall.OaAccess
import com.histmersion.upstreams.Offset
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.plugin.Year
import com.histmersion.upstreams.landing.RepositoryLanding
import com.histmersion.plugin.WorkKey
import com.histmersion.upstreams.ArxivId
import com.histmersion.plugin.Doi
import com.histmersion.upstreams.jats.Jats
import com.histmersion.upstreams.ArchiveId
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** PURE parsers for the JSON the acquisition upstreams return (OpenAlex, Crossref, Unpaywall, CORE, Semantic Scholar,
  * Europe PMC, PMC Article Datasets, archive.org, Wikipedia).
  *
  * Keeping them apart from the HTTP clients is what makes the toolset testable from fixtures: a test feeds real
  * captured JSON straight into these functions with no network involved. Everything is total: a missing or unexpected
  * field yields `None`/`Nil`, never an exception, because upstreams do change shape.
  */
@atBoundary(BoundaryKind.Json, "reads Crossref, OpenAlex, Unpaywall and arXiv answers into work records")
object AcquireParse:
  type PmcVersion = PmcVersion.T
  object PmcVersion extends Newtype[Int, Pure]
  type WantedFileKind = WantedFileKind.T
  object WantedFileKind extends Newtype[String, Pure]
  type Wikitext = Wikitext.T
  object Wikitext extends Newtype[String, Pure]

  // ── small cursor helpers ────────────────────────────────────────────────────────────────────
  type AvailabilityCode = AvailabilityCode.T
  object AvailabilityCode extends Newtype[String, Pure]

  extension (c: Cursor)
    private def str(keys: String*): Option[String] = c.down(keys*).filled
    private def number(keys: String*): Option[Int] = c.down(keys*).as[Int].toOption
    private def flag(keys: String*): Boolean = c.down(keys*).focus.flatMap(_.asBoolean).getOrElse(false)
    private def arr(keys: String*): List[Json] = c.down(keys*).elements
    private def strs(keys: String*): List[String] =
      c.down(keys*).elements.flatMap(_.asString).map(_.trim).filter(_.nonEmpty)

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
      .filter(_.nonEmpty))
      .map(refactorValue4 => WorkRef.AbstractText(refactorValue4))

  /** One OpenAlex `work` → the normalised [[WorkRef]] (the port of `discover.py::normalize_work`). */
  def openalexWork(json: Json): WorkRef =
    val c = json.cursor
    val authors = c.arr("authorships").flatMap(a => a.cursor.str("author", "display_name"))
    val doi = c.str("doi").flatMap(doiOf)
    val title = c.str("display_name").map {
      val refactorFunction2 = inlineMarkup; (refactorValue6) => refactorFunction2(((refactorValue6)))
    }
    val year = c.number("publication_year").map(Year(_))
    WorkRef(
      doi = doi,
      openalexId = c.str("id").map(id => WorkRef.OpenalexId(tail(id))),
      // the arXiv preprint of a paywalled paper is usually the best OA location, so look there too
      arxivId = (c.downField("best_oa_location").str("landing_page_url").toList ++
        c.str("open_access", "oa_url").toList ++
        c.arr("locations").flatMap(l => l.cursor.str("landing_page_url"))).flatMap {
        val refactorFunction3 = arxivIdFromUrl;
        (refactorValue7) => refactorFunction3(RepositoryLanding.Candidate.Url((refactorValue7)))
      }.headOption,
      title = title,
      year = year,
      authors = authors.map(WorkKey.Authors(_)),
      venue = c.str("primary_location", "source", "display_name").map(WorkRef.Venue(_)),
      citedByCount = c.number("cited_by_count").map(WorkRef.CitedByCount(_)),
      isOa = WorkRef.IsOa(c.flag("open_access", "is_oa")),
      oaStatus = c.str("open_access", "oa_status").map(WorkRef.OaStatus(_)),
      oaUrl = c.str("open_access", "oa_url").map(WorkRef.OaUrl(_)),
      isRetracted = WorkRef.IsRetracted(c.flag("is_retracted")),
      workType = c.str("type").map(WorkRef.WorkType(_)),
      abstractText = c.downField("abstract_inverted_index").focus.flatMap(openalexAbstract),
      referencedWorks = c.strs("referenced_works").map(w => WorkRef.ReferencedWorks(tail(w))),
      oaLocations = c.openalexOaLocations,
      oaLicence = c
        .downField("best_oa_location")
        .str("license")
        .orElse(
          c.arr("locations")
            .filter(l => ((l.cursor.flag("is_oa")) == (true)))
            .flatMap(l => l.cursor.str("license"))
            .headOption
        )
        .map(WorkRef.OaLicence(_))
    )

  /** The open locations' landing pages and PDFs, the best location first. */
  extension (c: Cursor)
    private def openalexOaLocations: List[WorkRef.OaLocations] =
      val best = c.downField("best_oa_location")
      val open = c.arr("locations").filter(l => l.cursor.flag("is_oa"))
      (List(best.str("landing_page_url"), best.str("pdf_url")).flatten ++
        open.flatMap(l => List(l.cursor.str("landing_page_url"), l.cursor.str("pdf_url")).flatten)).distinct
        .map(WorkRef.OaLocations(_))

  /** An OpenAlex `/works` search page. */
  def openalexWorks(json: Json): List[WorkRef] =
    json.cursor.arr("results").map(openalexWork)

  /** OA locations OpenAlex knows about, best first — catches green-OA repository PDFs (PMC, bioRxiv) that Unpaywall's
    * `best_oa_location` sometimes misses.
    */
  def openalexPdfUrls(json: Json): List[EuropePmcHit.PdfUrls] =
    val c = json.cursor
    val oa = c.str("open_access", "oa_url").toList
    val best = c.downField("best_oa_location").str("pdf_url").toList
    val rest = c.arr("locations").flatMap(l => l.cursor.str("pdf_url"))
    ((best ++ oa ++ rest).distinct).map(refactorValue18 => EuropePmcHit.PdfUrls((refactorValue18)))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def arxivIdFromUrl(url: RepositoryLanding.Candidate.Url): Option[ArxivId] =
    if !(url).value.toLowerCase.contains("arxiv.org") then None
    else Identifiers.arxiv(Identifiers.ArxivText((url).value.split("/").lastOption.getOrElse("").stripSuffix(".pdf")))

  /** Citing works from OpenAlex (`filter=cites:<id>`) — the fallback when Semantic Scholar is down or rate-limited.
    * OpenAlex has no citation intents, so every row has an empty context (the host classifies it as `cites`).
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def openalexCitations(json: Json): List[FollowupRow] =
    openalexWorks(json).map { w =>
      FollowupRow(
        title = w.title,
        year = w.year,
        doi = w.doi,
        arxiv = w.arxivId,
        citedBy = w.citedByCount.map(c => FollowupRow.CitedBy(c.value)),
        isInfluential = FollowupRow.IsInfluential(false),
        intents = Nil,
        contexts = Nil
      )
    }

  // ── Crossref ────────────────────────────────────────────────────────────────────────────────

  /** A Crossref `works/{doi}` message → [[WorkRef]] (the metadata fallback when OpenAlex misses). */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def crossrefWork(json: Json): WorkRef =
    val m = json.cursor.downField("message")
    val c = if m.succeeded then m else json.cursor
    val authors = c
      .arr("author")
      .map { a =>
        val h = a.cursor
        List(h.str("given"), h.str("family")).flatten.mkString(" ").trim
      }
      .filter(_.nonEmpty)
    val title = c
      .strs("title")
      .headOption
      .map { val refactorFunction4 = inlineMarkup; (refactorValue23) => refactorFunction4(((refactorValue23))) }
      .map {
        val refactorFunction5 = gluedPageNumber; (refactorValue26) => refactorFunction5(((refactorValue26).value))
      }
    val year = c
      .arr("issued", "date-parts")
      .headOption
      .flatMap(_.asArray)
      .flatMap(_.headOption)
      .flatMap(_.asNumber)
      .flatMap(_.toInt)
    WorkRef(
      doi = c.str("DOI").flatMap(doiOf),
      title = title,
      year = year.map(Year(_)),
      authors = authors.map(WorkKey.Authors(_)),
      venue = c.strs("container-title").headOption.map(WorkRef.Venue(_)),
      citedByCount = c.number("is-referenced-by-count").map(WorkRef.CitedByCount(_)),
      workType = c.str("type").map(WorkRef.WorkType(_)),
      abstractText = c.str("abstract").map(a => WorkRef.AbstractText(stripTags(a)))
    )

  /** Titles carry inline markup (`<i>c</i>. 1200 BC`, `H<sub>2</sub>O`): drop the tags WITHOUT a space — they sit
    * inside words — and collapse what is left.
    */
  private[upstreams] def inlineMarkup(s: String): WorkKey.Title =
    WorkKey.Title((s).replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim)

  /** Some publishers deposit a chapter title with its first page glued on: "109Writing and Religion in …". Digits
    * running straight into a capitalised word are that page; "1177 B.C." (a space) and "3.2 ka" are left alone.
    */
  private[upstreams] def gluedPageNumber(s: String): WorkKey.Title =
    WorkKey.Title((s).replaceFirst("^\\d{1,4}(?=\\p{Lu}\\p{Ll})", ""))

  /** A Crossref `works?query=` page → the works on it (the search fallback when OpenAlex is down). */
  def crossrefWorks(json: Json): List[WorkRef] =
    json.cursor.downField("message").downField("items").values.toList.flatten.map(crossrefWork)

  /** Crossref abstracts are JATS XML fragments. */
  private def stripTags(s: String): String =
    ((s).replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim)

  /** The deposited `reference[]` array — structured, often carrying DOIs, and free of tokens. */
  def crossrefReferences(json: Json): List[RefRow] =
    val m = json.cursor.downField("message")
    val c = if m.succeeded then m else json.cursor
    c.arr("reference").map { r =>
      val h = r.cursor
      RefRow(
        title = h.str("article-title").orElse(h.str("volume-title")).map(RefRow.Title(_)),
        authors = h.str("author").toList.map(RefRow.Authors(_)),
        year = h.str("year").filter((_).forall(_.isDigit)).map(y => Year(y.toInt)),
        doi = h.str("DOI").flatMap(doiOf),
        raw = h.str("unstructured").map(u => RefRow.Raw(u.take(300)))
      )
    }

  /** `update-to`: the retraction / correction / expression-of-concern notices attached to a DOI. */
  def crossrefUpdates(json: Json): List[UpdateNotice] =
    val m = json.cursor.downField("message")
    val c = if m.succeeded then m else json.cursor
    c.arr("update-to").map { u =>
      val h = u.cursor
      UpdateNotice(
        h.str("type").map(UpdateNotice.Kind(_)),
        h.str("label").map(UpdateNotice.Label(_)),
        h.str("DOI").flatMap(doiOf)
      )
    }

  // ── Unpaywall / CORE ────────────────────────────────────────────────────────────────────────

  /** `(pdf urls best-first, oa_status)`. Unpaywall's `best_oa_location` first, then every other location it lists.
    */
  def unpaywall(json: Json): (List[RepositoryLanding.Candidate.Url], Option[OaAccess.Status]) =
    val c = json.cursor
    val best = c.downField("best_oa_location").str("url_for_pdf").toList
    val rest = c.arr("oa_locations").flatMap(l => l.cursor.str("url_for_pdf"))
    (
      ((best ++ rest).distinct).map(refactorValue32 => RepositoryLanding.Candidate.Url((refactorValue32))),
      (c.str("oa_status")).map(refactorValue33 => OaAccess.Status((refactorValue33)))
    )

  /** Unpaywall's verdict on one DOI: whether a free copy exists, its `oa_status` (`gold`, `green`, …, `closed`) and the
    * best location — the PDF when there is one, else its landing page.
    */
  def unpaywallAccess(json: Json): OaAccess =
    val c = json.cursor
    val best = c.downField("best_oa_location")
    OaAccess(
      OaAccess.IsOa(c.flag("is_oa")),
      c.str("oa_status").map(OaAccess.Status(_)),
      best.str("url_for_pdf").orElse(best.str("url")).orElse(best.str("url_for_landing_page")).map(OaAccess.Url(_))
    )

  // ── Europe PMC / PMC Article Datasets ───────────────────────────────────────────────────────

  /** Europe PMC availability codes that mean a reader may download the copy without an account. */
  val EuropePmcFree: Set[AvailabilityCode] = Set(AvailabilityCode("OA"), AvailabilityCode("F"))

  /** The `search?query=DOI:"…"&resultType=core` hit whose DOI IS the one asked for (a DOI query can match a correction
    * or a comment that cites it).
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def europepmcHit(json: Json, doi: Doi): Option[EuropePmcHit] =
    val want = doiOf(doi.value)
    json.cursor
      .arr("resultList", "result")
      .find(r => want.isDefined && r.cursor.str("doi").flatMap(doiOf) == want)
      .map { r =>
        val c = r.cursor
        val pdfs = c
          .arr("fullTextUrlList", "fullTextUrl")
          .flatMap { u =>
            val h = u.cursor
            val isPdf = h.str("documentStyle").exists((_).equalsIgnoreCase("pdf"))
            val free = h.str("availabilityCode").exists(a => EuropePmcFree(AvailabilityCode((a).toUpperCase)))
            if isPdf && free then h.str("url").filter((_).startsWith("http")) else None
          }
          .distinct
          .map(EuropePmcHit.PdfUrls(_))
        EuropePmcHit(
          (c.str("pmcid")
            .map((_).toUpperCase)
            .filter(PmcId.matches))
            .map(refactorValue31 => EuropePmcHit.Pmcid(refactorValue31)),
          EuropePmcHit.IsOpenAccess(c.str("isOpenAccess").contains("Y")),
          pdfs
        )
      }

  private val PmcId = """PMC\d+""".r

  /** The newest version NCBI publishes of `pmcid`, from an S3 `ListObjectsV2` answer for the prefix `metadata/<pmcid>.`
    * (keys `metadata/PMC123.1.json`, `metadata/PMC123.2.json`, …).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def pmcLatestVersion(listXml: Jats.Xml, pmcid: WorkKey.Pmcid): Option[PmcVersion] =
    if !PmcId.matches((pmcid).value) then None
    else
      val key = ("<Key>metadata/" + pmcid + """\.(\d+)\.json</Key>""").r
      (key
        .findAllMatchIn((listXml).value)
        .flatMap(m => m.group(1).toIntOption)
        .maxOption)
        .map(refactorValue37 => PmcVersion(refactorValue37))

  /** A dataset metadata record (`metadata/<pmcid>.<v>.json`). */
  def pmcRecord(json: Json): Option[PmcRecord] =
    val c = json.cursor
    c.str("pmcid").filter((PmcId).matches).map { id =>
      PmcRecord(
        PmcRecord.Pmcid(id),
        PmcRecord.OpenAccess(c.flag("is_pmc_openaccess")),
        PmcRecord.Manuscript(c.flag("is_manuscript")),
        c.str("license_code").map(PmcRecord.License(_)),
        c.str("pdf_url").flatMap {
          val refactorFunction8 = pmcObject; (refactorValue39) => refactorFunction8(PmcObject.Key((refactorValue39)))
        },
        c.str("xml_url").flatMap {
          val refactorFunction9 = pmcObject; (refactorValue40) => refactorFunction9(PmcObject.Key((refactorValue40)))
        }
      )
    }

  /** `s3://pmc-oa-opendata/PMC1.1/PMC1.1.pdf?md5=…` → the key and the MD5. Only the shapes the dataset uses are
    * accepted, so a record can never point a download outside an article's own folder.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def pmcObject(s3: PmcObject.Key): Option[PmcObject] =
    S3Url
      .findFirstMatchIn((s3).value.trim)
      .map(m => PmcObject(PmcObject.Key(m.group(1)), Option(m.group(2)).map(md5 => PmcObject.Md5(md5.toLowerCase))))

  private val S3Url = """^s3://[a-z0-9.-]+/(PMC\d+\.\d+/[A-Za-z0-9_-][A-Za-z0-9._-]*)(?:\?md5=([0-9a-fA-F]{32}))?$""".r

  def coreDownloadUrls(json: Json): List[RepositoryLanding.Candidate.Url] =
    json.cursor.arr("results").flatMap(r => r.cursor.str("downloadUrl").map(RepositoryLanding.Candidate.Url(_)))

  // ── Semantic Scholar ────────────────────────────────────────────────────────────────────────

  /** One page of `/paper/DOI:{doi}/citations`. Each row carries the citing paper plus the citation's `intents`,
    * `isInfluential` flag and `contexts` — the signals the host classifies a citation by.
    */
  def s2Citations(json: Json): List[FollowupRow] =
    json.cursor.arr("data").map { d =>
      val h = d.cursor
      val cp = h.downField("citingPaper")
      FollowupRow(
        title = cp.str("title").map(FollowupRow.Title(_)),
        year = cp.number("year").map(Year(_)),
        doi = cp.str("externalIds", "DOI").flatMap(doiOf),
        arxiv = cp.str("externalIds", "ArXiv").flatMap(arxivOf),
        citedBy = cp.number("citationCount").map(FollowupRow.CitedBy(_)),
        isInfluential = FollowupRow.IsInfluential(h.flag("isInfluential")),
        intents = h.strs("intents").map(FollowupRow.Intents(_)),
        contexts = (h
          .strs("contexts")
          .map((_).replaceAll("\\s+", " ").take(400))
          .take(3))
          .map(refactorValue41 => FollowupRow.Contexts(refactorValue41))
      )
    }

  /** `next` offset of an S2 page, absent on the last page. */
  def s2Next(json: Json): Option[Offset] = json.cursor.number("next").map(Offset(_))

  // ── archive.org ─────────────────────────────────────────────────────────────────────────────

  /** `/metadata/{id}` → the item, including the lending restriction that stops an import dead.
    *
    * `access-restricted-item = true` (or `is_dark`) means the scan is LENDING-ONLY: it may be borrowed on archive.org
    * but never downloaded here. The Python tool refused at exactly this point and so do we — there is no DRM
    * circumvention anywhere in this pipeline.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def archiveItem(identifier: ArchiveId, json: Json): ArchiveItem =
    val c = json.cursor
    val m = c.downField("metadata")
    val yr = m.str("year").orElse(m.str("date")).map((_).take(4)).filter(_.forall(_.isDigit)).map(_.toInt)
    val creators =
      (m.str("creator").toList ++ m.strs("creator")).distinct.filterNot(_.startsWith("http"))
    val files = c.arr("files").flatMap { f =>
      val h = f.cursor
      val name = h.str("name").getOrElse("")
      val ext = name.toLowerCase.split("\\.").lastOption.getOrElse("")
      Option.when(ext == "pdf" || ext == "epub") {
        val size = h.str("size").flatMap((_).toLongOption).getOrElse(0L)
        ArchiveFile(
          ArchiveFile.Name(name),
          h.str("format").map(ArchiveFile.Format(_)),
          ArchiveFile.Megabytes(math.round(size / 1e5) / 10.0)
        )
      }
    }
    ArchiveItem(
      identifier = identifier,
      title = m.str("title").map(ArchiveItem.Title(_)),
      creator = creators.map(ArchiveItem.Creator(_)),
      year = (yr).map(refactorValue46 => Year(refactorValue46)),
      publisher = m.str("publisher").map(ArchiveItem.Publisher(_)),
      description = m
        .str("description")
        .map { val refactorFunction11 = stripTags; (refactorValue55) => refactorFunction11(((refactorValue55))) }
        .map((refactorCallback => ArchiveItem.Description((refactorCallback).take(500)))),
      lendingOnly = ArchiveItem.LendingOnly(
        m.str("access-restricted-item").exists((_).equalsIgnoreCase("true")) || (c.flag("is_dark"))
      ),
      files = files
    )

  /** `advancedsearch.php` docs → identifiers with the little metadata the index carries. */
  def archiveSearch(json: Json): List[ArchiveItem] =
    json.cursor.arr("response", "docs").flatMap { d =>
      val h = d.cursor
      h.str("identifier").map { id =>
        ArchiveItem(
          identifier = ArchiveItem.Identifier(id),
          title = h.str("title").map(ArchiveItem.Title(_)),
          creator = (h.str("creator").toList ++ h.strs("creator")).distinct.map(ArchiveItem.Creator(_)),
          year = h.str("year").map((_).take(4)).filter(_.forall(_.isDigit)).map(y => Year(y.toInt))
        )
      }
    }

  /** Pick the best downloadable file: a clean text format (EPUB, or the OCR'd `_text.pdf`) beats the giant raw image
    * scan, unless the caller asks for a specific format. Returns the file name.
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def pickArchiveFile(files: List[ArchiveFile], want: Option[WantedFileKind]): Option[ArchiveFile] =
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
  def wikiTitle(json: Json): Option[WorkKey.Title] =
    (json.cursor.downField("parse").str("title")).map(refactorValue58 => WorkKey.Title((refactorValue58)))

  def wikiText(json: Json): Option[Wikitext] =
    (json.cursor
      .downField("parse")
      .downField("wikitext")
      .downField("*")
      .as[String]
      .toOption)
      .map(refactorValue48 => Wikitext(refactorValue48))

  /** Harvest every DOI and arXiv id cited by a Wikipedia article's wikitext. Citations trail punctuation (`|`, `}}`,
    * `.`) into the match, so each hit goes through [[Identifiers.doi]].
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def wikiRefs(wikitext: Wikitext): (List[Doi], List[ArxivId]) =
    val dois = DoiInText.findAllIn((wikitext).value).toList.flatMap(doiOf).distinct.sorted
    val arxiv = ArxivInText.findAllMatchIn((wikitext).value).map(_.group(1)).toList.distinct.sorted
    (dois, arxiv.map(ArxivId(_)))

  /** The Wikipedia page title of an article URL (`…/wiki/Late_Bronze_Age_collapse`). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def wikiPageTitle(url: RepositoryLanding.Candidate.Url): Option[WorkKey.Title] =
    val s = (url).value.trim.stripSuffix("/")
    (Option(if s.contains("/wiki/") then s.split("/wiki/").last else s)
      .map(_.split("[?#]").head)
      .map(_.trim)
      .filter(_.nonEmpty))
      .map(refactorValue52 => WorkKey.Title(refactorValue52))
