package com.histmersion
package api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A source collection the server can run: its name, title and description, what it lets the server do (`read`, `add`,
  * `update`), and whether the server can reach it now (`problem` says what is missing when it cannot).
  */
final case class CollectionInfo(
    name: CollectionInfo.Name,
    title: Title,
    description: CollectionInfo.Description,
    capabilities: List[CollectionInfo.Capabilities],
    reachable: CollectionInfo.Reachable,
    problem: Option[CollectionInfo.Problem]
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CollectionInfo:
  type Name = Name.T
  object Name extends RefinedType[String, Pure]
  type Description = Description.T
  object Description extends RefinedType[String, Pure]
  type Capabilities = Capabilities.T
  object Capabilities extends RefinedType[String, Pure]
  type Reachable = Reachable.T
  object Reachable extends RefinedType[Boolean, Pure]
  type Problem = Problem.T
  object Problem extends RefinedType[String, Pure]
