package net.verdagon.vale

object PentagonPattern9 {
  val code =
    """
      |fn makePentagonPattern9() {
      |  Pattern(
      |    "pentagon9",
      |    toArray<imm>([ // cornersByShapeIndex
      |      toArray<imm>([ // corner for shape 0
      |        Vec2(-0.1322435067, -0.4965076715),
      |        Vec2(0.3777818011, -0.4965076715),
      |        Vec2(0.7323878613, 0.3125858406),
      |        Vec2(-0.1300622573, 0.50379034),
      |        Vec2(-0.8551640757, -0.0007977013127)
      |      ]),
      |      toArray<imm>([ // corner for shape 1
      |        Vec2(0.1322435067, -0.4965076715),
      |        Vec2(0.8551640757, -0.0007977013127),
      |        Vec2(0.1300622573, 0.50379034),
      |        Vec2(-0.7323878613, 0.3125858406),
      |        Vec2(-0.3777818011, -0.4965076715)
      |      ])
      |    ]),
      |    toArray<imm>([ // patternTiles
      |      PatternTile(
      |        0, 0.0, Vec2(0.0, 0.0),
      |        toArray<imm>([ // sideAdjacenciesBySideIndex
      |          PatternSideAdjacency(0, -1, 6, 4),
      |          PatternSideAdjacency(1, 0, 1, 1),
      |          PatternSideAdjacency(0, 0, 2, 1),
      |          PatternSideAdjacency(0, 0, 1, 3),
      |          PatternSideAdjacency(-1, -1, 7, 1)
      |        ]),
      |        toArray<imm>([ // cornerAdjacenciesByCornerIndex
      |          toArray<imm>([ // corner 0
      |            PatternCornerAdjacency(-1, -1, 7, 1),
      |            PatternCornerAdjacency(0, -1, 6, 0)
      |          ]),
      |          toArray<imm>([ // corner 1
      |            PatternCornerAdjacency(0, -1, 6, 4),
      |            PatternCornerAdjacency(1, 0, 1, 2)
      |          ]),
      |          toArray<imm>([ // corner 2
      |            PatternCornerAdjacency(0, 0, 2, 2),
      |            PatternCornerAdjacency(1, 0, 1, 1),
      |            PatternCornerAdjacency(1, 0, 3, 3)
      |          ]),
      |          toArray<imm>([ // corner 3
      |            PatternCornerAdjacency(0, 0, 1, 4),
      |            PatternCornerAdjacency(0, 0, 2, 1)
      |          ]),
      |          toArray<imm>([ // corner 4
      |            PatternCornerAdjacency(0, 0, 1, 3),
      |            PatternCornerAdjacency(-1, -1, 6, 3),
      |            PatternCornerAdjacency(-1, -1, 7, 2)
      |          ])
      |        ])
      |      ),
      |      PatternTile( // tile 1
      |        1, 101.5, Vec2(-0.6966363824, 0.773766964),
      |        toArray<imm>([ // sideAdjacenciesBySideIndex
      |          PatternSideAdjacency(0, 0, 3, 3),
      |          PatternSideAdjacency(-1, 0, 0, 1),
      |          PatternSideAdjacency(-1, -1, 6, 3),
      |          PatternSideAdjacency(0, 0, 0, 3),
      |          PatternSideAdjacency(0, 0, 2, 0)
      |        ]),
      |        toArray<imm>([ // cornerAdjacenciesByCornerIndex
      |          toArray<imm>([ // corner 0
      |            PatternCornerAdjacency(0, 0, 2, 0),
      |            PatternCornerAdjacency(0, 0, 3, 4)
      |          ]),
      |          toArray<imm>([ // corner 4
      |            PatternCornerAdjacency(-1, 0, 0, 2),
      |            PatternCornerAdjacency(-1, 0, 2, 2),
      |            PatternCornerAdjacency(0, 0, 3, 3)
      |          ]),
      |          toArray<imm>([ // corner 3
      |            PatternCornerAdjacency(-1, -1, 6, 4),
      |            PatternCornerAdjacency(-1, 0, 0, 1)
      |          ]),
      |          toArray<imm>([ // corner 2
      |            PatternCornerAdjacency(0, 0, 0, 4),
      |            PatternCornerAdjacency(-1, -1, 7, 2),
      |            PatternCornerAdjacency(-1, -1, 6, 3)
      |          ]),
      |          toArray<imm>([ // corner 1
      |            PatternCornerAdjacency(0, 0, 0, 3),
      |            PatternCornerAdjacency(0, 0, 2, 1)
      |          ])
      |        ])
      |      ),
      |      PatternTile( // tile 2
      |        0, 281.5, Vec2(0.2738541546, 0.971740549),
      |        toArray<imm>([ // sideAdjacenciesBySideIndex
      |          PatternSideAdjacency(0, 0, 1, 4),
      |          PatternSideAdjacency(0, 0, 0, 2),
      |          PatternSideAdjacency(1, 0, 3, 2),
      |          PatternSideAdjacency(0, 0, 4, 0),
      |          PatternSideAdjacency(0, 0, 3, 4)
      |        ]),
      |        toArray<imm>([ // cornerAdjacenciesByCornerIndex
      |          toArray<imm>([ // corner 0
      |            PatternCornerAdjacency(0, 0, 1, 0),
      |            PatternCornerAdjacency(0, 0, 3, 4)
      |          ]),
      |          toArray<imm>([ // corner 1
      |            PatternCornerAdjacency(0, 0, 0, 3),
      |            PatternCornerAdjacency(0, 0, 1, 4)
      |          ]),
      |          toArray<imm>([ // corner 2
      |            PatternCornerAdjacency(0, 0, 0, 2),
      |            PatternCornerAdjacency(1, 0, 1, 1),
      |            PatternCornerAdjacency(1, 0, 3, 3)
      |          ]),
      |          toArray<imm>([ // corner 3
      |            PatternCornerAdjacency(0, 0, 4, 1),
      |            PatternCornerAdjacency(1, 0, 3, 2),
      |            PatternCornerAdjacency(1, 0, 5, 2)
      |          ]),
      |          toArray<imm>([ // corner 4
      |            PatternCornerAdjacency(0, 0, 3, 0),
      |            PatternCornerAdjacency(0, 0, 4, 0)
      |          ])
      |        ])
      |      ),
      |      PatternTile( // tile 3
      |        0, 461.5, Vec2(-0.4063642295, 1.846307043),
      |        toArray<imm>([ // sideAdjacenciesBySideIndex
      |          PatternSideAdjacency(0, 0, 4, 4),
      |          PatternSideAdjacency(0, 0, 5, 2),
      |          PatternSideAdjacency(-1, 0, 2, 2),
      |          PatternSideAdjacency(0, 0, 1, 0),
      |          PatternSideAdjacency(0, 0, 2, 4)
      |        ]),
      |        toArray<imm>([ // cornerAdjacenciesByCornerIndex
      |          toArray<imm>([ // corner 0
      |            PatternCornerAdjacency(0, 0, 2, 4),
      |            PatternCornerAdjacency(0, 0, 4, 0)
      |          ]),
      |          toArray<imm>([ // corner 1
      |            PatternCornerAdjacency(0, 0, 4, 4),
      |            PatternCornerAdjacency(0, 0, 5, 3)
      |          ]),
      |          toArray<imm>([ // corner 2
      |            PatternCornerAdjacency(-1, 0, 2, 3),
      |            PatternCornerAdjacency(-1, 0, 4, 1),
      |            PatternCornerAdjacency(0, 0, 5, 2)
      |          ]),
      |          toArray<imm>([ // corner 3
      |            PatternCornerAdjacency(0, 0, 1, 1),
      |            PatternCornerAdjacency(-1, 0, 0, 2),
      |            PatternCornerAdjacency(-1, 0, 2, 2)
      |          ]),
      |          toArray<imm>([ // corner 4
      |            PatternCornerAdjacency(0, 0, 1, 0),
      |            PatternCornerAdjacency(0, 0, 2, 0)
      |          ])
      |        ])
      |      ),
      |      PatternTile( // tile 4
      |        1, 281.5, Vec2(0.5729603125, 2.028195672),
      |        toArray<imm>([ // sideAdjacenciesBySideIndex
      |          PatternSideAdjacency(0, 0, 2, 3),
      |          PatternSideAdjacency(1, 0, 5, 1),
      |          PatternSideAdjacency(0, 0, 7, 3),
      |          PatternSideAdjacency(0, 0, 5, 3),
      |          PatternSideAdjacency(0, 0, 3, 0)
      |        ]),
      |        toArray<imm>([ // cornerAdjacenciesByCornerIndex
      |          toArray<imm>([ // corner 0
      |            PatternCornerAdjacency(0, 0, 2, 4),
      |            PatternCornerAdjacency(0, 0, 3, 0)
      |          ]),
      |          toArray<imm>([ // corner 4
      |            PatternCornerAdjacency(0, 0, 2, 3),
      |            PatternCornerAdjacency(1, 0, 3, 2),
      |            PatternCornerAdjacency(1, 0, 5, 2)
      |          ]),
      |          toArray<imm>([ // corner 3
      |            PatternCornerAdjacency(0, 0, 7, 4),
      |            PatternCornerAdjacency(1, 0, 5, 1)
      |          ]),
      |          toArray<imm>([ // corner 2
      |            PatternCornerAdjacency(0, 0, 5, 4),
      |            PatternCornerAdjacency(0, 0, 6, 2),
      |            PatternCornerAdjacency(0, 0, 7, 3)
      |          ]),
      |          toArray<imm>([ // corner 1
      |            PatternCornerAdjacency(0, 0, 3, 1),
      |            PatternCornerAdjacency(0, 0, 5, 3)
      |          ])
      |        ])
      |      ),
      |      PatternTile( // tile 5
      |        0, 180.0, Vec2(-0.1148420649, 2.817164191),
      |        toArray<imm>([ // sideAdjacenciesBySideIndex
      |          PatternSideAdjacency(-1, 0, 7, 4),
      |          PatternSideAdjacency(-1, 0, 4, 1),
      |          PatternSideAdjacency(0, 0, 3, 1),
      |          PatternSideAdjacency(0, 0, 4, 3),
      |          PatternSideAdjacency(0, 0, 6, 1)
      |        ]),
      |        toArray<imm>([ // cornerAdjacenciesByCornerIndex
      |          toArray<imm>([ // corner 0
      |            PatternCornerAdjacency(-1, 0, 7, 0),
      |            PatternCornerAdjacency(0, 0, 6, 1)
      |          ]),
      |          toArray<imm>([ // corner 1
      |            PatternCornerAdjacency(-1, 0, 4, 2),
      |            PatternCornerAdjacency(-1, 0, 7, 4)
      |          ]),
      |          toArray<imm>([ // corner 2
      |            PatternCornerAdjacency(0, 0, 3, 2),
      |            PatternCornerAdjacency(-1, 0, 2, 3),
      |            PatternCornerAdjacency(-1, 0, 4, 1)
      |          ]),
      |          toArray<imm>([ // corner 3
      |            PatternCornerAdjacency(0, 0, 3, 1),
      |            PatternCornerAdjacency(0, 0, 4, 4)
      |          ]),
      |          toArray<imm>([ // corner 4
      |            PatternCornerAdjacency(0, 0, 6, 2),
      |            PatternCornerAdjacency(0, 0, 7, 3),
      |            PatternCornerAdjacency(0, 0, 4, 3)
      |          ])
      |        ])
      |      ),
      |      PatternTile( // tile 6
      |        1, 180.0, Vec2(0.8657324889, 3.317168873),
      |        toArray<imm>([ // sideAdjacenciesBySideIndex
      |          PatternSideAdjacency(-1, 0, 7, 0),
      |          PatternSideAdjacency(0, 0, 5, 4),
      |          PatternSideAdjacency(0, 0, 7, 2),
      |          PatternSideAdjacency(1, 1, 1, 2),
      |          PatternSideAdjacency(0, 1, 0, 0)
      |        ]),
      |        toArray<imm>([ // cornerAdjacenciesByCornerIndex
      |          toArray<imm>([ // corner 0
      |            PatternCornerAdjacency(0, 1, 0, 0),
      |            PatternCornerAdjacency(-1, 0, 7, 1)
      |          ]),
      |          toArray<imm>([ // corner 4
      |            PatternCornerAdjacency(0, 0, 5, 0),
      |            PatternCornerAdjacency(-1, 0, 7, 0)
      |          ]),
      |          toArray<imm>([ // corner 3
      |            PatternCornerAdjacency(0, 0, 5, 4),
      |            PatternCornerAdjacency(0, 0, 4, 3),
      |            PatternCornerAdjacency(0, 0, 7, 3)
      |          ]),
      |          toArray<imm>([ // corner 2
      |            PatternCornerAdjacency(0, 0, 7, 2),
      |            PatternCornerAdjacency(1, 1, 1, 3),
      |            PatternCornerAdjacency(1, 1, 0, 4)
      |          ]),
      |          toArray<imm>([ // corner 1
      |            PatternCornerAdjacency(0, 1, 0, 1),
      |            PatternCornerAdjacency(1, 1, 1, 2)
      |          ])
      |        ])
      |      ),
      |      PatternTile( // tile 7
      |        1, 0.0, Vec2(1.466444828, 2.500023412),
      |        toArray<imm>([ // sideAdjacenciesBySideIndex
      |          PatternSideAdjacency(1, 0, 6, 0),
      |          PatternSideAdjacency(1, 1, 0, 4),
      |          PatternSideAdjacency(0, 0, 6, 2),
      |          PatternSideAdjacency(0, 0, 4, 2),
      |          PatternSideAdjacency(1, 0, 5, 0)
      |        ]),
      |        toArray<imm>([ // cornerAdjacenciesByCornerIndex
      |          toArray<imm>([ // corner 0
      |            PatternCornerAdjacency(1, 0, 5, 0),
      |            PatternCornerAdjacency(1, 0, 6, 1)
      |          ]),
      |          toArray<imm>([ // corner 4
      |            PatternCornerAdjacency(1, 0, 6, 0),
      |            PatternCornerAdjacency(1, 1, 0, 0)
      |          ]),
      |          toArray<imm>([ // corner 3
      |            PatternCornerAdjacency(0, 0, 6, 3),
      |            PatternCornerAdjacency(1, 1, 1, 3),
      |            PatternCornerAdjacency(1, 1, 0, 4)
      |          ]),
      |          toArray<imm>([ // corner 2
      |            PatternCornerAdjacency(0, 0, 4, 3),
      |            PatternCornerAdjacency(0, 0, 5, 4),
      |            PatternCornerAdjacency(0, 0, 6, 2)
      |          ]),
      |          toArray<imm>([ // corner 1
      |            PatternCornerAdjacency(0, 0, 4, 2),
      |            PatternCornerAdjacency(1, 0, 5, 1)
      |          ])
      |        ])
      |      )
      |    ]),
      |    Vec2(1.598954903, -1.307432738), // xOffset
      |    Vec2(0.8657324889, 4.306577432) // yOffset
      |  )
      |}
      |""".stripMargin
}
