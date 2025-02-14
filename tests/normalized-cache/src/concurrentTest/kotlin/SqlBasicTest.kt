import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import com.apollographql.cache.normalized.sql.SqlNormalizedCacheFactory
import test.BasicTestHelper
import kotlin.test.Test

/**
 * A series of high level cache tests that use NetworkOnly to cache a json and then retrieve it with CacheOnly and make
 * sure everything works.
 *
 * The tests are simple and are most likely already covered by the other tests but it's kept here for consistency
 * and maybe they'll catch something one day?
 */
class SqlBasicTest {
  private val basicTestHelper = BasicTestHelper()
  private val sqlNormalizedCacheFactory = SqlNormalizedCacheFactory()
  private val memoryThenSqlNormalizedCacheFactory = MemoryCacheFactory().chain(sqlNormalizedCacheFactory)


  @Test
  fun sqlEpisodeHeroName() = basicTestHelper.episodeHeroName(sqlNormalizedCacheFactory)

  @Test
  fun memoryThenSqlEpisodeHeroName() = basicTestHelper.episodeHeroName(memoryThenSqlNormalizedCacheFactory)

  @Test
  fun sqlHeroAndFriendsNameResponse() = basicTestHelper.heroAndFriendsNameResponse(sqlNormalizedCacheFactory)

  @Test
  fun memoryThenSqlHeroAndFriendsNameResponse() = basicTestHelper.heroAndFriendsNameResponse(memoryThenSqlNormalizedCacheFactory)

  @Test
  fun sqlHeroAndFriendsNamesWithIDs() = basicTestHelper.heroAndFriendsNamesWithIDs(sqlNormalizedCacheFactory)

  @Test
  fun memoryThenSqlHeroAndFriendsNamesWithIDs() = basicTestHelper.heroAndFriendsNamesWithIDs(memoryThenSqlNormalizedCacheFactory)

  @Test
  fun sqlHeroAndFriendsNameWithIdsForParentOnly() = basicTestHelper.heroAndFriendsNameWithIdsForParentOnly(sqlNormalizedCacheFactory)

  @Test
  fun memoryThenSqlHeroAndFriendsNameWithIdsForParentOnly() =
    basicTestHelper.heroAndFriendsNameWithIdsForParentOnly(memoryThenSqlNormalizedCacheFactory)

  @Test
  fun sqlHeroAppearsInResponse() = basicTestHelper.heroAppearsInResponse(sqlNormalizedCacheFactory)

  @Test
  fun memoryThenSqlHeroAppearsInResponse() = basicTestHelper.heroAppearsInResponse(memoryThenSqlNormalizedCacheFactory)

  @Test
  fun sqlHeroAppearsInResponseWithNulls() = basicTestHelper.heroAppearsInResponseWithNulls(sqlNormalizedCacheFactory)

  @Test
  fun memoryThenSqlHeroAppearsInResponseWithNulls() = basicTestHelper.heroAppearsInResponseWithNulls(memoryThenSqlNormalizedCacheFactory)

  @Test
  fun sqlRequestingTheSameFieldTwiceWithAnAlias() = basicTestHelper.requestingTheSameFieldTwiceWithAnAlias(sqlNormalizedCacheFactory)

  @Test
  fun memoryThenSqlRequestingTheSameFieldTwiceWithAnAlias() =
    basicTestHelper.requestingTheSameFieldTwiceWithAnAlias(memoryThenSqlNormalizedCacheFactory)

  @Test
  fun sqlCacheResponseWithNullableFields() = basicTestHelper.cacheResponseWithNullableFields(sqlNormalizedCacheFactory)

  @Test
  fun memoryThenSqlCacheResponseWithNullableFields() = basicTestHelper.cacheResponseWithNullableFields(memoryThenSqlNormalizedCacheFactory)

  @Test
  fun sqlReadList() = basicTestHelper.readList(sqlNormalizedCacheFactory)

  @Test
  fun memoryThenSqlReadList() = basicTestHelper.readList(memoryThenSqlNormalizedCacheFactory)

  @Test
  fun sqlListOfList() = basicTestHelper.listOfList(sqlNormalizedCacheFactory)

  @Test
  fun memoryThenSqlListOfList() = basicTestHelper.listOfList(memoryThenSqlNormalizedCacheFactory)

  @Test
  fun sqlSkipFalse() = basicTestHelper.skipFalse(sqlNormalizedCacheFactory)

  @Test
  fun memoryThenSqlSkipFalse() = basicTestHelper.skipFalse(memoryThenSqlNormalizedCacheFactory)

  @Test
  fun sqlSkipTrue() = basicTestHelper.skipTrue(sqlNormalizedCacheFactory)

  @Test
  fun memoryThenSqlSkipTrue() = basicTestHelper.skipTrue(memoryThenSqlNormalizedCacheFactory)

  @Test
  fun sqlFragmentWithTypeConditionMatches() = basicTestHelper.fragmentWithTypeConditionMatches(sqlNormalizedCacheFactory)

  @Test
  fun memoryThenSqlFragmentWithTypeConditionMatches() =
    basicTestHelper.fragmentWithTypeConditionMatches(memoryThenSqlNormalizedCacheFactory)

  @Test
  fun sqlFragmentWithTypeConditionNoMatch() = basicTestHelper.fragmentWithTypeConditionNoMatch(sqlNormalizedCacheFactory)

  @Test
  fun memoryThenSqlFragmentWithTypeConditionNoMatch() =
    basicTestHelper.fragmentWithTypeConditionNoMatch(memoryThenSqlNormalizedCacheFactory)
}
