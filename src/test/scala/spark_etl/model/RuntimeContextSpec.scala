package spark_etl.model

import org.apache.spark.sql.{DataFrame, SparkSession}
import org.scalatest.{FlatSpec, Inside, Matchers}
import spark_etl.{ConfigError, ExtractReader, LoadWriter}

import scalaz.Scalaz._
import scalaz._

class RuntimeContextSpec extends FlatSpec with Matchers with Inside {
  val extractsAndTransformsStr =
    """
      |extracts:
      |  - name:  client
      |    uri:   "data/dev/client_2017"
      |    check: "/spark/extract-check/client.sql"
      |  - name:  item
      |    uri:   "data/dev/item_2017"
      |    check: "/spark/extract-check/item.sql"
      |  - name:  transaction
      |    uri:   "data/dev/transaction_2017"
      |    check: "/spark/extract-check/transaction.sql"
      |  # unused extract
      |  - name:  ____bogus_extract_not_loaded____
      |    uri:   "hdfs://aaa.bbb"
      |
      |transforms:
      |  - name:  client_spending
      |    check: "/spark/transform-check/client_spending.sql"
      |    sql:   "/spark/transform/client_spending.sql"
      |  - name:  item_purchase
      |    check: "/spark/transform-check/item_purchase.sql"
      |    sql:   "/spark/transform/item_purchase.sql"
      |  - name:  minor_purchase
      |    check: "/spark/transform-check/minor_purchase.sql"
      |    sql:   "/spark/transform/minor_purchase.sql"
      |
      |loads:
      |  - name:   client_spending_out
      |    source: client_spending
      |    uri:    "/tmp/out/client_spending"
      |    # no partition_by
      |  - name:   item_purchase_out
      |    source: item_purchase
      |    uri:    "/tmp/out/item_purchase"
      |    # no partition_by
      |  - name:   minor_purchase_out
      |    source: minor_purchase
      |    uri:    "/tmp/out/minor_purchase"
      |    # no partition_by
      |    """.stripMargin

  "RuntimeContext" should "validate ok extract_reader/load_writer" in {
    val confStr = extractsAndTransformsStr +
      """
        |extract_reader:
        |  class: spark_etl.model.OkExtractReader
        |  params:
        |    x: 11
        |    y: aa
        |
        |load_writer:
        |  class: spark_etl.model.OkLoadWriter
        |  params:
        |    b: false
        |    a: [1, xxx]
      """.stripMargin
    Config.parse(confStr) match {
      case Success(conf) =>
        RuntimeContext.load(conf) match {
          case Success(ctx) =>
            ctx.extractReader.asInstanceOf[OkExtractReader].params shouldBe Map("x" -> 11d, "y" -> "aa")
            ctx.loadWriter.asInstanceOf[OkLoadWriter].params shouldBe Map("b" -> false, "a" -> List(1d, "xxx"))
        }
    }
  }

  it should "fail on incorrect inheritance of extract_reader/load_writer" in {
    val confStr = extractsAndTransformsStr +
      """
        |extract_reader:
        |  class: spark_etl.model.BogusExtractReader1
        |
        |load_writer:
        |  class: spark_etl.model.BogusLoadWriter1
      """.stripMargin
    Config.parse(confStr) match {
      case Success(conf) =>
        RuntimeContext.load(conf) match {
          case Failure(errs) =>
            errs.toList.length shouldBe 2
            errs.toList.forall(_.msg.startsWith("Failed to cast class")) shouldBe true
        }
    }
  }

  it should "fail on parameterless constructors extract_reader/load_writer" in {
    val confStr = extractsAndTransformsStr +
      """
        |extract_reader:
        |  class: spark_etl.model.BogusExtractReader2
        |
        |load_writer:
        |  class: spark_etl.model.BogusLoadWriter2
      """.stripMargin
    Config.parse(confStr) match {
      case Success(conf) =>
        RuntimeContext.load(conf) match {
          case Failure(errs) =>
            errs.toList.length shouldBe 2
            errs.toList.forall(_.msg.startsWith("Failed to instantiate class")) shouldBe true
        }
    }
  }
}

class OkExtractReader(val params: Map[String, Any]) extends ExtractReader(params) {
  override def checkLocal(extracts: Seq[Extract]): ValidationNel[ConfigError, Unit] = ().successNel[ConfigError]
  override def checkRemote(extracts: Seq[Extract]): ValidationNel[ConfigError, Unit] = ???
  override def read(extracts: Seq[Extract])(implicit spark: SparkSession): Seq[(Extract, DataFrame)] = ???
}

class OkLoadWriter(val params: Map[String, Any]) extends LoadWriter(params) {
  override def write(loadsAndDfs: Seq[(Load, DataFrame)]): Unit = ???
  override def checkLocal(loads: Seq[Load]): ValidationNel[ConfigError, Unit] = ().successNel[ConfigError]
  override def checkRemote(loads: Seq[Load]): ValidationNel[ConfigError, Unit] = ???
}

class BogusExtractReader1(params: Map[String, Any])

class BogusLoadWriter1(params: Map[String, Any])

class BogusExtractReader2 extends ExtractReader(Map.empty) {
  override def checkLocal(extracts: Seq[Extract]): ValidationNel[ConfigError, Unit] = ().successNel[ConfigError]
  override def checkRemote(extracts: Seq[Extract]): ValidationNel[ConfigError, Unit] = ???
  override def read(extracts: Seq[Extract])(implicit spark: SparkSession): Seq[(Extract, DataFrame)] = ???
}

class BogusLoadWriter2 extends LoadWriter(Map.empty) {
  override def write(loadsAndDfs: Seq[(Load, DataFrame)]): Unit = ???
  override def checkLocal(loads: Seq[Load]): ValidationNel[ConfigError, Unit] = ().successNel[ConfigError]
  override def checkRemote(loads: Seq[Load]): ValidationNel[ConfigError, Unit] = ???
}
