package com.histmersion.api
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

  def isItem: com.histmersion.api.ItemKind.IsItemResult = ItemKind.Items.contains(this)

object ItemKind:
  type IsItemResult = IsItemResult.T
  object IsItemResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromWireS = FromWireS.T
  object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Items: Set[ItemKind] = Set(Source, Article, Event, Place, Picture, Goal)

  val Bookmarkable: Set[ItemKind] = values.toSet

  val Notable: Set[ItemKind] = Items + Point

  val Readable: Set[ItemKind] = Set(Source, Article)

  def parse(s: com.histmersion.api.ItemKind.ParseS): Option[ItemKind] =
    val t = Option(s).fold("")(_.trim.toLowerCase)
    values.find(k => k.wire == t || (t.endsWith("s") && k.wire == t.dropRight(1)))

  given WireEnumValues[ItemKind]:
    def all: Array[ItemKind] = ItemKind.values
    def wire(e: ItemKind): com.histmersion.api.ItemKind.WireResult = e.wire
    def fromWire(s: com.histmersion.api.ItemKind.FromWireS): Option[ItemKind] = values.find(_.wire == s.trim.toLowerCase)
  given Encoder[ItemKind] = WireEnum.encoder
  given Decoder[ItemKind] = WireEnum.decoder
  given Schema[ItemKind] = WireEnum.schema
