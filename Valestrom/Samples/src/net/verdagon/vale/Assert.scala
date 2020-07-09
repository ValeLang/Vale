package net.verdagon.vale

object Assert {
  val code =
    """
      |fn assert(cond bool) void {
      |  assert(cond, "Assertion failed!");
      |}
      |fn assert(cond bool, msg str) void {
      |  if (cond == false) {
      |    println(msg);
      |    panic();
      |  }
      |}
      |
      |fn assertEq<T>(a T, b T) void {
      |  assert(a == b, "Assertion failed, not equal!");
      |}
      |
      |fn assertEq<T>(a T, b T, msg str) void {
      |  assert(a == b, msg);
      |}
      |
    """.stripMargin
}
