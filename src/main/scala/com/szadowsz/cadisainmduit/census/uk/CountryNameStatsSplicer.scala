package com.szadowsz.cadisainmduit.census.uk

import java.io.File

import com.szadowsz.cadisainmduit.census.CensusHandler
import com.szadowsz.common.io.delete.DeleteUtil
import com.szadowsz.common.io.explore.{ExtensionFilter, FileFinder}
import com.szadowsz.common.io.read.FReader
import com.szadowsz.common.io.write.CsvWriter
import com.szadowsz.common.io.zip.ZipperUtil
import com.szadowsz.ulster.spark.Lineage
import com.szadowsz.ulster.spark.transformers.math.{CounterTransformer, DivisionTransformer}
import com.szadowsz.ulster.spark.transformers.math.vec.AverageTransformer
import com.szadowsz.ulster.spark.transformers.string.StringFiller
import com.szadowsz.ulster.spark.transformers.string.spelling.{CapitalisationTransformer, RegexValidationTransformer}
import com.szadowsz.ulster.spark.transformers.CsvTransformer
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.sql.types.{IntegerType, StringType, StructField, StructType}
import org.apache.spark.sql.{Row, SparkSession, _}
import org.slf4j.LoggerFactory

/**
  * Created on 13/01/2017.
  */
trait CountryNameStatsSplicer extends CensusHandler {
  protected val logger = LoggerFactory.getLogger(this.getClass)

  protected def extractFile(sess: SparkSession, f: File): (String, DataFrame) = {
    val year = f.getName.substring(0, 4) // assume the year is the first 4 characters of the file name
    val r = new FReader(f.getAbsolutePath)
    val stringRdd = sess.sparkContext.parallelize(r.lines().toArray.drop(1)) // assume the first line is the header
    val rowRDD = stringRdd.map(s => Row.fromSeq(List(s)))
    val df = sess.createDataFrame(rowRDD, StructType(Array(StructField("fields", StringType))))
    df.cache() // cache the constructed dataframe
    (year, df)
  }

  protected def getInitialCols(country: String, year: String): Array[String] = {
    Array("name", s"${country}_count_$year", s"${country}_rank_$year")
  }


  protected def selectStdCols(country: String, year: String, tmp: DataFrame): DataFrame = {
    tmp.select("name", "gender", s"${country}_count_$year")
  }

  protected def spliceYearData(sess: SparkSession, path: String, country: String, gender: Char): DataFrame = {
    val files = FileFinder.search(path, Some(new ExtensionFilter(".csv", false)))
    val dfs = files.map { f =>
      val (year, df) = extractFile(sess, f)
      df.cache()

      val pipe: Lineage = buildStdPipeline(s"$year-$country-caps", getInitialCols(country, year), Option(gender))

      val model = pipe.fit(df)
      val tmp = model.transform(df)
      selectStdCols(country, year, tmp)
    }
    join(dfs)
  }

  protected def aggData(country: String, boys: DataFrame, girls: DataFrame): DataFrame = {
    val all = boys.union(girls)
    val appFields = all.schema.fieldNames.filter(f => f.contains("count") || f.contains("rank"))
    val popFields = all.schema.fieldNames.filter(f => f.contains("count"))

    val pipe: Lineage = buildFractionPipeline(s"$country-frac",country, appFields,popFields)
    val m = pipe.fit(all)
    m.transform(all)
  }

  def loadData(save: Boolean): DataFrame

  def loadData(country: String, root: String, archiveRoot: String, save: Boolean): DataFrame = {
    DeleteUtil.delete(new File(s"$root/boys"))
    DeleteUtil.delete(new File(s"$root/girls"))
    ZipperUtil.unzip(s"$archiveRoot-boys.zip", root + "/")
    ZipperUtil.unzip(s"$archiveRoot-girls.zip", root + "/")

    val sess = SparkSession.builder()
      .config("spark.driver.host", "localhost")
      .master("local[6]")
      .getOrCreate()

    val boys: DataFrame = spliceYearData(sess, s"$root/boys/", country, 'M')

    if (boys.count() < 1) {
      DeleteUtil.delete(new File(s"$root/boys"))
      throw new RuntimeException(boys.schema.fieldNames.mkString("|"))
    }

    val girls: DataFrame = spliceYearData(sess, s"$root/girls/", country, 'F')

    if (girls.count() < 1) {
      DeleteUtil.delete(new File(s"$root/boys"))
      DeleteUtil.delete(new File(s"$root/girls"))
      throw new RuntimeException(girls.schema.fieldNames.mkString("|"))
    }

    val children: DataFrame = aggData(country, boys, girls)

    if (save) {
      val ord: Ordering[Seq[String]] = Ordering.by(seq => (seq.last, seq.head))
      writeDF(children, s"$root/baby_names.csv", "UTF-8", (seq: Seq[String]) => seq.head.length > 0, ord)
    }

    DeleteUtil.delete(new File(s"$root/boys"))
    DeleteUtil.delete(new File(s"$root/girls"))
    children
  }
}
