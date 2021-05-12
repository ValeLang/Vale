package net.verdagon.vale

import scala.collection.immutable.List

object FileCoordinate {
  val test = FileCoordinate("test", List(), "test")
}
case class FileCoordinate(module: String, namespaces: List[String], filename: String) {
  def isInternal = module == ""

  def namespaceCoordinate = NamespaceCoordinate(module, namespaces)
}

case class NamespaceCoordinate(module: String, namespaces: List[String]) {
  def isInternal = module == ""
}

object FileCoordinateMap {
  def test[T](contents: T): FileCoordinateMap[T] = {
    FileCoordinateMap(Map()).add("test", List(), "test.vale", contents)
  }
}

case class FileCoordinateMap[Contents](
    moduleToNamespacesToFilenameToContents: Map[String, Map[List[String], Map[String, Contents]]]) {
  def apply(coord: FileCoordinate): Contents = {
    moduleToNamespacesToFilenameToContents(coord.module)(coord.namespaces)(coord.filename)
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
}
