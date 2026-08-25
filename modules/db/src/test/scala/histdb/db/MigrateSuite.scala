package histdb.db

class MigrateSuite extends munit.FunSuite:
  test("migration index lists every V*.sql, in order, and each is loadable"):
    val names = Migrate.migrationNames
    assert(names.nonEmpty)
    assertEquals(names, names.sorted)
    assert(names.forall(_.matches("""V\d{3}__[a-z0-9_]+\.sql""")), names)
    names.foreach(n => assert(getClass.getClassLoader.getResource(s"db/migration/$n") != null, n))
