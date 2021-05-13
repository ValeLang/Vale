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
  def test[T](contents: T): FileCoordinateMap[T] = {
    FileCoordinateMap(Map()).add("test", List(), "test.vale", contents)
  }
  def test[T](contents: Map[String, T]): FileCoordinateMap[T] = {
    contents.foldLeft(FileCoordinateMap[T](Map()))({
      case (prev, (filename, c)) => prev.add("test", List(), filename, c)
    })
  }
}

case class FileCoordinateMap[Contents](
    moduleToNamespacesToFilenameToContents: Map[String, Map[List[String], Map[String, Contents]]]) {
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
