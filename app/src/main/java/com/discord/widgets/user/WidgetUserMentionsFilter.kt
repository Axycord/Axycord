package com.discord.widgets.user

import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentManager
import com.discord.R
import com.discord.app.AppBottomSheet
import com.discord.databinding.WidgetUserMentionsFilterBinding
import rx.functions.Action1

class WidgetUserMentionsFilter : AppBottomSheet() {
    private var filters = MentionFilters.ALL

    override fun getContentViewResId() = R.layout.widget_user_mentions_filter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filters = (savedInstanceState ?: arguments)?.getInt(FILTERS, MentionFilters.ALL) ?: MentionFilters.ALL
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = WidgetUserMentionsFilterBinding.bind(view)
        listOf(
            binding.userMentionsFilterYou to MentionFilters.YOU,
            binding.userMentionsFilterIncludeEveryone to MentionFilters.EVERYONE,
            binding.userMentionsFilterIncludeRoles to MentionFilters.ROLES,
        ).forEach { (setting, flag) ->
            setting.setChecked(filters and flag != 0, false)
            setting.setOnCheckedListener(Action1 { checked ->
                filters = if (checked) filters or flag else filters and flag.inv()
                parentFragmentManager.setFragmentResult(REQUEST_KEY, Bundle().apply { putInt(FILTERS, filters) })
            })
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(FILTERS, filters)
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val REQUEST_KEY = "axycord_mentions_filters"
        const val FILTERS = "filters"

        fun show(manager: FragmentManager, filters: Int) {
            if (manager.isStateSaved || manager.findFragmentByTag(REQUEST_KEY) != null) return
            WidgetUserMentionsFilter().apply {
                arguments = Bundle().apply { putInt(FILTERS, filters) }
            }.show(manager, REQUEST_KEY)
        }
    }
}
