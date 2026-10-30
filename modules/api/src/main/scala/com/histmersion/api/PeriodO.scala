package com.histmersion.api

import io.circe.ACursor

/** Named historical periods from **PeriodO** (https://perio.do) — a gazetteer of period DEFINITIONS: each is a label, a
  * time span and a spatial scope as one published authority stated them. The whole dataset is CC0.
  *
  * One pure reader serves both ways periods reach the store: the bundled preset seed (`db/periods/periodo-seed.json`, a
  * SUBSET of the PeriodO dump in the dump's own format, plus a `histmersion.parents` overlay — see
  * `db/periods/build.py`) and a live download of the dump (`histmersion periods fetch periodo`). Nothing here touches a
  * database or the network.
  *
  * Years: PeriodO writes ISO 8601 years, which count a year 0 (`"-2999"` is 3000 BC, `"0000"` is 1 BC). The store's
  * signed years have no year 0 (3000 BCE is `-3000`), so a year `≤ 0` moves down by one. A definition's span is its
  * OUTER bounds: the start's `year` or `earliestYear`, the stop's `year` or `latestYear`.
  */

object PeriodO:

  /** An ISO 8601 year (`"-2999"`, `"0640"`, `-2999`) → a signed year with no year 0. */
  def isoYear(raw: String): Option[Long] =
    Years.parseIso(raw)

  /** The earliest and latest year a definition may name (#771): the Lower Palaeolithic starts around 3.3 million years
    * ago, and no period ends after 2100.
    */
  val MinYear: Long = -3000000L
  val MaxYear: Long = 2100L

  /** A definition is implausibly long when it runs from BCE into CE — where a lost minus sign lands a period (EAMENA's
    * "Old Kingdom", -2701 → 2150) — AND spans more than this many times the longest OTHER definition its authority
    * gives for the same region (and at least [[MinImplausibleSpan]] years). Long spans alone are normal in deep time (a
    * Lower Palaeolithic of 1.2 million years), so length is never judged on its own. A period another definition names
    * as `broader` is an umbrella, and is never judged at all.
    */
  val SpanFactor: Long = 3L
  val MinImplausibleSpan: Long = 1000L

  /** ...and only when the authority states at least this many other definitions for the region. */
  val MinSiblings: Int = 3

  /** Parse a PeriodO dump, or the seed subset of one. A definition without a readable span, or with one that cannot be
    * right (#771: it ends before it starts, lies outside [[MinYear]]..[[MaxYear]], or is implausibly long for its
    * authority's region — EAMENA's "Old Kingdom" ran -2701 → 2150), is skipped and named in `skipped`, never guessed at
    * or "fixed". A seed's overlay naming a region heading that does not exist is refused: a preset filed under nothing
    * would silently vanish.
    */
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

  /** Split one authority's readable definitions into the implausibly long ones (see [[SpanFactor]]) and the rest, order
    * kept. Pure.
    */
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
    // the authority's own label when it is English; else its English alternative (FASTI labels its
    // Ukrainian periods in Ukrainian and gives the English beside them); else the label as it is
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

  /** `year` when the bound is a single year, else the OUTER end of its range. */
  private def bound(in: ACursor, outer: String): Option[Long] =
    yearAt(in, "year").orElse(yearAt(in, outer))

  private def yearAt(c: ACursor, key: String): Option[Long] =
    c.downField(key).focus.flatMap(j => j.asString.orElse(j.asNumber.map(_.toString))).flatMap(isoYear)

  private def str(c: ACursor, key: String): Option[String] = c.downField(key).focus.flatMap(_.asString)
  private def int(c: ACursor, key: String): Option[Int] =
    c.downField(key).focus.flatMap(j => j.asNumber.flatMap(_.toInt).orElse(j.asString.flatMap(_.toIntOption)))

  /** The rows to upsert, PARENTS BEFORE CHILDREN (the column is a foreign key). A period's parent is, in order:
    *   1. the dataset's curated overlay (`histmersion.parents` — only the seed has one);
    *   2. for anything but a curated seed, where the period ALREADY sits in the store — `existing` maps every stored id
    *      to its parent, `None` for a top-level one — so a fetch never moves a period the seed or a person placed. The
    *      seed itself is authoritative for its own rows: a new seed version re-files them (an older seed nested
    *      regional definitions under another authority's Eurasia-wide span, and a re-seed has to undo that);
    *   3. its PeriodO `broader` period, when that is in this batch;
    *   4. with `roots`, a node for its authority — so importing a whole PeriodO collection adds ONE node, not one root
    *      per period.
    * A row's `preset` is the seed's `histmersion.presets` entry for it, if any. A parent link that would close a cycle
    * is dropped.
    */
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

  /** Order rows so every parent IN THE LIST precedes its children; a parent outside the list is assumed to exist. A
    * link that closes a cycle is cut at the row that would close it.
    */
  private[api] def parentsFirst(rows: List[HistoricalPeriod]): List[HistoricalPeriod] =
    val byId = rows.map(r => r.id -> r).toMap
    // a local, insertion-ordered accumulator of a depth-first walk; nothing outside sees it mutate
    val out = scala.collection.mutable.LinkedHashMap.empty[String, HistoricalPeriod]
    def visit(r: HistoricalPeriod, path: Set[String]): Unit =
      if !out.contains(r.id) then
        val parent = r.parentId.flatMap(byId.get)
        parent match
          case Some(p) if path.contains(p.id) => out(r.id) = r.copy(parentId = None) // a cycle
          case Some(p)                        =>
            visit(p, path + r.id)
            if !out.contains(r.id) then out(r.id) = r
          case None => out(r.id) = r
    rows.foreach(r => visit(r, Set(r.id)))
    out.values.toList
