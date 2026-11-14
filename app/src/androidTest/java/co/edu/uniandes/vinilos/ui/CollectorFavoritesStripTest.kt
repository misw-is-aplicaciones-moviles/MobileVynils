package co.edu.uniandes.vinilos.ui

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.MainActivity
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.models.AlbumStatus
import co.edu.uniandes.vinilos.models.Collector
import co.edu.uniandes.vinilos.models.CollectorAlbum
import co.edu.uniandes.vinilos.models.CollectorDetail
import co.edu.uniandes.vinilos.models.PerformerSummary
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.models.UserRole
import co.edu.uniandes.vinilos.ui.collectors.CollectorDetailAdapter
import co.edu.uniandes.vinilos.viewmodels.CollectorDetailUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La tira de favoritos conserva el desplazamiento horizontal al republicar la fila, y un toque usa el artista
 * vinculado en ese momento. Los favoritos se arman aquí: la prueba no depende de la semilla del backend.
 */
@RunWith(AndroidJUnit4::class)
class CollectorFavoritesStripTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private lateinit var strip: FavoritesStrip

    @Test
    fun republishing_the_strip_keeps_the_horizontal_position() {
        val favorites = favorites(FAVORITE_COUNT)
        val updatedName = "Artista actualizado"
        install()
        onStrip { show(detailWith(favorites)) }
        waitUntil("the detail did not publish") { detailRowCount() > FAVORITES_ROW }
        onStrip { detailList.scrollToPosition(FAVORITES_ROW) }
        waitUntil("the strip did not publish its favorites") { favoriteCount() == FAVORITE_COUNT }

        onStrip { favoritesStrip().scrollToPosition(FAVORITE_COUNT - 1) }
        waitUntil("the strip did not leave the first favorite") { firstVisibleFavorite() > 0 }
        val position = read { firstVisibleFavorite() }

        onStrip { show(detailWith(favorites.dropLast(1) + favorites.last().copy(name = updatedName))) }
        waitUntil("the updated favorite was not published") { boundFavoriteNames().lastOrNull() == updatedName }
        assertEquals(position, read { firstVisibleFavorite() })

        onStrip { detailList.scrollToPosition(detailRowCount() - 1) }
        waitUntil("the strip stayed on screen") { favoritesStripOrNull() == null }
        onStrip { detailList.scrollToPosition(FAVORITES_ROW) }
        waitUntil("rebinding the same favorites returned to the start") { firstVisibleFavorite() == position }
    }

    @Test
    fun a_tap_reports_the_favorite_bound_at_that_moment() {
        val clicked = mutableListOf<Int>()
        val replacement =
            PerformerSummary(
                id = REPLACEMENT_ID,
                name = favoriteName(REPLACEMENT_ID),
                type = PerformerType.MUSICIAN,
            )
        install()
        onStrip { onFavoriteClick = { favoriteId -> clicked += favoriteId } }
        onStrip { show(detailWith(favorites(2))) }
        waitUntil("the detail did not publish") { detailRowCount() > FAVORITES_ROW }
        onStrip { detailList.scrollToPosition(FAVORITES_ROW) }
        waitUntil("the first favorite was not bound") { visibleFavoriteNames().contains(favoriteName(1)) }

        onStrip { tapFavorite(favoriteName(1)) }
        assertEquals(listOf(1), clicked.toList())

        onStrip { show(detailWith(listOf(replacement, favorites(2)[1]))) }
        waitUntil("the replacement favorite was not bound") {
            visibleFavoriteNames().contains(favoriteName(REPLACEMENT_ID))
        }
        onStrip { tapFavorite(favoriteName(REPLACEMENT_ID)) }
        assertEquals(listOf(1, REPLACEMENT_ID), clicked.toList())
    }

    private fun install() {
        activityRule.scenario.onActivity { activity -> strip = FavoritesStrip(activity) }
    }

    private fun onStrip(block: FavoritesStrip.() -> Unit) {
        activityRule.scenario.onActivity { strip.block() }
    }

    private fun <T> read(block: FavoritesStrip.() -> T): T {
        var value: T? = null
        onStrip { value = block() }
        return checkNotNull(value)
    }

    private fun waitUntil(
        message: String,
        condition: FavoritesStrip.() -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (read(condition)) return
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(POLL_MILLIS)
        }
        fail(message)
    }

    private companion object {
        const val FAVORITE_COUNT = 40
        const val FAVORITES_ROW = 3
        const val ALBUM_COUNT = 4
        const val REPLACEMENT_ID = 9
        const val POLL_MILLIS = 50L
        const val TIMEOUT_MILLIS = 5_000L

        fun favorites(count: Int): List<PerformerSummary> =
            List(count) { index ->
                val id = index + 1
                PerformerSummary(id = id, name = favoriteName(id), type = PerformerType.MUSICIAN)
            }

        fun favoriteName(id: Int): String = "Artista $id"

        fun detailWith(favorites: List<PerformerSummary>): CollectorDetail {
            val albums =
                List(ALBUM_COUNT) { index ->
                    CollectorAlbum(
                        albumId = index + 1,
                        name = "Album $index",
                        coverUrl = "",
                        price = 1,
                        status = AlbumStatus.ACTIVE,
                    )
                }
            return CollectorDetail(
                collector =
                    Collector(
                        id = 1,
                        name = "Coleccionista de prueba",
                        email = "prueba@example.com",
                        albumCount = albums.size,
                        favoriteCount = favorites.size,
                    ),
                telephone = "3000000000",
                favorites = favorites,
                albums = albums,
            )
        }
    }
}

