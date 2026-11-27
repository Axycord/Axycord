package com.discord.widgets.user.usersheet

import com.discord.api.channel.Channel
import com.discord.api.permission.Permission
import com.discord.api.premium.PremiumTier
import com.discord.api.user.NsfwAllowance
import com.discord.api.user.UserProfile
import com.discord.api.voice.state.StageRequestToSpeakState
import com.discord.api.voice.state.VoiceState
import com.discord.models.domain.ModelUserRelationship
import com.discord.models.user.MeUser
import com.discord.models.member.GuildMember
import com.discord.stores.Dispatcher
import com.discord.stores.StoreUserNotes
import com.discord.stores.StoreUserProfile
import com.discord.stores.updates.ObservationDeck
import com.discord.widgets.stage.StageRoles
import android.content.SharedPreferences
import com.discord.R
import com.discord.stores.StoreMediaSettings
import com.discord.stores.VoiceConfigurationCache
import com.discord.restapi.RestAPIInterface
import com.discord.restapi.RestAPIParams
import com.discord.utilities.rest.RestAPI
import com.discord.widgets.channels.WidgetChannelSelector
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import rx.Observable
import rx.android.plugins.RxAndroidPlugins
import rx.android.plugins.RxAndroidSchedulersHook
import rx.plugins.RxJavaHooks
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import rx.subjects.BehaviorSubject

class WidgetUserSheetActionsTest {

    @Before
    fun installImmediateSchedulers() {
        RxJavaHooks.setOnIOScheduler { Schedulers.immediate() }
        RxJavaHooks.setOnComputationScheduler { Schedulers.immediate() }
        RxAndroidPlugins.getInstance().reset()
        RestAPI.reset()
        RxAndroidPlugins.getInstance().registerSchedulersHook(
            object : RxAndroidSchedulersHook() {
                override fun getMainThreadScheduler() = Schedulers.immediate()
            },
        )
    }

    @After
    fun resetSchedulers() {
        RxJavaHooks.reset()
        RxAndroidPlugins.getInstance().reset()
        RestAPI.reset()
    }

    @Test
    fun `server bio wins over cached and account bios`() {
        val profile = UserProfile(
            userProfile = UserProfile.ProfileMetadata(bio = "account bio"),
            guildMemberProfile = UserProfile.ProfileMetadata(bio = "server bio"),
        )

        assertEquals("server bio" to true, WidgetUserSheetViewModel.resolveProfileBio(profile, "cached server", "cached account"))
        assertEquals("account bio" to false, WidgetUserSheetViewModel.resolveProfileBio(profile.copy(guildMemberProfile = null), null, "cached account"))
    }

    @Test
    fun `disabled server profiles preserve guild context but remove identity overrides`() {
        val profile = UserProfile(
            premiumGuildSince = "today",
            guildMemberProfile = UserProfile.ProfileMetadata(bio = "server bio", banner = "server banner"),
            userProfile = UserProfile.ProfileMetadata(bio = "account bio", banner = "account banner"),
            guildBadges = emptyList(),
        )
        val member = GuildMember(
            roleColor = 0x123456, hoistRoleId = 9L, roles = listOf(9L), nick = "server name",
            premiumSince = null, pending = false, joinedAt = null, guildId = 1L, userId = 2L,
            avatarHash = "server avatar", bannerHash = "server banner", bio = "cached server bio",
        )

        val (globalProfile, contextualMember) = WidgetUserSheetViewModel.profileIdentity(profile, member, true)
        assertEquals(null, globalProfile.guildMemberProfile)
        assertEquals(null, globalProfile.guildBadges)
        assertEquals(null, globalProfile.premiumGuildSince)
        assertEquals("account bio", globalProfile.userProfile?.bio)
        assertEquals(null, contextualMember)
        assertEquals(profile to member, WidgetUserSheetViewModel.profileIdentity(profile, member, false))
    }

