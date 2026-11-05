package conceptfixture

import io.github.iltotore.iron.*
import scala.compiletime.testing.typeChecks

@main def checkConcepts(): Unit =
  assert(!typeChecks("conceptfixture.Report(label = \"raw\")"))
  assert(!typeChecks("conceptfixture.Report(label = conceptfixture.Title(\"wrong concept\"))"))
  assert(!typeChecks("conceptfixture.Report(authors = List(\"raw\"))"))
  assert(!typeChecks("conceptfixture.Report(doi = Some(\"raw\"))"))
  val r = Calls.positional
  assert(r.label.value == "k" && r.title.value == "t")
  assert(r.bytes.value == 5L && r.lat.value == 1.5 && r.year.value == 1999)
  assert(r.doi.map(_.value).contains("d"))
  assert(r.authors.map(_.value) == List("a", "b"))
  assert(Calls.forwarded(r) == r)
  assert(Calls.copied(r).title.value == "changed")
  assert(Calls.label(r) == r.label && Calls.authors(r) == r.authors)
  assert(Calls.echoed.value == "echo")
  val named = Calls.named
  assert(Calls.evaluations == 1 && named.authors.map(_.value) == List("a", "b"))
  assert(Calls.defaults.doi.isEmpty && Calls.defaults.authors.isEmpty)
  assert(Calls.defaults.bytes.value == 0L)
  assert(Calls.unrelated == Unrelated("raw", 3L))
