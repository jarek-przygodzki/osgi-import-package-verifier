package jarekprzygodzki.tools.oipv

import java.io._
import java.util.jar._

import aQute.bnd.osgi._
import org.eclipse.osgi.util.ManifestElement
import org.osgi.framework.Constants
import scopt.OptionParser

trait OsgiBundleImportPackageReporter {

  def start(symbolicName: String, file: File): Unit

  def report(missingPackages: Set[String], unnecessaryPackages: Set[String]): Unit

  def stop(): Unit
}


class SimpleConsoleReporter extends OsgiBundleImportPackageReporter {

  var symbolicName: String = null
  var file: File = null

  override def start(symbolicName: String, file: File): Unit = {
    this.symbolicName = symbolicName
    this.file = file
  }

  override def report(missingPkgs: Set[String], unnecessaryPkgs: Set[String]): Unit = {
    println(s"- $symbolicName [${file.getName}]")
    if (!missingPkgs.isEmpty) {
      println(s"  Missing     [${missingPkgs.size}]")
      println("  \\")
      missingPkgs.toSeq.sorted.foreach { x => println(s"   + $x") }
    }
    if (!unnecessaryPkgs.isEmpty) {
      println(s"  Unnecessary [${unnecessaryPkgs.size}]")
      println("  \\")
      unnecessaryPkgs.toSeq.sorted.foreach { x => println(s"   + $x") }
    }
  }

  override def stop(): Unit = {}

}

case class Config(jarsOrFolders: Seq[File] = Seq(), excludedPackages: Seq[String] = Seq())

class OsgiBundleImportPackageAnalyzer(pkgFilter: String => Boolean) {

  def analyze(file: File, reporter: OsgiBundleImportPackageReporter) = {
    val jarFile = new JarFile(file)
    val actualManifest = jarFile.getManifest()
    def symbolicName = actualManifest.getMainAttributes.getValue(Constants.BUNDLE_SYMBOLICNAME)


    val actualPackages = getImportedPackages(actualManifest) filter pkgFilter
    val fragmentHost = actualManifest.getMainAttributes.getValue(Constants.FRAGMENT_HOST)

    // skip fragments as they share the classloader of their parent bundle and are therefore tricky
    if (fragmentHost == null) {
      reporter.start(symbolicName, file)
      val analyzer = new Analyzer()
      analyzer.setJar(file)
      analyzer.setProperty("Import-Package", "*")
      val calculatedManifest = analyzer.calcManifest()
      val calculatedPackages = getImportedPackages(calculatedManifest) filter pkgFilter
      val missing = calculatedPackages diff actualPackages
      val unnecessary = actualPackages diff calculatedPackages
      reporter.report(missing, unnecessary)
      reporter.stop()

    }
  }

  def getImportedPackages(mf: Manifest): Set[String] = {
    val importPackage = mf.getMainAttributes.getValue(Constants.IMPORT_PACKAGE)
    val imports = ManifestElement.parseHeader(Constants.IMPORT_PACKAGE, importPackage)
    if (imports != null) imports.map(_.getValue).toSet else Set()
  }
}


object App {
  def main(args: Array[String]): Unit = {
    val parser = new OptionParser[Config]("oipv") {
      head("oipv", "1.x")
      opt[Seq[File]]('j', "jars") required() valueName ("<jar1>,<jar2>...") action { (x, c) =>
        c.copy(jarsOrFolders = x)
      } text ("jars or folders to include")
      opt[Seq[String]]('e', "exclude-package") valueName ("<package1>,<package2>") action { (x, c) =>
        c.copy(excludedPackages = x)
      } text ("Package prefixes to exclude from analysis")
      help("help") text ("print usage text")
      note("Detecting unnecessarily imported and missing packages in OSGi bundles")

      override def showUsageOnError = true
    }

    parser.parse(args, Config()) map { config =>
      printBanner()
      val pkgFilter = (pkg: String) => !config.excludedPackages.exists(prefix => pkg.startsWith(prefix))
      config.jarsOrFolders.foreach { jarOrFolder =>
        for (file <- getFileTree(jarOrFolder) if file.getName.endsWith(".jar")) {
          val reporter = new SimpleConsoleReporter()
          new OsgiBundleImportPackageAnalyzer(pkgFilter).analyze(file, reporter)
        }
      }
    } getOrElse {
      // arguments are bad, usage message will have been displayed
      System exit 1
    }
  }

  def getFileTree(f: File): Stream[File] =
    f #:: (if (f.isDirectory) f.listFiles().toStream.flatMap(getFileTree)
    else Stream.empty)

  def printBanner(): Unit = {
    println("::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::")
    println("::                     Dependency Report                            ::")
    println("::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::")
  }
}