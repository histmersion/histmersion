package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class CritiqueRef(src: com.histmersion.api.library.Citekey, dst: com.histmersion.api.library.Citekey) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema:
  def wire: com.histmersion.api.research.CritiqueRef.Wire = _root_.com.histmersion.api.research.CritiqueRef.Wire(s"$src>$dst")

  def same(o: CritiqueRef): Boolean = ((src == o.src && dst == o.dst) || (src == o.dst && dst == o.src))

object CritiqueRef:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]



  private def key(s: String): String =
    ((s).trim.stripPrefix("[[").stripSuffix("]]").trim.toLowerCase(java.util.Locale.ROOT))

  def parse(s: com.histmersion.api.research.CritiqueRef.Wire): Either[com.histmersion.api.research.CritiqueRef.Problem, CritiqueRef] =
    val t = Option((s).value).getOrElse("").replace("->", ">").replace("→", ">")
    t.split(">", -1).toList match
      case a :: b :: Nil if (key((a))).nonEmpty && (key((b))).nonEmpty && key((a)) != key((b)) => Right(CritiqueRef(com.histmersion.api.library.Citekey(key((a))), com.histmersion.api.library.Citekey(key((b)))))
      case _                                                                       =>
        Left(_root_.com.histmersion.api.research.CritiqueRef.Problem(s"'$s' is not a pair — write it as <src>><dst>, the critic first (e.g. smith1987_dying>frazer1922_golden)"))
