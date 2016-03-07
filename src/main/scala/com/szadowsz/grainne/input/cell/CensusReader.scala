package com.szadowsz.grainne.input.cell

import java.io.StringReader

import com.szadowsz.grainne.data.CensusDataBean
import org.supercsv.cellprocessor.ift.CellProcessor
import org.supercsv.io.CsvBeanReader
import org.supercsv.prefs.CsvPreference

object CensusReader {

  def apply(headers: Seq[String], cells: Seq[CellProcessor]): CensusReader = new CensusReader(headers, cells)
}


/**
  * Created by zakski on 24/11/2015.
  */
final class CensusReader(headers: Seq[String], cells: Seq[CellProcessor]) {

  private val _beanClass = classOf[CensusDataBean]

  private val _headers = headers

  private val _cells = cells

  private val _prefs = CsvPreference.STANDARD_PREFERENCE

  def read(row: String): CensusDataBean = {
    val csvReader = new CsvBeanReader(new StringReader(row), CsvPreference.STANDARD_PREFERENCE)
    val bean: CensusDataBean = csvReader.read(_beanClass, _headers.toArray, _cells: _*)
    csvReader.close()
    bean
  }

}