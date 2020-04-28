// Databricks notebook source
// MAGIC %md #### Práctica 2: Procesamiento de tweets en tiempo real
// MAGIC Paso inicial: registrarse en https://developer.twitter.com/en/apps
// MAGIC 
// MAGIC https://www.dataneb.com/post/analyzing-twitter-texts-spark-streaming-example-2

// COMMAND ----------

// Verificamos la versión de Spark
if (org.apache.spark.SPARK_VERSION < "2.0") throw new Exception("Please use a Spark 2.0 (apache/branch-2.0 preview) cluster.")

// COMMAND ----------

// Añadimos las llaves para la autenticación con twitter
val consumerKey = dbutils.widgets.get("consumerKey") //crea una caja de texto para introducir las llaves
val consumerSecret = dbutils.widgets.get("consumerSecret")
val accessToken = dbutils.widgets.get("accessToken")
val accessTokenSecret = dbutils.widgets.get("accessTokenSecret")
val path = dbutils.widgets.get("dataPath")
val savingInterval = 2000 // creamos un nuevo archivo cada 2 segundos
val filters = Array("QuédateEnCasa", "COVID19", "Covid", "SanaDistancia")

// COMMAND ----------

// Se define una clase para descargar los tweets
import java.io.{BufferedReader, File, FileNotFoundException, InputStream, InputStreamReader}
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import scala.collection.JavaConverters._
import org.apache.commons.io.IOUtils
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients

