package com.jing.sakura.detail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import com.jing.sakura.R
import com.jing.sakura.compose.screen.DetailScreen
import com.jing.sakura.compose.theme.setAulamaTvContent
import com.jing.sakura.data.AnimeData
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class DetailActivity : ComponentActivity() {

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoId = intent.getStringExtra("id")!!
        val sourceId = intent.getStringExtra("source")!!
        val viewModel by viewModel<DetailPageViewModel> { parametersOf(videoId, sourceId) }
        setAulamaTvContent {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .padding(
                        dimensionResource(id = R.dimen.screen_h_padding),
                        dimensionResource(id = R.dimen.screen_v_padding)
                    )
                    .fillMaxSize()
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                    androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface
                ) {
                    DetailScreen(
                        viewModel = viewModel,
                        initialTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                        initialImageUrl = intent.getStringExtra(EXTRA_IMAGE_URL).orEmpty(),
                        initialTags = intent.getStringExtra(EXTRA_TAGS).orEmpty(),
                        initialEpisodeInfo = intent.getStringExtra(EXTRA_EPISODE_INFO).orEmpty(),
                        initialResumeEpisode = intent.getStringExtra(EXTRA_RESUME_EPISODE).orEmpty()
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_IMAGE_URL = "image_url"
        private const val EXTRA_TAGS = "tags"
        private const val EXTRA_EPISODE_INFO = "episode_info"
        private const val EXTRA_RESUME_EPISODE = "resume_episode"

        fun startActivity(
            context: Context,
            anime: AnimeData,
            sourceId: String = anime.sourceId,
            resumeEpisode: String = ""
        ) {
            startActivity(
                context = context,
                animeId = anime.id,
                sourceId = sourceId,
                title = anime.title,
                imageUrl = anime.imageUrl,
                tags = anime.tags,
                episodeInfo = anime.currentEpisode,
                resumeEpisode = resumeEpisode
            )
        }

        fun startActivity(
            context: Context,
            animeId: String,
            sourceId: String,
            title: String = "",
            imageUrl: String = "",
            tags: String = "",
            episodeInfo: String = "",
            resumeEpisode: String = ""
        ) {
            Intent(context, DetailActivity::class.java).apply {
                putExtra("id", animeId)
                putExtra("source", sourceId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_IMAGE_URL, imageUrl)
                putExtra(EXTRA_TAGS, tags)
                putExtra(EXTRA_EPISODE_INFO, episodeInfo)
                putExtra(EXTRA_RESUME_EPISODE, resumeEpisode)
                context.startActivity(this)
            }
        }
    }
}
