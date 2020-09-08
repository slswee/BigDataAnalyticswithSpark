/*
 Sally practice file
 */
package sparkrdd

import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}
import scalafx.application.JFXApp
import standardscala.TempData
import swiftvis2.plotting._
import swiftvis2.plotting.renderer.FXRenderer

object RDDTempData2 extends JFXApp {

  val conf = new SparkConf().setAppName("Temp Data").setMaster("local[*]")
  val sc = new SparkContext(conf)
  sc.setLogLevel("WARN")

  val lines: RDD[String] = sc.textFile("MN212142_9392.csv").filter(!_
    .contains("Day"))
  val data = lines.flatMap { line =>
    val p = line.split(",")
    if (p(7) == "." || p(8) == "." || p(9) == ".") Seq.empty
    else Seq(TempData(p(0).toInt, p(1).toInt, p(2).toInt, p(4).toInt,
      TempData.toDoubleOrNeg(p(5)), TempData.toDoubleOrNeg(p(6)), p(7)
        .toDouble, p(8).toDouble, p(9).toDouble))
  }.cache // cache() will store this RDD in memory, all future uses of
  // this will use this data in memory, so we don't have to reprocess the
  // data each and every time


  // Spark transforms (methods that return other RDDs)  are lazy, such as
  // map, flatMap
  // Spark methods that don't return RDDs are actions, they will force some
  // level
  // of computation to occur

  //    data.take(5) foreach println

  val maxTemp = data.map(_.tmax).max
  val hotDays = data.filter(_.tmax == maxTemp)
  println(s"Hot days are ${hotDays.collect().mkString(", ")}")

  println(data.max()(Ordering.by(_.tmax)))
  println(data.reduce((td1, td2) => if (td1.tmax >= td2.tmax) td1 else td2))

  val rainyCount = data.filter(_.precip >= 1.0).count // this is bad on a
  // normal Scala collection, because filter on a normal Scala collection
  // is eager and will build an entire new collection
  // however in Spark this is not nearly as inefficient
  println(s"There are $rainyCount rainy days. There is ${
    rainyCount *
      100.0 / data.count
  } percent")

  val (rainySum, rainyCount2) = data.aggregate(0.0 -> 0)(
    { case ((sum,
    cnt), td) => if (td.precip < 1.0) (sum, cnt) else (sum + td.tmax, cnt +
      1)
    },
    { case ((s1, c1), (s2, c2)) => (s1 + s2, c1 + c2) }
  )
  println(s"Average Rainy temp is ${rainySum / rainyCount2}")

  // could use flatMap to achieve the aggregate as well
  val rainyTemps = data.flatMap(td => if (td.precip < 1.0) Seq.empty else
    Seq(td.tmax))
  println(s"Average Rainy temp is ${rainyTemps.sum / rainyTemps.count}")

  val monthGroups = data.groupBy(_.month)
  val monthlyHighTemp = monthGroups.map {
    case (m, days) =>
      m -> days.foldLeft(0.0)((sum, td) => sum + td.tmax) / days.size
  }

  val monthlyLowTemp = monthGroups.map {
    case (m, days) =>
      m -> days.foldLeft(0.0)((sum, td) => sum + td.tmin) / days.size
  }
  // RDD is distributed across many executors, so foreach does not
  // necessarily run through tings in order, could use collect to bring to
  // an Array type, so we can get it in order
  monthlyHighTemp.collect.sortBy(_._1) foreach println

  val plot = Plot.scatterPlots(Seq(
    (monthlyHighTemp.map(_._1).collect, monthlyHighTemp.map(_._2).collect,
      0xffff0000, 5),
    (monthlyLowTemp.map(_._1).collect, monthlyLowTemp.map(_._2).collect,
      0xff0000ff, 5)

  ), "Temps", "Month", "Temperature")

  //  FXRenderer(plot, 800, 600)

  // RDDDoubleFunctions for statistics
  println("Stdev of highs: " + data.map(_.tmax).stdev)
  println("Stdev of lows: " + data.map(_.tmin).stdev)
  println("Stdev of avgs: " + data.map(_.tave).stdev)
}
