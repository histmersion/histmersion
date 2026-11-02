package com.histmersion.domain.catalogue.places

import com.histmersion.api.{
  ApiError,
  Dataset,
  ManualPlaceName,
  Period,
  PlaceDetail,
  PlaceFootprint,
  PlaceGone,
  PlaceHit,
  PlaceImportResult,
  PlaceInput,
  PlaceLink,
  PlaceNameChanged,
  PlaceOverride,
  PlaceOverrideChanged,
  PlaceOverrideEdit,
  PlaceOverrides,
  PlaceRelation,
  PlaceSummary,
  PlaceTreeRow,
  Write
}
import com.histmersion.api.catalogue.places.RegionNode
import com.histmersion.domain.catalogue.CatalogueState
import com.histmersion.domain.catalogue.places.PlaceSql.DefaultModernFrom
import com.histmersion.util.database.Transactor
import hearth.kindlings.optics.syntax.*

import java.time.{Clock, Instant}
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

/** The gazetteer's records: the places, their names (a person's among them), their old ids, the kinds and periods a
  * person set over a source's, and the hierarchy — imported, searched, read at a year, curated by hand. A finding's
  * place is the library's; what these answer about findings counts the places' matches (`finding_places`).
  */
trait PlaceService:
  def count(): Int

  /** A batch upserted in one transaction: rows, then names, then the edges whose parent exists by then. */
  def importPlaces(batch: List[PlaceInput], dataset: Dataset): PlaceImportResult

  /** The places a name search finds, valid at `asOf`: exact labels first, then prefixes. */
  def search(q: Option[String], asOf: Option[Long], limit: Int, modernFrom: Long = DefaultModernFrom): List[PlaceHit]

  /** One place with the hierarchy around it at `asOf`. */
  def get(id: String, asOf: Option[Long] = None, modernFrom: Long = DefaultModernFrom): Option[PlaceDetail]

  def children(id: String, asOf: Option[Long], limit: Int, modernFrom: Long = DefaultModernFrom): List[PlaceSummary]

  /** The hierarchy at `asOf` as a depth-tagged pre-order walk, from `root` or from every place that heads it. */
  def hierarchy(
      asOf: Option[Long],
      root: Option[String],
      maxDepth: Int,
      limit: Int,
      depthCap: Int = Int.MaxValue,
      modernFrom: Long = DefaultModernFrom
  ): List[PlaceTreeRow]

  def footprints(id: String, asOf: Option[Long]): List[PlaceFootprint]
  def links(id: String): List[PlaceLink]

  /** Which of a filter's `places` values are places, and what each stands for: itself and its descendants. */
  def scopeFor(values: List[String], period: Option[Period]): PlaceScope

  /** The places `placeIds` and all their ancestors, with the edges between them. */
  def gazetteer(placeIds: List[String]): (List[RegionNode], List[PlaceRelation])

  /** A new id; the old one stays an alias of it. */
  def rename(from: String, to: String): Either[ApiError, Unit]

  def addName(id: String, name: String, lang: String): Either[ApiError, PlaceNameChanged]

  /** Only a name a person added; an imported one is refused. */
  def removeName(id: String, name: String, lang: String): Either[ApiError, PlaceNameChanged]

  def manualNames(id: Option[String]): List[ManualPlaceName]

  /** A person's own point place (`local`); refused when its id is taken, removed or an old id. */
  def add(in: PlaceInput): Either[ApiError, PlaceSummary]

  def overrides(id: Option[String]): List[PlaceOverride]

  /** A person's kind, period or point set over the source's (or reset), and the place's findings matched again. */
  def setOverride(id: String, edit: PlaceOverrideEdit, toleranceKm: Double): Either[ApiError, PlaceOverrideChanged]

  /** `from` merged into `into`: what refers to it moves, its id resolves there. */
  def merge(from: String, into: String, toleranceKm: Double): Either[ApiError, PlaceGone]

  /** A place nothing refers to, removed for good. */
  def remove(id: String, reason: Option[String]): Either[ApiError, PlaceGone]

