package `in`.ashishchaudhary.smartresizing.server

import `in`.ashishchaudhary.smartresizing.Config
import io.ktor.application.Application
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.content.*
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.gson.gson
import io.ktor.http.HttpStatusCode
import io.ktor.request.isMultipart
import io.ktor.request.receiveMultipart
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.sessions.Sessions
import io.ktor.sessions.cookie
import io.ktor.sessions.sessions
import io.ktor.util.nextNonce
import io.ktor.websocket.*
import kotlinx.coroutines.experimental.channels.consumeEach
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.DateFormat
import java.time.Duration

fun Application.main() {
    install(DefaultHeaders)
    install(CallLogging)
    install(ContentNegotiation) {
        gson {
            setDateFormat(DateFormat.LONG)
            setPrettyPrinting()
        }
    }
    install(WebSockets) {
        pingPeriod = Duration.ofMinutes(1)
    }

    install(Routing) {
        install(Sessions) {
            cookie<Models.Session>("SESSION")
        }

        intercept(ApplicationCallPipeline.Infrastructure) {
            if (call.sessions.get("SESSION") == null) {
                call.sessions.set("SESSION", Models.Session(nextNonce()))
            }
        }

        webSocket("/ws") {
            val session = call.sessions.get("SESSION") as Models.Session?
            if (session == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No session"))
                return@webSocket
            }

            try {
                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        receivedMessage(session.id, frame.readText())
                    }
                }
            } catch (e: Exception) {
                println(e.message)
            }
        }

        post("/upload") {
            val multipart = call.receiveMultipart()
            if (!call.request.isMultipart()) {
                call.respondText { "Improper form data" }
                return@post
            }
            val mpr = Models.MultipartRequest()
            mpr.id = (call.sessions.get("SESSION") as Models.Session).id
            while (true) {
                val part = multipart.readPart() ?: break
                if (part is PartData.FileItem) if (part.partName == "file") {
                    mpr.fileName = part.originalFileName
                    mpr.inStream = part.streamProvider()
                }
            }
            if (mpr.id == null || mpr.fileName == null || mpr.inStream == null) {
                call.respond(HttpStatusCode.BadRequest, "Illegal form format")
                return@post
            }
            try {
                val baseDir = File(System.getProperty("java.io.tmpdir"))
                val seamDir = File(baseDir, "seam")
                if (!seamDir.exists()) seamDir.mkdir()
                val blobDir = File(seamDir, mpr.id)
                if (!blobDir.exists()) blobDir.mkdir()
                val filePath = File(blobDir, mpr.fileName).toPath()
                Files.copy(mpr.inStream, filePath, StandardCopyOption.REPLACE_EXISTING)
                call.respondText { "Success" }
            } catch (e: Exception) {
                println(e.message)
                call.respond(HttpStatusCode.InternalServerError, "Something went wrong")
            } finally {
                (mpr.inStream as InputStream).close()
            }
        }

        get("/demo-images") {
            call.respond(Config.preloadedImages)
        }

        static {
            defaultResource("index.html", "web")
            resources("web")
        }

        static("file") {
            staticRootFolder = File(System.getProperty("java.io.tmpdir"))
            files(File(staticRootFolder, "seam"))
        }
    }
}

private fun receivedMessage(id: String, command: String) {
    println("id: $id, command: $command")
}
