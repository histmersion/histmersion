package com.histmersion.plugin

import hearth.kindlings.sconfigderivation.ConfigReader
import org.ekrich.config.ConfigFactory
import com.histmersion.plugin.Plugin.Config.given

final case class WithSecrets(token: Secret, password: Option[Secret], retries: Int, verbose: Boolean)
    derives ConfigReader

final class ExamplePlugin extends Plugin:
  type Config = WithSecrets
  val info = Plugin.Info(_root_.com.histmersion.plugin.Plugin.Info.Id("example"), _root_.com.histmersion.plugin.Plugin.Info.Version("0.1.0"), Spi.Version)
  def register(config: WithSecrets, ctx: Plugin.Context) = Right(())

class SecretSuite extends munit.FunSuite:

  test("the three reference forms parse"):
    assertEquals(Secret.Ref.parse(_root_.com.histmersion.plugin.Secret.Ref.ParseText("value:hunter2")), Right(Secret.Ref.Value(_root_.com.histmersion.plugin.Secret.Ref.Value.Literal("hunter2"))))
    assertEquals(Secret.Ref.parse(_root_.com.histmersion.plugin.Secret.Ref.ParseText("env:PORTAL_TOKEN")), Right(Secret.Ref.Env(_root_.com.histmersion.plugin.Secret.Ref.Env.Name("PORTAL_TOKEN"))))
    assertEquals(
      Secret.Ref.parse(_root_.com.histmersion.plugin.Secret.Ref.ParseText(" keepassxc:Histmersion/Portal#password ")),
      Right(Secret.Ref.Vault(_root_.com.histmersion.plugin.Secret.Ref.Vault.Value("keepassxc"), _root_.com.histmersion.plugin.Secret.Ref.Vault.Ref("Histmersion/Portal#password")))
    )
    assertEquals(
      Secret.Ref.parse(_root_.com.histmersion.plugin.Secret.Ref.ParseText("1password:op://Private/Portal/password")),
      Right(Secret.Ref.Vault(_root_.com.histmersion.plugin.Secret.Ref.Vault.Value("1password"), _root_.com.histmersion.plugin.Secret.Ref.Vault.Ref("op://Private/Portal/password")))
    )
    // a literal keeps its colons and its spaces inside
    assertEquals(Secret.Ref.parse(_root_.com.histmersion.plugin.Secret.Ref.ParseText("value:a:b c")), Right(Secret.Ref.Value(_root_.com.histmersion.plugin.Secret.Ref.Value.Literal("a:b c"))))

  test("a bare value, an empty part or a bad name is refused, and the error never echoes a value"):
    val bare = Secret.Ref.parse(_root_.com.histmersion.plugin.Secret.Ref.ParseText("hunter2"))
    assert(bare.left.exists(_.contains("not a bare value")), bare)
    assert(!bare.left.exists(_.contains("hunter2")))
    assert(Secret.Ref.parse(_root_.com.histmersion.plugin.Secret.Ref.ParseText("value:")).isLeft)
    assert(Secret.Ref.parse(_root_.com.histmersion.plugin.Secret.Ref.ParseText("env:")).isLeft)
    assert(Secret.Ref.parse(_root_.com.histmersion.plugin.Secret.Ref.ParseText("env:NOT-A-NAME")).isLeft)
    assert(Secret.Ref.parse(_root_.com.histmersion.plugin.Secret.Ref.ParseText("keepassxc:")).isLeft)
    assert(Secret.Ref.parse(_root_.com.histmersion.plugin.Secret.Ref.ParseText("Vault:x")).left.exists(_.contains("not a vault name")))
    assert(Secret.Ref.parse(_root_.com.histmersion.plugin.Secret.Ref.ParseText(":x")).isLeft)
    assert(Secret.Ref.parse(_root_.com.histmersion.plugin.Secret.Ref.ParseText(null)).isLeft)

  test("toString shows the reference, never a literal value"):
    assertEquals(Secret.parse(_root_.com.histmersion.plugin.Secret.Ref.ParseText("env:PORTAL_TOKEN")).map(_.toString), Right("Secret(env:PORTAL_TOKEN)"))
    assertEquals(Secret.parse(_root_.com.histmersion.plugin.Secret.Ref.ParseText("bitwarden:abc#password")).map(_.toString), Right("Secret(bitwarden:abc#password)"))
    val literal = Secret.parse(_root_.com.histmersion.plugin.Secret.Ref.ParseText("value:hunter2")).toOption.get
    assertEquals(literal.toString, "Secret(value:***)")
    assert(!s"$literal ${Some(literal)} ${List(literal)}".contains("hunter2"))
    assertEquals(Secret.parse(_root_.com.histmersion.plugin.Secret.Ref.ParseText("env:A")), Secret.parse(_root_.com.histmersion.plugin.Secret.Ref.ParseText("env:A")))

  test("the reader parses references inside a derived config; numbers and booleans may be strings"):
    val c =
      ConfigFactory.parseString("token = \"env:PORTAL_TOKEN\"\npassword = null\nretries = \"3\"\nverbose = \"true\"")
    assertEquals(
      ConfigReader[WithSecrets].from(c.root),
      Right(WithSecrets(Secret(Secret.Ref.Env(_root_.com.histmersion.plugin.Secret.Ref.Env.Name("PORTAL_TOKEN"))), None, 3, true))
    )
    val withPw = c.withValue("password", org.ekrich.config.ConfigValueFactory.fromAnyRef("keepassxc:a#b"))
    assertEquals(
      ConfigReader[WithSecrets].from(withPw.root).map(_.password),
      Right(Some(Secret(Secret.Ref.Vault(_root_.com.histmersion.plugin.Secret.Ref.Vault.Value("keepassxc"), _root_.com.histmersion.plugin.Secret.Ref.Vault.Ref("a#b")))))
    )

  test("a bare string where a secret belongs is a decoding error that does not repeat the value"):
    val c = ConfigFactory.parseString("token = hunter2\npassword = null\nretries = 1\nverbose = false")
    val r = ConfigReader[WithSecrets].from(c.root)
    assert(r.isLeft, r)
    assert(!r.left.toOption.get.toString.contains("hunter2"), r)

  test("a plugin's config type and reader are path-dependent members"):
    val p: Plugin = ExamplePlugin()
    val c = ConfigFactory.parseString("token = \"env:T\"\npassword = null\nretries = 2\nverbose = true")
    val typed: Either[?, p.Config] = p.configReader.from(c.root)
    assert(typed.isRight, typed)
    assertEquals(((p.defaultsResource).value: String), "META-INF/histmersion/plugins/example.conf")
