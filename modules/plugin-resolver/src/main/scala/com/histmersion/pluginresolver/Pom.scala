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

/** The one thing read from a plugin's POM here: whose it is. Everything else in it (dependencies, properties, parents,
  * dependency management) is coursier's to read.
  */
object Pom:
  type ElementName = ElementName.T
  object ElementName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ElementText = ElementText.T
  object ElementText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The POM's own coordinates; a group or version it does not state is its parent's, as in Maven. */
  type CoordinatesResultPart1 = _root_.com.histmersion.pluginresolver.Resolver.Problem
  val CoordinatesResultPart1: _root_.com.histmersion.pluginresolver.Resolver.Problem.type = _root_.com.histmersion.pluginresolver.Resolver.Problem
  type CoordinatesXml = _root_.com.histmersion.pluginresolver.PluginArtifact.Pom
  val CoordinatesXml: _root_.com.histmersion.pluginresolver.PluginArtifact.Pom.type = _root_.com.histmersion.pluginresolver.PluginArtifact.Pom
  type OfResult = _root_.com.histmersion.pluginresolver.PluginArtifact.Pom
  val OfResult: _root_.com.histmersion.pluginresolver.PluginArtifact.Pom.type = _root_.com.histmersion.pluginresolver.PluginArtifact.Pom
  type EmbeddedResult = _root_.com.histmersion.pluginresolver.PluginArtifact.Pom
  val EmbeddedResult: _root_.com.histmersion.pluginresolver.PluginArtifact.Pom.type = _root_.com.histmersion.pluginresolver.PluginArtifact.Pom
  type ParseResultPart1 = _root_.com.histmersion.pluginresolver.Resolver.Problem
  val ParseResultPart1: _root_.com.histmersion.pluginresolver.Resolver.Problem.type = _root_.com.histmersion.pluginresolver.Resolver.Problem
  type ParseXml = _root_.com.histmersion.pluginresolver.PluginArtifact.Pom
  val ParseXml: _root_.com.histmersion.pluginresolver.PluginArtifact.Pom.type = _root_.com.histmersion.pluginresolver.PluginArtifact.Pom
  type TextName = _root_.com.histmersion.pluginresolver.Pom.ElementName
  val TextName: _root_.com.histmersion.pluginresolver.Pom.ElementName.type = _root_.com.histmersion.pluginresolver.Pom.ElementName

  def coordinates(xml: com.histmersion.pluginresolver.Pom.CoordinatesXml): Either[com.histmersion.pluginresolver.Pom.CoordinatesResultPart1, ModuleCoordinates] =
    for
      project <- parse(xml)
      parent = child(project, _root_.com.histmersion.pluginresolver.Pom.ElementName("parent"))
      group <- text(project, _root_.com.histmersion.pluginresolver.Pom.ElementName("groupId")).orElse(parent.flatMap(text(_, _root_.com.histmersion.pluginresolver.Pom.ElementName("groupId")))).toRight(_root_.com.histmersion.pluginresolver.Resolver.Problem("the POM names no groupId"))
      artifact <- text(project, _root_.com.histmersion.pluginresolver.Pom.ElementName("artifactId")).toRight(_root_.com.histmersion.pluginresolver.Resolver.Problem("the POM names no artifactId"))
      version <- text(project, _root_.com.histmersion.pluginresolver.Pom.ElementName("version")).orElse(parent.flatMap(text(_, _root_.com.histmersion.pluginresolver.Pom.ElementName("version")))).toRight(_root_.com.histmersion.pluginresolver.Resolver.Problem("the POM names no version"))
      coords <- ModuleCoordinates.parse(_root_.com.histmersion.pluginresolver.ModuleCoordinates.Text(s"$group:$artifact:$version"))
    yield coords

  /** The POM published beside a jar (`x-1.0.jar` → `x-1.0.pom`), else the one Maven's jar plugin puts in the jar when
    * it holds exactly one (`META-INF/maven/<group>/<artifact>/pom.xml`). A shaded jar holds several, of the libraries
    * shaded into it: no POM can be told to be the plugin's then.
    */
  def of(jar: Path): Option[com.histmersion.pluginresolver.Pom.OfResult] =
    val beside = besideJar(jar)
    if Files.isRegularFile(beside) then Some(_root_.com.histmersion.pluginresolver.Pom.OfResult(Files.readString(beside, UTF_8))) else embedded(jar)

  def besideJar(jar: Path): Path = jar.resolveSibling(jar.getFileName.toString.stripSuffix(".jar") + ".pom")

  private def embedded(jar: Path): Option[com.histmersion.pluginresolver.Pom.EmbeddedResult] =
    (Try {
      val zip = ZipFile(jar.toFile)
      try
        zip
          .entries()
          .asScala
          .filter(e => e.getName.startsWith("META-INF/maven/") && e.getName.endsWith("/pom.xml"))
          .toList match
          case List(only) => Some(String(zip.getInputStream(only).readAllBytes(), UTF_8))
          case _          => None
      finally zip.close()
    }.toOption.flatten[_root_.java.lang.String]).map(refactorValue => _root_.com.histmersion.pluginresolver.PluginArtifact.Pom(refactorValue))

  private def parse(xml: com.histmersion.pluginresolver.Pom.ParseXml): Either[com.histmersion.pluginresolver.Pom.ParseResultPart1, Element] =
    Try {
      val factory = DocumentBuilderFactory.newInstance()
      // a POM is data from the network: no DOCTYPE, so no external entity is ever resolved
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
      factory.setNamespaceAware(true)
      val builder = factory.newDocumentBuilder()
      // the parse error is the Left below; the parser's own printing to stderr is not wanted
      builder.setErrorHandler(new DefaultHandler)
      builder.parse(ByteArrayInputStream((xml).value.getBytes(UTF_8))).getDocumentElement
    }.toEither.left
      .map(e => _root_.com.histmersion.pluginresolver.Resolver.Problem(s"not a POM: ${e.getMessage}"))
      .filterOrElse(_.getLocalName == "project", _root_.com.histmersion.pluginresolver.Resolver.Problem("not a POM: no <project>"))

  private def child(e: Element, name: com.histmersion.pluginresolver.Pom.ElementName): Option[Element] =
    val nodes = e.getChildNodes
    (0 until nodes.getLength).map(nodes.item).collectFirst {
      case c: Element if c.getNodeType == Node.ELEMENT_NODE && c.getLocalName == (name).value => c
    }

  private def text(e: Element, name: com.histmersion.pluginresolver.Pom.TextName): Option[com.histmersion.pluginresolver.Pom.ElementText] =
    (child(e, name).map(_.getTextContent.trim).filter(_.nonEmpty)).map(refactorValue5 => _root_.com.histmersion.pluginresolver.Pom.ElementText(refactorValue5))
