package `in`.ashishchaudhary.smartresizing.taskrunner

import `in`.ashishchaudhary.smartresizing.Config.RESULTS_QUEUE
import `in`.ashishchaudhary.smartresizing.Config.TASK_QUEUE
import `in`.ashishchaudhary.smartresizing.Config.host
import `in`.ashishchaudhary.smartresizing.task.SeamCarver
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import java.io.File
import java.nio.file.Path

class TaskRunner(private val TASK_QUEUE: String, private val RESULTS_QUEUE: String, host: String) {
    private val factory = ConnectionFactory()
    private val connection = factory.newConnection()
    private val channel = connection.createChannel()
    private val consumer: Consumer
    private val messagePattern = Regex(
        "(Session=\\w+)\\|(Width=\\d+)\\|(Height=\\d+)\\|(Filepath=/[a-zA-Z0-9_./]+)"
    )

    data class Message(val session: String, val width: Int, val height: Int, val filePath: Path)

    sealed class Result {
        data class Failure(val session: String, val reason: String) : Result()
        data class Success(val session: String, val filePath: Path) : Result()
    }

    inner class Consumer : DefaultConsumer(channel) {
        override fun handleDelivery(
            consumerTag: String,
            envelope: Envelope,
            properties: AMQP.BasicProperties,
            body: ByteArray
        ) {
            val message = parseMessage(body)
            if (message == null) {
                enqueueResult(
                    serializeResult(
                        Result.Failure("Null", "Failed to parse the message")
                    )
                )
            } else {
                val result = doTask(message)
                enqueueResult(serializeResult(result))
            }
            channel.basicAck(envelope.deliveryTag, false)
        }
    }

    init {
        factory.host = host
        channel.queueDeclare(TASK_QUEUE, false, false, false, null)
        channel.queueDeclare(RESULTS_QUEUE, false, false, false, null)
        consumer = Consumer()
    }

    fun parseMessage(body: ByteArray): Message? {
        val message = String(body)
        if (!messagePattern.matches(message)) return null
        val groupValues = messagePattern.find(message)!!.groupValues.map { it.split("=")[1] }
        return Message(
            groupValues[1],
            groupValues[2].toInt(),
            groupValues[3].toInt(),
            File(groupValues[4]).toPath()
        )
    }

    fun serializeResult(result: Result): ByteArray {
        return when (result) {
            is Result.Failure -> "Session=${result.session}|Status=Failure|Reason=${result.reason}"
            is Result.Success -> "Session=${result.session}|Status=Success|Filepath=${result.filePath}"
        }.toByteArray()
    }

    fun doTask(message: Message): Result {
        return try {
            val sc = SeamCarver(message.filePath, message.width, message.height)
            val resizedFilePath = sc.executeActionSequence()
            Result.Success(message.session, resizedFilePath)
        } catch (e: Exception) {
            Result.Failure(message.session, e.message ?: "")
        }
    }

    fun enqueueResult(message: ByteArray) {
        channel.basicPublish("", RESULTS_QUEUE, null, message)
    }

    fun consume() {
        channel.basicConsume(TASK_QUEUE, false, consumer)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val taskRunner = TaskRunner(TASK_QUEUE, RESULTS_QUEUE, host)
            taskRunner.consume()
        }
    }
}