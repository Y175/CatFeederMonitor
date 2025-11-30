package com.example.catfeedermonitor.logic

import android.content.Context
import com.example.catfeedermonitor.data.FeedingDao
import io.ktor.serialization.gson.gson
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

class WebServer(
    private val appContext: Context,
    private val dao: FeedingDao
) {
    private var server: NettyApplicationEngine? = null

    fun start() {
        if (server != null) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                server = embeddedServer(Netty, port = 8080) {
                    install(ContentNegotiation) {
                        gson {
                            setPrettyPrinting()
                        }
                    }

                    routing {
                        // Serve static files (HTML, JS, CSS)
                        // We will serve index.html from assets
                        get("/") {
                            try {
                                val indexHtml = appContext.assets.open("web/index.html").bufferedReader().use { it.readText() }
                                call.respondText(indexHtml, io.ktor.http.ContentType.Text.Html)
                            } catch (e: Exception) {
                                call.respondText("Error loading index.html: ${e.message}", status = io.ktor.http.HttpStatusCode.InternalServerError)
                            }
                        }

                        // API: Get recent records
                        get("/api/records") {
                            val records = dao.getRecentRecordsSync()
                            call.respond(records)
                        }

                        // API: Get stats (last 7 days)
                        get("/api/stats") {
                            val oneWeekAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000
                            val records = dao.getRecordsSinceSync(oneWeekAgo)
                            call.respond(records)
                        }

                        // Serve images
                        get("/images/{imageName}") {
                            val imageName = call.parameters["imageName"]
                            if (imageName != null) {
                                // Images are stored in external files dir
                                val dir = appContext.getExternalFilesDir(null)
                                val file = File(dir, imageName)

                                if (file.exists()) {
                                    call.respondFile(file)
                                } else {
                                    call.respond(io.ktor.http.HttpStatusCode.NotFound)
                                }
                            }
                        }
                    }
                }.start(wait = false)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stop() {
        server?.stop(1000, 1000)
        server = null
    }

    fun getIpAddress(): String {
        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.hostAddress?.toString() ?: ""
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return "Unavailable"
    }
}
