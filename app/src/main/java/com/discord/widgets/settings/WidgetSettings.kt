package com.discord.widgets.settings

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import com.discord.R
import com.discord.app.AppFragment
import com.discord.app.appViewModels
import com.discord.databinding.WidgetSettingsBinding
import com.discord.models.user.MeUser
import com.discord.stores.StoreStream
import com.discord.utilities.presence.PresenceUtils
import com.discord.utilities.rx.ui
import com.discord.utilities.uri.UriHandler
import com.discord.utilities.user.UserUtils.displayName
import com.discord.utilities.cache.SharedPreferencesProvider
import com.discord.utilities.feature.ClientSettings
import com.discord.utilities.feature.FeatureFlags
import com.discord.utilities.feature.NativeFeature
import com.discord.widgets.media.WidgetQRScanner
import com.discord.widgets.multiaccount.WidgetSwitchAccounts
import com.discord.widgets.settings.account.WidgetSettingsAccount
import com.discord.widgets.settings.developer.WidgetSettingsDeveloper
import com.discord.widgets.notice.WidgetNoticeDialog
import com.discord.widgets.user.Badge
import com.discord.widgets.user.WidgetUserStatusSheet
import com.discord.widgets.settings.profile.WidgetEditUserOrGuildMemberProfile
import com.discord.widgets.user.profile.UserProfileHeaderViewModel

/**
 * The user-settings root screen, hosted by the settings tab.
 *
 * Reference: `com.discord.widgets.settings.WidgetSettings` + `SettingsViewModel`.
 *
 * One deliberate divergence from the reference is recorded here:
 *
 * **[hideUnavailableRows]** has no reference counterpart. The reference shows every row and gates
 *    only `developer_options*`, `nitro_settings_container`, `role_subscriptions`,
 *    `nitro_gifting_badge` and `notification_upsell`. Rows here are hidden when their destination
 *    screen does not exist yet, so the screen has no dead taps; every id in [implementedRouteIds]
 *    navigates to a reconstructed screen. Delete the mechanism once the missing screens exist.
 *
 *    One caveat, so the set is not read as stronger than it is: `notifications` reaches a complete
 *    [WidgetSettingsNotifications], but that screen's toggles stay disabled until `StoreStream`
 *    publishes `StoreNotifications` (see `NotificationStore`). The row is exposed anyway because the
 *    destination exists and renders real state — hiding a finished screen is the mistake the parity
 *    audit already caught on `blocked_users`.
 */
class WidgetSettings : AppFragment(R.layout.widget_settings) {

