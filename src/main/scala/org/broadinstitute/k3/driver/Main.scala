package org.broadinstitute.k3.driver

import net.jpountz.lz4.LZ4Factory
import org.broadinstitute.k3.variant.VariantDataset

import scala.io.Source

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.rdd._

import org.broadinstitute.k3.methods._

import scala.reflect.ClassTag

object Main {
  def usage(): Unit = {
    System.err.println("usage:")
    System.err.println("")
    System.err.println("  k3 <cluster> <input> <command> [options...]")
    System.err.println("")
    System.err.println("options:")
    System.err.println("  -h, --help: print usage")
    System.err.println("")
    System.err.println("commands:")
    System.err.println("  write <output>")
    System.err.println("  nocall")
  }

  def fatal(msg: String): Unit = {
    System.err.println("k3: " + msg)
    System.exit(1)
  }

  def main(args: Array[String]) {
    if (args.exists(a => a == "-h" || a == "--help")) {
      usage()
      System.exit(0)
    }

    if (args.length < 3)
      fatal("too few arguments")

    val master = args(0)
    val input = args(1)
    val command = args(2)

    val conf = new SparkConf().setAppName("K3").setMaster(master)
    conf.set("spark.sql.parquet.compression.codec", "uncompressed")
    conf.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")

    val sc = new SparkContext(conf)

    // FIXME take as argument or scan CLASSPATH
    sc.addJar("/Users/cseed/k3/build/libs/k3.jar")

    val sqlContext = new org.apache.spark.sql.SQLContext(sc)

    val vds: VariantDataset =
      if (input.endsWith(".vds"))
        VariantDataset.read(sqlContext, input)
      else {
        if (!input.endsWith(".vcf")
          && !input.endsWith(".vcf.gz")
          && !input.endsWith(".vcfd"))
          fatal("unknown input file type")


        LoadVCF(sc, input)
      }

    println("entries: " + vds.count())

    if (command == "write") {
      if (args.length < 4)
        fatal("write: too few arguments")

      val output = args(3)
      vds.write(sqlContext, output)
    } else if (command == "nocall") {
      if (args.length != 3)
        fatal("nocall: unexpected arguments")

      val sampleNoCall = SampleNoCall(vds)
      for ((s, nc) <- sampleNoCall)
        println(vds.sampleIds(s) + ": " + nc)
    } else
      fatal("unknown command: " + command)
  }
}
