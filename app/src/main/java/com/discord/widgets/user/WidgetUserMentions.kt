package com.discord.widgets.user

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.discord.R
import com.discord.app.AppFragment
import com.discord.app.appActivityViewModels
import com.discord.app.appViewModels
import com.discord.databinding.WidgetUserMentionsBinding
import com.discord.models.guild.Guild
import com.discord.models.message.Message
import com.discord.stores.StoreNavigation
import com.discord.stores.StoreStream
import com.discord.utilities.mg_recycler.MGRecyclerAdapter
import com.discord.utilities.rx.appSubscribe
import com.discord.utilities.rx.ui
import com.discord.views.ThemedColor
import com.discord.widgets.tabs.NavigationTab
import com.discord.widgets.chat.bookmarks.WidgetMessageBookmarks
import com.discord.widgets.chat.list.ThreadSpineItemDecoration
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapter
import com.discord.widgets.chat.list.entries.ChatListEntry
import com.discord.widgets.chat.list.entries.LoadingEntry
import com.discord.widgets.chat.list.entries.MentionFooterEntry
import com.discord.widgets.chat.list.entries.MessageHeaderEntry
import com.discord.widgets.chat.list.model.ChatListRows
import com.discord.widgets.user.search.WidgetGlobalSearchDismissModel
import rx.Subscription
import rx.schedulers.Schedulers

/**
 * Recent mentions.
 *
 * Every result is a message from a different channel, so each one is preceded by a
 * [MessageHeaderEntry] naming the guild and channel it came from. The list is not reversed — unlike
 * the chat list, results read newest-first from the top — and it never paginates upwards, so the
 * footer marking the end of the results is a row rather than a scroll state.
 */
class WidgetUserMentions : AppFragment(R.layout.widget_user_mentions) {

