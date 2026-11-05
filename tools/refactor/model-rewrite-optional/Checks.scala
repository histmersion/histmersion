package migrationfixture

import scala.compiletime.testing.typeChecks

@main def checkOptionalMigration(): Unit =
  assert(!typeChecks("migrationfixture.Page(Some(1))"))
  assert(Calls.defaults.next.isEmpty)
  assert(Calls.positional.next.contains(Offset(3)))
  assert(Calls.named.next.contains(Offset(4)))
  assert(Calls.copied(Calls.positional).next.contains(Offset(5)))
  assert(Calls.forwarded(Calls.positional).next == Calls.positional.next)
  assert(Calls.fromRaw.next.contains(Offset(6)))
  assert(Calls.unrelated.offset.contains(7))
