package com.histmersion
package pluginresolver

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
import io.github.iltotore.iron.constraint.any.Pure

/** The one thing read from a plugin's POM here: whose it is. Everything else in it (dependencies, properties, parents,
  * dependency management) is coursier's to read.
  */
object Pom:
  type ElementName = ElementName.T
  object ElementName extends Newtype[String, Pure]
  type ElementText = ElementText.T
  object ElementText extends Newtype[String, Pure]

  /** The POM's own coordinates; a group or version it does not state is its parent's, as in Maven. */
  export PluginArtifact.Pom as CoordinatesXml
  export PluginArtifact.Pom as ParseXml
  type TextName = ElementName
  val TextName: ElementName.type = ElementName

  def coordinates(xml: Pom.CoordinatesXml): Either[Problem, ModuleCoordinates] =
    for
      project <- parse(xml)
      parent = child(project, ElementName("parent"))
      group <- text(project, ElementName("groupId"))
        .orElse(parent.flatMap(text(_, ElementName("groupId"))))
        .toRight(Problem("the POM names no groupId"))
      artifact <- text(project, ElementName("artifactId")).toRight(Problem("the POM names no artifactId"))
      version <- text(project, ElementName("version"))
        .orElse(parent.flatMap(text(_, ElementName("version"))))
        .toRight(Problem("the POM names no version"))
      coords <- ModuleCoordinates.parse(ModuleCoordinates.Text(s"$group:$artifact:$version"))
    yield coords

  /** The POM published beside a jar (`x-1.0.jar` → `x-1.0.pom`), else the one Maven's jar plugin puts in the jar when
    * it holds exactly one (`META-INF/maven/<group>/<artifact>/pom.xml`). A shaded jar holds several, of the libraries
    * shaded into it: no POM can be told to be the plugin's then.
    */
  def of(jar: Path): Option[Pom.CoordinatesXml] =
    val beside = besideJar(jar)
    if Files.isRegularFile(beside) then Some(Pom.CoordinatesXml(Files.readString(beside, UTF_8))) else embedded(jar)

  def besideJar(jar: Path): Path = jar.resolveSibling(jar.getFileName.toString.stripSuffix(".jar") + ".pom")

  private def embedded(jar: Path): Option[Pom.CoordinatesXml] =
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
    }.toOption.flatten[String]).map(refactorValue => PluginArtifact.Pom(refactorValue))

  private def parse(xml: Pom.ParseXml): Either[Problem, Element] =
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
      .map(e => Problem(s"not a POM: ${e.getMessage}"))
      .filterOrElse(_.getLocalName == "project", Problem("not a POM: no <project>"))

  private def child(e: Element, name: ElementName): Option[Element] =
    val nodes = e.getChildNodes
    (0 until nodes.getLength).map(nodes.item).collectFirst {
      case c: Element if c.getNodeType == Node.ELEMENT_NODE && c.getLocalName == (name).value => c
    }

  private def text(e: Element, name: TextName): Option[ElementText] =
    (child(e, name).map(_.getTextContent.trim).filter(_.nonEmpty)).map(refactorValue5 => ElementText(refactorValue5))
