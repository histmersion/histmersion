package modelinventoryfixture

final case class CrossFile(externalId: OpaqueId, externalYear: Year, externalAlias: Bag[Count])
object Constants:
  val sentinel: 42 = 42
  given String = "fixture"
trait Bounded:
  type Small <: Int

type Unrestricted = io.github.iltotore.iron.constraint.any.Pure
