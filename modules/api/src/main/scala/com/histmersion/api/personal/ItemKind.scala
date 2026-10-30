package com.histmersion.api.personal

import com.histmersion.api.WireEnum
import com.histmersion.values.WireEnumValues
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema

/** What a bookmark, a note or a read mark is about: an item of the collection, a point on the map, a saved view of
  * Explore or a period.
  */
enum ItemKind(val wire: String, val label: String):
  case Source extends ItemKind("source", "Source")
  case Article extends ItemKind("article", "Article")
  case Event extends ItemKind("event", "Event")
  case Place extends ItemKind("place", "Place")
  case Picture extends ItemKind("picture", "Picture")
  case Goal extends ItemKind("goal", "Research goal")
  case View extends ItemKind("view", "Saved view")
  case Point extends ItemKind("point", "Place on the map")
  case Period extends ItemKind("period", "Period")

  /** An item of the collection, named by its key. */
  def isItem: Boolean = ItemKind.Items.contains(this)

object ItemKind:
  val Items: Set[ItemKind] = Set(Source, Article, Event, Place, Picture, Goal)

  /** What a bookmark may point at: everything. */
  val Bookmarkable: Set[ItemKind] = values.toSet

  /** What a note may be left on: an item or a point on the map. */
  val Notable: Set[ItemKind] = Items + Point

  /** What is marked read: what is read. */
  val Readable: Set[ItemKind] = Set(Source, Article)

  def parse(s: String): Option[ItemKind] =
    val t = Option(s).fold("")(_.trim.toLowerCase)
    values.find(k => k.wire == t || (t.endsWith("s") && k.wire == t.dropRight(1)))

  given WireEnumValues[ItemKind]:
    def all: Array[ItemKind] = ItemKind.values
    def wire(e: ItemKind): String = e.wire
    def fromWire(s: String): Option[ItemKind] = values.find(_.wire == s.trim.toLowerCase)
  given Encoder[ItemKind] = WireEnum.encoder
  given Decoder[ItemKind] = WireEnum.decoder
  given Schema[ItemKind] = WireEnum.schema
