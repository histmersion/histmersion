package operationinventoryfixture

trait Text[A]:
  def render(value: A): String
object Text:
  given textInt: Text[Int]:
    def render(value: Int): String = value.toString

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

type Identifier = Identifier.T
object Identifier extends RefinedType[String, Pure]
final case class Total(value: Int)
final class FileLike:
  def copy(): String = "copied"
object Examples:
  def direct(total: Total): Total = total.copy(value = 1)
  def unrelated(file: FileLike): String = file.copy()
  def accessor(total: Total): Int = total.value
  def raw(id: Identifier): String = id.value
  def accumulating(xs: List[Int]): Total = xs.foldLeft(Total(0))((acc, n) => acc.copy(value = acc.value + n))
  def seedOnly(xs: List[Int]): Total = xs.foldLeft(Total(0))((acc, _) => acc)
  def constructing(xs: List[Int]): Total = xs.foldLeft(Total(0))((acc, n) => Total(acc.value + n))
  def tuple(xs: List[Int]): (Int, Int) = xs.foldLeft((0, 0))((acc, n) => (acc._1 + n, acc._2 + 1))
  def primitive(xs: List[Int]): Int = xs.foldLeft(0)(_ + _)

object Schemas:
  given hearth.kindlings.circederivation.Configuration = hearth.kindlings.circederivation.Configuration.default.withDefaults
  final case class Wire(number: Int) derives hearth.kindlings.tapirschemaderivation.KindlingsSchema
  given sttp.tapir.Schema[Wire] = summon[hearth.kindlings.tapirschemaderivation.KindlingsSchema[Wire]].schema
  given io.circe.Codec.AsObject[Wire] = hearth.kindlings.circederivation.KindlingsCodecAsObject.derived[Wire]
object Lookalike:
  def derived(): Int = 1
  val instance = derived()
