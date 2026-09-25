package histdb.db

class TestDbSuite extends munit.FunSuite:

  test("a *_test database is accepted"):
    assertEquals(TestDb.validate("jdbc:postgresql://127.0.0.1:55432/histdb_test"),
      Right("jdbc:postgresql://127.0.0.1:55432/histdb_test"))
    assert(TestDb.validate("jdbc:postgresql://h/x_test?sslmode=disable").isRight)

  test("the live corpus database is refused"):
    assert(TestDb.validate("jdbc:postgresql://localhost:5432/histdb").isLeft)
    assert(TestDb.validate("jdbc:postgresql://localhost:5432/histdb?user=histdb").isLeft)
    assert(TestDb.validate("jdbc:postgresql://localhost:5432/test_histdb").isLeft)
