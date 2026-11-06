package com.histmersion.api.library
import hearth.kindlings.optics.syntax.*

import io.circe.{ACursor, Json}
import com.histmersion.api.given

object Candidates:
  type AuthorId = AuthorId.T
  object AuthorId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Index = Index.T
  object Index extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Lookback = Lookback.T
  object Lookback extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Watermark = Watermark.T
  object Watermark extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type Indexes = IndexesValue
  type IndexesValue = com.histmersion.api.library.Candidates.Index
  val IndexesValue: _root_.com.histmersion.api.library.Candidates.Index.type = _root_.com.histmersion.api.library.Candidates.Index

  enum WatchKind(val wire: WatchKind.Wire):
    case Query extends WatchKind(_root_.com.histmersion.api.library.Candidates.WatchKind.Wire("query"))
    case Citing extends WatchKind(_root_.com.histmersion.api.library.Candidates.WatchKind.Wire("citing"))
    case Author extends WatchKind(_root_.com.histmersion.api.library.Candidates.WatchKind.Wire("author"))
  object WatchKind:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    def parse(s: com.histmersion.api.library.Candidates.WatchKind.Wire): Option[WatchKind] = values.find(_.wire == _root_.com.histmersion.api.library.Candidates.WatchKind.Wire((s).value.trim.toLowerCase))

  enum State(val wire: State.Wire):
    case New extends State(_root_.com.histmersion.api.library.Candidates.State.Wire("new"))
    case Seen extends State(_root_.com.histmersion.api.library.Candidates.State.Wire("seen"))
    case Queued extends State(_root_.com.histmersion.api.library.Candidates.State.Wire("queued"))
    case Dismissed extends State(_root_.com.histmersion.api.library.Candidates.State.Wire("dismissed"))
  object State:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    def parse(s: com.histmersion.api.library.Candidates.State.Wire): Option[State] = values.find(_.wire == _root_.com.histmersion.api.library.Candidates.State.Wire((s).value.trim.toLowerCase))

  val Indexes: List[com.histmersion.api.library.Candidates.Indexes] = List(_root_.com.histmersion.api.library.Candidates.Index("openalex"), _root_.com.histmersion.api.library.Candidates.Index("crossref"), _root_.com.histmersion.api.library.Candidates.Index("europepmc"), _root_.com.histmersion.api.library.Candidates.Index("arxiv"), _root_.com.histmersion.api.library.Candidates.Index("core"))

  def since(watermark: Option[com.histmersion.api.library.Candidates.Watermark], today: java.time.LocalDate, lookbackDays: com.histmersion.api.library.Candidates.Lookback): java.time.LocalDate =
    watermark
      .flatMap(w => scala.util.Try(java.time.LocalDate.parse((w).value.take(10))).toOption)
      .getOrElse(today.minusDays(math.max(0, (lookbackDays).value).toLong))

  final case class Found(key: Found.Key, work: WorkRef, by: Found.By)

  object Found:
    type Key = Key.T
    object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type By = By.T
    object By extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def richer(a: WorkRef, b: WorkRef): WorkRef =
    a.modify(
      _.title).setTo(a.title.orElse(b.title)).modify(_.year).setTo(a.year.orElse(b.year)).modify(_.authors).setTo(if a.authors.nonEmpty then a.authors else b.authors).modify(_.venue).setTo(a.venue.orElse(b.venue)).modify(_.isOa).setTo(((a.isOa) == _root_.com.histmersion.api.library.Source.Candidate.IsOa(true)) || ((b.isOa) == _root_.com.histmersion.api.library.Source.Candidate.IsOa(true))).modify(_.abstractText).setTo(a.abstractText.orElse(b.abstractText)).modify(_.arxivId).setTo(a.arxivId.orElse(b.arxivId)).modify(_.openalexId).setTo(a.openalexId.orElse(b.openalexId)
    )

  private def str(c: ACursor, k: String): Option[String] = (c.get[String]((k)).toOption.map(_.trim).filter(_.nonEmpty)).map(refactorValue4 => (refactorValue4))

  def europepmcWorks(json: Json): List[WorkRef] =
    json.hcursor.downField("resultList").downField("result").values.toList.flatten.map { r =>
      val c = r.hcursor
      WorkRef(
        doi = (str(c, ("doi"))).map((com).histmersion.api.library.Doi.apply),
        title = (str(c, ("title")).map((_).stripSuffix("."))).map(com.histmersion.api.library.Title.apply),
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
        isOa = str(c, "downloadUrl").isDefined,
        abstractText = str(c, "abstract"),
        arxivId = str(c, ("arxivId"))
      )
    }

  def arxivWorks(atom: String): List[WorkRef] =
    def tag(s: String, t: String): Option[String] =
      (s"(?s)<$t[^>]*>(.*?)</$t>".r.findFirstMatchIn((s)).map(_.group(1).replaceAll("\\s+", " ").trim).filter(_.nonEmpty)).map(refactorValue3 => (refactorValue3))
    def unescape(s: String) =
      (s).replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
    "(?s)<entry>(.*?)</entry>".r.findAllMatchIn((atom)).map(_.group(1)).toList.map { e =>
      val id = tag((e), ("id")).map((_).value.replaceFirst("^https?://arxiv.org/abs/", "").replaceAll("v\\d+$", ""))
      WorkRef(
        arxivId = (id).map(refactorValue8 => _root_.com.histmersion.api.library.WorkRef.ArxivId(refactorValue8)),
        title = (tag((e), ("title")).map(unescape)).map(com.histmersion.api.library.Title.apply),
        year = (tag((e), ("published")).flatMap((_).value.take(4).toIntOption)).map(com.histmersion.api.catalogue.Year.apply),
        authors = ("(?s)<author>\\s*<name>(.*?)</name>".r.findAllMatchIn(e).map(m => unescape((m.group(1).trim))).toList).map(com.histmersion.api.library.AuthorName.apply),
        doi = (tag((e), ("arxiv:doi"))).map((com).value.histmersion.api.library.Doi.apply),
        venue = (Some("arXiv")).map(com.histmersion.api.library.Source.Venue.apply),
        isOa = true,
        abstractText = tag(e, "summary").map(unescape)
      )
    }

  def openalexAuthorIds(json: Json): List[com.histmersion.api.library.Candidates.AuthorId] =
    (json.hcursor
      .downField("authorships")
      .values
      .toList
      .flatten
      .flatMap(a => a.hcursor.downField("author").get[String]("id").toOption)
      .map(_.split("/").last)
      .filter(_.startsWith("A"))).map(refactorValue2 => _root_.com.histmersion.api.library.Candidates.AuthorId(refactorValue2))
