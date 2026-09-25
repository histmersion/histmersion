package histdb.db

/** The database the DB-backed suites run against: `HISTDB_TEST_DB_URL`, or `None` (the suites skip).
  *
  * The suites WRITE fixtures (`zz*` places, sources, articles), so the URL must name a disposable
  * database — one whose name ends in `_test`, as `make test-db` creates. Anything else (notably the
  * live corpus, `histdb`, which `make dev-up` publishes on :5432) is refused with an error instead of
  * silently seeding test polygons into the atlas, which is exactly what happened once. */
object TestDb:
  val Env = "HISTDB_TEST_DB_URL"

  private[db] def validate(url: String): Either[String, String] =
    val name = url.split('?').head.reverse.takeWhile(_ != '/').reverse
    if name.endsWith("_test") then Right(url)
    else Left(
      s"$Env points at database '$name', which is not a disposable test database (its name must end in " +
        "'_test'). The DB suites write fixtures — never run them against the live corpus. " +
        "Use `make test-db`, then HISTDB_TEST_DB_URL=jdbc:postgresql://127.0.0.1:55432/histdb_test.")

  lazy val url: Option[String] =
    sys.env.get(Env).filter(_.nonEmpty).map(u => validate(u).fold(e => throw IllegalStateException(e), identity))
