package com.histmersion
package db
import com.histmersion.util.database.given

class TestDbSuite extends munit.FunSuite:

  test("a *_test database is accepted"):
    assertEquals(
      TestDb.validate("jdbc:postgresql://127.0.0.1:55432/histmersion_test"),
      Right("jdbc:postgresql://127.0.0.1:55432/histmersion_test")
    )
    assert(TestDb.validate("jdbc:postgresql://h/x_test?sslmode=disable").isRight)

  test("the live corpus database is refused"):
    assert(TestDb.validate("jdbc:postgresql://localhost:5432/histmersion").isLeft)
    assert(TestDb.validate("jdbc:postgresql://localhost:5432/histmersion?user=histmersion").isLeft)
    assert(TestDb.validate("jdbc:postgresql://localhost:5432/test_histmersion").isLeft)

  test("no URL skips the DB suites, unless HISTMERSION_TEST_DB_REQUIRED asks for them: then it is an error (B8)"):
    val url = "jdbc:postgresql://127.0.0.1:55432/histmersion_test"
    assertEquals(TestDb.configured(Map.empty), Right(None))
    assertEquals(TestDb.configured(Map(TestDb.Env -> "")), Right(None))
    List("", "0", "false", "FALSE").foreach { off =>
      assertEquals(TestDb.configured(Map(TestDb.RequiredEnv -> off)), Right(None), off)
    }
    List("1", "true", "yes").foreach { on =>
      val got = TestDb.configured(Map(TestDb.RequiredEnv -> on))
      assert(got.left.exists(_.contains(TestDb.Env)), s"$on: $got")
      assertEquals(TestDb.configured(Map(TestDb.RequiredEnv -> on, TestDb.Env -> url)), Right(Some(url)))
    }

  test("a configured URL that is not a test database is refused, required or not"):
    val live = "jdbc:postgresql://localhost:5432/histmersion"
    assert(TestDb.configured(Map(TestDb.Env -> live)).isLeft)
    assert(TestDb.configured(Map(TestDb.Env -> live, TestDb.RequiredEnv -> "1")).isLeft)

  test("the database name is the URL's last path segment, without its parameters"):
    assertEquals(
      TestDb.name("jdbc:postgresql://127.0.0.1:55432/histmersion_server_2_test"),
      "histmersion_server_2_test"
    )
    assertEquals(TestDb.name("jdbc:postgresql://h/x_test?sslmode=disable"), "x_test")