/** Lista corta, sin caché de filas, para forzar el rebind de la tira al salir de pantalla. */
private class FavoritesStrip(
    activity: Activity,
) {
    var onFavoriteClick: (Int) -> Unit = {}
    val detailList: RecyclerView
    private val adapter: CollectorDetailAdapter

    init {
        val viewport = (VIEWPORT_DP * activity.resources.displayMetrics.density).toInt()
        detailList =
            RecyclerView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, viewport)
                layoutManager = LinearLayoutManager(activity).apply { isItemPrefetchEnabled = false }
                setItemViewCacheSize(0)
            }
        (detailList.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        adapter =
            CollectorDetailAdapter(
                onAlbumClick = {},
                onFavoriteClick = { favoriteId -> onFavoriteClick(favoriteId) },
                onAddAlbumClick = {},
            )
        detailList.adapter = adapter
        (activity.window.decorView as ViewGroup).addView(detailList)
    }

    fun show(detail: CollectorDetail) {
        adapter.submitDetail(
            CollectorDetailUiState(detail = detail, isLoading = false, role = UserRole.VISITOR),
        )
    }

    private companion object {
        const val VIEWPORT_DP = 220
    }
}

private fun FavoritesStrip.favoritesStrip(): RecyclerView = checkNotNull(favoritesStripOrNull())

private fun FavoritesStrip.favoritesStripOrNull(): RecyclerView? = detailList.findViewById(R.id.collectorFavorites)

private fun FavoritesStrip.firstVisibleFavorite(): Int {
    val strip = favoritesStripOrNull() ?: return RecyclerView.NO_POSITION
    val layoutManager = strip.layoutManager as? LinearLayoutManager ?: return RecyclerView.NO_POSITION
    return layoutManager.findFirstVisibleItemPosition()
}

private fun FavoritesStrip.detailRowCount(): Int = detailList.adapter?.itemCount ?: 0

private fun FavoritesStrip.favoriteCount(): Int = favoritesStripOrNull()?.adapter?.itemCount ?: 0

@Suppress("UNCHECKED_CAST")
private fun FavoritesStrip.boundFavoriteNames(): List<String> {
    val rows =
        favoritesStripOrNull()?.adapter as? ListAdapter<PerformerSummary, RecyclerView.ViewHolder>
            ?: return emptyList()
    return rows.currentList.map { it.name }
}

private fun FavoritesStrip.visibleFavoriteNames(): List<String> {
    val strip = favoritesStripOrNull() ?: return emptyList()
    return (0 until strip.childCount).mapNotNull { index ->
        strip
            .getChildAt(index)
            .findViewById<TextView>(R.id.favoriteName)
            ?.text
            ?.toString()
    }
}

private fun FavoritesStrip.tapFavorite(name: String) {
    val strip = favoritesStrip()
    val row =
        (0 until strip.childCount)
            .map { strip.getChildAt(it) }
            .first { it.findViewById<TextView>(R.id.favoriteName)?.text?.toString() == name }
    row.performClick()
}
