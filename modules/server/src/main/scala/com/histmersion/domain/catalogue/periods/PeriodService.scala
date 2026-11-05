package com.histmersion.domain.catalogue.periods

import com.histmersion.api.Dataset
import com.histmersion.api.catalogue.HistoricalPeriod
import com.histmersion.domain.catalogue.CatalogueState
import com.histmersion.util.database.Transactor
import hearth.kindlings.optics.syntax.*

import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

/** The named periods: the timeline's presets and the PeriodO definitions behind them, searched, read one by one and
  * written by the seed and the imports.
  */
trait PeriodService:
  def get(id: com.histmersion.domain.catalogue.periods.PeriodService.GetId): Option[HistoricalPeriod]

  /** A period's parts, by start then label. */
  def children(id: com.histmersion.domain.catalogue.periods.PeriodService.ChildrenId): List[HistoricalPeriod]

  /** The periods a search selects, by start then label. */
  def search(q: PeriodSearch): List[HistoricalPeriod]

  def count(): com.histmersion.domain.catalogue.periods.PeriodService.CountResult

  /** How many the timeline offers as presets (those filed under a region heading). */
  def presetCount(): com.histmersion.domain.catalogue.periods.PeriodService.PresetCountResult

  /** Where each of `ids` sits now: every stored id → its parent (`None` = top level); ids with no row are absent. */
  def placement(ids: List[com.histmersion.domain.catalogue.periods.PeriodService.PlacementIds]): Map[com.histmersion.domain.catalogue.periods.PeriodService.PlacementResultPart1, Option[com.histmersion.domain.catalogue.periods.PeriodService.PlacementResultPart2]]

  /** Upsert in the order given, parents first, in one transaction; a row with no preset keeps a stored one. */
  def write(rows: List[HistoricalPeriod], dataset: Dataset): com.histmersion.domain.catalogue.periods.PeriodService.WriteResult

  /** Take every preset not among `ids` off the menu (the rows stay); how many lost their heading. */
  def retainPresets(ids: List[com.histmersion.domain.catalogue.periods.PeriodService.RetainPresetsIds]): com.histmersion.domain.catalogue.periods.PeriodService.RetainPresetsResult

  /** Delete the seed's rows not among `ids` (a collection's own are kept; their parts lose their parent). */
  def deleteSeedExcept(ids: List[com.histmersion.domain.catalogue.periods.PeriodService.DeleteSeedExceptIds]): com.histmersion.domain.catalogue.periods.PeriodService.DeleteSeedExceptResult

  /** The version of the bundled seed `name` last applied here. */
  def seedVersion(name: com.histmersion.domain.catalogue.periods.PeriodService.SeedVersionName): Option[com.histmersion.domain.catalogue.periods.PeriodService.SeedVersionResult]
  def setSeedVersion(name: com.histmersion.domain.catalogue.periods.PeriodService.SetSeedVersionName, version: com.histmersion.domain.catalogue.periods.PeriodService.SetSeedVersionVersion): Unit

object PeriodService:
  type GetId = GetId.T
  object GetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChildrenId = ChildrenId.T
  object ChildrenId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountResult = CountResult.T
  object CountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PresetCountResult = PresetCountResult.T
  object PresetCountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacementResultPart1 = PlacementResultPart1.T
  object PlacementResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacementResultPart2 = PlacementResultPart2.T
  object PlacementResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlacementIds = PlacementIds.T
  object PlacementIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteResult = WriteResult.T
  object WriteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetainPresetsResult = RetainPresetsResult.T
  object RetainPresetsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RetainPresetsIds = RetainPresetsIds.T
  object RetainPresetsIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteSeedExceptResult = DeleteSeedExceptResult.T
  object DeleteSeedExceptResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteSeedExceptIds = DeleteSeedExceptIds.T
  object DeleteSeedExceptIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeedVersionResult = SeedVersionResult.T
  object SeedVersionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeedVersionName = SeedVersionName.T
  object SeedVersionName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetSeedVersionName = SetSeedVersionName.T
  object SetSeedVersionName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetSeedVersionVersion = SetSeedVersionVersion.T
  object SetSeedVersionVersion extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): PeriodService = Implementation(db)

  final private class Implementation(db: Transactor) extends PeriodService:
    def get(id: com.histmersion.domain.catalogue.periods.PeriodService.Implementation.GetId): Option[HistoricalPeriod] = db.read(PeriodRepo.get(id))
    def children(id: com.histmersion.domain.catalogue.periods.PeriodService.Implementation.ChildrenId): List[HistoricalPeriod] = db.read(PeriodRepo.children(id)).toList
    def search(q: PeriodSearch): List[HistoricalPeriod] =
      db.read(PeriodRepo.search(q.text, q.region, q.from, q.to)).toList
    def count(): com.histmersion.domain.catalogue.periods.PeriodService.Implementation.CountResult = db.read(PeriodRepo.count)
    def presetCount(): com.histmersion.domain.catalogue.periods.PeriodService.Implementation.PresetCountResult = db.read(PeriodRepo.presetCount)
    def placement(ids: List[com.histmersion.domain.catalogue.periods.PeriodService.Implementation.PlacementIds]): Map[com.histmersion.domain.catalogue.periods.PeriodService.Implementation.PlacementResultPart1, Option[com.histmersion.domain.catalogue.periods.PeriodService.Implementation.PlacementResultPart2]] = db.read(PeriodRepo.placement(ids))
    def write(rows: List[HistoricalPeriod], dataset: Dataset): com.histmersion.domain.catalogue.periods.PeriodService.Implementation.WriteResult = db.transact(PeriodRepo.upsert(rows, dataset))
    def retainPresets(ids: List[com.histmersion.domain.catalogue.periods.PeriodService.Implementation.RetainPresetsIds]): com.histmersion.domain.catalogue.periods.PeriodService.Implementation.RetainPresetsResult = db.transact(PeriodRepo.retainPresets(ids))
    def deleteSeedExcept(ids: List[com.histmersion.domain.catalogue.periods.PeriodService.Implementation.DeleteSeedExceptIds]): com.histmersion.domain.catalogue.periods.PeriodService.Implementation.DeleteSeedExceptResult = db.transact(PeriodRepo.deleteSeedExcept(ids))
    def seedVersion(name: com.histmersion.domain.catalogue.periods.PeriodService.Implementation.SeedVersionName): Option[com.histmersion.domain.catalogue.periods.PeriodService.Implementation.SeedVersionResult] = db.read(PeriodRepo.seedVersion(name))
    def setSeedVersion(name: com.histmersion.domain.catalogue.periods.PeriodService.Implementation.SetSeedVersionName, version: com.histmersion.domain.catalogue.periods.PeriodService.Implementation.SetSeedVersionVersion): Unit = db.transact(PeriodRepo.setSeedVersion(name, version))

  object Implementation:
    type GetId = GetId.T
    object GetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ChildrenId = ChildrenId.T
    object ChildrenId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountResult = CountResult.T
    object CountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PresetCountResult = PresetCountResult.T
    object PresetCountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlacementResultPart1 = PlacementResultPart1.T
    object PlacementResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlacementResultPart2 = PlacementResultPart2.T
    object PlacementResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlacementIds = PlacementIds.T
    object PlacementIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WriteResult = WriteResult.T
    object WriteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RetainPresetsResult = RetainPresetsResult.T
    object RetainPresetsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RetainPresetsIds = RetainPresetsIds.T
    object RetainPresetsIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteSeedExceptResult = DeleteSeedExceptResult.T
    object DeleteSeedExceptResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteSeedExceptIds = DeleteSeedExceptIds.T
    object DeleteSeedExceptIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SeedVersionResult = SeedVersionResult.T
    object SeedVersionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SeedVersionName = SeedVersionName.T
    object SeedVersionName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetSeedVersionName = SetSeedVersionName.T
    object SetSeedVersionName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetSeedVersionVersion = SetSeedVersionVersion.T
    object SetSeedVersionVersion extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The same rules over the shared in-memory state: a part whose parent is unknown is refused as the foreign key
    * refuses it, and the whole write with it.
    */
  final class InMemory(state: AtomicReference[CatalogueState]) extends PeriodService:
    private def all: Vector[HistoricalPeriod] = state.get.periods.map(_.period)
    private def ordered(ps: Vector[HistoricalPeriod]): List[HistoricalPeriod] =
      ps.sortBy(p => (p.period.start, p.label)).toList

    def get(id: com.histmersion.domain.catalogue.periods.PeriodService.InMemory.GetId): Option[HistoricalPeriod] = all.find(_.id == id)
    def children(id: com.histmersion.domain.catalogue.periods.PeriodService.InMemory.ChildrenId): List[HistoricalPeriod] = ordered(all.filter(_.parentId.contains(id)))

    def search(q: PeriodSearch): List[HistoricalPeriod] =
      def lower(s: com.histmersion.domain.catalogue.periods.PeriodService.InMemory.SearchLowerS) = s.toLowerCase(Locale.ROOT)
      val text = q.text.map(_.trim).filter(_.nonEmpty).map(lower)
      val region = q.region.map(_.trim).filter(_.nonEmpty).map(lower)
      ordered(all.filter { p =>
        text.forall(t => lower(p.label).contains(t)) &&
        region.forall(r => p.region.map(lower).contains(r)) &&
        q.from.forall(_ <= p.period.end) && q.to.forall(_ >= p.period.start)
      })

    def count(): com.histmersion.domain.catalogue.periods.PeriodService.InMemory.CountResult = all.size
    def presetCount(): com.histmersion.domain.catalogue.periods.PeriodService.InMemory.PresetCountResult = all.count(_.preset.isDefined)

    def placement(ids: List[com.histmersion.domain.catalogue.periods.PeriodService.InMemory.PlacementIds]): Map[com.histmersion.domain.catalogue.periods.PeriodService.InMemory.PlacementResultPart1, Option[com.histmersion.domain.catalogue.periods.PeriodService.InMemory.PlacementResultPart2]] =
      all.filter(p => ids.contains(p.id)).map(p => p.id -> p.parentId).toMap

    def write(rows: List[HistoricalPeriod], dataset: Dataset): com.histmersion.domain.catalogue.periods.PeriodService.InMemory.WriteResult =
      @tailrec def upsert(rest: List[HistoricalPeriod], held: Vector[StoredPeriod]): Vector[StoredPeriod] =
        rest match
          case Nil       => held
          case p :: tail =>
            if p.parentId.exists(parent => !held.exists(_.period.id == parent)) then
              throw IllegalArgumentException(s"period ${p.id}: no parent ${p.parentId.getOrElse("")}")
            val i = held.indexWhere(_.period.id == p.id)
            if i < 0 then upsert(tail, held :+ StoredPeriod(p, dataset))
            else
              val kept = p.modify(_.preset).using(_.orElse(held(i).period.preset))
              upsert(tail, held.updated(i, StoredPeriod(kept, dataset)))
      if rows.isEmpty then 0
      else
        val _ = state.updateAndGet(_.modify(_.periods).using(upsert(rows, _)))
        rows.size

    def retainPresets(ids: List[com.histmersion.domain.catalogue.periods.PeriodService.InMemory.RetainPresetsIds]): com.histmersion.domain.catalogue.periods.PeriodService.InMemory.RetainPresetsResult =
      val lost = all.count(p => p.preset.isDefined && !ids.contains(p.id))
      val _ = state.updateAndGet(
        _.modify(_.periods).using(
          _.map(s => if ids.contains(s.period.id) then s else s.modify(_.period.preset).setTo(None))
        )
      )
      lost

    def deleteSeedExcept(ids: List[com.histmersion.domain.catalogue.periods.PeriodService.InMemory.DeleteSeedExceptIds]): com.histmersion.domain.catalogue.periods.PeriodService.InMemory.DeleteSeedExceptResult =
      val gone = state.get.periods.filter(s => s.dataset == Dataset.Seed && !ids.contains(s.period.id))
      val goneIds = gone.map(_.period.id).toSet
      val _ = state.updateAndGet(
        _.modify(_.periods).using(
          _.filterNot(s => goneIds(s.period.id))
            .map(s => if s.period.parentId.exists(goneIds) then s.modify(_.period.parentId).setTo(None) else s)
        )
      )
      gone.size

    def seedVersion(name: com.histmersion.domain.catalogue.periods.PeriodService.InMemory.SeedVersionName): Option[com.histmersion.domain.catalogue.periods.PeriodService.InMemory.SeedVersionResult] = state.get.seedVersions.get(name)
    def setSeedVersion(name: com.histmersion.domain.catalogue.periods.PeriodService.InMemory.SetSeedVersionName, version: com.histmersion.domain.catalogue.periods.PeriodService.InMemory.SetSeedVersionVersion): Unit =
      val _ = state.updateAndGet(_.modify(_.seedVersions).using(_.updated(name, version)))

  object InMemory:
    type SearchLowerS = SearchLowerS.T
    object SearchLowerS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type GetId = GetId.T
    object GetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ChildrenId = ChildrenId.T
    object ChildrenId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountResult = CountResult.T
    object CountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PresetCountResult = PresetCountResult.T
    object PresetCountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlacementResultPart1 = PlacementResultPart1.T
    object PlacementResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlacementResultPart2 = PlacementResultPart2.T
    object PlacementResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlacementIds = PlacementIds.T
    object PlacementIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WriteResult = WriteResult.T
    object WriteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RetainPresetsResult = RetainPresetsResult.T
    object RetainPresetsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RetainPresetsIds = RetainPresetsIds.T
    object RetainPresetsIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteSeedExceptResult = DeleteSeedExceptResult.T
    object DeleteSeedExceptResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteSeedExceptIds = DeleteSeedExceptIds.T
    object DeleteSeedExceptIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SeedVersionResult = SeedVersionResult.T
    object SeedVersionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SeedVersionName = SeedVersionName.T
    object SeedVersionName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetSeedVersionName = SetSeedVersionName.T
    object SetSeedVersionName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetSeedVersionVersion = SetSeedVersionVersion.T
    object SetSeedVersionVersion extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
