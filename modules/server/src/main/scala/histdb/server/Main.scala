package histdb.server

import cats.effect.*
import com.comcast.ip4s.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import histdb.core.Version

/** hist-db server entrypoint. Phase-1 skeleton: a live http4s server with health/version.
  * Next: Tapir REST endpoints, the MCP JSON-RPC route (`/mcp`), the static web UI, and the
  * Doobie-backed query service. */
object Main extends IOApp.Simple:

  private val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "health" =>
      Ok("""{"status":"ok"}""")
    case GET -> Root / "version" =>
      Ok(s"""{"name":"${Version.name}","version":"${Version.version}"}""")
  }

  private val httpApp: HttpApp[IO] = routes.orNotFound

  val run: IO[Unit] =
    EmberServerBuilder
      .default[IO]
      .withHost(host"0.0.0.0")
      .withPort(port"8000")
      .withHttpApp(httpApp)
      .build
      .use(_ => IO.println(s"hist-db server on :8000") *> IO.never)
