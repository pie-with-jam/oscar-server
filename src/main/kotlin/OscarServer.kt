package ru.alertKaput

import java.net.ServerSocket
import java.net.Socket
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Данные сообщения OSCAR-сервера.
 *
 * @property id Уникальный идентификатор сообщения.
 * @property senderId Идентификатор отправителя.
 * @property recipientId Идентификатор получателя.
 * @property content Содержимое сообщения.
 * @property timestamp Время отправки сообщения.
 */
data class OscarMessage(
    val id: String,
    val senderId: String,
    val recipientId: String,
    val content: String,
    val timestamp: String
)

/**
 * Синглтон для OSCAR-сервера, который обрабатывает подключения клиентов
 * и хранит сообщения.
 */
object OscarServer {
    private const val PORT = 5190 // Порт OSCAR
    private val executor = Executors.newCachedThreadPool() // Для обработки клиентов
    private val messages = ConcurrentHashMap<String, MutableList<OscarMessage>>() // Хранилище сообщений

    /**
     * Запуск OSCAR-сервера, который ожидает подключения клиентов и обрабатывает их.
     * Прослушивает указанный порт и принимает подключения от клиентов.
     */
    fun startServer() {
        println("[DEBUG] OSCAR-сервер запускается на порту $PORT...")

        try {
            val serverSocket = ServerSocket(PORT)
            println("[DEBUG] OSCAR-сервер запущен и ожидает подключения.")

            while (true) {
                val clientSocket = serverSocket.accept()
                println("[DEBUG] Новое подключение: ${clientSocket.inetAddress.hostAddress}")
                executor.execute { handleClient(clientSocket) }
            }
        } catch (e: Exception) {
            println("[DEBUG] Ошибка запуска OSCAR-сервера: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Обработка подключения клиента.
     * Получает запрос от клиента, разбирает его и выполняет соответствующее действие
     * (отправка сообщений или получение сообщений).
     *
     * @param socket Сокет клиента для получения и отправки данных.
     */
    private fun handleClient(socket: Socket) {
        socket.use { client ->
            val input = client.getInputStream().bufferedReader()
            val output = client.getOutputStream().bufferedWriter()

            try {
                val request = input.readLine() ?: ""
                println("[DEBUG] Получен запрос: $request")
                val parts = request.split("|")

                if (parts.size < 2) {
                    output.write("ERROR|Invalid request format\n")
                    output.flush()
                    return
                }

                when (parts[0]) {
                    "SEND" -> {
                        if (parts.size == 5) {
                            val message = OscarMessage(
                                id = UUID.randomUUID().toString(),
                                senderId = parts[1],
                                recipientId = parts[2],
                                content = parts[3],
                                timestamp = parts[4]
                            )
                            println("[DEBUG] Получена команда SEND. Сообщение: $message")

                            // Добавление сообщения в хранилище для получателя
                            messages.computeIfAbsent(message.recipientId) { mutableListOf() }.add(message)
                            output.write("OK\n")
                            output.flush()
                        } else {
                            output.write("ERROR|Invalid SEND format\n")
                            output.flush()
                        }
                    }

                    "RECEIVE" -> {
                        if (parts.size == 2) {
                            val userId = parts[1]
                            println("[DEBUG] Получена команда RECEIVE для пользователя: $userId")

                            // Получение сообщений для пользователя
                            val userMessages = messages[userId] ?: emptyList()

                            if (userMessages.isNotEmpty()) {
                                println("[DEBUG] Найдено ${userMessages.size} сообщений для $userId")
                                userMessages.forEach {
                                    println("[DEBUG] Отправка сообщения: $it")
                                    output.write("MESSAGE|${it.id}|${it.senderId}|${it.recipientId}|${it.content}|${it.timestamp}\n")
                                }
                                output.flush()
                            } else {
                                println("[DEBUG] Сообщений для $userId нет")
                                output.write("ERROR|No messages found for user\n")
                                output.flush()
                            }
                        } else {
                            output.write("ERROR|Invalid RECEIVE format\n")
                            output.flush()
                        }
                    }

                    else -> {
                        println("[DEBUG] Неизвестная команда: ${parts[0]}")
                        output.write("ERROR|Unknown command\n")
                        output.flush()
                    }
                }
            } catch (e: Exception) {
                println("[DEBUG] Ошибка обработки клиента: ${e.message}")
                e.printStackTrace()
                output.write("ERROR|Exception occurred: ${e.message}\n")
                output.flush()
            }
        }
    }
}
