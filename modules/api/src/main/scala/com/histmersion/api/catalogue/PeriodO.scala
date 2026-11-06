package com.histmersion.api.catalogue
import hearth.kindlings.optics.syntax.*

import io.circe.ACursor
import com.histmersion.api.{PeriodODataset, Years}
import com.histmersion.api.given

/** PeriodO writes ISO 8601 years, which count a year 0 (`"-2999"` is 3000 BC, `"0000"` is 1 BC). The store's signed
  * years have no year 0, so a year `≤ 0` moves down by one.
  */

object PeriodO:

  type ParseStringsResultPart1 = ParseStringsResultPart1.T
  object ParseStringsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseStringsResultPart2 = ParseStringsResultPart2.T
  object ParseStringsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseStringsKey = ParseStringsKey.T
  object ParseStringsKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DefinitionSkipDetail = DefinitionSkipDetail.T
  object DefinitionSkipDetail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DefinitionOutsideY = DefinitionOutsideY.T
  object DefinitionOutsideY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParentsFirstVisitPath = ParentsFirstVisitPath.T
  object ParentsFirstVisitPath extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type IsoYearResult = IsoYearResult.T
  object IsoYearResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsoYearRaw = IsoYearRaw.T
  object IsoYearRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MinYear = MinYear.T
  object MinYear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxYear = MaxYear.T
  object MaxYear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SpanFactor = SpanFactor.T
  object SpanFactor extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MinImplausibleSpan = MinImplausibleSpan.T
  object MinImplausibleSpan extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MinSiblings = MinSiblings.T
  object MinSiblings extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseResultPart1 = ParseResultPart1.T
  object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseJson = ParseJson.T
  object ParseJson extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AuthorityId = _root_.com.histmersion.api.catalogue.PeriodO.Authority.Id
  val AuthorityId: _root_.com.histmersion.api.catalogue.PeriodO.Authority.Id.type = _root_.com.histmersion.api.catalogue.PeriodO.Authority.Id
  type DefinitionId = _root_.com.histmersion.api.catalogue.PeriodO.Definition.Id
  val DefinitionId: _root_.com.histmersion.api.catalogue.PeriodO.Definition.Id.type = _root_.com.histmersion.api.catalogue.PeriodO.Definition.Id
  type BoundResult = BoundResult.T
  object BoundResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BoundOuter = BoundOuter.T
  object BoundOuter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type YearAtResult = YearAtResult.T
  object YearAtResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type YearAtKey = YearAtKey.T
  object YearAtKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StrResult = StrResult.T
  object StrResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StrKey = StrKey.T
  object StrKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IntResult = IntResult.T
  object IntResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IntKey = IntKey.T
  object IntKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToPeriodsExistingPart1 = ToPeriodsExistingPart1.T
  object ToPeriodsExistingPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToPeriodsExistingPart2 = ToPeriodsExistingPart2.T
  object ToPeriodsExistingPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToPeriodsRoots = ToPeriodsRoots.T
  object ToPeriodsRoots extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def isoYear(raw: com.histmersion.api.catalogue.PeriodO.IsoYearRaw): Option[com.histmersion.api.catalogue.PeriodO.IsoYearResult] =
    Years.parseIso(raw)

  /** The Lower Palaeolithic starts around 3.3 million years ago, and no period ends after 2100. */
  val MinYear: com.histmersion.api.catalogue.PeriodO.MinYear = _root_.com.histmersion.api.catalogue.PeriodO.MinYear(-3000000L)
  val MaxYear: com.histmersion.api.catalogue.PeriodO.MaxYear = _root_.com.histmersion.api.catalogue.PeriodO.MaxYear(2100L)

  /** A lost minus sign lands a period across BCE into CE (EAMENA's "Old Kingdom", -2701 → 2150), but long spans alone
    * are normal in deep time (a Lower Palaeolithic of 1.2 million years), so length is judged against the authority's
    * other definitions for the same region.
    */
  val SpanFactor: com.histmersion.api.catalogue.PeriodO.SpanFactor = _root_.com.histmersion.api.catalogue.PeriodO.SpanFactor(3L)
  val MinImplausibleSpan: com.histmersion.api.catalogue.PeriodO.MinImplausibleSpan = _root_.com.histmersion.api.catalogue.PeriodO.MinImplausibleSpan(1000L)

  val MinSiblings: com.histmersion.api.catalogue.PeriodO.MinSiblings = _root_.com.histmersion.api.catalogue.PeriodO.MinSiblings(3)

  def parse(json: com.histmersion.api.catalogue.PeriodO.ParseJson): Either[com.histmersion.api.catalogue.PeriodO.ParseResultPart1, PeriodODataset] =
    io.circe.parser.parse(json).left.map(e => s"not JSON: ${e.getMessage}").flatMap { doc =>
      val c = doc.hcursor
      val overlay = c.downField("histmersion")
      def strings(key: com.histmersion.api.catalogue.PeriodO.ParseStringsKey): List[(com.histmersion.api.catalogue.PeriodO.ParseStringsResultPart1, com.histmersion.api.catalogue.PeriodO.ParseStringsResultPart2)] = overlay
        .downField(key)
        .focus
        .flatMap(_.asObject)
        .map(_.toList.flatMap((k, v) => v.asString.map(k -> _)))
        .getOrElse(Nil)
      val presets = strings("presets").map((id, r) =>
        PresetRegion
          .fromWire(r)
          .map(id -> _)
          .toRight(
            s"$id: no preset region '$r' (known: " +
              PresetRegion.values.map(_.wire).mkString(", ") + ")"
          )
      )
      for
        auths <- c
          .downField("authorities")
          .focus
          .flatMap(_.asObject)
          .toRight("not a PeriodO dataset: no `authorities` object")
        ps <- presets.collectFirst { case Left(e) => e }.toLeft(presets.collect { case Right(p) => p }.toMap)
      yield
        val read = auths.toList.map((id, a) => authority(id, a.hcursor))
        PeriodODataset(
          read.map(_._1),
          strings("parents").toMap,
          read.flatMap(_._2),
          ps,
          overlay.downField("version").focus.flatMap(_.asNumber).flatMap(_.toInt)
        )
    }

  private def authority(
      id: com.histmersion.api.catalogue.PeriodO.AuthorityId,
      c: ACursor
  ): (com.histmersion.api.catalogue.PeriodO.Authority, List[SkippedPeriod]) =
    val src = c.downField("source")
    val title = str(src, "title").orElse(str(src.downField("partOf"), "title")).getOrElse(id)
    val year = int(src, "yearPublished").orElse(int(src.downField("partOf"), "yearPublished"))
    val defs = c
      .downField("periods")
      .focus
      .flatMap(_.asObject)
      .map(_.toList)
      .getOrElse(Nil)
      .map((pid, p) => definition(_root_.com.histmersion.api.catalogue.PeriodO.DefinitionId(pid), p.hcursor))
    val (implausible, kept) = implausibleSpans(defs.flatMap(_.toOption))
    (
      com.histmersion.api.catalogue.PeriodO.Authority(id, title, year, kept),
      defs.flatMap(_.left.toOption) ++ implausible
    )

  def implausibleSpans(
      defs: List[com.histmersion.api.catalogue.PeriodO.Definition]
  ): (List[SkippedPeriod], List[com.histmersion.api.catalogue.PeriodO.Definition]) =
    val umbrellas = defs.flatMap(_.broader).toSet
    def span(d: com.histmersion.api.catalogue.PeriodO.Definition) = d.period.end - d.period.start
    val judged = defs.filterNot(d => umbrellas.contains(d.id))
    val verdicts = defs.map { d =>
      val others = judged.filter(o => o.id != d.id && o.region == d.region)
      val longest = others.map(span).maxOption.getOrElse(0L)
      val crossesEra = d.period.start < 0 && d.period.end > 0
      if !crossesEra || umbrellas.contains(d.id) || others.size < MinSiblings || span(d) < MinImplausibleSpan ||
        span(d) <= SpanFactor * longest
      then Right(d)
      else
        Left(
          SkippedPeriod(
            d.id,
            d.label,
            Period.Skip.ImplausibleSpan,
            s"spans ${span(d)} years (${d.period.start} → ${d.period.end}), more than $SpanFactor× the longest " +
              s"other ${d.region.getOrElse("unlocated")} period of its authority ($longest years)"
          )
        )
    }
    (verdicts.flatMap(_.left.toOption), verdicts.flatMap(_.toOption))

  private def definition(
      id: com.histmersion.api.catalogue.PeriodO.DefinitionId,
      c: ACursor
  ): Either[SkippedPeriod, com.histmersion.api.catalogue.PeriodO.Definition] =
    // FASTI labels its Ukrainian periods in Ukrainian, with the English label as an alternative
    val own = str(c, "label").map(_.trim).filter(_.nonEmpty)
    val english = str(c, "languageTag").exists(_.toLowerCase.startsWith("en"))
    val label = (if english then own else None)
      .orElse(
        c.downField("localizedLabels")
          .downField("en")
          .downArray
          .focus
          .flatMap(_.asString)
          .map(_.trim)
          .filter(_.nonEmpty)
      )
      .orElse(own)
      .getOrElse(id)
    val start = bound(c.downField("start").downField("in"), _root_.com.histmersion.api.catalogue.PeriodO.BoundOuter("earliestYear"))
    val stop = bound(c.downField("stop").downField("in"), _root_.com.histmersion.api.catalogue.PeriodO.BoundOuter("latestYear"))
    val region = str(c, "spatialCoverageDescription").map(_.trim).filter(_.nonEmpty).orElse {
      val labels = c
        .downField("spatialCoverage")
        .focus
        .flatMap(_.asArray)
        .toList
        .flatten
        .flatMap(_.hcursor.downField("label").focus.flatMap(_.asString))
        .map(_.trim)
        .filter(_.nonEmpty)
      Option.when(labels.nonEmpty)(labels.mkString(", "))
    }
    def skip(reason: Period.Skip, detail: com.histmersion.api.catalogue.PeriodO.DefinitionSkipDetail) = Left(SkippedPeriod(id, label, reason, detail))
    def outside(y: com.histmersion.api.catalogue.PeriodO.DefinitionOutsideY) = (y).value < MinYear || y > MaxYear
    (start, stop) match
      case (Some(s), Some(e)) if outside(s) || outside(e) =>
        skip(Period.Skip.OutOfRange, _root_.com.histmersion.api.catalogue.PeriodO.DefinitionSkipDetail(s"$s → $e lies outside $MinYear..$MaxYear"))
      case (Some(s), Some(e)) if s <= e =>
        Right(com.histmersion.api.catalogue.PeriodO.Definition(id, label, Period(s, e), region, str(c, _root_.com.histmersion.api.catalogue.PeriodO.StrKey("broader"))))
      case (Some(s), Some(e)) => skip(Period.Skip.EndsBeforeStart, _root_.com.histmersion.api.catalogue.PeriodO.DefinitionSkipDetail(s"starts after it ends ($s > $e)"))
      case _                  => skip(Period.Skip.NoSpan, _root_.com.histmersion.api.catalogue.PeriodO.DefinitionSkipDetail("no readable start or stop year"))

  private def bound(in: ACursor, outer: com.histmersion.api.catalogue.PeriodO.BoundOuter): Option[com.histmersion.api.catalogue.PeriodO.BoundResult] =
    yearAt(in, "year").orElse(yearAt(in, outer))

  private def yearAt(c: ACursor, key: com.histmersion.api.catalogue.PeriodO.YearAtKey): Option[com.histmersion.api.catalogue.PeriodO.YearAtResult] =
    c.downField(key).focus.flatMap(j => j.asString.orElse(j.asNumber.map(_.toString))).flatMap(isoYear)

  private def str(c: ACursor, key: com.histmersion.api.catalogue.PeriodO.StrKey): Option[com.histmersion.api.catalogue.PeriodO.StrResult] = c.downField(key).focus.flatMap(_.asString)
  private def int(c: ACursor, key: com.histmersion.api.catalogue.PeriodO.IntKey): Option[com.histmersion.api.catalogue.PeriodO.IntResult] =
    c.downField(key).focus.flatMap(j => j.asNumber.flatMap(_.toInt).orElse(j.asString.flatMap(_.toIntOption)))

  def toPeriods(
      ds: PeriodODataset,
      existing: Map[com.histmersion.api.catalogue.PeriodO.ToPeriodsExistingPart1, Option[com.histmersion.api.catalogue.PeriodO.ToPeriodsExistingPart2]] = Map.empty,
      roots: com.histmersion.api.catalogue.PeriodO.ToPeriodsRoots = _root_.com.histmersion.api.catalogue.PeriodO.ToPeriodsRoots(false)
  ): List[HistoricalPeriod] =
    val defs = ds.authorities.flatMap(a => a.periods.map(a -> _))
    val ids = defs.map(_._2.id).toSet
    val authorityRoots =
      if !(roots).value then Nil
      else
        ds.authorities.filter(_.periods.nonEmpty).map { a =>
          HistoricalPeriod(
            a.id,
            a.title,
            Some(_root_.com.histmersion.api.catalogue.HistoricalPeriod.Authority(s"PeriodO ${a.id}")),
            Period(a.periods.map(_.period.start).min, a.periods.map(_.period.end).max),
            parentId = existing.get(a.id).flatten
          )
        }
    val rows = defs.map { (a, d) =>
      val parent = ds.parents.get(d.id).filter(ids.contains) match
        case curated @ Some(_) => curated
        case None              =>
          val own = d.broader.filter(ids.contains).orElse(Option.when((roots).value)(a.id))
          if ds.curated then own else existing.getOrElse(d.id, own)
      HistoricalPeriod(
        d.id,
        d.label,
        Some(a.cite(d.id)),
        d.period,
        d.region,
        parent.filter(_ != d.id),
        ds.presets.get(d.id)
      )
    }
    parentsFirst(authorityRoots ++ rows)

  private[api] def parentsFirst(rows: List[HistoricalPeriod]): List[HistoricalPeriod] =
    val byId = rows.map(r => r.id -> r).toMap
    val out = scala.collection.mutable.LinkedHashMap.empty[String, HistoricalPeriod]
    def visit(r: HistoricalPeriod, path: Set[com.histmersion.api.catalogue.PeriodO.ParentsFirstVisitPath]): Unit =
      if !out.contains(r.id) then
        val parent = r.parentId.flatMap(byId.get)
        parent match
          case Some(p) if path.contains(p.id) => out(r.id) = r.modify(_.parentId).setTo(None)
          case Some(p)                        =>
            visit(p, path + r.id)
            if !out.contains(r.id) then out(r.id) = r
          case None => out(r.id) = r
    rows.foreach(r => visit(r, Set(r.id)))
    out.values.toList

  final case class Authority(id: Authority.Id, title: Authority.Title, year: Option[Authority.Year], periods: List[Definition]):
    /** A PeriodO id resolves as `http://n2t.net/ark:/99152/<id>`. */
    def cite(periodId: com.histmersion.api.catalogue.PeriodO.Authority.CitePeriodId): com.histmersion.api.catalogue.PeriodO.Authority.CiteResult =
      _root_.com.histmersion.api.catalogue.PeriodO.Authority.CiteResult(s"PeriodO $periodId · $title${year.fold("")(y => s" ($y)")}")

  object Authority:
    type CiteResult = CiteResult.T
    object CiteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CitePeriodId = CitePeriodId.T
    object CitePeriodId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Year = Year.T
    object Year extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Definition(
      id: Definition.Id,
      label: Definition.Label,
      period: Period,
      region: Option[Definition.Region],
      broader: Option[Definition.Broader]
  )

  object Definition:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Region = Region.T
    object Region extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Broader = Broader.T
    object Broader extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