    @Test
    fun `profile actions match the recovered predicates`() {
        val ordinary = visibility()
        assertTrue(ordinary.showActions)
        assertTrue(ordinary.showMessage)
        assertTrue(ordinary.showCall)
        assertTrue(ordinary.showAddFriend)
        assertTrue(ordinary.showMore)

        assertFalse(visibility(channelType = Channel.DM).showMessage)
        assertFalse(visibility(isBot = true).showAddFriend)
        assertTrue(visibility(relationship = ModelUserRelationship.TYPE_BLOCKED).showCall)

        val incoming = visibility(relationship = ModelUserRelationship.TYPE_INVITE_INCOMING)
        assertTrue(incoming.showPendingRequest)
        assertTrue(incoming.showIncomingRequest)

        val outgoing = visibility(relationship = ModelUserRelationship.TYPE_INVITE_OUTGOING)
        assertTrue(outgoing.showPendingRequest)
        assertFalse(outgoing.showIncomingRequest)
    }

    @Test
    fun `current user popout exposes profile editing and overflow but no social actions`() {
        visibility(isMe = true).let { visibility ->
            assertFalse(visibility.showActions)
            assertFalse(visibility.showMessage)
            // The overflow survives on your own profile: it is where the copy rows live.
            assertTrue(visibility.showMore)
            assertTrue(visibility.showEditProfile)
        }

        assertFalse(visibility(isSystemUser = true).showEditProfile)
        assertFalse(visibility(isSystemUser = true).showMore)
    }

    @Test
    fun `message reuses an existing dm before closing the sheet`() {
        val calls = mutableListOf<String>()
        val navigator = navigator(
            calls = calls,
            findExistingDm = { userId -> 9L.also { calls += "find:$userId" } },
        )

        navigator.open(42L)

        assertEquals(listOf("find:42", "select:9", "close", "home", "activity"), calls)
    }

    @Test
    fun `message creates a missing dm and selects it when ready`() {
        val calls = mutableListOf<String>()
        var created: ((Result<Long>) -> Unit)? = null
        val navigator = navigator(
            calls = calls,
            findExistingDm = { null },
            createDm = { userId, completed ->
                calls += "create:$userId"
                created = completed
            },
        )

        navigator.open(42L)
        assertEquals(listOf("create:42", "close", "home", "activity"), calls)

        created?.invoke(Result.success(12L))
        assertEquals(listOf("create:42", "close", "home", "activity", "select:12"), calls)
    }

    @Test
    fun `invite to speak disables only invite and dismisses after success`() {
        val request = PublishSubject.create<Void>()
        var call: Triple<Long, Long, Boolean>? = null
        val viewModel = stageViewModel { channel, userId, suppressed ->
            call = Triple(channel.id, userId, suppressed)
            request
        }
        val events = mutableListOf<WidgetUserSheetViewModel.Event>()
        viewModel.observeEvents().subscribe(events::add)

        viewModel.inviteUserToSpeak()

        assertEquals(Triple(STAGE_ID, USER_ID, false), call)
        assertTrue(loaded(viewModel).stageViewState.isInvitingToSpeak)
        assertFalse(loaded(viewModel).stageViewState.isUpdatingSuppressed)
        assertTrue(events.isEmpty())

        request.onNext(null)
        assertFalse(loaded(viewModel).stageViewState.isInvitingToSpeak)
        assertEquals(listOf(WidgetUserSheetViewModel.Event.DismissSheet), events)
    }

    @Test
    fun `move to audience restores the row and reports a failed request`() {
        val viewModel = stageViewModel { _, _, _ -> Observable.error<Void>(IllegalStateException("failed")) }
        val events = mutableListOf<WidgetUserSheetViewModel.Event>()
        viewModel.observeEvents().subscribe(events::add)

        viewModel.setUserSuppressedInChannel(true)

        assertFalse(loaded(viewModel).stageViewState.isUpdatingSuppressed)
        assertEquals(
            listOf(WidgetUserSheetViewModel.Event.ShowToast(com.discord.R.string.default_failure_to_perform_action_message)),
            events,
        )
    }

    private fun visibility(
        isMe: Boolean = false,
        isSystemUser: Boolean = false,
        isBot: Boolean = false,
        channelType: Int? = Channel.GUILD_TEXT,
        relationship: Int = ModelUserRelationship.TYPE_NONE,
    ) = WidgetUserSheetViewModel.actionVisibility(
        isMe = isMe,
        isSystemUser = isSystemUser,
        isBot = isBot,
        channelType = channelType,
        relationshipType = relationship,
    )

    private fun navigator(
        calls: MutableList<String>,
        findExistingDm: (Long) -> Long?,
        createDm: (Long, (Result<Long>) -> Unit) -> Unit = { _, _ -> error("unexpected create") },
    ) = UserSheetMessageNavigator(
        findExistingDm = findExistingDm,
        createDm = createDm,
        selectDm = { calls += "select:$it" },
        closeSheet = { calls += "close" },
        selectHome = { calls += "home" },
        ensureHomeActivity = { calls += "activity" },
    )

    @Test
    fun `peer controls update the shared configuration and restore in the rendered state`() {
        val settings = mediaSettings()
        val states = BehaviorSubject.create<WidgetUserSheetViewModel.StoreState?>(stageStoreState())
        val subscription = settings.getVoiceConfiguration().subscribe { config ->
            states.onNext(stageStoreState().copy(
                muted = config.mutedUsers[USER_ID] == true,
                outputVolume = config.userOutputVolumes[USER_ID] ?: 100f,
            ))
        }
        val viewModel = stageViewModel(mediaSettings = settings, states = states)
        viewModel.toggleMute(false)
        assertTrue(settings.getMutedUsers().isEmpty())
        viewModel.toggleMute(true)
        viewModel.toggleMute(true)
        assertEquals(true, settings.getMutedUsers()[USER_ID])
        assertTrue(loaded(viewModel).voiceSettingsViewState.isMuted)
        viewModel.setUserOutputVolume(50f)
        assertEquals(50f, loaded(viewModel).voiceSettingsViewState.outputVolume!!, 0.0001f)
        assertTrue(loaded(viewModel).voiceSettingsViewState.isMuted)
        viewModel.toggleMute(false)
        viewModel.toggleMute(false)
        assertFalse(loaded(viewModel).voiceSettingsViewState.isMuted)
        viewModel.setUserOutputVolume(200f)
        assertEquals(200f, loaded(viewModel).voiceSettingsViewState.outputVolume!!, 0.0001f)
        assertFalse(loaded(viewModel).voiceSettingsViewState.isMuted)
        subscription.unsubscribe()
    }

    @Test
    fun `move flow emits the picker action and sends only the selected voice channel`() {
        val target = stageChannel().copy(id = STAGE_ID + 1, type = Channel.GUILD_VOICE)
        val moves = mutableListOf<Triple<Long, Long, RestAPIParams.GuildMember>>()
        installMemberApi { guild, user, body ->
            moves += Triple(guild, user, body)
            Observable.empty()
        }
        val model = moveViewModel(target)
        val events = mutableListOf<WidgetUserSheetViewModel.Event>()
        model.observeEvents().subscribe(events::add)
        model.guildMoveForUser()
        assertEquals(WidgetUserSheetViewModel.Event.LaunchMoveUser(GUILD_ID), events.single())
        model.moveUserToChannel(target.id)
        assertEquals(GUILD_ID, moves.single().first)
        assertEquals(USER_ID, moves.single().second)
        val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()
        assertEquals("{\"channel_id\":${target.id}}", gson.toJson(moves.single().third))
        assertTrue(events.contains(WidgetUserSheetViewModel.Event.ShowToast(R.string.move_to_success)))
        assertEquals(WidgetUserSheetViewModel.Event.DismissSheet, events.last())
    }

    @Test
    fun `voice move rechecks lost permissions and disconnected members before sending`() {
        val target = stageChannel().copy(id = STAGE_ID + 1)
        var requests = 0
        installMemberApi { _, _, _ -> requests++; Observable.empty() }
        val permissions = mutableMapOf(STAGE_ID to MOVE_PERMISSIONS, target.id to MOVE_PERMISSIONS)
        val states = BehaviorSubject.create<WidgetUserSheetViewModel.StoreState?>(moveState())
        val model = moveViewModel(target, states) { permissions }
        val events = mutableListOf<WidgetUserSheetViewModel.Event>()
        model.observeEvents().subscribe(events::add)
        model.guildMoveForUser()
        permissions.remove(STAGE_ID)
        model.moveUserToChannel(target.id)
        permissions[STAGE_ID] = MOVE_PERMISSIONS
        permissions[target.id] = Permission.VIEW_CHANNEL or Permission.MOVE_MEMBERS
        model.moveUserToChannel(target.id)
        permissions[target.id] = MOVE_PERMISSIONS
        states.onNext(moveState().copy(currentChannelVoiceStates = emptyMap()))
        model.moveUserToChannel(target.id)
        assertEquals(0, requests)
        assertEquals(3, events.count { it == WidgetUserSheetViewModel.Event.ShowToast(R.string.channel_locked) })
        assertFalse(events.contains(WidgetUserSheetViewModel.Event.DismissSheet))
    }

