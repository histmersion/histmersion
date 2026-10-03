package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.ACursor
import sttp.tapir.Schema

/** Named historical periods from **PeriodO** (https://perio.do) — a gazetteer of period DEFINITIONS:
  * each is a label, a time span and a spatial scope as one published authority stated them. The whole
  * dataset is CC0.
  *
  * One pure reader serves both ways periods reach the store: the bundled preset seed
  * (`db/periods/periodo-seed.json`, a SUBSET of the PeriodO dump in the dump's own format, plus a
  * `histmersion.parents` overlay — see `db/periods/build.py`) and a live download of the dump
  * (`histmersion periods fetch periodo`). Nothing here touches a database or the network.
  *
  * Years: PeriodO writes ISO 8601 years, which count a year 0 (`"-2999"` is 3000 BC, `"0000"` is
  * 1 BC). The store's signed years have no year 0 (3000 BCE is `-3000`), so a year `≤ 0` moves down by
  * one. A definition's span is its OUTER bounds: the start's `year` or `earliestYear`, the stop's
  * `year` or `latestYear`.
  */

/** One period definition as PeriodO states it, its years already converted to signed years. */
final case class PeriodODefinition(
    id: String,
    label: String,
    period: Period,
    region: Option[String],
    broader: Option[String]
)

/** One PeriodO authority — a publication or dataset — and the definitions it makes. */
final case class PeriodOAuthority(id: String, title: String, year: Option[Int], periods: List[PeriodODefinition]):
  /** What every period row says it comes from: the PeriodO id (resolvable as
    * `http://n2t.net/ark:/99152/<id>`) and the authority that states the range. */
  def cite(periodId: String): String =
    s"PeriodO $periodId · $title${year.fold("")(y => s" ($y)")}"

/** A parsed dump (or seed): the authorities, the seed's curated `parents` overlay (empty in a real
  * PeriodO dump) and the definitions that were not imported, with the reason. */
final case class PeriodODataset(
    authorities: List[PeriodOAuthority],
    parents: Map[String, String] = Map.empty,
    skipped: List[SkippedPeriod] = Nil
):
  def size: Int = authorities.map(_.periods.size).sum

  /** Only the named authorities (all of them when `ids` is empty) — and only THEIR unreadable
    * definitions: a PeriodO period id begins with its authority's id. */
  def only(ids: Set[String]): PeriodODataset =
    if ids.isEmpty then this
    else copy(authorities = authorities.filter(a => ids.contains(a.id)),
      skipped = skipped.filter(s => ids.exists(s.id.startsWith)))

