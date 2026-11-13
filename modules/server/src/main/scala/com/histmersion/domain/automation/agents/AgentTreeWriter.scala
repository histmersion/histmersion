package com.histmersion
package domain.automation.agents
import com.histmersion.util.json.RawJson
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.util.problem.Text.sentence
import com.histmersion.api.automation.Agents.GenerateResult
import com.histmersion.util.storage.{ByteSource, Store}
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Clock

private[agents] object AgentTreeWriter:
  private val Manifest =
    Store.Key.of("manifest.json").fold(error => throw IllegalArgumentException(error.sentence), identity)

  def write(store: Store, tree: Map[String, String], runtimes: List[String], sha: Sha256)(using
      clock: Clock
  ): Either[String, GenerateResult] =
    try
      def key(name: String): Store.Key =
        Store.Key.of(name).fold(error => throw IllegalArgumentException(error.sentence), identity)
      store.makeDirectory().fold(error => throw java.io.IOException(error.message.sentence), identity)
      val previous = store.get(Manifest).toList.flatMap { blob =>
        RawJson
          .parse(blob.text().value)
          .toOption
          .flatMap(_.cursor.downField("files").as[Map[String, String]].toOption)
          .map(_.keys.toList)
          .getOrElse(Nil)
      }
      val entries = tree.toList.sortBy(_._1).map((name, text) => (key(name), text))
      var written = 0
      entries.foreach { (target, text) =>
        val same = store.get(target).exists(_.text().value == text)
        if !same then
          store
            .put(target, ByteSource.Content(text.getBytes(UTF_8)))
            .fold(error => throw java.io.IOException(error.message.sentence), _ => ())
          written += 1
      }
      val gone = previous.filter(p => runtimes.exists(r => p.startsWith(s"$r/")) && !tree.contains(p))
      gone.foreach { name =>
        val _ = store.delete(key(name))
      }
      if runtimes.contains(AgentRuntime.ClaudeCode.wire.value) then
        store
          .makeDirectory(key("claude-code/login"))
          .fold(error => throw java.io.IOException(error.message.sentence), identity)
      val keep = previous.filterNot(p => runtimes.exists(r => p.startsWith(s"$r/")))
      val files = (keep.flatMap(name => store.get(key(name)).map(blob => name -> Prompt.sha256(blob.text().value))) ++
        tree.toList.map((name, text) => name -> Prompt.sha256(text))).sortBy(_._1)
      val manifest = Json.obj(
        "generator" -> AgentGenerator.PluginVersion.asJson,
        "definitions" -> Json.fromString(sha.value),
        "generatedAt" -> java.time.Instant.now(clock).toString.asJson,
        "runtimes" -> runtimes.asJson,
        "files" -> Json.obj(files.map((name, sha) => name -> Json.fromString(sha.value))*)
      )
      store
        .write(Manifest, ByteSource.Content(AgentGenerator.pretty(manifest).getBytes(UTF_8)))
        .fold(error => throw java.io.IOException(error.message.sentence), _ => ())
      Right(
        GenerateResult(
          GenerateResult.Dir(store.where().value),
          runtimes.map(GenerateResult.Runtimes(_)),
          GenerateResult.Files(tree.size),
          GenerateResult.Written(written),
          GenerateResult.Removed(gone.size),
          GenerateResult.Definitions(sha.value)
        )
      )
    catch
      case error: Exception =>
        Left(s"generating into ${store.where()} failed: ${error.getClass.getSimpleName}: ${error.getMessage}")