object PlaceService:
  def apply(db: Transactor): PlaceService = Implementation(db)

  final private class Implementation(db: Transactor) extends PlaceService:
    def count(): Int = db.read(PlaceRepo.count())
    def importPlaces(batch: List[PlaceInput], dataset: Dataset): PlaceImportResult =
      if batch.isEmpty then PlaceImportResult(0, 0, 0, Nil) else db.transact(PlaceRepo.upsertPlaces(batch, dataset))
    def search(q: Option[String], asOf: Option[Long], limit: Int, modernFrom: Long): List[PlaceHit] =
      db.read(PlaceRepo.search(q, asOf, limit, modernFrom)).toList
    def get(id: String, asOf: Option[Long], modernFrom: Long): Option[PlaceDetail] =
      db.read(PlaceRepo.get(id, asOf, modernFrom))
    def children(id: String, asOf: Option[Long], limit: Int, modernFrom: Long): List[PlaceSummary] =
      db.read(PlaceRepo.children(id, asOf, limit, modernFrom)).toList
    def hierarchy(
        asOf: Option[Long],
        root: Option[String],
        maxDepth: Int,
        limit: Int,
        depthCap: Int,
        modernFrom: Long
    ): List[PlaceTreeRow] =
      db.read(PlaceRepo.hierarchy(asOf, root, maxDepth, limit, depthCap, modernFrom)).toList
    def footprints(id: String, asOf: Option[Long]): List[PlaceFootprint] =
      db.read(PlaceRepo.footprints(id, asOf)).toList
    def links(id: String): List[PlaceLink] = db.read(PlaceRepo.links(id)).toList
    def scopeFor(values: List[String], period: Option[Period]): PlaceScope = db.read(PlaceRepo.scopeFor(values, period))
    def gazetteer(placeIds: List[String]): (List[RegionNode], List[PlaceRelation]) =
      db.read(TreeRepo.gazetteer(placeIds))
    def rename(from: String, to: String): Either[ApiError, Unit] = db.transact(PlaceRepo.rename(from, to))
    def addName(id: String, name: String, lang: String): Either[ApiError, PlaceNameChanged] =
      db.transact(PlaceRepo.addName(id, name, lang))
    def removeName(id: String, name: String, lang: String): Either[ApiError, PlaceNameChanged] =
      db.transact(PlaceRepo.removeName(id, name, lang))
    def manualNames(id: Option[String]): List[ManualPlaceName] = db.read(PlaceRepo.manualNames(id))
    def add(in: PlaceInput): Either[ApiError, PlaceSummary] = db.transact(PlaceRepo.addPlace(in))
    def overrides(id: Option[String]): List[PlaceOverride] = db.read(PlaceOverrideRepo.list(id))
    def setOverride(id: String, edit: PlaceOverrideEdit, toleranceKm: Double): Either[ApiError, PlaceOverrideChanged] =
      db.transact(PlaceOverrideRepo.set(id, edit, toleranceKm))
    def merge(from: String, into: String, toleranceKm: Double): Either[ApiError, PlaceGone] =
      db.transact(PlaceOverrideRepo.merge(from, into, toleranceKm))
    def remove(id: String, reason: Option[String]): Either[ApiError, PlaceGone] =
      db.transact(PlaceOverrideRepo.remove(id, reason))

  /** The places as a database holding only the catalogue's rows answers: no finding is stored, so none is matched to a
    * place (every count of findings is 0), and a geometry is kept as it was given; a place is valid at a year its
    * period covers. `clock` stands in for the database's `now()` (an override's time).
    */
  final class InMemory(state: AtomicReference[CatalogueState], clock: Clock) extends PlaceService:
    import HeldPlace.lower

    private def places: Vector[HeldPlace] = state.get.places
    private def find(id: String): Option[HeldPlace] = places.find(_.id == id)
    private def notFound(id: String): ApiError = ApiError(s"No place '$id'", Some(Write.CodeNotFound))

    def count(): Int = places.size

    def importPlaces(batch: List[PlaceInput], dataset: Dataset): PlaceImportResult =
      if batch.isEmpty then PlaceImportResult(0, 0, 0, Nil)
      else
        val s0 = state.get
        val skip = batch.map(_.place.id).filter(id => s0.placeRemovals(id) || s0.placeAliases.contains(id)).toSet
        val kept = batch.filterNot(in => skip(in.place.id))
        val ids = kept.map(_.place.id).distinct
        val names = kept.flatMap(in => in.names.map(in.place.id -> _))
        @tailrec def rows(rest: List[PlaceInput], held: Vector[HeldPlace]): Vector[HeldPlace] = rest match
          case Nil        => held
          case in :: tail =>
            val i = held.indexWhere(_.id == in.place.id)
            val o = s0.placeOverrides.find(_.placeId == in.place.id)
            val shown = o.fold(in.place)(o =>
              in.place
                .modify(_.kind)
                .using(k => o.kind.getOrElse(k))
                .modify(_.period)
                .using(p => o.period.flatMap(InMemory.periodOf).orElse(p))
            )
            if i < 0 then rows(tail, held :+ HeldPlace(shown, dataset))
            else
              val before = held(i)
              val manual = before.names.filter(_.manual)
              rows(tail, held.updated(i, HeldPlace(shown, dataset, manual, Vector.empty, before.links)))
        val written = rows(kept, s0.places)
        val withNames = written.map { p =>
          if !ids.contains(p.id) then p
          else
            val stated = names.collect { case (pid, n) if pid == p.id => HeldPlace.Name.of(n) }
            val merged = stated.foldLeft(p.names)((ns, n) =>
              if ns.exists(m => m.name == n.name && m.lang == n.lang) then
                ns.map(m => if m.name == n.name && m.lang == n.lang then m.modify(_.period).setTo(n.period) else m)
              else ns :+ n
            )
            p.modify(_.names).setTo(merged)
        }
        val wanted = kept.flatMap(_.relations)
        val (ok, dangling) = wanted.partition(r => withNames.exists(_.id == r.parentId))
        val related = withNames.map { p =>
          if !ids.contains(p.id) then p
          else p.modify(_.parents).setTo(ok.filter(_.childId == p.id).distinctBy(r => (r.parentId, r.kind)).toVector)
        }
        val _ = state.updateAndGet(_.modify(_.places).setTo(related))
        PlaceImportResult(
          places = ids.size,
          names = names.size,
          relations = ok.size,
          failed = dangling.map(r => s"${r.childId}: no such parent '${r.parentId}'").distinct.take(20)
        )

    def search(q: Option[String], asOf: Option[Long], limit: Int, modernFrom: Long): List[PlaceHit] =
      val text = q.map(_.trim).filter(_.nonEmpty)
      val t = text.map(lower)
      def named(p: HeldPlace) = p.names.filter(n => HeldPlace.covers(n.period, asOf))
      def matches(p: HeldPlace): Boolean = t.forall { w =>
        if w.length >= 3 then lower(p.place.label).contains(w) || named(p).exists(n => lower(n.name).contains(w))
        else lower(p.place.label) == w || named(p).exists(n => lower(n.name) == w)
      }
      val exact = t.getOrElse("")
      places
        .filter(p => p.validAt(asOf) && matches(p))
        .sortBy(p =>
          (
            lower(p.place.label) != exact,
            !t.exists(lower(p.place.label).startsWith(_)),
            p.place.label.length,
            p.id
          )
        )
        .take(limit.max(1))
        .toList
        .map { p =>
          val matched = t.flatMap(w =>
            named(p)
              .filter(n => lower(n.name).contains(w))
              .sortBy(n => (lower(n.name) != exact, n.name.length, n.name))
              .headOption
              .map(_.name)
          )
          val s = p.summary
          PlaceHit(
            s.id,
            s.label,
            s.kind,
            s.periodStart,
            s.periodEnd,
            s.source,
            p.namesAt(asOf),
            matched,
            0,
            s.contested,
            s.dating,
            s.importSource
          )
        }

    private def parentsOf(p: HeldPlace, asOf: Option[Long]): List[PlaceSummary] =
      p.parents
        .filter(r => HeldPlace.covers(r.period, asOf))
        .flatMap(r => find(r.parentId))
        .filter(_.validAt(asOf))
        .distinctBy(_.id)
        .sortBy(q => (q.place.label, q.id))
        .toList
        .map(_.summary)

    private def kidsOf(id: String, asOf: Option[Long]): Vector[HeldPlace] =
      places
        .filter(c => c.parents.exists(r => r.parentId == id && HeldPlace.covers(r.period, asOf)) && c.validAt(asOf))
        .sortBy(c => (c.place.label, c.id))

    def get(id: String, asOf: Option[Long], modernFrom: Long): Option[PlaceDetail] =
      find(id).map { p =>
        PlaceDetail(
          p.summary,
          p.place.geometry,
          parentsOf(p, asOf),
          kidsOf(id, asOf).toList.map(_.summary),
          0,
          asOf,
          p.contested,
          links = p.links.sortBy(l => (l.scheme, l.externalId)).toList
        )
      }

    def children(id: String, asOf: Option[Long], limit: Int, modernFrom: Long): List[PlaceSummary] =
      kidsOf(id, asOf).take(limit.max(1)).toList.map(_.summary)

    def hierarchy(
        asOf: Option[Long],
        root: Option[String],
        maxDepth: Int,
        limit: Int,
        depthCap: Int,
        modernFrom: Long
    ): List[PlaceTreeRow] =
      val depth = math.max(0, math.min(maxDepth, depthCap))
      val heads = root match
        case Some(r) => find(r).toVector
        case None    =>
          places.filter(p =>
            InMemory.RootKinds(p.place.kind) && !places.exists(_.id == p.id && p.parents.nonEmpty) &&
              places.exists(_.parents.exists(_.parentId == p.id))
          )
      def row(p: HeldPlace, d: Int) = PlaceTreeRow(p.id, p.place.label, p.place.kind, d, 0, p.contested)
      def walk(p: HeldPlace, d: Int, seen: Set[String]): List[PlaceTreeRow] =
        row(p, d) :: (if d >= depth then Nil
                      else
                        kidsOf(p.id, asOf).filterNot(c => seen(c.id)).toList.flatMap(c => walk(c, d + 1, seen + c.id)))
      heads
        .filter(_.validAt(asOf))
        .sortBy(h => (lower(h.place.label), h.id))
        .toList
        .flatMap(h => walk(h, 0, Set(h.id)))
        .take(limit.max(1))

    def footprints(id: String, asOf: Option[Long]): List[PlaceFootprint] = Nil
    def links(id: String): List[PlaceLink] = find(id).toList.flatMap(_.links).sortBy(l => (l.scheme, l.externalId))

    def scopeFor(values: List[String], period: Option[Period]): PlaceScope =
      val s = state.get
      val wanted = values.map(_.trim).filter(_.nonEmpty).distinct
      val target = wanted.map(v => v -> s.placeAliases.getOrElse(v, v)).toMap
      val known = wanted.filter(v => find(target(v)).isDefined)
      def overlaps(r: PlaceRelation) =
        period.forall(w => r.period.forall(p => p.start <= w.end && w.start <= p.end))
      @tailrec def down(frontier: List[String], seen: Vector[String]): Vector[String] = frontier match
        case Nil       => seen
        case id :: are =>
          val kids = places.filter(c => c.parents.exists(r => r.parentId == id && overlaps(r))).map(_.id)
          val fresh = kids.filterNot(seen.contains).distinct
          down(are ++ fresh, seen ++ fresh)
      if known.isEmpty then PlaceScope.empty
      else
        PlaceScope(
          known.map(v => v -> down(List(target(v)), Vector(target(v))).toList).toMap,
          known.map(v => v -> find(target(v)).fold(v)(_.place.label)).toMap
        )

    def gazetteer(placeIds: List[String]): (List[RegionNode], List[PlaceRelation]) =
      @tailrec def up(frontier: List[String], seen: Vector[String]): Vector[String] = frontier match
        case Nil       => seen
        case id :: are =>
          val parents = find(id).toVector.flatMap(_.parents.filter(_.kind == PlaceRelation.PartOf).map(_.parentId))
          val fresh = parents.filter(p => find(p).isDefined && !seen.contains(p)).distinct
          up(are ++ fresh, seen ++ fresh)
      val ids = up(placeIds.distinct.filter(find(_).isDefined), placeIds.distinct.filter(find(_).isDefined).toVector)
      val nodes = ids
        .flatMap(find)
        .toList
        .map(p => RegionNode(p.id, p.place.label, p.place.kind, p.place.period, p.contested, withdrawn = false))
      val edges = ids.flatMap(find).flatMap(_.parents).filter(r => ids.contains(r.parentId)).toList
      (nodes, edges)

    def rename(from: String, to: String): Either[ApiError, Unit] =
      val (o, n) = (from.trim, to.trim)
      val aliasOf = state.get.placeAliases.get(n)
      if find(o).isEmpty then Left(notFound(o))
      else if find(n).isDefined then Left(ApiError(s"'$n' is already the id of another place", Some(Write.CodeInvalid)))
      else if aliasOf.exists(_ != o) then
        Left(
          ApiError(
            s"'$n' is an old id of '${aliasOf.get}' and still resolves to it — pick another",
            Some(Write.CodeInvalid)
          )
        )
      else
        val _ = state.updateAndGet(s =>
          s.modify(_.places)
            .using(_.map { p =>
              val renamed = if p.id == o then p.modify(_.place.id).setTo(n) else p
              renamed.modify(_.parents).using(_.map(r => if r.parentId == o then r.modify(_.parentId).setTo(n) else r))
            })
            .modify(_.placeAliases)
            .using(as => (as - n).map((a, t) => a -> (if t == o then n else t)) + (o -> n))
            .modify(_.placeOverrides)
            .using(_.map(x => if x.placeId == o then x.modify(_.placeId).setTo(n) else x))
        )
        Right(())

    def addName(id: String, name: String, lang: String): Either[ApiError, PlaceNameChanged] =
      find(id).toRight(notFound(id)).map { p =>
        val before = p.names.find(n => n.name == name && n.lang == lang).map(_.manual)
        if !before.contains(true) then
          val _ = state.updateAndGet(_.modify(_.places).using(_.map { q =>
            if q.id != id then q
            else if before.isDefined then
              q.modify(_.names)
                .using(_.map(n => if n.name == name && n.lang == lang then n.modify(_.manual).setTo(true) else n))
            else q.modify(_.names).using(_ :+ HeldPlace.Name(name, lang, None, manual = true))
          }))
        PlaceNameChanged(id, p.place.label, name, lang, true, !before.contains(true), before.contains(false))
      }

    def removeName(id: String, name: String, lang: String): Either[ApiError, PlaceNameChanged] =
      find(id).toRight(notFound(id)).flatMap { p =>
        val same = p.names.filter(n => n.name == name && n.lang == lang)
        if same.isEmpty then
          val langs =
            p.names.filter(n => n.name == name && n.manual).map(n => if n.lang.isEmpty then "no language" else n.lang)
          val hint = if langs.isEmpty then "" else s" (it has it with ${langs.mkString(", ")} — pass that language)"
          Left(
            ApiError(
              s"'${p.place.label}' [$id] has no name '$name'${if lang.isEmpty then "" else s" in $lang"}$hint",
              Some(Write.CodeNotFound)
            )
          )
        else if !same.exists(_.manual) then
          Left(
            ApiError(
              s"'$name' is a name the gazetteer seed or an imported source gives '${p.place.label}' [$id], not one added by hand — " +
                "removing it would last only until the next import",
              Some(Write.CodeInvalid)
            )
          )
        else
          val _ = state.updateAndGet(_.modify(_.places).using(_.map { q =>
            if q.id != id then q
            else q.modify(_.names).using(_.filterNot(n => n.name == name && n.lang == lang && n.manual))
          }))
          Right(PlaceNameChanged(id, p.place.label, name, lang, added = false, changed = true))
      }

    def manualNames(id: Option[String]): List[ManualPlaceName] =
      places
        .filter(p => id.forall(_ == p.id))
        .flatMap(p => p.names.filter(_.manual).map(n => ManualPlaceName(p.id, p.place.label, n.name, n.lang)))
        .sortBy(m => (m.placeId, m.name, m.lang))
        .toList

    def add(in: PlaceInput): Either[ApiError, PlaceSummary] =
      val id = in.place.id
      val s = state.get
      if find(id).isDefined then
        Left(ApiError(s"'$id' is already the id of a place — choose another id", Some(Write.CodeInvalid)))
      else if s.placeRemovals(id) then
        Left(ApiError(s"'$id' is the id of a place that was removed — choose another id", Some(Write.CodeInvalid)))
      else if s.placeAliases.contains(id) then
        Left(
          ApiError(
            s"'$id' is an old id of '${s.placeAliases(id)}' and still resolves to it — choose another id",
            Some(Write.CodeInvalid)
          )
        )
      else
        val _ = importPlaces(List(in), Dataset.Local)
        find(id).map(_.summary).toRight(ApiError(s"place '$id' was not stored"))

    def overrides(id: Option[String]): List[PlaceOverride] =
      state.get.placeOverrides
        .filter(o => id.forall(_ == o.placeId))
        .sortBy(_.placeId)
        .toList
        .flatMap(o =>
          find(o.placeId).map { p =>
            val period = o.period.flatMap(InMemory.periodOf)
            PlaceOverride(
              o.placeId,
              p.place.label,
              o.kind,
              period.map(_.start),
              period.map(_.end),
              o.period.isDefined,
              o.reason,
              o.sourceKind,
              o.sourcePeriod.map(_.start),
              o.sourcePeriod.map(_.end),
              o.updatedAt
            )
          }
        )

    def setOverride(id: String, edit: PlaceOverrideEdit, toleranceKm: Double): Either[ApiError, PlaceOverrideChanged] =
      find(id).toRight(notFound(id)).flatMap { before =>
        val s = state.get
        val had = s.placeOverrides.find(_.placeId == id)
        val now = InMemory.timestamp(Instant.now(clock))
        val done: Either[ApiError, Option[HeldOverride]] = edit match
          case PlaceOverrideEdit.Reset =>
            had
              .toRight(
                ApiError(
                  s"'${before.place.label}' [$id] has no kind, period or point set by hand",
                  Some(Write.CodeNotFound)
                )
              )
              .map(_ => None)
          case PlaceOverrideEdit.Put(_, _, _, Some(_)) if before.place.geometry.exists(!InMemory.isPoint(_)) =>
            Left(
              ApiError(
                s"'${before.place.label}' [$id] has a shape, not a point — a point set by hand moves only a place " +
                  "known by a point",
                Some(Write.CodeInvalid)
              )
            )
          case PlaceOverrideEdit.Put(kind, period, reason, _) =>
            val base = had.getOrElse(HeldOverride(id, None, None, None, before.place.kind, before.place.period, now))
            val merged =
              period.map((lo, hi) => base.period.fold((lo, hi))((plo, phi) => (lo.orElse(plo), hi.orElse(phi))))
            Right(
              Some(
                base
                  .modify(_.kind)
                  .using(k => kind.orElse(k))
                  .modify(_.period)
                  .using(p => merged.orElse(p))
                  .modify(_.reason)
                  .using(r => reason.orElse(r))
                  .modify(_.updatedAt)
                  .setTo(now)
              )
            )
        done.map { o =>
          val restored = had.filter(_ => o.isEmpty)
          val _ = state.updateAndGet(st =>
            st.modify(_.placeOverrides)
              .using(os => os.filterNot(_.placeId == id) ++ o.toVector)
              .modify(_.places)
              .using(_.map { p =>
                if p.id != id then p
                else
                  o match
                    case Some(x) =>
                      p.modify(_.place.kind)
                        .using(k => x.kind.getOrElse(k))
                        .modify(_.place.period)
                        .using(q => x.period.flatMap(InMemory.periodOf).orElse(q))
                    case None =>
                      restored.fold(p)(r =>
                        p.modify(_.place.kind).setTo(r.sourceKind).modify(_.place.period).setTo(r.sourcePeriod)
                      )
              })
          )
          val after = find(id).getOrElse(before)
          PlaceOverrideChanged(
            id,
            before.place.label,
            edit == PlaceOverrideEdit.Reset,
            before.place.kind,
            after.place.kind,
            PlaceOverrides.periodText(before.place.period.map(_.start), before.place.period.map(_.end)),
            PlaceOverrides.periodText(after.place.period.map(_.start), after.place.period.map(_.end)),
            0,
            0,
            0
          )
        }
      }

    def merge(from: String, into: String, toleranceKm: Double): Either[ApiError, PlaceGone] =
      (find(from), find(into)) match
        case (None, _)          => Left(notFound(from))
        case (_, None)          => Left(notFound(into))
        case _ if from == into  => Left(ApiError("a place cannot be merged into itself", Some(Write.CodeInvalid)))
        case (Some(f), Some(i)) =>
          val _ = state.updateAndGet(s =>
            s.modify(_.places)
              .using(_.filterNot(_.id == from).map { p =>
                val moved = p
                  .modify(_.parents)
                  .using(_.map(r => if r.parentId == from then r.modify(_.parentId).setTo(into) else r))
                if p.id == into then moved.modify(_.links).using(ls => (ls ++ f.links).distinct) else moved
              })
              .modify(_.placeAliases)
              .using(as => as.map((a, t) => a -> (if t == from then into else t)) + (from -> into))
              .modify(_.placeOverrides)
              .using(_.filterNot(_.placeId == from))
          )
          Right(PlaceGone(from, f.place.label, Some(into), Some(i.place.label), 0, 0, 0))

    def remove(id: String, reason: Option[String]): Either[ApiError, PlaceGone] =
      find(id).toRight(notFound(id)).flatMap { p =>
        val s = state.get
        val refs = List(
          "event(s)" -> s.events.count(_.placeId.contains(id)),
          "attestation(s)" -> s.attestations.count(a => a.targetType == "place" && a.targetId == id),
          "child place(s)" -> places.count(_.parents.exists(_.parentId == id)),
          "name(s) added by hand" -> p.names.count(_.manual)
        ).filter(_._2 > 0)
        if refs.nonEmpty then
          Left(
            ApiError(
              s"'${p.place.label}' [$id] is referred to by ${refs.map((w, c) => s"$c $w").mkString(", ")} — " +
                "merge it into the place it is the same as instead, or move those first",
              Some(Write.CodeInvalid)
            )
          )
        else
          val _ = state.updateAndGet(
            _.modify(_.places).using(_.filterNot(_.id == id)).modify(_.placeRemovals).using(_ + id)
          )
          Right(PlaceGone(id, p.place.label, None, None, 0, 0, 0))
      }

  object InMemory:
    /** The kinds that may head the hierarchy. */
    val RootKinds: Set[String] = Set("region", "polity", "continent")

    /** An override's sides as a closed period (an open side reads as the widest year). */
    def periodOf(sides: (Option[Long], Option[Long])): Option[Period] =
      sides match
        case (None, None) => None
        case (lo, hi)     => Some(Period(lo.getOrElse(Long.MinValue), hi.getOrElse(Long.MaxValue)))

    def isPoint(geojson: String): Boolean =
      io.circe.parser.parse(geojson).toOption.flatMap(_.hcursor.downField("type").as[String].toOption).contains("Point")

    def timestamp(at: Instant): String = at.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString
