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
  def get(id: com.histmersion.domain.catalogue.periods.BPeriodRow.Id): Option[HistoricalPeriod]

  /** A period's parts, by start then label. */
  def children(id: com.histmersion.domain.catalogue.periods.BPeriodRow.Id): List[HistoricalPeriod]

  /** The periods a search selects, by start then label. */
  def search(q: PeriodSearch): List[HistoricalPeriod]

  def count(): com.histmersion.RowCount

  /** How many the timeline offers as presets (those filed under a region heading). */
  def presetCount(): com.histmersion.RowCount

  /** Where each of `ids` sits now: every stored id → its parent (`None` = top level); ids with no row are absent. */
  def placement(ids: List[com.histmersion.domain.catalogue.periods.BPeriodRow.Id]): Map[com.histmersion.domain.catalogue.periods.BPeriodRow.Id, Option[com.histmersion.domain.catalogue.periods.BPeriodRow.ParentId]]

  /** Upsert in the order given, parents first, in one transaction; a row with no preset keeps a stored one. */
  def write(rows: List[HistoricalPeriod], dataset: Dataset): com.histmersion.RowCount

  /** Take every preset not among `ids` off the menu (the rows stay); how many lost their heading. */
  def retainPresets(ids: List[com.histmersion.domain.catalogue.periods.BPeriodRow.Id]): com.histmersion.RowCount

  /** Delete the seed's rows not among `ids` (a collection's own are kept; their parts lose their parent). */
  def deleteSeedExcept(ids: List[com.histmersion.domain.catalogue.periods.BPeriodRow.Id]): com.histmersion.RowCount

  /** The version of the bundled seed `name` last applied here. */
  def seedVersion(name: com.histmersion.domain.catalogue.periods.PeriodService.SeedName): Option[com.histmersion.domain.catalogue.periods.PeriodService.SeedVersion]
  def setSeedVersion(name: com.histmersion.domain.catalogue.periods.PeriodService.SeedName, version: com.histmersion.domain.catalogue.periods.PeriodService.SeedVersion): Unit

