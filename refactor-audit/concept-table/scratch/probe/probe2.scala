//> using scala 3.10.0
//> using dep com.kubuszok::kindlings-jsoniter-derivation:0.3.3
//> using dep com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-core:2.41.5
import hearth.kindlings.jsoniterderivation.*
import com.github.plokhotnyuk.jsoniter_scala.core.*

final case class F(q: Option[String] = None, l: List[Int] = Nil)
final case class G(q: Option[String] = None)
final case class H(a: Int = 1, b: String = "x")

@main def run(): Unit =
  val cfgs = List(
    "default" -> JsoniterConfig.default,
    "circeLike" -> JsoniterConfig.default.withCirceLikeObjectEncoding,
    "tNone" -> JsoniterConfig.default.withTransientNone,
    "tDefault" -> JsoniterConfig.default.withTransientDefault,
    "both+dec" -> JsoniterConfig.default.withCirceLikeObjectEncoding.withTransientNone.withTransientDefault.withDecodingOnly
  )
  for (n, c) <- cfgs do
    given JsoniterConfig = c
    val cf = KindlingsJsonValueCodec.derived[F]
    val cg = KindlingsJsonValueCodec.derived[G]
    val ch = KindlingsJsonValueCodec.derived[H]
    println(s"$n F ${scala.util.Try(readFromString("{}")(cf))} G ${scala.util.Try(readFromString("{}")(cg))} H ${scala.util.Try(readFromString("{}")(ch))} H2 ${scala.util.Try(readFromString(" { } ")(ch))}")
