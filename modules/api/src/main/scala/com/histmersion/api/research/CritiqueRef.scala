package com.histmersion
package api.research
import com.histmersion.api.Problem
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure

final case class CritiqueRef(src: Citekey, dst: Citekey)
    derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema:
  def wire: CritiqueRef.Wire = CritiqueRef.Wire(s"$src>$dst")

  def same(o: CritiqueRef): Boolean = ((src == o.src && dst == o.dst) || (src == o.dst && dst == o.src))

object CritiqueRef:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  private def key(s: String): String =
    ((s).trim.stripPrefix("[[").stripSuffix("]]").trim.toLowerCase(java.util.Locale.ROOT))

  def parse(s: Wire): Either[Problem, CritiqueRef] =
    val t = Option((s).value).getOrElse("").replace("->", ">").replace("→", ">")
    t.split(">", -1).toList match
      case a :: b :: Nil if (key((a))).nonEmpty && (key((b))).nonEmpty && key((a)) != key((b)) =>
        Right(CritiqueRef(Citekey(key((a))), Citekey(key((b)))))
      case _ =>
        Left(
          Problem(
            s"'$s' is not a pair — write it as <src>><dst>, the critic first (e.g. smith1987_dying>frazer1922_golden)"
          )
        )
