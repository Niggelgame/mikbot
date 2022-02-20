package dev.schlaubi.mikbot.game.googolplex.game

import dev.kord.common.entity.ButtonStyle
import dev.kord.core.behavior.UserBehavior
import dev.kord.core.behavior.interaction.EphemeralInteractionResponseBehavior
import dev.kord.core.behavior.interaction.InteractionResponseBehavior
import dev.kord.core.behavior.interaction.edit
import dev.kord.core.behavior.interaction.followUpEphemeral
import dev.kord.core.builder.components.emoji
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.entity.interaction.FollowupMessage
import dev.kord.core.event.interaction.ComponentInteractionCreateEvent
import dev.kord.rest.builder.message.modify.MessageModifyBuilder
import dev.kord.rest.builder.message.modify.actionRow
import dev.schlaubi.mikbot.game.api.ControlledPlayer
import dev.schlaubi.mikbot.game.api.translate
import dev.schlaubi.mikbot.game.google_emotes.google
import dev.schlaubi.mikbot.plugin.api.util.componentLive
import kotlinx.coroutines.CompletableDeferred

class GoogolplexPlayer(
    override val user: UserBehavior,
    override var controls: FollowupMessage,
    override val ack: InteractionResponseBehavior,
) : ControlledPlayer {

    private var currentAwaiter: CompletableDeferred<List<ReactionEmoji>?>? = null

    private fun awaiter() = currentAwaiter ?: CompletableDeferred<List<ReactionEmoji>?>().also {
        currentAwaiter = it
    }

    private fun resetAwaiter() {
        currentAwaiter = null
    }

    suspend fun awaitInitialSequence(
        game: GoogolplexGame
    ) = awaitSequence(
        game.size,
        game.translate(user, "googolplex.controls.request_initial", game.size),
        renderChosenButtons = true
    ) { title, chosenButtons ->
        interaction.acknowledgeEphemeralDeferredMessageUpdate()
        controls.edit {
            content = """$title
                    |
                    |${chosenButtons.joinToString("") { it.mention }}
                """.trimMargin()

            if (chosenButtons.size != game.size) {
                addButtons()
            }
        }
    }

    override suspend fun resendControls(ack: EphemeralInteractionResponseBehavior) {
        controls = ack.followUpEphemeral {
            content = "Waiting for next game cycle ..."
        }
        currentAwaiter?.complete(null)
    }

    suspend fun awaitSequence(
        size: Int,
        title: String,
        chosenButtons: MutableList<ReactionEmoji> = mutableListOf(),
        renderChosenButtons: Boolean = false,
        onPress: suspend ComponentInteractionCreateEvent.(title: String, currentSequence: List<ReactionEmoji>) -> Unit
    ): List<ReactionEmoji> {
        val waiter = awaiter()
        controls.edit {
            content = if (renderChosenButtons && chosenButtons.isNotEmpty()) {
                """$title
                    |
                    |${chosenButtons.joinToString("") { it.mention }}
                """.trimMargin()
            } else {
                title
            }

            addButtons()
        }

        val reactionListener = controls.message.componentLive().onInteraction {
            val chosenButtonName = interaction.componentId.substringAfter("select_")
            chosenButtons += google.first { it.name == chosenButtonName }

            onPress(title, chosenButtons)
            if (chosenButtons.size == size) {
                waiter.complete(chosenButtons)
            }
        }

        val sequence = waiter.await()
        reactionListener.cancel()
        resetAwaiter()
        // if the awaiter completes with "null", that means resend controls
        return sequence ?: awaitSequence(size, title, chosenButtons, renderChosenButtons, onPress)
    }

    private fun MessageModifyBuilder.addButtons() {
        google.chunked(5).forEach {
            actionRow {
                it.forEach {
                    interactionButton(ButtonStyle.Primary, "select_${it.name}") {
                        emoji(it)
                    }
                }
            }
        }
    }
}