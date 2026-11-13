//> using scala 3.10.0
//> using dep com.kubuszok::kindlings-jsoniter-derivation:0.3.3
//> using dep com.github.plokhotnyuk.jsoniter-scala::jsoniter-scala-core:2.41.5
import hearth.kindlings.jsoniterderivation.*
import com.github.plokhotnyuk.jsoniter_scala.core.*

trait JsonCodec[A] extends JsonValueCodec[A]
object JsonCodec:
  val reading: JsoniterConfig = JsoniterConfig.default.withCirceLikeObjectEncoding.withTransientNone.withTransientDefault.withDecodingOnly
  val writing: JsoniterConfig = JsoniterConfig.default.withCirceLikeObjectEncoding.withEncodingOnly
  inline def derived[A]: JsonCodec[A] =
    val reads = KindlingsJsonValueCodec.derived[A](using reading)
    val writes = KindlingsJsonValueCodec.derived[A](using writing)
    val check: A => Option[String] = compiletime.summonFrom {
      case ev: (A <:< Product) =>
        (a: A) => { val p = ev(a); p.productIterator.zipWithIndex.collectFirst { case (null, i) => p.productElementName(i) } }
      case _ => (_: A) => None
    }
    new JsonCodec[A]:
      def nullValue: A = reads.nullValue
      def decodeValue(in: JsonReader, default: A): A =
        val a = reads.decodeValue(in, default)
        check(a).foreach(f => in.decodeError(s"missing required field: $f"))
        a
      def encodeValue(x: A, out: JsonWriter): Unit = writes.encodeValue(x, out)

enum Color derives JsonCodec:
  case Red, Green
  case Mixed(a: Int)
final case class Tree(v: Int, kids: List[Tree]) derives JsonCodec
final case class In(x: String) derives JsonCodec
final case class R(s: String, d: Int = 7, o: Option[String], l: List[Int], m: Map[String, Int], c: Color = Color.Red, i: Option[In] = None, t: Option[Tree] = None) derives JsonCodec

@main def run(): Unit =
  println(writeToString(R("x", 1, None, Nil, Map("a" -> 1), Color.Green, Some(In("q")), Some(Tree(1, List(Tree(2, Nil)))))))
  for j <- List("""{"s":"x","l":[],"m":{}}""", """{"s":"x","o":null,"l":[],"m":{}}""", """{"l":[],"m":{}}""", """{"s":"x","m":{}}""", """{"s":"x","l":[],"m":{},"i":{}}""","""{"s":"x","l":[],"m":{},"c":{"Mixed":{"a":2}},"t":{"v":1,"kids":[{"v":2,"kids":[]}]}}""", """{"s":"x","l":[],"m":{},"c":{"Mixed":{}}}""") do
    println(j + " -> " + scala.util.Try(readFromString[R](j, ReaderConfig.withAppendHexDumpToParseException(false))))