class TwitterStream(
  consumerKey: String,
  consumerSecret: String,
  accessToken: String,
  accessTokenSecret: String,
  path: String,
  savingInterval: Long,
  filters: Array[String]) {
  
  private val threadName = "tweet-downloader"
  
  {
    // Throw an exception if there is already an active stream.
    // We do this check at here to prevent users from overriding the existing
    // TwitterStream and losing the reference of the active stream.
    val hasActiveStream = Thread.getAllStackTraces().keySet().asScala.map(_.getName).contains(threadName)
    if (hasActiveStream) {
      throw new RuntimeException(
        "There is already an active stream that writes tweets to the configured path. " +
        "Please stop the existing stream first (using twitterStream.stop()).")
    }
  }
  
  @volatile private var thread: Thread = null
  @volatile private var isStopped = false
  @volatile var isDownloading = false
  @volatile var exception: Throwable = null

  private var httpclient: CloseableHttpClient = null
  private var input: InputStream = null
  private var httpGet: HttpGet = null
  
  private def encode(string: String): String = {
    URLEncoder.encode(string, StandardCharsets.UTF_8.name)
  }

  def start(): Unit = synchronized {
    isDownloading = false
    isStopped = false
    thread = new Thread(threadName) {
      override def run(): Unit = {
        httpclient = HttpClients.createDefault()
        try {
          requestStream(httpclient)
        } catch {
          case e: Throwable => exception = e
        } finally {
          TwitterStream.this.stop()
        }
      }
    }
    thread.start()
  }

  private def requestStream(httpclient: CloseableHttpClient): Unit = {
    val url = "https://stream.twitter.com/1.1/statuses/filter.json"
    val timestamp = System.currentTimeMillis / 1000
    val nonce = timestamp + scala.util.Random.nextInt
    val oauthNonce = nonce.toString
    val oauthTimestamp = timestamp.toString

    val oauthHeaderParams = List(
      "oauth_consumer_key" -> encode(consumerKey),
      "oauth_signature_method" -> encode("HMAC-SHA1"),
      "oauth_timestamp" -> encode(oauthTimestamp),
      "oauth_nonce" -> encode(oauthNonce),
      "oauth_token" -> encode(accessToken),
      "oauth_version" -> "1.0"
    )
    // Parameters used by requests
    // See https://dev.twitter.com/streaming/overview/request-parameters for a complete list of available parameters.
    val requestParams = List(
      "track" -> encode(filters.mkString(","))
    )

    val parameters = (oauthHeaderParams ++ requestParams).sortBy(_._1).map(pair => s"""${pair._1}=${pair._2}""").mkString("&")
    val base = s"GET&${encode(url)}&${encode(parameters)}"
    val oauthBaseString: String = base.toString
    val signature = generateSignature(oauthBaseString)
    val oauthFinalHeaderParams = oauthHeaderParams ::: List("oauth_signature" -> encode(signature))
    val authHeader = "OAuth " + ((oauthFinalHeaderParams.sortBy(_._1).map(pair => s"""${pair._1}="${pair._2}"""")).mkString(", "))

    httpGet = new HttpGet(s"https://stream.twitter.com/1.1/statuses/filter.json?${requestParams.map(pair => s"""${pair._1}=${pair._2}""").mkString("&")}")
    httpGet.addHeader("Authorization", authHeader)
    println("Downloading tweets!")
    val response = httpclient.execute(httpGet)
    val entity = response.getEntity()
    input = entity.getContent()
    if (response.getStatusLine.getStatusCode != 200) {
      throw new RuntimeException(IOUtils.toString(input, StandardCharsets.UTF_8))
    }
    isDownloading = true
    val reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
    var line: String = null
    var lineno = 1
    line = reader.readLine()
    var lastSavingTime = System.currentTimeMillis()
    val s = new StringBuilder()
    while (line != null && !isStopped) {
      lineno += 1
      line = reader.readLine()
      s.append(line + "\n")
      val now = System.currentTimeMillis()
      if (now - lastSavingTime >= savingInterval) {
        val file = new File(path, now.toString).getAbsolutePath
        println("saving to " + file)
        dbutils.fs.put(file, s.toString, true)
        lastSavingTime = now
        s.clear()
      }
    }
  }

  private def generateSignature(data: String): String = {
    val mac = Mac.getInstance("HmacSHA1")
    val oauthSignature = encode(consumerSecret) + "&" + encode(accessTokenSecret)
    val spec = new SecretKeySpec(oauthSignature.getBytes, "HmacSHA1")
    mac.init(spec)
    val byteHMAC = mac.doFinal(data.getBytes)
    return Base64.getEncoder.encodeToString(byteHMAC)
  }

  def stop(): Unit = synchronized {
    isStopped = true
    isDownloading = false
    try {
      if (httpGet != null) {
        httpGet.abort()
        httpGet = null
      }
      if (input != null) {
        input.close()
        input = null
      }
      if (httpclient != null) {
        httpclient.close()
        httpclient = null
      }
      if (thread != null) {
        thread.interrupt()
        thread = null
      }
    } catch {
      case _: Throwable =>
    }
  }
}

// COMMAND ----------

// Se inicializa la conexión con Twitter para comenzar la descarga de tweets
dbutils.fs.mkdirs(path)

val twitterStream = new TwitterStream(consumerKey, consumerSecret, accessToken, accessTokenSecret, path, savingInterval, filters)
twitterStream.start()
while (!twitterStream.isDownloading && twitterStream.exception == null) {
  Thread.sleep(100)
}
if (twitterStream.exception != null) {
  throw twitterStream.exception
}

// COMMAND ----------

// DBTITLE 0,Sleep 30 seconds to collect some initial data
//Recolectamos tweets por 30 segundos
Thread.sleep(30000)

// COMMAND ----------

// MAGIC %md ## Demos un vistazo a los tweets descargados :)

// COMMAND ----------

display(spark.read.text(path))

// COMMAND ----------

// MAGIC %md ## Ahora, ya podemos hacer consultas con los tweets descargados

// COMMAND ----------

import java.sql.Timestamp

import org.apache.spark.sql.functions.{from_unixtime, unix_timestamp, window}
import org.apache.spark.sql.types.{StructType, StructField, StringType}

case class Tweet(start: Timestamp, text: String)
val clean = udf((s: String) => if (s == null) null else s.replaceAll("[^\\x20-\\x7e]", "")) //eliminamos caracteres especiales

// Cargamos los tweets en formato JSON
val dataset = spark.read.json(path) // el esquema será automaticamente inferido

// Creamos una lista de palabras candidatas que buscaremos sobre los tweets descargados
val candidates = Seq("covid", "hospital","trump")

// Assign tweets to 1-second time windows.
dataset
  .where("created_at IS NOT null")
  .withColumn("text", clean($"text"))
  .withColumn("created_at", from_unixtime(unix_timestamp($"created_at", "EEE MMM dd HH:mm:ss ZZZZ yyyy")))
  .select(
    window('created_at, "1 second").getField("start") as 'start,
    'text)
  .as[Tweet]
  .flatMap { case tweet =>
    candidates.filter(tweet.text.toLowerCase.contains).map(candidate => candidate -> tweet.start)
  }
  .toDF("candidate", "start")
  .createOrReplaceTempView("tweets")

// COMMAND ----------

// Contamos el número tweets por cada palabra candidata
display(sql("""
  SELECT count(*), candidate, start
  FROM tweets
  GROUP BY candidate, start
  ORDER BY candidate, start
"""))

// COMMAND ----------

display(sql("""
  SELECT count(*), candidate
  FROM tweets
  GROUP BY candidate
"""))

// COMMAND ----------

// MAGIC %sql
// MAGIC select * from tweets;

// COMMAND ----------

dataset.createOrReplaceTempView("tweets")

// COMMAND ----------

// MAGIC %sql
// MAGIC select text from tweets

// COMMAND ----------

twitterStream.stop() // Detenemos la descarga de los tweets
//dbutils.fs.rm(path, true) // Para eliminar los tweets descargados
