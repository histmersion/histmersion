package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.StatusCount
import com.histmersion.api.catalogue.ArticleEventLead
import com.histmersion.api.given
import com.histmersion.api.library.Source

final case class Coverage(
    slug: com.histmersion.api.research.Article.Slug,
    filter: Article.Filter,
    filterLine: Coverage.FilterLine,
    total: Coverage.Total,
    checked: Coverage.Checked,
    cited: List[Coverage.Source],
    notUsed: List[Coverage.Entry],
    citedOutside: List[com.histmersion.api.library.Citekey],
    statuses: List[StatusCount],
    edges: List[Coverage.Edge],
    searches: List[CoverageSearch],
    reasons: List[Coverage.Reason],
    drift: List[Coverage.Drift],
    eventLeads: List[ArticleEventLead] = Nil,
    critiques: List[CritiquePair] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def capped: com.histmersion.api.research.Coverage.CappedResult = (checked).value < (total).value
  def unplaced: List[com.histmersion.api.research.Coverage.UnplacedResult] = drift.filter(_.kind == DriftKind.Unplaced).flatMap(_.citekeys)
object Coverage:


  type CappedResult = CappedResult.T
  object CappedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnplacedResult = UnplacedResult.T
  object UnplacedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type FilterLine = FilterLine.T
  object FilterLine extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Total = Total.T
  object Total extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Checked = Checked.T
  object Checked extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Drift(kind: DriftKind, citekeys: List[com.histmersion.api.library.Citekey]) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema


  final case class Edge(from: com.histmersion.api.library.Citekey, kind: Edge.Kind, to: com.histmersion.api.library.Citekey) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object Edge:
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Entry(source: Source, reason: Option[Entry.Reason]) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object Entry:
    type Reason = Reason.T
    object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Reason(citekey: com.histmersion.api.library.Citekey, reason: Reason.Value) derives KindlingsCodecAsObject
  object Reason:
    type MergeResultPart1 = MergeResultPart1.T
    object MergeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MergeResultPart2Part1 = MergeResultPart2Part1.T
    object MergeResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MergeResultPart2Part2 = MergeResultPart2Part2.T
    object MergeResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MergeCurrentPart1 = MergeCurrentPart1.T
    object MergeCurrentPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MergeCurrentPart2 = MergeCurrentPart2.T
    object MergeCurrentPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ListMPart1 = ListMPart1.T
    object ListMPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ListMPart2 = ListMPart2.T
    object ListMPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[Reason] = KindlingsSchema
      .derived[Reason]
      .schema
      .description("Why the article does not cite one source its filter returns")
      .modify(_.citekey)(_.description("The source's citekey"))
      .modify(_.reason)(
        _.description(
          "One plain sentence: why it is left out (wrong region, period or sub-problem; no finding on the question). Empty removes it"
        )
      )

    val MaxChars = 500

    def merge(current: Map[com.histmersion.api.research.Coverage.Reason.MergeCurrentPart1, com.histmersion.api.research.Coverage.Reason.MergeCurrentPart2], stated: List[Reason]): Either[com.histmersion.api.research.Coverage.Reason.MergeResultPart1, Map[com.histmersion.api.research.Coverage.Reason.MergeResultPart2Part1, com.histmersion.api.research.Coverage.Reason.MergeResultPart2Part2]] =
      stated.foldLeft[Either[String, Map[String, String]]](Right((current).iterator.map { case (refactorKey2, refactorItem2) => ((refactorKey2).value, (refactorItem2).value) }.toMap)) { (acc, r) =>
        acc.flatMap { m =>
          val ck = Option(r.citekey).map(_.trim.stripPrefix("[[").stripSuffix("]]").trim.toLowerCase).getOrElse("")
          val text = Option(r.reason).map((_).value.trim.replaceAll("\\s+", " ")).getOrElse("")
          if !Article.Links.isCitekeyShaped(_root_.com.histmersion.api.text.WikiLinks.IsCitekeyShapedS(ck)) then Left(s"reason for '${r.citekey}': not a citekey")
          else if text.length > MaxChars then
            Left(s"reason for $ck: ${text.length} characters (at most $MaxChars — one sentence)")
          else if text.isEmpty then Right(m - ck)
          else Right(m.updated(ck, text))
        }
      }

    def list(m: Map[com.histmersion.api.research.Coverage.Reason.ListMPart1, com.histmersion.api.research.Coverage.Reason.ListMPart2]): List[Reason] = m.toList.sortBy(_._1).map(Reason(_, _))

  final case class Source(
      citekey: com.histmersion.api.library.Citekey,
      title: com.histmersion.api.library.Title,
      year: Option[com.histmersion.api.catalogue.Year],
      status: com.histmersion.api.library.Source.Status,
      authors: List[com.histmersion.api.library.AuthorName] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema
