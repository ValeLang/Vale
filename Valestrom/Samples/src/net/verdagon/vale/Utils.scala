package net.verdagon.vale

object Utils {
  val code =
    """
      |fn panic(msg: Str) {
      |  println(msg);
      |  = panic();
      |}
      |
      |fn abs(a: Int) {
      |  = if (a < 0) { a * -1 } else { a }
      |}
      |
    """.stripMargin
}
