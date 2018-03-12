package `in`.ashishchaudhary.smartresizing.taskdispatcher

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.experimental.async
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

class TaskDispatcher(
    private val TASK_QUEUE: String, private val RESULTS_QUEUE: String, host: String
) {
    private val factory = ConnectionFactory()
    private val connection = factory.newConnection()
    private val channel = connection.createChannel()
    private val resultListener: Consumer
    private val resultPattern =
        Regex("(Session=\\w+)\\|(Status=\\w+)\\|(Filepath=/[a-zA-Z0-9_./]+)")
    private val sockets = ConcurrentHashMap<String, WebSocketSession>()

    data class Message(val session: String, val width: Int, val height: Int, val filePath: Path)

    private sealed class Result {
        data class Failure(val session: String, val reason: String) : Result()
        data class Success(val session: String, val filePath: Path) : Result()
    }

    private inner class Consumer : DefaultConsumer(channel) {
        override fun handleDelivery(
            consumerTag: String,
            envelope: Envelope,
            properties: AMQP.BasicProperties,
            body: ByteArray
        ) {
            val result = parseResult(body)
            when (result) {
                is Result.Failure -> {
                    async {
                        sockets[result.session]?.send(
                            Frame.Text("Status=Failure|Reason=${result.reason}")
                        )
                    }
                }
                is Result.Success -> {
                    async {
                        sockets[result.session]?.send(
                            Frame.Text("Status=Success|Filepath=${result.filePath}")
                        )
                    }
                }
            }
        }
    }

    init {
        factory.host = host
        channel.queueDeclare(TASK_QUEUE, false, false, false, null)
        channel.queueDeclare(RESULTS_QUEUE, false, false, false, null)
        resultListener = Consumer()
        listenResults()
    }

    fun addSocket(sessionId: String, socket: WebSocketSession) {
        sockets[sessionId] = socket
    }

    fun removeSocket(sessionId: String) {
        sockets.remove(sessionId)
    }

    private fun parseResult(body: ByteArray): Result {
        val message = String(body)
        if (!resultPattern.matches(message)) return Result.Failure(
            "Null", "Failed to parse the result"
        )
        val groupValues = resultPattern.find(message)!!.groupValues.map { it.split("=")[1] }
        val status = groupValues[2]
        return when (status) {
            "Failure" -> Result.Failure(
                groupValues[1], groupValues[3]
            )
            else -> Result.Success(
                groupValues[1], File(groupValues[3]).toPath()
            )
        }
    }

    private fun serializeMessage(message: Message): ByteArray {
        return "Session=${message.session}|Width=${message.width}|Height=${message.height}|Filepath=${message.filePath}".toByteArray()
    }

    fun enqueueTask(message: Message) {
        channel.basicPublish("", TASK_QUEUE, null, serializeMessage(message))
    }

    private fun listenResults() {
        channel.basicConsume(RESULTS_QUEUE, true, resultListener)
    }

    fun closeChannel() = channel.close()
    fun closeConnection() = connection.close()
}