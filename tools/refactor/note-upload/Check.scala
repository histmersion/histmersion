package com.histmersion.refactor

import com.histmersion.domain.library.sources.SourceNote
import com.histmersion.domain.library.ingest.Upload
import com.histmersion.util.storage.{TextFiles, DiskTextFiles, HandedFile}
import java.nio.file.{Files, Path}
import java.nio.charset.StandardCharsets.UTF_8

object NoteUploadCheck:
  def main(args: Array[String]): Unit =
    val notePath = Path.of("/corpus/wiki/sources/fallback.md")
    val ocrPath = Path.of("/corpus/md/fallback/paper.md")
    def note(ocr: String) = s"---\ntitle: Note\nmd_path: $ocr\n---\nBody"
    var content = Map(notePath -> note("md/fallback/paper.md"), ocrPath -> "a\u0000b")
    var observed = List.empty[Path]
    given TextFiles = new TextFiles:
      def exists(path: Path): Boolean =
        observed = observed :+ path.normalize
        content.contains(path.normalize)
      def read(path: Path): String =
        observed = observed :+ path.normalize
        content.getOrElse(path.normalize, throw java.nio.file.NoSuchFileException(path.toString))
    val source = SourceNote.parseFile(notePath, Some("/corpus")).get
    assert(source.citekey == "fallback" && source.notes.contains("Body") && source.ocrMarkdown.contains("ab"))
    assert(observed == List(notePath, ocrPath, ocrPath))
    content = content.updated(notePath, note("../outside.md")).updated(Path.of("/outside.md"), "outside")
    observed = Nil
    assert(SourceNote.parseFile(notePath, Some("/corpus")).get.ocrMarkdown.isEmpty)
    assert(observed == List(notePath), "Confinement must run before probing or reading outside paths")
    observed = Nil
    assert(SourceNote.parseFile(notePath).get.ocrMarkdown.contains("outside"))
    assert(observed == List(notePath, Path.of("/outside.md"), Path.of("/outside.md")))
    content = Map(notePath -> note("md/missing/paper.md"))
    assert(SourceNote.parseFile(notePath, Some("/corpus")).get.ocrMarkdown.isEmpty)
    content = Map(notePath -> "no frontmatter")
    observed = Nil
    assert(SourceNote.parseFile(notePath).isEmpty && observed == List(notePath))
    content = Map.empty
    assert(scala.util.Try(SourceNote.parseFile(notePath)).failed.get.isInstanceOf[java.nio.file.NoSuchFileException])

    val directory = Files.createTempDirectory(Files.createDirectories(Path.of("target/note-upload-fixture")), "input-")
    val file = directory.resolve("input.pdf")
    Files.writeString(file, "bytes", UTF_8)
    val disk = DiskTextFiles()
    assert(disk.exists(file) && disk.read(file) == "bytes")
    var names = 0
    var lengths = 0
    val counted = new java.io.File(file.toString):
      override def getName(): String = { names += 1; super.getName() }
      override def length(): Long = { lengths += 1; super.length() }
    val snapshot = HandedFile.snapshot(counted)
    assert(names == 1 && lengths == 1)
    val upload = Upload.handed(snapshot)
    assert(upload.name == "input.pdf" && upload.size == 5)
    assert(names == 1 && lengths == 1, "Domain construction must not query the underlying file")
    assert(upload.bytes.read(in => String(in.readAllBytes(), UTF_8)) == "bytes")
    assert(upload.bytes.read(in => String(in.readAllBytes(), UTF_8)) == "bytes")
    Files.delete(file)
    Files.delete(directory)
    assert(Upload.handed(snapshot).size == 5 && names == 1 && lengths == 1)
    println("PASS: injected note reads; OCR confinement before I/O; optional OCR; NUL filtering; missing-note failure; disk adapter; boundary metadata snapshot; repeatable byte source")