    @Test
    fun `voice move failures are reported without a success dismissal`() {
        val target = stageChannel().copy(id = STAGE_ID + 1)
        installMemberApi { _, _, _ -> Observable.error(IllegalStateException("move rejected")) }
        val model = moveViewModel(target)
        val events = mutableListOf<WidgetUserSheetViewModel.Event>()
        model.observeEvents().subscribe(events::add)
        model.moveUserToChannel(target.id)
        assertEquals(listOf(WidgetUserSheetViewModel.Event.ShowToast(R.string.error_occurred_try_again)), events)
    }

    @Test
    fun `move picker excludes text hidden current and foreign guild channels`() {
        val target = stageChannel().copy(id = STAGE_ID + 1)
        val filter = WidgetChannelSelector.MoveUserFilterFunction(GUILD_ID, STAGE_ID, mapOf(target.id to MOVE_PERMISSIONS))
        assertTrue(filter.includeChannel(target))
        assertTrue(filter.includeChannel(target.copy(type = Channel.GUILD_VOICE)))
        assertFalse(filter.includeChannel(target.copy(type = Channel.GUILD_TEXT)))
        assertFalse(filter.includeChannel(target.copy(type = Channel.DM)))
        assertFalse(filter.includeChannel(target.copy(guildId = GUILD_ID + 1)))
        assertFalse(filter.includeChannel(target.copy(id = STAGE_ID)))
        assertFalse(filter.includeChannel(target.copy(id = STAGE_ID + 2)))
        for (missing in listOf(Permission.VIEW_CHANNEL, Permission.CONNECT, Permission.MOVE_MEMBERS)) {
            assertFalse(WidgetChannelSelector.MoveUserFilterFunction(GUILD_ID, STAGE_ID, mapOf(target.id to (MOVE_PERMISSIONS xor missing))).includeChannel(target))
        }
    }

    private fun moveState() = stageStoreState().copy(currentChannelVoiceStates = mapOf(USER_ID to voiceState(USER_ID)))

    private fun moveViewModel(
        target: Channel,
        states: Observable<WidgetUserSheetViewModel.StoreState?> = Observable.just(moveState()),
        permissions: () -> Map<Long, Long> = { mapOf(STAGE_ID to MOVE_PERMISSIONS, target.id to MOVE_PERMISSIONS) },
    ) = stageViewModel(states = states, moveChannel = { if (it == target.id) target else null }, movePermissions = permissions)

    private fun installMemberApi(request: (Long, Long, RestAPIParams.GuildMember) -> Observable<Void>) {
        RestAPI.install(Proxy.newProxyInstance(RestAPIInterface::class.java.classLoader, arrayOf(RestAPIInterface::class.java)) { _, method, args ->
            check(method.name == "changeGuildMember") { "Unexpected API call: ${method.name}" }
            request(args[0] as Long, args[1] as Long, args[2] as RestAPIParams.GuildMember)
        } as RestAPIInterface)
    }

