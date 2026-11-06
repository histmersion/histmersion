package com.histmersion.domain.media.imports

import com.histmersion.api.catalogue.Geocode
import com.histmersion.domain.DeepTime
import com.histmersion.domain.catalogue.places.ExternalIds
import com.histmersion.Period

import java.util.Locale

/** How a collection record is tied to a place on the map — conservatively, and with the reason recorded (#1446). Pure.
  *
  *   - **Geography**, never coordinates: a record's place field (a find-spot, a city, a country, a map's location) is
  *     compared with the place's name and alternative names by the gazetteer's name key ([[Geocode.key]] =
  *     `histmersion_place_key`), whole value against whole name. A value marked uncertain (`?`, "probably", "said to
  *     be") is never used.
  *   - **Most specific first** (a museum object): the first field whose value names ANY place on the map decides. It
  *     must name the target and no other place (equivalents of one site excepted — the caller's `resolve`), so an
  *     object from Mycenae is not also filed under Greece.
  *   - **Any field** (a map): a map lists every place it shows, so it is linked to the target when one of its locations
  *     names the target alone.
  *   - **Dates** (an object): its range must overlap the place's period — the place's own, else the span of the
  *     findings it holds. A record or place without dates is not matched.
  */
object MediaMatch:

  /** Why a record is not tied to the place. */
  type Refusal = Refusal.T
  object Refusal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** What ties a record to the place: the field and value (or the id) that name it, and the dates. */
  type Basis = Basis.T
  object Basis extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** A period as a person reads it (`1400–1200 BCE`). */
  type PeriodText = PeriodText.T
  object PeriodText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** One signed year as a person reads it (`1500 CE`). */
  type YearText = YearText.T
  object YearText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GeographySpecific = GeographySpecific.T
  object GeographySpecific extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A place the import asks about: its id and label, the keys of every name it goes by, its period. */
  final case class PlaceInfo(id: PlaceInfo.Id, label: PlaceInfo.Label, keys: Set[PlaceInfo.Keys], period: Option[Period])

  object PlaceInfo:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Keys = Keys.T
    object Keys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Uncertain =
    List("?", "probably", "possibly", "perhaps", "said to be", "attributed", "likely", "uncertain")

  /** Pure: is a place value marked as uncertain? */
  def uncertain(v: MediaRecord.GeographyPart2): Boolean =
    val s = v.value.toLowerCase(Locale.ROOT)
    Uncertain.exists(s.contains)

  /** Pure: the name key of a value (empty when it is uncertain or blank). */
  def keyOf(v: MediaRecord.GeographyPart2): PlaceInfo.Keys = if uncertain(v) then PlaceInfo.Keys("") else PlaceInfo.Keys(Geocode.key(v.value).value)

  /** Pure: do two periods overlap? */
  def overlaps(a: Period, b: Period): Boolean = a.start.value <= b.end.value && b.start.value <= a.end.value

  /** Pure: a period as a person reads it (`1400–1200 BCE`, `200 BCE–100 CE`). */
  def years(p: Period): PeriodText =
    val (start, end) = (p.start.value, p.end.value)
    PeriodText(
      if start < 0 && end < 0 then s"${-start}–${-end} BCE"
      else if start >= 0 && end >= 0 then s"$start–$end CE"
      else s"${year(p.start)}–${year(p.end)}"
    )

  /** Pure: one signed year as a person reads it (`1500 CE`, `1200 BCE`). */
  def year(v: com.histmersion.Year): YearText = YearText(if v.value < 0 then s"${-v.value} BCE" else s"${v.value} CE")

  /** What a value names on the map: the ids of the map places with that name key, equivalents of one site counted once
    * — the caller's lookup.
    */
  type Resolve = PlaceInfo.Keys => Set[PlaceInfo.Id]

  /** Pure: which field of the record ties it to the target — `Right("excavation = Mycenae")` — or why none does.
    * `specific` = museum rule (the most specific naming field decides); otherwise the map rule (any field naming the
    * target alone).
    */
  def geography(rec: MediaRecord, target: PlaceInfo, resolve: Resolve, specific: com.histmersion.domain.media.imports.MediaMatch.GeographySpecific): Either[Refusal, Basis] =
    val named = rec.geography.flatMap((f, v) => Option(keyOf(v)).filter(_.value.nonEmpty).map(k => (f, v.value.trim, k)))
    if named.isEmpty then Left(Refusal("no place on the record"))
    else if specific.value then
      named.iterator.map((f, v, k) => (f, v, resolve(k))).find(_._3.nonEmpty) match
        case None                                         => Left(Refusal("its place is not on the map"))
        case Some((f, v, ids)) if ids == Set(target.id)   => Right(Basis(s"$f = $v"))
        case Some((_, _, ids)) if ids.contains(target.id) => Left(Refusal("its place name is ambiguous on the map"))
        case Some(_)                                      => Left(Refusal("names another place on the map"))
    else
      named.find((_, _, k) => target.keys(k)) match
        case None            => Left(Refusal("its place is not this one"))
        case Some((f, v, k)) =>
          if resolve(k) == Set(target.id) then Right(Basis(s"$f = $v")) else Left(Refusal("its place name is ambiguous on the map"))

  /** Pure: the museum rule — geography, then the dates ([[dated]]); the basis names both. */
  def museum(
      rec: MediaRecord,
      target: PlaceInfo,
      resolve: Resolve,
      latest: Option[com.histmersion.Year] = None
  ): Either[Refusal, Basis] =
    geography(rec, target, resolve, specific = com.histmersion.domain.media.imports.MediaMatch.GeographySpecific(true)).flatMap(dated(_, rec, target, latest))

  /** Pure: the dates half of a match, after the place half (`basis`): the record must be dated; with a `latest` year (a
    * collection's `max-year`: the last year the research is about), made by then — a record made later is refused
    * whatever place it names, so a 19th-century specimen or print is not filed under a place whose gazetteer span runs
    * to the present — and its dates must overlap the place's period, read up to `latest`.
    */
  def dated(basis: Basis, rec: MediaRecord, target: PlaceInfo, latest: Option[com.histmersion.Year]): Either[Refusal, Basis] =
    (rec.period, target.period) match
      case (None, _)                                                => Left(Refusal("no dates on the record"))
      case (Some(r), _) if latest.exists(y => r.end.value > y.value) => Left(Refusal(s"made after ${year(latest.get)}"))
      case (_, None)                                                => Left(Refusal("the place has no period to compare"))
      case (Some(r), Some(p))                                       =>
        val window = latest
          .filter(y => y.value >= p.start.value && y.value < p.end.value)
          .flatMap(y => DeepTime.span(DeepTime.SignedYear(p.start.value.toLong), DeepTime.SignedYear(y.value.toLong)))
          .getOrElse(p)
        if overlaps(r, window) then Right(Basis(s"$basis; ${years(r)} within ${years(window)}"))
        else Left(Refusal("its dates do not overlap the place's"))

  /** What a gazetteer id names on the map: the ids of the map places carrying it, equivalents of one site counted once
    * — the caller's lookup.
    */
  type ResolveId = ((MediaRecord.IdsPart1, MediaRecord.IdsPart2)) => Set[PlaceInfo.Id]

  /** Pure: the rule for a record that may carry gazetteer ids (Open Context). The FINEST evidence decides (#2571): its
    * fields in `deeper` (Open Context's context path below its country — the site, then the parts of it; excavation
    * units are not among them, [[OpenContext.unitSegment]]) are finer than its ids, which Open Context often gives for
    * a province or a region (Domuztepe's records name Kahramanmaraş and the Middle East). So:
    *   - the most specific `deeper` field naming a place on the map names the target alone: tied by it, whatever the
    *     ids say; names only another place: refused (a find from a trench of a site on the map is that site's); names
    *     the target among others: an id naming the target alone settles it, else it is ambiguous;
    *   - with no `deeper` field on the map, the ids decide — one naming the target alone ties it, one naming it among
    *     others is ambiguous, one naming only other places refuses it (an object from Petra is not also filed under
    *     Jordan) — and with no id on the map, the museum rule's names.
    * Either way the dates decide as in [[dated]]; the basis names the id or the field, and the dates.
    */
  def withIds(
      rec: MediaRecord,
      target: PlaceInfo,
      resolveId: ResolveId,
      resolve: Resolve,
      deeper: Set[MediaRecord.GeographyPart1],
      latest: Option[com.histmersion.Year] = None
  ): Either[Refusal, Basis] =
    val byId = rec.ids.map(id => id -> resolveId(id)).filter(_._2.nonEmpty)
    val idAlone = byId.find(_._2 == Set(target.id)).map((id, _) => Basis(s"${ExternalIds.show(id)} (the record's place id)"))
    val finer = rec.geography
      .filter((f, _) => deeper(f))
      .flatMap((f, v) => Option(keyOf(v)).filter(_.value.nonEmpty).map(k => (f, v.value.trim, resolve(k))))
      .find(_._3.nonEmpty)
    val geo = finer match
      case Some((f, v, ids)) if ids == Set(target.id)    => Right(Basis(s"$f = $v"))
      case Some((f, _, ids)) if !ids.contains(target.id) => Left(Refusal(s"its $f names another place on the map"))
      case Some((f, _, _))                               => idAlone.toRight(Refusal(s"its $f name is ambiguous on the map"))
      case None                                          =>
        idAlone match
          case Some(basis)                                   => Right(basis)
          case None if byId.exists(_._2.contains(target.id)) => Left(Refusal("its place id is ambiguous on the map"))
          case None if byId.nonEmpty                         => Left(Refusal("its place id names another place on the map"))
          case None                                          => geography(rec, target, resolve, specific = com.histmersion.domain.media.imports.MediaMatch.GeographySpecific(true))
    geo.flatMap(dated(_, rec, target, latest))

  /** Pure: the map rule — a location naming the target, a map made by `maxYear` (public domain). */
  def map(rec: MediaRecord, target: PlaceInfo, resolve: Resolve, maxYear: com.histmersion.Year): Either[Refusal, Basis] =
    rec.period match
      case None                                   => Left(Refusal("no date on the map"))
      case Some(p) if p.end.value > maxYear.value => Left(Refusal(s"made after $maxYear"))
      case Some(p) => geography(rec, target, resolve, specific = com.histmersion.domain.media.imports.MediaMatch.GeographySpecific(false)).map(geo => Basis(s"$geo; map of ${years(p)}"))
