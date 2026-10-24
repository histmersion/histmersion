package com.histmersion.pluginresolver

import org.w3c.dom.{Element, Node}
import org.xml.sax.helpers.DefaultHandler

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import scala.jdk.CollectionConverters.*
import scala.util.Try

/** The one thing read from a plugin's POM here: whose it is. Everything else in it (dependencies,
  * properties, parents, dependency management) is coursier's to read. */
object Pom:

  /** The POM's own coordinates; a group or version it does not state is its parent's, as in Maven. */
  def coordinates(xml: String): Either[String, ModuleCoordinates] =
    for
      project  <- parse(xml)
      parent    = child(project, "parent")
      group    <- text(project, "groupId").orElse(parent.flatMap(text(_, "groupId"))).toRight("the POM names no groupId")
      artifact <- text(project, "artifactId").toRight("the POM names no artifactId")
      version  <- text(project, "version").orElse(parent.flatMap(text(_, "version"))).toRight("the POM names no version")
      coords   <- ModuleCoordinates.parse(s"$group:$artifact:$version")
    yield coords

  /** The POM published beside a jar (`x-1.0.jar` → `x-1.0.pom`), else the one Maven's jar plugin puts in
    * the jar when it holds exactly one (`META-INF/maven/<group>/<artifact>/pom.xml`). A shaded jar holds
    * several, of the libraries shaded into it: no POM can be told to be the plugin's then. */
  def of(jar: Path): Option[String] =
    val beside = besideJar(jar)
    if Files.isRegularFile(beside) then Some(Files.readString(beside, UTF_8)) else embedded(jar)

  def besideJar(jar: Path): Path = jar.resolveSibling(jar.getFileName.toString.stripSuffix(".jar") + ".pom")

  private def embedded(jar: Path): Option[String] =
    Try {
      val zip = ZipFile(jar.toFile)
      try
        zip.entries().asScala.filter(e => e.getName.startsWith("META-INF/maven/") && e.getName.endsWith("/pom.xml")).toList match
          case List(only) => Some(String(zip.getInputStream(only).readAllBytes(), UTF_8))
          case _          => None
      finally zip.close()
    }.toOption.flatten

  private def parse(xml: String): Either[String, Element] =
    Try {
      val factory = DocumentBuilderFactory.newInstance()
      // a POM is data from the network: no DOCTYPE, so no external entity is ever resolved
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
      factory.setNamespaceAware(true)
      val builder = factory.newDocumentBuilder()
      // the parse error is the Left below; the parser's own printing to stderr is not wanted
      builder.setErrorHandler(new DefaultHandler)
      builder.parse(ByteArrayInputStream(xml.getBytes(UTF_8))).getDocumentElement
    }.toEither.left.map(e => s"not a POM: ${e.getMessage}").filterOrElse(_.getLocalName == "project", "not a POM: no <project>")

  private def child(e: Element, name: String): Option[Element] =
    val nodes = e.getChildNodes
    (0 until nodes.getLength).map(nodes.item).collectFirst {
      case c: Element if c.getNodeType == Node.ELEMENT_NODE && c.getLocalName == name => c
    }

  private def text(e: Element, name: String): Option[String] =
    child(e, name).map(_.getTextContent.trim).filter(_.nonEmpty)
