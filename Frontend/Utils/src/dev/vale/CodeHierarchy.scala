package dev.vale

import scala.collection.immutable.List
import scala.collection.mutable

case class FileCoordinate(packageCoordinate: PackageCoordinate, filepath: String) extends IInterning {
  def isInternal = packageCoordinate.isInternal
  def isTest(): Boolean = packageCoordinate.isTest && filepath == "test.vale"
//  def compareTo(that: FileCoordinate) = FileCoordinate.compare(this, that)
}

object FileCoordinate {// extends Ordering[FileCoordinate] {

  def test(interner: Interner): FileCoordinate = {
    interner.intern(FileCoordinate(
      interner.intern(PackageCoordinate(
        interner.intern(StrI("test")),
        Vector.empty)),
      "test.vale"))
  }

//  override def compare(a: FileCoordinate, b: FileCoordinate):Int = {
//    val diff = a.packageCoordinate.compareTo(b.packageCoordinate)
//    if (diff != 0) {
//      diff
//    } else {
//      a.filepath.compareTo(b.filepath)
//    }
//  }
}

case class PackageCoordinate(module: StrI, packages: Vector[StrI]) extends IInterning {
  def isInternal = module.str == ""
  def isTest = module.str == "test" && packages == Vector()

//  def compareTo(that: PackageCoordinate) = PackageCoordinate.compare(this, that)

  def parent(interner: Interner): Option[PackageCoordinate] = {
    if (packages.isEmpty) {
      None
    } else {
      Some(interner.intern(PackageCoordinate(module, packages.init)))
    }
  }
}

object PackageCoordinate {// extends Ordering[PackageCoordinate] {
  def TEST_TLD(interner: Interner, keywords: Keywords): PackageCoordinate = interner.intern(PackageCoordinate(interner.intern(StrI("test")), Vector.empty))

  def BUILTIN(interner: Interner, keywords: Keywords): PackageCoordinate = interner.intern(PackageCoordinate(keywords.emptyString, Vector.empty))

  def internal(interner: Interner, keywords: Keywords): PackageCoordinate = interner.intern(PackageCoordinate(keywords.emptyString, Vector.empty))
//
//  override def compare(a: PackageCoordinate, b: PackageCoordinate):Int = {
//    val lenDiff = a.packages.length - b.packages.length
//    if (lenDiff != 0) {
//      return lenDiff
//    }
//    a.packages.zip(b.packages).foreach({ case (stepA, stepB) =>
//      val stepDiff = stepA.uid - stepB.uid
//      if (stepDiff != 0L) {
//        return U.sign(stepDiff)
//      }
//    })
//    return U.sign(a.module.uid - b.module.uid)
//  }
}

object FileCoordinateMap {
  val TEST_MODULE = "test"

  def simple[T](fileCoord: FileCoordinate, contents: T): FileCoordinateMap[T] = {
    val result = new FileCoordinateMap[T]()
    result.put(fileCoord, contents)
    result
  }
  def test[T](interner: Interner, contents: T): FileCoordinateMap[T] = {
    val result = new FileCoordinateMap[T]()
    result.put(
      interner.intern(FileCoordinate(
        interner.intern(PackageCoordinate(
          interner.intern(StrI(TEST_MODULE)), Vector.empty)),
        "test.vale")),
      contents)
    result
  }
  def test[T](interner: Interner, contents: Vector[T]): FileCoordinateMap[T] = {
    test(interner, contents.zipWithIndex.map({ case (code, index) => (index + ".vale", code) }).toMap)
  }
  def test[T](interner: Interner, contents: Map[String, T]): FileCoordinateMap[T] = {
    val result = new FileCoordinateMap[T]()
    contents.foreach({ case (filepath, contents) =>
      result.put(
        interner.intern(FileCoordinate(
          interner.intern(PackageCoordinate(
            interner.intern(StrI(TEST_MODULE)), Vector.empty)),
          filepath)),
        contents)
    })
    result
  }
}

