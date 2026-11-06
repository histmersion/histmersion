package com.histmersion.api.settings
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

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
  object Published extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Generation = Generation.T
  object Generation extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Applied = Applied.T
  object Applied extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RestartOnly = RestartOnly.T
  object RestartOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Rebuilt = Rebuilt.T
  object Rebuilt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Plugins = Plugins.T
  object Plugins extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Draining = Draining.T
  object Draining extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Errors = Errors.T
  object Errors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Warnings = Warnings.T
  object Warnings extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type File = File.T
  object File extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
