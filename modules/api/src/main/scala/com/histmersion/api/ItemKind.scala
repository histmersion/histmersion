package com.histmersion.api
import com.histmersion.api.WireEnum
import com.histmersion.WireEnumValues
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema

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

  def isItem: Boolean = ItemKind.Items.contains(this)

object ItemKind:
  val Items: Set[ItemKind] = Set(Source, Article, Event, Place, Picture, Goal)

  val Bookmarkable: Set[ItemKind] = values.toSet

  val Notable: Set[ItemKind] = Items + Point

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
