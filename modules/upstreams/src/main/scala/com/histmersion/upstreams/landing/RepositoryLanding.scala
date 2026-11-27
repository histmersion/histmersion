package com.histmersion
package upstreams.landing
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import com.histmersion.util.json.Cursor
import hearth.kindlings.jsoniterjson.Json
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.domain.library.acquisition.CoreEndpoint

/** Repository LANDING PAGES (#1580): an open-access location whose URL is an HTML record page rather than the PDF — a
  * Zenodo record, a DSpace/EPrints/Pure item, a DataCite DOI that resolves to one. The fetch resolves such a page ONE
  * step, and only in two ways a repository itself offers:
  *
  *   - **Zenodo**: its REST API (`/api/records/<id>`) names the record's files; an open record's PDF files are
  *     downloaded from `files[].links.self` and checked against the stated MD5;
  *   - **`citation_pdf_url`**: the `<meta name="citation_pdf_url">` an institutional repository puts on an item page
  *     for Google Scholar's inclusion guidelines — read only on a page that is a REPOSITORY ([[isRepository]]: an OA
  *     index says so, an allowed host, an academic host, or a repository platform's `generator` tag) and never on a
  *     publisher's site ([[Rules.denied]], which wins over every other signal; the PDF's own host is held to it too).
  *
  * An OPEN-ACCESS PUBLISHER's page (#2550) is the one exception to "never a publisher's page": a host on
  * `acquire.landing.oa-publisher-hosts` — publishers whose robots.txt and terms permit a script to download their
  * open-access PDFs, checked one by one — is read when an OA index names the location as OPEN ([[Candidate.open]]), and
  * gives its `citation_pdf_url`s and its OJS PDF galleys ([[galleyPdfUrls]]), on its own hosts only.
  *
  * A page behind bot protection or a 403 is no copy: the HTTP layer reports it as a failure, and the work goes to the
  * acquisition queue as before. Everything here is pure and total.
  */
