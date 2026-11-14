package com.histmersion
package api.catalogue

import com.histmersion.util.problem.{External, Problem}

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.{PeriodODataset, Years}
import com.histmersion.api.given
import com.histmersion.util.json.{Cursor, RawJson}
import com.histmersion.util.json.RawJson.*
import io.github.iltotore.iron.constraint.any.Pure

/** PeriodO writes ISO 8601 years, which count a year 0 (`"-2999"` is 3000 BC, `"0000"` is 1 BC). The store's signed
  * years have no year 0, so a year `≤ 0` moves down by one.
  */

object PeriodO:
  type MaxYearNumber = MaxYearNumber.T
  object MaxYearNumber extends Newtype[Long, Pure]
  type MinImplausibleSpanNumber = MinImplausibleSpanNumber.T
  object MinImplausibleSpanNumber extends Newtype[Long, Pure]
  type MinSiblingsCount = MinSiblingsCount.T
  object MinSiblingsCount extends Newtype[Int, Pure]
  type MinYearNumber = MinYearNumber.T
  object MinYearNumber extends Newtype[Long, Pure]
  type SpanFactorNumber = SpanFactorNumber.T
  object SpanFactorNumber extends Newtype[Long, Pure]
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  type MinYear = MinYearValue
  type MinYearValue = MinYearNumber
  val MinYearValue: MinYearNumber.type = MinYearNumber
  type MaxYear = MaxYearValue
  type MaxYearValue = MaxYearNumber
  val MaxYearValue: MaxYearNumber.type = MaxYearNumber
  type SpanFactor = SpanFactorValue
  type SpanFactorValue = SpanFactorNumber
  val SpanFactorValue: SpanFactorNumber.type = SpanFactorNumber
  type MinImplausibleSpan = MinImplausibleSpanValue
  type MinImplausibleSpanValue = MinImplausibleSpanNumber
  val MinImplausibleSpanValue: MinImplausibleSpanNumber.type = MinImplausibleSpanNumber
  type MinSiblings = MinSiblingsValue
  type MinSiblingsValue = MinSiblingsCount
  val MinSiblingsValue: MinSiblingsCount.type = MinSiblingsCount

  def isoYear(raw: String): Option[Year] =
    raw.trim.toLongOption.map(n => Years.fromAstronomical(Years.Astronomical(n)))

  /** The Lower Palaeolithic starts around 3.3 million years ago, and no period ends after 2100. */
  val MinYear: MinYear = MinYearNumber(-3000000L)
  val MaxYear: MaxYear = MaxYearNumber(2100L)

  /** A lost minus sign lands a period across BCE into CE (EAMENA's "Old Kingdom", -2701 → 2150), but long spans alone
    * are normal in deep time (a Lower Palaeolithic of 1.2 million years), so length is judged against the authority's
    * other definitions for the same region.
    */
  val SpanFactor: SpanFactor = SpanFactorNumber(3L)
  val MinImplausibleSpan: MinImplausibleSpan = MinImplausibleSpanNumber(1000L)

  val MinSiblings: MinSiblings = MinSiblingsCount(3)

  def parse(json: String): Either[Problem, PeriodODataset] =
    RawJson
      .parse(json)
      .left
      .map(e => Problem.Io.Corrupt(Problem.Io.Format.Json, Problem.Where("PeriodO dataset"), Problem.Io.Damage.Unreadable(External.Reported.of(s"$e"))))
      .flatMap { doc =>
        val c = doc.cursor
        val overlay = c.downField("histmersion")
        def strings(key: Wire): List[(String, String)] = overlay
          .downField((key).value)
          .focus
          .flatMap(_.asObject)
          .map(_.fields.toList.flatMap((k, v) => v.asString.map(k -> _)))
          .getOrElse(Nil)
        val presets = strings(Wire("presets")).map((id, r) =>
          PresetRegion
            .fromWire(PresetRegion.Wire(r))
            .map(PeriodODataset.PresetsKey(id) -> _)
            .toRight(
              Problem.Invalid.Unknown(
                Problem.TypeName.named[PresetRegion],
                Problem.Raw(s"$r"),
                PresetRegion.values.toVector.map(v => Problem.Raw(s"${v.wire}")),
                Vector(Problem.Field("presets"), Problem.Field(s"$id"))
              )
            )
        )
        for
          auths <- c
            .downField("authorities")
            .focus
            .flatMap(_.asObject)
            .toRight(Problem.Invalid.Missing.here(Problem.Field("authorities")))
          ps <- presets.collectFirst { case Left(e) => e }.toLeft(presets.collect { case Right(p) => p }.toMap)
        yield
          val read = auths.fields.toList.map((id, a) => authority(HistoricalPeriod.Id(id), a.cursor))
          PeriodODataset(
            read.map(_._1),
            strings(Wire("parents"))
              .map((k, v) => PeriodODataset.ParentsKey(k) -> PeriodODataset.ParentsValue(v))
              .toMap,
            read.flatMap(_._2),
            ps,
            overlay
              .downField("version")
              .focus
              .flatMap(_.asNumber)
              .flatMap(
                (
                    refactorCallback2 =>
                      (refactorCallback2.asInt).map(refactorValue3 => PeriodODataset.Version(refactorValue3))
                )
              )
          )
      }

  private def authority(
      id: HistoricalPeriod.Id,
      c: Cursor
  ): (Authority, List[SkippedPeriod]) =
    val src = c.downField("source")
    val title = src.downField("title").text.orElse(src.downField("partOf").downField("title").text).getOrElse(id.value)
    val year = src.downField("yearPublished").whole.orElse(src.down("partOf", "yearPublished").whole).map(Year(_))
    val defs = c
      .downField("periods")
      .focus
      .flatMap(_.asObject)
      .map(_.fields.toList)
      .getOrElse(Nil)
      .map((pid, p) => definition(HistoricalPeriod.Id(pid), p.cursor))
    val (implausible, kept) = implausibleSpans(defs.flatMap(_.toOption))
    (
      Authority(id, HistoricalPeriod.Label(title), year, kept),
      defs.flatMap(_.left.toOption) ++ implausible
    )

  def implausibleSpans(
      defs: List[Definition]
  ): (List[SkippedPeriod], List[Definition]) =
    val umbrellas = defs.flatMap(_.broader).toSet
    def span(d: Definition) = (d.period.end).value - (d.period.start).value
    val judged = defs.filterNot(d => umbrellas.contains(d.id))
    val verdicts = defs.map { d =>
      val others = judged.filter(o => o.id != d.id && o.region == d.region)
      val longest = others.map(span).maxOption.getOrElse(0L)
      val crossesEra = (d.period.start).value < 0 && (d.period.end).value > 0
      if !crossesEra || umbrellas.contains(d.id) || others.size < MinSiblings.value || span(
          d
        ) < MinImplausibleSpan.value ||
        span(d) <= SpanFactor.value * longest
      then Right(d)
      else
        Left(
          SkippedPeriod(
            SkippedPeriod.Id(d.id.value),
            SkippedPeriod.Label(d.label.value),
            Period.Skip.ImplausibleSpan,
            SkippedPeriod.Detail(
              s"spans ${span(d)} years (${d.period.start} → ${d.period.end}), more than $SpanFactor× the longest " +
                s"other ${d.region.fold("unlocated")(_.value)} period of its authority ($longest years)"
            )
          )
        )
    }
    (verdicts.flatMap(_.left.toOption), verdicts.flatMap(_.toOption))

  private def definition(
      id: HistoricalPeriod.Id,
      c: Cursor
  ): Either[SkippedPeriod, Definition] =
    // FASTI labels its Ukrainian periods in Ukrainian, with the English label as an alternative
    val own = c.downField("label").text.map((_).trim).filter(_.nonEmpty)
    val english = c.downField("languageTag").text.exists((_).toLowerCase.startsWith("en"))
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
      .getOrElse(id.value)
    def bound(in: Cursor, outer: String) =
      in.downField("year").literal.orElse(in.downField(outer).literal).flatMap(isoYear)
    val start = bound(c.downField("start").downField("in"), "earliestYear")
    val stop = bound(c.downField("stop").downField("in"), "latestYear")
    val region = c.downField("spatialCoverageDescription").text.map((_).trim).filter(_.nonEmpty).orElse {
      val labels = c
        .downField("spatialCoverage")
        .focus
        .flatMap(_.asArray)
        .toList
        .flatten
        .flatMap(_.cursor.downField("label").focus.flatMap(_.asString))
        .map(_.trim)
        .filter(_.nonEmpty)
      Option.when(labels.nonEmpty)(labels.mkString(", "))
    }
    def skip(reason: Period.Skip, detail: SkippedPeriod.Detail) = Left(
      SkippedPeriod(SkippedPeriod.Id(id.value), SkippedPeriod.Label(label), reason, detail)
    )
    def outside(y: Year) = (y).value < (MinYear).value || (y).value > (MaxYear).value
    (start, stop) match
      case (Some(s), Some(e)) if outside(s) || outside(e) =>
        skip(Period.Skip.OutOfRange, SkippedPeriod.Detail(s"$s → $e lies outside $MinYear..$MaxYear"))
      case (Some(s), Some(e)) if s <= e =>
        Right(
          Definition(
            id,
            HistoricalPeriod.Label(label),
            Period(s, e),
            region.map(HistoricalPeriod.Region(_)),
            c.downField("broader").text.map(HistoricalPeriod.Id(_))
          )
        )
      case (Some(s), Some(e)) =>
        skip(Period.Skip.EndsBeforeStart, SkippedPeriod.Detail(s"starts after it ends ($s > $e)"))
      case _ => skip(Period.Skip.NoSpan, SkippedPeriod.Detail("no readable start or stop year"))

  def toPeriods(
      ds: PeriodODataset,
      existing: Map[HistoricalPeriod.Id, Option[HistoricalPeriod.Id]] = Map.empty,
      roots: Boolean = (false)
  ): List[HistoricalPeriod] =
    val defs = ds.authorities.flatMap(a => a.periods.map(a -> _))
    val ids = defs.map(_._2.id).toSet
    val authorityRoots =
      if !(roots) then Nil
      else
        ds.authorities.filter(_.periods.nonEmpty).map { a =>
          HistoricalPeriod(
            a.id,
            a.title,
            Some(HistoricalPeriod.Authority(s"PeriodO ${a.id}")),
            Period(
              a.periods.map(_.period.start).minBy(y => (y.value: Long)),
              a.periods.map(_.period.end).maxBy(y => (y.value: Long))
            ),
            parentId = existing.get(a.id).flatten.map(p => HistoricalPeriod.ParentId(p.value))
          )
        }
    val rows = defs.map { (a, d) =>
      val parent = ds.parents
        .get(PeriodODataset.ParentsKey(d.id.value))
        .map(v => HistoricalPeriod.Id(v.value))
        .filter(ids.contains) match
        case curated @ Some(_) => curated
        case None              =>
          val own = d.broader.filter(ids.contains).orElse(Option.when((roots))(a.id))
          if (ds.curated) == (true) then own else existing.getOrElse(d.id, own)
      HistoricalPeriod(
        d.id,
        d.label,
        Some(a.cite(d.id)),
        d.period,
        d.region,
        parent.filter(_ != d.id).map(p => HistoricalPeriod.ParentId(p.value)),
        ds.presets.get(PeriodODataset.PresetsKey(d.id.value))
      )
    }
    parentsFirst(authorityRoots ++ rows)

  private[api] def parentsFirst(rows: List[HistoricalPeriod]): List[HistoricalPeriod] =
    val byId = rows.map(r => r.id -> r).toMap
    val out = scala.collection.mutable.LinkedHashMap.empty[HistoricalPeriod.Id, HistoricalPeriod]
    def visit(r: HistoricalPeriod, path: Set[HistoricalPeriod.Id]): Unit =
      if !out.contains(r.id) then
        val parent = r.parentId.flatMap(pid => byId.get(HistoricalPeriod.Id(pid.value)))
        parent match
          case Some(p) if path.contains(p.id) => out(r.id) = r.modify(_.parentId).setTo(None)
          case Some(p)                        =>
            visit(p, path + r.id)
            if !out.contains(r.id) then out(r.id) = r
          case None => out(r.id) = r
    rows.foreach(r => visit(r, Set(r.id)))
    out.values.toList

  final case class Authority(
      id: HistoricalPeriod.Id,
      title: HistoricalPeriod.Label,
      year: Option[Year],
      periods: List[Definition]
  ):
    /** A PeriodO id resolves as `http://n2t.net/ark:/99152/<id>`. */
    def cite(periodId: HistoricalPeriod.Id): HistoricalPeriod.Authority =
      HistoricalPeriod.Authority(s"PeriodO $periodId · $title${year.fold("")(y => s" ($y)")}")

  final case class Definition(
      id: HistoricalPeriod.Id,
      label: HistoricalPeriod.Label,
      period: Period,
      region: Option[HistoricalPeriod.Region],
      broader: Option[HistoricalPeriod.Id]
  )
