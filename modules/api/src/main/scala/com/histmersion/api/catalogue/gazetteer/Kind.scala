package com.histmersion
package api.catalogue.gazetteer

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.catalogue.gazetteer.SourceInfo
import com.histmersion.api.catalogue.Gazetteer.DatasetName

enum Kind derives CanEqual:
  case Geonames, Wikidata, Lpf, Tgn

  def wire: Kind.Wire = Kind.Wire(toString.toLowerCase(java.util.Locale.ROOT))

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object Kind:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  def parse(s: Wire): Option[Kind] = values.find(_.wire == Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))

  def ofKey(key: SourceInfo.Key): Option[Kind] = parse(Wire((key).value.takeWhile(_ != ':')))

  given WireEnumValues[Kind]:
    def all: Array[Kind] = values
    def wire(e: Kind): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
    def fromWire(s: WireEnumValues.Wire): Option[Kind] = parse(Wire((s).value))
  given JsonValueCodec[Kind] = WireEnum.codec
  given Schema[Kind] = WireEnum.schema

  extension (kind: Kind)
    def sourceKey(dataset: Option[DatasetName] = None): SourceInfo.Key = kind match
      case Kind.Lpf => SourceInfo.Key(s"lpf:${dataset.getOrElse("")}")
      case k        => SourceInfo.Key(k.wire.value)
