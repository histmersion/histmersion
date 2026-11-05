package migrationfixture

import scala.compiletime.testing.typeChecks

@main def checkMigration(): Unit =
  assert(!typeChecks("val x: migrationfixture.Offset = 1"))
  assert(!typeChecks("val x: migrationfixture.Offset = migrationfixture.RowCount(1)"))
  assert(!typeChecks("migrationfixture.Page(offset = 1)"))
  assert(Calls.offset(Calls.positional) == Offset(1))
  assert(Calls.named.total == RowCount(12))
  assert(Calls.defaults.offset == Offset(0))
  assert(Calls.copied(Calls.positional).offset == Offset(4))
  assert(Calls.forwarded(Calls.positional) == Calls.positional)
  assert(Calls.unrelated.offset == 3)
