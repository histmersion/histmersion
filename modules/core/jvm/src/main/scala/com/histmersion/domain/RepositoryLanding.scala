package com.histmersion.domain

import com.histmersion.api.*
import com.histmersion.api.given

import io.circe.{ACursor, Json}

/** Repository LANDING PAGES (#1580): an open-access location whose URL is an HTML record page rather
  * than the PDF — a Zenodo record, a DSpace/EPrints/Pure item, a DataCite DOI that resolves to one.
  * The fetch resolves such a page ONE step, and only in two ways a repository itself offers:
  *
  *  - **Zenodo**: its REST API (`/api/records/<id>`) names the record's files; an open record's PDF
  *    files are downloaded from `files[].links.self` and checked against the stated MD5;
  *  - **`citation_pdf_url`**: the `<meta name="citation_pdf_url">` an institutional repository puts on
  *    an item page for Google Scholar's inclusion guidelines — read only on a page that is a
  *    REPOSITORY ([[isRepository]]: an OA index says so, an allowed host, an academic host, or a
  *    repository platform's `generator` tag) and never on a publisher's site ([[Rules.denied]], which
  *    wins over every other signal; the PDF's own host is held to it too).
  *
  * An OPEN-ACCESS PUBLISHER's page (#2550) is the one exception to "never a publisher's page": a host
  * on `acquire.landing.oa-publisher-hosts` — publishers whose robots.txt and terms permit a script to
  * download their open-access PDFs, checked one by one — is read when an OA index names the location
  * as OPEN ([[Candidate.open]]), and gives its `citation_pdf_url`s and its OJS PDF galleys
  * ([[galleyPdfUrls]]), on its own hosts only.
  *
  * A page behind bot protection or a 403 is no copy: the HTTP layer reports it as a failure, and the
  * work goes to the acquisition queue as before. Everything here is pure and total. */
