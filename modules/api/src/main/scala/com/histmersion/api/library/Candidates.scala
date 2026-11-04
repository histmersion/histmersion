package com.histmersion.api.library
import io.circe.{ACursor, Json}
import com.histmersion.api.given

object Candidates:

  enum WatchKind(val wire: String):
    case Query extends WatchKind("query")
    case Citing extends WatchKind("citing")
    case Author extends WatchKind("author")
  object WatchKind:
    def parse(s: String): Option[WatchKind] = values.find(_.wire == s.trim.toLowerCase)

  enum State(val wire: String):
    case New extends State("new")
    case Seen extends State("seen")
    case Queued extends State("queued")
    case Dismissed extends State("dismissed")
  object State:
    def parse(s: String): Option[State] = values.find(_.wire == s.trim.toLowerCase)

  val Indexes: List[String] = List("openalex", "crossref", "europepmc", "arxiv", "core")

  def since(watermark: Option[String], today: java.time.LocalDate, lookbackDays: Int): java.time.LocalDate =
    watermark
      .flatMap(w => scala.util.Try(java.time.LocalDate.parse(w.take(10))).toOption)
      .getOrElse(today.minusDays(math.max(0, lookbackDays).toLong))

  final case class Found(key: String, work: WorkRef, by: String)

  def richer(a: WorkRef, b: WorkRef): WorkRef =
    a.copy(
      title = a.title.orElse(b.title),
      year = a.year.orElse(b.year),
      authors = if a.authors.nonEmpty then a.authors else b.authors,
      venue = a.venue.orElse(b.venue),
      isOa = a.isOa || b.isOa,
      abstractText = a.abstractText.orElse(b.abstractText),
      arxivId = a.arxivId.orElse(b.arxivId),
      openalexId = a.openalexId.orElse(b.openalexId)
    )

  private def str(c: ACursor, k: String): Option[String] = c.get[String](k).toOption.map(_.trim).filter(_.nonEmpty)

  def europepmcWorks(json: Json): List[WorkRef] =
    json.hcursor.downField("resultList").downField("result").values.toList.flatten.map { r =>
      val c = r.hcursor
      WorkRef(
        doi = str(c, "doi"),
        title = str(c, "title").map(_.stripSuffix(".")),
        year = str(c, "pubYear").flatMap(_.toIntOption),
        authors = str(c, "authorString").toList.flatMap(_.stripSuffix(".").split(",").map(_.trim).filter(_.nonEmpty)),
        venue =
          str(c, "journalTitle").orElse(c.downField("journalInfo").downField("journal").get[String]("title").toOption),
        isOa = str(c, "isOpenAccess").contains("Y"),
        abstractText = str(c, "abstractText").map(_.replaceAll("<[^>]+>", ""))
      )
    }

  /** CORE holds full texts, so a work with one is open access by CORE's nature. */
  def coreWorks(json: Json): List[WorkRef] =
    json.hcursor.downField("results").values.toList.flatten.map { r =>
      val c = r.hcursor
      WorkRef(
        doi = str(c, "doi"),
        title = str(c, "title"),
        year = c.get[Int]("yearPublished").toOption,
        authors = c.downField("authors").values.toList.flatten.flatMap(a => str(a.hcursor, "name")),
        venue = str(c, "publisher"),
        isOa = str(c, "downloadUrl").isDefined,
        abstractText = str(c, "abstract"),
        arxivId = str(c, "arxivId")
      )
    }

  def arxivWorks(atom: String): List[WorkRef] =
    def tag(s: String, t: String): Option[String] =
      s"(?s)<$t[^>]*>(.*?)</$t>".r.findFirstMatchIn(s).map(_.group(1).replaceAll("\\s+", " ").trim).filter(_.nonEmpty)
    def unescape(s: String) =
      s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
    "(?s)<entry>(.*?)</entry>".r.findAllMatchIn(atom).map(_.group(1)).toList.map { e =>
      val id = tag(e, "id").map(_.replaceFirst("^https?://arxiv.org/abs/", "").replaceAll("v\\d+$", ""))
      WorkRef(
        arxivId = id,
        title = tag(e, "title").map(unescape),
        year = tag(e, "published").flatMap(_.take(4).toIntOption),
        authors = "(?s)<author>\\s*<name>(.*?)</name>".r.findAllMatchIn(e).map(m => unescape(m.group(1).trim)).toList,
        doi = tag(e, "arxiv:doi"),
        venue = Some("arXiv"),
        isOa = true,
        abstractText = tag(e, "summary").map(unescape)
      )
    }

  def openalexAuthorIds(json: Json): List[String] =
    json.hcursor
      .downField("authorships")
      .values
      .toList
      .flatten
      .flatMap(a => a.hcursor.downField("author").get[String]("id").toOption)
      .map(_.split("/").last)
      .filter(_.startsWith("A"))
