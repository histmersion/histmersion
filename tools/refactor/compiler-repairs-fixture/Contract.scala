package compilerfixture

object Contract:
  @main def verifyCompilerRepairs(): Unit =
    val text = Positive.produce("hello")
    assert(Positive.consume(text) == "hello")
    assert(Positive.upper(text) == "HELLO")
    assert(Positive.number.value == 7)
    val bytes = new java.io.ByteArrayOutputStream
    Positive.output(new java.io.PrintStream(bytes), text)
    assert(bytes.toString("UTF-8") == "hello")
    assert(Positive.produce(null).value == null)
    assert(Positive.combined("hi").value == "HI!")
    assert(Positive.optional("hello").map(_.value).contains("hello"))
    assert(Positive.optional(null).isEmpty)
