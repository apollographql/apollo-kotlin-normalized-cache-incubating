package test

import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.toApolloResponse
import com.apollographql.cache.normalized.api.CacheHeaders
import com.apollographql.cache.normalized.api.CacheKey
import com.apollographql.cache.normalized.api.DefaultRecordMerger
import com.apollographql.cache.normalized.api.IdCacheKeyGenerator
import com.apollographql.cache.normalized.api.NormalizedCache
import com.apollographql.cache.normalized.api.Record
import com.apollographql.cache.normalized.internal.hashed
import com.apollographql.cache.normalized.internal.normalized
import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import httpcache.AllPlanetsQuery
import normalizer.EpisodeHeroNameQuery
import normalizer.HeroAndFriendsNamesQuery
import normalizer.HeroAndFriendsNamesWithIDForParentOnlyQuery
import normalizer.HeroAndFriendsNamesWithIDsQuery
import normalizer.HeroAppearsInQuery
import normalizer.HeroNameQuery
import normalizer.HeroParentTypeDependentFieldQuery
import normalizer.HeroTypeDependentAliasedFieldQuery
import normalizer.SameHeroTwiceQuery
import normalizer.type.Episode
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for the normalization without an instance of [com.apollographql.apollo.ApolloClient]
 */
class NormalizerTest {
  private lateinit var normalizedCache: NormalizedCache

  private val rootKey = "QUERY_ROOT"

  @BeforeTest
  fun setUp() {
    normalizedCache = MemoryCacheFactory().create()
  }

  @Test
  @Throws(Exception::class)
  fun testHeroName() {
    val records = records(HeroNameQuery(), "HeroNameResponse.json")
    val record = records.get(rootKey)
    val reference = record!!["hero"] as CacheKey?
    assertEquals(reference, CacheKey("hero".hashed()))
    val heroRecord = records.get(reference!!.key)
    assertEquals(heroRecord!!["name"], "R2-D2")
  }

  @Test
  @Throws(Exception::class)
  fun testMergeNull() {
    val record = Record(
        key = "Key",
        fields = mapOf("field1" to "value1"),
    )
    normalizedCache.merge(listOf(record), CacheHeaders.NONE, DefaultRecordMerger)

    val newRecord = Record(
        key = "Key",
        fields = mapOf("field2" to null),
    )

    normalizedCache.merge(listOf(newRecord), CacheHeaders.NONE, DefaultRecordMerger)
    val finalRecord = normalizedCache.loadRecord(record.key, CacheHeaders.NONE)
    assertTrue(finalRecord!!.containsKey("field2"))
    normalizedCache.remove(CacheKey(record.key), false)
  }

  @Test
  @Throws(Exception::class)
  fun testHeroNameWithVariable() {
    val records = records(EpisodeHeroNameQuery(Episode.JEDI), "EpisodeHeroNameResponse.json")
    val record = records.get(rootKey)
    val reference = record!![TEST_FIELD_KEY_JEDI] as CacheKey?
    assertEquals(reference, CacheKey(TEST_FIELD_KEY_JEDI.hashed()))
    val heroRecord = records.get(reference!!.key)
    assertEquals(heroRecord!!["name"], "R2-D2")
  }

  @Test
  @Throws(Exception::class)
  fun testHeroAppearsInQuery() {
    val records = records(HeroAppearsInQuery(), "HeroAppearsInResponse.json")

    val rootRecord = records.get(rootKey)!!

    val heroReference = rootRecord["hero"] as CacheKey?
    assertEquals(heroReference, CacheKey("hero".hashed()))

    val hero = records.get(heroReference!!.key)
    assertEquals(hero?.get("appearsIn"), listOf("NEWHOPE", "EMPIRE", "JEDI"))
  }

