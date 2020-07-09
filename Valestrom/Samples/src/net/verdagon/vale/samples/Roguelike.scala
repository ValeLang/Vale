package net.verdagon.vale.samples

import net.verdagon.vale.{ArrayUtils, HashMap, Opt, OptingArrayList}

object Roguelike {
  val code: String =
    Opt.code +
      OptingArrayList.code +
      HashMap.code +
  ArrayUtils.code +
      """
        |struct Vec2 imm {
        |  x Float;
        |  y Float;
        |}
        |
        |struct Vec3 imm {
        |  x Float;
        |  y Float;
        |  z Float;
        |}
        |
        |struct Location imm {
        |  groupX int;
        |  groupY int;
        |  indexInGroup int;
        |}
        |
        |struct Pattern imm {
        |  name str;
        |  cornersByShapeIndex Array<imm, Array<imm, Vec2>>;
        |  patternTiles Array<imm, PatternTile>;
        |  xOffset Vec2;
        |  yOffset Vec2;
        |}
        |
        |struct PatternTile imm {
        |  shapeIndex int;
        |  rotateRadians Float;
        |  translate Vec2;
        |  sideAdjacenciesBySideIndex Array<imm, PatternSideAdjacency>;
        |  cornerAdjacenciesByCornerIndex Array<imm, Array<imm, PatternCornerAdjacency>>;
        |}
        |
        |struct PatternSideAdjacency imm {
        |  groupRelativeX int;
        |  groupRelativeY int;
        |  tileIndex int;
        |  sideIndex int;
        |}
        |
        |struct PatternCornerAdjacency imm {
        |  groupRelativeX int;
        |  groupRelativeY int;
        |  tileIndex int;
        |  cornerIndex int;
        |}
        |
        |
        |struct TerrainTile {
        |  elevation int;
        |  walkable bool;
        |  classId str;
        |}
        |
        |struct LocationHasher { }
        |fn __call(this &LocationHasher, loc Location) {
        |  hash! = 0;
        |  mut hash = 41 * hash + loc.groupX;
        |  mut hash = 41 * hash + loc.groupY;
        |  mut hash = 41 * hash + loc.indexInGroup;
        |  = hash;
        |}
        |
        |struct LocationEquator { }
        |fn __call(this &LocationEquator, a Location, b Location) {
        |  (a.groupX == b.groupX) and (a.groupY == b.groupY) and (a.indexInGroup == b.indexInGroup)
        |}
        |
        |struct Terrain {
        |  pattern Pattern;
        |  elevationStepHeight Float;
        |  tiles HashMap<Location, TerrainTile, LocationHasher, LocationEquator>;
        |}
        |
        |fn makeBoard() Array<mut, Array<mut, Str>> {
        |  ret
        |    Arr<mut>(10, (row){
        |      Arr<mut>(10, (col){
        |        = if (row == 0) { "#" }
        |          else if (col == 0) { "#" }
        |          else if (row == 9) { "#" }
        |          else if (col == 9) { "#" }
        |          else { "." }
        |      })
        |    });
        |}
        |
        |fn display(board &Array<mut, Array<mut, Str>>, playerRow int, playerCol int) {
        |  toPrint! = "";
        |  eachI &board (rowI, row){
        |    eachI &row (cellI, cell){
        |      if (and(rowI == playerRow, cellI == playerCol)) {
        |        mut toPrint = toPrint + "@";
        |      } else {
        |        mut toPrint = toPrint + cell;
        |      }
        |    }
        |    mut toPrint = toPrint + "\n";
        |  }
        |  print(toPrint);
        |}
        |
        |fn main() {
        |  board = makeBoard();
        |
        |  playerRow! = 4;
        |  playerCol! = 3;
        |
        |  running! = true;
        |  while (running) {
        |    display(&board, playerRow, playerCol);
        |
        |    key = __getch();
        |    println(key);
        |    newPlayerRow! = playerRow;
        |    newPlayerCol! = playerCol;
        |    if (key == 81) {
        |      mut running = false;
        |    } else if (key == 119) {
        |      mut newPlayerRow = newPlayerRow - 1;
        |    } else if (key == 115) {
        |      mut newPlayerRow = newPlayerRow + 1;
        |    } else if (key == 97) {
        |      mut newPlayerCol = newPlayerCol - 1;
        |    } else if (key == 100) {
        |      mut newPlayerCol = newPlayerCol + 1;
        |    }
        |    if (board[newPlayerRow][newPlayerCol] == ".") {
        |      mut playerRow = newPlayerRow;
        |      mut playerCol = newPlayerCol;
        |    }
        |  }
        |}
        |""".stripMargin
}
