package net.verdagon.vale

import java.io.{OutputStream, PrintStream}

object Terrain {
  val code =
    """
      |struct TerrainTile {
      |  elevation: Int;
      |  walkable: Bool;
      |  classId: Str;
      |}
      |
      |struct LocationHasher { }
      |fn __call(this: &LocationHasher, loc: Location) int {
      |  hash! = 0;
      |  set hash = 41 * hash + loc.groupX;
      |  set hash = 41 * hash + loc.groupY;
      |  set hash = 41 * hash + loc.indexInGroup;
      |  = hash;
      |}
      |
      |struct LocationEquator { }
      |fn __call(this: &LocationEquator, a: Location, b: Location) bool {
      |  (a.groupX == b.groupX) and (a.groupY == b.groupY) and (a.indexInGroup == b.indexInGroup)
      |}
      |
      |struct Terrain {
      |  pattern: Pattern;
      |  elevationStepHeight: Float;
      |  tiles: HashMap<Location, TerrainTile, LocationHasher, LocationEquator>;
      |}
      |
    """.stripMargin

  val generatorCode =
    Samples.get("genericvirtuals/opt.vale") +
      Samples.get("genericvirtuals/optingarraylist.vale") +
      Samples.get("genericvirtuals/hashmap.vale") +
      Samples.get("vec.vale") +
      Pattern.code +
      Samples.get("generics/opt.vale") +
      PentagonPattern9.code +
      Terrain.code +
      """
        |fn println(f: Float) void {
        |  println(str(f));
        |}
        |
        |fn GetTileCenter(pattern: Pattern, loc: Location) Vec2 {
        |  (pattern.xOffset * loc.groupX) + (pattern.yOffset * loc.groupY) + (pattern.patternTiles.(loc.indexInGroup).translate)
        |}
        |
        |fn DistanceBetween(pattern: Pattern, locA: Location, locB: Location) Float {
        |  pattern.GetTileCenter(locA).distance(pattern.GetTileCenter(locB))
        |}
        |
        |fn getRelativeAdjacentLocations(pattern: Pattern, tileIndex: Int, adjacentCornersToo: Bool)
        |Array<imm, Location> {
        |  result = HashMap<Location, Int, LocationHasher, LocationEquator>(LocationHasher(), LocationEquator());
        |  tile = pattern.patternTiles.(tileIndex);
        |  tile.sideAdjacenciesBySideIndex each (sideAdjacency){
        |    location =
        |        Location(sideAdjacency.groupRelativeX, sideAdjacency.groupRelativeY, sideAdjacency.tileIndex);
        |    if (not(result.has(location))) {
        |      result.add(location, 0);
        |    }
        |  };
        |  if (adjacentCornersToo) {
        |    tile.cornerAdjacenciesByCornerIndex each (cornerAdjacencies){
        |      cornerAdjacencies each (cornerAdjacency){
        |        location =
        |            Location(cornerAdjacency.groupRelativeX, cornerAdjacency.groupRelativeY, cornerAdjacency.tileIndex);
        |        if (not(result.has(location))) {
        |          result.add(location, 0);
        |        }
        |      }
        |    }
        |  }
        |  = result.keys();
        |}
        |
        |fn getAdjacentLocations(pattern: Pattern, loc: Location, considerCornersAdjacent<Bool>) Array<imm, Location> {
        |  result = List<Location>();
        |  pattern.getRelativeAdjacentLocations(loc.indexInGroup, considerCornersAdjacent) each (relativeLoc){
        |    result.add(Location(
        |      loc.groupX + relativeLoc.groupX,
        |      loc.groupY + relativeLoc.groupY,
        |      relativeLoc.indexInGroup));
        |  };
        |  = toArray<imm>(&result);
        |}
        |
        |fn str(loc: Location) {
        |  "Location(" + str(loc.groupX) + ", " + str(loc.groupY) + ", " + str(loc.indexInGroup) + ")"
        |}
        |
        |fn getAdjacentLocationsToAll(
        |    pattern: Pattern,
        |    sourceLocs: &Array<imm, Location>,
        |    includeSourceLocs: Bool,
        |    considerCornersAdjacent: Bool)
        |HashMap<Location, Int, LocationHasher, LocationEquator> {
        |  sourceLocsSet = HashMap<Location, Int>(LocationHasher(), LocationEquator());
        |  sourceLocs each (sourceLoc){
        |    sourceLocsSet.add(sourceLoc, 0);
        |  };
        |
        |  result = HashMap<Location, Int>(LocationHasher(), LocationEquator());
        |  sourceLocs each (originalLocation){
        |    adjacents = pattern.getAdjacentLocations(originalLocation, considerCornersAdjacent).toList();
        |    if (includeSourceLocs) {
        |      adjacents.add(originalLocation);
        |    }
        |    adjacents.toArray<imm>() each (adjacentLocation){
        |      if (sourceLocsSet.has(adjacentLocation) and not(includeSourceLocs)) {
        |        // if this is a source loc, and we don't want to include them, do nothing.
        |      } else {
        |        if (not(result.has(adjacentLocation))) {
        |          println("Found an adjacent location: " + str(adjacentLocation));
        |          result.add(adjacentLocation, 0);
        |        }
        |      }
        |    };
        |  };
        |  = result;
        |}
        |
        |fn locationsAreAdjacent(pattern: Pattern, a: Location, b: Location, considerCornersAdjacent: Bool) Bool {
        |  locsAdjacentToA = pattern.getAdjacentLocations(a, considerCornersAdjacent);
        |  = locsAdjacentToA.has(b, LocationEquator());
        |}
        |
        |
        |
        |//
        |//    public static List<Vec2> GetRelativeCornerPositions(this Pattern pattern, Location loc) {
        |//      var patternTile = pattern.patternTiles[loc.indexInGroup];
        |//      int shapeIndex = patternTile.shapeIndex;
        |//      float rotateDegrees = patternTile.rotateDegrees;
        |//      double rotateRadians = DegreesToRadians(rotateDegrees);
        |//      var corners = pattern.cornersByShapeIndex[shapeIndex];
        |//
        |//      List<Vec2> results = new List<Vec2>();
        |//
        |//      for (int i = 0; i < corners.Count; i++) {
        |//        Vec2 unrotatedCorner = corners[i];
        |//        Vec2 rotatedCorner =
        |//            new Vec2(
        |//                (float)(unrotatedCorner.x * Math.Cos(rotateRadians) -
        |//                    unrotatedCorner.y * Math.Sin(rotateRadians)),
        |//                (float)(unrotatedCorner.y * Math.Cos(rotateRadians) +
        |//                    unrotatedCorner.x * Math.Sin(rotateRadians)));
        |//        results.Add(rotatedCorner);
        |//      }
        |//
        |//      return results;
        |//    }
        |//
        |//    public static List<Vec2> GetCornerPositions(this Pattern pattern, Location loc) {
        |//      var center = pattern.GetTileCenter(loc);
        |//      List<Vec2> results = new List<Vec2>();
        |//      foreach (var relativeCorner in pattern.GetRelativeCornerPositions(loc)) {
        |//        results.Add(center.plus(relativeCorner));
        |//      }
        |//      return results;
        |//    }
        |//
        |
        |fn main() int export {
        |  pattern = makePentagonPattern9();
        |  //adjacent1Locations = getAdjacentLocations(pattern, Location(0, 0, 0), true);
        |  //println("Adjacent 1s: " + str(adjacent1Locations.len()));
        |  //adjacent2Locations = getAdjacentLocationsToAll(pattern, adjacent1Locations, true, true);
        |  //println("Adjacent 2s: " + str(adjacent2Locations.keys().len()));
        |  //adjacent3Locations = getAdjacentLocationsToAll(pattern, adjacent2Locations.keys(), true, true);
        |  //println("Adjacent 3s: " + str(adjacent3Locations.keys().len()));
        |  //adjacent4Locations = getAdjacentLocationsToAll(pattern, adjacent3Locations.keys(), true, true);
        |  //println("Adjacent 4s: " + str(adjacent4Locations.keys().len()));
        |  //adjacent5Locations = getAdjacentLocationsToAll(pattern, adjacent4Locations.keys(), true, true);
        |  //println("Done! 5s: " + str(adjacent5Locations.keys().len()));
        |  terrain = Terrain(pattern, 0.4, HashMap<Location, TerrainTile>(LocationHasher(), LocationEquator(), 1024));
        |
        |  terrain.tiles.add(Location(0, 0, 0), TerrainTile(1, true, "ground"));
        |  println("a");
        |  terrain.tiles.add(Location(0, 0, 1), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(0, 0, 2), TerrainTile(1, true, "ground"));
        |  println("b");
        |  terrain.tiles.add(Location(0, 0, 3), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(0, 0, 4), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(0, 0, 5), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(0, 0, 6), TerrainTile(1, true, "ground"));
        |  println("c");
        |  terrain.tiles.add(Location(0, 0, 7), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-3, -2, 6), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(2, -1, 5), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-2, 0, 7), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-2, 0, 6), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-2, 0, 5), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-2, 0, 4), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-2, 0, 3), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-2, 0, 2), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-2, 0, 1), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-2, 0, 0), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(0, -1, 6), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(0, -1, 5), TerrainTile(1, true, "ground"));
        |  println("d");
        |  terrain.tiles.add(Location(0, -1, 4), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(0, -1, 7), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(2, 0, 0), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(2, 0, 2), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(0, 1, 0), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(2, 0, 3), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(2, 0, 1), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(2, 0, 4), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(2, 0, 5), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(2, 0, 6), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(2, 0, 7), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(0, 1, 1), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(0, 1, 2), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(0, -1, 2), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(0, -1, 3), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(0, -1, 0), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(0, -1, 1), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-2, -1, 7), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-2, -1, 6), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-2, -1, 5), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-2, -1, 4), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-2, -1, 3), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-2, -1, 2), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-2, -1, 1), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(2, 1, 0), TerrainTile(1, true, "ground"));
        |  println("e");
        |  terrain.tiles.add(Location(0, -2, 6), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-2, -1, 0), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(2, 1, 1), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(2, 1, 2), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(2, 1, 3), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(0, -2, 7), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(1, -1, 0), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(1, -1, 1), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(1, -1, 2), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(1, -1, 3), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(1, -1, 4), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(1, -1, 5), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(1, -1, 6), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(1, -1, 7), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-2, -2, 6), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-1, 1, 0), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-2, -2, 7), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-1, 0, 7), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-1, 0, 6), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-1, 0, 5), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-1, 0, 4), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-1, 0, 3), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-1, 0, 2), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-1, 0, 1), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-1, 0, 0), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(1, 0, 1), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(1, 0, 0), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(1, 0, 3), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(1, 0, 2), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(1, 0, 5), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(1, 0, 6), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(1, 0, 4), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(1, 0, 7), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-1, -1, 7), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-1, -1, 6), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(3, 0, 1), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-1, -1, 4), TerrainTile(1, true, "ground"));
        |  println("f");
        |  terrain.tiles.add(Location(3, 0, 3), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-1, -1, 2), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(3, 0, 5), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(1, 1, 0), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(1, 1, 1), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-1, -1, 0), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(1, 1, 3), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(3, 0, 6), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-1, -1, 5), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-1, -1, 3), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-1, -1, 1), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(1, 1, 2), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-3, -1, 7), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-3, -1, 6), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-3, -1, 5), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-3, -1, 4), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-3, -1, 3), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-3, -1, 2), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-3, -1, 1), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(3, 1, 0), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(3, 1, 1), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-1, -2, 6), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-1, -2, 7), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-3, -1, 0), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(3, 1, 3), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(2, -1, 1), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(2, -1, 3), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(-3, -2, 7), TerrainTile(1, true, "ground"));
        |  terrain.tiles.add(Location(2, -1, 6), TerrainTile(1, true, "ground"));
        |  println("g");
        |
        |  rand! = 0;
        |  terrain.tiles.keys() each (location){
        |    tile?: Opt:&TerrainTile = terrain.tiles.get(location);
        |    tile = tile?^.get();
        |    set tile.elevation = 1 + (abs(location.groupX + location.groupY + location.indexInGroup + rand) mod 3);
        |    set rand = rand * 41 + 13;
        |    println(str(location) + " " + str(tile.elevation));
        |  };
        |
        |  = terrain.tiles.keys();
        |}
        |""".stripMargin

  def main(args: Array[String]): Unit = {
//    val compile = Compilation(generatorCode)
//
//    val data =
//      Vivem.executeWithPrimitiveArgs(
//        compile.getHamuts(),
//        Vector(),
//        new PrintStream(new OutputStream() {
//          override def write(b: Int): Unit = {
//            // System.out.write(b)
//          }
//        }),
//        () => {
//          scala.io.StdIn.readLine()
//        },
//        (str: String) => {
//          print(str)
//        })
//    println(new VonPrinter(JsonSyntax, 120).print(data.get))
  }
}
