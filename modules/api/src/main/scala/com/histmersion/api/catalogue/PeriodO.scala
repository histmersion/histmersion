package com.histmersion.api.catalogue
import hearth.kindlings.optics.syntax.*

import io.circe.ACursor
import com.histmersion.api.{PeriodODataset, Years}
import com.histmersion.api.given

/** PeriodO writes ISO 8601 years, which count a year 0 (`"-2999"` is 3000 BC, `"0000"` is 1 BC). The store's signed
  * years have no year 0, so a year `≤ 0` moves down by one.
  */

object PeriodO:
  type MaxYearNumber = MaxYearNumber.T
  object MaxYearNumber extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MinImplausibleSpanNumber = MinImplausibleSpanNumber.T
  object MinImplausibleSpanNumber extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MinSiblingsCount = MinSiblingsCount.T
  object MinSiblingsCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MinYearNumber = MinYearNumber.T
  object MinYearNumber extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SpanFactorNumber = SpanFactorNumber.T
  object SpanFactorNumber extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type MinYear = MinYearValue
  type MinYearValue = com.histmersion.api.catalogue.PeriodO.MinYearNumber
  val MinYearValue: _root_.com.histmersion.api.catalogue.PeriodO.MinYearNumber.type = _root_.com.histmersion.api.catalogue.PeriodO.MinYearNumber
  type MaxYear = MaxYearValue
  type MaxYearValue = com.histmersion.api.catalogue.PeriodO.MaxYearNumber
  val MaxYearValue: _root_.com.histmersion.api.catalogue.PeriodO.MaxYearNumber.type = _root_.com.histmersion.api.catalogue.PeriodO.MaxYearNumber
  type SpanFactor = SpanFactorValue
  type SpanFactorValue = com.histmersion.api.catalogue.PeriodO.SpanFactorNumber
  val SpanFactorValue: _root_.com.histmersion.api.catalogue.PeriodO.SpanFactorNumber.type = _root_.com.histmersion.api.catalogue.PeriodO.SpanFactorNumber
  type MinImplausibleSpan = MinImplausibleSpanValue
  type MinImplausibleSpanValue = com.histmersion.api.catalogue.PeriodO.MinImplausibleSpanNumber
  val MinImplausibleSpanValue: _root_.com.histmersion.api.catalogue.PeriodO.MinImplausibleSpanNumber.type = _root_.com.histmersion.api.catalogue.PeriodO.MinImplausibleSpanNumber
  type MinSiblings = MinSiblingsValue
  type MinSiblingsValue = com.histmersion.api.catalogue.PeriodO.MinSiblingsCount
  val MinSiblingsValue: _root_.com.histmersion.api.catalogue.PeriodO.MinSiblingsCount.type = _root_.com.histmersion.api.catalogue.PeriodO.MinSiblingsCount
  type AuthorityId = _root_.com.histmersion.api.catalogue.PeriodO.Authority.Id
  val AuthorityId: _root_.com.histmersion.api.catalogue.PeriodO.Authority.Id.type = _root_.com.histmersion.api.catalogue.PeriodO.Authority.Id
  type DefinitionId = _root_.com.histmersion.api.catalogue.PeriodO.Definition.Id
  val DefinitionId: _root_.com.histmersion.api.catalogue.PeriodO.Definition.Id.type = _root_.com.histmersion.api.catalogue.PeriodO.Definition.Id

  def isoYear(raw: String): Option[com.histmersion.api.catalogue.Period.IsoYear] =
    Years.parseIso(raw)

  /** The Lower Palaeolithic starts around 3.3 million years ago, and no period ends after 2100. */
  val MinYear: com.histmersion.api.catalogue.PeriodO.MinYear = _root_.com.histmersion.api.catalogue.PeriodO.MinYearNumber(-3000000L)
  val MaxYear: com.histmersion.api.catalogue.PeriodO.MaxYear = _root_.com.histmersion.api.catalogue.PeriodO.MaxYearNumber(2100L)

  /** A lost minus sign lands a period across BCE into CE (EAMENA's "Old Kingdom", -2701 → 2150), but long spans alone
    * are normal in deep time (a Lower Palaeolithic of 1.2 million years), so length is judged against the authority's
    * other definitions for the same region.
    */
  val SpanFactor: com.histmersion.api.catalogue.PeriodO.SpanFactor = _root_.com.histmersion.api.catalogue.PeriodO.SpanFactorNumber(3L)
  val MinImplausibleSpan: com.histmersion.api.catalogue.PeriodO.MinImplausibleSpan = _root_.com.histmersion.api.catalogue.PeriodO.MinImplausibleSpanNumber(1000L)

  val MinSiblings: com.histmersion.api.catalogue.PeriodO.MinSiblings = _root_.com.histmersion.api.catalogue.PeriodO.MinSiblingsCount(3)

  def parse(json: String): Either[com.histmersion.api.catalogue.PeriodO.Problem, PeriodODataset] =
    io.circe.parser.parse((json).value).left.map(e => s"not JSON: ${e.getMessage}").flatMap { doc =>
      val c = doc.hcursor
      val overlay = c.downField("histmersion")
      def strings(key: com.histmersion.api.catalogue.PeriodO.Wire): List[(com.histmersion.api.catalogue.PeriodO.Definition.Id, String)] = (overlay
        .downField((key).value)
        .focus
        .flatMap(_.asObject)
        .map(_.toList.flatMap((k, v) => v.asString.map(k -> _)))
        .getOrElse(Nil)).map(refactorValue2 => { val refactorTuple = refactorValue2; (_root_.com.histmersion.api.catalogue.PeriodO.Definition.Id(refactorTuple._1), (refactorTuple._2)) })
      val presets = strings(_root_.com.histmersion.api.catalogue.PeriodO.Wire("presets")).map((id, r) =>
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
        val read = auths.toList.map((id, a) => authority(_root_.com.histmersion.api.catalogue.PeriodO.Authority.Id(id), a.hcursor))
        PeriodODataset(
          read.map(_._1),
          strings(_root_.com.histmersion.api.catalogue.PeriodO.Wire("parents")).toMap,
          read.flatMap(_._2),
          ps,
          overlay.downField("version").focus.flatMap(_.asNumber).flatMap((refactorCallback2 => (refactorCallback2.toInt).map(refactorValue3 => _root_.com.histmersion.api.PeriodODataset.Version(refactorValue3))))
        )
    }

  private def authority(
      id: com.histmersion.api.catalogue.PeriodO.AuthorityId,
      c: ACursor
  ): (com.histmersion.api.catalogue.PeriodO.Authority, List[SkippedPeriod]) =
    val src = c.downField("source")
    val title = str(src, _root_.com.histmersion.api.catalogue.PeriodO.Wire("title")).orElse(str(src.downField("partOf"), _root_.com.histmersion.api.catalogue.PeriodO.Wire("title"))).getOrElse(id)
    val year = int(src, _root_.com.histmersion.api.catalogue.PeriodO.Wire("yearPublished")).orElse(int(src.downField("partOf"), _root_.com.histmersion.api.catalogue.PeriodO.Wire("yearPublished")))
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
    def span(d: com.histmersion.api.catalogue.PeriodO.Definition) = (d.period.end).value - d.period.start
    val judged = defs.filterNot(d => umbrellas.contains(d.id))
    val verdicts = defs.map { d =>
      val others = judged.filter(o => o.id != d.id && o.region == d.region)
      val longest = others.map(span).maxOption.getOrElse(0L)
      val crossesEra = (d.period.start).value < 0 && (d.period.end).value > 0
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
    val own = str(c, _root_.com.histmersion.api.catalogue.PeriodO.Wire("label")).map((_).value.trim).filter(_.nonEmpty)
    val english = str(c, _root_.com.histmersion.api.catalogue.PeriodO.Wire("languageTag")).exists((_).value.toLowerCase.startsWith("en"))
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
    val start = bound(c.downField("start").downField("in"), _root_.com.histmersion.api.catalogue.PeriodO.Wire("earliestYear"))
    val stop = bound(c.downField("stop").downField("in"), _root_.com.histmersion.api.catalogue.PeriodO.Wire("latestYear"))
    val region = str(c, _root_.com.histmersion.api.catalogue.PeriodO.Wire("spatialCoverageDescription")).map((_).value.trim).filter(_.nonEmpty).orElse {
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
    def skip(reason: Period.Skip, detail: com.histmersion.api.catalogue.PeriodO.Problem) = Left(SkippedPeriod(id, label, reason, detail))
    def outside(y: com.histmersion.api.catalogue.Year) = (y).value < (MinYear).value || (y).value > MaxYear
    (start, stop) match
      case (Some(s), Some(e)) if outside(s) || outside(e) =>
        skip(Period.Skip.OutOfRange, _root_.com.histmersion.api.catalogue.PeriodO.Problem(s"$s → $e lies outside $MinYear..$MaxYear"))
      case (Some(s), Some(e)) if s <= e =>
        Right(com.histmersion.api.catalogue.PeriodO.Definition(id, label, Period(s, e), (region).map(refactorValue => _root_.com.histmersion.api.catalogue.PeriodO.Definition.Region(refactorValue)), str(c, _root_.com.histmersion.api.catalogue.PeriodO.Wire("broader"))))
      case (Some(s), Some(e)) => skip(Period.Skip.EndsBeforeStart, _root_.com.histmersion.api.catalogue.PeriodO.Problem(s"starts after it ends ($s > $e)"))
      case _                  => skip(Period.Skip.NoSpan, _root_.com.histmersion.api.catalogue.PeriodO.Problem("no readable start or stop year"))

  private def bound(in: ACursor, outer: com.histmersion.api.catalogue.PeriodO.Wire): Option[com.histmersion.api.catalogue.Year] =
    (yearAt(in, _root_.com.histmersion.api.catalogue.PeriodO.Wire("year")).orElse(yearAt(in, _root_.com.histmersion.api.catalogue.PeriodO.Wire((outer).value)))).map(refactorValue7 => _root_.com.histmersion.api.catalogue.Year((refactorValue7).value))

  private def yearAt(c: ACursor, key: com.histmersion.api.catalogue.PeriodO.Wire): Option[com.histmersion.api.catalogue.Year] =
    c.downField((key).value).focus.flatMap(j => j.asString.orElse(j.asNumber.map(_.toString))).flatMap((isoYear).map(refactorValue10 => _root_.com.histmersion.api.catalogue.Year((refactorValue10).value)))

  private def str(c: ACursor, key: com.histmersion.api.catalogue.PeriodO.Wire): Option[String] = c.downField((key).value).focus.flatMap((refactorCallback => (refactorCallback.asString).map(refactorValue4 => (refactorValue4))))
  private def int(c: ACursor, key: com.histmersion.api.catalogue.PeriodO.Wire): Option[com.histmersion.api.catalogue.Year] =
    c.downField((key).value).focus.flatMap(j => (j.asNumber.flatMap(_.toInt).orElse(j.asString.flatMap(_.toIntOption))).map(refactorValue12 => _root_.com.histmersion.api.catalogue.Year(refactorValue12)))

  def toPeriods(
      ds: PeriodODataset,
      existing: Map[com.histmersion.api.catalogue.PeriodO.Definition.Id, Option[com.histmersion.api.catalogue.PeriodO.Definition.Id]] = Map.empty,
      roots: Boolean = (false)
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
          if ((ds.curated) == (true)) then own else existing.getOrElse(d.id, own)
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
    def visit(r: HistoricalPeriod, path: Set[com.histmersion.api.catalogue.PeriodO.Definition.Id]): Unit =
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
    def cite(periodId: com.histmersion.api.catalogue.PeriodO.Definition.Id): com.histmersion.api.catalogue.PeriodO.Authority.Citation =
      _root_.com.histmersion.api.catalogue.PeriodO.Authority.Citation(s"PeriodO $periodId · $title${year.fold("")(y => s" ($y)")}")

  object Authority:
    type Citation = Citation.T
    object Citation extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
