@file:OptIn(ExperimentalPagingApi::class)

package com.example.apollokotlinpaginationsample.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.example.apollokotlinpaginationsample.R
import com.example.apollokotlinpaginationsample.graphql.RepositoryListQuery
import com.example.apollokotlinpaginationsample.graphql.fragment.RepositoryFields
import com.example.apollokotlinpaginationsample.repository.RepositoryPagingSource
import com.example.apollokotlinpaginationsample.repository.RepositoryRemoteMediator
import kotlinx.coroutines.flow.Flow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repositoryPagingData: Flow<PagingData<RepositoryListQuery.Edge>> = Pager(
            config = PagingConfig(pageSize = 15, enablePlaceholders = false),
            remoteMediator = RepositoryRemoteMediator(),
            pagingSourceFactory = {
                RepositoryPagingSource(lifecycleScope)
            },
        ).flow
        setContent {
            val repositoryPagingItems: LazyPagingItems<RepositoryListQuery.Edge> =
                repositoryPagingData.collectAsLazyPagingItems()
            MaterialTheme {
                Column(modifier = Modifier.fillMaxSize()) {
                    RefreshBanner(repositoryPagingItems)
                    RepositoryList(repositoryPagingItems)
                }
            }
        }
    }
}

@Composable
private fun RefreshBanner(repositoryPagingItems: LazyPagingItems<RepositoryListQuery.Edge>) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Button(
            modifier = Modifier.align(Alignment.Center),
            onClick = {
                repositoryPagingItems.refresh()
            }
        ) {
            Text("Refresh")
        }
    }
}

@Composable
private fun RepositoryList(repositoryPagingItems: LazyPagingItems<RepositoryListQuery.Edge>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(
            count = repositoryPagingItems.itemCount,
            key = repositoryPagingItems.itemKey { it.node!!.id },
        ) { index ->
            val edge: RepositoryListQuery.Edge = repositoryPagingItems[index]!!
            RepositoryItem(edge.node!!.repositoryFields)
        }

        if (repositoryPagingItems.loadState.append == LoadState.Loading) {
            item {
                LoadingItem()
            }
        }
    }
}

@Composable
private fun RepositoryItem(repositoryFields: RepositoryFields) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(modifier = Modifier.weight(1F), text = repositoryFields.name)
                Text(
                    text = repositoryFields.stargazers.totalCount.toString(),
                    style = MaterialTheme.typography.bodyMedium
                )
                Icon(
                    painter = painterResource(R.drawable.ic_star_black_16dp),
                    contentDescription = null
                )
            }
        },
        supportingContent = {
            Text(repositoryFields.description.orEmpty())
        }
    )
}


@Composable
private fun LoadingItem() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        CircularProgressIndicator()
    }
}
