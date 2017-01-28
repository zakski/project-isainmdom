package com.szadowsz.cadisainmduit.census.uk.norire

import java.io.File

import com.szadowsz.cadisainmduit.census.uk.CountryNameStatsSplicer
import org.apache.spark.sql.DataFrame

/**
  * Built to stitch all england and wales popular name data together
  *
  * Created on 19/10/2016.
  */
object NorireNamesStatsSplicer extends CountryNameStatsSplicer{

  override def loadData(save: Boolean): DataFrame = loadData("NI","./data/census/norire/","./archives/data/census/norire/common/norirefirstnames",save)

  def main(args: Array[String]): Unit = {
    loadData(true)
  }
}

