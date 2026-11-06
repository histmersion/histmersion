package interfacefixture

@main def verifyInterfaceSignatures(): Unit =
  assert(Generic.create[Int]().add(Values.Text("id"), 7) == Right(()))
  assert(Concrete.add(Values.Text("id"), 7) == Right(()))
  assert(ImportedUsing.Live.add("unchanged", 7))
  assert(LocalUsing.Live.add("unchanged", 7))
  assert(Other.read() == "unchanged")
  NestedTesting.fake.emit(Values.Text("message"))
