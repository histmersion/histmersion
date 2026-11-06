package com.histmersion.api.library
import hearth.kindlings.optics.syntax.*

import io.circe.{ACursor, Json}
import com.histmersion.api.given

object Candidates:

  type ArxivWorksTagResult = ArxivWorksTagResult.T
  object ArxivWorksTagResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArxivWorksTagS = ArxivWorksTagS.T
  object ArxivWorksTagS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArxivWorksTagT = ArxivWorksTagT.T
  object ArxivWorksTagT extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArxivWorksUnescapeS = ArxivWorksUnescapeS.T
  object ArxivWorksUnescapeS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Indexes = Indexes.T
  object Indexes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SinceWatermark = SinceWatermark.T
  object SinceWatermark extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SinceLookbackDays = SinceLookbackDays.T
  object SinceLookbackDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StrResult = StrResult.T
  object StrResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StrK = StrK.T
  object StrK extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArxivWorksAtom = ArxivWorksAtom.T
  object ArxivWorksAtom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenalexAuthorIdsResult = OpenalexAuthorIdsResult.T
  object OpenalexAuthorIdsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum WatchKind(val wire: WatchKind.Wire):
    case Query extends WatchKind(_root_.com.histmersion.api.library.Candidates.WatchKind.Wire("query"))
    case Citing extends WatchKind(_root_.com.histmersion.api.library.Candidates.WatchKind.Wire("citing"))
    case Author extends WatchKind(_root_.com.histmersion.api.library.Candidates.WatchKind.Wire("author"))
  object WatchKind:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.library.Candidates.WatchKind.ParseS): Option[WatchKind] = values.find(_.wire == _root_.com.histmersion.api.library.Candidates.WatchKind.Wire((s).value.trim.toLowerCase))

  enum State(val wire: State.Wire):
    case New extends State(_root_.com.histmersion.api.library.Candidates.State.Wire("new"))
    case Seen extends State(_root_.com.histmersion.api.library.Candidates.State.Wire("seen"))
    case Queued extends State(_root_.com.histmersion.api.library.Candidates.State.Wire("queued"))
    case Dismissed extends State(_root_.com.histmersion.api.library.Candidates.State.Wire("dismissed"))
  object State:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.library.Candidates.State.ParseS): Option[State] = values.find(_.wire == _root_.com.histmersion.api.library.Candidates.State.Wire((s).value.trim.toLowerCase))

  val Indexes: List[com.histmersion.api.library.Candidates.Indexes] = List(_root_.com.histmersion.api.library.Candidates.Indexes("openalex"), _root_.com.histmersion.api.library.Candidates.Indexes("crossref"), _root_.com.histmersion.api.library.Candidates.Indexes("europepmc"), _root_.com.histmersion.api.library.Candidates.Indexes("arxiv"), _root_.com.histmersion.api.library.Candidates.Indexes("core"))

  def since(watermark: Option[com.histmersion.api.library.Candidates.SinceWatermark], today: java.time.LocalDate, lookbackDays: com.histmersion.api.library.Candidates.SinceLookbackDays): java.time.LocalDate =
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
      _.title).setTo(a.title.orElse(b.title)).modify(_.year).setTo(a.year.orElse(b.year)).modify(_.authors).setTo(if a.authors.nonEmpty then a.authors else b.authors).modify(_.venue).setTo(a.venue.orElse(b.venue)).modify(_.isOa).setTo(((a.isOa) == _root_.com.histmersion.api.library.WorkRef.IsOa(true)) || ((b.isOa) == _root_.com.histmersion.api.library.WorkRef.IsOa(true))).modify(_.abstractText).setTo(a.abstractText.orElse(b.abstractText)).modify(_.arxivId).setTo(a.arxivId.orElse(b.arxivId)).modify(_.openalexId).setTo(a.openalexId.orElse(b.openalexId)
    )

  private def str(c: ACursor, k: com.histmersion.api.library.Candidates.StrK): Option[com.histmersion.api.library.Candidates.StrResult] = (c.get[String]((k).value).toOption.map(_.trim).filter(_.nonEmpty)).map(refactorValue4 => _root_.com.histmersion.api.library.Candidates.StrResult(refactorValue4))

  def europepmcWorks(json: Json): List[WorkRef] =
    json.hcursor.downField("resultList").downField("result").values.toList.flatten.map { r =>
      val c = r.hcursor
      WorkRef(
        doi = (str(c, _root_.com.histmersion.api.library.Candidates.StrK("doi"))).map((com).value.histmersion.api.library.Doi.apply),
        title = (str(c, _root_.com.histmersion.api.library.Candidates.StrK("title")).map(_.stripSuffix("."))).map(com.histmersion.api.library.Title.apply),
        year = (str(c, _root_.com.histmersion.api.library.Candidates.StrK("pubYear")).flatMap(_.toIntOption)).map(com.histmersion.api.catalogue.Year.apply),
        authors = (str(c, _root_.com.histmersion.api.library.Candidates.StrK("authorString")).toList.flatMap(_.stripSuffix(".").split(",").map(_.trim).filter(_.nonEmpty))).map(com.histmersion.api.library.AuthorName.apply),
        venue =
          (str(c, _root_.com.histmersion.api.library.Candidates.StrK("journalTitle")).orElse(c.downField("journalInfo").downField("journal").get[String]("title").toOption)).map(com.histmersion.api.library.Venue.apply),
        isOa = str(c, "isOpenAccess").contains("Y"),
        abstractText = str(c, "abstractText").map(_.replaceAll("<[^>]+>", ""))
      )
    }

  /** CORE holds full texts, so a work with one is open access by CORE's nature. */
  def coreWorks(json: Json): List[WorkRef] =
    json.hcursor.downField("results").values.toList.flatten.map { r =>
      val c = r.hcursor
      WorkRef(
        doi = (str(c, _root_.com.histmersion.api.library.Candidates.StrK("doi"))).map((com).value.histmersion.api.library.Doi.apply),
        title = (str(c, _root_.com.histmersion.api.library.Candidates.StrK("title"))).map(com.histmersion.api.library.Title.apply),
        year = (c.get[Int]("yearPublished").toOption).map(com.histmersion.api.catalogue.Year.apply),
        authors = (c.downField("authors").values.toList.flatten.flatMap(a => str(a.hcursor, _root_.com.histmersion.api.library.Candidates.StrK("name")))).map((com).value.histmersion.api.library.AuthorName.apply),
        venue = (str(c, _root_.com.histmersion.api.library.Candidates.StrK("publisher"))).map(com.histmersion.api.library.Venue.apply),
        isOa = str(c, "downloadUrl").isDefined,
        abstractText = str(c, "abstract"),
        arxivId = str(c, _root_.com.histmersion.api.library.Candidates.StrK("arxivId"))
      )
    }

  def arxivWorks(atom: com.histmersion.api.library.Candidates.ArxivWorksAtom): List[WorkRef] =
    def tag(s: com.histmersion.api.library.Candidates.ArxivWorksTagS, t: com.histmersion.api.library.Candidates.ArxivWorksTagT): Option[com.histmersion.api.library.Candidates.ArxivWorksTagResult] =
      (s"(?s)<$t[^>]*>(.*?)</$t>".r.findFirstMatchIn((s).value).map(_.group(1).replaceAll("\\s+", " ").trim).filter(_.nonEmpty)).map(refactorValue3 => _root_.com.histmersion.api.library.Candidates.ArxivWorksTagResult(refactorValue3))
    def unescape(s: com.histmersion.api.library.Candidates.ArxivWorksUnescapeS) =
      (s).value.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
    "(?s)<entry>(.*?)</entry>".r.findAllMatchIn((atom).value).map(_.group(1)).toList.map { e =>
      val id = tag(_root_.com.histmersion.api.library.Candidates.ArxivWorksTagS(e), _root_.com.histmersion.api.library.Candidates.ArxivWorksTagT("id")).map(_.replaceFirst("^https?://arxiv.org/abs/", "").replaceAll("v\\d+$", ""))
      WorkRef(
        arxivId = id,
        title = (tag(e, "title").map(unescape)).map(com.histmersion.api.library.Title.apply),
        year = (tag(e, "published").flatMap(_.take(4).toIntOption)).map(com.histmersion.api.catalogue.Year.apply),
        authors = ("(?s)<author>\\s*<name>(.*?)</name>".r.findAllMatchIn(e).map(m => unescape(m.group(1).trim)).toList).map(com.histmersion.api.library.AuthorName.apply),
        doi = (tag(_root_.com.histmersion.api.library.Candidates.ArxivWorksTagS(e), _root_.com.histmersion.api.library.Candidates.ArxivWorksTagT("arxiv:doi"))).map(com.histmersion.api.library.Doi.apply),
        venue = (Some("arXiv")).map(com.histmersion.api.library.Venue.apply),
        isOa = true,
        abstractText = tag(e, "summary").map(unescape)
      )
    }

  def openalexAuthorIds(json: Json): List[com.histmersion.api.library.Candidates.OpenalexAuthorIdsResult] =
    (json.hcursor
      .downField("authorships")
      .values
      .toList
      .flatten
      .flatMap(a => a.hcursor.downField("author").get[String]("id").toOption)
      .map(_.split("/").last)
      .filter(_.startsWith("A"))).map(refactorValue2 => _root_.com.histmersion.api.library.Candidates.OpenalexAuthorIdsResult(refactorValue2))
