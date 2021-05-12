package net.verdagon.vale.astronomer

import net.verdagon.vale.scout.ProgramS
import net.verdagon.vale.{NamespaceCoordinateMap, vassert}

import scala.collection.immutable.List

object OrderModules {
  def orderModules(mergedProgramS: NamespaceCoordinateMap[ProgramS]): List[String] = {
    val dependentAndDependeeModule: List[(String, String)] =
      mergedProgramS.moduleToNamespacesToFilenameToContents.map({ case (dependentModuleName, namespacesToFilenameToContents) =>
        val dependeeModules = namespacesToFilenameToContents.values.flatMap(_.imports.map(_.moduleName))
        dependeeModules.map(_.name).map(dependeeName => (dependentModuleName -> dependeeName))
      }).flatten.toList
    orderModules(dependentAndDependeeModule)
  }

  def orderModules(dependentAndDependeeModule: List[(String, String)]): List[String] = {
    var dependentToDependeeModule: Map[String, Set[String]] =
      dependentAndDependeeModule.groupBy(_._1).mapValues(_.map(_._2).toSet)

    var dependeeToDependentModule: Map[String, Set[String]] =
      dependentAndDependeeModule.groupBy(_._2).mapValues(_.map(_._1).toSet)

    var orderedModulesReversed = List[String]()

    val maxNumPasses = dependentToDependeeModule.size
    0.until(maxNumPasses).foreach(_ => {
      if (dependentToDependeeModule.isEmpty) {
        // Do nothing
      } else {
        // Find a module that depends on nothing
        dependentToDependeeModule.find(_._2.isEmpty) match {
          case None => {
            throw CompileErrorExceptionA(CircularModuleDependency(dependeeToDependentModule.keySet))
          }
          case Some((thisModule, dependentModules)) => {
            vassert(dependentModules.isEmpty)
            orderedModulesReversed = thisModule :: orderedModulesReversed
            dependentToDependeeModule = dependentToDependeeModule - thisModule
            val modulesDependingOnThisOne = dependeeToDependentModule(thisModule)
            dependeeToDependentModule = dependeeToDependentModule - thisModule
            modulesDependingOnThisOne.foreach(moduleDependingOnThisOne => {
              val allDependenciesOfModuleDependingOnThisOne = dependentToDependeeModule(moduleDependingOnThisOne)
              val allOtherDependenciesOfModuleDependingOnThisOne =
                allDependenciesOfModuleDependingOnThisOne - thisModule
              dependentToDependeeModule = dependentToDependeeModule + allOtherDependenciesOfModuleDependingOnThisOne
            })
          }
        }
      }
    })
    vassert(dependentToDependeeModule.isEmpty)
    vassert(dependeeToDependentModule.isEmpty)

    orderedModulesReversed.reverse
  }
}
