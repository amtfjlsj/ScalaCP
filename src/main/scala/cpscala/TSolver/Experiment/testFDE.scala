package cpscala.TSolver.Experiment

import java.io.File

import com.github.tototoshi.csv.CSVWriter
import cpscala.TSolver.CpUtil.Constants
import cpscala.TSolver.Model.Solver.FDESolver.{FDECoarseSolver, FDECoarseSolver1, FDEFineSolver}
import cpscala.TSolver.Model.Solver.PWSolver.PWCoarseSolver
import cpscala.TSolver.Model.Solver.SSolver.SCoarseSolver
import cpscala.XModel.{FDEModel1, XModel}

import scala.collection.mutable.ArrayBuffer
import scala.util.Sorting
import scala.xml.XML

object testFDE {
  var name = ""
  var pType = ""
  var varType = ""
  var heuName = "Dom/Ddeg"
//  var heuName = "Dom/Wdeg"


  def main(args: Array[String]): Unit = {

    if (args.isEmpty)
      argEmpty()
    else
      withArgs(args)
  }

  def argEmpty(): Unit = {
    println(s"hardware cocurrency: ${Runtime.getRuntime.availableProcessors()}")
    val file = XML.loadFile("benchmarks/FDEFolders.xml")
    val inputRoot = (file \\ "inputRoot").text
    val outputRoot = (file \\ "outputRoot").text
    val outputFolder = (file \\ "outputFolder").text
    val inputFolderNodes = file \\ "folder"

    for (fn <- inputFolderNodes) {
      val fmt = (fn \\ "@format").text.toInt
      val folderStr = fn.text
      val inputPath = inputRoot + "/" + folderStr
      val files = getFiles(new File(inputPath))
      Sorting.quickSort(files)
      println("exp files:")
      files.foreach(f => println(f.getName))
      val resFile = new File(outputRoot + "/" + outputFolder + folderStr + ".csv")
      val writer = CSVWriter.open(resFile)
      val titleLine = ArrayBuffer[String]()
      titleLine += "name"
      titleLine ++= Array("algorithm", "nodes", "time")
      titleLine ++= Array("algorithm", "nodes", "time")
      titleLine ++= Array("algorithm", "nodes", "time")
      titleLine ++= Array("algorithm", "nodes", "time")
      titleLine ++= Array("algorithm", "nodes", "time")
      titleLine ++= Array("algorithm", "nodes", "time")
      titleLine ++= Array("algorithm", "nodes", "time")


      writer.writeRow(titleLine)
      var dataLine = new ArrayBuffer[String](titleLine.length)

      for (f <- files) {
        println("Build Model: " + f.getName)
        val xm = new XModel(f.getPath, true, 3)
        val fdem = new FDEModel1(f.getPath, 3);
        dataLine.clear()
        dataLine += f.getName()
        //-------------CT串行算法-------------
        pType = "CT_Bit"
        varType = "BitSet"
        //        heuName = "Dom/Ddeg"
//        heuName = "Dom/Wdeg"
        println(s"${pType} ${heuName}===============>")
        val ct = new SCoarseSolver(xm, pType, varType, heuName)
        ct.search(Constants.TIME)
        dataLine += pType
        dataLine += ct.helper.nodes.toString()
        dataLine += (ct.helper.time.toDouble * 1e-9).toString()
        //-------------间隔单独提交并行CT-------------

        try {
          pType = "CT_Bit"
          varType = "BitSet"
          //          heuName = "Dom/Ddeg"
//          heuName = "Dom/Wdeg"
          println(s"${pType} ${heuName}===============>")
          val CTFDE = new FDECoarseSolver(fdem, pType, varType, heuName)
          CTFDE.search(Constants.TIME)
          dataLine += pType
          dataLine += CTFDE.helper.nodes.toString()
          dataLine += (CTFDE.helper.time.toDouble * 1e-9).toString()
        } catch {
          case e: OutOfMemoryError => {
            println("1111111111111111111")
            dataLine += "CT+FDE"
            dataLine += "memeory out"
            dataLine += "memeory out"
          }
        }

        try {
          pType = "STRbit_Bit"
          varType = "BitSet"
          //          heuName = "Dom/Ddeg"
//          heuName = "Dom/Wdeg"
          println(s"${pType} ${heuName}===============>")
          val Strbit = new FDEFineSolver(fdem, pType, varType, heuName)
          Strbit.search(Constants.TIME)
          dataLine += "STRbit+FDE"
          dataLine += Strbit.helper.nodes.toString()
          dataLine += (Strbit.helper.time.toDouble * 1e-9).toString()
        } catch {
          case e: OutOfMemoryError => {
            println("1111111111111111111")
            dataLine += pType
            dataLine += "memeory out"
            dataLine += "memeory out"
          }
        }


        try {
          pType = "PW-CT"
          varType = "BitSet"
          //          heuName = "Dom/Ddeg"
//          heuName = "Dom/Wdeg"
          println(s"${pType} ${heuName}===============>")
          val PWCT = new PWCoarseSolver(xm, pType, varType, heuName)
          PWCT.search(Constants.TIME)
          dataLine += pType
          dataLine += PWCT.helper.nodes.toString()
          dataLine += (PWCT.helper.time.toDouble * 1e-9).toString()
        } catch {
          case e: OutOfMemoryError => {
            println("1111111111111111111")
            dataLine += pType
            dataLine += "memeory out"
            dataLine += "memeory out"
          }
        }

        pType = "STR2"
        varType = "FDEBitSet"
        //        heuName = "Dom/Ddeg"
//        heuName = "Dom/Wdeg"
        println(s"${pType} ${heuName}===============>")
        val FDESTR2 = new FDECoarseSolver(fdem, pType, varType, heuName)
        FDESTR2.search(Constants.TIME)
        dataLine += pType
        dataLine += FDESTR2.helper.nodes.toString()
        dataLine += (FDESTR2.helper.time.toDouble * 1e-9).toString()


        pType = "STRbit_FDE"
        varType = "FDEBitSet"
        //        heuName = "Dom/Ddeg"
//        heuName = "Dom/Wdeg"
        println(s"${pType} ${heuName}===============>")
        val STRbit_FDE = new FDECoarseSolver1(fdem, pType, varType, heuName)
        STRbit_FDE.search(Constants.TIME)
        dataLine += pType
        dataLine += STRbit_FDE.helper.nodes.toString()
        dataLine += (STRbit_FDE.helper.time.toDouble * 1e-9).toString()

        try {
          pType = "STRbit_FDE1"
          varType = "FDEBitSet"
//          heuName = "Dom/Ddeg"
//          heuName = "Dom/Wdeg"
          println(s"${pType} ${heuName}===============>")
          val FDE = new FDEFineSolver(fdem, pType, varType, heuName)
          FDE.search(Constants.TIME)
          dataLine += pType
          dataLine += FDE.helper.nodes.toString()
          dataLine += (FDE.helper.time.toDouble * 1e-9).toString()
        } catch {
          case e: OutOfMemoryError => {
            println("1111111111111111111")
            dataLine += pType
            dataLine += "memeory out"
            dataLine += "memeory out"
          }
        }
        //

        writer.writeRow(dataLine)
        println("end: " + f.getName)
      }
      writer.close()
      println("-----" + folderStr + " done!-----")
    }
    println("-----All done!-----")
  }

  def withArgs(args: Array[String]): Unit = {

  }

  //获取指定单个目录下所有文件
  def getFiles(dir: File): Array[File] = {
    dir.listFiles.filter(_.isFile) ++
      dir.listFiles.filter(_.isDirectory).flatMap(getFiles)
  }

}
