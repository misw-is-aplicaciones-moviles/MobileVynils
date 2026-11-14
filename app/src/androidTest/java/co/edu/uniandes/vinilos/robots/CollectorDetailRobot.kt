package co.edu.uniandes.vinilos.robots

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.PerformException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.testing.AlbumSnapshot
import co.edu.uniandes.vinilos.testing.CollectorSnapshot
import co.edu.uniandes.vinilos.testing.FavoriteKind
import co.edu.uniandes.vinilos.testing.FavoriteSnapshot
import co.edu.uniandes.vinilos.testing.bringIntoView
import co.edu.uniandes.vinilos.testing.waitForDisplayed
import co.edu.uniandes.vinilos.viewmodels.collectorInitials
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not

/** Page Object del detalle de coleccionista: contacto, favoritos, álbumes y los estados de error. */
class CollectorDetailRobot {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    fun waitUntilReady(name: String): CollectorDetailRobot {
        val nameInList =
            allOf(withId(R.id.collectorName), withText(name), isDescendantOfA(withId(R.id.detailList)))
        waitForDisplayed(allOf(nameInList, isDisplayed())) { revealCollectorName(name) }
        return this
    }

    private fun revealCollectorName(name: String) {
        try {
            onView(withId(R.id.detailList)).perform(
                RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
                    hasDescendant(allOf(withId(R.id.collectorName), withText(name))),
                ),
            )
            onView(
                allOf(withId(R.id.collectorName), withText(name), isDescendantOfA(withId(R.id.detailList))),
            ).perform(bringIntoView())
        } catch (ignored: PerformException) {
            // La fila todavía no está en el adaptador.
        } catch (ignored: NoMatchingViewException) {
            // El detalle todavía no está en pantalla.
        }
    }

    /** Cabecera, teléfono y correo tal como los devolvió el GET, sin reformatear el teléfono. */
    fun assertContact(collector: CollectorSnapshot): CollectorDetailRobot {
        onView(withId(R.id.headerTitle)).check(matches(withText(R.string.collector_detail_title)))
        scrollTo(collector.name)
        onView(withId(R.id.collectorOverline)).check(matches(withText(R.string.collector_detail_title)))
        onView(withId(R.id.collectorName)).check(matches(withText(collector.name)))
        onView(withId(R.id.collectorInitials)).check(matches(withText(collectorInitials(collector.name))))
        scrollTo(collector.telephone)
        onView(withId(R.id.collectorPhone)).check(matches(withText(collector.telephone)))
        onView(withId(R.id.collectorEmail)).check(matches(withText(collector.email)))
        onView(withId(R.id.primaryAction)).check(matches(not(isDisplayed())))
        return this
    }

    fun assertCounts(collector: CollectorSnapshot): CollectorDetailRobot {
        assertSection(R.string.collector_section_favorites, collector.favorites.size)
        assertSection(R.string.collector_section_albums, collector.albums.size)
        return this
    }

    fun assertAddActionsHidden(): CollectorDetailRobot {
        assertAction(R.string.action_add_favorite, displayed = false)
        assertAction(R.string.action_add_album, displayed = false)
        return this
    }

    fun assertFavoriteDisabledAndAlbumEnabled(): CollectorDetailRobot {
        assertAction(R.string.action_add_favorite, displayed = true, enabled = false)
        assertAction(R.string.action_add_album, displayed = true, enabled = true)
        onView(withId(R.id.headerTitle)).check(matches(withText(R.string.collector_detail_title)))
        return this
    }

    fun tapAddAlbum(): CollectorDetailRobot {
        val label = context.getString(R.string.action_add_album)
        scrollTo(label)
        onView(allOf(withText(label), isEnabled(), isDisplayed())).perform(click())
        return this
    }

    fun assertFavorites(favorites: List<FavoriteSnapshot>): CollectorDetailRobot {
        if (favorites.isEmpty()) {
            val message = context.getString(R.string.collector_no_favorites)
            scrollTo(message)
            onView(allOf(withId(R.id.detailMessage), withText(message))).check(matches(isDisplayed()))
            return this
        }
        favorites.forEach { favorite ->
            val type = context.getString(typeRes(favorite.kind))
            // La tira publica sus filas en diferido: se espera a que cada nombre esté en pantalla.
            waitForDisplayed(allOf(withId(R.id.favoriteName), withText(favorite.name), isDisplayed())) {
                revealFavorite(favorite.name)
            }
            onView(typeOf(favorite)).check(matches(withText(type)))
        }
        return this
    }

    fun openFavorite(favorite: FavoriteSnapshot): CollectorDetailRobot {
        onView(allOf(withId(R.id.favoriteName), withText(favorite.name))).perform(click())
        waitForDisplayed(allOf(withId(R.id.performerName), withText(favorite.name), isDisplayed()))
        onView(allOf(withId(R.id.headerTitle), isDisplayed()))
            .check(matches(withText(typeRes(favorite.kind))))
        return this
    }

    fun assertAlbums(albums: List<AlbumSnapshot>): CollectorDetailRobot {
        if (albums.isEmpty()) {
            val message = context.getString(R.string.collector_no_albums)
            scrollTo(message)
            onView(allOf(withId(R.id.detailMessage), withText(message))).check(matches(isDisplayed()))
            return this
        }
        albums.forEach { album ->
            scrollTo(album.name)
            onView(allOf(withId(R.id.albumTitle), withText(album.name))).check(matches(isDisplayed()))
            onView(priceOf(album)).check(matches(withText(priceText(album.price))))
            onView(statusOf(album)).check(matches(withText(statusRes(album.status))))
        }
        return this
    }

    fun openAlbum(album: AlbumSnapshot): CollectorDetailRobot {
        scrollTo(album.name)
        onView(allOf(withId(R.id.albumTitle), withText(album.name))).perform(click())
        waitForDisplayed(allOf(withId(R.id.albumName), withText(album.name), isDisplayed()))
        onView(withId(R.id.headerTitle)).check(matches(withText(R.string.album_detail_title)))
        return this
    }

    /** Los comentarios viajan en el GET del coleccionista y esta pantalla no los pinta. */
    fun assertCommentsHidden(descriptions: List<String>): CollectorDetailRobot {
        descriptions.forEach { description -> onView(withText(description)).check(doesNotExist()) }
        onView(withId(R.id.commentAuthor)).check(doesNotExist())
        return this
    }

    fun assertNotFound(): CollectorDetailRobot {
        waitForDisplayed(withText(R.string.collector_detail_not_found))
        waitForDisplayed(withText(R.string.collector_detail_not_found_body))
        waitForDisplayed(withId(R.id.retryButton))
        return this
    }

    fun assertNetworkError(): CollectorDetailRobot {
        waitForDisplayed(withText(R.string.collector_detail_error))
        waitForDisplayed(withText(R.string.collector_list_error_body))
        waitForDisplayed(withId(R.id.retryButton))
        return this
    }

    fun tapRetry(): CollectorDetailRobot {
        onView(withId(R.id.retryButton)).perform(click())
        return this
    }

    fun tapBack(): CollectorDetailRobot {
        // En API 21 la cabecera sigue bajo la barra de estado un instante; click() exige el 90 % visible.
        val back = allOf(withId(R.id.headerBack), isDisplayingAtLeast(90))
        waitForDisplayed(back)
        onView(back).perform(click())
        return this
    }

    private fun assertSection(
        @StringRes titleRes: Int,
        count: Int,
    ) {
        val title = context.getString(titleRes, count)
        scrollTo(title)
        onView(allOf(withId(R.id.sectionTitle), withText(title))).check(matches(isDisplayed()))
    }

    private fun assertAction(
        @StringRes label: Int,
        displayed: Boolean,
        enabled: Boolean = false,
    ) {
        val text = context.getString(label)
        scrollTo(text)
        if (!displayed) {
            onView(withText(text)).check(matches(not(isDisplayed())))
            return
        }
        scrollTo(text)
        val action = onView(withText(text))
        if (enabled) {
            action.check(matches(allOf(isDisplayed(), isEnabled())))
        } else {
            action.check(matches(allOf(isDisplayed(), not(isEnabled()))))
        }
    }

    private fun scrollTo(text: String) {
        onView(withId(R.id.detailList)).perform(
            RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText(text))),
        )
    }

    private fun revealFavorite(name: String) {
        try {
            scrollToId(R.id.collectorFavorites)
            onView(withId(R.id.collectorFavorites)).perform(
                RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText(name))),
            )
        } catch (ignored: PerformException) {
            // La tira todavía no publicó esta fila.
        } catch (ignored: NoMatchingViewException) {
            // La sección de favoritos todavía no está en pantalla.
        }
    }

    private fun scrollToId(
        @IdRes id: Int,
    ) {
        onView(withId(R.id.detailList)).perform(
            RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(withId(id)),
        )
    }

    private fun typeOf(favorite: FavoriteSnapshot) = allOf(withId(R.id.favoriteType), inFavoriteRow(favorite.name))

    private fun priceOf(album: AlbumSnapshot) = allOf(withId(R.id.albumPrice), inAlbumRow(album.name))

    private fun statusOf(album: AlbumSnapshot) = allOf(withId(R.id.albumStatus), inAlbumRow(album.name))

    /** La fila del favorito, no la lista horizontal ni el detalle completo: ambos también contienen el nombre. */
    private fun inFavoriteRow(name: String) =
        isDescendantOfA(
            allOf(
                hasDescendant(allOf(withId(R.id.favoriteName), withText(name))),
                not(withId(R.id.collectorFavorites)),
                not(withId(R.id.detailList)),
            ),
        )

    /** La fila del álbum. Un ancestro más alto también contiene los otros álbumes y haría ambiguo el precio. */
    private fun inAlbumRow(name: String) =
        isDescendantOfA(
            allOf(
                withId(R.id.collectorAlbumRow),
                hasDescendant(allOf(withId(R.id.albumTitle), withText(name))),
            ),
        )

    private fun priceText(price: Int): String = context.getString(R.string.collector_album_price, price)

    @StringRes
    private fun statusRes(status: String): Int =
        when (status) {
            "Active" -> R.string.album_status_available
            "Inactive" -> R.string.album_status_unavailable
            else -> error("Estado de álbum no soportado: $status")
        }

    @StringRes
    private fun typeRes(kind: FavoriteKind): Int =
        when (kind) {
            FavoriteKind.MUSICIAN -> R.string.artist_musician
            FavoriteKind.BAND -> R.string.artist_band
        }
}
