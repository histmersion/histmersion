package com.histmersion.api

import io.circe.ACursor

/** PeriodO writes ISO 8601 years, which count a year 0 (`"-2999"` is 3000 BC, `"0000"` is 1 BC). The store's signed
  * years have no year 0, so a year `≤ 0` moves down by one.
  */

object PeriodO:

  def isoYear(raw: String): Option[Long] =
    Years.parseIso(raw)

  /** The Lower Palaeolithic starts around 3.3 million years ago, and no period ends after 2100. */
  val MinYear: Long = -3000000L
  val MaxYear: Long = 2100L

  /** A lost minus sign lands a period across BCE into CE (EAMENA's "Old Kingdom", -2701 → 2150), but long spans alone
    * are normal in deep time (a Lower Palaeolithic of 1.2 million years), so length is judged against the authority's
    * other definitions for the same region.
    */
  val SpanFactor: Long = 3L
  val MinImplausibleSpan: Long = 1000L

  val MinSiblings: Int = 3

  def parse(json: String): Either[String, PeriodODataset] =
    io.circe.parser.parse(json).left.map(e => s"not JSON: ${e.getMessage}").flatMap { doc =>
      val c = doc.hcursor
      val overlay = c.downField("histmersion")
      def strings(key: String): List[(String, String)] = overlay
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

  private def authority(id: String, c: ACursor): (PeriodOAuthority, List[SkippedPeriod]) =
    val src = c.downField("source")
    val title = str(src, "title").orElse(str(src.downField("partOf"), "title")).getOrElse(id)
    val year = int(src, "yearPublished").orElse(int(src.downField("partOf"), "yearPublished"))
    val defs = c
      .downField("periods")
      .focus
      .flatMap(_.asObject)
      .map(_.toList)
      .getOrElse(Nil)
      .map((pid, p) => definition(pid, p.hcursor))
    val (implausible, kept) = implausibleSpans(defs.flatMap(_.toOption))
    (PeriodOAuthority(id, title, year, kept), defs.flatMap(_.left.toOption) ++ implausible)

  def implausibleSpans(defs: List[PeriodODefinition]): (List[SkippedPeriod], List[PeriodODefinition]) =
    val umbrellas = defs.flatMap(_.broader).toSet
    def span(d: PeriodODefinition) = d.period.end - d.period.start
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
            PeriodSkip.ImplausibleSpan,
            s"spans ${span(d)} years (${d.period.start} → ${d.period.end}), more than $SpanFactor× the longest " +
              s"other ${d.region.getOrElse("unlocated")} period of its authority ($longest years)"
          )
        )
    }
    (verdicts.flatMap(_.left.toOption), verdicts.flatMap(_.toOption))

  private def definition(id: String, c: ACursor): Either[SkippedPeriod, PeriodODefinition] =
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
    val start = bound(c.downField("start").downField("in"), "earliestYear")
    val stop = bound(c.downField("stop").downField("in"), "latestYear")
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
    def skip(reason: PeriodSkip, detail: String) = Left(SkippedPeriod(id, label, reason, detail))
    def outside(y: Long) = y < MinYear || y > MaxYear
    (start, stop) match
      case (Some(s), Some(e)) if outside(s) || outside(e) =>
        skip(PeriodSkip.OutOfRange, s"$s → $e lies outside $MinYear..$MaxYear")
      case (Some(s), Some(e)) if s <= e =>
        Right(PeriodODefinition(id, label, Period(s, e), region, str(c, "broader")))
      case (Some(s), Some(e)) => skip(PeriodSkip.EndsBeforeStart, s"starts after it ends ($s > $e)")
      case _                  => skip(PeriodSkip.NoSpan, "no readable start or stop year")

  private def bound(in: ACursor, outer: String): Option[Long] =
    yearAt(in, "year").orElse(yearAt(in, outer))

  private def yearAt(c: ACursor, key: String): Option[Long] =
    c.downField(key).focus.flatMap(j => j.asString.orElse(j.asNumber.map(_.toString))).flatMap(isoYear)

  private def str(c: ACursor, key: String): Option[String] = c.downField(key).focus.flatMap(_.asString)
  private def int(c: ACursor, key: String): Option[Int] =
    c.downField(key).focus.flatMap(j => j.asNumber.flatMap(_.toInt).orElse(j.asString.flatMap(_.toIntOption)))

  def toPeriods(
      ds: PeriodODataset,
      existing: Map[String, Option[String]] = Map.empty,
      roots: Boolean = false
  ): List[HistoricalPeriod] =
    val defs = ds.authorities.flatMap(a => a.periods.map(a -> _))
    val ids = defs.map(_._2.id).toSet
    val authorityRoots =
      if !roots then Nil
      else
        ds.authorities.filter(_.periods.nonEmpty).map { a =>
          HistoricalPeriod(
            a.id,
            a.title,
            Some(s"PeriodO ${a.id}"),
            Period(a.periods.map(_.period.start).min, a.periods.map(_.period.end).max),
            parentId = existing.get(a.id).flatten
          )
        }
    val rows = defs.map { (a, d) =>
      val parent = ds.parents.get(d.id).filter(ids.contains) match
        case curated @ Some(_) => curated
        case None              =>
          val own = d.broader.filter(ids.contains).orElse(Option.when(roots)(a.id))
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
    def visit(r: HistoricalPeriod, path: Set[String]): Unit =
      if !out.contains(r.id) then
        val parent = r.parentId.flatMap(byId.get)
        parent match
          case Some(p) if path.contains(p.id) => out(r.id) = r.copy(parentId = None)
          case Some(p)                        =>
            visit(p, path + r.id)
            if !out.contains(r.id) then out(r.id) = r
          case None => out(r.id) = r
    rows.foreach(r => visit(r, Set(r.id)))
    out.values.toList
