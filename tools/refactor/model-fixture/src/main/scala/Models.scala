package modelinventoryfixture

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

type Count = Int
type Bag[A] = List[A]
opaque type OpaqueId = String
object OpaqueId:
  def from(rawId: String): OpaqueId = rawId
type Year = Year.T
object Year extends RefinedType[Int, Pure]

final case class Source()
final case class Article()
final case class PageSize[A](amount: Int)
final case class Page[A](items: List[A], size: PageSize[A])
final case class Record(
    title: String,
    nestedCounts: Option[Map[String, List[Long]]],
    aliasCount: Count,
    aliasBag: Bag[Int],
    existingId: OpaqueId,
    existingYear: Year,
    sourceSize: PageSize[Source],
    articlePage: Page[Article],
    handler: String => Int,
    pair: (String, Double),
    eitherCount: Int | String
)
trait Service:
  def fetch(pageSize: Int)(using tag: String): List[Record]
  def count: Long
  val defaultSize: Int
object Operations:
  val inferredCount = 3
  val (leftCount, rightName) = (4, "right")
  def inferredName = "name"
  def byName(later: => Int): Int = later
  def repeated(numbers: Int*): Int = numbers.sum
  def lookup(record: Record): String = record.title
  def localScope: Int =
    val localCount = 1
    localCount
object Lookalikes:
  final case class String(value: scala.Int)
  final case class Int()
  final case class Other(notPrimitive: String, alsoNotPrimitive: Int)
