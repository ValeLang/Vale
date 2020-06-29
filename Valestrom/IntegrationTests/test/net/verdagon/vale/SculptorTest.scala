package net.verdagon.vale

object SculptorTests {
//  private def check[T](testNumber: Int, code: String, expected: T) {
//    CommonEnv.runSculptor(code) match {
//      case None => {
//        println("No! " + testNumber)
//      }
//      case Some(programH) => {
//        if (programH.toString.trim.equals(expected.toString.trim)) {
////          println("Yes!")
//        } else {
//          println("Uh sculptor " + testNumber + "...");
//            println(programH.toString());
//            println(expected.toString());
//        }
//      }
//    }
//  }
//
//  private def runTests() {
//    check(1,
//        "fn main()Int{7}",
//        """
//          |define i64* @"main<0>"(){
//          |%line0sizeptr = getelementptr i64, i64* null, i64 1
//          |%line0size = ptrtoint i64* %line0sizeptr to i64
//          |%line0raw = call i8* @malloc(i64 %line0size)
//          |%line0 = bitcast i8* %line0raw to i64*
//          |%line0value = add i64 0, 7
//          |store i64 %line0value, i64* %line0
//          |ret i64* %line0
//          |}
//          |""".stripMargin);
//    check(2,
//        "interface Car {}" +
//        "struct Civic {}" +
//        "Civic implements Car;",
//        """
//          |
//        """.stripMargin)
//  }
}
