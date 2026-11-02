package com.histmersion.domain.catalogue.events

import com.histmersion.api.given
import com.histmersion.util.data.exchange.BundleImport
import com.histmersion.util.data.{DataOwner, TableName}
import com.histmersion.util.data.exchange.{BundleEntry, CarriedKind, ImportRank, ImportStage, RowsIn}

import hearth.kindlings.optics.syntax.*
import com.histmersion.util.data.exchange.Bundle

/** What a bundle carries of the events: the events (renamed first, as the other side renamed them, after the places and
  * periods they point at), their old ids, what the catalogues say about them and the corrections by hand.
  */
object EventsCarried:
  private val Catalogue = DataOwner("catalogue")

  private def rows(rank: Int)(write: BundleImport.Writer => Unit) = RowsIn(ImportStage.Rows, ImportRank(rank), write)

  val events: CarriedKind = CarriedKind.records[com.histmersion.api.exchange.Bundle.Event](
    Catalogue,
    Bundle.Kind.Event,
    TableName("events"),
    List(rows(41)(EventBundleRepo.eventRenames), rows(46)(EventBundleRepo.eventsIn))
  )(scope => EventBundleRepo.events(scope).toList)(_.updatedAt, (es, ls) => es.modify(_.events).setTo(ls))

  val eventAliases: CarriedKind = CarriedKind.owned[com.histmersion.api.exchange.Bundle.EventAlias](
    Catalogue,
    BundleEntry(com.histmersion.api.exchange.Bundle.Files.EventAliases),
    List(rows(47)(EventBundleRepo.eventAliasesIn))
  )(scope => EventBundleRepo.eventAliases(scope).toList)(
    a => Some(a.createdAt),
    (es, ls) => es.modify(_.eventAliases).setTo(ls)
  )

  val eventCatalogue: CarriedKind = CarriedKind.owned[com.histmersion.api.exchange.Bundle.EventCatalogue](
    Catalogue,
    BundleEntry(com.histmersion.api.exchange.Bundle.Files.EventCatalogue),
    List(rows(60)(EventBundleRepo.eventCatalogueIn))
  )(scope => EventBundleRepo.eventCatalogue(scope).toList)(
    c => Some(c.retrievedAt),
    (es, ls) => es.modify(_.eventCatalogue).setTo(ls)
  )

  val corrections: CarriedKind = CarriedKind.owned[com.histmersion.api.exchange.Bundle.Correction](
    Catalogue,
    BundleEntry(com.histmersion.api.exchange.Bundle.Files.EventCorrections),
    List(rows(63)(EventBundleRepo.correctionsIn))
  )(scope => EventBundleRepo.corrections(scope).toList)(
    c => Some(c.updatedAt),
    (es, ls) => es.modify(_.corrections).setTo(ls)
  )

  /** Every kind of the events. */
  val all: List[CarriedKind] = List(events, eventAliases, eventCatalogue, corrections)
