package histdb.server.importers

import histdb.core.*

/** A repeatable importer that fetches external historical-geography data, transforms it to the
  * hist-db place schema, and returns `PlaceInput` values ready for `PlaceRepo.upsertPlaces`.
  *
  * Contract:
  *  - `transform` is a **pure function** from raw data (JSON string) to `PlaceInput` values,
  *    so it can be unit-tested against fixtures without a network or database.
  *  - `fetchUrl` is the canonical URL of the upstream dataset; the runner downloads it.
  *  - Running the same import twice is idempotent: place IDs are deterministic and upsert. */
trait GazetteerImporter:
  def name: String
  def description: String
  def fetchUrl: String
  def transform(rawJson: String): Either[String, List[PlaceInput]]

object GazetteerImporter:
  val all: List[GazetteerImporter] = List(CliopatriaImporter, AwmcImporter, ChronOntologyImporter)
  def byName(n: String): Option[GazetteerImporter] = all.find(_.name == n)
