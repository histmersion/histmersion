package com.histmersion
package util.settings
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import com.histmersion.api.settings.ConfigEdit
import com.histmersion.config.edit.*
import com.histmersion.api.Api
import com.histmersion.api.settings.ReloadReport

/** Settings as the wire shows them ([[SettingShown]]: a credential's value never crosses). */
object SettingsViews:

  def page(s: SettingsSnapshot, restartable: Boolean): ConfigEdit.Page =
    val fields = s.readings.map(field)
    val sections = fields
      .map(_.section)
      .distinct
      .map(id =>
        ConfigEdit.Section(
          ConfigEdit.Section.Id(id.value),
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

  private def sectionDoc(id: String): String =
    SettingKey.either(s"${SettingKey.Root}.$id").toOption.fold("")(k => ConfigSchema.reference.blockDoc(k).value)

  def field(r: SettingReading): ConfigEdit.Field =
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
      s.locked.map(p => ConfigEdit.Field.Locked(p.sentence))
    )

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
        ConfigEdit.RestartNote(ConfigEdit.RestartNote.Key(k.value), Problem.Unclassified(Legacy.Text(why.value)))
      ),
      o.warnings.map(w => ConfigEdit.Written.Warnings(w.value)),
      reload,
      ConfigEdit.Written.Restartable(restartable)
    )

  def error(e: SettingError): Api.Error =
    def refusal(code: String) = Api.Error(e.message, Some(Api.Error.Code(code)))
    e match
      case SettingError.Changed                        => refusal(ConfigEdit.CodeChanged)
      case SettingError.NoFile(_) | SettingError.Io(_) => refusal(ConfigEdit.CodeUnavailable)
      case _                                           => refusal(ConfigEdit.CodeRefused)
