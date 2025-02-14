package test

import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import kotlin.test.Test

/**
 * A series of high level cache tests that use NetworkOnly to cache a json and then retrieve it with CacheOnly and make
 * sure everything works.
 *
 * The tests are simple and are most likely already covered by the other tests but it's kept here for consistency
 * and maybe they'll catch something one day?
 */
class BasicTest {
  private val basicTestHelper = BasicTestHelper()
  private val normalizedCacheFactory = MemoryCacheFactory()


  @Test
  fun episodeHeroName() = basicTestHelper.episodeHeroName(normalizedCacheFactory)

  @Test
  fun heroAndFriendsNameResponse() = basicTestHelper.heroAndFriendsNameResponse(normalizedCacheFactory)

  @Test
  fun heroAndFriendsNamesWithIDs() = basicTestHelper.heroAndFriendsNamesWithIDs(normalizedCacheFactory)

  @Test
  fun heroAndFriendsNameWithIdsForParentOnly() = basicTestHelper.heroAndFriendsNameWithIdsForParentOnly(normalizedCacheFactory)

  @Test
  fun heroAppearsInResponse() = basicTestHelper.heroAppearsInResponse(normalizedCacheFactory)

  @Test
  fun heroAppearsInResponseWithNulls() = basicTestHelper.heroAppearsInResponseWithNulls(normalizedCacheFactory)

  @Test
  fun requestingTheSameFieldTwiceWithAnAlias() = basicTestHelper.requestingTheSameFieldTwiceWithAnAlias(normalizedCacheFactory)

  @Test
  fun cacheResponseWithNullableFields() = basicTestHelper.cacheResponseWithNullableFields(normalizedCacheFactory)

  @Test
  fun readList() = basicTestHelper.readList(normalizedCacheFactory)

  @Test
  fun listOfList() = basicTestHelper.listOfList(normalizedCacheFactory)

  @Test
  fun skipFalse() = basicTestHelper.skipFalse(normalizedCacheFactory)

  @Test
  fun skipTrue() = basicTestHelper.skipTrue(normalizedCacheFactory)

  @Test
  fun fragmentWithTypeConditionMatches() = basicTestHelper.fragmentWithTypeConditionMatches(normalizedCacheFactory)

  @Test
  fun fragmentWithTypeConditionNoMatch() = basicTestHelper.fragmentWithTypeConditionNoMatch(normalizedCacheFactory)
}