    private fun mediaSettings(): StoreMediaSettings {
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, method, _ ->
            when (method.returnType) {
                SharedPreferences.Editor::class.java -> editor
                Boolean::class.javaPrimitiveType -> true
                else -> null
            }
        } as SharedPreferences.Editor
        val prefs = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "edit" -> editor
                "getAll" -> emptyMap<String, Any>()
                "contains" -> false
                else -> args?.getOrNull(1)
            }
        } as SharedPreferences
        return StoreMediaSettings(voiceConfigurationCache = VoiceConfigurationCache(prefs))
    }

    private fun stageViewModel(
        mediaSettings: StoreMediaSettings? = null,
        states: Observable<WidgetUserSheetViewModel.StoreState?> = Observable.just(stageStoreState()),
        moveChannel: (Long) -> Channel? = { null },
        movePermissions: () -> Map<Long, Long> = { emptyMap() },
        action: (Channel, Long, Boolean) -> Observable<Void> = { _, _, _ -> Observable.empty() },
    ): WidgetUserSheetViewModel {
        val dispatcher = Dispatcher(Schedulers.immediate())
        val deck = ObservationDeck()
        val notes = StoreUserNotes(dispatcher, deck, { _, _, _ -> }, { _, _, _, _ -> })
        val profiles = StoreUserProfile(dispatcher, deck, { _, _, _, _, _ -> })
        return WidgetUserSheetViewModel(
            userId = USER_ID,
            channelId = STAGE_ID,
            guildId = GUILD_ID,
            friendToken = null,
            isVoiceContext = true,
            storeStateObservable = states,
            storeMediaSettings = mediaSettings,
            storeUserNotes = notes,
            storeUserProfile = profiles,
            setStageSuppressed = action,
            getVoiceMoveChannel = moveChannel,
            getVoiceMovePermissions = movePermissions,
        )
    }

    private fun stageStoreState(): WidgetUserSheetViewModel.StoreState {
        val channel = stageChannel()
        return WidgetUserSheetViewModel.StoreState(
            user = me(USER_ID),
            me = me(ME_ID),
            channel = channel,
            computedMembers = emptyMap(),
            guildRoles = emptyMap(),
            mySelectedVoiceChannelVoiceStates = mapOf(
                ME_ID to voiceState(ME_ID),
                USER_ID to voiceState(USER_ID),
            ),
            currentChannelVoiceStates = emptyMap(),
            muted = false,
            selfMuted = false,
            selfDeafened = false,
            outputVolume = 100f,
            richPresence = null,
            guild = null,
            permissions = Permission.MUTE_MEMBERS,
            streamContext = null,
            userProfile = UserProfile(),
            userRelationshipType = ModelUserRelationship.TYPE_NONE,
            userNoteFetchState = StoreUserNotes.UserNoteState.Empty,
            stageChannel = channel,
            userStageRoles = StageRoles.AUDIENCE,
            userRequestToSpeakState = StageRequestToSpeakState.NONE,
            myStageRoles = StageRoles(isSpeaker = true, isModerator = true, isInvitedToSpeak = false),
        )
    }

    private fun loaded(viewModel: WidgetUserSheetViewModel) =
        viewModel.requireViewState() as WidgetUserSheetViewModel.ViewState.Loaded

    private fun stageChannel() = Channel(
        topic = null, type = Channel.GUILD_STAGE_VOICE, guildId = GUILD_ID, name = "stage",
        lastMessageId = 0, id = STAGE_ID, ownerId = 0, recipientIds = null, recipients = null,
        position = 0, permissionOverwrites = null, userLimit = 0, bitrate = 0, icon = null,
        originChannelId = 0, applicationId = 0, nicks = null, nsfw = false, parentId = 0,
        memberListId = null, rateLimitPerUser = 0, defaultAutoArchiveDuration = null,
        rtcRegion = null, flags = null, guildHashes = null, availableTags = null, appliedTags = null,
        threadMetadata = null, messageCount = null, memberCount = null, memberIdsPreview = null,
        member = null,
    )

    private fun voiceState(userId: Long) = VoiceState(
        userId, true, null, false, false, false, false, false, GUILD_ID, false, null, null, STAGE_ID,
    )

    private fun me(id: Long) = MeUser(
        id, "user$id", null, null, false, false, 1, PremiumTier.NONE, null, false, true,
        null, 0, 0, null, NsfwAllowance.UNKNOWN, null, null,
    )

    private companion object {
        const val MOVE_PERMISSIONS = Permission.VIEW_CHANNEL or Permission.CONNECT or Permission.MOVE_MEMBERS
        const val USER_ID = 42L
        const val ME_ID = 7L
        const val GUILD_ID = 10L
        const val STAGE_ID = 20L
    }
}
