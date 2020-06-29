package net.verdagon.vale

object Vec {
  val code =
    """
      |struct Vec2 imm {
      |  x: Float;
      |  y: Float;
      |}
      |
      |struct Vec3 imm {
      |  x: Float;
      |  y: Float;
      |  z: Float;
      |}
      |
      |fn +(a: Vec2, b: Vec2) { Vec2(a.x + b.x, a.y + b.y) }
      |fn -(a: Vec2, b: Vec2) { Vec2(a.x - b.x, a.y - b.y) }
      |fn *(v: Vec2, f: Float) Vec2 { Vec2(v.x * f, v.y * f) }
      |fn *(v: Vec2, i: Int) Vec2 { v * Float(i) }
      |fn dot(a: Vec2, b: Vec2) { (a.x * b.x) + (a.y * b.y) }
      |fn distance(a: Vec2, b: Vec2) {
      |  sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y))
      |}
      |fn min(a: #T, b: #T) { = if (a < b) { a } else { b } }
      |fn max(a: #T, b: #T) { = if (a > b) { a } else { b } }
      |
      |fn minimums(a: Vec2, b: Vec2) { Vec2(min(a.x, b.x), min(a.y, b.y)) }
      |fn maximums(a: Vec2, b: Vec2) { Vec2(max(a.x, b.x), max(a.y, b.y)) }
      |
    """.stripMargin
}
