package com.histmersion
package server

/** G123's check: every place in main code that starts a thread or a fork is either a worker a restore holds (it
  * registers with the server's `Workers`, under the name given here; RestoreSafetySuite asserts a composed server lists
  * exactly these names) or listed here with the reason it needs no holding: it never writes the database. Work that
  * runs once goes through `Workers.detached`, which is the registry itself, so it starts no thread of its own. A new
  * place that starts a thread fails here until it is registered or its reason is written down.
  */
class WorkersSuite extends munit.FunSuite:
  import WorkersSuite.*

  test("every place that starts a thread or a fork is a registered worker, or says why it needs no holding"):
    val found = Scanned.flatMap { module =>
      ArchitectureSuite.SourceFile.all(module).filter(s => Starts.findFirstIn(s.code).isDefined).map(_.key)
    }.toSet
    val unlisted = found -- Listed.keySet
    val stale = Listed.keySet -- found
    assert(
      unlisted.isEmpty,
      s"these files start a thread or a fork that no restore holds: register the worker (Workers.register, or start " +
        s"the work through Workers.detached) and list it, or list why it never writes the database:\n" +
        unlisted.toList.sorted.mkString("\n")
    )
    assert(stale.isEmpty, s"listed, but starting no thread any more (remove the row): ${stale.toList.sorted}")

  test("the registered workers are the ones a composed server lists"):
    assertEquals(
      Listed.values.collect { case Place.Registered(name) => name }.toSet,
      Set(
        "work started once",
        "the OCR worker pool",
        "the agent run pool",
        "the process managers' loop",
        "the agent lease's renewal",
        "the structured log's writer"
      )
    )

object WorkersSuite:
  /** The modules whose main code may start threads that reach the database. */
  val Scanned: List[String] = List("db", "server", "plugin-host")

  /** A thread or a fork started: in code, not in comments or text. */
  val Starts =
    """Thread\s*\.\s*of(Platform|Virtual)|new\s+Thread\b|\bThread\s*\(|\bfork(User|Discard|Error)?\s*\(|Executors\s*\.""".r

  enum Place:
    /** A worker registered with the server's `Workers` under this name. */
    case Registered(name: String)

    /** Starts threads that never write the database, for this reason. */
    case Exempt(why: String)

  val Listed: Map[String, Place] = Map(
    "server:util/jobs/Workers.scala" -> Place.Registered("work started once"),
    "server:util/jobs/JobPool.scala" -> Place.Registered("the OCR worker pool"),
    "server:domain/automation/agents/Engine.scala" -> Place.Registered("the agent run pool"),
    "server:domain/automation/managers/ManagerSupervisor.scala" -> Place.Registered("the process managers' loop"),
    "server:domain/automation/agents/AgentLease.scala" -> Place.Registered("the agent lease's renewal"),
    "server:util/log/LogWriter.scala" -> Place.Registered("the structured log's writer"),
    "server:server/hot/Hot.scala" -> Place.Exempt("the config graph's own build and drain threads"),
    "server:server/ServerGraph.scala" -> Place.Exempt(
      "the config watcher (a reload is refused while a restore runs), the start's ANALYZE, and the instance lock's " +
        "watch, on a session of its own outside the pools"
    ),
    "server:server/Supervisor.scala" -> Place.Exempt("the bundled converter's supervision and the shutdown hook"),
    "server:server/Main.scala" -> Place.Exempt("the exit that hands a restart to the service manager"),
    "server:server/agents/CliProcess.scala" -> Place.Exempt("the pipes of a command-line runtime's process"),
    "server:server/agents/Codex.scala" -> Place.Exempt("the retention of Codex homes on disk"),
    "server:util/basemap/BasemapService.scala" -> Place.Exempt("closes a downloaded file later"),
    "server:util/data/diagnostics/StorageCache.scala" -> Place.Exempt("measures the disk"),
    "db:util/database/Transactor.scala" -> Place.Exempt(
      "readToCompletion: a read on its caller's connection, which the caller waits for, so an interrupt does not cut " +
        "the statement"
    ),
    "plugin-host:pluginhost/SystemProcesses.scala" -> Place.Exempt("the pipes of a plugin's process")
  )
