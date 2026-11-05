package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class CritiqueRef(src: com.histmersion.api.library.Citekey, dst: com.histmersion.api.library.Citekey) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema:
  def wire: com.histmersion.api.research.CritiqueRef.WireResult = s"$src>$dst"

  def same(o: CritiqueRef): com.histmersion.api.research.CritiqueRef.SameResult = (src == o.src && dst == o.dst) || (src == o.dst && dst == o.src)

object CritiqueRef:


  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SameResult = SameResult.T
  object SameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeyResult = KeyResult.T
  object KeyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeyS = KeyS.T
  object KeyS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseResultPart1 = ParseResultPart1.T
  object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def key(s: com.histmersion.api.research.CritiqueRef.KeyS): com.histmersion.api.research.CritiqueRef.KeyResult =
    s.trim.stripPrefix("[[").stripSuffix("]]").trim.toLowerCase(java.util.Locale.ROOT)

  def parse(s: com.histmersion.api.research.CritiqueRef.ParseS): Either[com.histmersion.api.research.CritiqueRef.ParseResultPart1, CritiqueRef] =
    val t = Option(s).getOrElse("").replace("->", ">").replace("→", ">")
    t.split(">", -1).toList match
      case a :: b :: Nil if key(a).nonEmpty && key(b).nonEmpty && key(a) != key(b) => Right(CritiqueRef(key(a), key(b)))
      case _                                                                       =>
        Left(s"'$s' is not a pair — write it as <src>><dst>, the critic first (e.g. smith1987_dying>frazer1922_golden)")
