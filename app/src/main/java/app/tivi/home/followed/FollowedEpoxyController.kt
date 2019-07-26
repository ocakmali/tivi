/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.tivi.home.followed

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import app.tivi.HeaderBindingModel_
import app.tivi.LibraryFollowedItemBindingModel_
import app.tivi.data.entities.SortOption
import app.tivi.data.entities.findHighestRatedPoster
import app.tivi.data.resultentities.FollowedShowEntryWithShow
import app.tivi.emptyState
import app.tivi.filter
import app.tivi.header
import app.tivi.home.HomeTextCreator
import app.tivi.ui.SortPopupMenuListener
import app.tivi.common.epoxy.EpoxyModelProperty
import app.tivi.common.epoxy.TotalSpanOverride
import app.tivi.ui.popupMenuItemIdToSortOption
import com.airbnb.epoxy.EpoxyAsyncUtil
import com.airbnb.epoxy.EpoxyModel
import com.airbnb.epoxy.paging.PagedListEpoxyController
import javax.inject.Inject

class FollowedEpoxyController @Inject constructor(
    private val textCreator: HomeTextCreator
) : PagedListEpoxyController<FollowedShowEntryWithShow>(
        modelBuildingHandler = Handler(Looper.getMainLooper()),
        diffingHandler = EpoxyAsyncUtil.getAsyncBackgroundHandler()
) {
    var viewState by EpoxyModelProperty { FollowedViewState() }
    var callbacks by EpoxyModelProperty<Callbacks?> { null }

    override fun addModels(models: List<EpoxyModel<*>>) {
        if (viewState.isEmpty) {
            emptyState {
                id("empty")
                spanSizeOverride(TotalSpanOverride)
            }
        } else {
            header {
                id("header")
                titleString(textCreator.showHeaderCount(models.size, viewState.filterActive))
            }
            filter {
                id("filters")
                filter(viewState.filter)
                watcher(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        callbacks?.onFilterChanged(s?.toString() ?: "")
                    }

                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                })

                popupMenuListener(SortPopupMenuListener(viewState.sort, viewState.availableSorts))
                popupMenuClickListener {
                    val option = popupMenuItemIdToSortOption(it.itemId)
                            ?: throw IllegalArgumentException("Selected sort option is null")
                    callbacks?.onSortSelected(option)
                    true
                }
            }
            super.addModels(models)
        }
    }

    override fun buildItemModel(currentPosition: Int, item: FollowedShowEntryWithShow?): EpoxyModel<*> {
        return LibraryFollowedItemBindingModel_().apply {
            if (item != null) {
                id(item.generateStableId())
                tiviShow(item.show)
                posterImage(item.images.findHighestRatedPoster())
                posterTransitionName("show_${item.show.homepage}")
                clickListener(View.OnClickListener {
                    callbacks?.onItemClicked(item)
                })
            } else {
                id("item_placeholder_$currentPosition")
            }
            followedEntry(item?.entry)
            stats(item?.stats)
            textCreator(textCreator)
            tmdbImageUrlProvider(viewState.tmdbImageUrlProvider)
        }
    }

    fun isHeader(model: EpoxyModel<*>): Boolean {
        return model is HeaderBindingModel_
    }

    interface Callbacks {
        fun onItemClicked(item: FollowedShowEntryWithShow)
        fun onFilterChanged(filter: String)
        fun onSortSelected(sort: SortOption)
    }
}