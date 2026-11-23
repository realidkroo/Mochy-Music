package com.music.innertube.models.response

import com.music.innertube.models.AccountInfo
import com.music.innertube.models.Icon
import com.music.innertube.models.NavigationEndpoint
import com.music.innertube.models.Runs
import com.music.innertube.models.Thumbnails
import kotlinx.serialization.Serializable

@Serializable
data class AccountMenuResponse(
    val actions: List<Action>,
) {
    @Serializable
    data class Action(
        val openPopupAction: OpenPopupAction,
    ) {
        @Serializable
        data class OpenPopupAction(
            val popup: Popup,
        ) {
            @Serializable
            data class Popup(
                val multiPageMenuRenderer: MultiPageMenuRenderer,
            ) {
                @Serializable
                data class MultiPageMenuRenderer(
                    val header: Header?,
                    val sections: List<Section>? = null,
                ) {
                    @Serializable
                    data class Header(
                        val activeAccountHeaderRenderer: ActiveAccountHeaderRenderer,
                    ) {
                        @Serializable
                        data class ActiveAccountHeaderRenderer(
                            val accountName: Runs,
                            val email: Runs?,
                            val channelHandle: Runs?,
                            val accountPhoto: Thumbnails,
                        ) {
                            fun toAccountInfo(isPremium: Boolean? = null) =
                                AccountInfo(
                                    name = accountName.runs!!.first().text,
                                    email = email?.runs?.first()?.text,
                                    channelHandle = channelHandle?.runs?.first()?.text,
                                    thumbnailUrl = accountPhoto.thumbnails.lastOrNull()?.url?.let {
                                        if (it.startsWith("//")) "https:$it" else it
                                    },
                                    isPremium = isPremium,
                                )
                        }
                    }

                    @Serializable
                    data class Section(
                        val multiPageMenuSectionRenderer: MultiPageMenuSectionRenderer? = null,
                    ) {
                        @Serializable
                        data class MultiPageMenuSectionRenderer(
                            val items: List<Item>? = null,
                        ) {
                            @Serializable
                            data class Item(
                                val compactLinkRenderer: CompactLinkRenderer? = null,
                            ) {
                                @Serializable
                                data class CompactLinkRenderer(
                                    val icon: Icon? = null,
                                    val title: Runs? = null,
                                    val navigationEndpoint: NavigationEndpoint? = null,
                                )
                            }
                        }
                    }

                    /**
                     * Infers YouTube Music Premium status from the account menu items:
                     * - Free accounts show an upsell entry ("Get Music Premium", urlEndpoint containing "music_premium" / iconType "YOUTUBE_MUSIC_MONOCHROME" or "UPGRADE").
                     * - Premium accounts show "Paid memberships" / "Manage membership" (browseId "SPunlimited" or iconType "MONETIZATION_ON" / "PURCHASES" / "PAID_MEMBERSHIPS").
                     * Returns null when the menu layout can't tell conclusively.
                     */
                    fun detectPremium(): Boolean? {
                        val links = sections.orEmpty()
                            .flatMap { it.multiPageMenuSectionRenderer?.items.orEmpty() }
                            .mapNotNull { it.compactLinkRenderer }
                        if (links.isEmpty()) return null
                        var sawUpsell = false
                        var sawPaidMembership = false
                        for (link in links) {
                            val iconType = link.icon?.iconType.orEmpty()
                            val browseId = link.navigationEndpoint?.browseEndpoint?.browseId.orEmpty()
                            val url = link.navigationEndpoint?.urlEndpoint?.url.orEmpty()
                            val label = link.title?.runs.orEmpty().joinToString("") { it.text }
                            val isUpsell =
                                url.contains("music_premium", ignoreCase = true) ||
                                    url.contains("premium", ignoreCase = true) && label.contains("Get", ignoreCase = true) ||
                                    label.contains("Get Music Premium", ignoreCase = true) ||
                                    label.contains("Get YouTube Premium", ignoreCase = true) ||
                                    label.contains("Upgrade", ignoreCase = true)
                            if (isUpsell) {
                                sawUpsell = true
                                continue
                            }
                            if (
                                browseId == "SPunlimited" ||
                                iconType == "MONETIZATION_ON" ||
                                iconType == "PAID_MEMBERSHIPS" ||
                                iconType == "PURCHASES" ||
                                label.contains("Paid memberships", ignoreCase = true) ||
                                label.contains("Manage membership", ignoreCase = true)
                            ) {
                                sawPaidMembership = true
                            }
                        }
                        return when {
                            sawUpsell -> false
                            sawPaidMembership -> true
                            else -> null
                        }
                    }
                }
            }
        }
    }
}
