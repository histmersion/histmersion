package com.histmersion
package api.library
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.Cursor
import hearth.kindlings.jsoniterjson.Json
import hearth.kindlings.optics.syntax.*

import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure

object Candidates:
  type AuthorId = AuthorId.T
  object AuthorId extends Newtype[String, Pure]
  type Index = Index.T
  object Index extends Newtype[String, Pure]
  type Lookback = Lookback.T
  object Lookback extends Newtype[Int, Pure]
  type Watermark = Watermark.T
  object Watermark extends Newtype[String, Pure]

  type Indexes = IndexesValue
  type IndexesValue = Index
  val IndexesValue: Index.type = Index

  enum WatchKind(val wire: WatchKind.Wire):
    case Query extends WatchKind(WatchKind.Wire("query"))
    case Citing extends WatchKind(WatchKind.Wire("citing"))
    case Author extends WatchKind(WatchKind.Wire("author"))
  object WatchKind:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def parse(s: Wire): Option[WatchKind] = values.find(_.wire == Wire((s).value.trim.toLowerCase))

  enum State(val wire: State.Wire):
    case New extends State(State.Wire("new"))
    case Seen extends State(State.Wire("seen"))
    case Queued extends State(State.Wire("queued"))
    case Dismissed extends State(State.Wire("dismissed"))
  object State:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def parse(s: Wire): Option[State] = values.find(_.wire == Wire((s).value.trim.toLowerCase))

  val Indexes: List[Indexes] =
    List(Index("openalex"), Index("crossref"), Index("europepmc"), Index("arxiv"), Index("core"))

  def since(watermark: Option[Watermark], today: java.time.LocalDate, lookbackDays: Lookback): java.time.LocalDate =
    watermark
      .flatMap(w => scala.util.Try(java.time.LocalDate.parse((w).value.take(10))).toOption)
      .getOrElse(today.minusDays(math.max(0, (lookbackDays).value).toLong))

  final case class Found(key: Found.Key, work: WorkRef, by: Found.By)

  object Found:
    type Key = Key.T
    object Key extends Newtype[String, Pure]
    type By = By.T
    object By extends Newtype[String, Pure]

  def richer(a: WorkRef, b: WorkRef): WorkRef =
    a.modify(_.title)
      .setTo(a.title.orElse(b.title))
      .modify(_.year)
      .setTo(a.year.orElse(b.year))
      .modify(_.authors)
      .setTo(if a.authors.nonEmpty then a.authors else b.authors)
      .modify(_.venue)
      .setTo(a.venue.orElse(b.venue))
      .modify(_.isOa)
      .setTo(WorkRef.IsOa(a.isOa.value || b.isOa.value))
      .modify(_.abstractText)
      .setTo(a.abstractText.orElse(b.abstractText))
      .modify(_.arxivId)
      .setTo(a.arxivId.orElse(b.arxivId))
      .modify(_.openalexId)
      .setTo(a.openalexId.orElse(b.openalexId))

  def europepmcWorks(json: Json): List[WorkRef] =
    json.cursor.downField("resultList").downField("result").values.toList.flatten.map { r =>
      val c = r.cursor
      WorkRef(
        doi = (c.downField("doi").filled).map((com).histmersion.api.library.Doi.apply),
        title = (c.downField("title").filled.map((_).stripSuffix("."))).map(Title.apply),
        year = (c.downField("pubYear").filled.flatMap((_).toIntOption)).map(api.catalogue.Year.apply),
        authors = (c
          .downField("authorString")
          .filled
          .toList
          .flatMap((_).stripSuffix(".").split(",").map(_.trim).filter(_.nonEmpty)))
          .map(AuthorName.apply),
        venue = (c
          .downField("journalTitle")
          .filled
          .orElse(c.downField("journalInfo").downField("journal").get[String]("title").toOption))
          .map(Source.Venue.apply),
        isOa = Source.Candidate.IsOa(c.downField("isOpenAccess").filled.contains("Y")),
        abstractText = c
          .downField("abstractText")
          .filled
          .map(
            (
                refactorCallback =>
                  Source.Candidate
                    .AbstractText((refactorCallback).replaceAll("<[^>]+>", ""))
            )
          )
      )
    }

  /** CORE holds full texts, so a work with one is open access by CORE's nature. */
  def coreWorks(json: Json): List[WorkRef] =
    json.cursor.downField("results").values.toList.flatten.map { r =>
      val c = r.cursor
      WorkRef(
        doi = (c.downField("doi").filled).map((com).histmersion.api.library.Doi.apply),
        title = (c.downField("title").filled).map((com).histmersion.api.library.Title.apply),
        year = (c.get[Int]("yearPublished").toOption).map(api.catalogue.Year.apply),
        authors = (c
          .downField("authors")
          .values
          .toList
          .flatten
          .flatMap(a => a.cursor.downField("name").filled))
          .map((com).histmersion.api.library.AuthorName.apply),
        venue = (c.downField("publisher").filled).map((com).histmersion.api.library.Venue.apply),
        isOa = WorkRef.IsOa(c.downField("downloadUrl").filled.isDefined),
        abstractText = c.downField("abstract").filled.map(WorkRef.AbstractText(_)),
        arxivId = c.downField("arxivId").filled.map(WorkRef.ArxivId(_))
      )
    }

  def arxivWorks(atom: String): List[WorkRef] =
    def tag(s: String, t: String): Option[String] =
      (s"(?s)<$t[^>]*>(.*?)</$t>".r
        .findFirstMatchIn((s))
        .map(_.group(1).replaceAll("\\s+", " ").trim)
        .filter(_.nonEmpty))
        .map(refactorValue3 => (refactorValue3))
    def unescape(s: String) =
      (s).replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
    "(?s)<entry>(.*?)</entry>".r.findAllMatchIn((atom)).map(_.group(1)).toList.map { e =>
      val id = tag((e), ("id")).map((_).replaceFirst("^https?://arxiv.org/abs/", "").replaceAll("v\\d+$", ""))
      WorkRef(
        arxivId = (id).map(refactorValue8 => WorkRef.ArxivId(refactorValue8)),
        title = (tag((e), ("title")).map(unescape)).map(Title.apply),
        year = (tag((e), ("published")).flatMap((_).take(4).toIntOption)).map(api.catalogue.Year.apply),
        authors = ("(?s)<author>\\s*<name>(.*?)</name>".r
          .findAllMatchIn(e)
          .map(m => unescape((m.group(1).trim)))
          .toList)
          .map(AuthorName.apply),
        doi = (tag((e), ("arxiv:doi"))).map((com).histmersion.api.library.Doi.apply),
        venue = (Some("arXiv")).map(Source.Venue.apply),
        isOa = WorkRef.IsOa(true),
        abstractText = tag(e, "summary").map(unescape).map(WorkRef.AbstractText(_))
      )
    }

  def openalexAuthorIds(json: Json): List[AuthorId] =
    (json.cursor
      .downField("authorships")
      .values
      .toList
      .flatten
      .flatMap(a => a.cursor.downField("author").get[String]("id").toOption)
      .map(_.split("/").last)
      .filter(_.startsWith("A")))
      .map(refactorValue2 => AuthorId(refactorValue2))
