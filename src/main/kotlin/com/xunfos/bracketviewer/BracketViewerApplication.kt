package com.xunfos.bracketviewer

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.listenToAsFlow
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
class BracketViewerApplication

fun main(args: Array<String>) {
    runApplication<BracketViewerApplication>(*args)
}

@Configuration
class RedisConfiguration {
    @Bean
    fun channelTopic(): ChannelTopic = ChannelTopic("redisTopic")

    @Bean
    fun reactiveRedisConnectionFactory(): ReactiveRedisConnectionFactory {
        return LettuceConnectionFactory("localhost", 6379)
    }
}


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type", include = JsonTypeInfo.As.PROPERTY)
sealed interface Message
data class MatchMessage(val p1: String, val p2: String, val game: Game) : Message

enum class Game {
    SamSho, SF6, Strive, T8, VF5, Mahvel3
}



@Service
class MessageService(
    private val channelTopic: ChannelTopic,
    private val objectMapper: ObjectMapper,
    private val redisTemplate: ReactiveRedisTemplate<String, String>,
) {
    suspend fun send(message: Message) {
        redisTemplate
            .convertAndSend(channelTopic.topic, message.toJson())
            .awaitSingleOrNull()
    }

    suspend fun subscribe(): Flow<Message> {
        return redisTemplate
            .listenToAsFlow(channelTopic)
            .map {
                objectMapper.readValue<Message>(it.message)
            }
    }

    private fun Message.toJson(): String = objectMapper.writeValueAsString(this)
}


@RestController
@RequestMapping("/send")
class SendController(private val messageService: MessageService) {
    val players = listOf("Tib", "Schub", "MrCosta", "Sami", "DayWalker", "MrBoomer", "Miguel Rossington")
    @PostMapping("/match")
    suspend fun sendMatch() {
        messageService.send(MatchMessage(players.random(), players.random(), Game.entries.random()))
    }
}

@Component
class MessageListener(private val messageService: MessageService) {
    init {
        GlobalScope.launch {
            messageService.subscribe()
                .collect { println("Received: $it") }
        }
    }
}


