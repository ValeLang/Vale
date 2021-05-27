package net.verdagon.vale

import scala.collection.immutable.List

case class FileCoordinate(module: String, packages: List[String], filepath: String) {
  def isInternal = module == ""

  def packageCoordinate = PackageCoordinate(module, packages)

  def compareTo(that: FileCoordinate) = FileCoordinate.compare(this, that)
}

object FileCoordinate extends Ordering[FileCoordinate] {
  val test = FileCoordinate("test", List(), "test.vale")

  override def compare(a: FileCoordinate, b: FileCoordinate):Int = {
    val diff = a.packageCoordinate.compareTo(b.packageCoordinate)
    if (diff != 0) {
      diff
    } else {
      a.filepath.compareTo(b.filepath)
    }
  }
}

case class PackageCoordinate(module: String, packages: List[String]) {
  def isInternal = module == ""

  def compareTo(that: PackageCoordinate) = PackageCoordinate.compare(this, that)

  override def toString: String = module + packages.map("." + _).mkString("")
}

object PackageCoordinate extends Ordering[PackageCoordinate] {
  val TEST_TLD = PackageCoordinate("test", List())

  val BUILTIN = PackageCoordinate("", List())

  val internal = PackageCoordinate("", List())

  override def compare(a: PackageCoordinate, b: PackageCoordinate):Int = {
    val lenDiff = a.packages.length - b.packages.length
    if (lenDiff != 0) {
      return lenDiff
    }
    val stepsDiff =
      a.packages.zip(b.packages).foldLeft(0)({
        case (0, (stepA, stepB)) => stepA.compareTo(stepB)
        case (diffSoFar, _) => diffSoFar
      })
    if (stepsDiff != 0) {
      return stepsDiff
    }
    return a.module.compareTo(b.module)
  }
}

object FileCoordinateMap {
  val TEST_MODULE = "test"
  def test[T](contents: T): FileCoordinateMap[T] = {
    FileCoordinateMap(Map()).add(TEST_MODULE, List(), "test.vale", contents)
  }
  def test[T](contents: List[T]): FileCoordinateMap[T] = {
    test(contents.zipWithIndex.map({ case (code, index) => (index + ".vale", code) }).toMap)
  }
  def test[T](contents: Map[String, T]): FileCoordinateMap[T] = {
    contents.foldLeft(FileCoordinateMap[T](Map()))({
      case (prev, (filename, c)) => prev.add(TEST_MODULE, List(), filename, c)
    })
  }
}

case class FileCoordinateMap[Contents](
    moduleToPackagesToFilenameToContents: Map[String, Map[List[String], Map[String, Contents]]])
extends IPackageResolver[Map[String, Contents]] {
  def apply(coord: FileCoordinate): Contents = {
    vassertSome(
      vassertSome(
        vassertSome(
          moduleToPackagesToFilenameToContents.get(coord.module))
          .get(coord.packages))
        .get(coord.filepath))
  }

  def add(module: String, packages: List[String], filename: String, contents: Contents):
  FileCoordinateMap[Contents] = {
    val packagesToFilenameToContents = moduleToPackagesToFilenameToContents.getOrElse(module, Map())
    val filenameToContents = packagesToFilenameToContents.getOrElse(packages, Map())
    vassert(!filenameToContents.contains(filename))
    val newFilenameToContents = filenameToContents + (filename -> contents)
    val newPackagesToFilenameToContents = packagesToFilenameToContents + (packages -> newFilenameToContents)
    val newModuleToPackagesToFilenameToContents = moduleToPackagesToFilenameToContents + (module -> newPackagesToFilenameToContents)
    FileCoordinateMap(newModuleToPackagesToFilenameToContents)
  }

  def map[T](func: (FileCoordinate, Contents) => T): FileCoordinateMap[T] = {
    FileCoordinateMap(
      moduleToPackagesToFilenameToContents.map({ case (module, packagesToFilenameToContents) =>
        module ->
          packagesToFilenameToContents.map({ case (packages, filenameToContents) =>
            packages ->
              filenameToContents.map({ case (filename, contents) =>
                filename -> func(FileCoordinate(module, packages, filename), contents)
              })
          })
      }))
  }

  def flatMap[T](func: (FileCoordinate, Contents) => T): Iterable[T] = {
    moduleToPackagesToFilenameToContents.flatMap({ case (module, packagesToFilenameToContents) =>
      packagesToFilenameToContents.flatMap({ case (packages, filenameToContents) =>
        filenameToContents.map({ case (filename, contents) =>
          func(FileCoordinate(module, packages, filename), contents)
        })
      })
    })
  }

  def expectOne(): Contents = {
    val List(only) = moduleToPackagesToFilenameToContents.values.flatMap(_.values.flatMap(_.values))
    only
  }

  def mergeNonOverlapping(that: FileCoordinateMap[Contents]): FileCoordinateMap[Contents] = {
    val thisMap = this.moduleToPackagesToFilenameToContents
    val thatMap = that.moduleToPackagesToFilenameToContents
    FileCoordinateMap(
      (thisMap.keySet ++ thatMap.keySet).toList.map(module => {
        val moduleContentsFromThis = thisMap.getOrElse(module, Map())
        val moduleContentsFromThat = thatMap.getOrElse(module, Map())
        // Make sure there was no overlap
        vassert(moduleContentsFromThis.keySet.size + moduleContentsFromThat.keySet.size ==
          (moduleContentsFromThis.keySet ++ moduleContentsFromThat.keySet).size)
        val contents = moduleContentsFromThis ++ moduleContentsFromThat
        (module -> contents)
      }).toMap)
  }

  def resolve(packageCoord: PackageCoordinate): Option[Map[String, Contents]] = {
    moduleToPackagesToFilenameToContents.get(packageCoord.module) match {
      case None => None
      case Some(packagesToFilenameToContents) => {
        packagesToFilenameToContents.get(packageCoord.packages) match {
          case None => None
          case Some(filenameToContents) => Some(filenameToContents)
        }
      }
    }
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
    files.moduleToPackagesToFilenameToContents.get(packageCoord.module) match {
      case Some(packagesToFilenameToContents) => {
        packagesToFilenameToContents.get(packageCoord.packages) match {
          case Some(filenameToContents) => {
            return filenameToContents
          }
          case None =>
        }
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
  moduleToPackagesToFilenameToContents: Map[String, Map[List[String], Contents]]) {
  def add(module: String, packages: List[String], contents: Contents):
  PackageCoordinateMap[Contents] = {
    val packagesToContents = moduleToPackagesToFilenameToContents.getOrElse(module, Map())
    vassert(!packagesToContents.contains(packages))
    val newPackagesToFilenameToContents = packagesToContents + (packages -> contents)
    val newModuleToPackagesToFilenameToContents = moduleToPackagesToFilenameToContents + (module -> newPackagesToFilenameToContents)
    PackageCoordinateMap(newModuleToPackagesToFilenameToContents)
  }

  def test[T](contents: T): PackageCoordinateMap[T] = {
    PackageCoordinateMap(Map()).add("test", List(), contents)
  }

  def expectOne(): Contents = {
    val List(only) = moduleToPackagesToFilenameToContents.values.flatMap(_.values)
    only
  }
}
