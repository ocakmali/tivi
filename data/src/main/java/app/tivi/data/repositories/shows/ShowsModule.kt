/*
 * Copyright 2018 Google LLC
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

package app.tivi.data.repositories.shows

import app.tivi.data.daos.ShowImagesDao
import app.tivi.data.daos.TiviShowDao
import app.tivi.data.entities.ErrorResult
import app.tivi.data.entities.Success
import app.tivi.data.entities.TiviShow
import app.tivi.data.repositories.ShowImagesStore
import app.tivi.data.repositories.ShowStore
import app.tivi.inject.Tmdb
import app.tivi.inject.Trakt
import com.dropbox.android.external.store4.StoreBuilder
import dagger.Binds
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
internal abstract class ShowsModuleBinds {
    @Binds
    @Trakt
    abstract fun bindTraktShowDataSource(source: TraktShowDataSource): ShowDataSource

    @Binds
    @Tmdb
    abstract fun bindTmdbShowDataSource(source: TmdbShowDataSource): ShowDataSource

    @Binds
    @Tmdb
    abstract fun bindTmdbShowImagesDataSource(source: TmdbShowImagesDataSource): ShowImagesDataSource
}

@Module(includes = [ShowsModuleBinds::class])
class ShowsModule {
    @Provides
    @Singleton
    fun provideShowStore(
        showDao: TiviShowDao,
        @Trakt traktShowDataSource: ShowDataSource,
        @Tmdb tmdbShowDataSource: ShowDataSource
    ): ShowStore {
        return StoreBuilder.fromNonFlow { showId: Long ->
            val localShow = showDao.getShowWithId(showId) ?: TiviShow.EMPTY_SHOW
            // TODO parallelize these calls again
            val traktResult = traktShowDataSource.getShow(localShow)
            val tmdbResult = tmdbShowDataSource.getShow(localShow)

            mergeShows(
                localShow,
                traktResult.get() ?: TiviShow.EMPTY_SHOW,
                tmdbResult.get() ?: TiviShow.EMPTY_SHOW
            )
        }.persister(
            reader = showDao::getShowWithIdFlow,
            writer = { _, show -> showDao.insertOrUpdate(show) },
            delete = showDao::delete,
            deleteAll = showDao::deleteAll
        ).build()
    }

    @Provides
    @Singleton
    fun provideTmdbShowImagesStore(
        showImagesDao: ShowImagesDao,
        showDao: TiviShowDao,
        @Tmdb tmdbShowImagesDataSource: ShowImagesDataSource
    ): ShowImagesStore {
        return StoreBuilder.fromNonFlow { showId: Long ->
            val show = showDao.getShowWithId(showId)
                ?: throw IllegalArgumentException("Show with ID $showId does not exist")
            when (val result = tmdbShowImagesDataSource.getShowImages(show)) {
                is Success -> result.get().map { it.copy(showId = showId) }
                is ErrorResult -> throw result.throwable!!
            }
        }.persister(
            reader = showImagesDao::getImagesForShowId,
            writer = { showId, images -> showImagesDao.saveImages(showId, images) },
            delete = showImagesDao::deleteForShowId,
            deleteAll = showImagesDao::deleteAll
        ).build()
    }
}
