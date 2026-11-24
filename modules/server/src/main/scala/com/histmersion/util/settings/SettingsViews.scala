package com.histmersion
package util.settings
import com.histmersion.util.problem.Text.sentences

import com.histmersion.api.settings.ConfigEdit
import com.histmersion.config.{PgBytes, PgParameter}
import com.histmersion.config.edit.*
import com.histmersion.api.Api
import com.histmersion.api.settings.ReloadReport
import com.histmersion.lint.{LintRule, tolerated}

/** Settings as the wire shows them ([[SettingShown]]: a credential's value never crosses). */
object SettingsViews:

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def page(s: SettingsSnapshot, restartable: Boolean): ConfigEdit.Page =
    val fields = s.readings.map(field(_, s.automatic))
    val sections = fields
      .map(_.section)
      .distinct
      .map(id =>
        ConfigEdit.Section(
          id,
          doc = ConfigEdit.Section.Doc(sectionDoc(id.value)),
          fields.filter(_.section == id)
        )
      )
    ConfigEdit.Page(
      s.file.map(f => ConfigEdit.Page.File(f.value)),
      ConfigEdit.Page.Exists(s.exists.value),
      ConfigEdit.Page.Version(s.version.value),
      sections,
      s.problems.map(p => ConfigEdit.Page.Problems(p.value)),
      ConfigEdit.Page.Restartable(restartable),
      ConfigEdit.Page.SetupDue(s.setupDue.value)
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def sectionDoc(id: String): String =
    SettingKey.either(s"${SettingKey.Root}.$id").toOption.fold("")(k => ConfigSchema.reference.blockDoc(k).value)

  /** One setting as the wire shows it; `automatic` (the bundled Postgres' sizes set to `auto`, and what each comes to
    * on this machine) gives the setting it sizes its `resolved` value.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def field(r: SettingReading, automatic: List[(PgParameter, PgBytes)] = Nil): ConfigEdit.Field =
    val s = SettingShown.of(r)
    ConfigEdit.Field(
      ConfigEdit.Field.Key(s.key.value),
      ConfigEdit.Field.Section(s.section.value),
      ConfigEdit.Field.Kind(s.kind.value),
      ConfigEdit.Field.Doc(s.doc.value),
      s.default.map(d => ConfigEdit.Field.Default(d.value)),
      s.value.map(v => ConfigEdit.Field.Value(v.value)),
      s.effective.map(v => ConfigEdit.Field.Effective(v.value)),
      ConfigEdit.Field.Source(s.source.value),
      s.envVar.map(e => ConfigEdit.Field.EnvVar(e.value)),
      s.restart.map(x => ConfigEdit.Field.Restart(x.value)),
      s.credentialForm.map(form =>
        ConfigEdit.Credential(
          ConfigEdit.Credential.Rule(s.credentialRule.fold("reference")(_.value)),
          ConfigEdit.Credential.Form(form.value),
          s.credentialShown.map(x => ConfigEdit.Credential.Shown(x.value))
        )
      ),
      s.locked.map(p => ConfigEdit.Field.Locked(p.sentences.mkString("; "))),
      automatic.collectFirst {
        case (p, bytes) if p.key == r.field.key => ConfigEdit.Field.Resolved(s"${bytes.written}")
      }
    )

  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def written(
      o: WriteOutcome,
      reload: Option[ReloadReport],
      restartable: Boolean
  ): ConfigEdit.Written =
    ConfigEdit.Written(
      ConfigEdit.Written.File(o.file.value),
      o.changed.map(k => ConfigEdit.Written.Changed(k.value)),
      o.unchanged.map(k => ConfigEdit.Written.Unchanged(k.value)),
      o.backup.map(b => ConfigEdit.Written.Backup(b.value)),
      ConfigEdit.Written.Version(o.version.value),
      ConfigEdit.Written.Created(o.created.value),
      o.restart.map((k, why) =>
        ConfigEdit.RestartNote(ConfigEdit.RestartNote.Key(k.value), ConfigEdit.RestartNote.Why(why.value))
      ),
      o.warnings.map(w => ConfigEdit.Written.Warnings(w.value)),
      reload,
      ConfigEdit.Written.Restartable(restartable)
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def error(e: SettingError): Api.Error =
    def refusal(code: String) = Api.Error(e.message, Some(Api.Error.Code(code)))
    e match
      case SettingError.Changed(_, _)                  => refusal(ConfigEdit.CodeChanged)
      case SettingError.NoFile(_) | SettingError.Io(_) => refusal(ConfigEdit.CodeUnavailable)
      case _                                           => refusal(ConfigEdit.CodeRefused)