    private val viewModel: SettingsViewModel by appViewModels { SettingsViewModel() }
    private var _binding: WidgetSettingsBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val featureListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == ClientSettings.orbsShop.key || key == ClientSettings.quests.key) {
            view?.post { configureBilling() }
        }
    }

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        _binding = WidgetSettingsBinding.bind(view)
        configureToolbar()
        hideUnavailableRows()
        SharedPreferencesProvider.get().registerOnSharedPreferenceChangeListener(featureListener)
        configureBilling()
        binding.settingsSwitchAccounts.setOnClickListener { WidgetSwitchAccounts.show(parentFragmentManager) }
        binding.settingsLogout.setOnClickListener { showLogoutDialog(requireContext()) }
        binding.settingsBillingQuests.setOnClickListener { WidgetAxycordFeature.open(requireContext(), NativeFeature.QUESTS) }
        binding.settingsBillingOrbsShop.setOnClickListener { WidgetAxycordFeature.open(requireContext(), NativeFeature.ORBS_SHOP) }
        binding.settingsBillingServerBoosts.setOnClickListener {
            WidgetNoticeDialog.Builder(requireContext())
                .setTitle(R.string.axycord_server_boosts)
                .setMessage(R.string.axycord_server_boosts_coming_soon)
                .setPositiveButton(R.string.okay)
                .show(parentFragmentManager)
        }

        binding.setStatusContainer.apply {
            contentDescription = getString(R.string.set_status)
            isFocusable = true
            setOnClickListener {
                onSetStatus?.invoke() ?: WidgetUserStatusSheet.show(parentFragmentManager)
            }
        }

        binding.userSettingsProfileHeaderView.apply {
            setOnBadgeClick(Badge.onBadgeClick(parentFragmentManager, requireContext()))
            setOnBannerPress { viewModel.toggleAllowAnimationInReducedMotion() }
        }

        binding.appInfoHeader.text = buildString {
            append(getString(R.string.app_information))
            append(" - ")
            append(getString(R.string.axycord_build_label))
            append("\n")
            append(getString(R.string.axycord_maintainer))
        }

        for (row in listOf(binding.account, binding.privacy, binding.authorizedApps, binding.sessions,
            binding.voice, binding.notifications, binding.textImagesSettings, binding.appearance,
            binding.accessibility, binding.behavior, binding.language, binding.activityStatus,
            binding.backend, binding.proxy, binding.featureFlags, binding.plugins,
            binding.developerOptions, binding.updates)) {
            row.setOnClickListener { SettingsNavigation.push(this, routes.getValue(row.id)()) }
        }
        binding.profile.setOnClickListener { WidgetEditUserOrGuildMemberProfile.launch(requireContext()) }

        // Not a bindRoute: the scanner is a fullscreen screen with its own toolbar and status bar
        // colour, so it is launched through the screen router rather than pushed into this tab.
        binding.qrScanner.setOnClickListener { WidgetQRScanner.launch(requireContext()) }

        binding.support.setOnClickListener {
            UriHandler.handle(it.context, SUPPORT_URL)
        }
        binding.supportServer.setOnClickListener {
            UriHandler.handle(it.context, SUPPORT_SERVER_URL)
        }
        binding.acknowledgements.setOnClickListener {
            UriHandler.handle(it.context, ACKNOWLEDGEMENTS_URL)
        }
        binding.notificationUpsellEnable.setOnClickListener {
            openSystemNotificationSettings(it.context)
        }
        binding.notificationUpsellDismiss.setOnClickListener {
            viewModel.dismissPushNotificationUpsell()
        }

        listOf(
            binding.userSettingsHeader,
            binding.billingSettingsHeader,
            binding.appSettingsHeader,
            binding.developerOptionsHeader,
            binding.appInfoHeader,
        ).forEach { ViewCompat.setAccessibilityHeading(it, true) }
    }

    override fun onDestroyView() {
        SharedPreferencesProvider.get().unregisterOnSharedPreferenceChangeListener(featureListener)
        _binding = null
        super.onDestroyView()
    }

    override fun onViewBoundOrOnResume() {
        super.onViewBoundOrOnResume()
        configureToolbar()
        configureBilling()
        // Reference registers itself as the settings tab's `OnTabSelectedListener` and refreshes
        // here. `widgets/tabs` has no listener registry yet, so the refresh runs on every resume.
        viewModel.refreshSubscriptions()
        viewModel.observeViewState().ui(this).subscribe(::configureUI)
        viewModel.observeProfileHeaderViewState().ui(this).subscribe { state ->
            // `UserProfileHeaderView` only renders the loaded state; there is nothing to show for
            // any other one.
            if (state is UserProfileHeaderViewModel.ViewState.Loaded) {
                _binding?.userSettingsProfileHeaderView?.updateViewState(state)
            }
        }
    }

    private fun configureToolbar() {
        setActionBarTitle(R.string.user_settings)
        setActionBarTitleLayoutMinimumTappableArea()
        setActionBarOptionsMenu(
            R.menu.menu_settings,
            onSelected = { item ->
                when (item.itemId) {
                    R.id.menu_settings_search -> SettingsNavigation.push(this, WidgetSettingsSearch())
                }
            },
        )
    }

    private fun configureBilling() {
        val bound = _binding ?: return
        bound.settingsBillingQuests.visibility = if (FeatureFlags.questsEnabled()) View.VISIBLE else View.GONE
        bound.settingsBillingOrbsShop.visibility = if (FeatureFlags.orbsShopEnabled()) View.VISIBLE else View.GONE
    }

    private fun configureUI(viewState: SettingsViewModel.ViewState) {
        when (viewState) {
            is SettingsViewModel.ViewState.Uninitialized -> Unit
            is SettingsViewModel.ViewState.Loaded -> configureLoaded(viewState)
        }
    }

    private fun configureLoaded(model: SettingsViewModel.ViewState.Loaded) {
        if (_binding == null) return
        binding.settingsStatusView.setPresence(model.presence)
        binding.settingsPresenceText.setText(PresenceUtils.getStatusStringResForPresence(model.presence))
        binding.userSettingsProfileHeaderView.contentDescription = displayName(model.meUser)

        binding.developerOptionsDivider.visibility = View.VISIBLE
        binding.developerOptionsHeader.visibility = View.VISIBLE
        binding.developerOptions.visibility = View.VISIBLE

        val notificationsBlocked = !NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
        binding.notificationUpsell.visibility =
            if (notificationsBlocked && !model.pushNotificationUpsellDismissed) View.VISIBLE else View.GONE
    }

    private fun showLogoutDialog(context: Context) {
        WidgetNoticeDialog.Builder(context)
            .setTitle(R.string.logout)
            .setMessage(R.string.user_settings_confirm_logout)
            .setNegativeButton(R.string.cancel)
            .setPositiveButton(R.string.logout) {
                onLogout?.invoke() ?: StoreStream.getAuthentication().logout()
            }
            .setDialogAttrTheme(R.attr.notice_theme_positive_red)
            .show(parentFragmentManager)
    }

    private fun openSystemNotificationSettings(context: Context) {
        // Reference: `SystemSettingNavigators.openSystemNotification(context)`, in a utilities
        // package this slice does not own.
        val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        runCatching { context.startActivity(intent) }
    }

    private fun hideUnavailableRows() {
        val keep = setOf(
            R.id.user_settings_header,
            R.id.set_status_container,
            R.id.app_settings_header,
            R.id.app_settings_divider,
            R.id.billing_settings_header,
            R.id.billing_settings_divider,
            R.id.settings_billing_quests,
            R.id.settings_billing_orbs_shop,
            R.id.settings_billing_server_boosts,
            R.id.settings_server_boosts_status,
            R.id.settings_switch_accounts,
            R.id.settings_logout,
            R.id.developer_options_divider,
            R.id.developer_options_header,
            R.id.app_info_header,
            R.id.support,
            R.id.support_server,
            R.id.acknowledgements,
            *implementedRouteIds.toTypedArray(),
        )
        val rows = binding.userSettingsHeader.parent as ViewGroup
        for (index in 0 until rows.childCount) {
            val child = rows.getChildAt(index)
            child.visibility = if (child.id in keep) View.VISIBLE else View.GONE
        }
    }

    companion object {
        private const val SUPPORT_URL = "https://github.com/Axycord/Axycord/issues"
        private const val SUPPORT_SERVER_URL = "https://github.com/Axycord/Axycord/issues"
        private const val ACKNOWLEDGEMENTS_URL = "https://discord.com/acknowledgements"

        /** Rows that push a settings page into this tab, by row id. [WidgetSettingsSearch] indexes these pages. */
        internal val routes: Map<Int, () -> Fragment> = mapOf(
            R.id.account to ::WidgetSettingsAccount,
            R.id.privacy to ::WidgetSettingsPrivacy,
            R.id.authorized_apps to ::WidgetSettingsAuthorizedApps,
            R.id.sessions to ::WidgetSettingsSessions,
            R.id.voice to ::WidgetSettingsVoice,
            R.id.notifications to ::WidgetSettingsNotifications,
            R.id.text_images_settings to ::WidgetSettingsMedia,
            R.id.appearance to ::WidgetSettingsAppearance,
            R.id.accessibility to ::WidgetSettingsAccessibility,
            R.id.behavior to ::WidgetSettingsBehavior,
            R.id.language to ::WidgetSettingsLanguage,
            R.id.activity_status to ::WidgetSettingsActivityStatus,
            R.id.backend to ::WidgetSettingsBackend,
            R.id.proxy to ::WidgetSettingsProxy,
            R.id.feature_flags to ::WidgetSettingsFeatureFlags,
            R.id.plugins to ::WidgetSettingsPlugins,
            R.id.developer_options to ::WidgetSettingsDeveloper,
            R.id.updates to ::WidgetSettingsUpdates,
        )

        /** Rows whose destination screen exists; everything else is hidden by [hideUnavailableRows]. */
        internal val implementedRouteIds = routes.keys + setOf(R.id.profile, R.id.qr_scanner)

        /**
         * Host overrides, kept because `WidgetTabsHost` assigns them so the settings tab can push
         * destinations into its own child fragment manager instead of the parent one. Each has a
         * working default; the reference has no equivalent indirection.
         */
        @JvmField var onSetStatus: (() -> Unit)? = null
        @JvmField var onLogout: (() -> Unit)? = null

        internal fun displayName(user: MeUser) = user.displayName
    }
}
