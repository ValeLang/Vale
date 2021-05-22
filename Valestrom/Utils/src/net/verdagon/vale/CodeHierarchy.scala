package net.verdagon.vale

import scala.collection.immutable.List

case class FileCoordinate(module: String, namespaces: List[String], filepath: String) {
  def isInternal = module == ""

  def namespaceCoordinate = NamespaceCoordinate(module, namespaces)

  def compareTo(that: FileCoordinate) = FileCoordinate.compare(this, that)
}

object FileCoordinate extends Ordering[FileCoordinate] {
  val test = FileCoordinate("test", List(), "test.vale")

  override def compare(a: FileCoordinate, b: FileCoordinate):Int = {
    val diff = a.namespaceCoordinate.compareTo(b.namespaceCoordinate)
    if (diff != 0) {
      diff
    } else {
      a.filepath.compareTo(b.filepath)
    }
  }
}

case class NamespaceCoordinate(module: String, namespaces: List[String]) {
  def isInternal = module == ""

  def compareTo(that: NamespaceCoordinate) = NamespaceCoordinate.compare(this, that)

  override def toString: String = module + namespaces.map("." + _).mkString("")
}

object NamespaceCoordinate extends Ordering[NamespaceCoordinate] {
  val test = NamespaceCoordinate("test", List())

  val internal = NamespaceCoordinate("", List())

  override def compare(a: NamespaceCoordinate, b: NamespaceCoordinate):Int = {
    val lenDiff = a.namespaces.length - b.namespaces.length
    if (lenDiff != 0) {
      return lenDiff
    }
    val stepsDiff =
      a.namespaces.zip(b.namespaces).foldLeft(0)({
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
    moduleToNamespacesToFilenameToContents: Map[String, Map[List[String], Map[String, Contents]]])
extends INamespaceResolver[Map[String, Contents]] {
  def apply(coord: FileCoordinate): Contents = {
    vassertSome(
      vassertSome(
        vassertSome(
          moduleToNamespacesToFilenameToContents.get(coord.module))
          .get(coord.namespaces))
        .get(coord.filepath))
  }

  def add(module: String, namespaces: List[String], filename: String, contents: Contents):
  FileCoordinateMap[Contents] = {
    val namespacesToFilenameToContents = moduleToNamespacesToFilenameToContents.getOrElse(module, Map())
    val filenameToContents = namespacesToFilenameToContents.getOrElse(namespaces, Map())
    vassert(!filenameToContents.contains(filename))
    val newFilenameToContents = filenameToContents + (filename -> contents)
    val newNamespacesToFilenameToContents = namespacesToFilenameToContents + (namespaces -> newFilenameToContents)
    val newModuleToNamespacesToFilenameToContents = moduleToNamespacesToFilenameToContents + (module -> newNamespacesToFilenameToContents)
    FileCoordinateMap(newModuleToNamespacesToFilenameToContents)
  }

  def map[T](func: (FileCoordinate, Contents) => T): FileCoordinateMap[T] = {
    FileCoordinateMap(
      moduleToNamespacesToFilenameToContents.map({ case (module, namespacesToFilenameToContents) =>
        module ->
          namespacesToFilenameToContents.map({ case (namespaces, filenameToContents) =>
            namespaces ->
              filenameToContents.map({ case (filename, contents) =>
                filename -> func(FileCoordinate(module, namespaces, filename), contents)
              })
          })
      }))
  }

  def flatMap[T](func: (FileCoordinate, Contents) => T): Iterable[T] = {
    moduleToNamespacesToFilenameToContents.flatMap({ case (module, namespacesToFilenameToContents) =>
      namespacesToFilenameToContents.flatMap({ case (namespaces, filenameToContents) =>
        filenameToContents.map({ case (filename, contents) =>
          func(FileCoordinate(module, namespaces, filename), contents)
        })
      })
    })
  }

  def expectOne(): Contents = {
    val List(only) = moduleToNamespacesToFilenameToContents.values.flatMap(_.values.flatMap(_.values))
    only
  }

  def mergeNonOverlapping(that: FileCoordinateMap[Contents]): FileCoordinateMap[Contents] = {
    val thisMap = this.moduleToNamespacesToFilenameToContents
    val thatMap = that.moduleToNamespacesToFilenameToContents
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

  def resolve(nsCoord: NamespaceCoordinate): Option[Map[String, Contents]] = {
    moduleToNamespacesToFilenameToContents.get(nsCoord.module) match {
      case None => None
      case Some(namespacesToFilenameToContents) => {
        namespacesToFilenameToContents.get(nsCoord.namespaces) match {
          case None => None
          case Some(filenameToContents) => Some(filenameToContents)
        }
      }
    }
  }
}

object NamespaceCoordinateMap {
  def composeResolvers[Contents](
    resolverA: NamespaceCoordinate => Option[Map[String, Contents]],
    resolverB: NamespaceCoordinate => Map[String, Contents])
    (nsCoord: NamespaceCoordinate):
  Map[String, Contents] = {
    resolverA(nsCoord) match {
      case Some(result) => result
      case None => resolverB(nsCoord)
    }
  }

  def composeMapAndResolver[Contents](
    files: FileCoordinateMap[Contents],
    thenResolver: NamespaceCoordinate => Map[String, Contents])
    (nsCoord: NamespaceCoordinate):
  Map[String, Contents] = {
    files.moduleToNamespacesToFilenameToContents.get(nsCoord.module) match {
      case Some(namespacesToFilenameToContents) => {
        namespacesToFilenameToContents.get(nsCoord.namespaces) match {
          case Some(filenameToContents) => {
            return filenameToContents
          }
          case None =>
        }
      }
      case None =>
    }
    thenResolver(nsCoord)
  }
}

trait INamespaceResolver[T] {
  def resolve(nsCoord: NamespaceCoordinate): Option[T]

  def or(fallback: INamespaceResolver[T]): INamespaceResolver[T] =
    x => innerOr(fallback, x)

  def innerOr(fallback: INamespaceResolver[T], nsCoord: NamespaceCoordinate): Option[T] = {
    resolve(nsCoord) match {
      case Some(x) => Some(x)
      case None => fallback.resolve(nsCoord)
    }
  }
}

case class NamespaceCoordinateMap[Contents](
  moduleToNamespacesToFilenameToContents: Map[String, Map[List[String], Contents]]) {
  def add(module: String, namespaces: List[String], contents: Contents):
  NamespaceCoordinateMap[Contents] = {
    val namespacesToContents = moduleToNamespacesToFilenameToContents.getOrElse(module, Map())
    vassert(!namespacesToContents.contains(namespaces))
    val newNamespacesToFilenameToContents = namespacesToContents + (namespaces -> contents)
    val newModuleToNamespacesToFilenameToContents = moduleToNamespacesToFilenameToContents + (module -> newNamespacesToFilenameToContents)
    NamespaceCoordinateMap(newModuleToNamespacesToFilenameToContents)
  }

  def test[T](contents: T): NamespaceCoordinateMap[T] = {
    NamespaceCoordinateMap(Map()).add("test", List(), contents)
  }

  def expectOne(): Contents = {
    val List(only) = moduleToNamespacesToFilenameToContents.values.flatMap(_.values)
    only
  }
}
