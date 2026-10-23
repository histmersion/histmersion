package com.histmersion.db

class TestDbSuite extends munit.FunSuite:

  test("a *_test database is accepted"):
    assertEquals(TestDb.validate("jdbc:postgresql://127.0.0.1:55432/histmersion_test"),
      Right("jdbc:postgresql://127.0.0.1:55432/histmersion_test"))
    assert(TestDb.validate("jdbc:postgresql://h/x_test?sslmode=disable").isRight)

  test("the live corpus database is refused"):
    assert(TestDb.validate("jdbc:postgresql://localhost:5432/histmersion").isLeft)
    assert(TestDb.validate("jdbc:postgresql://localhost:5432/histmersion?user=histmersion").isLeft)
    assert(TestDb.validate("jdbc:postgresql://localhost:5432/test_histmersion").isLeft)

  test("the database name is the URL's last path segment, without its parameters"):
    assertEquals(TestDb.name("jdbc:postgresql://127.0.0.1:55432/histmersion_server_2_test"), "histmersion_server_2_test")
    assertEquals(TestDb.name("jdbc:postgresql://h/x_test?sslmode=disable"), "x_test")
