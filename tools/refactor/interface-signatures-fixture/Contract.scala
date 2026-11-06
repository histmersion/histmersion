package interfacefixture

@main def verifyInterfaceSignatures(): Unit =
  assert(Generic.create[Int]().add(Values.Text("id"), 7) == Right(()))
  assert(Other.read() == "unchanged")
  NestedTesting.fake.emit(Values.Text("message"))
