package com.histmersion.api

import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import com.histmersion.values.WireEnumValues

/** Which importer a source is read by. `Tgn` is the Getty Thesaurus of Geographic Names (#1403), a pinned extract like
  * GeoNames and Wikidata.
  */
enum GazetteerKind:
  case Geonames, Wikidata, Lpf, Tgn

  def wire: String = toString.toLowerCase(java.util.Locale.ROOT)

object GazetteerKind:
  def parse(s: String): Option[GazetteerKind] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

  /** The kind of a stored source key: `geonames`, `wikidata`, `lpf:<dataset>`. */
  def ofKey(key: String): Option[GazetteerKind] = parse(key.takeWhile(_ != ':'))

  given WireEnumValues[GazetteerKind]:
    def all: Array[GazetteerKind] = GazetteerKind.values
    def wire(e: GazetteerKind): String = e.wire
    def fromWire(s: String): Option[GazetteerKind] = parse(s)
  given Encoder[GazetteerKind] = WireEnum.encoder
  given Decoder[GazetteerKind] = WireEnum.decoder
  given Schema[GazetteerKind] = WireEnum.schema
