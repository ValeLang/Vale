package net.verdagon.vale

object Pattern {
  val code =
    """
      |struct Location imm {
      |  groupX: Int;
      |  groupY: Int;
      |  indexInGroup: Int;
      |}
      |
      |struct Pattern imm {
      |  name: Str;
      |  cornersByShapeIndex: Array:(imm, Array<imm, Vec2>);
      |  patternTiles: Array<imm, PatternTile>;
      |  xOffset: Vec2;
      |  yOffset: Vec2;
      |}
      |
      |struct PatternTile imm {
      |  shapeIndex: Int;
      |  rotateRadians: Float;
      |  translate: Vec2;
      |  sideAdjacenciesBySideIndex: Array<imm, PatternSideAdjacency>;
      |  cornerAdjacenciesByCornerIndex: Array:(imm, Array<imm, PatternCornerAdjacency>);
      |}
      |
      |struct PatternSideAdjacency imm {
      |  groupRelativeX: Int;
      |  groupRelativeY: Int;
      |  tileIndex: Int;
      |  sideIndex: Int;
      |}
      |
      |struct PatternCornerAdjacency imm {
      |  groupRelativeX: Int;
      |  groupRelativeY: Int;
      |  tileIndex: Int;
      |  cornerIndex: Int;
      |}
      |
    """.stripMargin
}
