package com.histmersion.upstreams.landing
import io.circe.{ACursor, Json}

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
object RepositoryLanding:
  type Host = Host.T
  object Host extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HostPattern = HostPattern.T
  object HostPattern extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Href = Href.T
  object Href extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Html = Html.T
  object Html extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MetaContent = MetaContent.T
  object MetaContent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MetaName = MetaName.T
  object MetaName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageBytes = PageBytes.T
  object PageBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ZenodoLocator = ZenodoLocator.T
  object ZenodoLocator extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ZenodoRecordId = ZenodoRecordId.T
  object ZenodoRecordId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A landing page to resolve; `repository` = an OA index (OpenAlex `source.type`, Unpaywall `host_type`) says the
    * location is a repository, `publisher` = it says a journal's or a publisher's (such a page is never read, unless it
    * is an open-access publisher's — [[oaPublisherPage]]); `open` = the index says the location is open access
    * (OpenAlex `is_oa`; every Unpaywall `oa_location` is).
    */

  type PublisherSourceTypes = PublisherSourceTypesValue
  type PublisherSourceTypesValue = PublisherSourceTypesValue.T
  object PublisherSourceTypesValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OnHostUrl = _root_.com.histmersion.upstreams.landing.RepositoryLanding.HostUrl
  val OnHostUrl: _root_.com.histmersion.upstreams.landing.RepositoryLanding.HostUrl.type = _root_.com.histmersion.upstreams.landing.RepositoryLanding.HostUrl
  type HostUrl = _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url
  val HostUrl: _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url.type = _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url
  type MetaTagsHtml = _root_.com.histmersion.upstreams.landing.RepositoryLanding.Html
  val MetaTagsHtml: _root_.com.histmersion.upstreams.landing.RepositoryLanding.Html.type = _root_.com.histmersion.upstreams.landing.RepositoryLanding.Html
  type GeneratorsHtml = _root_.com.histmersion.upstreams.landing.RepositoryLanding.Html
  val GeneratorsHtml: _root_.com.histmersion.upstreams.landing.RepositoryLanding.Html.type = _root_.com.histmersion.upstreams.landing.RepositoryLanding.Html
  type CitationPdfUrlsHtml = _root_.com.histmersion.upstreams.landing.RepositoryLanding.Html
  val CitationPdfUrlsHtml: _root_.com.histmersion.upstreams.landing.RepositoryLanding.Html.type = _root_.com.histmersion.upstreams.landing.RepositoryLanding.Html
  type CitationPdfUrlsPageUrl = _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url
  val CitationPdfUrlsPageUrl: _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url.type = _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url
  type IsRepositoryPageUrl = _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url
  val IsRepositoryPageUrl: _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url.type = _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url
  type IsRepositoryHtml = _root_.com.histmersion.upstreams.landing.RepositoryLanding.Html
  val IsRepositoryHtml: _root_.com.histmersion.upstreams.landing.RepositoryLanding.Html.type = _root_.com.histmersion.upstreams.landing.RepositoryLanding.Html
  type PdfsOfPageUrl = _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url
  val PdfsOfPageUrl: _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url.type = _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url
  type PdfsOfIndexSaysRepository = _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Repository
  val PdfsOfIndexSaysRepository: _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Repository.type = _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Repository
  type PdfsOfHtml = _root_.com.histmersion.upstreams.landing.RepositoryLanding.Html
  val PdfsOfHtml: _root_.com.histmersion.upstreams.landing.RepositoryLanding.Html.type = _root_.com.histmersion.upstreams.landing.RepositoryLanding.Html
  type GalleyPdfUrlsPageUrl = _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url
  val GalleyPdfUrlsPageUrl: _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url.type = _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url

  final case class Candidate(url: Candidate.Url, repository: Candidate.Repository, publisher: Candidate.Publisher = _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Publisher(false), open: Candidate.Open = _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Open(false))

  object Candidate:
    type Url = Url.T
    object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Repository = Repository.T
    object Repository extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Publisher = Publisher.T
    object Publisher extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Open = Open.T
    object Open extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** OpenAlex `source.type`s that are a publisher's, not a repository's. */
  val PublisherSourceTypes: Set[com.histmersion.upstreams.landing.RepositoryLanding.PublisherSourceTypes] = Set(_root_.com.histmersion.upstreams.landing.RepositoryLanding.PublisherSourceTypesValue("journal"), _root_.com.histmersion.upstreams.landing.RepositoryLanding.PublisherSourceTypesValue("conference"), _root_.com.histmersion.upstreams.landing.RepositoryLanding.PublisherSourceTypesValue("ebook platform"), _root_.com.histmersion.upstreams.landing.RepositoryLanding.PublisherSourceTypesValue("book series"))

  /** One file of a Zenodo record: where to download it and its MD5, when the record states one. */
  final case class ZenodoFile(url: ZenodoFile.Url, md5: Option[ZenodoFile.Md5])

  object ZenodoFile:
    type Url = Url.T
    object Url extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Md5 = Md5.T
    object Md5 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The hosts and platforms the decision reads (`acquire.landing.*`, comma-separated in config). A host entry matches
    * the host itself or any subdomain of it.
    */
  final case class Rules(
      allowHosts: List[Rules.AllowHosts],
      denyHosts: List[Rules.DenyHosts],
      academicHostWords: List[Rules.AcademicHostWords],
      generators: List[Rules.Generators],
      denyGenerators: List[Rules.DenyGenerators] = Nil,
      denyHostWords: List[Rules.DenyHostWords] = Nil,
      oaPublisherHosts: List[Rules.OaPublisherHosts] = Nil
  ):
    def allowed(host: com.histmersion.upstreams.landing.RepositoryLanding.Host): Boolean = allowHosts.exists(d => matchesHost(host, d))

    /** An open-access publisher whose open locations may be read (`oa-publisher-hosts`, #2550). */
    def oaPublisher(host: com.histmersion.upstreams.landing.RepositoryLanding.Host): Boolean = oaPublisherHosts.exists(d => matchesHost(host, d))

    /** A publisher's host: a denied host or its subdomain, or — unless the host is allowed — a label that is or starts
      * with a denied word (`press.…`, `journals.…`: a university press on a `.edu`).
      */
    def denied(host: com.histmersion.upstreams.landing.RepositoryLanding.Host): Boolean =
      denyHosts.exists(d => matchesHost(host, d)) ||
        (!((allowed(_root_.com.histmersion.upstreams.landing.RepositoryLanding.Host((host).value))) == (true)) && (host).value.split('.').toList.dropRight(1).exists(l => denyHostWords.exists(w => wordLabel((l), w))))

    /** A university's host: `.edu` (`.edu.au` too), `.ac.<cc>`, or a label that is a configured word ([[wordLabel]]:
      * `repository`, `eprints`, `pure`, `opus4`, `uni-tuebingen` for `uni-`).
      */
    def academic(host: com.histmersion.upstreams.landing.RepositoryLanding.Host): Boolean =
      val labels = (host).value.split('.').toList
      labels.contains("edu") || labels.dropRight(1).lastOption.contains("ac") ||
      labels.dropRight(1).exists(l => academicHostWords.exists(w => wordLabel((l), w)))

    /** A platform `generator` tag that names a repository (`DSpace 6.3`) — and not a journal's. */
    def repositoryGenerator(gs: List[com.histmersion.upstreams.landing.RepositoryLanding.Rules.Generators]): Boolean =
      val low = gs.map((_).value.toLowerCase)
      !low.exists(g => denyGenerators.exists(d => (d).value.nonEmpty && g.contains(d))) &&
      low.exists(g => generators.exists(p => (p).value.nonEmpty && g.contains(p)))

    /** A journal platform's page (`Open Journal Systems`): a publisher's, whatever its host. */
    def journalGenerator(gs: List[com.histmersion.upstreams.landing.RepositoryLanding.Rules.Generators]): Boolean =
      (gs.map((_).value.toLowerCase).exists(g => denyGenerators.exists(d => (d).value.nonEmpty && g.contains(d))))

  object Rules:

    type AllowHosts = AllowHosts.T
    object AllowHosts extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DenyHosts = DenyHosts.T
    object DenyHosts extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AcademicHostWords = AcademicHostWords.T
    object AcademicHostWords extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Generators = Generators.T
    object Generators extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DenyGenerators = DenyGenerators.T
    object DenyGenerators extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DenyHostWords = DenyHostWords.T
    object DenyHostWords extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OaPublisherHosts = OaPublisherHosts.T
    object OaPublisherHosts extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Pure: is the host label `label` the word `w` — the word itself, the word then a digit or a hyphen (`opus4`,
    * `pure-portal`), or, for a word ending in `-`, any label it starts (`uni-` → `uni-koeln`)? Never a longer word
    * (`pure` is not `purepharma`, `press` is not `pressreader`).
    */
  def wordLabel(label: String, w: String): Boolean =
    ((w).nonEmpty && (label == w || ((w).endsWith("-") && (label).value.startsWith((w).value)) ||
      ((label).value.startsWith((w).value) && (label).value.length > (w).value.length && { val c = (label).value.charAt((w).value.length); c == '-' || c.isDigit })))

  /** A host entry matches the host itself or any subdomain of it. */
  def matchesHost(host: com.histmersion.upstreams.landing.RepositoryLanding.Host, entry: com.histmersion.upstreams.landing.RepositoryLanding.HostPattern): Boolean =
    val e = (entry).value.trim.toLowerCase.stripPrefix(".")
    (e.nonEmpty && (host == _root_.com.histmersion.upstreams.landing.RepositoryLanding.Host(e) || (host).value.endsWith("." + e)))

  /** Pure: the lower-case host of an absolute http(s) URL. */
  /** The host of `url` when it is one of `hosts` or a subdomain of one. */
  def onHost(url: com.histmersion.upstreams.landing.RepositoryLanding.OnHostUrl, hosts: List[com.histmersion.upstreams.landing.RepositoryLanding.HostPattern]): Option[com.histmersion.upstreams.landing.RepositoryLanding.HostPattern] = host(url).filter(h => hosts.exists(matchesHost(_root_.com.histmersion.upstreams.landing.RepositoryLanding.Host((h).value), _root_.com.histmersion.upstreams.landing.RepositoryLanding.HostPattern((_).value))))

  def host(url: com.histmersion.upstreams.landing.RepositoryLanding.HostUrl): Option[com.histmersion.upstreams.landing.RepositoryLanding.Host] =
    "(?i)^https?://([^/?#:]+)".r.findFirstMatchIn((url).value.trim).map(_root_.com.histmersion.upstreams.landing.RepositoryLanding.Host(_.group(1).toLowerCase.stripPrefix("www.")))

  // ── which pages ────────────────────────────────────────────────────────────────────────────

  private def str(c: ACursor, keys: String*): Option[String] =
    (keys.foldLeft(c)(_.downField(_)).as[String].toOption.map(_.trim).filter(_.nonEmpty)).map(refactorValue => (refactorValue))
  private def arr(c: ACursor, keys: String*): List[Json] =
    keys.foldLeft(c)(_.downField(_)).values.map(_.toList).getOrElse(Nil)

  private def notPdfPath(u: String) = !(u).toLowerCase.split("[?#]").head.endsWith(".pdf")

  /** Pure: the landing pages an OpenAlex work names — each location's landing page, and a `pdf_url` that is a record
    * page (no `.pdf`: the direct step already tried it as a file) — best location first, repositories marked.
    */
  def openalexCandidates(json: Json): List[Candidate] =
    val c = json.hcursor
    val locs = c.downField("best_oa_location").focus.filterNot(_.isNull).toList ++ arr(c, "locations")
    distinct(locs.flatMap { l =>
      val h = l.hcursor
      val kind = str(h, "source", "type").map((_).toLowerCase)
      val repo = kind.contains("repository")
      val pub = kind.exists(PublisherSourceTypes)
      val open = h.downField("is_oa").as[Boolean].contains(true)
      (str(h, "pdf_url").filter(((notPdfPath))).toList ++ str(h, "landing_page_url").toList)
        .map(Candidate(_, _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Repository(repo), _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Publisher(pub), _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Open(open)))
    })

  /** Pure: the landing pages of Unpaywall's OA locations (`host_type: repository` marked). */
  def unpaywallCandidates(json: Json): List[Candidate] =
    val c = json.hcursor
    val locs = c.downField("best_oa_location").focus.filterNot(_.isNull).toList ++ arr(c, "oa_locations")
    distinct(locs.flatMap { l =>
      val h = l.hcursor
      val repo = str(h, "host_type").contains("repository")
      val pub = str(h, "host_type").contains("publisher")
      (str(h, "url_for_pdf").filter(((notPdfPath))).toList ++ str(h, "url_for_landing_page").toList)
        .map(Candidate(_, _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Repository(repo), _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Publisher(pub), open = _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Open(true)))
    })

  /** Pure: where a DataCite DOI resolves (`data.attributes.url` of `api.datacite.org/dois/<doi>`). */
  def dataciteUrl(json: Json): Option[com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url] = (str(json.hcursor, "data", "attributes", "url")).map(refactorValue2 => _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url((refactorValue2)))

  /** Candidates by URL, the first of each kept; repository if any index says so, publisher only if one says so and none
    * says repository, open if any says so.
    */
  def distinct(cs: List[Candidate]): List[Candidate] =
    cs.filter(c => host(c.url).isDefined)
      .groupBy(_.url)
      .toList
      .map { (u, xs) =>
        val repo = xs.exists((refactorCallback => ((refactorCallback.repository) == _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Repository(true))))
        (cs.indexWhere(_.url == u), Candidate(u, _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Repository(repo), _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Publisher(!repo && xs.exists((refactorCallback => ((refactorCallback.publisher) == _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Publisher(true))))), _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Open(xs.exists((refactorCallback => ((refactorCallback.open) == _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Open(true)))))))
      }
      .sortBy(_._1)
      .map(_._2)

  /** Pure: is this an open-access publisher's OPEN location (#2550) — read although it is a publisher's: an index says
    * it is open, and its host is on `oa-publisher-hosts`.
    */
  def oaPublisherPage(c: Candidate, rules: Rules): Boolean = ((c.open) == _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Open(true)) && host(c.url).exists(rules.oaPublisher)

  /** Pure: where the DOI handle API (`doi.org/api/handles/<doi>?type=URL`) says a DOI resolves — its first URL value.
    * Read only for an open location on an OA publisher, so a `doi.org` link is still never followed blind.
    */
  def handleUrl(json: Json): Option[com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url] =
    (arr(json.hcursor, "values").iterator
      .map(_.hcursor)
      .filter(v => str(v, "type").contains("URL"))
      .flatMap(v => str(v, "data", "value"))
      .nextOption()).map(refactorValue3 => _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url((refactorValue3)))

  // ── Zenodo ─────────────────────────────────────────────────────────────────────────────────

  private val ZenodoRecord = "(?i)^https?://(?:www\\.)?zenodo\\.org/(?:records?|api/records)/(\\d+)".r
  private val ZenodoDoi = "(?i)(?:^|doi\\.org/)10\\.5281/zenodo\\.(\\d+)".r

  /** Pure: the Zenodo record id of a record URL (`zenodo.org/record/3695551`, `…/records/…`) or of a Zenodo DOI
    * (`10.5281/zenodo.3695551`, bare or as a `doi.org` URL).
    */
  def zenodoRecordId(urlOrDoi: com.histmersion.upstreams.landing.RepositoryLanding.ZenodoLocator): Option[com.histmersion.upstreams.landing.RepositoryLanding.ZenodoRecordId] =
    val t = (urlOrDoi).value.trim
    ZenodoRecord.findFirstMatchIn(t).orElse(ZenodoDoi.findFirstMatchIn(t)).map(_root_.com.histmersion.upstreams.landing.RepositoryLanding.ZenodoRecordId(_.group(1)))

  /** Pure: the PDF files of a Zenodo record, when its files are open (`metadata.access_right: open`, or the newer
    * `access.files: public`); restricted, embargoed or closed records give none.
    */
  def zenodoFiles(json: Json): List[ZenodoFile] =
    val c = json.hcursor
    val right = str(c, "metadata", "access_right").map((_).toLowerCase)
    val files = str(c, "access", "files").map((_).toLowerCase)
    val open = right.forall(_ == "open") && files.forall(_ == "public") && (right.isDefined || files.isDefined)
    if !open then Nil
    else
      arr(c, "files").flatMap { f =>
        val h = f.hcursor
        val name = str(h, "key").orElse(str(h, "filename")).getOrElse("")
        val mime = str(h, "mimetype").getOrElse("")
        val url = str(h, "links", "self").orElse(str(h, "links", "content")).orElse(str(h, "links", "download"))
        val md5 = str(h, "checksum").filter((_).toLowerCase.startsWith("md5:")).map((_).drop(4).toLowerCase)
        url
          .filter(_ => name.toLowerCase.endsWith(".pdf") || mime.equalsIgnoreCase("application/pdf"))
          .map(ZenodoFile(_, (md5).map(refactorValue4 => _root_.com.histmersion.upstreams.landing.RepositoryLanding.ZenodoFile.Md5(refactorValue4))))
      }

  // ── an HTML item page ──────────────────────────────────────────────────────────────────────

  private val MetaTag = "(?is)<meta\\s+([^>]*?)/?>".r
  private val Attr = "(?is)([a-zA-Z_:.-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))".r

  /** Pure: the `<meta>` tags of a page as (name or property, lower-cased; content), in order — whatever order the
    * attributes come in, entities in the content decoded.
    */
  def metaTags(html: com.histmersion.upstreams.landing.RepositoryLanding.MetaTagsHtml): List[(com.histmersion.upstreams.landing.RepositoryLanding.MetaName, com.histmersion.upstreams.landing.RepositoryLanding.MetaContent)] =
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
        content <- attrs.get("content").map({ val refactorFunction = decode; (refactorValue5) => refactorFunction((refactorValue5)) }).map((_).trim)
      yield { val refactorTuple = key -> content; (_root_.com.histmersion.upstreams.landing.RepositoryLanding.MetaName(refactorTuple._1), _root_.com.histmersion.upstreams.landing.RepositoryLanding.MetaContent(refactorTuple._2)) }
    }

  /** The part of a page the tags are read from: an item page's `<head>` sits well inside this. */
  val MaxHead = 400000

  private def decode(s: String): String =
    ((s).replace("&amp;", "&")
      .replace("&quot;", "\"")
      .replace("&#39;", "'")
      .replace("&apos;", "'")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&#x2F;", "/")
      .replace("&#47;", "/"))

  /** Pure: the page's `generator` tags (`DSpace 6.3`, `EPrints 3.4`). */
  def generators(html: com.histmersion.upstreams.landing.RepositoryLanding.GeneratorsHtml): List[com.histmersion.upstreams.landing.RepositoryLanding.Rules.Generators] = metaTags(html).collect { case (refactorPattern, g) if (refactorPattern.value == "generator") && ((g).value.nonEmpty) => _root_.com.histmersion.upstreams.landing.RepositoryLanding.Rules.Generators((g).value) }

  /** Pure: the `citation_pdf_url`s a page names, made absolute against `pageUrl`, http(s) only. */
  def citationPdfUrls(html: com.histmersion.upstreams.landing.RepositoryLanding.CitationPdfUrlsHtml, pageUrl: com.histmersion.upstreams.landing.RepositoryLanding.CitationPdfUrlsPageUrl): List[com.histmersion.upstreams.europepmc.EuropePmcHit.PdfUrls] =
    (metaTags(html).collect { case (refactorPattern2, u) if (refactorPattern2.value == "citation_pdf_url") && ((u).value.nonEmpty) => absolute(_root_.com.histmersion.upstreams.landing.RepositoryLanding.Href((u).value), pageUrl) }.flatten.distinct).map(refactorValue6 => _root_.com.histmersion.upstreams.europepmc.EuropePmcHit.PdfUrls((refactorValue6).value))

  /** Pure: `href` against the page it is on (`/bitstream/…` → `https://host/bitstream/…`). */
  def absolute(href: com.histmersion.upstreams.landing.RepositoryLanding.Href, pageUrl: com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url): Option[com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url] =
    val h = (href).value.trim
    if "(?i)^https?://".r.findPrefixOf(h).isDefined then Some(_root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url(h))
    else
      val origin = "(?i)^(https?://[^/?#]+)".r.findFirstMatchIn((pageUrl).value.trim).map(_.group(1))
      if h.startsWith("//") then Some(_root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url((pageUrl).value.trim.takeWhile(_ != ':') + ":" + h))
      else if h.startsWith("/") then origin.map(_root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url(_ + h))
      else if h.isEmpty || h.contains(":") then None
      else
        val dir = (pageUrl).value.trim.split("[?#]").head
        Some(_root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url(dir.substring(0, dir.lastIndexOf('/') + 1) + h))

  /** Pure: may `citation_pdf_url` be read on this page? Never on a denied (publisher) host nor a journal platform's
    * page (`deny-generators`: Open Journal Systems); otherwise when an OA index calls the location a repository, the
    * host is allowed or academic, or the page names a repository platform as its generator.
    */
  def isRepository(pageUrl: com.histmersion.upstreams.landing.RepositoryLanding.IsRepositoryPageUrl, indexSaysRepository: com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Repository, html: com.histmersion.upstreams.landing.RepositoryLanding.IsRepositoryHtml, rules: Rules): Boolean =
    host(pageUrl).exists { h =>
      val gs = generators(html)
      !rules.denied(h) && !rules.journalGenerator(gs) &&
      (indexSaysRepository || rules.allowed(h) || rules.academic(h) || rules.repositoryGenerator(gs))
    }

  /** Pure: the PDFs to try from a repository page — its `citation_pdf_url`s, none on a denied host; nothing at all when
    * the page is not a repository's. An open-access publisher's open page (`oaPublisher`, [[oaPublisherPage]]) gives
    * its `citation_pdf_url`s and its OJS PDF galleys, an OJS viewer link turned into its download, on the page's own
    * host or another OA publisher's only.
    */
  def pdfsOf(
      pageUrl: com.histmersion.upstreams.landing.RepositoryLanding.PdfsOfPageUrl,
      indexSaysRepository: com.histmersion.upstreams.landing.RepositoryLanding.PdfsOfIndexSaysRepository,
      html: com.histmersion.upstreams.landing.RepositoryLanding.PdfsOfHtml,
      rules: Rules,
      oaPublisher: com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Publisher = _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Publisher(false)
  ): List[com.histmersion.upstreams.europepmc.EuropePmcHit.PdfUrls] =
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
  def galleyPdfUrls(html: com.histmersion.upstreams.landing.RepositoryLanding.Html, pageUrl: com.histmersion.upstreams.landing.RepositoryLanding.GalleyPdfUrlsPageUrl): List[com.histmersion.upstreams.europepmc.EuropePmcHit.PdfUrls] =
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
        attrs.get("href").filter(_ => cls("obj_galley_link") && cls("pdf")).map({ val refactorFunction3 = decode; (refactorValue7) => refactorFunction3((refactorValue7)) })
      }
      .toList
    val unescaped = page.replace("\\u0022", "\"").replace("&quot;", "\"").replace("\\\\/", "/").replace("\\/", "/")
    val listed = GalleyJson.findAllMatchIn(unescaped).map(_.group(1)).toList
    (links ++ listed).flatMap(absolute(_, pageUrl)).filter(u => "(?i)^https?://".r.findPrefixOf((u).value).isDefined).distinct

  /** Pure: an OJS galley's viewer URL (`…/article/view/<article>/<galley>`, an HTML page around the PDF) as its
    * download (`…/article/download/<article>/<galley>`); any other URL as it is.
    */
  def ojsDownload(url: com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url): com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url = _root_.com.histmersion.upstreams.landing.RepositoryLanding.Candidate.Url(OjsView.replaceFirstIn((url).value, "/article/download/$1/$2"))

  /** Pure: does a non-PDF answer look like an HTML page (worth reading for its tags)? */
  def looksLikeHtml(b: com.histmersion.upstreams.landing.RepositoryLanding.PageBytes): Boolean =
    val head = new String(b.take(2048), "ISO-8859-1").toLowerCase
    head.contains("<html") || head.contains("<!doctype html") || head.contains("<head") || head.contains("<meta")