@atBoundary(BoundaryKind.Json, "reads a repository landing page into download links")
object RepositoryLanding:
  type Host = Host.T
  object Host extends Newtype[String, Pure]
  type HostLabel = HostLabel.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object HostLabel extends Newtype[String, Pure]
  type HostWord = HostWord.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object HostWord extends Newtype[String, Pure]
  type HostPattern = HostPattern.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object HostPattern extends Newtype[String, Pure]
  type Href = Href.T
  object Href extends Newtype[String, Pure]
  type Html = Html.T
  @tolerated(LintRule.HtmlNewtype, "pending G96 (one markup type, api.Html)")
  object Html extends Newtype[String, Pure]
  type MetaContent = MetaContent.T
  object MetaContent extends Newtype[String, Pure]
  type MetaName = MetaName.T
  object MetaName extends Newtype[String, Pure]
  type PageBytes = PageBytes.T
  object PageBytes extends Newtype[Array[Byte], Pure]
  type ZenodoLocator = ZenodoLocator.T
  object ZenodoLocator extends Newtype[String, Pure]
  type ZenodoRecordId = ZenodoRecordId.T
  object ZenodoRecordId extends Newtype[String, Pure]

  /** A landing page to resolve; `repository` = an OA index (OpenAlex `source.type`, Unpaywall `host_type`) says the
    * location is a repository, `publisher` = it says a journal's or a publisher's (such a page is never read, unless it
    * is an open-access publisher's — [[oaPublisherPage]]); `open` = the index says the location is open access
    * (OpenAlex `is_oa`; every Unpaywall `oa_location` is).
    */

  type PublisherSourceType = PublisherSourceType.T
  object PublisherSourceType extends Newtype[String, Pure]
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type OnHostUrl = HostUrl
  val OnHostUrl: HostUrl.type = HostUrl
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type HostUrl = Candidate.Url
  val HostUrl: Candidate.Url.type = Candidate.Url
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type MetaTagsHtml = Html
  val MetaTagsHtml: Html.type = Html
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type GeneratorsHtml = Html
  val GeneratorsHtml: Html.type = Html
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type CitationPdfUrlsHtml = Html
  val CitationPdfUrlsHtml: Html.type = Html
  export Candidate.{Url as CitationPdfUrlsPageUrl, Url as IsRepositoryPageUrl}
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type IsRepositoryHtml = Html
  val IsRepositoryHtml: Html.type = Html
  export Candidate.{Repository as PdfsOfIndexSaysRepository, Url as PdfsOfPageUrl}
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type PdfsOfHtml = Html
  val PdfsOfHtml: Html.type = Html
  export Candidate.Url as GalleyPdfUrlsPageUrl

  final case class Candidate(
      url: Candidate.Url,
      repository: Candidate.Repository,
      publisher: Candidate.Publisher = Candidate.Publisher(false),
      open: Candidate.Open = Candidate.Open(false)
  )

  object Candidate:
    export CoreEndpoint.Url
    type Repository = Repository.T
    object Repository extends Newtype[Boolean, Pure]
    type Publisher = Publisher.T
    object Publisher extends Newtype[Boolean, Pure]
    type Open = Open.T
    object Open extends Newtype[Boolean, Pure]

  /** OpenAlex `source.type`s that are a publisher's, not a repository's. */
  val PublisherSourceTypes: Set[PublisherSourceType] = Set(
    PublisherSourceType("journal"),
    PublisherSourceType("conference"),
    PublisherSourceType("ebook platform"),
    PublisherSourceType("book series")
  )

  /** One file of a Zenodo record: where to download it and its MD5, when the record states one. */
  final case class ZenodoFile(url: ZenodoFile.Url, md5: Option[ZenodoFile.Md5])

  object ZenodoFile:
    export CoreEndpoint.Url
    export com.histmersion.upstreams.identifiers.Md5

  /** The hosts and platforms the decision reads (`acquire.landing.*`, comma-separated in config). A host entry matches
    * the host itself or any subdomain of it.
    */
  final case class Rules(
      allowHosts: List[HostPattern],
      denyHosts: List[HostPattern],
      academicHostWords: List[HostWord],
      generators: List[Rules.Generators],
      denyGenerators: List[Rules.DenyGenerators] = Nil,
      denyHostWords: List[HostWord] = Nil,
      oaPublisherHosts: List[HostPattern] = Nil
  ):
    def allowed(host: Host): Boolean = allowHosts.exists(d => matchesHost(host, d))

    /** An open-access publisher whose open locations may be read (`oa-publisher-hosts`, #2550). */
    def oaPublisher(host: Host): Boolean = oaPublisherHosts.exists(d => matchesHost(host, d))

    /** A publisher's host: a denied host or its subdomain, or — unless the host is allowed — a label that is or starts
      * with a denied word (`press.…`, `journals.…`: a university press on a `.edu`).
      */
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def denied(host: Host): Boolean =
      denyHosts.exists(d => matchesHost(host, d)) ||
        (!allowed(host) && host.value
          .split('.')
          .toList
          .dropRight(1)
          .exists(l => denyHostWords.exists(w => wordLabel(HostLabel(l), w))))

    /** A university's host: `.edu` (`.edu.au` too), `.ac.<cc>`, or a label that is a configured word ([[wordLabel]]:
      * `repository`, `eprints`, `pure`, `opus4`, `uni-tuebingen` for `uni-`).
      */
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def academic(host: Host): Boolean =
      val labels = (host).value.split('.').toList
      labels.contains("edu") || labels.dropRight(1).lastOption.contains("ac") ||
      labels.dropRight(1).exists(l => academicHostWords.exists(w => wordLabel(HostLabel(l), w)))

    /** A platform `generator` tag that names a repository (`DSpace 6.3`) — and not a journal's. */
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def repositoryGenerator(gs: List[Rules.Generators]): Boolean =
      val low = gs.map((_).value.toLowerCase)
      !low.exists(g => denyGenerators.exists(d => (d).value.nonEmpty && g.contains(d.value))) &&
      low.exists(g => generators.exists(p => (p).value.nonEmpty && g.contains(p.value)))

    /** A journal platform's page (`Open Journal Systems`): a publisher's, whatever its host. */
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def journalGenerator(gs: List[Rules.Generators]): Boolean =
      (gs.map((_).value.toLowerCase).exists(g => denyGenerators.exists(d => (d).value.nonEmpty && g.contains(d.value))))

  object Rules:

    type Generators = Generators.T
    object Generators extends Newtype[String, Pure]
    type DenyGenerators = DenyGenerators.T
    object DenyGenerators extends Newtype[String, Pure]

  /** Pure: is the host label `label` the word `w` — the word itself, the word then a digit or a hyphen (`opus4`,
    * `pure-portal`), or, for a word ending in `-`, any label it starts (`uni-` → `uni-koeln`)? Never a longer word
    * (`pure` is not `purepharma`, `press` is not `pressreader`).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def wordLabel(label: HostLabel, w: HostWord): Boolean =
    val (l, word) = (label.value, w.value)
    word.nonEmpty && (l == word || (word.endsWith("-") && l.startsWith(word)) ||
      (l.startsWith(word) && l.length > word.length && { val c = l.charAt(word.length); c == '-' || c.isDigit }))

  /** A host entry matches the host itself or any subdomain of it. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def matchesHost(host: Host, entry: HostPattern): Boolean =
    val e = (entry).value.trim.toLowerCase.stripPrefix(".")
    (e.nonEmpty && (host == Host(e) || (host).value.endsWith("." + e)))

  /** Pure: the lower-case host of an absolute http(s) URL. */
  /** The host of `url` when it is one of `hosts` or a subdomain of one. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def onHost(url: OnHostUrl, hosts: List[HostPattern]): Option[Host] =
    host(url).filter(h => hosts.exists(matchesHost(h, _)))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def host(url: HostUrl): Option[Host] =
    "(?i)^https?://([^/?#:]+)".r
      .findFirstMatchIn((url).value.trim)
      .map(m => Host(m.group(1).toLowerCase.stripPrefix("www.")))

  // ── which pages ────────────────────────────────────────────────────────────────────────────

  extension (c: Cursor)
    private def str(keys: String*): Option[String] = c.down(keys*).filled
    private def arr(keys: String*): List[Json] = c.down(keys*).elements

  private def notPdfPath(u: String) = !(u).toLowerCase.split("[?#]").head.endsWith(".pdf")

  /** Pure: the landing pages an OpenAlex work names — each location's landing page, and a `pdf_url` that is a record
    * page (no `.pdf`: the direct step already tried it as a file) — best location first, repositories marked.
    */
  def openalexCandidates(json: Json): List[Candidate] =
    val c = json.cursor
    val locs = c.downField("best_oa_location").focus.filterNot(_.isNull).toList ++ c.arr("locations")
    distinct(locs.flatMap { l =>
      val h = l.cursor
      val kind = h.str("source", "type").map((_).toLowerCase)
      val repo = kind.contains("repository")
      val pub = kind.exists(k => PublisherSourceTypes.contains(PublisherSourceType(k)))
      val open = h.downField("is_oa").as[Boolean].contains(true)
      (h.str("pdf_url").filter(((notPdfPath))).toList ++ h.str("landing_page_url").toList)
        .map(u =>
          Candidate(Candidate.Url(u), Candidate.Repository(repo), Candidate.Publisher(pub), Candidate.Open(open))
        )
    })

  /** Pure: the landing pages of Unpaywall's OA locations (`host_type: repository` marked). */
  def unpaywallCandidates(json: Json): List[Candidate] =
    val c = json.cursor
    val locs = c.downField("best_oa_location").focus.filterNot(_.isNull).toList ++ c.arr("oa_locations")
    distinct(locs.flatMap { l =>
      val h = l.cursor
      val repo = h.str("host_type").contains("repository")
      val pub = h.str("host_type").contains("publisher")
      (h.str("url_for_pdf").filter(((notPdfPath))).toList ++ h.str("url_for_landing_page").toList)
        .map(u =>
          Candidate(Candidate.Url(u), Candidate.Repository(repo), Candidate.Publisher(pub), open = Candidate.Open(true))
        )
    })

  /** Pure: where a DataCite DOI resolves (`data.attributes.url` of `api.datacite.org/dois/<doi>`). */
  def dataciteUrl(json: Json): Option[Candidate.Url] =
    (json.cursor.str("data", "attributes", "url")).map(refactorValue2 => Candidate.Url((refactorValue2)))

  /** Candidates by URL, the first of each kept; repository if any index says so, publisher only if one says so and none
    * says repository, open if any says so.
    */
  def distinct(cs: List[Candidate]): List[Candidate] =
    cs.filter(c => host(c.url).isDefined)
      .groupBy(_.url)
      .toList
      .map { (u, xs) =>
        val repo = xs.exists((refactorCallback => ((refactorCallback.repository) == Candidate.Repository(true))))
        (
          cs.indexWhere(_.url == u),
          Candidate(
            u,
            Candidate.Repository(repo),
            Candidate.Publisher(
              !repo && xs.exists((refactorCallback => ((refactorCallback.publisher) == Candidate.Publisher(true))))
            ),
            Candidate.Open(xs.exists((refactorCallback => ((refactorCallback.open) == Candidate.Open(true)))))
          )
        )
      }
      .sortBy(_._1)
      .map(_._2)

  /** Pure: is this an open-access publisher's OPEN location (#2550) — read although it is a publisher's: an index says
    * it is open, and its host is on `oa-publisher-hosts`.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def oaPublisherPage(c: Candidate, rules: Rules): Boolean =
    ((c.open) == Candidate.Open(true)) && host(c.url).exists(rules.oaPublisher)

  /** Pure: where the DOI handle API (`doi.org/api/handles/<doi>?type=URL`) says a DOI resolves — its first URL value.
    * Read only for an open location on an OA publisher, so a `doi.org` link is still never followed blind.
    */
  def handleUrl(json: Json): Option[Candidate.Url] =
    (json.cursor
      .arr("values")
      .iterator
      .map(_.cursor)
      .filter(v => v.str("type").contains("URL"))
      .flatMap(v => v.str("data", "value"))
      .nextOption())
      .map(refactorValue3 => Candidate.Url((refactorValue3)))

  // ── Zenodo ─────────────────────────────────────────────────────────────────────────────────

  private val ZenodoRecord = "(?i)^https?://(?:www\\.)?zenodo\\.org/(?:records?|api/records)/(\\d+)".r
  private val ZenodoDoi = "(?i)(?:^|doi\\.org/)10\\.5281/zenodo\\.(\\d+)".r

  /** Pure: the Zenodo record id of a record URL (`zenodo.org/record/3695551`, `…/records/…`) or of a Zenodo DOI
    * (`10.5281/zenodo.3695551`, bare or as a `doi.org` URL).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def zenodoRecordId(urlOrDoi: ZenodoLocator): Option[ZenodoRecordId] =
    val t = (urlOrDoi).value.trim
    ZenodoRecord.findFirstMatchIn(t).orElse(ZenodoDoi.findFirstMatchIn(t)).map(m => ZenodoRecordId(m.group(1)))

  /** Pure: the PDF files of a Zenodo record, when its files are open (`metadata.access_right: open`, or the newer
    * `access.files: public`); restricted, embargoed or closed records give none.
    */
  def zenodoFiles(json: Json): List[ZenodoFile] =
    val c = json.cursor
    val right = c.str("metadata", "access_right").map((_).toLowerCase)
    val files = c.str("access", "files").map((_).toLowerCase)
    val open = right.forall(_ == "open") && files.forall(_ == "public") && (right.isDefined || files.isDefined)
    if !open then Nil
    else
      c.arr("files").flatMap { f =>
        val h = f.cursor
        val name = h.str("key").orElse(h.str("filename")).getOrElse("")
        val mime = h.str("mimetype").getOrElse("")
        val url = h.str("links", "self").orElse(h.str("links", "content")).orElse(h.str("links", "download"))
        val md5 = h.str("checksum").filter((_).toLowerCase.startsWith("md5:")).map((_).drop(4).toLowerCase)
        url
          .filter(_ => name.toLowerCase.endsWith(".pdf") || mime.equalsIgnoreCase("application/pdf"))
          .map(u => ZenodoFile(ZenodoFile.Url(u), (md5).map(refactorValue4 => ZenodoFile.Md5(refactorValue4))))
      }

  // ── an HTML item page ──────────────────────────────────────────────────────────────────────

  private val MetaTag = "(?is)<meta\\s+([^>]*?)/?>".r
  private val Attr = "(?is)([a-zA-Z_:.-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))".r

  /** Pure: the `<meta>` tags of a page as (name or property, lower-cased; content), in order — whatever order the
    * attributes come in, entities in the content decoded.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def metaTags(html: MetaTagsHtml): List[(MetaName, MetaContent)] =
    MetaTag.findAllMatchIn((html).value.take(MaxHead)).toList.flatMap { m =>
      val attrs = Attr
        .findAllMatchIn(m.group(1))
        .map { a =>
          a.group(1)
            .toLowerCase -> Option(a.group(2)).orElse(Option(a.group(3))).orElse(Option(a.group(4))).getOrElse("")
        }
        .toMap
      for
        key <- attrs.get("name").orElse(attrs.get("property")).map(_.trim.toLowerCase).filter(_.nonEmpty)
        content <- attrs
          .get("content")
          .map { val refactorFunction = decode; (refactorValue5) => refactorFunction((refactorValue5)) }
          .map((_).trim)
      yield { val refactorTuple = key -> content; (MetaName(refactorTuple._1), MetaContent(refactorTuple._2)) }
    }

  /** The part of a page the tags are read from: an item page's `<head>` sits well inside this. */
  val MaxHead = 400000

  private def decode(s: String): String =
    ((s)
      .replace("&amp;", "&")
      .replace("&quot;", "\"")
      .replace("&#39;", "'")
      .replace("&apos;", "'")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&#x2F;", "/")
      .replace("&#47;", "/"))

  /** Pure: the page's `generator` tags (`DSpace 6.3`, `EPrints 3.4`). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def generators(html: GeneratorsHtml): List[Rules.Generators] = metaTags(html).collect {
    case (refactorPattern, g) if (refactorPattern.value == "generator") && ((g).value.nonEmpty) =>
      Rules.Generators((g).value)
  }

  /** Pure: the `citation_pdf_url`s a page names, made absolute against `pageUrl`, http(s) only. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def citationPdfUrls(
      html: CitationPdfUrlsHtml,
      pageUrl: RepositoryLanding.CitationPdfUrlsPageUrl
  ): List[Candidate.Url] =
    metaTags(html)
      .collect {
        case (refactorPattern2, u) if (refactorPattern2.value == "citation_pdf_url") && ((u).value.nonEmpty) =>
          absolute(Href((u).value), pageUrl)
      }
      .flatten
      .distinct

  /** Pure: `href` against the page it is on (`/bitstream/…` → `https://host/bitstream/…`). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def absolute(href: Href, pageUrl: Candidate.Url): Option[Candidate.Url] =
    val h = (href).value.trim
    if "(?i)^https?://".r.findPrefixOf(h).isDefined then Some(Candidate.Url(h))
    else
      val origin = "(?i)^(https?://[^/?#]+)".r.findFirstMatchIn((pageUrl).value.trim).map(_.group(1))
      if h.startsWith("//") then Some(Candidate.Url((pageUrl).value.trim.takeWhile(_ != ':') + ":" + h))
      else if h.startsWith("/") then origin.map(o => Candidate.Url(o + h))
      else if h.isEmpty || h.contains(":") then None
      else
        val dir = (pageUrl).value.trim.split("[?#]").head
        Some(Candidate.Url(dir.substring(0, dir.lastIndexOf('/') + 1) + h))

  /** Pure: may `citation_pdf_url` be read on this page? Never on a denied (publisher) host nor a journal platform's
    * page (`deny-generators`: Open Journal Systems); otherwise when an OA index calls the location a repository, the
    * host is allowed or academic, or the page names a repository platform as its generator.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def isRepository(
      pageUrl: RepositoryLanding.IsRepositoryPageUrl,
      indexSaysRepository: Candidate.Repository,
      html: IsRepositoryHtml,
      rules: Rules
  ): Boolean =
    host(pageUrl).exists { h =>
      val gs = generators(html)
      !rules.denied(h) && !rules.journalGenerator(gs) &&
      (indexSaysRepository.value || rules.allowed(h) || rules.academic(h) || rules.repositoryGenerator(gs))
    }

  /** Pure: the PDFs to try from a repository page — its `citation_pdf_url`s, none on a denied host; nothing at all when
    * the page is not a repository's. An open-access publisher's open page (`oaPublisher`, [[oaPublisherPage]]) gives
    * its `citation_pdf_url`s and its OJS PDF galleys, an OJS viewer link turned into its download, on the page's own
    * host or another OA publisher's only.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def pdfsOf(
      pageUrl: RepositoryLanding.PdfsOfPageUrl,
      indexSaysRepository: RepositoryLanding.PdfsOfIndexSaysRepository,
      html: PdfsOfHtml,
      rules: Rules,
      oaPublisher: Candidate.Publisher = Candidate.Publisher(false)
  ): List[Candidate.Url] =
    if (oaPublisher).value then
      val own = host(pageUrl)
      (citationPdfUrls(html, pageUrl) ++ galleyPdfUrls(html, pageUrl))
        .map(ojsDownload)
        .distinct
        .filter(u => host(u).exists(h => own.contains(h) || rules.oaPublisher(h)))
    else if !((isRepository(pageUrl, indexSaysRepository, html, rules)) == (true)) then Nil
    else citationPdfUrls(html, pageUrl).filterNot(u => host(u).exists(rules.denied))

  private val GalleyLink = "(?is)<a\\s+([^>]*)>".r
  private val GalleyJson = "(?is)\"label\"\\s*:\\s*\"PDF\"\\s*,\\s*\"href\"\\s*:\\s*\"([^\"]+)\"".r
  private val OjsView = "(?i)/article/view/(\\d+)/(\\d+)(?=$|[?#])".r

  /** Pure: an Open Journal Systems page's PDF galleys — its `<a class="obj_galley_link pdf">` links, and the galleys an
    * OJS 3.5+ theme lists as JSON (`{"label":"PDF","href":…}`, escaped inside an attribute) — made absolute against
    * `pageUrl`, http(s) only.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def galleyPdfUrls(html: Html, pageUrl: RepositoryLanding.GalleyPdfUrlsPageUrl): List[Candidate.Url] =
    val page = (html).value.take(MaxHead)
    val links = GalleyLink
      .findAllMatchIn(page)
      .flatMap { m =>
        val attrs = Attr
          .findAllMatchIn(m.group(1))
          .map { a =>
            a.group(1)
              .toLowerCase -> Option(a.group(2)).orElse(Option(a.group(3))).orElse(Option(a.group(4))).getOrElse("")
          }
          .toMap
        val cls = attrs.getOrElse("class", "").toLowerCase.split("\\s+").toSet
        attrs.get("href").filter(_ => cls("obj_galley_link") && cls("pdf")).map {
          val refactorFunction3 = decode; (refactorValue7) => refactorFunction3((refactorValue7))
        }
      }
      .toList
    val unescaped = page.replace("\\u0022", "\"").replace("&quot;", "\"").replace("\\\\/", "/").replace("\\/", "/")
    val listed = GalleyJson.findAllMatchIn(unescaped).map(_.group(1)).toList
    (links ++ listed)
      .flatMap(h => absolute(Href(h), pageUrl))
      .filter(u => "(?i)^https?://".r.findPrefixOf((u).value).isDefined)
      .distinct

  /** Pure: an OJS galley's viewer URL (`…/article/view/<article>/<galley>`, an HTML page around the PDF) as its
    * download (`…/article/download/<article>/<galley>`); any other URL as it is.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def ojsDownload(url: Candidate.Url): Candidate.Url =
    Candidate.Url(OjsView.replaceFirstIn((url).value, "/article/download/$1/$2"))

  /** Pure: does a non-PDF answer look like an HTML page (worth reading for its tags)? */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def looksLikeHtml(b: PageBytes): Boolean =
    val head = new String(b.value.take(2048), "ISO-8859-1").toLowerCase
    head.contains("<html") || head.contains("<!doctype html") || head.contains("<head") || head.contains("<meta")
