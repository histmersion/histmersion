package com.histmersion.api
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema

enum ItemKind(val wire: ItemKind.Wire, val label: ItemKind.Label):
  case Source extends ItemKind(_root_.com.histmersion.api.ItemKind.Wire("source"), _root_.com.histmersion.api.ItemKind.Label("Source"))
  case Article extends ItemKind(_root_.com.histmersion.api.ItemKind.Wire("article"), _root_.com.histmersion.api.ItemKind.Label("Article"))
  case Event extends ItemKind(_root_.com.histmersion.api.ItemKind.Wire("event"), _root_.com.histmersion.api.ItemKind.Label("Event"))
  case Place extends ItemKind(_root_.com.histmersion.api.ItemKind.Wire("place"), _root_.com.histmersion.api.ItemKind.Label("Place"))
  case Picture extends ItemKind(_root_.com.histmersion.api.ItemKind.Wire("picture"), _root_.com.histmersion.api.ItemKind.Label("Picture"))
  case Goal extends ItemKind(_root_.com.histmersion.api.ItemKind.Wire("goal"), _root_.com.histmersion.api.ItemKind.Label("Research goal"))
  case View extends ItemKind(_root_.com.histmersion.api.ItemKind.Wire("view"), _root_.com.histmersion.api.ItemKind.Label("Saved view"))
  case Point extends ItemKind(_root_.com.histmersion.api.ItemKind.Wire("point"), _root_.com.histmersion.api.ItemKind.Label("Place on the map"))
  case Period extends ItemKind(_root_.com.histmersion.api.ItemKind.Wire("period"), _root_.com.histmersion.api.ItemKind.Label("Period"))

  def isItem: Boolean = (ItemKind.Items.contains(this))

object ItemKind:
  object Label:
    type Pattern = Pattern.T
    object Pattern extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Word = Word.T
  object Word extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  val Items: Set[ItemKind] = Set(Source, Article, Event, Place, Picture, Goal)

  val Bookmarkable: Set[ItemKind] = values.toSet

  val Notable: Set[ItemKind] = Items + Point

  val Readable: Set[ItemKind] = Set(Source, Article)

  def parse(s: com.histmersion.api.ItemKind.Wire): Option[ItemKind] =
    val t = Option(s).fold("")((_).value.trim.toLowerCase)
    values.find(k => k.wire == _root_.com.histmersion.api.ItemKind.Wire(t) || (t.endsWith("s") && k.wire == _root_.com.histmersion.api.ItemKind.Wire(t.dropRight(1))))

  given WireEnumValues[ItemKind]:
    def all: Array[ItemKind] = ItemKind.values
    def wire(e: ItemKind): _root_.com.histmersion.api.WireEnumValues.Wire = _root_.com.histmersion.api.WireEnumValues.Wire((e.wire).value)
    def fromWire(s: _root_.com.histmersion.api.WireEnumValues.Wire): _root_.scala.Option[ItemKind] = values.find(_.wire == _root_.com.histmersion.api.ItemKind.Wire((s).value.trim.toLowerCase))
  given Encoder[ItemKind] = WireEnum.encoder
  given Decoder[ItemKind] = WireEnum.decoder
  given Schema[ItemKind] = WireEnum.schema
