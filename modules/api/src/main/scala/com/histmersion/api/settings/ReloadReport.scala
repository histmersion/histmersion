package com.histmersion
package api.settings
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class ReloadReport(
    published: ReloadReport.Published,
    generation: ReloadReport.Generation,
    applied: List[ReloadReport.Applied] = Nil,
    restartOnly: List[ReloadReport.RestartOnly] = Nil,
    rebuilt: List[ReloadReport.Rebuilt] = Nil,
    plugins: List[ReloadReport.Plugins] = Nil,
    draining: List[ReloadReport.Draining] = Nil,
    errors: List[ReloadReport.Errors] = Nil,
    warnings: List[ReloadReport.Warnings] = Nil,
    file: Option[ReloadReport.File] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def ok: Boolean = (errors.isEmpty)

object ReloadReport:

  type Published = Published.T
  object Published extends RefinedType[Boolean, Pure]
  type Generation = Generation.T
  object Generation extends RefinedType[Long, Pure]
  type Applied = Applied.T
  object Applied extends RefinedType[String, Pure]
  type RestartOnly = RestartOnly.T
  object RestartOnly extends RefinedType[String, Pure]
  type Rebuilt = Rebuilt.T
  object Rebuilt extends RefinedType[String, Pure]
  type Plugins = Plugins.T
  object Plugins extends RefinedType[String, Pure]
  type Draining = Draining.T
  object Draining extends RefinedType[String, Pure]
  type Errors = Errors.T
  object Errors extends RefinedType[String, Pure]
  type Warnings = Warnings.T
  object Warnings extends RefinedType[String, Pure]
  type File = File.T
  object File extends RefinedType[String, Pure]