object RepositoryLanding:

  /** A landing page to resolve; `repository` = an OA index (OpenAlex `source.type`, Unpaywall
    * `host_type`) says the location is a repository, `publisher` = it says a journal's or a publisher's
    * (such a page is never read, unless it is an open-access publisher's — [[oaPublisherPage]]);
    * `open` = the index says the location is open access (OpenAlex `is_oa`; every Unpaywall
    * `oa_location` is). */
  final case class Candidate(url: String, repository: Boolean, publisher: Boolean = false, open: Boolean = false)

  /** OpenAlex `source.type`s that are a publisher's, not a repository's. */
  val PublisherSourceTypes: Set[String] = Set("journal", "conference", "ebook platform", "book series")

  /** One file of a Zenodo record: where to download it and its MD5, when the record states one. */
  final case class ZenodoFile(url: String, md5: Option[String])

  /** The hosts and platforms the decision reads (`acquire.landing.*`, comma-separated in config).
    * A host entry matches the host itself or any subdomain of it. */
  final case class Rules(allowHosts: List[String], denyHosts: List[String], academicHostWords: List[String],
      generators: List[String], denyGenerators: List[String] = Nil, denyHostWords: List[String] = Nil,
      oaPublisherHosts: List[String] = Nil):
    def allowed(host: String): Boolean = allowHosts.exists(d => matchesHost(host, d))
    /** An open-access publisher whose open locations may be read (`oa-publisher-hosts`, #2550). */
    def oaPublisher(host: String): Boolean = oaPublisherHosts.exists(d => matchesHost(host, d))
    /** A publisher's host: a denied host or its subdomain, or — unless the host is allowed — a label
      * that is or starts with a denied word (`press.…`, `journals.…`: a university press on a `.edu`). */
    def denied(host: String): Boolean =
      denyHosts.exists(d => matchesHost(host, d)) ||
        (!allowed(host) && host.split('.').toList.dropRight(1).exists(l => denyHostWords.exists(w => wordLabel(l, w))))
    /** A university's host: `.edu` (`.edu.au` too), `.ac.<cc>`, or a label that is a configured word
      * ([[wordLabel]]: `repository`, `eprints`, `pure`, `opus4`, `uni-tuebingen` for `uni-`). */
    def academic(host: String): Boolean =
      val labels = host.split('.').toList
      labels.contains("edu") || labels.dropRight(1).lastOption.contains("ac") ||
        labels.dropRight(1).exists(l => academicHostWords.exists(w => wordLabel(l, w)))
    /** A platform `generator` tag that names a repository (`DSpace 6.3`) — and not a journal's. */
    def repositoryGenerator(gs: List[String]): Boolean =
      val low = gs.map(_.toLowerCase)
      !low.exists(g => denyGenerators.exists(d => d.nonEmpty && g.contains(d))) &&
        low.exists(g => generators.exists(p => p.nonEmpty && g.contains(p)))
    /** A journal platform's page (`Open Journal Systems`): a publisher's, whatever its host. */
    def journalGenerator(gs: List[String]): Boolean =
      gs.map(_.toLowerCase).exists(g => denyGenerators.exists(d => d.nonEmpty && g.contains(d)))

  /** Pure: is the host label `label` the word `w` — the word itself, the word then a digit or a hyphen
    * (`opus4`, `pure-portal`), or, for a word ending in `-`, any label it starts (`uni-` → `uni-koeln`)?
    * Never a longer word (`pure` is not `purepharma`, `press` is not `pressreader`). */
  def wordLabel(label: String, w: String): Boolean =
    w.nonEmpty && (label == w || (w.endsWith("-") && label.startsWith(w)) ||
      (label.startsWith(w) && label.length > w.length && { val c = label.charAt(w.length); c == '-' || c.isDigit }))

  /** A host entry matches the host itself or any subdomain of it. */
  def matchesHost(host: String, entry: String): Boolean =
    val e = entry.trim.toLowerCase.stripPrefix(".")
    e.nonEmpty && (host == e || host.endsWith("." + e))

  /** Pure: the lower-case host of an absolute http(s) URL. */
  def host(url: String): Option[String] =
    "(?i)^https?://([^/?#:]+)".r.findFirstMatchIn(url.trim).map(_.group(1).toLowerCase.stripPrefix("www."))

  // ── which pages ────────────────────────────────────────────────────────────────────────────

  private def str(c: ACursor, keys: String*): Option[String] =
    keys.foldLeft(c)(_.downField(_)).as[String].toOption.map(_.trim).filter(_.nonEmpty)
  private def arr(c: ACursor, keys: String*): List[Json] =
    keys.foldLeft(c)(_.downField(_)).values.map(_.toList).getOrElse(Nil)

  private def notPdfPath(u: String) = !u.toLowerCase.split("[?#]").head.endsWith(".pdf")

  /** Pure: the landing pages an OpenAlex work names — each location's landing page, and a `pdf_url`
    * that is a record page (no `.pdf`: the direct step already tried it as a file) — best location
    * first, repositories marked. */
  def openalexCandidates(json: Json): List[Candidate] =
    val c = json.hcursor
    val locs = c.downField("best_oa_location").focus.filterNot(_.isNull).toList ++ arr(c, "locations")
    distinct(locs.flatMap { l =>
      val h    = l.hcursor
      val kind = str(h, "source", "type").map(_.toLowerCase)
      val repo = kind.contains("repository")
      val pub  = kind.exists(PublisherSourceTypes)
      val open = h.downField("is_oa").as[Boolean].contains(true)
      (str(h, "pdf_url").filter(notPdfPath).toList ++ str(h, "landing_page_url").toList).map(Candidate(_, repo, pub, open))
    })

  /** Pure: the landing pages of Unpaywall's OA locations (`host_type: repository` marked). */
  def unpaywallCandidates(json: Json): List[Candidate] =
    val c = json.hcursor
    val locs = c.downField("best_oa_location").focus.filterNot(_.isNull).toList ++ arr(c, "oa_locations")
    distinct(locs.flatMap { l =>
      val h    = l.hcursor
      val repo = str(h, "host_type").contains("repository")
      val pub  = str(h, "host_type").contains("publisher")
      (str(h, "url_for_pdf").filter(notPdfPath).toList ++ str(h, "url_for_landing_page").toList).map(Candidate(_, repo, pub, open = true))
    })

  /** Pure: where a DataCite DOI resolves (`data.attributes.url` of `api.datacite.org/dois/<doi>`). */
  def dataciteUrl(json: Json): Option[String] = str(json.hcursor, "data", "attributes", "url")

  /** Candidates by URL, the first of each kept; repository if any index says so, publisher only if
    * one says so and none says repository, open if any says so. */
  def distinct(cs: List[Candidate]): List[Candidate] =
    cs.filter(c => host(c.url).isDefined).groupBy(_.url).toList
      .map { (u, xs) =>
        val repo = xs.exists(_.repository)
        (cs.indexWhere(_.url == u), Candidate(u, repo, !repo && xs.exists(_.publisher), xs.exists(_.open)))
      }.sortBy(_._1).map(_._2)

  /** Pure: is this an open-access publisher's OPEN location (#2550) — read although it is a
    * publisher's: an index says it is open, and its host is on `oa-publisher-hosts`. */
  def oaPublisherPage(c: Candidate, rules: Rules): Boolean = c.open && host(c.url).exists(rules.oaPublisher)

  /** Pure: where the DOI handle API (`doi.org/api/handles/<doi>?type=URL`) says a DOI resolves — its
    * first URL value. Read only for an open location on an OA publisher, so a `doi.org` link is still
    * never followed blind. */
  def handleUrl(json: Json): Option[String] =
    arr(json.hcursor, "values").iterator.map(_.hcursor)
      .filter(v => str(v, "type").contains("URL"))
      .flatMap(v => str(v, "data", "value")).nextOption()

  // ── Zenodo ─────────────────────────────────────────────────────────────────────────────────

  private val ZenodoRecord = "(?i)^https?://(?:www\\.)?zenodo\\.org/(?:records?|api/records)/(\\d+)".r
  private val ZenodoDoi    = "(?i)(?:^|doi\\.org/)10\\.5281/zenodo\\.(\\d+)".r

  /** Pure: the Zenodo record id of a record URL (`zenodo.org/record/3695551`, `…/records/…`) or of a
    * Zenodo DOI (`10.5281/zenodo.3695551`, bare or as a `doi.org` URL). */
  def zenodoRecordId(urlOrDoi: String): Option[String] =
    val t = urlOrDoi.trim
    ZenodoRecord.findFirstMatchIn(t).orElse(ZenodoDoi.findFirstMatchIn(t)).map(_.group(1))

  /** Pure: the PDF files of a Zenodo record, when its files are open (`metadata.access_right: open`,
    * or the newer `access.files: public`); restricted, embargoed or closed records give none. */
  def zenodoFiles(json: Json): List[ZenodoFile] =
    val c = json.hcursor
    val right = str(c, "metadata", "access_right").map(_.toLowerCase)
    val files = str(c, "access", "files").map(_.toLowerCase)
    val open  = right.forall(_ == "open") && files.forall(_ == "public") && (right.isDefined || files.isDefined)
    if !open then Nil
    else arr(c, "files").flatMap { f =>
      val h    = f.hcursor
      val name = str(h, "key").orElse(str(h, "filename")).getOrElse("")
      val mime = str(h, "mimetype").getOrElse("")
      val url  = str(h, "links", "self").orElse(str(h, "links", "content")).orElse(str(h, "links", "download"))
      val md5  = str(h, "checksum").filter(_.toLowerCase.startsWith("md5:")).map(_.drop(4).toLowerCase)
      url.filter(_ => name.toLowerCase.endsWith(".pdf") || mime.equalsIgnoreCase("application/pdf")).map(ZenodoFile(_, md5))
    }

  // ── an HTML item page ──────────────────────────────────────────────────────────────────────

  private val MetaTag = "(?is)<meta\\s+([^>]*?)/?>".r
  private val Attr    = "(?is)([a-zA-Z_:.-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))".r

  /** Pure: the `<meta>` tags of a page as (name or property, lower-cased; content), in order —
    * whatever order the attributes come in, entities in the content decoded. */
  def metaTags(html: String): List[(String, String)] =
    MetaTag.findAllMatchIn(html.take(MaxHead)).toList.flatMap { m =>
      val attrs = Attr.findAllMatchIn(m.group(1)).map { a =>
        a.group(1).toLowerCase -> Option(a.group(2)).orElse(Option(a.group(3))).orElse(Option(a.group(4))).getOrElse("")
      }.toMap
      for
        key     <- attrs.get("name").orElse(attrs.get("property")).map(_.trim.toLowerCase).filter(_.nonEmpty)
        content <- attrs.get("content").map(decode).map(_.trim)
      yield key -> content
    }

  /** The part of a page the tags are read from: an item page's `<head>` sits well inside this. */
  val MaxHead = 400000

  private def decode(s: String): String =
    s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
      .replace("&lt;", "<").replace("&gt;", ">").replace("&#x2F;", "/").replace("&#47;", "/")

  /** Pure: the page's `generator` tags (`DSpace 6.3`, `EPrints 3.4`). */
  def generators(html: String): List[String] = metaTags(html).collect { case ("generator", g) if g.nonEmpty => g }

  /** Pure: the `citation_pdf_url`s a page names, made absolute against `pageUrl`, http(s) only. */
  def citationPdfUrls(html: String, pageUrl: String): List[String] =
    metaTags(html).collect { case ("citation_pdf_url", u) if u.nonEmpty => absolute(u, pageUrl) }.flatten.distinct

  /** Pure: `href` against the page it is on (`/bitstream/…` → `https://host/bitstream/…`). */
  def absolute(href: String, pageUrl: String): Option[String] =
    val h = href.trim
    if "(?i)^https?://".r.findPrefixOf(h).isDefined then Some(h)
    else
      val origin = "(?i)^(https?://[^/?#]+)".r.findFirstMatchIn(pageUrl.trim).map(_.group(1))
      if h.startsWith("//") then Some(pageUrl.trim.takeWhile(_ != ':') + ":" + h)
      else if h.startsWith("/") then origin.map(_ + h)
      else if h.isEmpty || h.contains(":") then None
      else
        val dir = pageUrl.trim.split("[?#]").head
        Some(dir.substring(0, dir.lastIndexOf('/') + 1) + h)

  /** Pure: may `citation_pdf_url` be read on this page? Never on a denied (publisher) host nor a
    * journal platform's page (`deny-generators`: Open Journal Systems); otherwise
    * when an OA index calls the location a repository, the host is allowed or academic, or the page
    * names a repository platform as its generator. */
  def isRepository(pageUrl: String, indexSaysRepository: Boolean, html: String, rules: Rules): Boolean =
    host(pageUrl).exists { h =>
      val gs = generators(html)
      !rules.denied(h) && !rules.journalGenerator(gs) &&
        (indexSaysRepository || rules.allowed(h) || rules.academic(h) || rules.repositoryGenerator(gs))
    }

  /** Pure: the PDFs to try from a repository page — its `citation_pdf_url`s, none on a denied host;
    * nothing at all when the page is not a repository's. An open-access publisher's open page
    * (`oaPublisher`, [[oaPublisherPage]]) gives its `citation_pdf_url`s and its OJS PDF galleys, an
    * OJS viewer link turned into its download, on the page's own host or another OA publisher's only. */
  def pdfsOf(pageUrl: String, indexSaysRepository: Boolean, html: String, rules: Rules,
      oaPublisher: Boolean = false): List[String] =
    if oaPublisher then
      val own = host(pageUrl)
      (citationPdfUrls(html, pageUrl) ++ galleyPdfUrls(html, pageUrl)).map(ojsDownload).distinct
        .filter(u => host(u).exists(h => own.contains(h) || rules.oaPublisher(h)))
    else if !isRepository(pageUrl, indexSaysRepository, html, rules) then Nil
    else citationPdfUrls(html, pageUrl).filterNot(u => host(u).exists(rules.denied))

  private val GalleyLink = "(?is)<a\\s+([^>]*)>".r
  private val GalleyJson = "(?is)\"label\"\\s*:\\s*\"PDF\"\\s*,\\s*\"href\"\\s*:\\s*\"([^\"]+)\"".r
  private val OjsView    = "(?i)/article/view/(\\d+)/(\\d+)(?=$|[?#])".r

  /** Pure: an Open Journal Systems page's PDF galleys — its `<a class="obj_galley_link pdf">` links,
    * and the galleys an OJS 3.5+ theme lists as JSON (`{"label":"PDF","href":…}`, escaped inside an
    * attribute) — made absolute against `pageUrl`, http(s) only. */
  def galleyPdfUrls(html: String, pageUrl: String): List[String] =
    val page = html.take(MaxHead)
    val links = GalleyLink.findAllMatchIn(page).flatMap { m =>
      val attrs = Attr.findAllMatchIn(m.group(1)).map { a =>
        a.group(1).toLowerCase -> Option(a.group(2)).orElse(Option(a.group(3))).orElse(Option(a.group(4))).getOrElse("")
      }.toMap
      val cls = attrs.getOrElse("class", "").toLowerCase.split("\\s+").toSet
      attrs.get("href").filter(_ => cls("obj_galley_link") && cls("pdf")).map(decode)
    }.toList
    val unescaped = page.replace("\\u0022", "\"").replace("&quot;", "\"").replace("\\\\/", "/").replace("\\/", "/")
    val listed = GalleyJson.findAllMatchIn(unescaped).map(_.group(1)).toList
    (links ++ listed).flatMap(absolute(_, pageUrl)).filter(u => "(?i)^https?://".r.findPrefixOf(u).isDefined).distinct

  /** Pure: an OJS galley's viewer URL (`…/article/view/<article>/<galley>`, an HTML page around the
    * PDF) as its download (`…/article/download/<article>/<galley>`); any other URL as it is. */
  def ojsDownload(url: String): String = OjsView.replaceFirstIn(url, "/article/download/$1/$2")

  /** Pure: does a non-PDF answer look like an HTML page (worth reading for its tags)? */
  def looksLikeHtml(b: Array[Byte]): Boolean =
    val head = new String(b.take(2048), "ISO-8859-1").toLowerCase
    head.contains("<html") || head.contains("<!doctype html") || head.contains("<head") || head.contains("<meta")
