package methodfixture
@main def checkMethods(): Unit =
  assert(Calls.positional.total.value == 5)
  assert(Calls.named.total.value == 7)
  assert(Calls.defaulted.total.value == 0)
  assert(Calls.abstractCall(Service).total.value == 9)
  assert(Calls.forwarded(Page(RowCount(13))).total.value == 13)
  assert(Calls.optional.map(_.value).contains(4))
  assert(Service.optional().isEmpty)
  assert(Calls.unrelated == 11)
  assert(Calls.converted(Wire(WireCount(17))).total.value == 17)
  assert(!scala.compiletime.testing.typeChecks("methodfixture.Service.page(1)"))
