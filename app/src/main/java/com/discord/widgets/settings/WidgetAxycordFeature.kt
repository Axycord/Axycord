package com.discord.widgets.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import com.discord.R
import com.discord.app.AppFragment
import com.discord.databinding.WidgetAxycordFeatureBinding
import com.discord.stores.StoreStream
import com.discord.utilities.cache.SharedPreferencesProvider
import com.discord.utilities.feature.NativeFeature
import com.discord.utilities.feature.NativeFeatureSession
import com.discord.utilities.rest.ClientBackend
import com.discord.widgets.orbsshop.CatalogCache
import com.discord.widgets.orbsshop.ShopApi
import com.discord.widgets.orbsshop.ShopIcons
import com.discord.widgets.orbsshop.ShopSession
import com.discord.widgets.orbsshop.ShopView
import com.discord.widgets.quests.QuestTask
import com.discord.widgets.quests.QuestVideoView
import com.discord.widgets.quests.QuestsScreen
import com.discord.widgets.quests.restoredVideoTask
import com.google.gson.Gson
import java.io.File
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers

/** Restorable native destination; the gate lives here as well as in navigation and transport. */
class WidgetAxycordFeature : AppFragment(R.layout.widget_axycord_feature) {
    private var binding: WidgetAxycordFeatureBinding? = null
    private var session: NativeFeatureSession? = null
    private var shop: ShopSession? = null
    private var accountSubscription: Subscription? = null
    private var preferences: SharedPreferences? = null
    private var accountChanged = false
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        // Invalidate immediately, before the UI callback, so retained confirmation actions cannot run.
        if (session?.isActive == false) session?.close()
        view?.post { if (binding != null) render() }
    }

    private val feature get() = if (arguments?.getString(ARG_FEATURE) == NativeFeature.ORBS_SHOP.id)
        NativeFeature.ORBS_SHOP else NativeFeature.QUESTS

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        binding = WidgetAxycordFeatureBinding.bind(view)
        setActionBarTitle(if (arguments?.getBoolean(ARG_VIDEO) == true) R.string.axycord_quest_video
            else if (feature == NativeFeature.ORBS_SHOP) R.string.axycord_orbs_shop else R.string.axycord_quests)
        setActionBarDisplayHomeAsUpEnabled()
        SettingsNavigation.bindBack(this)
        preferences = SharedPreferencesProvider.get().also { it.registerOnSharedPreferenceChangeListener(preferenceListener) }
        render()
        accountSubscription = StoreStream.getUsers().observeMeId().distinctUntilChanged()
            .observeOn(AndroidSchedulers.mainThread()).subscribe { id ->
                if (session?.userId?.let { it != id } == true) {
                    accountChanged = true
                    clearContent()
                    render()
                }
            }
    }

    override fun onViewBoundOrOnResume() {
        super.onViewBoundOrOnResume()
        render()
    }

    private fun render() {
        val bound = binding ?: return
        if (feature.enabled() && session?.isActive == false) accountChanged = true
        val status = when {
            !feature.enabled() -> R.string.axycord_feature_disabled
            accountChanged || (arguments?.getLong(ARG_ACCOUNT, 0L)?.takeIf { it > 0 }?.let {
                it != StoreStream.getUsers().getMe().id
            } == true) -> R.string.axycord_feature_session_ended
            !ClientBackend.isDiscord || StoreStream.getUsers().getMe().id <= 0 ||
                StoreStream.getAuthentication().getAuthState()?.token.isNullOrBlank() -> R.string.axycord_feature_account_required
            session?.isActive == false -> R.string.axycord_feature_session_ended
            else -> null
        }
        if (status != null) {
            clearContent()
            bound.axycordFeatureStatus.setText(status)
            bound.axycordFeatureStatus.visibility = View.VISIBLE
            return
        }
        if (session != null) return
        val owner = NativeFeatureSession(feature)
        session = owner
        val content = if (arguments?.getBoolean(ARG_VIDEO) == true) {
            val questId = arguments?.getString(ARG_QUEST_ID).orEmpty()
            val task = restoredVideoTask(questId, arguments?.getString(ARG_TASK))
            if (feature != NativeFeature.QUESTS || owner.userId != arguments?.getLong(ARG_ACCOUNT) ||
                task == null) {
                clearContent()
                bound.axycordFeatureStatus.setText(R.string.axycord_quest_video_unavailable)
                return
            }
            QuestVideoView(requireContext(), questId, task, owner.userId, owner)
        } else when (feature) {
            NativeFeature.ORBS_SHOP -> {
                val cache = CatalogCache(File(requireContext().cacheDir, "axycord-orbs-shop"))
                val shopSession = ShopSession(ShopApi(owner), cache).also { shop = it }
                ShopView(requireContext(), shopSession, ShopIcons(requireContext()), childFragmentManager)
            }
            NativeFeature.QUESTS -> QuestsScreen(requireContext(), owner, childFragmentManager) { quest, task, account ->
                if (owner.isActive) openVideo(requireContext(), quest, task, account)
            }
        }
        bound.axycordFeatureStatus.visibility = View.GONE
        bound.axycordFeatureContent.addView(content)
    }

    private fun clearContent() {
        session?.close()
        session = null
        shop?.stop()
        shop = null
        binding?.axycordFeatureContent?.let { content ->
            while (content.childCount > 1) content.removeViewAt(1)
        }
    }

    override fun onDestroyView() {
        preferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        preferences = null
        accountSubscription?.unsubscribe()
        accountSubscription = null
        clearContent()
        binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_FEATURE = "axycord_feature"
        private const val ARG_VIDEO = "axycord_feature_video"
        private const val ARG_TASK = "axycord_feature_task"
        private const val ARG_QUEST_ID = "axycord_feature_quest_id"
        private const val ARG_ACCOUNT = "axycord_feature_account"

        internal fun open(context: Context, feature: NativeFeature) {
            if (!feature.enabled()) return
            SettingsNavigation.launch(context, WidgetAxycordFeature().apply {
                arguments = Bundle().apply {
                    putString(ARG_FEATURE, feature.id)
                    putLong(ARG_ACCOUNT, StoreStream.getUsers().getMe().id)
                }
            })
        }

        private fun openVideo(context: Context, quest: com.discord.widgets.quests.Quest, task: QuestTask, account: Long) {
            SettingsNavigation.launch(context, WidgetAxycordFeature().apply {
                arguments = Bundle().apply {
                    putString(ARG_FEATURE, NativeFeature.QUESTS.id)
                    putBoolean(ARG_VIDEO, true)
                    putString(ARG_QUEST_ID, quest.id)
                    putString(ARG_TASK, Gson().toJson(task))
                    putLong(ARG_ACCOUNT, account)
                }
            })
        }
    }
}
