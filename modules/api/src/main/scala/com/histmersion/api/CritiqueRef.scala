package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class CritiqueRef(src: String, dst: String) derives KindlingsCodecAsObject, KindlingsSchema:
  def wire: String = s"$src>$dst"

  def same(o: CritiqueRef): Boolean = (src == o.src && dst == o.dst) || (src == o.dst && dst == o.src)

object CritiqueRef:
  given Schema[CritiqueRef] = summon[KindlingsSchema[CritiqueRef]].schema

  private def key(s: String): String =
    s.trim.stripPrefix("[[").stripSuffix("]]").trim.toLowerCase(java.util.Locale.ROOT)

  def parse(s: String): Either[String, CritiqueRef] =
    val t = Option(s).getOrElse("").replace("->", ">").replace("→", ">")
    t.split(">", -1).toList match
      case a :: b :: Nil if key(a).nonEmpty && key(b).nonEmpty && key(a) != key(b) => Right(CritiqueRef(key(a), key(b)))
      case _                                                                       =>
        Left(s"'$s' is not a pair — write it as <src>><dst>, the critic first (e.g. smith1987_dying>frazer1922_golden)")
