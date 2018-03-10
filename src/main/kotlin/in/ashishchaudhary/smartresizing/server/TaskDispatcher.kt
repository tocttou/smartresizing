package `in`.ashishchaudhary.smartresizing.server

import `in`.ashishchaudhary.smartresizing.Config.RESULTS_QUEUE
import `in`.ashishchaudhary.smartresizing.Config.TASK_QUEUE
import `in`.ashishchaudhary.smartresizing.Config.host
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope

class TaskDispatcher(
    private val TASK_QUEUE: String, private val RESULTS_QUEUE: String, host: String
) {
    private val factory = ConnectionFactory()
    private val connection = factory.newConnection()
    private val channel = connection.createChannel()
    private val resultListener: Consumer
    private val resultPattern = Regex(
        "(Session=\\w+)\\|(Status=\\w+)\\|(Filepath=/[a-zA-Z0-9_/]+)", RegexOption.MULTILINE
    )

    data class Message(val session: String, val filePath: String)

    private sealed class Result {
        data class Failure(val session: String, val reason: String) : Result()
        data class Success(val session: String, val filePath: String) : Result()
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
                is Result.Failure -> println("Failure in dispatcher (result): ${result.reason}")
                is Result.Success -> println("Success in dispatcher (result): ${result.filePath}")
            }
        }
    }

    init {
        factory.host = host
        channel.queueDeclare(TASK_QUEUE, false, false, false, null)
        channel.queueDeclare(RESULTS_QUEUE, false, false, false, null)
        resultListener = Consumer()
    }

    private fun parseResult(body: ByteArray): Result {
        val message = String(body)
        if (!resultPattern.matches(message)) return Result.Failure(
            "Null", "Failed to parse the result"
        )
        val groupValues = resultPattern.find(message)!!.groupValues.map { it.split("=")[1] }
        val status = groupValues[2]
        return when (status) {
            "Failure" -> Result.Failure(groupValues[1], groupValues[3])
            else -> Result.Success(groupValues[1], groupValues[3])
        }
    }

    private fun serializeMessage(message: Message): ByteArray {
        return "Session=${message.session}|Filepath=${message.filePath}".toByteArray()
    }

    fun enqueueTask(message: Message) {
        channel.basicPublish("", TASK_QUEUE, null, serializeMessage(message))
    }

    fun listenResults() {
        channel.basicConsume(RESULTS_QUEUE, true, resultListener)
    }

    fun closeChannel() = channel.close()
    fun closeConnection() = connection.close()

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val taskDispatcher = TaskDispatcher(TASK_QUEUE, RESULTS_QUEUE, host)
            taskDispatcher.enqueueTask(TaskDispatcher.Message("SampleSession", "/tmp/lol"))
            taskDispatcher.listenResults()
        }
    }
}