package fixture

import fixture.old.Moved
import fixture.old.{Kept, Moved as Renamed}
import fixture.old.Kept

object old:
  trait Moved
  trait Kept

object fresh:
  trait Moved

object Uses:
  val a: Option[Moved] = None
  val b: Option[Kept] = None
  val c: Option[Renamed] = None
