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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.exception.CacheMissException
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.watch
import com.example.apollokotlinpaginationsample.R
import com.example.apollokotlinpaginationsample.graphql.RepositoryListQuery
import com.example.apollokotlinpaginationsample.graphql.fragment.RepositoryFields
import com.example.apollokotlinpaginationsample.repository.apolloClient
import com.example.apollokotlinpaginationsample.repository.fetchAndMergeNextPage
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val responseFlow = apolloClient.query(RepositoryListQuery())
            .watch()
            .filterNot { it.exception is CacheMissException }
        setContent {
            val response: ApolloResponse<RepositoryListQuery.Data>? by responseFlow.collectAsState(initial = null)
            MaterialTheme {
                Column(modifier = Modifier.fillMaxSize()) {
                    RefreshBanner()
                    if (response == null) {
                        Text(text = "Loading...")
                    } else {
                        RepositoryList(response!!)
                    }
                }
            }
        }
    }
}

@Composable
private fun RefreshBanner() {
    val coroutineScope = rememberCoroutineScope()
    Box(modifier = Modifier.fillMaxWidth()) {
        Button(
            modifier = Modifier.align(Alignment.Center),
            onClick = {
                coroutineScope.launch {
                    // Re-fetching the 1st page from the network will discard all other pages from the cache
                    apolloClient.query(RepositoryListQuery())
                        .fetchPolicy(FetchPolicy.NetworkOnly)
                        .execute()
                }
            }
        ) {
            Text("Refresh")
        }
    }
}

@Composable
private fun RepositoryList(response: ApolloResponse<RepositoryListQuery.Data>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(response.data!!.organization!!.repositories.edges!!.map { it!!.node!!.repositoryFields }) {
            RepositoryItem(it)
        }
        item {
            if (response.data!!.organization!!.repositories.pageInfo.hasNextPage) {
                LoadingItem()
                LaunchedEffect(Unit) {
                    fetchAndMergeNextPage()
                }
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
                Text(text = repositoryFields.stargazers.totalCount.toString(), style = MaterialTheme.typography.bodyMedium)
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