object PeriodService:
  type SeedName = SeedName.T
  object SeedName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SeedVersion = SeedVersion.T
  object SeedVersion extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): PeriodService = Implementation(db)

  final private class Implementation(db: Transactor) extends PeriodService:
    def get(id: com.histmersion.domain.catalogue.periods.BPeriodRow.Id): Option[HistoricalPeriod] = db.read(PeriodRepo.get(id))
    def children(id: com.histmersion.domain.catalogue.periods.BPeriodRow.Id): List[HistoricalPeriod] = db.read(PeriodRepo.children(id)).toList
    def search(q: PeriodSearch): List[HistoricalPeriod] =
      db.read(PeriodRepo.search(q.text, q.region, q.from, q.to)).toList
    def count(): com.histmersion.RowCount = db.read(PeriodRepo.count)
    def presetCount(): com.histmersion.RowCount = db.read(PeriodRepo.presetCount)
    def placement(ids: List[com.histmersion.domain.catalogue.periods.BPeriodRow.Id]): Map[com.histmersion.domain.catalogue.periods.BPeriodRow.Id, Option[com.histmersion.domain.catalogue.periods.BPeriodRow.ParentId]] = db.read(PeriodRepo.placement(ids))
    def write(rows: List[HistoricalPeriod], dataset: Dataset): com.histmersion.RowCount = db.transact(PeriodRepo.upsert(rows, dataset))
    def retainPresets(ids: List[com.histmersion.domain.catalogue.periods.BPeriodRow.Id]): com.histmersion.RowCount = db.transact(PeriodRepo.retainPresets(ids))
    def deleteSeedExcept(ids: List[com.histmersion.domain.catalogue.periods.BPeriodRow.Id]): com.histmersion.RowCount = db.transact(PeriodRepo.deleteSeedExcept(ids))
    def seedVersion(name: com.histmersion.domain.catalogue.periods.PeriodService.SeedName): Option[com.histmersion.domain.catalogue.periods.PeriodService.SeedVersion] = db.read(PeriodRepo.seedVersion(name))
    def setSeedVersion(name: com.histmersion.domain.catalogue.periods.PeriodService.SeedName, version: com.histmersion.domain.catalogue.periods.PeriodService.SeedVersion): Unit = db.transact(PeriodRepo.setSeedVersion(name, version))


  /** The same rules over the shared in-memory state: a part whose parent is unknown is refused as the foreign key
    * refuses it, and the whole write with it.
    */
  final class InMemory(state: AtomicReference[CatalogueState]) extends PeriodService:
    private def all: Vector[HistoricalPeriod] = state.get.periods.map(_.period)
    private def ordered(ps: Vector[HistoricalPeriod]): List[HistoricalPeriod] =
      ps.sortBy(p => (p.period.start, p.label)).toList

    def get(id: com.histmersion.domain.catalogue.periods.BPeriodRow.Id): Option[HistoricalPeriod] = all.find(_.id == id)
    def children(id: com.histmersion.domain.catalogue.periods.BPeriodRow.Id): List[HistoricalPeriod] = ordered(all.filter(_.parentId.contains(id)))

    def search(q: PeriodSearch): List[HistoricalPeriod] =
      def lower(s: com.histmersion.domain.catalogue.periods.PeriodSearch.Text) = s.toLowerCase(Locale.ROOT)
      val text = q.text.map(_.trim).filter(_.nonEmpty).map(lower)
      val region = q.region.map(_.trim).filter(_.nonEmpty).map(lower)
      ordered(all.filter { p =>
        text.forall(t => lower(p.label).contains(t)) &&
        region.forall(r => p.region.map(lower).contains(r)) &&
        q.from.forall(_ <= p.period.end) && q.to.forall(_ >= p.period.start)
      })

    def count(): com.histmersion.RowCount = all.size
    def presetCount(): com.histmersion.RowCount = all.count(_.preset.isDefined)

    def placement(ids: List[com.histmersion.domain.catalogue.periods.BPeriodRow.Id]): Map[com.histmersion.domain.catalogue.periods.BPeriodRow.Id, Option[com.histmersion.domain.catalogue.periods.BPeriodRow.ParentId]] =
      all.filter(p => ids.contains(p.id)).map(p => p.id -> p.parentId).toMap

    def write(rows: List[HistoricalPeriod], dataset: Dataset): com.histmersion.RowCount =
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
      if rows.isEmpty then com.histmersion.RowCount(0)
      else
        val _ = state.updateAndGet(_.modify(_.periods).using(upsert(rows, _)))
        rows.size

    def retainPresets(ids: List[com.histmersion.domain.catalogue.periods.BPeriodRow.Id]): com.histmersion.RowCount =
      val lost = all.count(p => p.preset.isDefined && !ids.contains(p.id))
      val _ = state.updateAndGet(
        _.modify(_.periods).using(
          _.map(s => if ids.contains(s.period.id) then s else s.modify(_.period.preset).setTo(None))
        )
      )
      lost

    def deleteSeedExcept(ids: List[com.histmersion.domain.catalogue.periods.BPeriodRow.Id]): com.histmersion.RowCount =
      val gone = state.get.periods.filter(s => s.dataset == Dataset.Seed && !ids.contains(s.period.id))
      val goneIds = gone.map(_.period.id).toSet
      val _ = state.updateAndGet(
        _.modify(_.periods).using(
          _.filterNot(s => goneIds(s.period.id))
            .map(s => if s.period.parentId.exists(goneIds) then s.modify(_.period.parentId).setTo(None) else s)
        )
      )
      gone.size

    def seedVersion(name: com.histmersion.domain.catalogue.periods.PeriodService.SeedName): Option[com.histmersion.domain.catalogue.periods.PeriodService.SeedVersion] = state.get.seedVersions.get(name)
    def setSeedVersion(name: com.histmersion.domain.catalogue.periods.PeriodService.SeedName, version: com.histmersion.domain.catalogue.periods.PeriodService.SeedVersion): Unit =
      val _ = state.updateAndGet(_.modify(_.seedVersions).using(_.updated(name, version)))


