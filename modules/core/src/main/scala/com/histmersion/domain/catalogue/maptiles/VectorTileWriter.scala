package com.histmersion.domain.catalogue.maptiles

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/** Writes Mapbox vector tiles (version 2, https://github.com/mapbox/vector-tile-spec) of point layers. Each key and
  * value is stored once per layer. Pure.
  */
object VectorTileWriter:

  /** A tile of the `layers` given (name → its points); an empty layer is left out. */
  def tile(layers: Seq[(String, Seq[TilePoint])]): Array[Byte] =
    val out = ByteArrayOutputStream()
    layers.filter(_._2.nonEmpty).foreach((name, points) => message(out, 3, layer(name, points)))
    out.toByteArray

  /** One layer's message. */
  def layer(name: String, points: Seq[TilePoint]): Array[Byte] =
    val keys = scala.collection.mutable.LinkedHashMap.empty[String, Int]
    val values = scala.collection.mutable.LinkedHashMap.empty[TileValue, Int]
    val out = ByteArrayOutputStream()
    varintField(out, 15, 2)
    message(out, 1, name.getBytes(StandardCharsets.UTF_8))
    points.foreach { p =>
      val tags = ByteArrayOutputStream()
      p.props.foreach { (k, v) =>
        varint(tags, keys.getOrElseUpdate(k, keys.size).toLong)
        varint(tags, values.getOrElseUpdate(v, values.size).toLong)
      }
      val geometry = ByteArrayOutputStream()
      // one MoveTo of one point, at its zig-zagged place
      varint(geometry, 9)
      varint(geometry, zigzag(p.x.toLong))
      varint(geometry, zigzag(p.y.toLong))
      val feature = ByteArrayOutputStream()
      message(feature, 2, tags.toByteArray)
      varintField(feature, 3, 1)
      message(feature, 4, geometry.toByteArray)
      message(out, 2, feature.toByteArray)
    }
    keys.keys.foreach(k => message(out, 3, k.getBytes(StandardCharsets.UTF_8)))
    values.keys.foreach(v => message(out, 4, value(v)))
    varintField(out, 5, TileCoord.Extent.toLong)
    out.toByteArray

  private def value(v: TileValue): Array[Byte] =
    val out = ByteArrayOutputStream()
    v match
      case TileValue.Text(s) => message(out, 1, s.getBytes(StandardCharsets.UTF_8))
      case TileValue.Real(d) =>
        varint(out, (3L << 3) | 1)
        val bits = java.lang.Double.doubleToLongBits(d)
        (0 until 8).foreach(i => out.write(((bits >>> (8 * i)) & 0xff).toInt))
      case TileValue.Whole(n) => varintField(out, 6, zigzag(n))
      case TileValue.Flag(b)  => varintField(out, 7, if b then 1L else 0L)
    out.toByteArray

  /** Protobuf's zig-zag encoding of a signed number. Pure. */
  def zigzag(n: Long): Long = (n << 1) ^ (n >> 63)

  private def message(out: ByteArrayOutputStream, number: Int, payload: Array[Byte]): Unit =
    varint(out, (number.toLong << 3) | 2)
    varint(out, payload.length.toLong)
    out.write(payload)

  private def varintField(out: ByteArrayOutputStream, number: Int, v: Long): Unit =
    varint(out, number.toLong << 3)
    varint(out, v)

  private def varint(out: ByteArrayOutputStream, v: Long): Unit =
    // `var`: the value's next seven bits
    var rest = v
    while (rest & ~0x7fL) != 0 do
      out.write(((rest & 0x7f) | 0x80).toInt)
      rest >>>= 7
    out.write(rest.toInt)
