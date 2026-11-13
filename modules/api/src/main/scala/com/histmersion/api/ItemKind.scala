package com.histmersion
package api
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure

enum ItemKind(val wire: ItemKind.Wire, val label: ItemKind.Label) derives CanEqual:
  case Source extends ItemKind(ItemKind.Wire("source"), ItemKind.Label("Source"))
  case Article extends ItemKind(ItemKind.Wire("article"), ItemKind.Label("Article"))
  case Event extends ItemKind(ItemKind.Wire("event"), ItemKind.Label("Event"))
  case Place extends ItemKind(ItemKind.Wire("place"), ItemKind.Label("Place"))
  case Picture extends ItemKind(ItemKind.Wire("picture"), ItemKind.Label("Picture"))
  case Goal extends ItemKind(ItemKind.Wire("goal"), ItemKind.Label("Research goal"))
  case View extends ItemKind(ItemKind.Wire("view"), ItemKind.Label("Saved view"))
  case Point extends ItemKind(ItemKind.Wire("point"), ItemKind.Label("Place on the map"))
  case Period extends ItemKind(ItemKind.Wire("period"), ItemKind.Label("Period"))

  def isItem: Boolean = (ItemKind.Items.contains(this))

object ItemKind:
  type Word = Word.T
  object Word extends Newtype[String, Pure]
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]:
    type Pattern = Pattern.T
    object Pattern extends Newtype[String, Pure]

  val Items: Set[ItemKind] = Set(Source, Article, Event, Place, Picture, Goal)

  val Bookmarkable: Set[ItemKind] = values.toSet

  val Notable: Set[ItemKind] = Items + Point

  val Readable: Set[ItemKind] = Set(Source, Article)

  def parse(s: Wire): Option[ItemKind] =
    val t = Option(s).fold("")((_).value.trim.toLowerCase)
    values.find(k => k.wire == Wire(t) || (t.endsWith("s") && k.wire == Wire(t.dropRight(1))))

  given WireEnumValues[ItemKind]:
    def all: Array[ItemKind] = values
    def wire(e: ItemKind): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
    def fromWire(s: WireEnumValues.Wire): Option[ItemKind] = values.find(_.wire == Wire((s).value.trim.toLowerCase))
  given JsonValueCodec[ItemKind] = WireEnum.codec
  given Schema[ItemKind] = WireEnum.schema
