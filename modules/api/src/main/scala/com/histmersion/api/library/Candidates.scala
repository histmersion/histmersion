package com.histmersion
package api.library
import hearth.kindlings.optics.syntax.*

import io.circe.{ACursor, Json}
import com.histmersion.api.given

object Candidates:
  type AuthorId = AuthorId.T
  object AuthorId extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Index = Index.T
  object Index extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Lookback = Lookback.T
  object Lookback extends Newtype[Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Watermark = Watermark.T
  object Watermark extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type Indexes = IndexesValue
  type IndexesValue = Index
  val IndexesValue: Index.type = Index

  enum WatchKind(val wire: WatchKind.Wire):
    case Query extends WatchKind(WatchKind.Wire("query"))
    case Citing extends WatchKind(WatchKind.Wire("citing"))
    case Author extends WatchKind(WatchKind.Wire("author"))
  object WatchKind:
    type Wire = Wire.T
    object Wire extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    def parse(s: Wire): Option[WatchKind] = values.find(_.wire == Wire((s).value.trim.toLowerCase))

  enum State(val wire: State.Wire):
    case New extends State(State.Wire("new"))
    case Seen extends State(State.Wire("seen"))
    case Queued extends State(State.Wire("queued"))
    case Dismissed extends State(State.Wire("dismissed"))
  object State:
    type Wire = Wire.T
    object Wire extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    def parse(s: Wire): Option[State] = values.find(_.wire == Wire((s).value.trim.toLowerCase))

  val Indexes: List[Indexes] = List(Index("openalex"), Index("crossref"), Index("europepmc"), Index("arxiv"), Index("core"))

  def since(watermark: Option[Watermark], today: java.time.LocalDate, lookbackDays: Lookback): java.time.LocalDate =
    watermark
      .flatMap(w => scala.util.Try(java.time.LocalDate.parse((w).value.take(10))).toOption)
      .getOrElse(today.minusDays(math.max(0, (lookbackDays).value).toLong))

  final case class Found(key: Found.Key, work: WorkRef, by: Found.By)

  object Found:
    type Key = Key.T
    object Key extends Newtype[String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type By = By.T
    object By extends Newtype[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def richer(a: WorkRef, b: WorkRef): WorkRef =
    a.modify(
      _.title).setTo(a.title.orElse(b.title)).modify(_.year).setTo(a.year.orElse(b.year)).modify(_.authors).setTo(if a.authors.nonEmpty then a.authors else b.authors).modify(_.venue).setTo(a.venue.orElse(b.venue)).modify(_.isOa).setTo(WorkRef.IsOa(a.isOa.value || b.isOa.value)).modify(_.abstractText).setTo(a.abstractText.orElse(b.abstractText)).modify(_.arxivId).setTo(a.arxivId.orElse(b.arxivId)).modify(_.openalexId).setTo(a.openalexId.orElse(b.openalexId)
    )

  private def str(c: ACursor, k: String): Option[String] = (c.get[String]((k)).toOption.map(_.trim).filter(_.nonEmpty)).map(refactorValue4 => (refactorValue4))

  def europepmcWorks(json: Json): List[WorkRef] =
    json.hcursor.downField("resultList").downField("result").values.toList.flatten.map { r =>
      val c = r.hcursor
      WorkRef(
        doi = (str(c, ("doi"))).map((com).histmersion.api.library.Doi.apply),
        title = (str(c, ("title")).map((_).stripSuffix("."))).map(Title.apply),
        year = (str(c, ("pubYear")).flatMap((_).toIntOption)).map(com.histmersion.api.catalogue.Year.apply),
        authors = (str(c, ("authorString")).toList.flatMap((_).stripSuffix(".").split(",").map(_.trim).filter(_.nonEmpty))).map(com.histmersion.api.library.AuthorName.apply),
        venue =
          (str(c, ("journalTitle")).orElse(c.downField("journalInfo").downField("journal").get[String]("title").toOption)).map(com.histmersion.api.library.Source.Venue.apply),
        isOa = _root_.com.histmersion.api.library.Source.Candidate.IsOa(str(c, ("isOpenAccess")).contains("Y")),
        abstractText = str(c, ("abstractText")).map((refactorCallback => _root_.com.histmersion.api.library.Source.Candidate.AbstractText((refactorCallback).replaceAll("<[^>]+>", ""))))
      )
    }

  /** CORE holds full texts, so a work with one is open access by CORE's nature. */
  def coreWorks(json: Json): List[WorkRef] =
    json.hcursor.downField("results").values.toList.flatten.map { r =>
      val c = r.hcursor
      WorkRef(
        doi = (str(c, ("doi"))).map((com).histmersion.api.library.Doi.apply),
        title = (str(c, ("title"))).map((com).histmersion.api.library.Title.apply),
        year = (c.get[Int]("yearPublished").toOption).map(com.histmersion.api.catalogue.Year.apply),
        authors = (c.downField("authors").values.toList.flatten.flatMap(a => str(a.hcursor, ("name")))).map((com).histmersion.api.library.AuthorName.apply),
        venue = (str(c, ("publisher"))).map((com).histmersion.api.library.Venue.apply),
        isOa = WorkRef.IsOa(str(c, "downloadUrl").isDefined),
        abstractText = str(c, "abstract").map(WorkRef.AbstractText(_)),
        arxivId = str(c, ("arxivId")).map(WorkRef.ArxivId(_))
      )
    }

  def arxivWorks(atom: String): List[WorkRef] =
    def tag(s: String, t: String): Option[String] =
      (s"(?s)<$t[^>]*>(.*?)</$t>".r.findFirstMatchIn((s)).map(_.group(1).replaceAll("\\s+", " ").trim).filter(_.nonEmpty)).map(refactorValue3 => (refactorValue3))
    def unescape(s: String) =
      (s).replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
    "(?s)<entry>(.*?)</entry>".r.findAllMatchIn((atom)).map(_.group(1)).toList.map { e =>
      val id = tag((e), ("id")).map((_).replaceFirst("^https?://arxiv.org/abs/", "").replaceAll("v\\d+$", ""))
      WorkRef(
        arxivId = (id).map(refactorValue8 => _root_.com.histmersion.api.library.WorkRef.ArxivId(refactorValue8)),
        title = (tag((e), ("title")).map(unescape)).map(Title.apply),
        year = (tag((e), ("published")).flatMap((_).take(4).toIntOption)).map(com.histmersion.api.catalogue.Year.apply),
        authors = ("(?s)<author>\\s*<name>(.*?)</name>".r.findAllMatchIn(e).map(m => unescape((m.group(1).trim))).toList).map(com.histmersion.api.library.AuthorName.apply),
        doi = (tag((e), ("arxiv:doi"))).map((com).histmersion.api.library.Doi.apply),
        venue = (Some("arXiv")).map(com.histmersion.api.library.Source.Venue.apply),
        isOa = WorkRef.IsOa(true),
        abstractText = tag(e, "summary").map(unescape).map(WorkRef.AbstractText(_))
      )
    }

  def openalexAuthorIds(json: Json): List[AuthorId] =
    (json.hcursor
      .downField("authorships")
      .values
      .toList
      .flatten
      .flatMap(a => a.hcursor.downField("author").get[String]("id").toOption)
      .map(_.split("/").last)
      .filter(_.startsWith("A"))).map(refactorValue2 => AuthorId(refactorValue2))
