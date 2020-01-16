package xsbt

import Compat._
import java.io.File
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue }
import scala.tools.nsc.ZincPicklePath

trait ZincSymbolLoaders extends GlobalSymbolLoaders with ZincPickleCompletion {
  import global._
  import scala.tools.nsc.io.AbstractFile
  import scala.tools.nsc.util.ClassRepresentation
  private type ConcurrentSet[A] = ConcurrentHashMap.KeySetView[A, java.lang.Boolean]

  private val invalidatedClassFilePaths: ConcurrentSet[String] = ConcurrentHashMap.newKeySet[String]()

  override def initializeFromClassPath(owner: Symbol, classRep: ClassRepresentation): Unit = {
    ((classRep.binary, classRep.source): @unchecked) match {
      case (Some(bin), Some(src))
          if platform.needCompile(bin, src) && !binaryOnly(owner, classRep.name) =>
        if (settings.verbose) inform("[symloader] picked up newer source file for " + src.path)
        enterToplevelsFromSource(owner, classRep.name, src)
      case (None, Some(src)) =>
        if (settings.verbose) inform("[symloader] no class, picked up source file for " + src.path)
        enterToplevelsFromSource(owner, classRep.name, src)
      case (Some(bin), _) =>
        val classFile: File = bin.file
        if (classFile != null && isInvalidatedClassFile(classFile.getCanonicalPath)) {
          () // An invalidated class file should not be loaded
        } else if (bin.path.startsWith("_BPICKLE_")) {
          enterClassAndModule(owner, classRep.name, new ZincPickleLoader(bin, _, _))
        } else {
          enterClassAndModule(owner, classRep.name, new ClassfileLoader(bin, _, _))
        }
    }
  }

  def isInvalidatedClassFile(path: String): Boolean = invalidatedClassFilePaths.contains(path)
  def addInvalidatedClassFile(path: String): Unit = invalidatedClassFilePaths.add(path)
  def clearInvalidatedClassFiles(): Unit = invalidatedClassFilePaths.clear()

  final class ZincPickleLoader(
      val pickleFile: AbstractFile,
      clazz: ClassSymbol,
      module: ModuleSymbol
  ) extends SymbolLoader
      with FlagAssigningCompleter {

    override def description = "pickle file from " + pickleFile.toString

    override def doComplete(sym: symbolTable.Symbol): Unit = {
      pickleComplete(pickleFile, clazz, module, sym)
    }
  }

}