  @Test
  @Throws(Exception::class)
  fun testHeroAndFriendsNamesQueryWithoutIDs() {
    val records = records(HeroAndFriendsNamesQuery(Episode.JEDI), "HeroAndFriendsNameResponse.json")
    val record = records.get(rootKey)
    val heroReference = record!![TEST_FIELD_KEY_JEDI] as CacheKey?
    assertEquals(heroReference, CacheKey(TEST_FIELD_KEY_JEDI.hashed()))
    val heroRecord = records.get(heroReference!!.key)
    assertEquals(heroRecord!!["name"], "R2-D2")
    assertEquals(
        listOf(
            CacheKey("${TEST_FIELD_KEY_JEDI.hashed()}.friends.0".hashed()),
            CacheKey("${TEST_FIELD_KEY_JEDI.hashed()}.friends.1".hashed()),
            CacheKey("${TEST_FIELD_KEY_JEDI.hashed()}.friends.2".hashed())
        ),
        heroRecord["friends"]
    )
    val luke = records.get("${TEST_FIELD_KEY_JEDI.hashed()}.friends.0".hashed())
    assertEquals(luke!!["name"], "Luke Skywalker")
  }

  @Test
  @Throws(Exception::class)
  fun testHeroAndFriendsNamesQueryWithIDs() {
    val records = records(HeroAndFriendsNamesWithIDsQuery(Episode.JEDI), "HeroAndFriendsNameWithIdsResponse.json")
    val record = records.get(rootKey)
    val heroReference = record!![TEST_FIELD_KEY_JEDI] as CacheKey?
    assertEquals(CacheKey("Character:2001".hashed()), heroReference)
    val heroRecord = records.get(heroReference!!.key)
    assertEquals(heroRecord!!["name"], "R2-D2")
    assertEquals(
        listOf(
            CacheKey("Character:1000".hashed()),
            CacheKey("Character:1002".hashed()),
            CacheKey("Character:1003".hashed())
        ),
        heroRecord["friends"]
    )
    val luke = records.get("Character:1000".hashed())
    assertEquals(luke!!["name"], "Luke Skywalker")
  }

  @Test
  @Throws(Exception::class)
  fun testHeroAndFriendsNamesWithIDForParentOnly() {
    val records = records(HeroAndFriendsNamesWithIDForParentOnlyQuery(Episode.JEDI), "HeroAndFriendsNameWithIdsParentOnlyResponse.json")
    val record = records[rootKey]
    val heroReference = record!![TEST_FIELD_KEY_JEDI] as CacheKey?
    assertEquals(CacheKey("Character:2001".hashed()), heroReference)
    val heroRecord = records.get(heroReference!!.key)
    assertEquals(heroRecord!!["name"], "R2-D2")
    assertEquals(
        listOf(
            CacheKey("${"Character:2001".hashed()}.friends.0".hashed()),
            CacheKey("${"Character:2001".hashed()}.friends.1".hashed()),
            CacheKey("${"Character:2001".hashed()}.friends.2".hashed())
        ),
        heroRecord["friends"]
    )
    val luke = records.get("${"Character:2001".hashed()}.friends.0".hashed())
    assertEquals(luke!!["name"], "Luke Skywalker")
  }

  @Test
  @Throws(Exception::class)
  fun testSameHeroTwiceQuery() {
    val records = records(SameHeroTwiceQuery(), "SameHeroTwiceResponse.json")
    val record = records.get(rootKey)
    val heroReference = record!!["hero"] as CacheKey?
    val hero = records.get(heroReference!!.key)

    assertEquals(hero!!["name"], "R2-D2")
    assertEquals(hero["appearsIn"], listOf("NEWHOPE", "EMPIRE", "JEDI"))
  }

  @Test
  @Throws(Exception::class)
  fun testHeroTypeDependentAliasedFieldQueryDroid() {
    val records = records(HeroTypeDependentAliasedFieldQuery(Episode.JEDI), "HeroTypeDependentAliasedFieldResponse.json")
    val record = records.get(rootKey)
    val heroReference = record!![TEST_FIELD_KEY_JEDI] as CacheKey?
    val hero = records.get(heroReference!!.key)
    assertEquals(hero!!["primaryFunction"], "Astromech")
    assertEquals(hero["__typename"], "Droid")
  }

