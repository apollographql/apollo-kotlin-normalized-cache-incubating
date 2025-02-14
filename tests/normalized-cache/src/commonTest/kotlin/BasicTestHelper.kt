package test

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Query
import com.apollographql.apollo.testing.internal.runTest
import com.apollographql.cache.normalized.ApolloStore
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.api.IdCacheKeyGenerator
import com.apollographql.cache.normalized.api.NormalizedCacheFactory
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.store
import com.apollographql.mockserver.MockServer
import com.apollographql.mockserver.enqueueString
import httpcache.AllPlanetsQuery
import normalizer.EpisodeHeroNameQuery
import normalizer.HeroAndFriendsNamesQuery
import normalizer.HeroAndFriendsNamesWithIDForParentOnlyQuery
import normalizer.HeroAndFriendsNamesWithIDsQuery
import normalizer.HeroAppearsInQuery
import normalizer.HeroWithFragmentsAndSkipQuery
import normalizer.HeroWithFragmentsWithTypeConditionQuery
import normalizer.SameHeroTwiceQuery
import normalizer.StarshipByIdQuery
import normalizer.fragment.DroidDetails
import normalizer.fragment.HeroDetails
import normalizer.type.Episode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class BasicTestHelper {
  private fun <D : Query.Data> basicTest(
      cacheFactory: NormalizedCacheFactory,
      resourceName: String,
      query: Query<D>,
      block: ApolloResponse<D>.() -> Unit,
  ) = runTest {
    val mockServer = MockServer()
    val apolloClient = ApolloClient.Builder()
        .serverUrl(mockServer.url())
        .store(
            ApolloStore(
                normalizedCacheFactory = cacheFactory,
                cacheKeyGenerator = IdCacheKeyGenerator()
            ).also { it.clearAll() }
        ).build()
    try {
      mockServer.enqueueString(testFixtureToUtf8(resourceName))
      var response = apolloClient.query(query)
          .fetchPolicy(FetchPolicy.NetworkOnly)
          .execute()
      response.block()
      response = apolloClient.query(query)
          .fetchPolicy(FetchPolicy.CacheOnly)
          .execute()
      response.block()
    } finally {
      apolloClient.close()
      mockServer.close()
    }
  }

  fun episodeHeroName(cacheFactory: NormalizedCacheFactory) = basicTest(
      cacheFactory,
      "HeroNameResponse.json",
      EpisodeHeroNameQuery(Episode.EMPIRE)
  ) {

    assertFalse(hasErrors())
    assertEquals(data?.hero?.name, "R2-D2")
  }

  fun heroAndFriendsNameResponse(cacheFactory: NormalizedCacheFactory) = basicTest(
      cacheFactory,
      "HeroAndFriendsNameResponse.json",
      HeroAndFriendsNamesQuery(Episode.JEDI)
  ) {

    assertFalse(hasErrors())
    assertEquals(data?.hero?.name, "R2-D2")
    assertEquals(data?.hero?.friends?.size, 3)
    assertEquals(data?.hero?.friends?.get(0)?.name, "Luke Skywalker")
    assertEquals(data?.hero?.friends?.get(1)?.name, "Han Solo")
    assertEquals(data?.hero?.friends?.get(2)?.name, "Leia Organa")
  }

  fun heroAndFriendsNamesWithIDs(cacheFactory: NormalizedCacheFactory) = basicTest(
      cacheFactory,
      "HeroAndFriendsNameWithIdsResponse.json",
      HeroAndFriendsNamesWithIDsQuery(Episode.NEWHOPE)
  ) {

    assertFalse(hasErrors())
    assertEquals(data?.hero?.id, "2001")
    assertEquals(data?.hero?.name, "R2-D2")
    assertEquals(data?.hero?.friends?.size, 3)
    assertEquals(data?.hero?.friends?.get(0)?.id, "1000")
    assertEquals(data?.hero?.friends?.get(0)?.name, "Luke Skywalker")
    assertEquals(data?.hero?.friends?.get(1)?.id, "1002")
    assertEquals(data?.hero?.friends?.get(1)?.name, "Han Solo")
    assertEquals(data?.hero?.friends?.get(2)?.id, "1003")
    assertEquals(data?.hero?.friends?.get(2)?.name, "Leia Organa")
  }

  fun heroAndFriendsNameWithIdsForParentOnly(cacheFactory: NormalizedCacheFactory) = basicTest(
      cacheFactory,
      "HeroAndFriendsNameWithIdsParentOnlyResponse.json",
      HeroAndFriendsNamesWithIDForParentOnlyQuery(Episode.NEWHOPE)
  ) {

    assertFalse(hasErrors())
    assertEquals(data?.hero?.id, "2001")
    assertEquals(data?.hero?.name, "R2-D2")
    assertEquals(data?.hero?.friends?.size, 3)
    assertEquals(data?.hero?.friends?.get(0)?.name, "Luke Skywalker")
    assertEquals(data?.hero?.friends?.get(1)?.name, "Han Solo")
    assertEquals(data?.hero?.friends?.get(2)?.name, "Leia Organa")
  }

  fun heroAppearsInResponse(cacheFactory: NormalizedCacheFactory) = basicTest(
      cacheFactory,
      "HeroAppearsInResponse.json",
      HeroAppearsInQuery()
  ) {

    assertFalse(hasErrors())
    assertEquals(data?.hero?.appearsIn?.size, 3)
    assertEquals(data?.hero?.appearsIn?.get(0), Episode.NEWHOPE)
    assertEquals(data?.hero?.appearsIn?.get(1), Episode.EMPIRE)
    assertEquals(data?.hero?.appearsIn?.get(2), Episode.JEDI)
  }

  fun heroAppearsInResponseWithNulls(cacheFactory: NormalizedCacheFactory) = basicTest(
      cacheFactory,
      "HeroAppearsInResponseWithNulls.json",
      HeroAppearsInQuery()
  ) {

    assertFalse(hasErrors())
    assertEquals(data?.hero?.appearsIn?.size, 6)
    assertNull(data?.hero?.appearsIn?.get(0))
    assertEquals(data?.hero?.appearsIn?.get(1), Episode.NEWHOPE)
    assertEquals(data?.hero?.appearsIn?.get(2), Episode.EMPIRE)
    assertNull(data?.hero?.appearsIn?.get(3))
    assertEquals(data?.hero?.appearsIn?.get(4), Episode.JEDI)
    assertNull(data?.hero?.appearsIn?.get(5))
  }

  fun requestingTheSameFieldTwiceWithAnAlias(cacheFactory: NormalizedCacheFactory) = basicTest(
      cacheFactory,
      "SameHeroTwiceResponse.json",
      SameHeroTwiceQuery()
  ) {
    assertFalse(hasErrors())
    assertEquals(data?.hero?.name, "R2-D2")
    assertEquals(data?.r2?.appearsIn?.size, 3)
    assertEquals(data?.r2?.appearsIn?.get(0), Episode.NEWHOPE)
    assertEquals(data?.r2?.appearsIn?.get(1), Episode.EMPIRE)
    assertEquals(data?.r2?.appearsIn?.get(2), Episode.JEDI)
  }

  fun cacheResponseWithNullableFields(cacheFactory: NormalizedCacheFactory) = basicTest(
      cacheFactory,
      "AllPlanetsNullableField.json",
      AllPlanetsQuery()
  ) {
    assertFalse(hasErrors())
  }

  fun readList(cacheFactory: NormalizedCacheFactory) = basicTest(
      cacheFactory,
      "HeroAndFriendsNameWithIdsResponse.json",
      HeroAndFriendsNamesWithIDsQuery(Episode.NEWHOPE)
  ) {
    assertEquals(data?.hero?.id, "2001")
    assertEquals(data?.hero?.name, "R2-D2")
    assertEquals(data?.hero?.friends?.size, 3)
    assertEquals(data?.hero?.friends?.get(0)?.id, "1000")
    assertEquals(data?.hero?.friends?.get(0)?.name, "Luke Skywalker")
    assertEquals(data?.hero?.friends?.get(1)?.id, "1002")
    assertEquals(data?.hero?.friends?.get(1)?.name, "Han Solo")
    assertEquals(data?.hero?.friends?.get(2)?.id, "1003")
    assertEquals(data?.hero?.friends?.get(2)?.name, "Leia Organa")
  }

  fun listOfList(cacheFactory: NormalizedCacheFactory) = basicTest(
      cacheFactory,
      "StarshipByIdResponse.json",
      StarshipByIdQuery("Starship1")
  ) {
    assertEquals(data?.starship?.name, "SuperRocket")
    assertEquals(data?.starship?.coordinates,
        listOf(
            listOf(100.0, 200.0),
            listOf(300.0, 400.0),
            listOf(500.0, 600.0)
        )
    )
  }

  fun skipFalse(cacheFactory: NormalizedCacheFactory) = basicTest(
      cacheFactory,
      "HeroWithFragmentsAndSkipFalseResponse.json",
      HeroWithFragmentsAndSkipQuery(skipSomeFields = false)
  ) {
    assertFalse(hasErrors())
    assertNull(exception)
    assertEquals(
        HeroWithFragmentsAndSkipQuery.Data(
            HeroWithFragmentsAndSkipQuery.Hero(
                __typename = "Droid",
                name = "R2-D2",
                appearsIn = listOf(Episode.NEWHOPE, Episode.EMPIRE, Episode.JEDI),
                heroDetails = HeroDetails(
                    "1977-05-25T00:00:00.000Z",
                    listOf(
                        HeroDetails.Friend("1000", "Luke Skywalker"),
                        HeroDetails.Friend("1002", "Han Solo"),
                        HeroDetails.Friend("1003", "Leia Organa")
                    )
                )
            )
        ),
        data
    )
  }

  fun skipTrue(cacheFactory: NormalizedCacheFactory) = basicTest(
      cacheFactory,
      "HeroWithFragmentsAndSkipTrueResponse.json",
      HeroWithFragmentsAndSkipQuery(skipSomeFields = true)
  ) {
    assertFalse(hasErrors())
    assertNull(exception)
    assertEquals(
        HeroWithFragmentsAndSkipQuery.Data(
            HeroWithFragmentsAndSkipQuery.Hero(
                __typename = "Droid",
                name = "R2-D2",
                appearsIn = null,
                heroDetails = null
            )
        ),
        data
    )
  }

  fun fragmentWithTypeConditionMatches(cacheFactory: NormalizedCacheFactory) = basicTest(
      cacheFactory,
      "HeroWithFragmentsWithTypeConditionDroid.json",
      HeroWithFragmentsWithTypeConditionQuery()
  ) {
    assertFalse(hasErrors())
    assertNull(exception)
    assertEquals(
        HeroWithFragmentsWithTypeConditionQuery.Data(
            HeroWithFragmentsWithTypeConditionQuery.Hero(
                __typename = "Droid",
                name = "R2-D2",
                droidDetails = DroidDetails(
                    "1977-05-25T00:00:00.000Z",
                    listOf(
                        DroidDetails.Friend("1000", "Luke Skywalker"),
                        DroidDetails.Friend("1002", "Han Solo"),
                        DroidDetails.Friend("1003", "Leia Organa")
                    )
                )
            )
        ),
        data
    )
  }

  fun fragmentWithTypeConditionNoMatch(cacheFactory: NormalizedCacheFactory) = basicTest(
      cacheFactory,
      "HeroWithFragmentsWithTypeConditionHuman.json",
      HeroWithFragmentsWithTypeConditionQuery()
  ) {
    assertFalse(hasErrors())
    assertNull(exception)
    assertEquals(
        HeroWithFragmentsWithTypeConditionQuery.Data(
            HeroWithFragmentsWithTypeConditionQuery.Hero(
                __typename = "Human",
                name = "Han Solo",
                droidDetails = null
            )
        ),
        data
    )
  }

}
