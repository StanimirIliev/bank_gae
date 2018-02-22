package helpers

import com.clouway.app.core.Server
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig
import com.google.appengine.tools.development.testing.LocalServiceTestHelper
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import org.junit.rules.ExternalResource
import spark.Filter
import spark.Spark
import java.io.File
import java.time.LocalDateTime
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

class E2EHelper(private val server: Server, enableLogging: Boolean) : ExternalResource() {

    val primaryUrl = "http://127.0.0.1:8080"
    val gson = GsonBuilder()
            .registerTypeAdapter(LocalDateTime::class.java, object : TypeAdapter<LocalDateTime>() {
                override fun read(`in`: JsonReader): LocalDateTime {
                    println("Read method was called")
                    val value = `in`.nextString()
                    return LocalDateTime.parse(value)
                }

                override fun write(out: JsonWriter, value: LocalDateTime?) {
                    out.value(value.toString())
                }
            }).create()!!
    val requestFactory = NetHttpTransport().createRequestFactory()!!

    private val configDatastore = LocalDatastoreServiceTestConfig()
            .setApplyAllHighRepJobPolicy()
            .setNoStorage(false)
            .setBackingStoreLocation("tmp/local_db.bin")
    private val helper = LocalServiceTestHelper(configDatastore)

    init {
        if (enableLogging) {
            enableLogging()
        }
    }

    override fun before() {
        server.start()
        server.awaitInitialization()
        Spark.before(Filter { _, _ -> helper.setUp() })
        Spark.after(Filter { _, _ -> helper.tearDown() })
    }

    override fun after() {
        server.stop()
        Thread.currentThread().join(10)// waits the server to stop
        File(configDatastore.backingStoreLocation).delete()
    }

    fun registerUserAndGetSessionId(url: String, username: String, password: String): String {
        val requestBody = "username=$username&password=$password&confirmPassword=$password"
        val postRequest = requestFactory.buildPostRequest(
                GenericUrl(url),
                ByteArrayContent.fromString("application/json", requestBody)
        )
        postRequest.followRedirects = false
        postRequest.throwExceptionOnExecuteError = false
        val response = postRequest.execute()
        return response.headers["set-cookie"].toString().trim('[', ']')
    }

    private fun enableLogging() {
        val logger = Logger.getLogger(HttpTransport::class.java.name)
        logger.level = Level.ALL
        logger.addHandler(object : Handler() {

            @Throws(SecurityException::class)
            override fun close() {
            }

            override fun flush() {}

            override fun publish(record: LogRecord) {
                System.out.println(record.message)
            }
        })
    }
}