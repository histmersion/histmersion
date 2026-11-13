package com.histmersion
package domain.catalogue.places
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.api.{Dataset, Write}
import com.histmersion.api.catalogue.{ManualPlaceName, Period, Year}
import com.histmersion.api.catalogue.RegionNode
import com.histmersion.domain.catalogue.CatalogueState
import com.histmersion.domain.catalogue.places.PlaceSql.DefaultModernFrom
import com.histmersion.util.database.Transactor
import hearth.kindlings.optics.syntax.*

import java.time.{Clock, Instant}
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import com.histmersion.api.Api
import com.histmersion.api.catalogue.Place
import com.histmersion.domain.DeepTime
import com.histmersion.domain.catalogue.Place as DPlace
import io.github.iltotore.iron.constraint.any.Pure

/** The gazetteer's records: the places, their names (a person's among them), their old ids, the kinds and periods a
  * person set over a source's, and the hierarchy — imported, searched, read at a year, curated by hand. A finding's
  * place is the library's; what these answer about findings counts the places' matches (`finding_places`).
  */
trait PlaceService:
  def count(): RowCount

  /** A batch upserted in one transaction: rows, then names, then the edges whose parent exists by then. */
  def importPlaces(batch: List[Place.Input], dataset: Dataset): Place.ImportResult

  /** The places a name search finds, valid at `asOf`: exact labels first, then prefixes. */
  def search(
      q: Option[PlaceService.SearchQ],
      asOf: Option[DeepTime.SignedYear],
      limit: PageSize,
      modernFrom: DeepTime.SignedYear = DefaultModernFrom
  ): List[Place.Hit]

  /** One place with the hierarchy around it at `asOf`. */
  def get(
      id: DPlace.Id,
      asOf: Option[DeepTime.SignedYear] = None,
      modernFrom: DeepTime.SignedYear = DefaultModernFrom
  ): Option[Place.Entry]

  def children(
      id: DPlace.Id,
      asOf: Option[DeepTime.SignedYear],
      limit: PageSize,
      modernFrom: DeepTime.SignedYear = DefaultModernFrom
  ): List[Place.Summary]

  /** The hierarchy at `asOf` as a depth-tagged pre-order walk, from `root` or from every place that heads it. */
  def hierarchy(
      asOf: Option[DeepTime.SignedYear],
      root: Option[DPlace.Id],
      maxDepth: PlaceService.DepthLimit,
      limit: PageSize,
      depthCap: PlaceService.DepthLimit = PlaceService.DepthLimit(Int.MaxValue),
      modernFrom: DeepTime.SignedYear = DefaultModernFrom
  ): List[Place.TreeRow]

  def footprints(id: DPlace.Id, asOf: Option[DeepTime.SignedYear]): List[Place.Footprint]
  def links(id: DPlace.Id): List[Place.Link]

  /** Which of a filter's `places` values are places, and what each stands for: itself and its descendants. */
  def scopeFor(values: List[PlaceService.ScopeForValues], period: Option[Period]): PlaceScope

  /** The places `placeIds` and all their ancestors, with the edges between them. */
  def gazetteer(placeIds: List[DPlace.Id]): (List[RegionNode], List[Place.Relation])

  /** A new id; the old one stays an alias of it. */
  def rename(from: DPlace.Id, to: DPlace.Id): Either[Api.Error, Unit]

  def addName(id: DPlace.Id, name: Place.Name.Value, lang: Place.Name.Lang): Either[Api.Error, Place.NameChanged]

  /** Only a name a person added; an imported one is refused. */
  def removeName(id: DPlace.Id, name: Place.Name.Value, lang: Place.Name.Lang): Either[Api.Error, Place.NameChanged]

  def manualNames(id: Option[DPlace.Id]): List[ManualPlaceName]

  /** A person's own point place (`local`); refused when its id is taken, removed or an old id. */
  def add(in: Place.Input): Either[Api.Error, Place.Summary]

  def overrides(id: Option[DPlace.Id]): List[Place.Override]

  /** A person's kind, period or point set over the source's (or reset), and the place's findings matched again. */
  def setOverride(
      id: DPlace.Id,
      edit: Place.OverrideEdit,
      toleranceKm: GeocodeSettings.ToleranceKm
  ): Either[Api.Error, Place.OverrideChanged]

  /** `from` merged into `into`: what refers to it moves, its id resolves there. */
  def merge(from: DPlace.Id, into: DPlace.Id, toleranceKm: GeocodeSettings.ToleranceKm): Either[Api.Error, Place.Gone]

  /** A place nothing refers to, removed for good. */
  def remove(id: DPlace.Id, reason: Option[Problem]): Either[Api.Error, Place.Gone]

