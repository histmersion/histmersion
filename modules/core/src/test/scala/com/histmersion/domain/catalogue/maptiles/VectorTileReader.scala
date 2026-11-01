package com.histmersion.domain.catalogue.maptiles

import java.nio.charset.StandardCharsets

/** Reads back the point layers [[VectorTileWriter]] writes — the tests' view of a tile, as MapLibre decodes it: each
  * layer's name, extent and point features (place and properties).
  */
object VectorTileReader:

  final case class Feature(x: Int, y: Int, props: Map[String, TileValue]):
    def text(k: String): Option[String] = props.get(k).collect { case TileValue.Text(s) => s }
    def whole(k: String): Option[Long] = props.get(k).collect { case TileValue.Whole(n) => n }
    def real(k: String): Option[Double] =
      props.get(k).collect { case TileValue.Real(d) => d; case TileValue.Whole(n) => n.toDouble }

  final case class Layer(name: String, extent: Int, features: Vector[Feature])

  def read(tile: Array[Byte]): Vector[Layer] =
    fields(tile, 0, tile.length).collect { case (3, _, from, until) => layer(tile, from, until) }

  private def layer(b: Array[Byte], from: Int, until: Int): Layer =
    val fs = fields(b, from, until)
    val name = fs.collectFirst { case (1, _, s, e) => String(b, s, e - s, StandardCharsets.UTF_8) }.getOrElse("")
    val keys = fs.collect { case (3, _, s, e) => String(b, s, e - s, StandardCharsets.UTF_8) }
    val values = fs.collect { case (4, _, s, e) => value(b, s, e) }
    val extent = fs.collectFirst { case (5, v, _, _) => v.toInt }.getOrElse(4096)
    val features = fs.collect { case (2, _, s, e) =>
      val ff = fields(b, s, e)
      val tags = ff.collectFirst { case (2, _, ts, te) => packed(b, ts, te) }.getOrElse(Vector.empty)
      val geom = ff.collectFirst { case (4, _, gs, ge) => packed(b, gs, ge) }.getOrElse(Vector.empty)
      val props = tags.grouped(2).collect { case Vector(k, v) => keys(k.toInt) -> values(v.toInt) }.toMap
      Feature(unzig(geom(1)).toInt, unzig(geom(2)).toInt, props)
    }
    Layer(name, extent, features)

  private def value(b: Array[Byte], from: Int, until: Int): TileValue =
    fields(b, from, until).headOption match
      case Some((1, _, s, e))    => TileValue.Text(String(b, s, e - s, StandardCharsets.UTF_8))
      case Some((3, bits, _, _)) => TileValue.Real(java.lang.Double.longBitsToDouble(bits))
      case Some((6, v, _, _))    => TileValue.Whole(unzig(v))
      case Some((7, v, _, _))    => TileValue.Flag(v != 0)
      case other                 => throw IllegalArgumentException(s"unexpected value $other")

  private def unzig(n: Long): Long = (n >>> 1) ^ -(n & 1)

  /** The fields of a message: number, the varint (or 64-bit) value, and a length-delimited payload's range. */
  private def fields(b: Array[Byte], from: Int, until: Int): Vector[(Int, Long, Int, Int)] =
    // `var`: the read position
    var pos = from
    def varint(): Long =
      var r = 0L
      var shift = 0
      var more = true
      while more do
        val byte = b(pos) & 0xff
        pos += 1
        r |= (byte & 0x7fL) << shift
        shift += 7
        more = (byte & 0x80) != 0
      r
    val out = Vector.newBuilder[(Int, Long, Int, Int)]
    while pos < until do
      val key = varint()
      val n = (key >>> 3).toInt
      (key & 7).toInt match
        case 0 => out += ((n, varint(), 0, 0))
        case 1 =>
          val bits = (0 until 8).foldLeft(0L)((acc, i) => acc | ((b(pos + i) & 0xffL) << (8 * i)))
          pos += 8
          out += ((n, bits, 0, 0))
        case 2 =>
          val len = varint().toInt
          out += ((n, 0L, pos, pos + len))
          pos += len
        case w => throw IllegalArgumentException(s"wire type $w")
    out.result()

  private def packed(b: Array[Byte], from: Int, until: Int): Vector[Long] =
    // `var`: the read position
    var pos = from
    val out = Vector.newBuilder[Long]
    while pos < until do
      var r = 0L
      var shift = 0
      var more = true
      while more do
        val byte = b(pos) & 0xff
        pos += 1
        r |= (byte & 0x7fL) << shift
        shift += 7
        more = (byte & 0x80) != 0
      out += r
    out.result()
