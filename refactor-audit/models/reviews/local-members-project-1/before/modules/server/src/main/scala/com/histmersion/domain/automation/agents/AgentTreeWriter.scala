package com.histmersion.domain.automation.agents

import com.histmersion.api.automation.Agents.GenerateResult
import com.histmersion.util.storage.Store
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Clock
import io.circe.Json
import io.circe.syntax.*

private[agents] object AgentTreeWriter:
  type WriteResultPart1 = WriteResultPart1.T
  object WriteResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteTreePart1 = WriteTreePart1.T
  object WriteTreePart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteTreePart2 = WriteTreePart2.T
  object WriteTreePart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteRuntimes = WriteRuntimes.T
  object WriteRuntimes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteSha = WriteSha.T
  object WriteSha extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Manifest = Store.Key.of("manifest.json").fold(error => throw IllegalArgumentException(error), identity)

  def write(store: Store, tree: Map[com.histmersion.domain.automation.agents.AgentTreeWriter.WriteTreePart1, com.histmersion.domain.automation.agents.AgentTreeWriter.WriteTreePart2], runtimes: List[com.histmersion.domain.automation.agents.AgentTreeWriter.WriteRuntimes], sha: com.histmersion.domain.automation.agents.AgentTreeWriter.WriteSha)(using clock: Clock): Either[com.histmersion.domain.automation.agents.AgentTreeWriter.WriteResultPart1, GenerateResult] =
    try
      def key(name: String): Store.Key = Store.Key.of(name).fold(error => throw IllegalArgumentException(error), identity)
      store.makeDirectory().fold(error => throw java.io.IOException(error.message), identity)
      val previous = store.get(Manifest).toList.flatMap { blob =>
        io.circe.parser.parse(blob.text()).toOption
          .flatMap(_.hcursor.downField("files").as[Map[String, String]].toOption)
          .map(_.keys.toList).getOrElse(Nil)
      }
      val entries = tree.toList.sortBy(_._1).map((name, text) => (key(name), text))
      var written = 0
      entries.foreach { (target, text) =>
        val same = store.get(target).exists(_.text() == text)
        if !same then
          store.put(target, text.getBytes(UTF_8)).fold(error => throw java.io.IOException(error.message), _ => ())
          written += 1
      }
      val gone = previous.filter(p => runtimes.exists(r => p.startsWith(s"$r/")) && !tree.contains(p))
      gone.foreach(name => { val _ = store.delete(key(name)) })
      if runtimes.contains(AgentRuntime.ClaudeCode.wire) then
        store.makeDirectory(key("claude-code/login")).fold(error => throw java.io.IOException(error.message), identity)
      val keep = previous.filterNot(p => runtimes.exists(r => p.startsWith(s"$r/")))
      val files = (keep.flatMap(name => store.get(key(name)).map(blob => name -> Prompt.sha256(blob.text()))) ++
        tree.toList.map((name, text) => name -> Prompt.sha256(text))).sortBy(_._1)
      val manifest = Json.obj(
        "generator" -> AgentGenerator.PluginVersion.asJson,
        "definitions" -> sha.asJson,
        "generatedAt" -> java.time.Instant.now(clock).toString.asJson,
        "runtimes" -> runtimes.asJson,
        "files" -> Json.obj(files.map((name, sha) => name -> sha.asJson)*)
      )
      store.write(Manifest, AgentGenerator.pretty(manifest).getBytes(UTF_8)).fold(error => throw java.io.IOException(error.message), _ => ())
      Right(GenerateResult(store.where(), runtimes, tree.size, written, gone.size, sha))
    catch case error: Exception => Left(s"generating into ${store.where()} failed: ${error.getClass.getSimpleName}: ${error.getMessage}")