object PlaceService:
  type DepthLimit = DepthLimit.T
  object DepthLimit extends Newtype[Int, Pure]
  type SearchQ = SearchQ.T
  object SearchQ extends Newtype[String, Pure]
  type ScopeForValues = ScopeForValues.T
  object ScopeForValues extends Newtype[String, Pure]

  def apply(db: Transactor): PlaceService = Implementation(db)

  final private class Implementation(db: Transactor) extends PlaceService:
    def count(): RowCount = db.read(PlaceRepo.count())
    def importPlaces(batch: List[Place.Input], dataset: Dataset): Place.ImportResult =
      if batch.isEmpty then
        Place.ImportResult(
          Place.ImportResult.Places(0),
          Place.ImportResult.Names(0),
          Place.ImportResult.Relations(0),
          Nil
        )
      else db.transact(PlaceRepo.upsertPlaces(batch, dataset))
    def search(
        q: Option[SearchQ],
        asOf: Option[DeepTime.SignedYear],
        limit: PageSize,
        modernFrom: DeepTime.SignedYear
    ): List[Place.Hit] =
      db.read(
        PlaceRepo.search(q.map(t => PlaceRepo.SearchText(t.value)), asOf, PlaceRepo.Limit(limit.value), modernFrom)
      ).toList
    def get(id: DPlace.Id, asOf: Option[DeepTime.SignedYear], modernFrom: DeepTime.SignedYear): Option[Place.Entry] =
      db.read(PlaceRepo.get(id, asOf, modernFrom))
    def children(
        id: DPlace.Id,
        asOf: Option[DeepTime.SignedYear],
        limit: PageSize,
        modernFrom: DeepTime.SignedYear
    ): List[Place.Summary] =
      db.read(PlaceRepo.children(id, asOf, PlaceRepo.Limit(limit.value), modernFrom)).toList
    def hierarchy(
        asOf: Option[DeepTime.SignedYear],
        root: Option[DPlace.Id],
        maxDepth: DepthLimit,
        limit: PageSize,
        depthCap: DepthLimit,
        modernFrom: DeepTime.SignedYear
    ): List[Place.TreeRow] =
      db.read(
        PlaceRepo.hierarchy(
          asOf,
          root,
          PlaceRepo.Depth(maxDepth.value),
          PlaceRepo.Limit(limit.value),
          PlaceRepo.Depth(depthCap.value),
          modernFrom
        )
      ).toList
    def footprints(id: DPlace.Id, asOf: Option[DeepTime.SignedYear]): List[Place.Footprint] =
      db.read(PlaceRepo.footprints(id, asOf)).toList
    def links(id: DPlace.Id): List[Place.Link] = db.read(PlaceRepo.links(id)).toList
    def scopeFor(values: List[ScopeForValues], period: Option[Period]): PlaceScope =
      db.read(PlaceRepo.scopeFor(values.map(v => PlaceRepo.ScopeForValues(v.value)), period))
    def gazetteer(placeIds: List[DPlace.Id]): (List[RegionNode], List[Place.Relation]) =
      db.read(TreeRepo.gazetteer(placeIds))
    def rename(from: DPlace.Id, to: DPlace.Id): Either[Api.Error, Unit] = db.transact(PlaceRepo.rename(from, to))
    def addName(id: DPlace.Id, name: Place.Name.Value, lang: Place.Name.Lang): Either[Api.Error, Place.NameChanged] =
      db.transact(PlaceRepo.addName(id, PlaceRepo.Name(name.value), PlaceRepo.Lang(lang.value)))
    def removeName(id: DPlace.Id, name: Place.Name.Value, lang: Place.Name.Lang): Either[Api.Error, Place.NameChanged] =
      db.transact(PlaceRepo.removeName(id, PlaceRepo.Name(name.value), PlaceRepo.Lang(lang.value)))
    def manualNames(id: Option[DPlace.Id]): List[ManualPlaceName] = db.read(PlaceRepo.manualNames(id))
    def add(in: Place.Input): Either[Api.Error, Place.Summary] = db.transact(PlaceRepo.addPlace(in))
    def overrides(id: Option[DPlace.Id]): List[Place.Override] =
      db.read(PlaceOverrideRepo.list(id.map(i => BPlaceRow.Id(i.value))))
    def setOverride(
        id: DPlace.Id,
        edit: Place.OverrideEdit,
        toleranceKm: GeocodeSettings.ToleranceKm
    ): Either[Api.Error, Place.OverrideChanged] =
      db.transact(PlaceOverrideRepo.set(BPlaceRow.Id(id.value), edit, toleranceKm))
    def merge(
        from: DPlace.Id,
        into: DPlace.Id,
        toleranceKm: GeocodeSettings.ToleranceKm
    ): Either[Api.Error, Place.Gone] =
      db.transact(PlaceOverrideRepo.merge(BPlaceRow.Id(from.value), BPlaceRow.Id(into.value), toleranceKm))
    def remove(id: DPlace.Id, reason: Option[Problem]): Either[Api.Error, Place.Gone] =
      db.transact(
        PlaceOverrideRepo.remove(BPlaceRow.Id(id.value), reason)
      )

  /** The places as a database holding only the catalogue's rows answers: no finding is stored, so none is matched to a
    * place (every count of findings is 0), and a geometry is kept as it was given; a place is valid at a year its
    * period covers. `clock` stands in for the database's `now()` (an override's time).
    */
  final class InMemory(state: AtomicReference[CatalogueState], clock: Clock) extends PlaceService:
    import HeldPlace.lower

    private def places: Vector[HeldPlace] = state.get.places
    private def find(id: Place.Id): Option[HeldPlace] = places.find(_.id == id)
    private def apiId(id: DPlace.Id): Place.Id = Place.Id(id.value)
    private def parentIs(r: Place.Relation, id: Place.Id): Boolean = r.parentId.value == id.value
    private def parentOf(r: Place.Relation): Place.Id = Place.Id(r.parentId.value)
    private def notFound(id: String): Api.Error =
      Api.Error(Problem.Unclassified(Legacy.Text(s"No place '$id'")), Some(Api.Error.Code(Write.CodeNotFound)))
    private def invalid(message: String): Api.Error =
      Api.Error(Problem.Unclassified(Legacy.Text(message)), Some(Api.Error.Code(Write.CodeInvalid)))

    def count(): RowCount = RowCount(places.size)

    def importPlaces(batch: List[Place.Input], dataset: Dataset): Place.ImportResult =
      if batch.isEmpty then
        Place.ImportResult(
          Place.ImportResult.Places(0),
          Place.ImportResult.Names(0),
          Place.ImportResult.Relations(0),
          Nil
        )
      else
        val s0 = state.get
        val skip = batch
          .map(_.place.id)
          .filter(id =>
            s0.placeRemovals(CatalogueState.PlaceRemovals(id.value)) || s0.placeAliases
              .contains(CatalogueState.PlaceAliasesKey(id.value))
          )
          .toSet
        val kept = batch.filterNot(in => skip(in.place.id))
        val ids = kept.map(_.place.id).distinct
        val names = kept.flatMap(in => in.names.map(in.place.id -> _))
        @tailrec def rows(rest: List[Place.Input], held: Vector[HeldPlace]): Vector[HeldPlace] = rest match
          case Nil        => held
          case in :: tail =>
            val i = held.indexWhere(_.id == in.place.id)
            val o = s0.placeOverrides.find(_.placeId.value == in.place.id.value)
            val shown = o.fold(in.place)(o =>
              in.place
                .modify(_.kind)
                .using(k => o.kind.fold(k)(x => Place.KindValue(x.value)))
                .modify(_.period)
                .using(p => o.period.flatMap(InMemory.periodOf).orElse(p))
            )
            if i < 0 then rows(tail, held :+ HeldPlace(shown, dataset))
            else
              val before = held(i)
              val manual = before.names.filter(_.manual.value)
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
        val (ok, dangling) = wanted.partition(r => withNames.exists(_.id.value == r.parentId.value))
        val related = withNames.map { p =>
          if !ids.contains(p.id) then p
          else
            p.modify(_.parents)
              .setTo(
                ok.filter(_.childId.value == p.id.value).distinctBy(r => (r.parentId.value, r.kind.value)).toVector
              )
        }
        val _ = state.updateAndGet(_.modify(_.places).setTo(related))
        Place.ImportResult(
          places = Place.ImportResult.Places(ids.size),
          names = Place.ImportResult.Names(names.size),
          relations = Place.ImportResult.Relations(ok.size),
          failed = dangling
            .map(r => Place.ImportResult.Failed(s"${r.childId}: no such parent '${r.parentId}'"))
            .distinct
            .take(20)
        )

    def search(
        q: Option[SearchQ],
        asOf: Option[DeepTime.SignedYear],
        limit: PageSize,
        modernFrom: DeepTime.SignedYear
    ): List[Place.Hit] =
      val text = q.map(_.value.trim).filter(_.nonEmpty)
      val t = text.map(lower)
      def named(p: HeldPlace) = p.names.filter(n => HeldPlace.covers(n.period, asOf))
      def matches(p: HeldPlace): Boolean = t.forall { w =>
        if w.length >= 3 then
          lower(p.place.label.value).contains(w) || named(p).exists(n => lower(n.name.value).contains(w))
        else lower(p.place.label.value) == w || named(p).exists(n => lower(n.name.value) == w)
      }
      val exact = t.getOrElse("")
      places
        .filter(p => p.validAt(asOf) && matches(p))
        .sortBy(p =>
          (
            lower(p.place.label.value) != exact,
            !t.exists(lower(p.place.label.value).startsWith(_)),
            p.place.label.value.length,
            p.id.value
          )
        )
        .take(limit.value)
        .toList
        .map { p =>
          val matched = t.flatMap(w =>
            named(p)
              .filter(n => lower(n.name.value).contains(w))
              .sortBy(n => (lower(n.name.value) != exact, n.name.value.length, n.name.value))
              .headOption
              .map(_.name)
          )
          val s = p.summary
          Place.Hit(
            s.id,
            s.label,
            s.kind,
            s.periodStart,
            s.periodEnd,
            s.source,
            p.namesAt(asOf),
            matched.map(m => Place.Hit.MatchedName(m.value)),
            Place.Hit.Findings(0),
            s.contested,
            s.dating,
            s.importSource
          )
        }

    private def parentsOf(p: HeldPlace, asOf: Option[DeepTime.SignedYear]): List[Place.Summary] =
      p.parents
        .filter(r => HeldPlace.covers(r.period, asOf))
        .flatMap(r => find(parentOf(r)))
        .filter(_.validAt(asOf))
        .distinctBy(_.id)
        .sortBy(q => (q.place.label.value, q.id.value))
        .toList
        .map(_.summary)

    private def kidsOf(id: Place.Id, asOf: Option[DeepTime.SignedYear]): Vector[HeldPlace] =
      places
        .filter(c => c.parents.exists(r => parentIs(r, id) && HeldPlace.covers(r.period, asOf)) && c.validAt(asOf))
        .sortBy(c => (c.place.label.value, c.id.value))

    def get(id: DPlace.Id, asOf: Option[DeepTime.SignedYear], modernFrom: DeepTime.SignedYear): Option[Place.Entry] =
      find(apiId(id)).map { p =>
        Place.Entry(
          p.summary,
          p.place.geometry,
          parentsOf(p, asOf),
          kidsOf(apiId(id), asOf).toList.map(_.summary),
          Place.Counts.Findings(0),
          asOf.map(y => Year(y.value)),
          Place.Entry.Contested(p.contested),
          links = p.links.sortBy(l => (l.scheme.value, l.externalId.value)).toList
        )
      }

    def children(
        id: DPlace.Id,
        asOf: Option[DeepTime.SignedYear],
        limit: PageSize,
        modernFrom: DeepTime.SignedYear
    ): List[Place.Summary] =
      kidsOf(apiId(id), asOf).take(limit.value).toList.map(_.summary)

    def hierarchy(
        asOf: Option[DeepTime.SignedYear],
        root: Option[DPlace.Id],
        maxDepth: DepthLimit,
        limit: PageSize,
        depthCap: DepthLimit,
        modernFrom: DeepTime.SignedYear
    ): List[Place.TreeRow] =
      val depth = math.max(0, math.min(maxDepth.value, depthCap.value))
      val heads = root match
        case Some(r) => find(apiId(r)).toVector
        case None    =>
          places.filter(p =>
            InMemory.RootKinds(p.place.kind.value) && !places.exists(_.id == p.id && p.parents.nonEmpty) &&
              places.exists(_.parents.exists(r => parentIs(r, p.id)))
          )
      def row(p: HeldPlace, d: Int) = Place.TreeRow(
        p.id,
        p.place.label,
        Place.TreeRow.Kind(p.place.kind.value),
        Place.TreeRow.Depth(d),
        Place.TreeRow.Findings(0),
        Place.TreeRow.Contested(p.contested)
      )
      def walk(p: HeldPlace, d: Int, seen: Set[Place.Id]): List[Place.TreeRow] =
        row(p, d) :: (if d >= depth then Nil
                      else
                        kidsOf(p.id, asOf).filterNot(c => seen(c.id)).toList.flatMap(c => walk(c, d + 1, seen + c.id)))
      heads
        .filter(_.validAt(asOf))
        .sortBy(h => (lower(h.place.label.value), h.id.value))
        .toList
        .flatMap(h => walk(h, 0, Set(h.id)))
        .take(limit.value)

    def footprints(id: DPlace.Id, asOf: Option[DeepTime.SignedYear]): List[Place.Footprint] = Nil
    def links(id: DPlace.Id): List[Place.Link] =
      find(apiId(id)).toList.flatMap(_.links).sortBy(l => (l.scheme.value, l.externalId.value))

    def scopeFor(values: List[ScopeForValues], period: Option[Period]): PlaceScope =
      val s = state.get
      val wanted = values.map(_.value.trim).filter(_.nonEmpty).distinct
      val target = wanted.map(v => v -> s.placeAliases.get(CatalogueState.PlaceAliasesKey(v)).fold(v)(_.value)).toMap
      val known = wanted.filter(v => find(Place.Id(target(v))).isDefined)
      def overlaps(r: Place.Relation) =
        period.forall(w => r.period.forall(p => p.start <= w.end && w.start <= p.end))
      @tailrec def down(frontier: List[Place.Id], seen: Vector[Place.Id]): Vector[Place.Id] = frontier match
        case Nil       => seen
        case id :: are =>
          val kids = places.filter(c => c.parents.exists(r => parentIs(r, id) && overlaps(r))).map(_.id)
          val fresh = kids.filterNot(seen.contains).distinct
          down(are ++ fresh, seen ++ fresh)
      if known.isEmpty then PlaceScope.empty
      else
        PlaceScope(
          known
            .map(v =>
              PlaceScope.Value(v) -> down(List(Place.Id(target(v))), Vector(Place.Id(target(v)))).toList
                .flatMap(i => DPlace.Id.option(i.value))
            )
            .toMap,
          known
            .map(v => PlaceScope.Value(v) -> PlaceRepo.Label(find(Place.Id(target(v))).fold(v)(_.place.label.value)))
            .toMap
        )

    def gazetteer(placeIds: List[DPlace.Id]): (List[RegionNode], List[Place.Relation]) =
      @tailrec def up(frontier: List[Place.Id], seen: Vector[Place.Id]): Vector[Place.Id] = frontier match
        case Nil       => seen
        case id :: are =>
          val parents = find(id).toVector.flatMap(_.parents.filter(_.kind.value == Place.Relation.PartOf).map(parentOf))
          val fresh = parents.filter(p => find(p).isDefined && !seen.contains(p)).distinct
          up(are ++ fresh, seen ++ fresh)
      val start = placeIds.distinct.map(apiId).filter(find(_).isDefined)
      val ids = up(start, start.toVector)
      val nodes = ids
        .flatMap(find)
        .toList
        .map(p =>
          RegionNode(
            RegionNode.Id(p.id.value),
            RegionNode.Label(p.place.label.value),
            RegionNode.Kind(p.place.kind.value),
            p.place.period,
            RegionNode.Contested(p.contested),
            withdrawn = RegionNode.Withdrawn(false)
          )
        )
      val edges = ids.flatMap(find).flatMap(_.parents).filter(r => ids.exists(_.value == r.parentId.value)).toList
      (nodes, edges)

    def rename(from: DPlace.Id, to: DPlace.Id): Either[Api.Error, Unit] =
      val (o, n) = (Place.Id(from.value.trim), Place.Id(to.value.trim))
      val aliasOf = state.get.placeAliases.get(CatalogueState.PlaceAliasesKey(n.value))
      if find(o).isEmpty then Left(notFound(o.value))
      else if find(n).isDefined then Left(invalid(s"'$n' is already the id of another place"))
      else if aliasOf.exists(_.value != o.value) then
        Left(
          invalid(s"'$n' is an old id of '${aliasOf.get}' and still resolves to it — pick another")
        )
      else
        val _ = state.updateAndGet(s =>
          s.modify(_.places)
            .using(_.map { p =>
              val renamed = if p.id == o then p.modify(_.place.id).setTo(n) else p
              renamed
                .modify(_.parents)
                .using(
                  _.map(r => if parentIs(r, o) then r.modify(_.parentId).setTo(Place.Relation.ParentId(n.value)) else r)
                )
            })
            .modify(_.placeAliases)
            .using(as =>
              (as - CatalogueState.PlaceAliasesKey(n.value)).map((a, t) =>
                a -> (if t.value == o.value then CatalogueState.PlaceAliasesValue(n.value) else t)
              ) + (CatalogueState.PlaceAliasesKey(o.value) -> CatalogueState.PlaceAliasesValue(n.value))
            )
            .modify(_.placeOverrides)
            .using(_.map(x => if x.placeId.value == o.value then x.modify(_.placeId).setTo(to) else x))
        )
        Right(())

    def addName(id: DPlace.Id, name: Place.Name.Value, lang: Place.Name.Lang): Either[Api.Error, Place.NameChanged] =
      find(apiId(id)).toRight(notFound(id.value)).map { p =>
        val before = p.names.find(n => n.name == name && n.lang == lang).map(_.manual.value)
        if !before.contains(true) then
          val _ = state.updateAndGet(_.modify(_.places).using(_.map { q =>
            if q.id != p.id then q
            else if before.isDefined then
              q.modify(_.names)
                .using(
                  _.map(n =>
                    if n.name == name && n.lang == lang then n.modify(_.manual).setTo(HeldPlace.Name.Manual(true))
                    else n
                  )
                )
            else q.modify(_.names).using(_ :+ HeldPlace.Name(name, lang, None, manual = HeldPlace.Name.Manual(true)))
          }))
        Place.NameChanged(
          p.id,
          p.place.label,
          Place.NameChanged.Name(name.value),
          lang,
          Place.NameChanged.Added(true),
          Place.NameChanged.Changed(!before.contains(true)),
          Place.NameChanged.WasImported(before.contains(false))
        )
      }

    def removeName(id: DPlace.Id, name: Place.Name.Value, lang: Place.Name.Lang): Either[Api.Error, Place.NameChanged] =
      find(apiId(id)).toRight(notFound(id.value)).flatMap { p =>
        val same = p.names.filter(n => n.name == name && n.lang == lang)
        if same.isEmpty then
          val langs =
            p.names
              .filter(n => n.name == name && n.manual.value)
              .map(n => if n.lang.value.isEmpty then "no language" else n.lang.value)
          val hint = if langs.isEmpty then "" else s" (it has it with ${langs.mkString(", ")} — pass that language)"
          Left(
            Api.Error(
              Problem.Unclassified(
                Legacy.Text(
                  s"'${p.place.label}' [$id] has no name '$name'${if lang.value.isEmpty then "" else s" in $lang"}$hint"
                )
              ),
              Some(Api.Error.Code(Write.CodeNotFound))
            )
          )
        else if !same.exists(_.manual.value) then
          Left(
            invalid(
              s"'$name' is a name the gazetteer seed or an imported source gives '${p.place.label}' [$id], not one added by hand — " +
                "removing it would last only until the next import"
            )
          )
        else
          val _ = state.updateAndGet(_.modify(_.places).using(_.map { q =>
            if q.id != p.id then q
            else q.modify(_.names).using(_.filterNot(n => n.name == name && n.lang == lang && n.manual.value))
          }))
          Right(
            Place.NameChanged(
              p.id,
              p.place.label,
              Place.NameChanged.Name(name.value),
              lang,
              added = Place.NameChanged.Added(false),
              changed = Place.NameChanged.Changed(true)
            )
          )
      }

    def manualNames(id: Option[DPlace.Id]): List[ManualPlaceName] =
      places
        .filter(p => id.forall(_.value == p.id.value))
        .flatMap(p =>
          p.names
            .filter(_.manual.value)
            .map(n =>
              ManualPlaceName(
                p.id,
                ManualPlaceName.Label(p.place.label.value),
                ManualPlaceName.Name(n.name.value),
                ManualPlaceName.Lang(n.lang.value)
              )
            )
        )
        .sortBy(m => (m.placeId.value, m.name.value, m.lang.value))
        .toList

    def add(in: Place.Input): Either[Api.Error, Place.Summary] =
      val id = in.place.id
      val s = state.get
      if find(id).isDefined then Left(invalid(s"'$id' is already the id of a place — choose another id"))
      else if s.placeRemovals(CatalogueState.PlaceRemovals(id.value)) then
        Left(invalid(s"'$id' is the id of a place that was removed — choose another id"))
      else if s.placeAliases.contains(CatalogueState.PlaceAliasesKey(id.value)) then
        Left(
          invalid(
            s"'$id' is an old id of '${s.placeAliases(CatalogueState.PlaceAliasesKey(id.value))}' and still resolves to it — choose another id"
          )
        )
      else
        val _ = importPlaces(List(in), Dataset.Local)
        find(id).map(_.summary).toRight(Api.Error(Problem.Unclassified(Legacy.Text(s"place '$id' was not stored"))))

    def overrides(id: Option[DPlace.Id]): List[Place.Override] =
      state.get.placeOverrides
        .filter(o => id.forall(_ == o.placeId))
        .sortBy(_.placeId.value)
        .toList
        .flatMap(o =>
          find(apiId(o.placeId)).map { p =>
            val period = o.period.flatMap(InMemory.periodOf)
            Place.Override(
              apiId(o.placeId),
              p.place.label,
              o.kind.map(k => Place.Override.Kind(k.value)),
              period.map(_.start),
              period.map(_.end),
              Place.Override.PeriodSet(o.period.isDefined),
              o.reason,
              Place.Override.SourceKind(o.sourceKind.value),
              o.sourcePeriod.map(_.start),
              o.sourcePeriod.map(_.end),
              Place.Override.UpdatedAt(o.updatedAt.value)
            )
          }
        )

    def setOverride(
        id: DPlace.Id,
        edit: Place.OverrideEdit,
        toleranceKm: GeocodeSettings.ToleranceKm
    ): Either[Api.Error, Place.OverrideChanged] =
      find(apiId(id)).toRight(notFound(id.value)).flatMap { before =>
        val s = state.get
        val had = s.placeOverrides.find(_.placeId == id)
        val now = InMemory.timestamp(Instant.now(clock))
        val done: Either[Api.Error, Option[HeldOverride]] = edit match
          case Place.OverrideEdit.Reset =>
            had
              .toRight(
                Api.Error(
                  Problem.Unclassified(
                    Legacy.Text(s"'${before.place.label}' [$id] has no kind, period or point set by hand")
                  ),
                  Some(Api.Error.Code(Write.CodeNotFound))
                )
              )
              .map(_ => None)
          case Place.OverrideEdit.Put(_, _, _, Some(_))
              if before.place.geometry.exists(g => !InMemory.isPoint(g.value)) =>
            Left(
              invalid(
                s"'${before.place.label}' [$id] has a shape, not a point — a point set by hand moves only a place " +
                  "known by a point"
              )
            )
          case Place.OverrideEdit.Put(kind, period, reason, _) =>
            val base = had.getOrElse(
              HeldOverride(
                id,
                None,
                None,
                None,
                HeldOverride.SourceKind(before.place.kind.value),
                before.place.period,
                now
              )
            )
            val merged =
              period.map((lo, hi) =>
                val (nlo, nhi) =
                  (lo.map(y => HeldOverride.Bound.Start(y.value)), hi.map(y => HeldOverride.Bound.End(y.value)))
                base.period.fold((nlo, nhi))((plo, phi) => (nlo.orElse(plo), nhi.orElse(phi)))
              )
            Right(
              Some(
                base
                  .modify(_.kind)
                  .using(k => kind.map(x => HeldOverride.Kind(x.value)).orElse(k))
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
              .using(
                _.map { p =>
                  if p.id.value != id.value then p
                  else
                    o match
                      case Some(x) =>
                        p.modify(_.place.kind)
                          .using(k => x.kind.fold(k)(v => Place.KindValue(v.value)))
                          .modify(_.place.period)
                          .using(q => x.period.flatMap(InMemory.periodOf).orElse(q))
                      case None =>
                        restored.fold(p)(r =>
                          p.modify(_.place.kind)
                            .setTo(Place.KindValue(r.sourceKind.value))
                            .modify(_.place.period)
                            .setTo(r.sourcePeriod)
                        )
                }
              )
          )
          val after = find(apiId(id)).getOrElse(before)
          Place.OverrideChanged(
            apiId(id),
            before.place.label,
            Place.OverrideChanged.Reset(edit == Place.OverrideEdit.Reset),
            Place.KindValue(before.place.kind.value),
            Place.KindValue(after.place.kind.value),
            Place.Overrides.periodText(before.place.period.map(_.start), before.place.period.map(_.end)),
            Place.Overrides.periodText(after.place.period.map(_.start), after.place.period.map(_.end)),
            Place.OverrideChanged.FindingsBefore(0),
            Place.OverrideChanged.FindingsAfter(0),
            Place.OverrideChanged.Unlocated(0)
          )
        }
      }

    def merge(
        from: DPlace.Id,
        into: DPlace.Id,
        toleranceKm: GeocodeSettings.ToleranceKm
    ): Either[Api.Error, Place.Gone] =
      (find(apiId(from)), find(apiId(into))) match
        case (None, _)          => Left(notFound(from.value))
        case (_, None)          => Left(notFound(into.value))
        case _ if from == into  => Left(invalid("a place cannot be merged into itself"))
        case (Some(f), Some(i)) =>
          val _ = state.updateAndGet(s =>
            s.modify(_.places)
              .using(_.filterNot(_.id.value == from.value).map { p =>
                val moved = p
                  .modify(_.parents)
                  .using(
                    _.map(r =>
                      if r.parentId.value == from.value then
                        r.modify(_.parentId).setTo(Place.Relation.ParentId(into.value))
                      else r
                    )
                  )
                if p.id.value == into.value then moved.modify(_.links).using(ls => (ls ++ f.links).distinct) else moved
              })
              .modify(_.placeAliases)
              .using(as =>
                as.map((a, t) =>
                  a -> (if t.value == from.value then CatalogueState.PlaceAliasesValue(into.value) else t)
                ) + (CatalogueState.PlaceAliasesKey(from.value) -> CatalogueState.PlaceAliasesValue(into.value))
              )
              .modify(_.placeOverrides)
              .using(_.filterNot(_.placeId == from))
          )
          Right(
            Place.Gone(
              f.id,
              f.place.label,
              Some(Place.Gone.Into(into.value)),
              Some(Place.Gone.IntoLabel(i.place.label.value)),
              Place.Gone.FindingsBefore(0),
              Place.Gone.FindingsAfter(0),
              Place.Gone.Unlocated(0)
            )
          )

    def remove(id: DPlace.Id, reason: Option[Problem]): Either[Api.Error, Place.Gone] =
      find(apiId(id)).toRight(notFound(id.value)).flatMap { p =>
        val s = state.get
        val refs = List(
          "event(s)" -> s.events.count(_.placeId.contains(id)),
          "attestation(s)" -> s.attestations.count(a => a.targetType.value == "place" && a.targetId.value == id.value),
          "child place(s)" -> places.count(_.parents.exists(r => parentIs(r, p.id))),
          "name(s) added by hand" -> p.names.count(_.manual.value)
        ).filter(_._2 > 0)
        if refs.nonEmpty then
          Left(
            invalid(
              s"'${p.place.label}' [$id] is referred to by ${refs.map((w, c) => s"$c $w").mkString(", ")} — " +
                "merge it into the place it is the same as instead, or move those first"
            )
          )
        else
          val _ = state.updateAndGet(
            _.modify(_.places)
              .using(_.filterNot(_.id == p.id))
              .modify(_.placeRemovals)
              .using(_ + CatalogueState.PlaceRemovals(id.value))
          )
          Right(
            Place.Gone(
              p.id,
              p.place.label,
              None,
              None,
              Place.Gone.FindingsBefore(0),
              Place.Gone.FindingsAfter(0),
              Place.Gone.Unlocated(0)
            )
          )
      }

  object InMemory:
    /** The kinds that may head the hierarchy. */
    val RootKinds: Set[String] = Set("region", "polity", "continent")

    /** An override's sides as a closed period (an open side reads as the widest year). */
    def periodOf(sides: (Option[HeldOverride.Bound.Start], Option[HeldOverride.Bound.End])): Option[Period] =
      sides match
        case (None, None) => None
        case (lo, hi)     => Some(Period(Year(lo.fold(Long.MinValue)(_.value)), Year(hi.fold(Long.MaxValue)(_.value))))

    def isPoint(geojson: String): Boolean =
      RawJson.parse(geojson).toOption.flatMap(_.cursor.downField("type").as[String].toOption).contains("Point")

    def timestamp(at: Instant): HeldOverride.UpdatedAt =
      HeldOverride.UpdatedAt(at.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString)
