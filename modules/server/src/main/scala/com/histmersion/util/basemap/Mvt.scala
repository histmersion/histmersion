package com.histmersion
package util.basemap
import com.histmersion.util.problem.{External, Problem}

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** Just enough of the Mapbox Vector Tile protobuf (https://github.com/mapbox/vector-tile-spec) to thin a tile WITHOUT
  * decoding its geometry: keep the named layers, and inside them drop the features whose `kind` is listed. Everything
  * kept is copied byte for byte, so the result is as valid as the input. Pure.
  *
  * This is what keeps the terrain base map free of modern features: roads, buildings, places, boundaries, POIs and
  * transit never reach the disk ([[BasemapLayers]]).
  */
object Mvt:
  type GeomType = GeomType.T
  object GeomType extends Newtype[Int, Pure]
  type GeometryCommand = GeometryCommand.T
  object GeometryCommand extends Newtype[Int, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type LayerBytes = LayerBytes.T
  object LayerBytes extends Newtype[Array[Byte], Pure]
  type LayerName = LayerName.T
  object LayerName extends Newtype[String, Pure]
  type Others = Others.T
  object Others extends Newtype[Boolean, Pure]
  type PointX = PointX.T
  object PointX extends Newtype[Int, Pure]
  type PointY = PointY.T
  object PointY extends Newtype[Int, Pure]
  type PropKey = PropKey.T
  object PropKey extends Newtype[String, Pure]
  type PropText = PropText.T
  object PropText extends Newtype[String, Pure]
  type SignedNumber = SignedNumber.T
  object SignedNumber extends Newtype[Long, Pure]
  export PmTiles.PlainBytes as TileBytes
  type Varint = Varint.T
  object Varint extends Newtype[Long, Pure]

  /** A protobuf field: its number, wire type and raw bytes (the whole field, tag included), and for a length-delimited
    * field its payload's range in `src`.
    */
  final private case class Field(number: Int, wireType: Int, start: Int, end: Int, payloadStart: Int)

  final private class Reader(val b: Array[Byte], from: Int, until: Int):
    var pos: Int = from // the next unread byte

    def hasNext: Boolean = pos < until

    def varint(): Long =
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

  private def fields(b: Array[Byte], from: Int, until: Int): Vector[Field] =
    val r = Reader(b, from, until)
    val out = Vector.newBuilder[Field]
    while r.hasNext do out += r.field()
    out.result()

  private def string(b: Array[Byte], f: Field): String =
    String(b, f.payloadStart, f.end - f.payloadStart, StandardCharsets.UTF_8)

  /** The names of a tile's layers, in order. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def layerNames(tile: TileBytes): Either[Problem, List[LayerName]] =
    val bytes = tile.value
    attempt(
      fields(bytes, 0, bytes.length)
        .filter(f => f.number == 3 && f.wireType == 2)
        .map(l => LayerName(name(bytes, l)))
        .toList
    )

  private def name(b: Array[Byte], layer: Field): String =
    fields(b, layer.payloadStart, layer.end).find(f => f.number == 1 && f.wireType == 2).map(string(b, _)).getOrElse("")

  private def attempt[A](a: => A): Either[Problem, A] =
    try Right(a)
    catch
      case e: IllegalArgumentException =>
        Left(
          Problem.Io.Damage.Unreadable(External.Reported.of(e)).in(Problem.Io.Format.Mvt, Problem.Where("the tile"))
        )

  /** Which features of one layer stay: those whose string `kind` is in `keep` (every kind when it is empty) and not in
    * `drop`. A feature without a `kind` stays unless `keep` is given. `props`: only these property keys stay (every one
    * when `None`) — a page's cut drops the names in a hundred languages it does not show.
    */
  final case class Rule(
      keep: Set[Rule.Keep] = Set.empty,
      drop: Set[Rule.Drop] = Set.empty,
      none: Rule.Empty = Rule.Empty(false),
      geoms: Set[Rule.Geoms] = Set.empty,
      props: Option[Set[Rule.Props]] = scala.None
  ):
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private val keepKinds: Set[String] = keep.map(_.value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private val dropKinds: Set[String] = drop.map(_.value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def keeps(kind: Option[Kind]): Boolean = kind match
      case scala.None => keep.isEmpty
      case Some(k)    => (keep.isEmpty || keepKinds(k.value)) && !dropKinds(k.value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def all: Boolean = keep.isEmpty && drop.isEmpty && !none.value && geoms.isEmpty && props.isEmpty

  object Rule:

    type Keep = Keep.T
    object Keep extends Newtype[String, Pure]
    type Drop = Drop.T
    object Drop extends Newtype[String, Pure]
    type Empty = Empty.T
    object Empty extends Newtype[Boolean, Pure]
    type Geoms = Geoms.T
    object Geoms extends Newtype[Int, Pure]
    type Props = Props.T
    object Props extends Newtype[String, Pure]

  /** The tile with the layers `rules` names thinned by their [[Rule]] (a `none` rule drops the layer) and the others
    * kept when `others`, dropped otherwise. A layer left with no feature is dropped too.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def filter(tile: TileBytes, rules: Map[LayerName, Rule], others: Others = Others(false)): Either[Problem, TileBytes] =
    val bytes = tile.value
    attempt {
      val out = ByteArrayOutputStream(bytes.length)
      fields(bytes, 0, bytes.length).foreach { f =>
        if f.number == 3 && f.wireType == 2 then
          rules
            .get(LayerName(name(bytes, f)))
            .orElse(Option.when(others.value)(Rule()))
            .filterNot(_.none.value)
            .foreach { rule =>
              if rule.all then out.write(bytes, f.start, f.end - f.start)
              else thinLayer(bytes, f, rule).foreach(l => writeField(out, 3, l))
            }
      }
      TileBytes(out.toByteArray)
    }

  /** One layer with only the features `rule` keeps; `None` when none is left. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def thinLayer(b: Array[Byte], layer: Field, rule: Rule): Option[Array[Byte]] =
    val fs = fields(b, layer.payloadStart, layer.end)
    val keys = fs.filter(f => f.number == 3 && f.wireType == 2).map(string(b, _))
    val values = fs.filter(f => f.number == 4 && f.wireType == 2).map(v => stringValue(b, v))
    val kindKey = keys.indexOf("kind")
    val geomKinds = rule.geoms.map[Int](_.value)
    def kind(feature: Field): Option[Kind] =
      if kindKey < 0 then scala.None
      else
        tags(b, feature)
          .grouped(2)
          .collectFirst { case Vector(k, v) if k == kindKey => values.lift(v).flatten }
          .flatten
          .map(Kind(_))
    def geomType(feature: Field): Int =
      fields(b, feature.payloadStart, feature.end)
        .find(f => f.number == 3 && f.wireType == 0)
        .map { f =>
          val r = Reader(b, f.start, f.end); r.varint(); r.varint().toInt
        }
        .getOrElse(0)
    def dropped(feature: Field): Boolean =
      !rule.keeps(kind(feature)) || (geomKinds.nonEmpty && !geomKinds(geomType(feature)))
    val features = fs.filter(f => f.number == 2 && f.wireType == 2)
    val gone = features.filter(dropped).map(_.start).toSet
    if gone.size == features.size then scala.None
    else
      rule.props match
        case Some(wanted)               => Some(withProps(b, fs, keys, gone, wanted.map(_.value)))
        case scala.None if gone.isEmpty => Some(b.slice(layer.payloadStart, layer.end))
        case scala.None                 =>
          val out = ByteArrayOutputStream(layer.end - layer.payloadStart)
          fs.foreach(f => if !gone(f.start) then out.write(b, f.start, f.end - f.start))
          Some(out.toByteArray)

  /** A layer's fields without the features `gone` and with only the property keys `wanted`: the key and value tables
    * rebuilt from what the features left still use, every feature's tags renumbered, its id, type and geometry copied
    * byte for byte.
    */
  private def withProps(
      b: Array[Byte],
      fs: Vector[Field],
      keys: Vector[String],
      gone: Set[Int],
      wanted: Set[String]
  ): Array[Byte] =
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
  private def tags(b: Array[Byte], feature: Field): Vector[Int] =
    fields(b, feature.payloadStart, feature.end).find(f => f.number == 2 && f.wireType == 2) match
      case scala.None => Vector.empty
      case Some(t)    =>
        val r = Reader(b, t.payloadStart, t.end)
        val out = Vector.newBuilder[Int]
        while r.hasNext do out += r.varint().toInt
        out.result()

  /** A `Value`'s string, when it is one (field 1). */
  private def stringValue(b: Array[Byte], v: Field): Option[String] =
    fields(b, v.payloadStart, v.end).find(f => f.number == 1 && f.wireType == 2).map(string(b, _))

  private def writeField(out: ByteArrayOutputStream, number: Int, payload: Array[Byte]): Unit =
    writeVarint(out, (number.toLong << 3) | 2)
    writeVarint(out, payload.length.toLong)
    out.write(payload)

  private def writeVarint(out: ByteArrayOutputStream, v: Long): Unit =
    var x = v // seven bits at a time
    while (x & ~0x7fL) != 0 do
      out.write(((x & 0x7f) | 0x80).toInt)
      x >>>= 7
    out.write(x.toInt)

  // ── building tiles (the test fixtures; a test-only encoder would duplicate the varint code) ────────

  /** A layer of `features` (each: its geometry type 1–3, its command-encoded geometry, its string properties). Extent
    * 4096, version 2.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def layer(name: LayerName, features: List[(GeomType, Vector[GeometryCommand], Map[PropKey, PropText])]): LayerBytes =
    typedLayer(name, features.map((g, c, p) => (g, c, p.toList.map((k, v) => (k, v.value: String | Long)))))

  /** The same with text or whole-number properties (a number as `sint_value`, as the Protomaps builds store
    * `min_zoom`), in the order given.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def typedLayer(
      name: LayerName,
      features: List[(GeomType, Vector[GeometryCommand], List[(PropKey, String | Long)])]
  ): LayerBytes =
    val keys = features.flatMap(_._3.map(_._1.value)).distinct.toVector
    val values = features.flatMap(_._3.map(_._2)).distinct.toVector
    val out = ByteArrayOutputStream()
    writeVarint(out, (15L << 3) | 0); writeVarint(out, 2)
    writeField(out, 1, name.value.getBytes(StandardCharsets.UTF_8))
    features.foreach { (gt, geom, props) =>
      val f = ByteArrayOutputStream()
      val t = ByteArrayOutputStream()
      props.foreach { (k, v) => writeVarint(t, keys.indexOf(k.value).toLong); writeVarint(t, values.indexOf(v).toLong) }
      writeField(f, 2, t.toByteArray)
      writeVarint(f, (3L << 3) | 0); writeVarint(f, gt.value.toLong)
      val g = ByteArrayOutputStream()
      geom.foreach(c => writeVarint(g, c.value.toLong & 0xffffffffL))
      writeField(f, 4, g.toByteArray)
      writeField(out, 2, f.toByteArray)
    }
    keys.foreach(k => writeField(out, 3, k.getBytes(StandardCharsets.UTF_8)))
    values.foreach { v =>
      val vb = ByteArrayOutputStream()
      v match
        case s: String => writeField(vb, 1, s.getBytes(StandardCharsets.UTF_8))
        case n: Long   => writeVarint(vb, (6L << 3) | 0); writeVarint(vb, zig(n))
      writeField(out, 4, vb.toByteArray)
    }
    writeVarint(out, (5L << 3) | 0); writeVarint(out, 4096)
    LayerBytes(out.toByteArray)

  /** Protobuf's zig-zag encoding of a signed number (geometry deltas, `sint_value`). Pure. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def zigzag(n: SignedNumber): Varint = Varint(zig(n.value))

  private def zig(n: Long): Long = (n << 1) ^ (n >> 63)

  /** A point's geometry commands: MoveTo(1) and its zig-zagged position. Pure. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def pointGeometry(x: PointX, y: PointY): Vector[GeometryCommand] =
    Vector(GeometryCommand(9), GeometryCommand(zig(x.value.toLong).toInt), GeometryCommand(zig(y.value.toLong).toInt))

  /** A (multi)line's geometry commands: per part MoveTo its first point, LineTo the rest — positions as deltas from the
    * previous point, parts of fewer than two points skipped. Pure.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def lineGeometry(parts: Seq[Seq[(PointX, PointY)]]): Vector[GeometryCommand] =
    val out = Vector.newBuilder[Int]
    // `var`s: the cursor the deltas are taken from, carried across parts as the spec requires
    var cx = 0
    var cy = 0
    parts.filter(_.size >= 2).foreach { part =>
      val x0 = part.head._1.value
      val y0 = part.head._2.value
      out += 9; out += zig(x0 - cx).toInt; out += zig(y0 - cy).toInt
      cx = x0; cy = y0
      out += (2 | ((part.size - 1) << 3))
      part.tail.foreach { (px, py) =>
        val x = px.value
        val y = py.value
        out += zig(x - cx).toInt; out += zig(y - cy).toInt
        cx = x; cy = y
      }
    }
    out.result().map(GeometryCommand(_))

  /** A tile of encoded layers. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def tile(layers: List[LayerBytes]): TileBytes =
    val out = ByteArrayOutputStream()
    layers.foreach(l => writeField(out, 3, l.value))
    TileBytes(out.toByteArray)

  /** Every feature of every layer: its layer, geometry type (1 point, 2 line, 3 polygon) and properties as text
    * (numbers and booleans written out) — what the measuring tools and the tests read. Pure.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def features(tile: TileBytes): Either[Problem, List[(LayerName, GeomType, Map[PropKey, PropText])]] =
    val bytes = tile.value
    attempt {
      fields(bytes, 0, bytes.length).filter(f => f.number == 3 && f.wireType == 2).toList.flatMap { l =>
        val fs = fields(bytes, l.payloadStart, l.end)
        val keys = fs.filter(f => f.number == 3 && f.wireType == 2).map(string(bytes, _))
        val values = fs.filter(f => f.number == 4 && f.wireType == 2).map(v => anyValue(bytes, v))
        val ln = name(bytes, l)
        fs.filter(f => f.number == 2 && f.wireType == 2).toList.map { f =>
          val gt = fields(bytes, f.payloadStart, f.end)
            .find(x => x.number == 3 && x.wireType == 0)
            .map { x =>
              val r = Reader(bytes, x.start, x.end); r.varint(); r.varint().toInt
            }
            .getOrElse(0)
          val props = tags(bytes, f)
            .grouped(2)
            .collect { case Vector(k, v) =>
              PropKey(keys.lift(k).getOrElse("?")) -> PropText(values.lift(v).getOrElse(""))
            }
            .toMap
          (LayerName(ln), GeomType(gt), props)
        }
      }
    }

  /** A `Value` as text, whatever its type. */
  private def anyValue(b: Array[Byte], v: Field): String =
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
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def kinds(tile: TileBytes, layerName: LayerName): Either[Problem, List[Kind]] =
    val bytes = tile.value
    attempt {
      fields(bytes, 0, bytes.length)
        .filter(f => f.number == 3 && f.wireType == 2)
        .filter(l => name(bytes, l) == layerName.value)
        .flatMap { l =>
          val fs = fields(bytes, l.payloadStart, l.end)
          val keys = fs.filter(f => f.number == 3 && f.wireType == 2).map(string(bytes, _))
          val values = fs.filter(f => f.number == 4 && f.wireType == 2).map(v => stringValue(bytes, v))
          val k = keys.indexOf("kind")
          fs.filter(f => f.number == 2 && f.wireType == 2).flatMap { f =>
            tags(bytes, f).grouped(2).collectFirst { case Vector(kk, v) if kk == k => values.lift(v).flatten }.flatten
          }
        }
        .toList
        .map(Kind(_))
    }
