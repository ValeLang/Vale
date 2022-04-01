package dev.vale

object repeatStr {
  def apply(str: String, n: Int): String = {
    var result = "";
    (0 until n).foreach(i => {
      result = result + str
    })
    result
  }
}