object PeriodO:

  /** An ISO 8601 year (`"-2999"`, `"0640"`, `-2999`) → a signed year with no year 0. */
  def isoYear(raw: String): Option[Long] =
    raw.trim.toLongOption.map(y => if y <= 0 then y - 1 else y)

  /** The earliest and latest year a definition may name (#771): the Lower Palaeolithic starts
    * around 3.3 million years ago, and no period ends after 2100. */
  val MinYear: Long = -3000000L
  val MaxYear: Long = 2100L

  /** A definition is implausibly long when it runs from BCE into CE — where a lost minus sign lands
    * a period (EAMENA's "Old Kingdom", -2701 → 2150) — AND spans more than this many times the longest
    * OTHER definition its authority gives for the same region (and at least [[MinImplausibleSpan]]
    * years). Long spans alone are normal in deep time (a Lower Palaeolithic of 1.2 million years), so
    * length is never judged on its own. A period another definition names as `broader` is an
    * umbrella, and is never judged at all. */
  val SpanFactor: Long         = 3L
  val MinImplausibleSpan: Long = 1000L
  /** ...and only when the authority states at least this many other definitions for the region. */
  val MinSiblings: Int         = 3

  /** Parse a PeriodO dump, or the seed subset of one. A definition without a readable span, or with
    * one that cannot be right (#771: it ends before it starts, lies outside [[MinYear]]..[[MaxYear]],
    * or is implausibly long for its authority's region — EAMENA's "Old Kingdom" ran -2701 → 2150), is
    * skipped and named in `skipped`, never guessed at or "fixed". */
  def parse(json: String): Either[String, PeriodODataset] =
    io.circe.parser.parse(json).left.map(e => s"not JSON: ${e.getMessage}").flatMap { doc =>
      val c = doc.hcursor
      c.downField("authorities").focus.flatMap(_.asObject)
        .toRight("not a PeriodO dataset: no `authorities` object")
        .map { auths =>
          val read = auths.toList.map((id, a) => authority(id, a.hcursor))
          val parents = c.downField("histmersion").downField("parents").focus.flatMap(_.asObject)
            .map(_.toList.flatMap((k, v) => v.asString.map(k -> _)).toMap).getOrElse(Map.empty)
          PeriodODataset(read.map(_._1), parents, read.flatMap(_._2))
        }
    }

  private def authority(id: String, c: ACursor): (PeriodOAuthority, List[SkippedPeriod]) =
    val src   = c.downField("source")
    val title = str(src, "title").orElse(str(src.downField("partOf"), "title")).getOrElse(id)
    val year  = int(src, "yearPublished").orElse(int(src.downField("partOf"), "yearPublished"))
    val defs  = c.downField("periods").focus.flatMap(_.asObject).map(_.toList).getOrElse(Nil)
      .map((pid, p) => definition(pid, p.hcursor))
    val (implausible, kept) = implausibleSpans(defs.flatMap(_.toOption))
    (PeriodOAuthority(id, title, year, kept), defs.flatMap(_.left.toOption) ++ implausible)

  /** Split one authority's readable definitions into the implausibly long ones (see [[SpanFactor]])
    * and the rest, order kept. Pure. */
  def implausibleSpans(defs: List[PeriodODefinition]): (List[SkippedPeriod], List[PeriodODefinition]) =
    val umbrellas = defs.flatMap(_.broader).toSet
    def span(d: PeriodODefinition) = d.period.end - d.period.start
    val judged = defs.filterNot(d => umbrellas.contains(d.id))
    val verdicts = defs.map { d =>
      val others = judged.filter(o => o.id != d.id && o.region == d.region)
      val longest = others.map(span).maxOption.getOrElse(0L)
      val crossesEra = d.period.start < 0 && d.period.end > 0
      if !crossesEra || umbrellas.contains(d.id) || others.size < MinSiblings || span(d) < MinImplausibleSpan ||
        span(d) <= SpanFactor * longest then Right(d)
      else Left(SkippedPeriod(d.id, d.label, PeriodSkip.ImplausibleSpan,
        s"spans ${span(d)} years (${d.period.start} → ${d.period.end}), more than $SpanFactor× the longest " +
          s"other ${d.region.getOrElse("unlocated")} period of its authority ($longest years)"))
    }
    (verdicts.flatMap(_.left.toOption), verdicts.flatMap(_.toOption))

  private def definition(id: String, c: ACursor): Either[SkippedPeriod, PeriodODefinition] =
    // the authority's own label when it is English; else its English alternative (FASTI labels its
    // Ukrainian periods in Ukrainian and gives the English beside them); else the label as it is
    val own     = str(c, "label").map(_.trim).filter(_.nonEmpty)
    val english = str(c, "languageTag").exists(_.toLowerCase.startsWith("en"))
    val label = (if english then own else None)
      .orElse(c.downField("localizedLabels").downField("en").downArray.focus.flatMap(_.asString)
        .map(_.trim).filter(_.nonEmpty))
      .orElse(own).getOrElse(id)
    val start = bound(c.downField("start").downField("in"), "earliestYear")
    val stop  = bound(c.downField("stop").downField("in"), "latestYear")
    val region = str(c, "spatialCoverageDescription").map(_.trim).filter(_.nonEmpty).orElse {
      val labels = c.downField("spatialCoverage").focus.flatMap(_.asArray).toList.flatten
        .flatMap(_.hcursor.downField("label").focus.flatMap(_.asString)).map(_.trim).filter(_.nonEmpty)
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

  /** The rows to upsert, PARENTS BEFORE CHILDREN (the column is a foreign key). A period's parent is,
    * in order:
    *  1. the dataset's curated overlay (`histmersion.parents` — only the seed has one);
    *  2. where the period ALREADY sits in the store — `existing` maps every stored id to its parent,
    *     `None` for a top-level one — so a re-run or a fetch never moves a period the seed or a
    *     person placed, and never buries a preset;
    *  3. its PeriodO `broader` period, when that is in this batch;
    *  4. with `roots`, a node for its authority — so importing a whole PeriodO collection adds ONE
    *     timeline preset, not one per period.
    * A parent link that would close a cycle is dropped. */
  def toPeriods(ds: PeriodODataset, existing: Map[String, Option[String]] = Map.empty,
      roots: Boolean = false): List[HistoricalPeriod] =
    val defs = ds.authorities.flatMap(a => a.periods.map(a -> _))
    val ids  = defs.map(_._2.id).toSet
    val authorityRoots =
      if !roots then Nil
      else ds.authorities.filter(_.periods.nonEmpty).map { a =>
        HistoricalPeriod(a.id, a.title, Some(s"PeriodO ${a.id}"),
          Period(a.periods.map(_.period.start).min, a.periods.map(_.period.end).max),
          parentId = existing.get(a.id).flatten)
      }
    val rows = defs.map { (a, d) =>
      val parent = ds.parents.get(d.id).filter(ids.contains) match
        case curated @ Some(_) => curated
        case None =>
          existing.getOrElse(d.id, d.broader.filter(ids.contains).orElse(Option.when(roots)(a.id)))
      HistoricalPeriod(d.id, d.label, Some(a.cite(d.id)), d.period, d.region, parent.filter(_ != d.id))
    }
    parentsFirst(authorityRoots ++ rows)

  /** Order rows so every parent IN THE LIST precedes its children; a parent outside the list is
    * assumed to exist. A link that closes a cycle is cut at the row that would close it. */
  private[core] def parentsFirst(rows: List[HistoricalPeriod]): List[HistoricalPeriod] =
    val byId = rows.map(r => r.id -> r).toMap
    // a local, insertion-ordered accumulator of a depth-first walk; nothing outside sees it mutate
    val out  = scala.collection.mutable.LinkedHashMap.empty[String, HistoricalPeriod]
    def visit(r: HistoricalPeriod, path: Set[String]): Unit =
      if !out.contains(r.id) then
        val parent = r.parentId.flatMap(byId.get)
        parent match
          case Some(p) if path.contains(p.id) => out(r.id) = r.copy(parentId = None) // a cycle
          case Some(p) =>
            visit(p, path + r.id)
            if !out.contains(r.id) then out(r.id) = r
          case None => out(r.id) = r
    rows.foreach(r => visit(r, Set(r.id)))
    out.values.toList

/** Rendering of the periods surface — its own object so this stream does not grow [[Markdown]]. */
object PeriodsMarkdown:

  private def years(p: Period): String = s"${p.start} → ${p.end}"

  def list(ps: Vector[HistoricalPeriod]): String =
    if ps.isEmpty then "_No periods._"
    else
      val rows = ps.map(p =>
        s"| `${p.id}` | ${p.label} | ${years(p.period)} | ${p.region.getOrElse("")} | " +
          s"${p.parentId.fold("")(x => s"`$x`")} |")
      (List(s"### ${ps.size} period(s)", "", "| id | label | years | region | part of |",
        "|---|---|---|---|---|") ++ rows).mkString("\n")

  def detail(d: PeriodDetail): String =
    val p = d.period
    val head = List(
      Some(s"### ${p.label} `${p.id}`"),
      Some(""),
      Some(s"- **years:** ${years(p.period)} (signed years, BCE negative)"),
      p.region.map(r => s"- **region:** $r"),
      p.authority.map(a => s"- **authority:** $a"),
      d.parent.map(x => s"- **part of:** ${x.label} `${x.id}` (${years(x.period)})")
    ).flatten
    val kids =
      if d.children.isEmpty then Nil
      else "" :: "**Divided into:**" :: "" ::
        d.children.map(c => s"- ${c.label} `${c.id}` — ${years(c.period)}${c.region.fold("")(r => s" · $r")}")
    (head ++ kids).mkString("\n")

  def imported(r: PeriodImportResult): String =
    val head = s"imported ${Plural.count(r.periods, "period")}" +
      (if r.roots > 0 then s", ${r.roots} of them ${Plural.noun(r.roots, "authority node")}" else "") +
      s" — ${Plural.count(r.presets, "timeline preset")} in the store"
    if r.skippedCount == 0 then head
    else
      val more = r.skippedCount - r.skipped.size
      s"$head\n\nskipped ${Plural.count(r.skippedCount, "definition")} (not imported):\n" +
        skippedList(r.skipped) + (if more > 0 then s"\n- …and $more more" else "")

  /** One line per skipped definition: id, label, why. */
  def skippedList(ss: List[SkippedPeriod]): String =
    ss.map(s => s"- `${s.id}` ${s.label}: ${s.detail}").mkString("\n")

// ── wire DTOs (declared last: see AGENTS.md on `derives KindlingsCodecAsObject` ordering) ─────────

/** `GET /api/periods/{id}`: one period, what it is part of and how it is divided. */
final case class PeriodDetail(period: HistoricalPeriod, parent: Option[HistoricalPeriod] = None,
    children: List[HistoricalPeriod] = Nil) derives KindlingsCodecAsObject
object PeriodDetail:
  given Schema[PeriodDetail] = KindlingsSchema.derived[PeriodDetail].schema

/** What a period import wrote: rows upserted (authority nodes included), how many of those were
  * authority nodes, the presets (top-level periods) now in the store, and the definitions NOT
  * imported — how many (`skippedCount`) and, up to a limit, which and why (`skipped`). */
final case class PeriodImportResult(periods: Int, roots: Int = 0, presets: Int = 0,
    skipped: List[SkippedPeriod] = Nil, skippedCount: Int = 0) derives KindlingsCodecAsObject
object PeriodImportResult:
  given Schema[PeriodImportResult] = KindlingsSchema.derived[PeriodImportResult].schema

/** Why a PeriodO definition was not imported (#771). */
enum PeriodSkip:
  /** No readable start or stop year. */
  case NoSpan
  /** It ends before it starts. */
  case EndsBeforeStart
  /** A bound lies outside [[PeriodO.MinYear]]..[[PeriodO.MaxYear]]. */
  case OutOfRange
  /** Far longer than anything else its authority states for the region ([[PeriodO.SpanFactor]]). */
  case ImplausibleSpan

object PeriodSkip:
  def wireOf(p: PeriodSkip): String = p match
    case NoSpan          => "no_span"
    case EndsBeforeStart => "ends_before_start"
    case OutOfRange      => "out_of_range"
    case ImplausibleSpan => "implausible_span"

  given WireEnumValues[PeriodSkip]:
    def all: Array[PeriodSkip] = PeriodSkip.values
    def wire(e: PeriodSkip): String = wireOf(e)
    def fromWire(s: String): Option[PeriodSkip] = PeriodSkip.values.find(wireOf(_) == s)

  given io.circe.Encoder[PeriodSkip] = WireEnum.encoder
  given io.circe.Decoder[PeriodSkip] = WireEnum.decoder
  given Schema[PeriodSkip]           = WireEnum.schema

/** One PeriodO definition left out of an import: its id and label, why, and the numbers that say so. */
final case class SkippedPeriod(id: String, label: String, reason: PeriodSkip, detail: String)
    derives KindlingsCodecAsObject
object SkippedPeriod:
  given Schema[SkippedPeriod] = KindlingsSchema.derived[SkippedPeriod].schema