    private val viewModel: WidgetUserMentionsViewModel by appViewModels { WidgetUserMentionsViewModel() }
    private var adapter: WidgetChatListAdapter? = null
    private val messages = ArrayList<Message>()
    private var request: Subscription? = null
    private var loading = false
    private var allLoaded = false
    private var requestGeneration = 0L
    private val dismissViewModel: WidgetGlobalSearchDismissModel by appActivityViewModels {
        WidgetGlobalSearchDismissModel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { viewModel.filters = it.getInt(WidgetUserMentionsFilter.FILTERS, MentionFilters.ALL) and MentionFilters.ALL }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(WidgetUserMentionsFilter.FILTERS, viewModel.filters)
        super.onSaveInstanceState(outState)
    }

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        val binding = WidgetUserMentionsBinding.bind(view)
        val isEmbedded = arguments?.getBoolean(ARG_EMBEDDED) == true
        binding.actionBarToolbar.apply {
            setTitle(R.string.recent_mentions)
            visibility = if (isEmbedded) View.GONE else View.VISIBLE
            if (!isEmbedded) {
                menu.add(R.string.bookmark_actions_title).apply {
                    icon = AppCompatResources.getDrawable(context, R.drawable.ic_bookmark_24dp)?.mutate()?.also {
                        DrawableCompat.setTint(it, ThemedColor.resolve(context, R.attr.colorInteractiveNormal))
                    }
                    setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    setOnMenuItemClickListener {
                        WidgetMessageBookmarks.launch(requireContext())
                        true
                    }
                }
                inflateMenu(R.menu.menu_user_mentions)
                menu.findItem(R.id.menu_user_mentions_filter).setOnMenuItemClickListener {
                    WidgetUserMentionsFilter.show(childFragmentManager, viewModel.filters)
                    true
                }
            }
        }
        childFragmentManager.setFragmentResultListener(WidgetUserMentionsFilter.REQUEST_KEY, viewLifecycleOwner) { _, result ->
            val filters = result.getInt(WidgetUserMentionsFilter.FILTERS, MentionFilters.ALL) and MentionFilters.ALL
            if (filters != viewModel.filters) {
                viewModel.filters = filters
                reloadFromTop(force = true)
            }
        }
        val recycler = binding.userMentionsList
        val mentionsAdapter = MGRecyclerAdapter.configure<ChatListEntry, WidgetChatListAdapter>(
            WidgetChatListAdapter(
                recycler, this, parentFragmentManager, MentionsEventHandler(isEmbedded),
                stickToBottom = false,
            ),
        ).apply { mentionMeMessageLevelHighlighting = false }
        recycler.addItemDecoration(ThreadSpineItemDecoration(requireContext(), mentionsAdapter))
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                if (dy > 0 && manager.findLastVisibleItemPosition() >= mentionsAdapter.itemCount - PREFETCH_ROWS) {
                    load()
                }
            }
        })
        adapter = mentionsAdapter
        viewModel.model?.let { mentionsAdapter.data = it }
        load()
        // The tab lives for the whole session and `load` only pages backwards from the oldest
        // mention held, so mentions received after the first fetch never appeared. Refetch on
        // opening the tab, and on every reconnect: a fetch made while offline failed, and mentions
        // received while away never arrive over the gateway.
        if (!isEmbedded) {
            StoreStream.getTabsNavigation().observeSelectedTab().distinctUntilChanged().ui(this)
                .appSubscribe(WidgetUserMentions::class.java) { selected ->
                    if (selected == NavigationTab.MENTIONS) reloadFromTop()
                }
            StoreStream.getConnectionOpen().observeConnectionOpen().distinctUntilChanged().filter { it }.ui(this)
                .appSubscribe(WidgetUserMentions::class.java) { reloadFromTop() }
        }
    }

    override fun onViewBoundOrOnResume() {
        super.onViewBoundOrOnResume()
        adapter?.setHandlers()
    }

    override fun onPause() {
        adapter?.disposeHandlers()
        super.onPause()
    }

    override fun onDestroyView() {
        requestGeneration++
        request?.unsubscribe()
        request = null
        // The cancelled request will never report back, so its flag would stay set for good.
        loading = false
        adapter?.dispose()
        adapter = null
        super.onDestroyView()
    }

    /** Starts over from the newest page, unless the first fetch is still on its way. */
    private fun reloadFromTop(force: Boolean = false) {
        if (!force && loading && messages.isEmpty()) return
        requestGeneration++
        request?.unsubscribe()
        messages.clear()
        allLoaded = false
        loading = false
        load()
    }

    private fun load() {
        if (loading || allLoaded) return
        val requestView = view ?: return
        if (viewModel.filters == 0) {
            allLoaded = true
            render()
            return
        }
        val generation = ++requestGeneration
        val before = messages.minOfOrNull(Message::id)
        loading = true
        render()
        request = StoreStream.getMentions(before)
            .subscribeOn(Schedulers.io())
            .subscribe(
                { page ->
                    requestView.post {
                        if (generation != requestGeneration || view !== requestView) return@post
                        val converted = page.map(::Message)
                        messages.addAll(converted.filter { incoming -> messages.none { it.id == incoming.id } })
                        messages.sortByDescending(Message::id)
                        allLoaded = MentionFilters.isLastPage(before, page.map { it.id }, PAGE_SIZE)
                        loading = false
                        val visible = render()
                        // Filtering can leave a whole page empty or too short to scroll. Keep the
                        // raw-message cursor moving until a visible page is filled or exhausted.
                        if (!allLoaded && visible < PAGE_SIZE) load()
                    }
                },
                { requestView.post {
                    if (generation == requestGeneration && view === requestView) {
                        loading = false
                        render()
                    }
                } },
            )
    }

    private fun render(): Int {
        val rows = ArrayList<ChatListEntry>()
        val userId = StoreStream.getUsers().getMe().id
        val members = StoreStream.getGuilds().getMembers()
        var visible = 0
        messages.forEach { message ->
            val channel = StoreStream.getChannels().getChannel(message.channelId) ?: return@forEach
            val roles = members[channel.guildId]?.get(userId)?.getRoles().orEmpty()
            if (!MentionFilters.matches(viewModel.filters, message, userId, roles)) return@forEach
            val guild = StoreStream.getGuilds().getGuild(channel.guildId)
            val state = ChatListRows.State(
                channel = channel,
                guild = guild,
                guildMembers = StoreStream.getGuilds().getMembers()[channel.guildId].orEmpty(),
                guildRoles = StoreStream.getGuilds().getRoles()[channel.guildId].orEmpty(),
                permissions = StoreStream.getPermissions().getPermissionsByChannel()[channel.id],
                animateEmojis = StoreStream.getUserSettings().getIsAnimatedEmojisEnabled(),
                autoPlayGifs = StoreStream.getUserSettings().getIsAutoPlayGifsEnabled(),
                renderEmbeds = StoreStream.getUserSettings().getIsRenderEmbedsEnabled(),
            )
            rows += ChatListRows.contextMessageRows(state, message)
            visible++
        }
        if (loading) rows += LoadingEntry() else if (allLoaded) rows += MentionFooterEntry()
        val model = Model(
            userId = StoreStream.getUsers().getMe().id,
            guildId = StoreStream.getGuildSelected().getSelectedGuildId(),
            guild = StoreStream.getGuilds().getGuild(StoreStream.getGuildSelected().getSelectedGuildId()),
            channelNames = StoreStream.getChannels().getChannelNames(),
            list = rows,
        )
        viewModel.model = model
        adapter?.data = model
        return visible
    }

    private fun openMessage(message: Message, isEmbedded: Boolean) = navigateToMention(
        channelId = message.channelId,
        messageId = message.id,
        isEmbedded = isEmbedded,
        jumpToMessage = StoreStream.getMessagesLoader()::jumpToMessage,
        selectHomeTab = StoreStream.getTabsNavigation()::selectHomeTab,
        dismiss = dismissViewModel::dismiss,
    )

    /** Tapping a result leaves this screen for the message in its own channel. */
    private inner class MentionsEventHandler(private val isEmbedded: Boolean) : WidgetChatListAdapter.EventHandler {
        override fun onMessageClicked(message: Message, isThreadStarterMessage: Boolean) =
            openMessage(message, isEmbedded)
    }

    /**
     * Read-only [WidgetChatListAdapter.Data] spanning many channels at once.
     *
     * `channelId`, `oldestMessageId` and `newMessagesMarkerMessageId` are `-1` rather than `0`,
     * matching the reference: this list belongs to no single channel, and `0` would read as
     * "channel 0" to the adapter's channel-change detection.
     */
    internal data class Model(
        override val userId: Long,
        override val guildId: Long,
        override val guild: Guild?,
        override val channelNames: Map<Long, String>,
        override val list: List<ChatListEntry>,
        override val channelId: Long = NO_CHANNEL,
        override val myRoleIds: Set<Long> = emptySet(),
        override val oldestMessageId: Long = NO_CHANNEL,
        override val newMessagesMarkerMessageId: Long = NO_CHANNEL,
        override val isSpoilerClickAllowed: Boolean = false,
    ) : WidgetChatListAdapter.Data

    companion object {
        private const val ARG_EMBEDDED = "is_embedded"

        fun create(isEmbedded: Boolean) = WidgetUserMentions().apply {
            arguments = android.os.Bundle().apply { putBoolean(ARG_EMBEDDED, isEmbedded) }
        }

        const val PAGE_SIZE = 25

        /** How close to the end of the loaded results the list must get before the next page. */
        const val PREFETCH_ROWS = 4

        /** The reference's sentinel for "these rows do not belong to one channel". */
        const val NO_CHANNEL = -1L
    }
}

internal fun navigateToMention(
    channelId: Long,
    messageId: Long,
    isEmbedded: Boolean,
    jumpToMessage: (Long, Long) -> Unit,
    selectHomeTab: (StoreNavigation.PanelAction, Boolean) -> Unit,
    dismiss: () -> Unit,
) {
    jumpToMessage(channelId, messageId)
    selectHomeTab(StoreNavigation.PanelAction.CLOSE, isEmbedded)
    dismiss()
}
