package com.histmersion.spike.cremabench

import java.util.function.Supplier

/** Plugin code calling JDK classes the host image never uses (java.util.zip.Adler32, the javax.xml parser): are they
  * there for a class loaded at run time?
  */
final class JdkUse extends Supplier[String]:
  def get(): String =
    val a = java.util.zip.Adler32()
    a.update("crema".getBytes("UTF-8"))
    val adler = s"adler32=${a.getValue}"
    val xml =
      try
        val doc = javax.xml.parsers.DocumentBuilderFactory
          .newInstance()
          .newDocumentBuilder()
          .parse(java.io.ByteArrayInputStream("<a><b>crema</b></a>".getBytes("UTF-8")))
        s"xml=${doc.getDocumentElement.getTextContent}"
      catch case e: Throwable => s"xml FAILED: $e"
    s"$adler $xml"