class FileCoordinateMap[Contents](
  val packageCoordToFileCoords: mutable.Map[PackageCoordinate, Vector[FileCoordinate]] =
    mutable.HashMap[PackageCoordinate, Vector[FileCoordinate]](),
  val fileCoordToContents: mutable.Map[FileCoordinate, Contents] =
    mutable.HashMap[FileCoordinate, Contents]()
) extends IPackageResolver[Map[String, Contents]] {
  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()

  def apply(coord: FileCoordinate): Contents = {
    vassertSome(fileCoordToContents.get(coord))
  }

  // This is different from put in that we can hand in an empty map here.
  // It's the only way to have an empty package in the FileCoordinateMap.
  def putPackage(
    interner: Interner,
    packageCoord: PackageCoordinate,
    newFileCoordToContents: Map[FileCoordinate, Contents]):
  Unit = {
    packageCoordToFileCoords.put(packageCoord, newFileCoordToContents.keys.toVector)
    newFileCoordToContents.foreach({ case (fileCoord, contents) =>
      fileCoordToContents.put(fileCoord, contents)
    })
  }

  def put(fileCoord: FileCoordinate, contents: Contents): Unit = {
    vassert(!fileCoordToContents.contains(fileCoord))
    fileCoordToContents.put(fileCoord, contents)
    packageCoordToFileCoords.put(
      fileCoord.packageCoordinate,
      packageCoordToFileCoords.getOrElse(fileCoord.packageCoordinate, Vector()) :+ fileCoord)
  }

  def map[T](func: (FileCoordinate, Contents) => T): FileCoordinateMap[T] = {
    val resultFileCoordToContents = mutable.HashMap[FileCoordinate, T]()
    fileCoordToContents.foreach({ case (fileCoord, contents) =>
      resultFileCoordToContents.put(fileCoord, func(fileCoord, contents))
    })
    new FileCoordinateMap(packageCoordToFileCoords, resultFileCoordToContents)
  }

  def flatMap[T](func: (FileCoordinate, Contents) => T): Iterable[T] = {
    fileCoordToContents.map({ case (fileCoord, contents) =>
      func(fileCoord, contents)
    })
  }

  def expectOne(): Contents = {
    vassertOne(fileCoordToContents.values)
  }

//  def mergeNonOverlapping(that: FileCoordinateMap[Contents]): FileCoordinateMap[Contents] = {
//    val result =
//      FileCoordinateMap(
//        this.packageCoordToFileCoords ++ that.packageCoordToFileCoords,
//        this.fileCoordToContents ++ that.fileCoordToContents)
//    vassert(
//      result.packageCoordToFileCoords.size ==
//        this.packageCoordToFileCoords.size + that.packageCoordToFileCoords.size)
//    vassert(
//      result.fileCoordToContents.size ==
//        this.fileCoordToContents.size + that.fileCoordToContents.size)
//    result
////      (this.fileCoordToContents.keySet ++ that.fileCoordToContents.keySet).map(fileCoord => {
////        val contents =
////          (this.fileCoordToContents.get(fileCoord).toList ++ that.fileCoordToContents.get(fileCoord).toList) match {
////            case List(_, _) => vfail()
////            case List(only) => only
////            case List() => vwat()
////          }
////        fileCoord -> contents
////      }).toMap)
//  }

  def resolve(packageCoord: PackageCoordinate): Option[Map[String, Contents]] = {
    Profiler.frame(() => {
      packageCoordToFileCoords
        .get(packageCoord)
        .map(fileCoords => {
          fileCoords.map(fileCoord => {
            fileCoord.filepath -> vassertSome(fileCoordToContents.get(fileCoord))
          }).toMap
        })
    })
  }
}

object PackageCoordinateMap {
  def composeResolvers[Contents](
    resolverA: PackageCoordinate => Option[Map[String, Contents]],
    resolverB: PackageCoordinate => Map[String, Contents])
    (packageCoord: PackageCoordinate):
  Map[String, Contents] = {
    resolverA(packageCoord) match {
      case Some(result) => result
      case None => resolverB(packageCoord)
    }
  }

  def composeMapAndResolver[Contents](
    files: FileCoordinateMap[Contents],
    thenResolver: PackageCoordinate => Map[String, Contents])
    (packageCoord: PackageCoordinate):
  Map[String, Contents] = {
    files.resolve(packageCoord) match {
      case Some(filenameToContents) => {
        return filenameToContents
      }
      case None =>
    }
    thenResolver(packageCoord)
  }
}

trait IPackageResolver[T] {
  def resolve(packageCoord: PackageCoordinate): Option[T]

  def or(fallback: IPackageResolver[T]): IPackageResolver[T] =
    x => innerOr(fallback, x)

  def innerOr(fallback: IPackageResolver[T], packageCoord: PackageCoordinate): Option[T] = {
    resolve(packageCoord) match {
      case Some(x) => Some(x)
      case None => fallback.resolve(packageCoord)
    }
  }
}

case class PackageCoordinateMap[Contents](
  packageCoordToContents: mutable.HashMap[PackageCoordinate, Contents] =
    mutable.HashMap[PackageCoordinate, Contents]()) {

  override def equals(obj: Any): Boolean = vcurious(); override def hashCode(): Int = vcurious()


  def put(packageCoord: PackageCoordinate, contents: Contents): Unit = {
    packageCoordToContents.put(packageCoord, contents)
  }

  def get(packageCoord: PackageCoordinate): Option[Contents] = {
    packageCoordToContents.get(packageCoord)
  }

  def expectOne(): Contents = {
    vassertOne(packageCoordToContents.values)
  }

  def map[T](func: (PackageCoordinate, Contents) => T): PackageCoordinateMap[T] = {
    val result = new PackageCoordinateMap[T]()
    packageCoordToContents.foreach({ case (packageCoord, contents) =>
      result.put(packageCoord, func(packageCoord, contents))
    })
    result
  }

  def flatMap[T](func: (PackageCoordinate, Contents) => T): Iterable[T] = {
    packageCoordToContents.map({ case (packageCoord, contents) =>
      func(packageCoord, contents)
    })
  }
}
