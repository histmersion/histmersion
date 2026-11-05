package com.histmersion.util.basemap

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/** Just enough of the Mapbox Vector Tile protobuf (https://github.com/mapbox/vector-tile-spec) to thin a tile WITHOUT
  * decoding its geometry: keep the named layers, and inside them drop the features whose `kind` is listed. Everything
  * kept is copied byte for byte, so the result is as valid as the input. Pure.
  *
  * This is what keeps the terrain base map free of modern features: roads, buildings, places, boundaries, POIs and
  * transit never reach the disk ([[BasemapLayers]]).
  */
object Mvt:

  /** A protobuf field: its number, wire type and raw bytes (the whole field, tag included), and for a length-delimited
    * field its payload's range in `src`.
    */
  type FieldsB = FieldsB.T
  object FieldsB extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StringB = StringB.T
  object StringB extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LayerNamesTile = LayerNamesTile.T
  object LayerNamesTile extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NameB = NameB.T
  object NameB extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FilterResultPart2 = FilterResultPart2.T
  object FilterResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FilterTile = FilterTile.T
  object FilterTile extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThinLayerResult = ThinLayerResult.T
  object ThinLayerResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThinLayerB = ThinLayerB.T
  object ThinLayerB extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WithPropsResult = WithPropsResult.T
  object WithPropsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WithPropsB = WithPropsB.T
  object WithPropsB extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TagsB = TagsB.T
  object TagsB extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StringValueB = StringValueB.T
  object StringValueB extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteFieldPayload = WriteFieldPayload.T
  object WriteFieldPayload extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LayerResult = LayerResult.T
  object LayerResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TypedLayerResult = TypedLayerResult.T
  object TypedLayerResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileResult = TileResult.T
  object TileResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TileLayers = TileLayers.T
  object TileLayers extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FeaturesTile = FeaturesTile.T
  object FeaturesTile extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnyValueB = AnyValueB.T
  object AnyValueB extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KindsTile = KindsTile.T
  object KindsTile extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

  type FieldsFrom = FieldsFrom.T
  object FieldsFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FieldsUntil = FieldsUntil.T
  object FieldsUntil extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StringResult = StringResult.T
  object StringResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LayerNamesResultPart1 = LayerNamesResultPart1.T
  object LayerNamesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LayerNamesResultPart2 = LayerNamesResultPart2.T
  object LayerNamesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NameResult = NameResult.T
  object NameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AttemptResultPart1 = AttemptResultPart1.T
  object AttemptResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FilterResultPart1 = FilterResultPart1.T
  object FilterResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FilterRulesPart1 = FilterRulesPart1.T
  object FilterRulesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FilterOthers = FilterOthers.T
  object FilterOthers extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WithPropsKeys = WithPropsKeys.T
  object WithPropsKeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WithPropsGone = WithPropsGone.T
  object WithPropsGone extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WithPropsWanted = WithPropsWanted.T
  object WithPropsWanted extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TagsResult = TagsResult.T
  object TagsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StringValueResult = StringValueResult.T
  object StringValueResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteFieldNumber = WriteFieldNumber.T
  object WriteFieldNumber extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteVarintV = WriteVarintV.T
  object WriteVarintV extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LayerName = LayerName.T
  object LayerName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LayerFeaturesPart1 = LayerFeaturesPart1.T
  object LayerFeaturesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LayerFeaturesPart2 = LayerFeaturesPart2.T
  object LayerFeaturesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LayerFeaturesPart3Part1 = LayerFeaturesPart3Part1.T
  object LayerFeaturesPart3Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LayerFeaturesPart3Part2 = LayerFeaturesPart3Part2.T
  object LayerFeaturesPart3Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TypedLayerName = TypedLayerName.T
  object TypedLayerName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TypedLayerFeaturesPart1 = TypedLayerFeaturesPart1.T
  object TypedLayerFeaturesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TypedLayerFeaturesPart2 = TypedLayerFeaturesPart2.T
  object TypedLayerFeaturesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TypedLayerFeaturesPart3Part1 = TypedLayerFeaturesPart3Part1.T
  object TypedLayerFeaturesPart3Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ZigzagResult = ZigzagResult.T
  object ZigzagResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ZigzagN = ZigzagN.T
  object ZigzagN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PointGeometryResult = PointGeometryResult.T
  object PointGeometryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PointGeometryX = PointGeometryX.T
  object PointGeometryX extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PointGeometryY = PointGeometryY.T
  object PointGeometryY extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LineGeometryResult = LineGeometryResult.T
  object LineGeometryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LineGeometryPartsPart1 = LineGeometryPartsPart1.T
  object LineGeometryPartsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LineGeometryPartsPart2 = LineGeometryPartsPart2.T
  object LineGeometryPartsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FeaturesResultPart1 = FeaturesResultPart1.T
  object FeaturesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FeaturesResultPart2Part1 = FeaturesResultPart2Part1.T
  object FeaturesResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FeaturesResultPart2Part2 = FeaturesResultPart2Part2.T
  object FeaturesResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FeaturesResultPart2Part3Part1 = FeaturesResultPart2Part3Part1.T
  object FeaturesResultPart2Part3Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FeaturesResultPart2Part3Part2 = FeaturesResultPart2Part3Part2.T
  object FeaturesResultPart2Part3Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnyValueResult = AnyValueResult.T
  object AnyValueResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KindsResultPart1 = KindsResultPart1.T
  object KindsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KindsResultPart2 = KindsResultPart2.T
  object KindsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KindsLayerName = KindsLayerName.T
  object KindsLayerName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final private case class Field(number: Field.Number, wireType: Field.WireType, start: Field.Start, end: Field.End, payloadStart: Field.PayloadStart)

  object Field:
    type Number = Number.T
    object Number extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WireType = WireType.T
    object WireType extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Start = Start.T
    object Start extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type End = End.T
    object End extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PayloadStart = PayloadStart.T
    object PayloadStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final private class Reader(val b: Array[Byte], from: Reader.From, until: Reader.Until):
    var pos: com.histmersion.util.basemap.Mvt.Reader.Pos = from // the next unread byte

    def hasNext: com.histmersion.util.basemap.Mvt.Reader.HasNextResult = pos < until

    def varint(): com.histmersion.util.basemap.Mvt.Reader.VarintResult =
      var result = 0L
      var shift = 0
      var more = true
      while more do
        if pos >= until then throw IllegalArgumentException("truncated varint")
        val byte = b(pos) & 0xff
        pos += 1
        result |= (byte & 0x7fL) << shift
        shift += 7
        more = (byte & 0x80) != 0
        if shift > 63 && more then throw IllegalArgumentException("varint too long")
      result

    def field(): Field =
      val start = pos
      val key = varint()
      val num = (key >>> 3).toInt
      val wt = (key & 7).toInt
      wt match
        case 0 => varint(); Field(num, wt, start, pos, pos)
        case 1 => pos += 8; check(); Field(num, wt, start, pos, pos)
        case 5 => pos += 4; check(); Field(num, wt, start, pos, pos)
        case 2 =>
          val len = varint()
          if len < 0 || len > until - pos then throw IllegalArgumentException("length past the end")
          val p = pos
          pos += len.toInt
          Field(num, wt, start, pos, p)
        case other => throw IllegalArgumentException(s"unsupported wire type $other")

    private def check(): Unit = if pos > until then throw IllegalArgumentException("field past the end")

  object Reader:
    type From = From.T
    object From extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Until = Until.T
    object Until extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Pos = Pos.T
    object Pos extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HasNextResult = HasNextResult.T
    object HasNextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type VarintResult = VarintResult.T
    object VarintResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def fields(b: com.histmersion.util.basemap.Mvt.FieldsB, from: com.histmersion.util.basemap.Mvt.FieldsFrom, until: com.histmersion.util.basemap.Mvt.FieldsUntil): Vector[Field] =
    val r = Reader(b, from, until)
    val out = Vector.newBuilder[Field]
    while r.hasNext do out += r.field()
    out.result()

  private def string(b: com.histmersion.util.basemap.Mvt.StringB, f: Field): com.histmersion.util.basemap.Mvt.StringResult =
    String(b, f.payloadStart, f.end - f.payloadStart, StandardCharsets.UTF_8)

  /** The names of a tile's layers, in order. */
  def layerNames(tile: com.histmersion.util.basemap.Mvt.LayerNamesTile): Either[com.histmersion.util.basemap.Mvt.LayerNamesResultPart1, List[com.histmersion.util.basemap.Mvt.LayerNamesResultPart2]] =
    attempt(fields(tile, 0, tile.length).filter(f => f.number == 3 && f.wireType == 2).map(l => name(tile, l)).toList)

  private def name(b: com.histmersion.util.basemap.Mvt.NameB, layer: Field): com.histmersion.util.basemap.Mvt.NameResult =
    fields(b, layer.payloadStart, layer.end).find(f => f.number == 1 && f.wireType == 2).map(string(b, _)).getOrElse("")

  private def attempt[A](a: => A): Either[com.histmersion.util.basemap.Mvt.AttemptResultPart1, A] =
    try Right(a)
    catch case e: IllegalArgumentException => Left(s"malformed vector tile: ${e.getMessage}")

  /** Which features of one layer stay: those whose string `kind` is in `keep` (every kind when it is empty) and not in
    * `drop`. A feature without a `kind` stays unless `keep` is given. `props`: only these property keys stay (every one
    * when `None`) — a page's cut drops the names in a hundred languages it does not show.
    */
  final case class Rule(
      keep: Set[Rule.Keep] = Set.empty,
      drop: Set[Rule.Drop] = Set.empty,
      none: Rule.None = false,
      geoms: Set[Rule.Geoms] = Set.empty,
      props: Option[Set[Rule.Props]] = None
  ):
    def keeps(kind: Option[com.histmersion.util.basemap.Mvt.Rule.KeepsKind]): com.histmersion.util.basemap.Mvt.Rule.KeepsResult = kind match
      case None    => keep.isEmpty
      case Some(k) => (keep.isEmpty || keep(k)) && !drop(k)
    def all: com.histmersion.util.basemap.Mvt.Rule.AllResult = keep.isEmpty && drop.isEmpty && !none && geoms.isEmpty && props.isEmpty

  object Rule:
    type KeepsResult = KeepsResult.T
    object KeepsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type KeepsKind = KeepsKind.T
    object KeepsKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AllResult = AllResult.T
    object AllResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Keep = Keep.T
    object Keep extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Drop = Drop.T
    object Drop extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type None = None.T
    object None extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Geoms = Geoms.T
    object Geoms extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Props = Props.T
    object Props extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The tile with the layers `rules` names thinned by their [[Rule]] (a `none` rule drops the layer) and the others
    * kept when `others`, dropped otherwise. A layer left with no feature is dropped too.
    */
  def filter(tile: com.histmersion.util.basemap.Mvt.FilterTile, rules: Map[com.histmersion.util.basemap.Mvt.FilterRulesPart1, Rule], others: com.histmersion.util.basemap.Mvt.FilterOthers = false): Either[com.histmersion.util.basemap.Mvt.FilterResultPart1, com.histmersion.util.basemap.Mvt.FilterResultPart2] =
    attempt {
      val out = ByteArrayOutputStream(tile.length)
      fields(tile, 0, tile.length).foreach { f =>
        if f.number == 3 && f.wireType == 2 then
          rules.get(name(tile, f)).orElse(Option.when(others)(Rule())).filterNot(_.none).foreach { rule =>
            if rule.all then out.write(tile, f.start, f.end - f.start)
            else thinLayer(tile, f, rule).foreach(l => writeField(out, 3, l))
          }
      }
      out.toByteArray
    }

  /** One layer with only the features `rule` keeps; `None` when none is left. */
  private def thinLayer(b: com.histmersion.util.basemap.Mvt.ThinLayerB, layer: Field, rule: Rule): Option[com.histmersion.util.basemap.Mvt.ThinLayerResult] =
    val fs = fields(b, layer.payloadStart, layer.end)
    val keys = fs.filter(f => f.number == 3 && f.wireType == 2).map(string(b, _))
    val values = fs.filter(f => f.number == 4 && f.wireType == 2).map(v => stringValue(b, v))
    val kindKey = keys.indexOf("kind")
    def kind(feature: Field): Option[String] =
      if kindKey < 0 then None
      else
        tags(b, feature).grouped(2).collectFirst { case Vector(k, v) if k == kindKey => values.lift(v).flatten }.flatten
    def geomType(feature: Field): Int =
      fields(b, feature.payloadStart, feature.end)
        .find(f => f.number == 3 && f.wireType == 0)
        .map { f =>
          val r = Reader(b, f.start, f.end); r.varint(); r.varint().toInt
        }
        .getOrElse(0)
    def dropped(feature: Field): Boolean =
      !rule.keeps(kind(feature)) || (rule.geoms.nonEmpty && !rule.geoms(geomType(feature)))
    val features = fs.filter(f => f.number == 2 && f.wireType == 2)
    val gone = features.filter(dropped).map(_.start).toSet
    if gone.size == features.size then None
    else
      rule.props match
        case Some(wanted)         => Some(withProps(b, fs, keys, gone, wanted))
        case None if gone.isEmpty => Some(b.slice(layer.payloadStart, layer.end))
        case None                 =>
          val out = ByteArrayOutputStream(layer.end - layer.payloadStart)
          fs.foreach(f => if !gone(f.start) then out.write(b, f.start, f.end - f.start))
          Some(out.toByteArray)

  /** A layer's fields without the features `gone` and with only the property keys `wanted`: the key and value tables
    * rebuilt from what the features left still use, every feature's tags renumbered, its id, type and geometry copied
    * byte for byte.
    */
  private def withProps(
      b: com.histmersion.util.basemap.Mvt.WithPropsB,
      fs: Vector[Field],
      keys: Vector[com.histmersion.util.basemap.Mvt.WithPropsKeys],
      gone: Set[com.histmersion.util.basemap.Mvt.WithPropsGone],
      wanted: Set[com.histmersion.util.basemap.Mvt.WithPropsWanted]
  ): com.histmersion.util.basemap.Mvt.WithPropsResult =
    val valueFields = fs.filter(f => f.number == 4 && f.wireType == 2)
    val keyIndex = keys.indices.filter(i => wanted(keys(i))).zipWithIndex.toMap // old key index → new
    val values = scala.collection.mutable.LinkedHashMap.empty[Int, Int] // old value index → new
    val out = ByteArrayOutputStream(fs.lastOption.fold(0)(_.end) - fs.headOption.fold(0)(_.start))
    fs.foreach { f =>
      if f.number == 2 && f.wireType == 2 then
        if !gone(f.start) then
          val feature = ByteArrayOutputStream(f.end - f.payloadStart)
          fields(b, f.payloadStart, f.end).foreach { ff =>
            if ff.number == 2 && ff.wireType == 2 then
              val packed = ByteArrayOutputStream()
              tags(b, f).grouped(2).foreach {
                case Vector(k, v) if keyIndex.contains(k) && v < valueFields.size =>
                  writeVarint(packed, keyIndex(k).toLong)
                  writeVarint(packed, values.getOrElseUpdate(v, values.size).toLong)
                case _ => ()
              }
              if packed.size() > 0 then writeField(feature, 2, packed.toByteArray)
            else feature.write(b, ff.start, ff.end - ff.start)
          }
          writeField(out, 2, feature.toByteArray)
      else if !((f.number == 3 || f.number == 4) && f.wireType == 2) then out.write(b, f.start, f.end - f.start)
    }
    keyIndex.toList.sortBy(_._2).foreach((old, _) => writeField(out, 3, keys(old).getBytes(StandardCharsets.UTF_8)))
    values.toList.sortBy(_._2).foreach { (old, _) =>
      val v = valueFields(old); out.write(b, v.start, v.end - v.start)
    }
    out.toByteArray

  /** A feature's packed `tags` (key index, value index, …). */
  private def tags(b: com.histmersion.util.basemap.Mvt.TagsB, feature: Field): Vector[com.histmersion.util.basemap.Mvt.TagsResult] =
    fields(b, feature.payloadStart, feature.end).find(f => f.number == 2 && f.wireType == 2) match
      case None    => Vector.empty
      case Some(t) =>
        val r = Reader(b, t.payloadStart, t.end)
        val out = Vector.newBuilder[Int]
        while r.hasNext do out += r.varint().toInt
        out.result()

  /** A `Value`'s string, when it is one (field 1). */
  private def stringValue(b: com.histmersion.util.basemap.Mvt.StringValueB, v: Field): Option[com.histmersion.util.basemap.Mvt.StringValueResult] =
    fields(b, v.payloadStart, v.end).find(f => f.number == 1 && f.wireType == 2).map(string(b, _))

  private def writeField(out: ByteArrayOutputStream, number: com.histmersion.util.basemap.Mvt.WriteFieldNumber, payload: com.histmersion.util.basemap.Mvt.WriteFieldPayload): Unit =
    writeVarint(out, (number.toLong << 3) | 2)
    writeVarint(out, payload.length.toLong)
    out.write(payload)

  private def writeVarint(out: ByteArrayOutputStream, v: com.histmersion.util.basemap.Mvt.WriteVarintV): Unit =
    var x = v // seven bits at a time
    while (x & ~0x7fL) != 0 do
      out.write(((x & 0x7f) | 0x80).toInt)
      x >>>= 7
    out.write(x.toInt)

  // ── building tiles (the test fixtures; a test-only encoder would duplicate the varint code) ────────

  /** A layer of `features` (each: its geometry type 1–3, its command-encoded geometry, its string properties). Extent
    * 4096, version 2.
    */
  def layer(name: com.histmersion.util.basemap.Mvt.LayerName, features: List[(com.histmersion.util.basemap.Mvt.LayerFeaturesPart1, Vector[com.histmersion.util.basemap.Mvt.LayerFeaturesPart2], Map[com.histmersion.util.basemap.Mvt.LayerFeaturesPart3Part1, com.histmersion.util.basemap.Mvt.LayerFeaturesPart3Part2])]): com.histmersion.util.basemap.Mvt.LayerResult =
    typedLayer(name, features.map((g, c, p) => (g, c, p.toList)))

  /** The same with text or whole-number properties (a number as `sint_value`, as the Protomaps builds store
    * `min_zoom`), in the order given.
    */
  def typedLayer(name: com.histmersion.util.basemap.Mvt.TypedLayerName, features: List[(com.histmersion.util.basemap.Mvt.TypedLayerFeaturesPart1, Vector[com.histmersion.util.basemap.Mvt.TypedLayerFeaturesPart2], List[(com.histmersion.util.basemap.Mvt.TypedLayerFeaturesPart3Part1, String | Long)])]): com.histmersion.util.basemap.Mvt.TypedLayerResult =
    val keys = features.flatMap(_._3.map(_._1)).distinct.toVector
    val values = features.flatMap(_._3.map(_._2)).distinct.toVector
    val out = ByteArrayOutputStream()
    writeVarint(out, (15L << 3) | 0); writeVarint(out, 2)
    writeField(out, 1, name.getBytes(StandardCharsets.UTF_8))
    features.foreach { (gt, geom, props) =>
      val f = ByteArrayOutputStream()
      val t = ByteArrayOutputStream()
      props.foreach { (k, v) => writeVarint(t, keys.indexOf(k).toLong); writeVarint(t, values.indexOf(v).toLong) }
      writeField(f, 2, t.toByteArray)
      writeVarint(f, (3L << 3) | 0); writeVarint(f, gt.toLong)
      val g = ByteArrayOutputStream()
      geom.foreach(c => writeVarint(g, c.toLong & 0xffffffffL))
      writeField(f, 4, g.toByteArray)
      writeField(out, 2, f.toByteArray)
    }
    keys.foreach(k => writeField(out, 3, k.getBytes(StandardCharsets.UTF_8)))
    values.foreach { v =>
      val vb = ByteArrayOutputStream()
      v match
        case s: String => writeField(vb, 1, s.getBytes(StandardCharsets.UTF_8))
        case n: Long   => writeVarint(vb, (6L << 3) | 0); writeVarint(vb, zigzag(n))
      writeField(out, 4, vb.toByteArray)
    }
    writeVarint(out, (5L << 3) | 0); writeVarint(out, 4096)
    out.toByteArray

  /** Protobuf's zig-zag encoding of a signed number (geometry deltas, `sint_value`). Pure. */
  def zigzag(n: com.histmersion.util.basemap.Mvt.ZigzagN): com.histmersion.util.basemap.Mvt.ZigzagResult = (n << 1) ^ (n >> 63)

  /** A point's geometry commands: MoveTo(1) and its zig-zagged position. Pure. */
  def pointGeometry(x: com.histmersion.util.basemap.Mvt.PointGeometryX, y: com.histmersion.util.basemap.Mvt.PointGeometryY): Vector[com.histmersion.util.basemap.Mvt.PointGeometryResult] = Vector(9, zigzag(x).toInt, zigzag(y).toInt)

  /** A (multi)line's geometry commands: per part MoveTo its first point, LineTo the rest — positions as deltas from the
    * previous point, parts of fewer than two points skipped. Pure.
    */
  def lineGeometry(parts: Seq[Seq[(com.histmersion.util.basemap.Mvt.LineGeometryPartsPart1, com.histmersion.util.basemap.Mvt.LineGeometryPartsPart2)]]): Vector[com.histmersion.util.basemap.Mvt.LineGeometryResult] =
    val out = Vector.newBuilder[Int]
    // `var`s: the cursor the deltas are taken from, carried across parts as the spec requires
    var cx = 0
    var cy = 0
    parts.filter(_.size >= 2).foreach { part =>
      val (x0, y0) = part.head
      out += 9; out += zigzag(x0 - cx).toInt; out += zigzag(y0 - cy).toInt
      cx = x0; cy = y0
      out += (2 | ((part.size - 1) << 3))
      part.tail.foreach { (x, y) =>
        out += zigzag(x - cx).toInt; out += zigzag(y - cy).toInt
        cx = x; cy = y
      }
    }
    out.result()

  /** A tile of encoded layers. */
  def tile(layers: List[com.histmersion.util.basemap.Mvt.TileLayers]): com.histmersion.util.basemap.Mvt.TileResult =
    val out = ByteArrayOutputStream()
    layers.foreach(writeField(out, 3, _))
    out.toByteArray

  /** Every feature of every layer: its layer, geometry type (1 point, 2 line, 3 polygon) and properties as text
    * (numbers and booleans written out) — what the measuring tools and the tests read. Pure.
    */
  def features(tile: com.histmersion.util.basemap.Mvt.FeaturesTile): Either[com.histmersion.util.basemap.Mvt.FeaturesResultPart1, List[(com.histmersion.util.basemap.Mvt.FeaturesResultPart2Part1, com.histmersion.util.basemap.Mvt.FeaturesResultPart2Part2, Map[com.histmersion.util.basemap.Mvt.FeaturesResultPart2Part3Part1, com.histmersion.util.basemap.Mvt.FeaturesResultPart2Part3Part2])]] =
    attempt {
      fields(tile, 0, tile.length).filter(f => f.number == 3 && f.wireType == 2).toList.flatMap { l =>
        val fs = fields(tile, l.payloadStart, l.end)
        val keys = fs.filter(f => f.number == 3 && f.wireType == 2).map(string(tile, _))
        val values = fs.filter(f => f.number == 4 && f.wireType == 2).map(v => anyValue(tile, v))
        val ln = name(tile, l)
        fs.filter(f => f.number == 2 && f.wireType == 2).toList.map { f =>
          val gt = fields(tile, f.payloadStart, f.end)
            .find(x => x.number == 3 && x.wireType == 0)
            .map { x =>
              val r = Reader(tile, x.start, x.end); r.varint(); r.varint().toInt
            }
            .getOrElse(0)
          val props = tags(tile, f)
            .grouped(2)
            .collect { case Vector(k, v) => keys.lift(k).getOrElse("?") -> values.lift(v).getOrElse("") }
            .toMap
          (ln, gt, props)
        }
      }
    }

  /** A `Value` as text, whatever its type. */
  private def anyValue(b: com.histmersion.util.basemap.Mvt.AnyValueB, v: Field): com.histmersion.util.basemap.Mvt.AnyValueResult =
    fields(b, v.payloadStart, v.end).headOption.fold("") { f =>
      f.number match
        case 1 => string(b, f)
        case 2 =>
          java.lang.Float
            .intBitsToFloat(
              java.nio.ByteBuffer.wrap(b, f.payloadStart - 4, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt
            )
            .toString
        case 3 =>
          java.lang.Double
            .longBitsToDouble(
              java.nio.ByteBuffer.wrap(b, f.payloadStart - 8, 8).order(java.nio.ByteOrder.LITTLE_ENDIAN).getLong
            )
            .toString
        case 4 | 5 => { val r = Reader(b, f.start, f.end); r.varint(); r.varint().toString }
        case 6 => { val r = Reader(b, f.start, f.end); r.varint(); val n = r.varint(); ((n >>> 1) ^ -(n & 1)).toString }
        case 7 => { val r = Reader(b, f.start, f.end); r.varint(); (r.varint() != 0).toString }
        case _ => ""
    }

  /** The kinds (string `kind` values) of one layer's features — what a test or a size report reads. */
  def kinds(tile: com.histmersion.util.basemap.Mvt.KindsTile, layerName: com.histmersion.util.basemap.Mvt.KindsLayerName): Either[com.histmersion.util.basemap.Mvt.KindsResultPart1, List[com.histmersion.util.basemap.Mvt.KindsResultPart2]] =
    attempt {
      fields(tile, 0, tile.length)
        .filter(f => f.number == 3 && f.wireType == 2)
        .filter(l => name(tile, l) == layerName)
        .flatMap { l =>
          val fs = fields(tile, l.payloadStart, l.end)
          val keys = fs.filter(f => f.number == 3 && f.wireType == 2).map(string(tile, _))
          val values = fs.filter(f => f.number == 4 && f.wireType == 2).map(v => stringValue(tile, v))
          val k = keys.indexOf("kind")
          fs.filter(f => f.number == 2 && f.wireType == 2).flatMap { f =>
            tags(tile, f).grouped(2).collectFirst { case Vector(kk, v) if kk == k => values.lift(v).flatten }.flatten
          }
        }
        .toList
    }
