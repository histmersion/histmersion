package com.histmersion.api

/** Diacritic folding as a lookup table: this code is also compiled to Scala.js, which has no `java.text.Normalizer`.
  * The single-letter part was generated from Unicode NFD decompositions; the letters NFD does not decompose (`ß`, `æ`,
  * `ø`, `ł`, `đ`, `þ`, …) are mapped by hand.
  */
object LatinFold:

  type Pairs = PairsValue
  type PairsValue = PairsValue.T
  object PairsValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FoldCharC = Char

  private val Pairs: com.histmersion.api.LatinFold.Pairs =
    // Latin-1 Supplement
    _root_.com.histmersion.api.LatinFold.PairsValue("ÀAÁAÂAÃAÄAÅAÇCÈEÉEÊEËEÌIÍIÎIÏIÐDÑNÒOÓOÔOÕOÖOØOÙUÚUÛUÜUÝYàaáaâaãaäaåaçcèeéeêeëeìi" +
      "íiîiïiðdñnòoóoôoõoöoøoùuúuûuüuýyÿy" +
      // Latin Extended-A
      "ĀAāaĂAăaĄAąaĆCćcĈCĉcĊCċcČCčcĎDďdĐDđdĒEēeĔEĕeĖEėeĘEęeĚEěeĜGĝgĞGğgĠGġgĢGģgĤHĥhĦHħh" +
      "ĨIĩiĪIīiĬIĭiĮIįiİIıiĴJĵjĶKķkĸqĹLĺlĻLļlĽLľlĿLŀlŁLłlŃNńnŅNņnŇNňnŉnŊNŋnŌOōoŎOŏoŐOőo" +
      "ŔRŕrŖRŗrŘRřrŚSśsŜSŝsŞSşsŠSšsŢTţtŤTťtŦTŧtŨUũuŪUūuŬUŭuŮUůuŰUűuŲUųuŴWŵwŶYŷyŸYŹZźzŻZ" +
      "żzŽZžzſs" +
      // Latin Extended-B
      "ƀbƁBƇCƈcƉDƊDƑFƒfƗIƘKƙkƚlƝNƞnƟOƠOơoƤPƥpƫtƬTƭtƮTƯUưuƲVƳYƴyƵZƶzǍAǎaǏIǐiǑOǒoǓUǔuǕUǖu" +
      "ǗUǘuǙUǚuǛUǜuǝeǞAǟaǠAǡaǤGǥgǦGǧgǨKǩkǪOǫoǬOǭoǰjǴGǵgǸNǹnǺAǻaǾOǿoȀAȁaȂAȃaȄEȅeȆEȇeȈIȉi" +
      "ȊIȋiȌOȍoȎOȏoȐRȑrȒRȓrȔUȕuȖUȗuȘSșsȚTțtȞHȟhȡdȤZȥzȦAȧaȨEȩeȪOȫoȬOȭoȮOȯoȰOȱoȲYȳyȴlȵnȶt" +
      "ȷjȺAȻCȼcȽLȾTȿsɀzɃBɄUɆEɇeɈJɉjɌRɍrɎYɏy" +
      // Latin Extended Additional
      "ḀAḁaḂBḃbḄBḅbḆBḇbḈCḉcḊDḋdḌDḍdḎDḏdḐDḑdḒDḓdḔEḕeḖEḗeḘEḙeḚEḛeḜEḝeḞFḟfḠGḡgḢHḣhḤHḥhḦHḧh" +
      "ḨHḩhḪHḫhḬIḭiḮIḯiḰKḱkḲKḳkḴKḵkḶLḷlḸLḹlḺLḻlḼLḽlḾMḿmṀMṁmṂMṃmṄNṅnṆNṇnṈNṉnṊNṋnṌOṍoṎOṏo" +
      "ṐOṑoṒOṓoṔPṕpṖPṗpṘRṙrṚRṛrṜRṝrṞRṟrṠSṡsṢSṣsṤSṥsṦSṧsṨSṩsṪTṫtṬTṭtṮTṯtṰTṱtṲUṳuṴUṵuṶUṷu" +
      "ṸUṹuṺUṻuṼVṽvṾVṿvẀWẁwẂWẃwẄWẅwẆWẇwẈWẉwẊXẋxẌXẍxẎYẏyẐZẑzẒZẓzẔZẕzẖhẗtẘwẙyẜsẝsẠAạaẢAảa" +
      "ẤAấaẦAầaẨAẩaẪAẫaẬAậaẮAắaẰAằaẲAẳaẴAẵaẶAặaẸEẹeẺEẻeẼEẽeẾEếeỀEềeỂEểeỄEễeỆEệeỈIỉiỊIịi" +
      "ỌOọoỎOỏoỐOốoỒOồoỔOổoỖOỗoỘOộoỚOớoỜOờoỞOởoỠOỡoỢOợoỤUụuỦUủuỨUứuỪUừuỬUửuỮUữuỰUựuỲYỳy" +
      "ỴYỵyỶYỷyỸYỹy")

  /** The transliteration half rings (`ʿ` ayin, `ʾ` aleph) fold to nothing: `Baʿal` → `Baal`. */
  private val Multi: Map[Char, String] = Map(
    { val refactorTuple31 = '\u02bf' -> ""; ((refactorTuple31._1), (refactorTuple31._2)) },
    { val refactorTuple32 = '\u02be' -> ""; ((refactorTuple32._1), (refactorTuple32._2)) },
    { val refactorTuple3 = 'Æ' -> "AE"; ((refactorTuple3._1), (refactorTuple3._2)) },
    { val refactorTuple34 = 'Þ' -> "TH"; ((refactorTuple34._1), (refactorTuple34._2)) },
    { val refactorTuple5 = 'ß' -> "ss"; ((refactorTuple5._1), (refactorTuple5._2)) },
    { val refactorTuple6 = 'æ' -> "ae"; ((refactorTuple6._1), (refactorTuple6._2)) },
    { val refactorTuple37 = 'þ' -> "th"; ((refactorTuple37._1), (refactorTuple37._2)) },
    { val refactorTuple38 = 'Ĳ' -> "IJ"; ((refactorTuple38._1), (refactorTuple38._2)) },
    { val refactorTuple9 = 'ĳ' -> "ij"; ((refactorTuple9._1), (refactorTuple9._2)) },
    { val refactorTuple40 = 'Œ' -> "OE"; ((refactorTuple40._1), (refactorTuple40._2)) },
    { val refactorTuple41 = 'œ' -> "oe"; ((refactorTuple41._1), (refactorTuple41._2)) },
    { val refactorTuple42 = 'Ǆ' -> "DZ"; ((refactorTuple42._1), (refactorTuple42._2)) },
    { val refactorTuple43 = 'ǅ' -> "Dz"; ((refactorTuple43._1), (refactorTuple43._2)) },
    { val refactorTuple14 = 'ǆ' -> "dz"; ((refactorTuple14._1), (refactorTuple14._2)) },
    { val refactorTuple15 = 'Ǉ' -> "LJ"; ((refactorTuple15._1), (refactorTuple15._2)) },
    { val refactorTuple46 = 'ǈ' -> "Lj"; ((refactorTuple46._1), (refactorTuple46._2)) },
    { val refactorTuple17 = 'ǉ' -> "lj"; ((refactorTuple17._1), (refactorTuple17._2)) },
    { val refactorTuple48 = 'Ǌ' -> "NJ"; ((refactorTuple48._1), (refactorTuple48._2)) },
    { val refactorTuple19 = 'ǋ' -> "Nj"; ((refactorTuple19._1), (refactorTuple19._2)) },
    { val refactorTuple50 = 'ǌ' -> "nj"; ((refactorTuple50._1), (refactorTuple50._2)) },
    { val refactorTuple21 = 'Ǣ' -> "AE"; ((refactorTuple21._1), (refactorTuple21._2)) },
    { val refactorTuple22 = 'ǣ' -> "ae"; ((refactorTuple22._1), (refactorTuple22._2)) },
    { val refactorTuple23 = 'Ǳ' -> "DZ"; ((refactorTuple23._1), (refactorTuple23._2)) },
    { val refactorTuple54 = 'ǲ' -> "Dz"; ((refactorTuple54._1), (refactorTuple54._2)) },
    { val refactorTuple55 = 'ǳ' -> "dz"; ((refactorTuple55._1), (refactorTuple55._2)) },
    { val refactorTuple56 = 'Ǽ' -> "AE"; ((refactorTuple56._1), (refactorTuple56._2)) },
    { val refactorTuple57 = 'ǽ' -> "ae"; ((refactorTuple57._1), (refactorTuple57._2)) },
    { val refactorTuple28 = 'ẞ' -> "SS"; ((refactorTuple28._1), (refactorTuple28._2)) },
    { val refactorTuple59 = 'Ỻ' -> "LL"; ((refactorTuple59._1), (refactorTuple59._2)) },
    { val refactorTuple30 = 'ỻ' -> "ll"; ((refactorTuple30._1), (refactorTuple30._2)) }
  )

  private val Table: Map[Char, String] =
    (Pairs).value.grouped(2).map(p => p(0) -> p(1).toString).toMap ++ Multi

  def tableChars: Set[Char] = (Table.keySet).map(refactorValue => ((refactorValue).value))

  private def isCombiningMark(c: Char): Boolean = c >= '\u0300' && c <= '\u036f'

  def foldChar(c: com.histmersion.api.LatinFold.FoldCharC): String =
    if ((isCombiningMark(c)) == (true)) then ("") else Table.getOrElse(((c).value), c.toString)

  def fold(s: String): String =
    val b = new StringBuilder((s).value.length)
    (s).value.foreach ({ c =>
      if !(isCombiningMark((c))).value then
        Table.get((c)) match
          case Some(r) => b ++= r
          case None    => b += c
    }).value
    (b.result())
