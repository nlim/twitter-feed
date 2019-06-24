object UnicodeUtils {
  def getUnicodeString(e: EmojiDefinition): Option[Char] = {
    val codepoints = e.unified.split("-")
    val ints = codepoints.map(a => Integer.parseInt(a, 16))
    val string = new String(ints, 0, ints.length)
    if (string.length > 0) Option(string.charAt(0)) else None
  }

  /*
  scala> val codepoints = List("26F9-FE0F-200D-2642-FE0F", "0023-FE0F-20E3")
  codepoints: List[String] = List(26F9-FE0F-200D-2642-FE0F, 0023-FE0F-20E3)

  scala> val cs = codepoints.map(_.split("-"))
  cs: List[Array[String]] = List(Array(26F9, FE0F, 200D, 2642, FE0F), Array(0023, FE0F, 20E3))

  scala> val ints = cs.map(arr => arr.map(a => Integer.parseInt(a, 16)))
  ints: List[Array[Int]] = List(Array(9977, 65039, 8205, 9794, 65039), Array(35, 65039, 8419))

  scala> ints.map(arr => new String(arr, 0, arr.length))
  res42: List[String] = List(⛹️‍♂️, #️⃣)
  */

}
