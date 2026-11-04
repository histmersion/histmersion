package com.histmersion.domain.automation.agents

import com.histmersion.util.storage.*
import java.nio.file.{Files, Path}
import java.nio.charset.StandardCharsets.UTF_8
import java.time.{Clock, Instant, ZoneOffset}

object AgentStorageCheck:
  def main(args: Array[String]): Unit =
    given Clock = Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC)
    given Stores = Stores.InMemory()
    given Resources = Resources.InMemory(Map("agents/a.md" -> "bundled", "agents/../outside.md" -> "outside fallback"))
    def key(name: String) = Store.Key.of(name).toOption.get
    val root = Path.of("target/agent-storage-fixture/prompts").toAbsolutePath
    val overrides = summon[Stores].at(Location.AgentHomes, root)
    overrides.put(key("a.md"), "override".getBytes(UTF_8)).toOption.get
    val prompts = Prompts(root.toString)
    assert(prompts.load("a.md") == Right("override"))
    overrides.delete(key("a.md"))
    assert(prompts.load("a.md") == Right("bundled"))
    assert(prompts.load("../outside.md") == Right("outside fallback"))
    assert(prompts.load("missing.md").isLeft)
    val generated = MemoryStore(Location.AgentHomes, "generated")
    generated.put(key("codex/home/auth.json"), "foreign".getBytes(UTF_8)).toOption.get
    val first = AgentTreeWriter.write(generated, Map("codex/home/a" -> "1", "codex/home/b" -> "2"), List("codex"), "sha1").toOption.get
    assert((first.files, first.written, first.removed) == (2, 2, 0))
    val second = AgentTreeWriter.write(generated, Map("codex/home/a" -> "1"), List("codex"), "sha2").toOption.get
    assert((second.written, second.removed) == (0, 1))
    assert(generated.get(key("codex/home/auth.json")).get.text() == "foreign")
    assert(!generated.exists(key("codex/home/b")))
    assert(generated.get(key("manifest.json")).get.text().contains("2026-01-02T03:04:05Z"))
    assert(AgentTreeWriter.write(generated, Map("../escape" -> "bad"), List("codex"), "bad").isLeft)
    val other = AgentTreeWriter.write(generated, Map("claude-code/x" -> "y"), List("claude-code"), "sha3").toOption.get
    assert(other.removed == 0 && generated.isDirectory(key("claude-code/login")) && generated.isFile(key("codex/home/a")))
    val plugin = MemoryStore(Location.AgentHomes, "plugin")
    plugin.put(key("plugin/agents/stale/nested.md"), Array.emptyByteArray).toOption.get
    plugin.put(key("unowned.txt"), "keep".getBytes(UTF_8)).toOption.get
    assert(PluginTree.writeTree(plugin, Map("plugin/agents/new.md" -> "new")).isRight)
    assert(!plugin.exists(key("plugin/agents/stale")))
    assert(plugin.get(key("unowned.txt")).get.text() == "keep")
    assert(plugin.get(key("plugin/agents/new.md")).get.text() == "new")
    val base = Files.createDirectories(Path.of("target/agent-storage-fixture"))
    val diskRoot = Files.createTempDirectory(base, "disk-")
    val external = Files.createTempDirectory(base, "external-").toAbsolutePath
    Files.writeString(external.resolve("keep.md"), "external")
    Files.createDirectories(diskRoot.resolve("plugin/agents"))
    Files.createDirectories(diskRoot.resolve("plugin/skills"))
    Files.createSymbolicLink(diskRoot.resolve("plugin/agents/link"), external)
    Files.createSymbolicLink(diskRoot.resolve("plugin/skills/replace"), external)
    val disk = Storage.at(Location.AgentHomes, diskRoot)
    assert(PluginTree.writeTree(disk, Map("plugin/skills/replace/SKILL.md" -> "skill")).isRight)
    assert(!Files.isSymbolicLink(diskRoot.resolve("plugin/skills/replace")))
    assert(Files.readString(external.resolve("keep.md")) == "external")
    assert(!Files.exists(external.resolve("SKILL.md")))
    assert(!Files.exists(diskRoot.resolve("plugin/agents/link")))
    var closed = false
    val reader = Resources.classpath(new ClassLoader(null):
      override def getResourceAsStream(name: String): java.io.InputStream = new java.io.ByteArrayInputStream("resource".getBytes(UTF_8)):
        override def close(): Unit = { closed = true; super.close() }
    )
    assert(reader.text("test") == Right("resource") && closed)
    val home = com.histmersion.util.system.JvmHome(Path.of("/home/user$literal"))
    assert(home.expand("~/config") == "/home/user$literal/config")
    assert(home.expand("/absolute/config") == "/absolute/config")
    closed = false
    val failingReader = Resources.classpath(new ClassLoader(null):
      override def getResourceAsStream(name: String): java.io.InputStream = new java.io.InputStream:
        def read(): Int = throw java.io.IOException("read failed")
        override def close(): Unit = { closed = true }
    )
    assert(scala.util.Try(failingReader.text("test")).isFailure && closed)
    println("PASS: injected prompt fallback; deterministic manifest; selective cleanup; confinement; memory and disk stores; symlink isolation; resource closure")
