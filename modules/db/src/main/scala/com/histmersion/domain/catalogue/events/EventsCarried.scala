package com.histmersion
package domain.catalogue.events

import com.histmersion.api.exchange.Bundle

import com.histmersion.api.given
import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.util.data.{DataOwner, TableName}
import com.histmersion.util.data.exchange.{BundleEntry, CarriedKind, ImportRank, ImportStage, RowsIn}

import hearth.kindlings.optics.syntax.*
import com.histmersion.lint.{LintRule, tolerated}

/** What a bundle carries of the events: the events (renamed first, as the other side renamed them, after the places and
  * periods they point at), their old ids, what the catalogues say about them and the corrections by hand.
  */
@tolerated(LintRule.FileSystem, "pending G23 (files only through util.storage)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object EventsCarried:

  private val Catalogue = DataOwner("catalogue")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def rows(rank: Int)(write: BundleImport.Writer => Unit) = RowsIn(ImportStage.Rows, ImportRank(rank), write)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val events: CarriedKind = CarriedKind.records[Bundle.Event](
    Catalogue,
    Bundle.Kind.Event,
    TableName("events"),
    List(rows(41)(EventBundleRepo.eventRenames), rows(46)(EventBundleRepo.eventsIn))
  )(scope => EventBundleRepo.events(scope).toList)(
    e => CarriedKind.RecordsClock(e.updatedAt.value),
    (es, ls) => es.modify(_.events).setTo(ls)
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val eventAliases: CarriedKind = CarriedKind.owned[Bundle.EventAlias](
    Catalogue,
    BundleEntry(Bundle.Files.EventAliases),
    List(rows(47)(EventBundleRepo.eventAliasesIn))
  )(scope => EventBundleRepo.eventAliases(scope).toList)(
    a => Some(CarriedKind.OwnedClock(a.createdAt.value)),
    (es, ls) => es.modify(_.eventAliases).setTo(ls)
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val eventCatalogue: CarriedKind = CarriedKind.owned[Bundle.EventCatalogue](
    Catalogue,
    BundleEntry(Bundle.Files.EventCatalogue),
    List(rows(60)(EventBundleRepo.eventCatalogueIn))
  )(scope => EventBundleRepo.eventCatalogue(scope).toList)(
    c => Some(CarriedKind.OwnedClock(c.retrievedAt.value)),
    (es, ls) => es.modify(_.eventCatalogue).setTo(ls)
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val corrections: CarriedKind = CarriedKind.owned[Bundle.Correction](
    Catalogue,
    BundleEntry(Bundle.Files.EventCorrections),
    List(rows(63)(EventBundleRepo.correctionsIn))
  )(scope => EventBundleRepo.corrections(scope).toList)(
    c => Some(CarriedKind.OwnedClock(c.updatedAt.value)),
    (es, ls) => es.modify(_.corrections).setTo(ls)
  )

  /** Every kind of the events. */
  val all: List[CarriedKind] = List(events, eventAliases, eventCatalogue, corrections)
