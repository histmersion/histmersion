package com.histmersion.api.settings
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.Write
import com.histmersion.api.library.Source
import com.histmersion.api.given

object ConfigEdit:

  enum Source(val wire: Source.Wire, val label: Source.Label):
    case Default extends Source(_root_.com.histmersion.api.settings.ConfigEdit.Source.Wire("default"), _root_.com.histmersion.api.settings.ConfigEdit.Source.Label("the default"))
    case File extends Source(_root_.com.histmersion.api.settings.ConfigEdit.Source.Wire("file"), _root_.com.histmersion.api.settings.ConfigEdit.Source.Label("the settings file"))
    case Environment extends Source(_root_.com.histmersion.api.settings.ConfigEdit.Source.Wire("environment"), _root_.com.histmersion.api.settings.ConfigEdit.Source.Label("an environment variable"))

  object Source:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.settings.ConfigEdit.Source.ParseS): Option[Source] = values.find(_.wire == (s).value.trim.toLowerCase)

  final case class Credential(rule: Credential.Rule, form: Credential.Form, shown: Option[Credential.Shown] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema:
    def isSet: com.histmersion.api.settings.ConfigEdit.Credential.IsSetResult = form != "unset"

  object Credential:
    type IsSetResult = IsSetResult.T
    object IsSetResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Rule = Rule.T
    object Rule extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Form = Form.T
    object Form extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Shown = Shown.T
    object Shown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Field(
      key: Field.Key,
      section: Field.Section,
      kind: Field.Kind,
      doc: Field.Doc = _root_.com.histmersion.api.settings.ConfigEdit.Field.Doc(""),
      default: Option[Field.Default] = None,
      value: Option[Field.Value] = None,
      effective: Option[Field.Effective] = None,
      source: Field.Source = Source.Default.wire,
      envVar: Option[Field.EnvVar] = None,
      restart: Option[Field.Restart] = None,
      credential: Option[Credential] = None,
      locked: Option[Field.Locked] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    def relative: com.histmersion.api.settings.ConfigEdit.Field.RelativeResult = _root_.com.histmersion.api.settings.ConfigEdit.Field.RelativeResult((key).value.stripPrefix("histmersion."))

  object Field:
    type RelativeResult = RelativeResult.T
    object RelativeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Key = Key.T
    object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Section = Section.T
    object Section extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Doc = Doc.T
    object Doc extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Default = Default.T
    object Default extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Effective = Effective.T
    object Effective extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Source = Source.T
    object Source extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type EnvVar = EnvVar.T
    object EnvVar extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Restart = Restart.T
    object Restart extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Locked = Locked.T
    object Locked extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Section(id: Section.Id, doc: Section.Doc = _root_.com.histmersion.api.settings.ConfigEdit.Section.Doc(""), fields: List[Field] = Nil)
      derives KindlingsCodecAsObject,
        KindlingsSchema

  object Section:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Doc = Doc.T
    object Doc extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Page(
      file: Option[Page.File] = None,
      exists: Page.Exists = _root_.com.histmersion.api.settings.ConfigEdit.Page.Exists(false),
      version: Page.Version = _root_.com.histmersion.api.settings.ConfigEdit.Page.Version("none"),
      sections: List[Section] = Nil,
      problems: List[Page.Problems] = Nil,
      restartable: Page.Restartable = _root_.com.histmersion.api.settings.ConfigEdit.Page.Restartable(false),
      setupDue: Page.SetupDue = _root_.com.histmersion.api.settings.ConfigEdit.Page.SetupDue(false)
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def fields: List[Field] = sections.flatMap(_.fields)
    def field(key: com.histmersion.api.settings.ConfigEdit.Page.FieldKey): Option[Field] =
      val full = if (key).value.startsWith("histmersion.") then key else s"histmersion.$key"
      fields.find(_.key == full)

  object Page:
    type FieldKey = FieldKey.T
    object FieldKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type File = File.T
    object File extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Exists = Exists.T
    object Exists extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Version = Version.T
    object Version extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Problems = Problems.T
    object Problems extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Restartable = Restartable.T
    object Restartable extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SetupDue = SetupDue.T
    object SetupDue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]



  final case class Change(key: Change.Key, value: Option[Change.Value] = None) derives KindlingsCodecAsObject, KindlingsSchema

  object Change:
    type Key = Key.T
    object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Write(
      changes: List[Change],
      version: Option[Write.Version] = None,
      literalConsent: Write.LiteralConsent = _root_.com.histmersion.api.settings.ConfigEdit.Write.LiteralConsent(false),
      reload: Write.Reload = _root_.com.histmersion.api.settings.ConfigEdit.Write.Reload(true)
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Write:
    type Version = Version.T
    object Version extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LiteralConsent = LiteralConsent.T
    object LiteralConsent extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Reload = Reload.T
    object Reload extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]



  final case class RestartNote(key: RestartNote.Key, why: RestartNote.Why) derives KindlingsCodecAsObject, KindlingsSchema

  object RestartNote:
    type Key = Key.T
    object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Written(
      file: Written.File,
      changed: List[Written.Changed] = Nil,
      unchanged: List[Written.Unchanged] = Nil,
      backup: Option[Written.Backup] = None,
      version: Written.Version = _root_.com.histmersion.api.settings.ConfigEdit.Written.Version("none"),
      created: Written.Created = _root_.com.histmersion.api.settings.ConfigEdit.Written.Created(false),
      restart: List[RestartNote] = Nil,
      warnings: List[Written.Warnings] = Nil,
      reload: Option[ReloadReport] = None,
      restartable: Written.Restartable = _root_.com.histmersion.api.settings.ConfigEdit.Written.Restartable(false)
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Written:
    type File = File.T
    object File extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Changed = Changed.T
    object Changed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Unchanged = Unchanged.T
    object Unchanged extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Backup = Backup.T
    object Backup extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Version = Version.T
    object Version extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Created = Created.T
    object Created extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Warnings = Warnings.T
    object Warnings extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Restartable = Restartable.T
    object Restartable extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]



  final case class Restart(accepted: Restart.Accepted, message: Restart.Message) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object Restart:
    type Accepted = Accepted.T
    object Accepted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Message = Message.T
    object Message extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]



  val CodeChanged = "settings_changed"

  val CodeRefused = "settings_refused"

  val CodeUnavailable = "settings_unavailable"
