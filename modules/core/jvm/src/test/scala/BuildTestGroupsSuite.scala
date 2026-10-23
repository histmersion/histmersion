// project/TestGroups.scala is a build helper (no package); build.sbt adds it to coreJVM's test sources so
// its pure functions are tested like any other.

class BuildTestGroupsSuite extends munit.FunSuite:

  test("a group's database keeps the _test suffix, the host, the port and the parameters"):
    assertEquals(TestGroups.groupDbUrl("jdbc:postgresql://127.0.0.1:55432/histmersion_test", "server-2"),
      "jdbc:postgresql://127.0.0.1:55432/histmersion_server_2_test")
    assertEquals(TestGroups.groupDbUrl("jdbc:postgresql://h:1/histmersion_test?sslmode=disable", "db-0"),
      "jdbc:postgresql://h:1/histmersion_db_0_test?sslmode=disable")
    assertEquals(TestGroups.groupDbUrl("jdbc:postgresql://h/x", "A.b"), "jdbc:postgresql://h/x_a_b_test")

  test("split: at most n non-empty groups, every item exactly once, longest first to the lightest group"):
    val items = List("a", "b", "c", "d", "e")
    val w     = Map("a" -> 10.0, "b" -> 1.0, "c" -> 1.0, "d" -> 1.0, "e" -> 7.0)
    val g     = TestGroups.split(items, 2)(identity, s => w.getOrElse(s, 1.0))
    assertEquals(g, Seq(Seq("a"), Seq("b", "c", "d", "e")))
    assertEquals(TestGroups.split(items, 9)(identity, _ => 1.0).flatten.sorted, items)
    assertEquals(TestGroups.split(items, 9)(identity, _ => 1.0).size, 5)
    assertEquals(TestGroups.split(List.empty[String], 3)(identity, _ => 1.0), Seq.empty)
    assertEquals(TestGroups.split(items, 0)(identity, _ => 1.0), Seq(items))

  test("split is stable: equal weights deal round-robin by name"):
    assertEquals(TestGroups.split(List("d", "b", "a", "c"), 2)(identity, _ => 1.0), Seq(Seq("a", "c"), Seq("b", "d")))

  test("weights: suite TAB seconds; comments, blanks and bad lines skipped"):
    assertEquals(TestGroups.weights(Seq("# head", "", "HardeningHttpSuite\t44", "Bad", "Neg\t-1", "X\tnope", "  Y  3.5 ")),
      Map("HardeningHttpSuite" -> 44.0, "Y" -> 3.5))

  test("forks: a positive integer overrides the default, anything else keeps it"):
    assertEquals(TestGroups.forks(Some("6"), 4), 6)
    assertEquals(TestGroups.forks(Some(" 2 "), 4), 2)
    assertEquals(TestGroups.forks(Some("0"), 4), 4)
    assertEquals(TestGroups.forks(Some("x"), 4), 4)
    assertEquals(TestGroups.forks(None, 4), 4)