  @Test
  @Throws(Exception::class)
  fun testHeroTypeDependentAliasedFieldQueryHuman() {
    val records = records(HeroTypeDependentAliasedFieldQuery(Episode.EMPIRE), "HeroTypeDependentAliasedFieldResponseHuman.json")
    val record = records.get(rootKey)
    val heroReference = record!![TEST_FIELD_KEY_EMPIRE] as CacheKey?
    val hero = records.get(heroReference!!.key)
    assertEquals(hero!!["homePlanet"], "Tatooine")
    assertEquals(hero["__typename"], "Human")
  }

  @Test
  @Throws(Exception::class)
  fun testHeroParentTypeDependentAliasedFieldQueryHuman() {
    val records = records(HeroTypeDependentAliasedFieldQuery(Episode.EMPIRE), "HeroTypeDependentAliasedFieldResponseHuman.json")
    val record = records.get(rootKey)
    val heroReference = record!![TEST_FIELD_KEY_EMPIRE] as CacheKey?
    val hero = records.get(heroReference!!.key)
    assertEquals(hero!!["homePlanet"], "Tatooine")
    assertEquals(hero["__typename"], "Human")
  }

  @Test
  @Throws(Exception::class)
  fun testHeroParentTypeDependentFieldDroid() {
    val records = records(HeroParentTypeDependentFieldQuery(Episode.JEDI), "HeroParentTypeDependentFieldDroidResponse.json")
    val lukeRecord = records.get((TEST_FIELD_KEY_JEDI.hashed() + ".friends.0").hashed())
    assertEquals(lukeRecord!!["name"], "Luke Skywalker")
    assertEquals(lukeRecord["height({\"unit\":\"METER\"})"], 1.72)


    val friends = records[TEST_FIELD_KEY_JEDI.hashed()]!!["friends"]

    assertIs<List<Any>>(friends)
    assertEquals(friends[0], CacheKey((TEST_FIELD_KEY_JEDI.hashed() + ".friends.0").hashed()))
    assertEquals(friends[1], CacheKey((TEST_FIELD_KEY_JEDI.hashed() + ".friends.1").hashed()))
    assertEquals(friends[2], CacheKey((TEST_FIELD_KEY_JEDI.hashed() + ".friends.2").hashed()))
  }

  @Test
  fun list_of_objects_with_null_object() {
    val records = records(AllPlanetsQuery(), "AllPlanetsListOfObjectWithNullObject.json")
    val fieldKey = "allPlanets({\"first\":300})".hashed()

    var record: Record? = records["$fieldKey.planets.0".hashed()]
    assertTrue(record?.get("filmConnection") == null)
    record = records.get("${"$fieldKey.planets.0".hashed()}.filmConnection".hashed()) as Record?
    assertTrue(record == null)
    record = records.get("${"$fieldKey.planets.1".hashed()}.filmConnection".hashed())
    assertTrue(record != null)
  }


  @Test
  @Throws(Exception::class)
  fun testHeroParentTypeDependentFieldHuman() {
    val records = records(HeroParentTypeDependentFieldQuery(Episode.EMPIRE), "HeroParentTypeDependentFieldHumanResponse.json")

    val lukeRecord = records.get("${TEST_FIELD_KEY_EMPIRE.hashed()}.friends.0".hashed())
    assertEquals(lukeRecord!!["name"], "Han Solo")
    assertEquals(lukeRecord["height({\"unit\":\"FOOT\"})"], 5.905512)
  }

  companion object {
    internal fun <D : Operation.Data> records(operation: Operation<D>, name: String): Map<String, Record> {
      val response = testFixtureToJsonReader(name).toApolloResponse(operation)
      return response.data!!.normalized(operation, cacheKeyGenerator = IdCacheKeyGenerator())
    }

    private const val TEST_FIELD_KEY_JEDI = "hero({\"episode\":\"JEDI\"})"
    const val TEST_FIELD_KEY_EMPIRE = "hero({\"episode\":\"EMPIRE\"})"
  }
}
